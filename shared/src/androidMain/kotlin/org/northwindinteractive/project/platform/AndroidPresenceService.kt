package org.northwindinteractive.project.platform

import android.Manifest
import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.os.Build
import android.os.Looper
import androidx.core.content.ContextCompat
import co.touchlab.kermit.Logger
import com.google.android.gms.location.Geofence
import com.google.android.gms.location.GeofencingEvent
import com.google.android.gms.location.GeofencingRequest
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import com.google.android.gms.tasks.Task
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import org.northwindinteractive.project.core.domain.model.GeoPoint
import org.northwindinteractive.project.core.domain.model.LocationObservation
import org.northwindinteractive.project.core.domain.model.LocationPermission
import org.northwindinteractive.project.core.domain.model.LocationSource
import org.northwindinteractive.project.core.domain.model.RegionTransition
import org.northwindinteractive.project.core.domain.model.VenueSummary
import org.northwindinteractive.project.core.platform.PresenceService
import kotlin.coroutines.resume
import kotlin.time.Clock
import kotlin.time.Instant

/**
 * Google Play services geofencing + fused location adapter (spec: Android adapters).
 * Foreground updates only; region monitoring is limited to the registered venue.
 */
class AndroidPresenceService(
    private val context: Context,
    private val clock: Clock,
) : PresenceService {

    private val log = Logger.withTag("AndroidPresence")
    private val fused = LocationServices.getFusedLocationProviderClient(context)
    private val geofencing = LocationServices.getGeofencingClient(context)
    private val prefs = context.getSharedPreferences("angel_presence", Context.MODE_PRIVATE)

    private val _permission = MutableStateFlow(readPermission())
    override val permission: StateFlow<LocationPermission> = _permission.asStateFlow()

    override val regionTransitions: Flow<RegionTransition> = GeofenceEvents.transitions

    private val geofenceIntent: PendingIntent by lazy {
        val intent = Intent(context, GeofenceBroadcastReceiver::class.java)
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or
            (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_MUTABLE else 0)
        PendingIntent.getBroadcast(context, 0, intent, flags)
    }

    override fun refreshPermission() {
        _permission.value = readPermission()
    }

    override suspend fun requestForegroundPermission(): LocationPermission {
        prefs.edit().putBoolean(KEY_ASKED, true).apply()
        AndroidPermissionBridge.request(
            arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION),
        )
        refreshPermission()
        return _permission.value
    }

    override suspend fun requestBackgroundPermission(): LocationPermission {
        if (!hasPermission(Manifest.permission.ACCESS_FINE_LOCATION)) return _permission.value
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            AndroidPermissionBridge.request(arrayOf(Manifest.permission.ACCESS_BACKGROUND_LOCATION))
        }
        refreshPermission()
        return _permission.value
    }

    @SuppressLint("MissingPermission")
    override suspend fun currentLocation(): LocationObservation? {
        if (!_permission.value.isGranted) return null
        return try {
            withTimeoutOrNull(FIX_TIMEOUT_MS) {
                val token = CancellationTokenSource()
                fused.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, token.token)
                    .awaitOrNull { token.cancel() }
                    ?.toObservation()
            }
        } catch (e: SecurityException) {
            refreshPermission()
            null
        }
    }

    @SuppressLint("MissingPermission")
    override fun locationUpdates(): Flow<LocationObservation> = callbackFlow {
        val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, UPDATE_INTERVAL_MS)
            .setMinUpdateIntervalMillis(MIN_UPDATE_INTERVAL_MS)
            .build()
        val callback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                result.lastLocation?.let { trySend(it.toObservation()) }
            }
        }
        try {
            fused.requestLocationUpdates(request, callback, Looper.getMainLooper())
        } catch (e: SecurityException) {
            refreshPermission()
            close()
        }
        awaitClose { fused.removeLocationUpdates(callback) }
    }

    @SuppressLint("MissingPermission")
    override suspend fun startRegionMonitoring(venues: List<VenueSummary>) {
        if (!hasPermission(Manifest.permission.ACCESS_FINE_LOCATION) || venues.isEmpty()) return
        val geofences = venues.map { venue ->
            Geofence.Builder()
                .setRequestId(venue.venueId)
                .setCircularRegion(
                    venue.geofence.center.latitude,
                    venue.geofence.center.longitude,
                    venue.geofence.radiusMeters.toFloat(),
                )
                .setExpirationDuration(Geofence.NEVER_EXPIRE)
                .setTransitionTypes(Geofence.GEOFENCE_TRANSITION_ENTER or Geofence.GEOFENCE_TRANSITION_EXIT)
                .build()
        }
        val request = GeofencingRequest.Builder()
            .setInitialTrigger(0) // no synthetic ENTER on registration; presence is evaluated separately
            .addGeofences(geofences)
            .build()
        try {
            geofencing.removeGeofences(geofenceIntent).awaitOrNull()
            geofencing.addGeofences(request, geofenceIntent).awaitOrNull()
        } catch (e: SecurityException) {
            log.w(e) { "Geofence registration not permitted" }
        }
    }

    override suspend fun stopRegionMonitoring() {
        geofencing.removeGeofences(geofenceIntent).awaitOrNull()
    }

    private fun readPermission(): LocationPermission {
        val fine = hasPermission(Manifest.permission.ACCESS_FINE_LOCATION)
        val coarse = hasPermission(Manifest.permission.ACCESS_COARSE_LOCATION)
        return when {
            fine && (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q ||
                hasPermission(Manifest.permission.ACCESS_BACKGROUND_LOCATION)) -> LocationPermission.GRANTED_BACKGROUND
            fine -> LocationPermission.GRANTED_FOREGROUND
            // Approximate-only location cannot confirm venue presence; treat as needing precise access.
            coarse -> LocationPermission.DENIED
            !prefs.getBoolean(KEY_ASKED, false) -> LocationPermission.NOT_DETERMINED
            AndroidPermissionBridge.shouldShowRationale(Manifest.permission.ACCESS_FINE_LOCATION) -> LocationPermission.DENIED
            else -> LocationPermission.DENIED_PERMANENTLY
        }
    }

    private fun hasPermission(permission: String) =
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED

    private fun Location.toObservation() = LocationObservation(
        point = GeoPoint(latitude, longitude),
        accuracyMeters = if (hasAccuracy()) accuracy.toDouble() else Double.MAX_VALUE,
        // Fix time, not receipt time, so stale cached fixes are recognised as stale.
        observedAt = Instant.fromEpochMilliseconds(time.takeIf { it > 0 } ?: clock.now().toEpochMilliseconds()),
        source = LocationSource.FUSED,
    )

    private companion object {
        const val KEY_ASKED = "asked_location"
        const val FIX_TIMEOUT_MS = 10_000L
        const val UPDATE_INTERVAL_MS = 10_000L
        const val MIN_UPDATE_INTERVAL_MS = 5_000L
    }
}

