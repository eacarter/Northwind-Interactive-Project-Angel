package org.northwindinteractive.project.core.domain.session

import co.touchlab.kermit.Logger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.northwindinteractive.project.core.domain.model.AngelError
import org.northwindinteractive.project.core.domain.model.AuthState
import org.northwindinteractive.project.core.domain.model.BeaconObservation
import org.northwindinteractive.project.core.domain.model.EnrollmentMode
import org.northwindinteractive.project.core.domain.model.Incident
import org.northwindinteractive.project.core.domain.model.LocationObservation
import org.northwindinteractive.project.core.domain.model.LocationPermission
import org.northwindinteractive.project.core.domain.model.PresenceEvidence
import org.northwindinteractive.project.core.domain.model.PresenceState
import org.northwindinteractive.project.core.domain.model.RegionTransition
import org.northwindinteractive.project.core.domain.model.RegistrationStatus
import org.northwindinteractive.project.core.domain.model.VenueRegistration
import org.northwindinteractive.project.core.domain.model.VenueSummary
import org.northwindinteractive.project.core.domain.model.ZoneEstimate
import org.northwindinteractive.project.core.domain.model.toAngelError
import org.northwindinteractive.project.core.domain.policy.Eligibility
import org.northwindinteractive.project.core.domain.policy.EligibilityPolicy
import org.northwindinteractive.project.core.domain.policy.PresencePolicy
import org.northwindinteractive.project.core.domain.policy.PresenceResult
import org.northwindinteractive.project.core.domain.repository.AlertRepository
import org.northwindinteractive.project.core.domain.repository.AuthRepository
import org.northwindinteractive.project.core.domain.repository.VenueRepository
import org.northwindinteractive.project.core.platform.BeaconScanner
import org.northwindinteractive.project.core.platform.PresenceService
import kotlin.time.Clock
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

/**
 * Owns the location-gated venue visit lifecycle (spec: "Domain states and business rules"):
 *
 * Outside → Inside·not joined → Inside·active → Exited·grace → Reentry before expiry / Expiry·rejoin.
 *
 * Combines OS presence observations with the backend registration record, locks venue actions the
 * moment an exit is observed, and restores access only after the backend verifies reentry.
 * Existing incidents are never affected by exit or expiry.
 */
