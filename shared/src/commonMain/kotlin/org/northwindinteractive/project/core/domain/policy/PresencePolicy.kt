package org.northwindinteractive.project.core.domain.policy

import org.northwindinteractive.project.core.domain.model.GeoPoint
import org.northwindinteractive.project.core.domain.model.LocationObservation
import org.northwindinteractive.project.core.domain.model.LocationPermission
import org.northwindinteractive.project.core.domain.model.PresenceState
import org.northwindinteractive.project.core.domain.model.VenuePresence
import org.northwindinteractive.project.core.domain.model.VenueSummary
import org.northwindinteractive.project.core.domain.model.ZoneEstimate
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

/** Tunable thresholds for client-side presence evaluation. The backend applies its own policy. */
data class PresenceConfig(
    /** Observations older than this are treated as STALE_UNKNOWN. */
    val maxObservationAge: Duration = 2.minutes,
    /** Fixes less precise than this cannot confirm inside or outside. */
    val maxAccuracyMeters: Double = 100.0,
    /** Fraction of the reported accuracy that must still fit inside / outside the boundary. */
    val accuracyMarginFactor: Double = 0.5,
)

data class PresenceResult(
    /** The venue the visitor is (or was last estimated to be) inside, if any. */
    val venue: VenueSummary?,
    val presence: VenuePresence,
    val reason: UnknownReason? = null,
)

enum class UnknownReason {
    PERMISSION_MISSING,
    NO_FIX,
    STALE_FIX,
    IMPRECISE_FIX,
    NEAR_BOUNDARY,
}

/**
 * Pure presence rules. Never silently treats a stale INSIDE state as confirmed
 * (spec: "Location unknown" acceptance rule).
 */
object PresencePolicy {

    fun evaluate(
        observation: LocationObservation?,
        venues: List<VenueSummary>,
        permission: LocationPermission,
        now: Instant,
        zone: ZoneEstimate? = null,
        config: PresenceConfig = PresenceConfig(),
    ): PresenceResult {
        if (!permission.isGranted) {
            return unknown(null, observation, UnknownReason.PERMISSION_MISSING)
        }
        if (observation == null) return unknown(null, null, UnknownReason.NO_FIX)

        val nearest = nearestCandidate(observation.point, venues)
        if (now - observation.observedAt > config.maxObservationAge) {
            return unknown(nearest?.venue, observation, UnknownReason.STALE_FIX)
        }
        if (observation.accuracyMeters > config.maxAccuracyMeters) {
            return unknown(nearest?.venue, observation, UnknownReason.IMPRECISE_FIX)
        }

        val margin = observation.accuracyMeters * config.accuracyMarginFactor
        // Overlapping venues: choose the venue whose centre is relatively closest.
        val confirmedInside = venues
            .map { Candidate(it, distanceMeters(observation.point, it.geofence.center)) }
            .filter { it.distance + margin <= it.venue.geofence.radiusMeters }
            .minByOrNull { it.distance / it.venue.geofence.radiusMeters }

        if (confirmedInside != null) {
            return PresenceResult(
                venue = confirmedInside.venue,
                presence = VenuePresence(
                    venueId = confirmedInside.venue.venueId,
                    state = PresenceState.CONFIRMED_INSIDE,
                    observedAt = observation.observedAt,
                    source = observation.source,
                    accuracyMeters = observation.accuracyMeters,
                    zone = zone?.takeIf { now - it.observedAt <= config.maxObservationAge },
                ),
            )
        }

        val ambiguous = venues.firstOrNull { venue ->
            val d = distanceMeters(observation.point, venue.geofence.center)
            d - margin <= venue.geofence.radiusMeters
        }
        if (ambiguous != null) {
            return unknown(ambiguous, observation, UnknownReason.NEAR_BOUNDARY)
        }

        return PresenceResult(
            venue = null,
            presence = VenuePresence(
                venueId = null,
                state = PresenceState.CONFIRMED_OUTSIDE,
                observedAt = observation.observedAt,
                source = observation.source,
                accuracyMeters = observation.accuracyMeters,
            ),
        )
    }

    /** Whether the visitor is confirmed outside a specific venue (used for exit detection). */
    fun isConfirmedOutside(
        observation: LocationObservation,
        venue: VenueSummary,
        now: Instant,
        config: PresenceConfig = PresenceConfig(),
    ): Boolean {
        if (now - observation.observedAt > config.maxObservationAge) return false
        if (observation.accuracyMeters > config.maxAccuracyMeters) return false
        val d = distanceMeters(observation.point, venue.geofence.center)
        return d - observation.accuracyMeters * config.accuracyMarginFactor > venue.geofence.radiusMeters
    }

    private data class Candidate(val venue: VenueSummary, val distance: Double)

    private fun nearestCandidate(point: GeoPoint, venues: List<VenueSummary>): Candidate? = venues
        .map { Candidate(it, distanceMeters(point, it.geofence.center)) }
        .filter { it.distance <= it.venue.geofence.radiusMeters * 2 }
        .minByOrNull { it.distance / it.venue.geofence.radiusMeters }

    private fun unknown(
        venue: VenueSummary?,
        observation: LocationObservation?,
        reason: UnknownReason,
    ) = PresenceResult(
        venue = venue,
        presence = VenuePresence(
            venueId = venue?.venueId,
            state = PresenceState.STALE_UNKNOWN,
            observedAt = observation?.observedAt,
            source = observation?.source,
            accuracyMeters = observation?.accuracyMeters,
        ),
        reason = reason,
    )

    /** Great-circle distance (haversine) in meters. */
    fun distanceMeters(a: GeoPoint, b: GeoPoint): Double {
        val earthRadius = 6_371_000.0
        val lat1 = a.latitude.toRadians()
        val lat2 = b.latitude.toRadians()
        val dLat = (b.latitude - a.latitude).toRadians()
        val dLng = (b.longitude - a.longitude).toRadians()
        val h = sin(dLat / 2) * sin(dLat / 2) + cos(lat1) * cos(lat2) * sin(dLng / 2) * sin(dLng / 2)
        return 2 * earthRadius * asin(min(1.0, sqrt(h)))
    }

    private fun Double.toRadians() = this * kotlin.math.PI / 180.0
}
