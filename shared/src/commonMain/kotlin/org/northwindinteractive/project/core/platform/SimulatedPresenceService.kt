package org.northwindinteractive.project.core.platform

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import org.northwindinteractive.project.core.domain.model.GeoPoint
import org.northwindinteractive.project.core.domain.model.LocationObservation
import org.northwindinteractive.project.core.domain.model.LocationPermission
import org.northwindinteractive.project.core.domain.model.LocationSource
import org.northwindinteractive.project.core.domain.model.RegionTransition
import org.northwindinteractive.project.core.domain.model.VenueSummary
import kotlin.time.Clock
import kotlin.time.Duration.Companion.seconds

/**
 * Scenario-driven presence for the demo build and for QA of every location state without
 * physically visiting a venue. Selectable from Settings → Demo controls.
 */
class SimulatedPresenceService(
    private val clock: Clock,
) : PresenceService {

    sealed interface Scenario {
        data class InsideVenue(val venue: VenueSummary) : Scenario
        data object OutsideCoverage : Scenario
        data object NoSignal : Scenario
        data class Imprecise(val venue: VenueSummary) : Scenario
    }

    private val _scenario = MutableStateFlow<Scenario>(Scenario.OutsideCoverage)
    val scenario: StateFlow<Scenario> = _scenario.asStateFlow()

    private val _permission = MutableStateFlow(LocationPermission.NOT_DETERMINED)
    override val permission: StateFlow<LocationPermission> = _permission.asStateFlow()

    private val transitions = MutableSharedFlow<RegionTransition>(extraBufferCapacity = 8)
    override val regionTransitions: Flow<RegionTransition> = transitions
    private var monitored: List<VenueSummary> = emptyList()

    fun setScenario(next: Scenario) {
        val previous = _scenario.value
        _scenario.value = next
        // Mimic OS region callbacks for monitored venues.
        monitored.forEach { venue ->
            val wasInside = previous is Scenario.InsideVenue && previous.venue.venueId == venue.venueId
            val isInside = next is Scenario.InsideVenue && next.venue.venueId == venue.venueId
            if (wasInside != isInside) {
                transitions.tryEmit(
                    RegionTransition(
                        venueId = venue.venueId,
                        type = if (isInside) RegionTransition.Type.ENTER else RegionTransition.Type.EXIT,
                        observedAt = clock.now(),
                    ),
                )
            }
        }
    }

    fun setPermission(permission: LocationPermission) {
        _permission.value = permission
    }

    override fun refreshPermission() = Unit

    override suspend fun requestForegroundPermission(): LocationPermission {
        if (_permission.value != LocationPermission.DENIED_PERMANENTLY) {
            _permission.value = LocationPermission.GRANTED_FOREGROUND
        }
        return _permission.value
    }

    override suspend fun requestBackgroundPermission(): LocationPermission {
        if (_permission.value.isGranted) _permission.value = LocationPermission.GRANTED_BACKGROUND
        return _permission.value
    }

    override suspend fun currentLocation(): LocationObservation? {
        delay(250)
        return observationFor(_scenario.value)
    }

    override fun locationUpdates(): Flow<LocationObservation> = _scenario.flatMapLatest { scenario ->
        flow {
            while (true) {
                observationFor(scenario)?.let { emit(it) }
                delay(5.seconds)
            }
        }
    }

    override suspend fun startRegionMonitoring(venues: List<VenueSummary>) {
        monitored = venues
    }

    override suspend fun stopRegionMonitoring() {
        monitored = emptyList()
    }

    private fun observationFor(scenario: Scenario): LocationObservation? = when (scenario) {
        is Scenario.InsideVenue -> LocationObservation(
            point = scenario.venue.geofence.center.offset(meters = 8.0),
            accuracyMeters = 12.0,
            observedAt = clock.now(),
            source = LocationSource.SIMULATED,
        )

        is Scenario.Imprecise -> LocationObservation(
            point = scenario.venue.geofence.center,
            accuracyMeters = 450.0,
            observedAt = clock.now(),
            source = LocationSource.SIMULATED,
        )

        Scenario.OutsideCoverage -> LocationObservation(
            point = GeoPoint(-37.8183, 144.9671), // Flinders Street Station, well outside demo venues
            accuracyMeters = 15.0,
            observedAt = clock.now(),
            source = LocationSource.SIMULATED,
        )

        Scenario.NoSignal -> null
    }

    private fun GeoPoint.offset(meters: Double): GeoPoint =
        copy(latitude = latitude + meters / 111_320.0)
}