class VenueSessionCoordinator(
    private val authRepository: AuthRepository,
    private val venueRepository: VenueRepository,
    private val alertRepository: AlertRepository,
    private val presenceService: PresenceService,
    private val beaconScanner: BeaconScanner,
    private val clock: Clock,
    private val scope: CoroutineScope,
) {
    private val log = Logger.withTag("VenueSession")

    private val latestFix = MutableStateFlow<LocationObservation?>(null)
    private val beaconObservations = MutableStateFlow<List<BeaconObservation>>(emptyList())
    private val venues = MutableStateFlow<Map<String, VenueSummary>>(emptyMap())
    private val venuesError = MutableStateFlow<AngelError?>(null)
    private val locallyExited = MutableStateFlow<Set<String>>(emptySet())
    private val pendingResume = MutableStateFlow<Set<String>>(emptySet())
    private val foreground = MutableStateFlow(true)
    private val sessionStartedAt = MutableStateFlow(clock.now())

    private val lastExitAttempt = mutableMapOf<String, Instant>()
    private val lastResumeAttempt = mutableMapOf<String, Instant>()

    private val ticker: Flow<Instant> = flow {
        while (true) {
            emit(clock.now())
            delay(TICK)
        }
    }

    private val registrations: Flow<Map<String, VenueRegistration>> = authRepository.authState
        .flatMapLatest { auth ->
            if (auth is AuthState.SignedIn) {
                venueRepository.observeRegistrations().catch { error ->
                    log.w(error) { "Registration listener failed" }
                    emit(emptyMap())
                }
            } else {
                flowOf(emptyMap())
            }
        }

    private val incidents: Flow<List<Incident>> = authRepository.authState
        .flatMapLatest { auth ->
            if (auth is AuthState.SignedIn) {
                alertRepository.observeMyIncidents().catch { error ->
                    log.w(error) { "Incident listener failed" }
                    emit(emptyList())
                }
            } else {
                flowOf(emptyList())
            }
        }

    private data class Inputs(
        val permission: LocationPermission,
        val venues: Map<String, VenueSummary>,
        val fix: LocationObservation?,
        val beacons: List<BeaconObservation>,
        val registrations: Map<String, VenueRegistration>,
        val incidents: List<Incident>,
        val locallyExited: Set<String>,
        val pendingResume: Set<String>,
        val venuesError: AngelError?,
        val startedAt: Instant,
        val now: Instant,
    )

    val state: StateFlow<VenueSessionState> = combine(
        combine(presenceService.permission, venues, latestFix, beaconObservations, ::Quad),
        combine(registrations, incidents, locallyExited, pendingResume, ::Quad),
        combine(venuesError, sessionStartedAt, ticker, ::Triple),
    ) { a, b, c ->
        Inputs(
            permission = a.first,
            venues = a.second,
            fix = a.third,
            beacons = a.fourth,
            registrations = b.first,
            incidents = b.second,
            locallyExited = b.third,
            pendingResume = b.fourth,
            venuesError = c.first,
            startedAt = c.second,
            now = c.third,
        )
    }
        .map(::reduce)
        .stateIn(scope, SharingStarted.Eagerly, VenueSessionState.initial(clock.now()))

    init {
        scope.launch {
            authRepository.authState
                .map { it is AuthState.SignedIn }
                .distinctUntilChanged()
                .collectLatest { signedIn ->
                    if (signedIn) {
                        sessionStartedAt.value = clock.now()
                        runSession()
                    } else {
                        clearSession()
                    }
                }
        }
    }

    // region Public actions

    fun setForeground(isForeground: Boolean) {
        foreground.value = isForeground
        if (isForeground) presenceService.refreshPermission()
    }

    /** Pull-to-refresh / retry: re-read permission, fetch a fresh fix and reload venues. */
    fun refresh() {
        presenceService.refreshPermission()
        scope.launch { refreshFix() }
        scope.launch { loadVenues() }
    }

    suspend fun requestLocationPermission(): LocationPermission {
        val result = presenceService.requestForegroundPermission()
        if (result.isGranted) refreshFix()
        return result
    }

    suspend fun requestBackgroundPermission(): LocationPermission =
        presenceService.requestBackgroundPermission()

    /**
     * Collects fresh presence evidence for a sensitive operation. Always attempts a new fix when
     * the cached one is older than [EVIDENCE_MAX_AGE].
     */
    suspend fun freshEvidence(venueId: String): PresenceEvidence {
        val now = clock.now()
        val cached = latestFix.value
        if (cached == null || now - cached.observedAt > EVIDENCE_MAX_AGE) refreshFix()
        val snapshot = reduce(currentInputs())
        val presence = snapshot.presence?.presence
        return PresenceEvidence(
            venueId = venueId,
            location = latestFix.value,
            beacons = beaconObservations.value.filter { clock.now() - it.observedAt <= BEACON_WINDOW },
            clientState = presence?.takeIf { it.venueId == venueId }?.state ?: PresenceState.STALE_UNKNOWN,
            capturedAt = clock.now(),
        )
    }

    /** Public, invitation or approval enrollment. Only allowed with confirmed on-site presence. */
    suspend fun enroll(mode: EnrollmentMode, invitationToken: String? = null): Result<VenueRegistration> {
        val venue = state.value.venue ?: return Result.failure(AngelError.PresenceRejected())
        val evidence = freshEvidence(venue.venueId)
        val snapshot = state.value
        val eligibility = EligibilityPolicy.canEnroll(
            presence = snapshot.presence?.presence ?: return Result.failure(AngelError.PresenceRejected()),
            venueId = venue.venueId,
            now = clock.now(),
        )
        if (eligibility is Eligibility.Denied) return Result.failure(AngelError.PresenceRejected())

        val result = when (mode) {
            EnrollmentMode.PUBLIC -> venueRepository.joinVenue(venue.venueId, evidence)
            EnrollmentMode.INVITATION -> venueRepository.redeemVenueInvite(
                venueId = venue.venueId,
                token = invitationToken.orEmpty().trim(),
                evidence = evidence,
            )
            EnrollmentMode.APPROVAL -> venueRepository.requestVenueAccess(venue.venueId, evidence)
        }
        result.onSuccess { locallyExited.update { it - venue.venueId } }
        return result
    }

    // endregion

    private suspend fun runSession() = coroutineScope {
        launch { loadVenues() }
        launch { collectForegroundLocation() }
        launch { presenceService.regionTransitions.collect(::onRegionTransition) }
        launch { manageRegionMonitoring() }
        launch { collectBeacons() }
        launch { state.collect(::applySideEffects) }
    }

    private fun clearSession() {
        latestFix.value = null
        beaconObservations.value = emptyList()
        locallyExited.value = emptySet()
        pendingResume.value = emptySet()
        lastExitAttempt.clear()
        lastResumeAttempt.clear()
        scope.launch { runCatching { presenceService.stopRegionMonitoring() } }
    }

    private suspend fun loadVenues() {
        val near = latestFix.value?.point
        venueRepository.getEligibleVenues(near)
            .onSuccess { list ->
                venues.update { current -> current + list.associateBy { it.venueId } }
                venuesError.value = null
            }
            .onFailure { error ->
                log.w(error) { "Unable to load venues" }
                venuesError.value = error.toAngelError()
            }
    }

    private suspend fun refreshFix() {
        if (!presenceService.permission.value.isGranted) return
        presenceService.currentLocation()?.let { latestFix.value = it }
    }

    /** Location is only collected while the app is in the foreground (no off-site tracking). */
    private suspend fun collectForegroundLocation() {
        combine(foreground, presenceService.permission) { fg, permission -> fg && permission.isGranted }
            .distinctUntilChanged()
            .collectLatest { active ->
                if (active) {
                    presenceService.locationUpdates()
                        .catch { error -> log.w(error) { "Location updates failed" } }
                        .collect { latestFix.value = it }
                }
            }
    }

    /** Monitors only venues with a live registration so the OS can report exits. */
    private suspend fun manageRegionMonitoring() {
        state
            .map { snapshot ->
                snapshot.registration
                    ?.takeIf {
                        it.status == RegistrationStatus.ACTIVE_INSIDE ||
                            it.status == RegistrationStatus.GRACE_OUTSIDE
                    }
                    ?.let { reg -> snapshot.venue?.takeIf { it.venueId == reg.venueId } }
            }
            .distinctUntilChanged()
            .collectLatest { venue ->
                runCatching {
                    if (venue != null) {
                        presenceService.startRegionMonitoring(listOf(venue))
                    } else {
                        presenceService.stopRegionMonitoring()
                    }
                }.onFailure { log.w(it) { "Region monitoring unavailable" } }
            }
    }

    private suspend fun collectBeacons() {
        combine(state, foreground) { snapshot, fg ->
            snapshot.venue?.takeIf {
                fg && it.hasBeaconZones && beaconScanner.isSupported &&
                    snapshot.presence?.presence?.state == PresenceState.CONFIRMED_INSIDE
            }
        }
            .distinctUntilChanged()
            .collectLatest { venue ->
                beaconObservations.value = emptyList()
                if (venue != null) {
                    beaconScanner.scan(venue).collect { observation ->
                        val cutoff = clock.now() - BEACON_WINDOW
                        beaconObservations.update { current ->
                            (current.filter { it.observedAt >= cutoff && it.beaconId != observation.beaconId } + observation)
                        }
                    }
                }
            }
    }

    private suspend fun onRegionTransition(transition: RegionTransition) {
        log.i { "Region ${transition.type} for ${transition.venueId}" }
        val before = latestFix.value
        refreshFix()
        if (transition.type == RegionTransition.Type.EXIT) {
            val fix = latestFix.value
            // If no fresh fix is available (e.g. background), trust the OS exit event to lock actions.
            if (fix == null || fix == before) {
                lockAndReportExit(transition.venueId, evidenceLocation = null)
            }
        }
    }

    private fun applySideEffects(snapshot: VenueSessionState) {
        val now = snapshot.now
        val fix = latestFix.value

        // Exit detection: confirmed outside the registered venue → lock locally, report to backend.
        snapshot.registrations.values
            .filter { it.status == RegistrationStatus.ACTIVE_INSIDE }
            .forEach { registration ->
                val venue = snapshot.venues[registration.venueId] ?: return@forEach
                if (fix != null && PresencePolicy.isConfirmedOutside(fix, venue, now)) {
                    val last = lastExitAttempt[venue.venueId]
                    if (last == null || now - last > RETRY_INTERVAL) {
                        scope.launch { lockAndReportExit(venue.venueId, fix) }
                    }
                }
            }

        // Reentry: confirmed inside a venue whose registration is in grace (or locally exited).
        val insideVenueId = snapshot.presence?.presence
            ?.takeIf { it.state == PresenceState.CONFIRMED_INSIDE || it.state == PresenceState.REENTRY_PENDING }
            ?.venueId
        if (insideVenueId != null) {
            val registration = snapshot.registrations[insideVenueId]
            val needsResume = registration?.status == RegistrationStatus.GRACE_OUTSIDE ||
                (registration?.status == RegistrationStatus.ACTIVE_INSIDE && insideVenueId in snapshot.locallyExited)
            if (needsResume && insideVenueId !in pendingResume.value) {
                val last = lastResumeAttempt[insideVenueId]
                if (last == null || now - last > RETRY_INTERVAL) {
                    lastResumeAttempt[insideVenueId] = now
                    scope.launch { resume(insideVenueId) }
                }
            }
        }
    }

    private suspend fun lockAndReportExit(venueId: String, evidenceLocation: LocationObservation?) {
        lastExitAttempt[venueId] = clock.now()
        locallyExited.update { it + venueId }
        val evidence = PresenceEvidence(
            venueId = venueId,
            location = evidenceLocation,
            clientState = PresenceState.CONFIRMED_OUTSIDE,
            capturedAt = clock.now(),
        )
        venueRepository.reportVenueExit(venueId, evidence)
            .onFailure { log.w(it) { "Exit report failed; will retry. Venue actions remain locked." } }
    }

    private suspend fun resume(venueId: String) {
        pendingResume.update { it + venueId }
        try {
            val evidence = freshEvidence(venueId)
            venueRepository.resumeVenueRegistration(venueId, evidence)
                .onSuccess { registration ->
                    if (registration.status == RegistrationStatus.ACTIVE_INSIDE ||
                        registration.status == RegistrationStatus.EXPIRED
                    ) {
                        locallyExited.update { it - venueId }
                    }
                }
                .onFailure { log.w(it) { "Reentry verification failed" } }
        } finally {
            pendingResume.update { it - venueId }
        }
    }

    private suspend fun currentInputs(): Inputs {
        val s = state.value
        return Inputs(
            permission = presenceService.permission.value,
            venues = venues.value,
            fix = latestFix.value,
            beacons = beaconObservations.value,
            registrations = s.registrations,
            incidents = s.incidents,
            locallyExited = locallyExited.value,
            pendingResume = pendingResume.value,
            venuesError = venuesError.value,
            startedAt = sessionStartedAt.value,
            now = clock.now(),
        )
    }

    private fun reduce(inputs: Inputs): VenueSessionState {
        val now = inputs.now
        val venueList = inputs.venues.values.toList()
        val zone = estimateZone(inputs.beacons, venueList)
        val presenceResult = PresencePolicy.evaluate(
            observation = inputs.fix,
            venues = venueList,
            permission = inputs.permission,
            now = now,
            zone = zone,
        )
        val insideVenue = presenceResult.venue
            ?.takeIf { presenceResult.presence.state == PresenceState.CONFIRMED_INSIDE }

        // The registration that matters right now.
        val registration: VenueRegistration? = if (insideVenue != null) {
            inputs.registrations[insideVenue.venueId]
        } else {
            inputs.registrations.values
                .filter { it.isRelevantWhileAway(now) }
                .maxByOrNull { it.registeredAt ?: Instant.DISTANT_PAST }
        }

        // Exit observed locally but not yet recorded by the backend: lock immediately.
        val locallyExited = registration != null &&
            registration.venueId in inputs.locallyExited &&
            registration.status == RegistrationStatus.ACTIVE_INSIDE
        val effectiveRegistration = if (locallyExited) {
            registration!!.copy(status = RegistrationStatus.GRACE_OUTSIDE)
        } else {
            registration
        }

        val reentry = insideVenue != null && (
            effectiveRegistration?.status == RegistrationStatus.GRACE_OUTSIDE ||
                insideVenue.venueId in inputs.pendingResume
            )
        val presence = if (reentry) {
            presenceResult.presence.copy(state = PresenceState.REENTRY_PENDING)
        } else {
            presenceResult.presence
        }

        val venue = insideVenue
            ?: effectiveRegistration?.let { inputs.venues[it.venueId] }
            ?: presenceResult.venue

        val phase = when {
            !inputs.permission.isGranted -> VisitPhase.PERMISSION_REQUIRED
            effectiveRegistration?.status == RegistrationStatus.SUSPENDED ||
                effectiveRegistration?.status == RegistrationStatus.REVOKED -> VisitPhase.BLOCKED
            presence.state == PresenceState.REENTRY_PENDING -> VisitPhase.REENTRY_VERIFYING
            effectiveRegistration?.status == RegistrationStatus.PENDING_APPROVAL -> VisitPhase.PENDING_APPROVAL
            effectiveRegistration?.status == RegistrationStatus.REJECTED && insideVenue != null -> VisitPhase.REJECTED
            effectiveRegistration?.status == RegistrationStatus.GRACE_OUTSIDE -> VisitPhase.EXITED_GRACE
            effectiveRegistration?.status == RegistrationStatus.ACTIVE_INSIDE -> when (presence.state) {
                PresenceState.CONFIRMED_INSIDE -> VisitPhase.INSIDE_ACTIVE
                PresenceState.CONFIRMED_OUTSIDE -> VisitPhase.EXITED_GRACE
                else -> if (inputs.fix == null && now - inputs.startedAt < LOCATING_WINDOW) {
                    VisitPhase.LOCATING
                } else {
                    VisitPhase.LOCATION_UNKNOWN
                }
            }
            effectiveRegistration?.status == RegistrationStatus.EXPIRED -> VisitPhase.EXPIRED
            insideVenue != null -> VisitPhase.INSIDE_NOT_JOINED
            presence.state == PresenceState.CONFIRMED_OUTSIDE -> VisitPhase.OUTSIDE_COVERAGE
            inputs.fix == null && now - inputs.startedAt < LOCATING_WINDOW -> VisitPhase.LOCATING
            else -> VisitPhase.LOCATION_UNKNOWN
        }

        return VenueSessionState(
            permission = inputs.permission,
            venues = inputs.venues,
            presence = PresenceResult(venue = presenceResult.venue, presence = presence, reason = presenceResult.reason),
            venue = venue,
            registration = effectiveRegistration,
            registrations = inputs.registrations,
            phase = phase,
            alertEligibility = EligibilityPolicy.canSubmitAlert(effectiveRegistration, presence, now),
            incidents = inputs.incidents,
            venuesError = inputs.venuesError,
            locallyExited = inputs.locallyExited,
            now = now,
        )
    }

    private fun VenueRegistration.isRelevantWhileAway(now: Instant): Boolean = when (status) {
        RegistrationStatus.ACTIVE_INSIDE,
        RegistrationStatus.GRACE_OUTSIDE,
        RegistrationStatus.PENDING_APPROVAL,
        RegistrationStatus.SUSPENDED,
        RegistrationStatus.REVOKED -> true
        // Keep showing "Registration expired" for a while after it happens.
        RegistrationStatus.EXPIRED -> (expiresAt ?: exitedAt)?.let { now - it < 12.hours } ?: false
        RegistrationStatus.NOT_REGISTERED, RegistrationStatus.REJECTED -> false
    }

    /** Strongest recent beacon wins. RSSI is a proximity hint only; never converted to meters. */
    private fun estimateZone(beacons: List<BeaconObservation>, venues: List<VenueSummary>): ZoneEstimate? {
        val strongest = beacons.filter { it.zoneId != null }.maxByOrNull { it.rssi } ?: return null
        val zoneId = strongest.zoneId!!
        val label = venues.firstNotNullOfOrNull { it.zoneLabels[zoneId] } ?: return null
        return ZoneEstimate(zoneId = zoneId, label = label, observedAt = strongest.observedAt)
    }

    private data class Quad<A, B, C, D>(val first: A, val second: B, val third: C, val fourth: D)

    companion object {
        private val TICK = 5.seconds
        private val EVIDENCE_MAX_AGE = 20.seconds
        private val BEACON_WINDOW = 30.seconds
        private val RETRY_INTERVAL = 30.seconds
        private val LOCATING_WINDOW = 15.seconds
    }
}

