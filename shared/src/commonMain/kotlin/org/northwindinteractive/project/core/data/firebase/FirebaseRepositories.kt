package org.northwindinteractive.project.core.data.firebase

import co.touchlab.kermit.Logger
import dev.gitlive.firebase.FirebaseNetworkException
import dev.gitlive.firebase.auth.FirebaseAuth
import dev.gitlive.firebase.auth.FirebaseAuthInvalidCredentialsException
import dev.gitlive.firebase.auth.FirebaseAuthInvalidUserException
import dev.gitlive.firebase.auth.FirebaseAuthUserCollisionException
import dev.gitlive.firebase.auth.FirebaseAuthWeakPasswordException
import dev.gitlive.firebase.firestore.Direction
import dev.gitlive.firebase.firestore.FirebaseFirestore
import dev.gitlive.firebase.functions.FirebaseFunctions
import dev.gitlive.firebase.functions.FirebaseFunctionsException
import dev.gitlive.firebase.functions.FunctionsExceptionCode
import dev.gitlive.firebase.functions.code
import dev.gitlive.firebase.functions.details
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import org.northwindinteractive.project.core.domain.model.AlertRequest
import org.northwindinteractive.project.core.domain.model.AngelError
import org.northwindinteractive.project.core.domain.model.AuthState
import org.northwindinteractive.project.core.domain.model.GeoPoint
import org.northwindinteractive.project.core.domain.model.Incident
import org.northwindinteractive.project.core.domain.model.IncidentReceipt
import org.northwindinteractive.project.core.domain.model.PresenceEvidence
import org.northwindinteractive.project.core.domain.model.UserProfile
import org.northwindinteractive.project.core.domain.model.VenueRegistration
import org.northwindinteractive.project.core.domain.model.VenueSummary
import org.northwindinteractive.project.core.domain.repository.AlertRepository
import org.northwindinteractive.project.core.domain.repository.AuthRepository
import org.northwindinteractive.project.core.domain.repository.ProfileRepository
import org.northwindinteractive.project.core.domain.repository.VenueRepository
import kotlin.time.Clock
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

/** Firestore collection layout written by the Cloud Functions (see /firebase/firestore.rules). */
internal object Paths {
    const val USERS = "users"
    const val REGISTRATIONS = "registrations"
    const val INCIDENTS = "incidents"
}

/** Maps SDK exceptions onto typed [AngelError]s. Functions send a machine reason in `details`. */
internal fun Throwable.toAngelFailure(): AngelError = when (this) {
    is AngelError -> this
    is FirebaseNetworkException -> AngelError.Network()
    is FirebaseAuthWeakPasswordException -> AngelError.WeakPassword()
    is FirebaseAuthInvalidCredentialsException, is FirebaseAuthInvalidUserException -> AngelError.InvalidCredentials()
    is FirebaseAuthUserCollisionException -> AngelError.AccountExists()
    is FirebaseFunctionsException -> {
        val reason = details?.toString().orEmpty()
        val msg = message ?: ""
        when {
            "INVALID_INVITATION" in reason -> AngelError.InvalidInvitation()
            "PRESENCE_REJECTED" in reason -> AngelError.PresenceRejected()
            "NOT_ENTITLED" in reason -> AngelError.NotEntitled(msg.ifBlank { "You're not registered for this venue" })
            code == FunctionsExceptionCode.UNAVAILABLE || code == FunctionsExceptionCode.DEADLINE_EXCEEDED -> AngelError.Network()
            code == FunctionsExceptionCode.UNAUTHENTICATED -> AngelError.NotAuthenticated()
            code == FunctionsExceptionCode.RESOURCE_EXHAUSTED -> AngelError.RateLimited()
            code == FunctionsExceptionCode.FAILED_PRECONDITION -> AngelError.PresenceRejected()
            code == FunctionsExceptionCode.PERMISSION_DENIED -> AngelError.NotEntitled()
            else -> AngelError.Unknown()
        }
    }
    else -> {
        Logger.w(this) { "Unmapped backend error" }
        AngelError.Unknown()
    }
}

private inline fun <T> firebaseCall(block: () -> T): Result<T> = try {
    Result.success(block())
} catch (e: kotlinx.coroutines.CancellationException) {
    throw e
} catch (e: Throwable) {
    Result.failure(e.toAngelFailure())
}

class FirebaseAuthRepository(
    private val auth: FirebaseAuth,
    private val firestore: FirebaseFirestore,
    private val clock: Clock,
    scope: CoroutineScope,
) : AuthRepository {

    override val authState: StateFlow<AuthState> = auth.authStateChanged
        .map { user -> if (user == null) AuthState.SignedOut else AuthState.SignedIn(user.uid, user.email) }
        .stateIn(scope, SharingStarted.Eagerly, AuthState.Loading)

    override suspend fun signIn(email: String, password: String): Result<Unit> = firebaseCall {
        auth.signInWithEmailAndPassword(email.trim(), password)
        Unit
    }

    override suspend fun createAccount(email: String, password: String, displayAlias: String?): Result<Unit> = firebaseCall {
        if (password.length < 8) throw AngelError.WeakPassword()
        val user = auth.createUserWithEmailAndPassword(email.trim(), password).user
            ?: throw AngelError.Unknown()
        // Minimal PII: uid, email and an optional alias. Rules restrict writes to these fields.
        firestore.collection(Paths.USERS).document(user.uid).set(
            UserProfileDto(
                uid = user.uid,
                email = user.email,
                displayAlias = displayAlias?.takeIf { it.isNotBlank() },
                createdAtMs = clock.now().toEpochMilliseconds(),
            ),
        )
        Unit
    }

    override suspend fun sendPasswordReset(email: String): Result<Unit> = firebaseCall {
        auth.sendPasswordResetEmail(email.trim())
    }

    override suspend fun signOut() {
        runCatching { auth.signOut() }
    }
}

