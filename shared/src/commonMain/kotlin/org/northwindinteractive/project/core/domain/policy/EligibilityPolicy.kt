package org.northwindinteractive.project.core.domain.policy

import org.northwindinteractive.project.core.domain.model.PresenceState
import org.northwindinteractive.project.core.domain.model.RegistrationStatus
import org.northwindinteractive.project.core.domain.model.VenuePresence
import org.northwindinteractive.project.core.domain.model.VenueRegistration
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.hours
import kotlin.time.Instant

sealed interface Eligibility {
    data object Allowed : Eligibility
    data class Denied(val reason: DenialReason) : Eligibility
}

enum class DenialReason {
    NOT_REGISTERED,
    PENDING_APPROVAL,
    OUTSIDE_VENUE,
    IN_GRACE_PERIOD,
    REGISTRATION_EXPIRED,
    REJECTED,
    SUSPENDED_OR_REVOKED,
    LOCATION_UNKNOWN,
    LOCATION_STALE,
    REENTRY_VERIFYING,
    DIFFERENT_VENUE,
    SESSION_LIMIT_REACHED,
}

/**
 * Local eligibility gate. It only decides whether the client should *offer* an action; the backend
 * re-validates everything. Grace period never grants alert eligibility (spec: product boundary).
 */
object EligibilityPolicy {

    /** Fresh-presence window required at the moment an alert is sent. */
    val ALERT_PRESENCE_FRESHNESS: Duration = 2.minutes

    /** Safety net for missed exits (spec: maximum staleness / session validity safeguard). */
    val MAX_SESSION_VALIDITY: Duration = 12.hours

    fun canSubmitAlert(
        registration: VenueRegistration?,
        presence: VenuePresence,
        now: Instant,
    ): Eligibility {
        val statusDenial = registrationDenial(registration)
        if (statusDenial != null) return Eligibility.Denied(statusDenial)
        registration!!

        val validUntil = registration.maxValidUntil
            ?: registration.registeredAt?.plus(MAX_SESSION_VALIDITY)
        if (validUntil != null && now > validUntil) {
            return Eligibility.Denied(DenialReason.SESSION_LIMIT_REACHED)
        }
        return presenceDenial(presence, registration.venueId, now)
            ?.let { Eligibility.Denied(it) }
            ?: Eligibility.Allowed
    }

    /** Joining, redeeming an invite, or requesting approval all require confirmed on-site presence. */
    fun canEnroll(presence: VenuePresence, venueId: String, now: Instant): Eligibility =
        presenceDenial(presence, venueId, now)?.let { Eligibility.Denied(it) } ?: Eligibility.Allowed

    private fun registrationDenial(registration: VenueRegistration?): DenialReason? =
        when (registration?.status) {
            null, RegistrationStatus.NOT_REGISTERED -> DenialReason.NOT_REGISTERED
            RegistrationStatus.PENDING_APPROVAL -> DenialReason.PENDING_APPROVAL
            RegistrationStatus.ACTIVE_INSIDE -> null
            RegistrationStatus.GRACE_OUTSIDE -> DenialReason.IN_GRACE_PERIOD
            RegistrationStatus.EXPIRED -> DenialReason.REGISTRATION_EXPIRED
            RegistrationStatus.REJECTED -> DenialReason.REJECTED
            RegistrationStatus.SUSPENDED, RegistrationStatus.REVOKED -> DenialReason.SUSPENDED_OR_REVOKED
        }

    private fun presenceDenial(presence: VenuePresence, venueId: String, now: Instant): DenialReason? =
        when (presence.state) {
            PresenceState.CONFIRMED_OUTSIDE -> DenialReason.OUTSIDE_VENUE
            PresenceState.STALE_UNKNOWN -> DenialReason.LOCATION_UNKNOWN
            PresenceState.REENTRY_PENDING -> DenialReason.REENTRY_VERIFYING
            PresenceState.CONFIRMED_INSIDE -> when {
                presence.venueId != venueId -> DenialReason.DIFFERENT_VENUE
                presence.observedAt == null -> DenialReason.LOCATION_UNKNOWN
                now - presence.observedAt > ALERT_PRESENCE_FRESHNESS -> DenialReason.LOCATION_STALE
                else -> null
            }
        }
}

/** Time and expiration rules. Server timestamps are authoritative; this is for display. */
object GracePolicy {
    fun expiresAt(exitedAt: Instant, graceMinutes: Int): Instant = exitedAt + graceMinutes.minutes

    fun remaining(registration: VenueRegistration, now: Instant): Duration? {
        val expires = registration.expiresAt ?: return null
        return (expires - now).coerceAtLeast(Duration.ZERO)
    }

    fun isExpired(registration: VenueRegistration, now: Instant): Boolean {
        val expires = registration.expiresAt ?: return false
        return registration.status == RegistrationStatus.GRACE_OUTSIDE && now >= expires
    }
}