/** High-level UI phase derived from presence + registration (one screen state per phase). */
enum class VisitPhase {
    PERMISSION_REQUIRED,
    LOCATING,
    LOCATION_UNKNOWN,
    OUTSIDE_COVERAGE,
    INSIDE_NOT_JOINED,
    PENDING_APPROVAL,
    REJECTED,
    INSIDE_ACTIVE,
    EXITED_GRACE,
    REENTRY_VERIFYING,
    EXPIRED,
    BLOCKED,
}

data class VenueSessionState(
    val permission: LocationPermission,
    val venues: Map<String, VenueSummary>,
    val presence: PresenceResult?,
    /** The venue in context: the one the visitor is inside, or the one they hold a registration for. */
    val venue: VenueSummary?,
    val registration: VenueRegistration?,
    val registrations: Map<String, VenueRegistration>,
    val phase: VisitPhase,
    val alertEligibility: Eligibility,
    val incidents: List<Incident>,
    val venuesError: AngelError?,
    val locallyExited: Set<String>,
    val now: Instant,
) {
    val openIncident: Incident? get() = incidents.firstOrNull { it.isOpen }
    val canSendAlert: Boolean get() = alertEligibility is Eligibility.Allowed

    companion object {
        fun initial(now: Instant) = VenueSessionState(
            permission = LocationPermission.NOT_DETERMINED,
            venues = emptyMap(),
            presence = null,
            venue = null,
            registration = null,
            registrations = emptyMap(),
            phase = VisitPhase.LOCATING,
            alertEligibility = Eligibility.Denied(org.northwindinteractive.project.core.domain.policy.DenialReason.LOCATION_UNKNOWN),
            incidents = emptyList(),
            venuesError = null,
            locallyExited = emptySet(),
            now = now,
        )
    }
}
