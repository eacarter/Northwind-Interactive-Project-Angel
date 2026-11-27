package org.northwindinteractive.project.core.data.demo

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.northwindinteractive.project.core.domain.model.AlertEvent
import org.northwindinteractive.project.core.domain.model.AlertEventType
import org.northwindinteractive.project.core.domain.model.AlertRequest
import org.northwindinteractive.project.core.domain.model.AngelError
import org.northwindinteractive.project.core.domain.model.EnrollmentMode
import org.northwindinteractive.project.core.domain.model.GeoPoint
import org.northwindinteractive.project.core.domain.model.Geofence
import org.northwindinteractive.project.core.domain.model.Incident
import org.northwindinteractive.project.core.domain.model.IncidentReceipt
import org.northwindinteractive.project.core.domain.model.IncidentStatus
import org.northwindinteractive.project.core.domain.model.LocationPermission
import org.northwindinteractive.project.core.domain.model.PresenceEvidence
import org.northwindinteractive.project.core.domain.model.PresenceState
import org.northwindinteractive.project.core.domain.model.RegistrationStatus
import org.northwindinteractive.project.core.domain.model.VenueRegistration
import org.northwindinteractive.project.core.domain.model.VenueSummary
import org.northwindinteractive.project.core.domain.model.ZoneEstimate
import org.northwindinteractive.project.core.domain.policy.EligibilityPolicy
import org.northwindinteractive.project.core.domain.policy.GracePolicy
import org.northwindinteractive.project.core.domain.policy.PresencePolicy
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.uuid.Uuid

/**
 * In-memory stand-in for the Cloud Functions + Firestore backend. It enforces the same server rules
 * described in the spec (presence evidence, entitlement, idempotency, versioned expiry, grace) so the
 * mobile UX behaves exactly as it will against the real backend.
 *
 * Fictional venues and simulated staff responses only. Not production telemetry.
 */
