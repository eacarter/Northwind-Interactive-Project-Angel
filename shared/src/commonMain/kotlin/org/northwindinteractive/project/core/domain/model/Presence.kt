package org.northwindinteractive.project.core.domain.model

import kotlinx.serialization.Serializable
import kotlin.time.Instant

/** Presence states (spec: "Presence states and rules"). */
@Serializable
enum class PresenceState {
    /** Fresh acceptable location evidence inside the venue. Alerts allowed if registration active. */
    CONFIRMED_INSIDE,

    /** Exit, or fresh outside evidence accepted. No alerts. */
    CONFIRMED_OUTSIDE,

    /** Evidence absent, too old, too imprecise, permission revoked or sensor unavailable. */
    STALE_UNKNOWN,

    /** Inside transition received but backend verification still pending. */
    REENTRY_PENDING,
}

@Serializable
enum class LocationSource { GPS, NETWORK, FUSED, REGION_MONITOR, SIMULATED }

/** A single raw location fix reported by the platform adapter. */
@Serializable
data class LocationObservation(
    val point: GeoPoint,
    val accuracyMeters: Double,
    val observedAt: Instant,
    val source: LocationSource,
)

/** Optional BLE proximity hint. RSSI is never converted into exact distances. */
@Serializable
data class BeaconObservation(
    val beaconId: String,
    val zoneId: String?,
    val rssi: Int,
    val observedAt: Instant,
)

/** Estimated indoor zone (e.g. "Second Floor Bar"). Always presented as an estimate. */
@Serializable
data class ZoneEstimate(
    val zoneId: String,
    val label: String,
    val observedAt: Instant,
)

/** Client-side presence evaluation for one venue. Untrusted evidence; checked again server side. */
@Serializable
data class VenuePresence(
    val venueId: String?,
    val state: PresenceState,
    val observedAt: Instant?,
    val source: LocationSource?,
    val accuracyMeters: Double?,
    val zone: ZoneEstimate? = null,
)

/**
 * Evidence bundle sent to privileged functions. Contains the minimum needed for the backend to
 * evaluate a bounded-freshness observation set.
 */
@Serializable
data class PresenceEvidence(
    val venueId: String,
    val location: LocationObservation?,
    val beacons: List<BeaconObservation> = emptyList(),
    val clientState: PresenceState,
    val capturedAt: Instant,
)

enum class LocationPermission {
    NOT_DETERMINED,
    DENIED,
    /** Permanently denied / restricted by policy; must be changed in system settings. */
    DENIED_PERMANENTLY,
    GRANTED_FOREGROUND,
    GRANTED_BACKGROUND;

    val isGranted: Boolean get() = this == GRANTED_FOREGROUND || this == GRANTED_BACKGROUND
}

/** Region monitoring callback from the OS (geofence enter / exit). */
data class RegionTransition(
    val venueId: String,
    val type: Type,
    val observedAt: Instant,
) {
    enum class Type { ENTER, EXIT }
}
