package org.northwindinteractive.project.platform

import co.touchlab.kermit.Logger
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.ObjCSignatureOverride
import kotlinx.cinterop.useContents
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import org.northwindinteractive.project.core.domain.model.GeoPoint
import org.northwindinteractive.project.core.domain.model.LocationObservation
import org.northwindinteractive.project.core.domain.model.LocationPermission
import org.northwindinteractive.project.core.domain.model.LocationSource
import org.northwindinteractive.project.core.domain.model.RegionTransition
import org.northwindinteractive.project.core.domain.model.VenueSummary
import org.northwindinteractive.project.core.platform.PresenceService
import platform.CoreLocation.CLAccuracyAuthorization
import platform.CoreLocation.CLAuthorizationStatus
import platform.CoreLocation.CLCircularRegion
import platform.CoreLocation.CLLocation
import platform.CoreLocation.CLLocationCoordinate2DMake
import platform.CoreLocation.CLLocationManager
import platform.CoreLocation.CLLocationManagerDelegateProtocol
import platform.CoreLocation.CLRegion
import platform.CoreLocation.kCLAuthorizationStatusAuthorizedAlways
import platform.CoreLocation.kCLAuthorizationStatusAuthorizedWhenInUse
import platform.CoreLocation.kCLAuthorizationStatusDenied
import platform.CoreLocation.kCLAuthorizationStatusNotDetermined
import platform.CoreLocation.kCLAuthorizationStatusRestricted
import platform.CoreLocation.kCLLocationAccuracyBest
import platform.Foundation.NSError
import platform.Foundation.timeIntervalSince1970
import platform.darwin.NSObject
import kotlin.time.Clock
import kotlin.time.Instant

/**
 * Core Location adapter (spec: iOS adapters). While-in-use location for presence checks, plus
 * region monitoring of the registered venue only so exits can lock venue actions.
 */
