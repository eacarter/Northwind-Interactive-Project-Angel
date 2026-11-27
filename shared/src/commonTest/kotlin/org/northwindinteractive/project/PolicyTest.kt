package org.northwindinteractive.project

import org.northwindinteractive.project.core.domain.model.EnrollmentMode
import org.northwindinteractive.project.core.domain.model.GeoPoint
import org.northwindinteractive.project.core.domain.model.Geofence
import org.northwindinteractive.project.core.domain.model.LocationObservation
import org.northwindinteractive.project.core.domain.model.LocationPermission
import org.northwindinteractive.project.core.domain.model.LocationSource
import org.northwindinteractive.project.core.domain.model.PresenceState
import org.northwindinteractive.project.core.domain.model.RegistrationStatus
import org.northwindinteractive.project.core.domain.model.VenuePresence
import org.northwindinteractive.project.core.domain.model.VenueRegistration
import org.northwindinteractive.project.core.domain.model.VenueSummary
import org.northwindinteractive.project.core.domain.policy.DenialReason
import org.northwindinteractive.project.core.domain.policy.Eligibility
import org.northwindinteractive.project.core.domain.policy.EligibilityPolicy
import org.northwindinteractive.project.core.domain.policy.GracePolicy
import org.northwindinteractive.project.core.domain.policy.PresencePolicy
import org.northwindinteractive.project.core.domain.policy.UnknownReason
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

private val NOW = Instant.fromEpochMilliseconds(1_800_000_000_000)

private val VENUE = VenueSummary(
    venueId = "v1",
    displayName = "Venue One",
    publicAddress = "1 Test St",
    geofence = Geofence(GeoPoint(-37.8126, 144.9617), radiusMeters = 60.0),
    enrollmentModes = listOf(EnrollmentMode.PUBLIC),
)

private val NEIGHBOUR = VENUE.copy(
    venueId = "v2",
    displayName = "Neighbour",
    geofence = Geofence(GeoPoint(-37.8130, 144.9617), radiusMeters = 60.0),
)

private fun fix(lat: Double, lng: Double, accuracy: Double = 10.0, age: kotlin.time.Duration = 0.seconds) =
    LocationObservation(GeoPoint(lat, lng), accuracy, NOW - age, LocationSource.GPS)

private fun metersNorth(m: Double) = VENUE.geofence.center.latitude + m / 111_320.0

class PresencePolicyTest {

    @Test
    fun insideWithFreshPreciseFixIsConfirmed() {
        val result = PresencePolicy.evaluate(fix(metersNorth(10.0), 144.9617), listOf(VENUE), LocationPermission.GRANTED_FOREGROUND, NOW)
        assertEquals(PresenceState.CONFIRMED_INSIDE, result.presence.state)
        assertEquals("v1", result.venue?.venueId)
    }

    @Test
    fun staleInsideFixIsNeverTreatedAsConfirmed() {
        val result = PresencePolicy.evaluate(
            fix(metersNorth(5.0), 144.9617, age = 5.minutes),
            listOf(VENUE),
            LocationPermission.GRANTED_FOREGROUND,
            NOW,
        )
        assertEquals(PresenceState.STALE_UNKNOWN, result.presence.state)
        assertEquals(UnknownReason.STALE_FIX, result.reason)
    }

    @Test
    fun impreciseFixCannotConfirmPresence() {
        val result = PresencePolicy.evaluate(fix(metersNorth(0.0), 144.9617, accuracy = 400.0), listOf(VENUE), LocationPermission.GRANTED_FOREGROUND, NOW)
        assertEquals(PresenceState.STALE_UNKNOWN, result.presence.state)
        assertEquals(UnknownReason.IMPRECISE_FIX, result.reason)
    }

    @Test
    fun boundaryFixIsAmbiguous() {
        // 55 m from centre with 30 m accuracy straddles the 60 m boundary.
        val result = PresencePolicy.evaluate(fix(metersNorth(55.0), 144.9617, accuracy = 30.0), listOf(VENUE), LocationPermission.GRANTED_FOREGROUND, NOW)
        assertEquals(PresenceState.STALE_UNKNOWN, result.presence.state)
        assertEquals(UnknownReason.NEAR_BOUNDARY, result.reason)
    }

    @Test
    fun farAwayIsConfirmedOutside() {
        val result = PresencePolicy.evaluate(fix(metersNorth(800.0), 144.9617), listOf(VENUE), LocationPermission.GRANTED_FOREGROUND, NOW)
        assertEquals(PresenceState.CONFIRMED_OUTSIDE, result.presence.state)
    }