class DemoBackend(
    private val clock: Clock,
    private val scope: CoroutineScope,
) {
    /** Demo controls exposed in Settings. */
    data class Controls(
        val offline: Boolean = false,
        val autoApprove: Boolean = true,
        val simulateStaff: Boolean = true,
        /** Shortened grace for testing expiry; null uses each venue's configured grace minutes. */
        val graceOverride: Duration? = null,
    )

    private val mutex = Mutex()

    private val _controls = MutableStateFlow(Controls())
    val controls: StateFlow<Controls> = _controls.asStateFlow()

    private data class Account(val uid: String, val email: String, val password: String, val alias: String?)

    private val accounts = mutableMapOf<String, Account>()
    private val _currentUid = MutableStateFlow<String?>(null)
    val currentUid: StateFlow<String?> = _currentUid.asStateFlow()

    private val _registrations = MutableStateFlow<Map<String, Map<String, VenueRegistration>>>(emptyMap())
    val registrations: StateFlow<Map<String, Map<String, VenueRegistration>>> = _registrations.asStateFlow()

    private val _incidents = MutableStateFlow<Map<String, Incident>>(emptyMap())
    val incidents: StateFlow<Map<String, Incident>> = _incidents.asStateFlow()
    private val incidentOwners = mutableMapOf<String, String>()
    private val requestIndex = mutableMapOf<String, String>()
    private val alertTimestamps = mutableMapOf<String, MutableList<kotlin.time.Instant>>()

    val venues: List<VenueSummary> = listOf(
        VenueSummary(
            venueId = "vesper-lounge",
            displayName = "Vesper Lounge",
            publicAddress = "12 Mercer St, Melbourne",
            tagline = "Cocktails • Music • Late Night",
            geofence = Geofence(GeoPoint(-37.8126, 144.9617), radiusMeters = 60.0),
            enrollmentModes = listOf(EnrollmentMode.PUBLIC, EnrollmentMode.INVITATION, EnrollmentMode.APPROVAL),
            hasBeaconZones = false,
            zoneLabels = mapOf("A1B2C3D4E5F6" to "Second Floor Bar"),
        ),
        VenueSummary(
            venueId = "harbour-social",
            displayName = "Harbour Social",
            publicAddress = "4 Wharf Rd, Docklands",
            tagline = "Members & guests",
            geofence = Geofence(GeoPoint(-37.8179, 144.9460), radiusMeters = 80.0),
            enrollmentModes = listOf(EnrollmentMode.INVITATION),
        ),
        VenueSummary(
            venueId = "atrium-club",
            displayName = "The Atrium Club",
            publicAddress = "88 Collins St, Melbourne",
            tagline = "Approval required",
            geofence = Geofence(GeoPoint(-37.8150, 144.9700), radiusMeters = 50.0),
            enrollmentModes = listOf(EnrollmentMode.APPROVAL),
        ),
    )

    fun updateControls(transform: (Controls) -> Controls) = _controls.update(transform)

    // region Auth

    suspend fun signIn(email: String, password: String): String = call {
        val account = accounts[email.lowercase()]
        if (account == null || account.password != password) throw AngelError.InvalidCredentials()
        _currentUid.value = account.uid
        account.uid
    }

    suspend fun createAccount(email: String, password: String, alias: String?): String = call {
        val key = email.lowercase()
        if (key in accounts) throw AngelError.AccountExists()
        if (password.length < 8) throw AngelError.WeakPassword()
        val account = Account(uid = "demo-" + Uuid.random().toString().take(8), email = key, password = password, alias = alias)
        accounts[key] = account
        _currentUid.value = account.uid
        account.uid
    }

    /** Restores a persisted demo session (the in-memory account store does not survive restarts). */
    fun restore(uid: String, email: String) {
        accounts.getOrPut(email.lowercase()) { Account(uid, email.lowercase(), "", null) }
        _currentUid.value = uid
    }

    fun emailFor(uid: String): String? = accounts.values.firstOrNull { it.uid == uid }?.email
    fun aliasFor(uid: String): String? = accounts.values.firstOrNull { it.uid == uid }?.alias

    suspend fun sendPasswordReset(@Suppress("UNUSED_PARAMETER") email: String) = call { }

    fun signOut() {
        _currentUid.value = null
    }

    // endregion

    // region Venue functions

    suspend fun getEligibleVenues(): List<VenueSummary> = call { venues }

    suspend fun joinVenue(venueId: String, evidence: PresenceEvidence): VenueRegistration = call {
        val (uid, venue) = authorize(venueId)
        requireMode(venue, EnrollmentMode.PUBLIC)
        requireOnSite(venue, evidence)
        activate(uid, venue, EnrollmentMode.PUBLIC)
    }

    suspend fun redeemVenueInvite(venueId: String, token: String, evidence: PresenceEvidence): VenueRegistration = call {
        val (uid, venue) = authorize(venueId)
        requireMode(venue, EnrollmentMode.INVITATION)
        requireOnSite(venue, evidence)
        if (!token.equals(DEMO_INVITE_CODE, ignoreCase = true)) throw AngelError.InvalidInvitation()
        activate(uid, venue, EnrollmentMode.INVITATION)
    }

    suspend fun requestVenueAccess(venueId: String, evidence: PresenceEvidence): VenueRegistration = call {
        val (uid, venue) = authorize(venueId)
        requireMode(venue, EnrollmentMode.APPROVAL)
        requireOnSite(venue, evidence)
        val registration = VenueRegistration(
            id = Uuid.random().toString(),
            venueId = venueId,
            uid = uid,
            mode = EnrollmentMode.APPROVAL,
            status = RegistrationStatus.PENDING_APPROVAL,
            registeredAt = clock.now(),
            version = (current(uid, venueId)?.version ?: 0) + 1,
        )
        put(uid, registration)
        if (_controls.value.autoApprove) {
            scope.launch {
                delay(APPROVAL_DELAY)
                mutex.withLock {
                    val latest = current(uid, venueId)
                    if (latest?.id == registration.id && latest.status == RegistrationStatus.PENDING_APPROVAL) {
                        put(uid, latest.copy(status = RegistrationStatus.ACTIVE_INSIDE, registeredAt = clock.now(), version = latest.version + 1))
                    }
                }
            }
        }
        registration
    }

    suspend fun resumeVenueRegistration(venueId: String, evidence: PresenceEvidence): VenueRegistration = call {
        val (uid, venue) = authorize(venueId)
        val registration = current(uid, venueId) ?: throw AngelError.NotEntitled()
        requireOnSite(venue, evidence)
        when (registration.status) {
            RegistrationStatus.ACTIVE_INSIDE -> registration
            RegistrationStatus.GRACE_OUTSIDE -> {
                if (GracePolicy.isExpired(registration, clock.now())) {
                    registration.copy(status = RegistrationStatus.EXPIRED, version = registration.version + 1)
                        .also { put(uid, it) }
                } else {
                    registration.copy(
                        status = RegistrationStatus.ACTIVE_INSIDE,
                        exitedAt = null,
                        expiresAt = null,
                        version = registration.version + 1,
                    ).also { put(uid, it) }
                }
            }
            else -> registration
        }
    }

    suspend fun reportVenueExit(venueId: String): VenueRegistration = call {
        val (uid, venue) = authorize(venueId)
        val registration = current(uid, venueId) ?: throw AngelError.NotEntitled()
        if (registration.status != RegistrationStatus.ACTIVE_INSIDE) return@call registration
        val exitedAt = clock.now()
        val grace = _controls.value.graceOverride ?: venue.graceMinutes.minutes
        val updated = registration.copy(
            status = RegistrationStatus.GRACE_OUTSIDE,
            exitedAt = exitedAt,
            expiresAt = exitedAt + grace,
            version = registration.version + 1,
        )
        put(uid, updated)
        scheduleExpiry(uid, updated)
        markVisitorExited(uid, venueId)
        updated
    }

    // endregion

    // region Alert functions

    suspend fun submitAlert(request: AlertRequest): IncidentReceipt = call {
        val (uid, venue) = authorize(request.venueId)
        // Idempotency: the same request id always returns the same incident.
        requestIndex[request.requestId]?.let { existingId ->
            val existing = _incidents.value.getValue(existingId)
            return@call IncidentReceipt(existing.incidentId, existing.requestId, existing.createdAt, existing.status)
        }
        val registration = current(uid, request.venueId)
        if (registration?.status != RegistrationStatus.ACTIVE_INSIDE) throw AngelError.NotEntitled()
        requireOnSite(venue, request.evidence)

        val now = clock.now()
        val recent = alertTimestamps.getOrPut(uid) { mutableListOf() }.apply { removeAll { now - it > 10.minutes } }
        if (recent.size >= MAX_ALERTS_PER_10_MIN) throw AngelError.RateLimited()
        recent += now

        val incidentId = "INC-" + Uuid.random().toString().take(6).uppercase()
        val zone = request.evidence.beacons.maxByOrNull { it.rssi }?.zoneId
            ?.let { zoneId -> venue.zoneLabels[zoneId]?.let { ZoneEstimate(zoneId, it, now) } }
        val incident = Incident(
            incidentId = incidentId,
            requestId = request.requestId,
            venueId = venue.venueId,
            venueName = venue.displayName,
            status = IncidentStatus.RECEIVED,
            createdAt = now,
            timeline = listOf(
                AlertEvent(incidentId, actor = "visitor", eventType = AlertEventType.SUBMITTED, serverTime = now),
                AlertEvent(incidentId, actor = "system", eventType = AlertEventType.RECEIVED, serverTime = now),
            ),
            lastKnownZone = zone,
            lastKnownAt = request.evidence.location?.observedAt ?: now,
        )
        requestIndex[request.requestId] = incidentId
        incidentOwners[incidentId] = uid
        _incidents.update { it + (incidentId to incident) }
        if (_controls.value.simulateStaff) simulateStaff(incidentId)
        IncidentReceipt(incidentId, request.requestId, now, IncidentStatus.RECEIVED)
    }

    fun incidentsFor(uid: String): List<Incident> = _incidents.value.values
        .filter { incidentOwners[it.incidentId] == uid }
        .sortedByDescending { it.createdAt }

    suspend fun getMyIncident(incidentId: String): Incident = call {
        val uid = _currentUid.value ?: throw AngelError.NotAuthenticated()
        if (incidentOwners[incidentId] != uid) throw AngelError.NotEntitled()
        _incidents.value.getValue(incidentId)
    }

    // endregion

    private suspend fun <T> call(block: suspend () -> T): T {
        delay(LATENCY)
        if (_controls.value.offline) throw AngelError.Network()
        return mutex.withLock { block() }
    }

    private fun authorize(venueId: String): Pair<String, VenueSummary> {
        val uid = _currentUid.value ?: throw AngelError.NotAuthenticated()
        val venue = venues.firstOrNull { it.venueId == venueId } ?: throw AngelError.Unknown("Unknown venue")
        return uid to venue
    }

    private fun requireMode(venue: VenueSummary, mode: EnrollmentMode) {
        if (mode !in venue.enrollmentModes) throw AngelError.NotEntitled("This venue doesn't allow that enrollment method")
    }

    /** Server-side re-evaluation of the untrusted client evidence with bounded freshness. */
    private fun requireOnSite(venue: VenueSummary, evidence: PresenceEvidence) {
        val location = evidence.location ?: throw AngelError.PresenceRejected()
        val result = PresencePolicy.evaluate(
            observation = location,
            venues = listOf(venue),
            permission = LocationPermission.GRANTED_FOREGROUND,
            now = clock.now(),
        )
        if (result.presence.state != PresenceState.CONFIRMED_INSIDE) throw AngelError.PresenceRejected()
    }

    private fun current(uid: String, venueId: String): VenueRegistration? = _registrations.value[uid]?.get(venueId)

    private fun put(uid: String, registration: VenueRegistration) {
        _registrations.update { all ->
            val mine = all[uid].orEmpty() + (registration.venueId to registration)
            all + (uid to mine)
        }
    }

    private fun activate(uid: String, venue: VenueSummary, mode: EnrollmentMode): VenueRegistration {
        val existing = current(uid, venue.venueId)
        if (existing?.status == RegistrationStatus.ACTIVE_INSIDE) return existing
        if (existing?.status == RegistrationStatus.SUSPENDED || existing?.status == RegistrationStatus.REVOKED) {
            throw AngelError.NotEntitled("Access to this venue has been disabled")
        }
        val now = clock.now()
        return VenueRegistration(
            id = Uuid.random().toString(),
            venueId = venue.venueId,
            uid = uid,
            mode = mode,
            status = RegistrationStatus.ACTIVE_INSIDE,
            registeredAt = now,
            maxValidUntil = now + EligibilityPolicy.MAX_SESSION_VALIDITY,
            version = (existing?.version ?: 0) + 1,
        ).also { put(uid, it) }
    }

    /** Versioned expiry: a stale task never invalidates a resumed visit. */
    private fun scheduleExpiry(uid: String, registration: VenueRegistration) {
        val expiresAt = registration.expiresAt ?: return
        scope.launch {
            delay((expiresAt - clock.now()).coerceAtLeast(Duration.ZERO))
            mutex.withLock {
                val latest = current(uid, registration.venueId)
                if (latest != null && latest.version == registration.version &&
                    latest.status == RegistrationStatus.GRACE_OUTSIDE
                ) {
                    put(uid, latest.copy(status = RegistrationStatus.EXPIRED, version = latest.version + 1))
                    appendEventToOpenIncidents(uid, registration.venueId, AlertEventType.REGISTRATION_EXPIRED)
                }
            }
        }
    }

    private fun markVisitorExited(uid: String, venueId: String) {
        _incidents.update { all ->
            all.mapValues { (id, incident) ->
                if (incidentOwners[id] == uid && incident.venueId == venueId && incident.isOpen && !incident.visitorExited) {
                    incident.copy(
                        visitorExited = true,
                        timeline = incident.timeline + AlertEvent(id, "system", AlertEventType.VISITOR_EXITED, clock.now()),
                    )
                } else {
                    incident
                }
            }
        }
    }

    private fun appendEventToOpenIncidents(uid: String, venueId: String, type: AlertEventType) {
        _incidents.update { all ->
            all.mapValues { (id, incident) ->
                if (incidentOwners[id] == uid && incident.venueId == venueId && incident.isOpen) {
                    incident.copy(timeline = incident.timeline + AlertEvent(id, "system", type, clock.now()))
                } else {
                    incident
                }
            }
        }
    }

    private fun simulateStaff(incidentId: String) {
        scope.launch {
            delay(ACK_DELAY)
            transition(incidentId, IncidentStatus.ACKNOWLEDGED, AlertEventType.ACKNOWLEDGED)
            delay(RESPOND_DELAY)
            transition(incidentId, IncidentStatus.RESPONDING, AlertEventType.RESPONDING)
        }
    }

    private suspend fun transition(incidentId: String, status: IncidentStatus, type: AlertEventType) = mutex.withLock {
        _incidents.update { all ->
            val incident = all[incidentId] ?: return@update all
            if (!incident.isOpen) return@update all
            all + (
                incidentId to incident.copy(
                    status = status,
                    timeline = incident.timeline + AlertEvent(incidentId, "security", type, clock.now()),
                )
                )
        }
    }

    /** Demo-only: lets QA resolve an incident from Settings. */
    suspend fun resolveIncident(incidentId: String) =
        transition(incidentId, IncidentStatus.RESOLVED, AlertEventType.RESOLVED)

    companion object {
        const val DEMO_INVITE_CODE = "ANGEL-2026"
        private const val MAX_ALERTS_PER_10_MIN = 5
        private val LATENCY = 600.milliseconds
        private val APPROVAL_DELAY = 10.seconds
        private val ACK_DELAY = 6.seconds
        private val RESPOND_DELAY = 10.seconds
    }
}
