package org.northwindinteractive.project.core.data.demo

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import org.northwindinteractive.project.core.domain.model.AlertRequest
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
import org.northwindinteractive.project.core.platform.SecureStore

class DemoAuthRepository(
    private val backend: DemoBackend,
    private val store: SecureStore,
    scope: CoroutineScope,
) : AuthRepository {

    init {
        val uid = store.getString(KEY_UID)
        val email = store.getString(KEY_EMAIL)
        if (uid != null && email != null) backend.restore(uid, email)
    }

    override val authState: StateFlow<AuthState> = backend.currentUid
        .map { uid -> if (uid == null) AuthState.SignedOut else AuthState.SignedIn(uid, backend.emailFor(uid)) }
        .stateIn(scope, SharingStarted.Eagerly, AuthState.Loading)

    override suspend fun signIn(email: String, password: String): Result<Unit> =
        runCatching { backend.signIn(email.trim(), password) }.map { persist(it, email) }

    override suspend fun createAccount(email: String, password: String, displayAlias: String?): Result<Unit> =
        runCatching { backend.createAccount(email.trim(), password, displayAlias) }.map { persist(it, email) }

    override suspend fun sendPasswordReset(email: String): Result<Unit> =
        runCatching { backend.sendPasswordReset(email) }

    override suspend fun signOut() {
        store.remove(KEY_UID)
        store.remove(KEY_EMAIL)
        backend.signOut()
    }

    private fun persist(uid: String, email: String) {
        store.putString(KEY_UID, uid)
        store.putString(KEY_EMAIL, email.trim().lowercase())
    }

    private companion object {
        const val KEY_UID = "demo.uid"
        const val KEY_EMAIL = "demo.email"
    }
}

class DemoProfileRepository(private val backend: DemoBackend) : ProfileRepository {
    override fun observeProfile(): Flow<UserProfile?> = backend.currentUid.map { uid ->
        uid?.let { UserProfile(uid = it, email = backend.emailFor(it), displayAlias = backend.aliasFor(it), createdAt = null) }
    }
}

class DemoVenueRepository(private val backend: DemoBackend) : VenueRepository {
    override suspend fun getEligibleVenues(near: GeoPoint?): Result<List<VenueSummary>> =
        runCatching { backend.getEligibleVenues() }

    override fun observeRegistrations(): Flow<Map<String, VenueRegistration>> =
        combine(backend.currentUid, backend.registrations) { uid, all -> uid?.let { all[it] }.orEmpty() }

    override suspend fun joinVenue(venueId: String, evidence: PresenceEvidence) =
        runCatching { backend.joinVenue(venueId, evidence) }

    override suspend fun redeemVenueInvite(venueId: String, token: String, evidence: PresenceEvidence) =
        runCatching { backend.redeemVenueInvite(venueId, token, evidence) }

    override suspend fun requestVenueAccess(venueId: String, evidence: PresenceEvidence) =
        runCatching { backend.requestVenueAccess(venueId, evidence) }

    override suspend fun resumeVenueRegistration(venueId: String, evidence: PresenceEvidence) =
        runCatching { backend.resumeVenueRegistration(venueId, evidence) }

    override suspend fun reportVenueExit(venueId: String, evidence: PresenceEvidence) =
        runCatching { backend.reportVenueExit(venueId) }
}

class DemoAlertRepository(private val backend: DemoBackend) : AlertRepository {
    override suspend fun submitAlert(request: AlertRequest): Result<IncidentReceipt> =
        runCatching { backend.submitAlert(request) }

    override fun observeMyIncidents(): Flow<List<Incident>> =
        combine(backend.currentUid, backend.incidents) { uid, _ -> uid?.let(backend::incidentsFor).orEmpty() }

    override fun observeIncident(incidentId: String): Flow<Incident?> =
        observeMyIncidents().map { list -> list.firstOrNull { it.incidentId == incidentId } }

    override suspend fun getMyIncident(incidentId: String): Result<Incident> =
        runCatching { backend.getMyIncident(incidentId) }
}
