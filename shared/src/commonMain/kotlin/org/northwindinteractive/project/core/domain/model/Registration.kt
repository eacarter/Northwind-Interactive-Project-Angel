package org.northwindinteractive.project.core.domain.model

import kotlinx.serialization.Serializable
import kotlin.time.Instant

/** Venue registration states (spec: "Venue registration states"). */
@Serializable
enum class RegistrationStatus {
    /** No active visit registration. Inside venue: show join options. */
    NOT_REGISTERED,

    /** Approval required and review outstanding. No alerts until approved and on site. */
    PENDING_APPROVAL,

    /** Registered and current presence accepted. Enables the alert control. */
    ACTIVE_INSIDE,

    /** Exit recorded; expiration scheduled. Venue features disabled; reentry recovery allowed. */
    GRACE_OUTSIDE,

    /** Grace deadline or maximum validity reached. New registration required upon return. */
    EXPIRED,

    /** An administrator declined the access request. */
    REJECTED,

    /** Venue or platform authority has temporarily disabled eligibility. */
    SUSPENDED,

    /** Venue or platform authority has removed eligibility. */
    REVOKED,
}

/**
 * The visitor's current visit session for one venue. Created and expired by the backend only;
 * the client never writes this record directly.
 */
@Serializable
data class VenueRegistration(
    val id: String,
    val venueId: String,
    val uid: String,
    val mode: EnrollmentMode,
    val status: RegistrationStatus,
    val registeredAt: Instant? = null,
    val exitedAt: Instant? = null,
    val expiresAt: Instant? = null,
    /** Hard upper bound on session validity so a missed exit cannot keep access forever. */
    val maxValidUntil: Instant? = null,
    /** Monotonic version used by the backend for conditional updates. */
    val version: Long = 0,
) {
    val isTerminal: Boolean
        get() = status == RegistrationStatus.EXPIRED ||
            status == RegistrationStatus.REJECTED ||
            status == RegistrationStatus.REVOKED
}
