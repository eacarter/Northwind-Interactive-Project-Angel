package org.northwindinteractive.project.core.domain.model

import kotlinx.serialization.Serializable

/** How a venue lets visitors enroll. Configured per venue by the venue admin portal. */
@Serializable
enum class EnrollmentMode {
    /** Open to all visitors who are physically inside the venue geofence. */
    PUBLIC,

    /** Requires a one-time invitation code, redeemed while on site. */
    INVITATION,

    /** Visitor requests access on site and waits for administrator approval. */
    APPROVAL,
}

@Serializable
data class GeoPoint(val latitude: Double, val longitude: Double)

/** Circular venue boundary. Client detection is a hint; the backend is authoritative. */
@Serializable
data class Geofence(
    val center: GeoPoint,
    val radiusMeters: Double,
)

/**
 * Publicly safe directory fields for a participating venue ("VenueSummary" in the spec).
 * Never contains private beacon identities or staff information.
 */
@Serializable
data class VenueSummary(
    val venueId: String,
    val displayName: String,
    val publicAddress: String,
    val tagline: String = "",
    val geofence: Geofence,
    val enrollmentModes: List<EnrollmentMode>,
    /** Grace period after a confirmed exit. Spec default: 300 minutes (5 hours). */
    val graceMinutes: Int = DEFAULT_GRACE_MINUTES,
    /** True if the venue has provisioned BLE beacon zones (optional, premium deployments). */
    val hasBeaconZones: Boolean = false,
    /** Eddystone-UID namespace for this venue's beacons, when [hasBeaconZones] is true. */
    val beaconNamespace: String? = null,
    /** Public display labels for estimated zones, keyed by zone id (e.g. "Second Floor Bar"). */
    val zoneLabels: Map<String, String> = emptyMap(),
) {
    companion object {
        const val DEFAULT_GRACE_MINUTES = 300
    }
}