    @Test
    fun missingPermissionIsUnknown() {
        val result = PresencePolicy.evaluate(fix(metersNorth(0.0), 144.9617), listOf(VENUE), LocationPermission.DENIED, NOW)
        assertEquals(PresenceState.STALE_UNKNOWN, result.presence.state)
        assertEquals(UnknownReason.PERMISSION_MISSING, result.reason)
    }

    @Test
    fun overlappingVenuesPickRelativelyClosestCentre() {
        // NEIGHBOUR's centre is ~44 m south of VENUE's; a point 30 m south is inside both.
        val result = PresencePolicy.evaluate(fix(metersNorth(-30.0), 144.9617, accuracy = 5.0), listOf(VENUE, NEIGHBOUR), LocationPermission.GRANTED_FOREGROUND, NOW)
        assertEquals(PresenceState.CONFIRMED_INSIDE, result.presence.state)
        assertEquals("v2", result.venue?.venueId)
    }
}

class EligibilityPolicyTest {
    private val inside = VenuePresence("v1", PresenceState.CONFIRMED_INSIDE, NOW, LocationSource.GPS, 10.0)
    private fun registration(status: RegistrationStatus) = VenueRegistration(
        id = "r", venueId = "v1", uid = "u", mode = EnrollmentMode.PUBLIC, status = status, registeredAt = NOW - 1.hours,
    )

    @Test
    fun activeAndInsideIsAllowed() {
        assertEquals(Eligibility.Allowed, EligibilityPolicy.canSubmitAlert(registration(RegistrationStatus.ACTIVE_INSIDE), inside, NOW))
    }

    @Test
    fun gracePeriodNeverGrantsAlerts() {
        // Product boundary: registration grace is not alert eligibility, even if a stale inside reading exists.
        val result = EligibilityPolicy.canSubmitAlert(registration(RegistrationStatus.GRACE_OUTSIDE), inside, NOW)
        assertEquals(Eligibility.Denied(DenialReason.IN_GRACE_PERIOD), result)
    }

    @Test
    fun pendingApprovalCannotAlert() {
        assertEquals(
            Eligibility.Denied(DenialReason.PENDING_APPROVAL),
            EligibilityPolicy.canSubmitAlert(registration(RegistrationStatus.PENDING_APPROVAL), inside, NOW),
        )
    }

    @Test
    fun outsidePresenceBlocksActiveRegistration() {
        val outside = inside.copy(state = PresenceState.CONFIRMED_OUTSIDE)
        assertEquals(
            Eligibility.Denied(DenialReason.OUTSIDE_VENUE),
            EligibilityPolicy.canSubmitAlert(registration(RegistrationStatus.ACTIVE_INSIDE), outside, NOW),
        )
    }

    @Test
    fun stalePresenceBlocksAlert() {
        val stale = inside.copy(observedAt = NOW - 10.minutes)
        assertEquals(
            Eligibility.Denied(DenialReason.LOCATION_STALE),
            EligibilityPolicy.canSubmitAlert(registration(RegistrationStatus.ACTIVE_INSIDE), stale, NOW),
        )
    }

    @Test
    fun missedExitSafeguardExpiresSession() {
        val old = registration(RegistrationStatus.ACTIVE_INSIDE).copy(registeredAt = NOW - 13.hours)
        assertEquals(Eligibility.Denied(DenialReason.SESSION_LIMIT_REACHED), EligibilityPolicy.canSubmitAlert(old, inside, NOW))
    }

    @Test
    fun suspendedOrRevokedNeverPermitAlert() {
        listOf(RegistrationStatus.SUSPENDED, RegistrationStatus.REVOKED).forEach { status ->
            assertEquals(
                Eligibility.Denied(DenialReason.SUSPENDED_OR_REVOKED),
                EligibilityPolicy.canSubmitAlert(registration(status), inside, NOW),
            )
        }
    }

    @Test
    fun graceDeadlineUsesExitPlusConfiguredMinutes() {
        val exitedAt = NOW
        assertEquals(NOW + 300.minutes, GracePolicy.expiresAt(exitedAt, VenueSummary.DEFAULT_GRACE_MINUTES))
        val grace = registration(RegistrationStatus.GRACE_OUTSIDE).copy(exitedAt = exitedAt, expiresAt = NOW + 300.minutes)
        assertTrue(!GracePolicy.isExpired(grace, NOW + 299.minutes))
        assertTrue(GracePolicy.isExpired(grace, NOW + 300.minutes))
    }
}