private suspend fun <T> Task<T>.awaitOrNull(onCancel: () -> Unit = {}): T? =
    suspendCancellableCoroutine { cont ->
        addOnSuccessListener { cont.resume(it) }
        addOnFailureListener { cont.resume(null) }
        addOnCanceledListener { cont.resume(null) }
        cont.invokeOnCancellation { onCancel() }
    }

/** Process-wide relay from the OS broadcast into shared code. */
object GeofenceEvents {
    private val _transitions = MutableSharedFlow<RegionTransition>(extraBufferCapacity = 16)
    val transitions: Flow<RegionTransition> = _transitions.asSharedFlow()

    internal fun emit(transition: RegionTransition) {
        _transitions.tryEmit(transition)
    }
}

class GeofenceBroadcastReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val event = GeofencingEvent.fromIntent(intent) ?: return
        if (event.hasError()) {
            Logger.w { "Geofence error ${event.errorCode}" }
            return
        }
        val type = when (event.geofenceTransition) {
            Geofence.GEOFENCE_TRANSITION_ENTER -> RegionTransition.Type.ENTER
            Geofence.GEOFENCE_TRANSITION_EXIT -> RegionTransition.Type.EXIT
            else -> return
        }
        val now = Clock.System.now()
        event.triggeringGeofences.orEmpty().forEach { geofence ->
            GeofenceEvents.emit(RegionTransition(geofence.requestId, type, now))
        }
    }
}