@OptIn(ExperimentalForeignApi::class)
class IosPresenceService(
    private val clock: Clock,
) : PresenceService {

    private val log = Logger.withTag("IosPresence")
    private val manager = CLLocationManager()
    private val fixes = MutableSharedFlow<LocationObservation>(extraBufferCapacity = 8)
    private val transitions = MutableSharedFlow<RegionTransition>(extraBufferCapacity = 8)
    private val _permission = MutableStateFlow(LocationPermission.NOT_DETERMINED)
    private var activeUpdateCollectors = 0

    private val delegate = LocationDelegate(
        onAuthorization = { refreshPermission() },
        onLocations = { locations -> locations.forEach { fixes.tryEmit(it.toObservation()) } },
        onError = { error -> log.w { "Location error: ${error.localizedDescription}" } },
        onRegion = { region, entered ->
            transitions.tryEmit(
                RegionTransition(
                    venueId = region.identifier,
                    type = if (entered) RegionTransition.Type.ENTER else RegionTransition.Type.EXIT,
                    observedAt = clock.now(),
                ),
            )
        },
    )

    init {
        manager.delegate = delegate
        manager.desiredAccuracy = kCLLocationAccuracyBest
        refreshPermission()
    }

    override val permission: StateFlow<LocationPermission> = _permission.asStateFlow()
    override val regionTransitions: Flow<RegionTransition> = transitions.asSharedFlow()

    override fun refreshPermission() {
        _permission.value = map(manager.authorizationStatus)
    }

    override suspend fun requestForegroundPermission(): LocationPermission {
        if (_permission.value != LocationPermission.NOT_DETERMINED) return _permission.value
        manager.requestWhenInUseAuthorization()
        return withTimeoutOrNull(60_000) {
            permission.first { it != LocationPermission.NOT_DETERMINED }
        } ?: _permission.value
    }

    override suspend fun requestBackgroundPermission(): LocationPermission {
        if (_permission.value != LocationPermission.GRANTED_FOREGROUND) return _permission.value
        manager.requestAlwaysAuthorization()
        return withTimeoutOrNull(60_000) {
            permission.first { it != LocationPermission.GRANTED_FOREGROUND }
        } ?: _permission.value
    }

    override suspend fun currentLocation(): LocationObservation? {
        if (!_permission.value.isGranted) return null
        return withTimeoutOrNull(10_000) {
            coroutineScope {
                // Subscribe before asking so the delegate callback can't be missed.
                val next = async(start = CoroutineStart.UNDISPATCHED) { fixes.first() }
                manager.requestLocation()
                next.await()
            }
        }
    }

    override fun locationUpdates(): Flow<LocationObservation> = callbackFlow {
        val job = launch(start = CoroutineStart.UNDISPATCHED) { fixes.collect { send(it) } }
        activeUpdateCollectors++
        manager.startUpdatingLocation()
        awaitClose {
            job.cancel()
            activeUpdateCollectors--
            if (activeUpdateCollectors <= 0) manager.stopUpdatingLocation()
        }
    }

    override suspend fun startRegionMonitoring(venues: List<VenueSummary>) {
        stopRegionMonitoring()
        if (!CLLocationManager.isMonitoringAvailableForClass(CLCircularRegion)) return
        venues.forEach { venue ->
            val region = CLCircularRegion(
                center = CLLocationCoordinate2DMake(venue.geofence.center.latitude, venue.geofence.center.longitude),
                radius = venue.geofence.radiusMeters,
                identifier = venue.venueId,
            )
            region.notifyOnEntry = true
            region.notifyOnExit = true
            manager.startMonitoringForRegion(region)
        }
    }

    override suspend fun stopRegionMonitoring() {
        manager.monitoredRegions.forEach { region ->
            (region as? CLRegion)?.let { manager.stopMonitoringForRegion(it) }
        }
    }

    private fun map(status: CLAuthorizationStatus): LocationPermission = when (status) {
        kCLAuthorizationStatusNotDetermined -> LocationPermission.NOT_DETERMINED
        kCLAuthorizationStatusRestricted, kCLAuthorizationStatusDenied -> LocationPermission.DENIED_PERMANENTLY
        kCLAuthorizationStatusAuthorizedAlways -> precise(LocationPermission.GRANTED_BACKGROUND)
        kCLAuthorizationStatusAuthorizedWhenInUse -> precise(LocationPermission.GRANTED_FOREGROUND)
        else -> LocationPermission.NOT_DETERMINED
    }

    /** Approximate location cannot confirm venue presence; the user must enable Precise Location. */
    private fun precise(granted: LocationPermission): LocationPermission =
        if (manager.accuracyAuthorization == CLAccuracyAuthorization.CLAccuracyAuthorizationReducedAccuracy) {
            LocationPermission.DENIED_PERMANENTLY
        } else {
            granted
        }

    private fun CLLocation.toObservation(): LocationObservation {
        val (lat, lng) = coordinate.useContents { latitude to longitude }
        return LocationObservation(
            point = GeoPoint(lat, lng),
            accuracyMeters = if (horizontalAccuracy >= 0) horizontalAccuracy else Double.MAX_VALUE,
            observedAt = Instant.fromEpochMilliseconds((timestamp.timeIntervalSince1970 * 1000).toLong()),
            source = LocationSource.GPS,
        )
    }
}

/** Standalone delegate (Objective-C subclasses can't be inner classes). */
@OptIn(ExperimentalForeignApi::class)
private class LocationDelegate(
    private val onAuthorization: () -> Unit,
    private val onLocations: (List<CLLocation>) -> Unit,
    private val onError: (NSError) -> Unit,
    private val onRegion: (CLRegion, Boolean) -> Unit,
) : NSObject(), CLLocationManagerDelegateProtocol {

    override fun locationManagerDidChangeAuthorization(manager: CLLocationManager) = onAuthorization()

    override fun locationManager(manager: CLLocationManager, didUpdateLocations: List<*>) {
        onLocations(didUpdateLocations.filterIsInstance<CLLocation>())
    }

    override fun locationManager(manager: CLLocationManager, didFailWithError: NSError) = onError(didFailWithError)

    @ObjCSignatureOverride
    override fun locationManager(manager: CLLocationManager, didEnterRegion: CLRegion) = onRegion(didEnterRegion, true)

    @ObjCSignatureOverride
    override fun locationManager(manager: CLLocationManager, didExitRegion: CLRegion) = onRegion(didExitRegion, false)
}
