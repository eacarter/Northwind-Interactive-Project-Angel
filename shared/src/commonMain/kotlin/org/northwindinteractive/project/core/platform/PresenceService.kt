package org.northwindinteractive.project.core.platform

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import org.northwindinteractive.project.core.domain.model.BeaconObservation
import org.northwindinteractive.project.core.domain.model.LocationObservation
import org.northwindinteractive.project.core.domain.model.LocationPermission
import org.northwindinteractive.project.core.domain.model.RegionTransition
import org.northwindinteractive.project.core.domain.model.VenueSummary

/**
 * Explicit platform boundary for location (spec: "The shared application consumes an explicit
 * PresenceService interface rather than importing Android or iOS APIs directly").
 *
 * Implementations report raw observations only. Domain code decides whether state is fresh enough
 * to allow a sensitive operation.
 */
interface PresenceService {
    val permission: StateFlow<LocationPermission>

    /** Re-reads the OS permission state (e.g. after returning from system settings). */
    fun refreshPermission()

    /** Asks for while-in-use location. Call only after the contextual explanation was shown. */
    suspend fun requestForegroundPermission(): LocationPermission

    /**
     * Optional background permission so the OS can deliver geofence exit events while the app is
     * closed. Never required for core functionality.
     */
    suspend fun requestBackgroundPermission(): LocationPermission

    /** A single fresh fix, or null on timeout / unavailable sensor. */
    suspend fun currentLocation(): LocationObservation?

    /** Foreground-only stream of fixes. Collection must stop when the app leaves the foreground. */
    fun locationUpdates(): Flow<LocationObservation>

    /** Region monitoring for the venue(s) the visitor is registered with. Not continuous tracking. */
    suspend fun startRegionMonitoring(venues: List<VenueSummary>)

    suspend fun stopRegionMonitoring()

    val regionTransitions: Flow<RegionTransition>
}

/** Optional BLE proximity hints. Advertisements can be spoofed: never identity credentials. */
interface BeaconScanner {
    val isSupported: Boolean
    fun scan(venue: VenueSummary): Flow<BeaconObservation>
}

interface Haptics {
    /** Distinct pattern confirming a server receipt, perceivable without looking at the screen. */
    fun confirm()

    /** Light tick used while the hold-to-send ring progresses. */
    fun tick()

    fun warning()
}

/** Small platform facts needed for device context and deep links to system settings. */
interface PlatformInfo {
    val platformName: String
    val osVersion: String
    val appVersion: String
    fun openAppSettings()
    fun openLocationSettings()
}

/** Encrypted key-value store (KVault: Keychain on iOS, EncryptedSharedPreferences on Android). */
interface SecureStore {
    fun getString(key: String): String?
    fun putString(key: String, value: String)
    fun getBoolean(key: String): Boolean?
    fun putBoolean(key: String, value: Boolean)
    fun remove(key: String)
}