class FirebaseProfileRepository(
    private val auth: FirebaseAuth,
    private val firestore: FirebaseFirestore,
) : ProfileRepository {
    override fun observeProfile(): Flow<UserProfile?> = auth.authStateChanged.flatMapLatest { user ->
        if (user == null) {
            flowOf(null)
        } else {
            firestore.collection(Paths.USERS).document(user.uid).snapshots.map { snapshot ->
                val dto = if (snapshot.exists) runCatching { snapshot.data<UserProfileDto>() }.getOrNull() else null
                UserProfile(
                    uid = user.uid,
                    email = dto?.email ?: user.email,
                    displayAlias = dto?.displayAlias,
                    createdAt = dto?.createdAtMs?.let(Instant::fromEpochMilliseconds),
                )
            }
        }
    }
}

class FirebaseVenueRepository(
    private val auth: FirebaseAuth,
    private val firestore: FirebaseFirestore,
    private val functions: FirebaseFunctions,
) : VenueRepository {

    override suspend fun getEligibleVenues(near: GeoPoint?): Result<List<VenueSummary>> = firebaseCall {
        functions.httpsCallable("getEligibleVenues", TIMEOUT)
            .invoke(GetEligibleVenuesRequest(near?.latitude, near?.longitude))
            .data<VenueListDto>()
            .venues.map { it.toDomain() }
    }

    override fun observeRegistrations(): Flow<Map<String, VenueRegistration>> =
        auth.authStateChanged.flatMapLatest { user ->
            if (user == null) {
                flowOf(emptyMap())
            } else {
                firestore.collection(Paths.USERS).document(user.uid).collection(Paths.REGISTRATIONS)
                    .snapshots
                    .map { snapshot ->
                        snapshot.documents
                            .mapNotNull { doc -> runCatching { doc.data<RegistrationDto>().toDomain() }.getOrNull() }
                            .associateBy { it.venueId }
                    }
            }
        }

    override suspend fun joinVenue(venueId: String, evidence: PresenceEvidence) =
        registrationCall("joinVenue", VenueEvidenceRequest(venueId, evidence.toDto()))

    override suspend fun redeemVenueInvite(venueId: String, token: String, evidence: PresenceEvidence) = firebaseCall {
        functions.httpsCallable("redeemVenueInvite", TIMEOUT)
            .invoke(RedeemInviteRequest(venueId, token, evidence.toDto()))
            .data<RegistrationDto>()
            .toDomain()
    }

    override suspend fun requestVenueAccess(venueId: String, evidence: PresenceEvidence) =
        registrationCall("requestVenueAccess", VenueEvidenceRequest(venueId, evidence.toDto()))

    override suspend fun resumeVenueRegistration(venueId: String, evidence: PresenceEvidence) =
        registrationCall("resumeVenueRegistration", VenueEvidenceRequest(venueId, evidence.toDto()))

    override suspend fun reportVenueExit(venueId: String, evidence: PresenceEvidence) =
        registrationCall("reportVenueExit", VenueEvidenceRequest(venueId, evidence.toDto()))

    private suspend fun registrationCall(name: String, request: VenueEvidenceRequest): Result<VenueRegistration> =
        firebaseCall {
            functions.httpsCallable(name, TIMEOUT).invoke(request).data<RegistrationDto>().toDomain()
        }

    private companion object {
        val TIMEOUT = 20.seconds
    }
}

class FirebaseAlertRepository(
    private val auth: FirebaseAuth,
    private val firestore: FirebaseFirestore,
    private val functions: FirebaseFunctions,
) : AlertRepository {

    override suspend fun submitAlert(request: AlertRequest): Result<IncidentReceipt> = firebaseCall {
        functions.httpsCallable("submitAlert", 15.seconds)
            .invoke(request.toDto())
            .data<ReceiptDto>()
            .toDomain()
    }

    override fun observeMyIncidents(): Flow<List<Incident>> = auth.authStateChanged.flatMapLatest { user ->
        if (user == null) {
            flowOf(emptyList())
        } else {
            firestore.collection(Paths.INCIDENTS)
                .where { "uid" equalTo user.uid }
                .orderBy("createdAtMs", Direction.DESCENDING)
                .limit(50)
                .snapshots
                .map { snapshot ->
                    snapshot.documents.mapNotNull { doc ->
                        runCatching { doc.data<IncidentDto>().toDomain() }
                            .onFailure { Logger.w(it) { "Bad incident document ${doc.id}" } }
                            .getOrNull()
                    }
                }
        }
    }

    override fun observeIncident(incidentId: String): Flow<Incident?> =
        firestore.collection(Paths.INCIDENTS).document(incidentId).snapshots.map { doc ->
            if (doc.exists) runCatching { doc.data<IncidentDto>().toDomain() }.getOrNull() else null
        }

    override suspend fun getMyIncident(incidentId: String): Result<Incident> = firebaseCall {
        functions.httpsCallable("getMyIncident", 15.seconds)
            .invoke(IncidentIdRequest(incidentId))
            .data<IncidentDto>()
            .toDomain()
    }
}
