package org.northwindinteractive.project.core.domain.model

import kotlinx.serialization.Serializable
import kotlin.time.Instant

@Serializable
enum class ActivationMethod {
    /** In-app press-and-hold on the main alert control. */
    IN_APP_HOLD,

    /** iOS App Intent / Shortcuts (including user-configured Back Tap). */
    APP_INTENT,

    /** Android launcher shortcut. */
    APP_SHORTCUT,

    /** Android Quick Settings tile. */
    QUICK_SETTINGS_TILE,
}

/** Canonical server-side incident status. Staff transitions come from the Flutter console. */
@Serializable
enum class IncidentStatus {
    /** Persisted by the backend. Distinct from staff acknowledgement. */
    RECEIVED,

    /** A security responder acknowledged the alert. */
    ACKNOWLEDGED,

    /** Security team is on the way / actively responding. */
    RESPONDING,

    /** Incident closed by venue security. */
    RESOLVED,
}

/** Privileged submitAlert request. Never written to Firestore directly by the client. */
@Serializable
data class AlertRequest(
    /** UUID idempotency key. Retrying with the same id returns the same incident. */
    val requestId: String,
    val venueId: String,
    val evidence: PresenceEvidence,
    val activationMethod: ActivationMethod,
    val deviceContext: DeviceContext,
)

@Serializable
data class DeviceContext(
    val platform: String,
    val osVersion: String,
    val appVersion: String,
)

/** Server-issued receipt, distinct from staff acknowledgement. */
@Serializable
data class IncidentReceipt(
    val incidentId: String,
    val requestId: String,
    val receivedAt: Instant,
    val status: IncidentStatus,
)

@Serializable
enum class AlertEventType {
    SUBMITTED,
    RECEIVED,
    ACKNOWLEDGED,
    RESPONDING,
    RESOLVED,
    VISITOR_EXITED,
    REGISTRATION_EXPIRED,
}

/** Append-only audit/timeline entry, authored by the backend. */
@Serializable
data class AlertEvent(
    val incidentId: String,
    val actor: String,
    val eventType: AlertEventType,
    val serverTime: Instant,
)

/** The visitor's read-only view of an incident they own (spec: getMyIncident). */
@Serializable
data class Incident(
    val incidentId: String,
    val requestId: String,
    val venueId: String,
    val venueName: String,
    val status: IncidentStatus,
    val createdAt: Instant,
    val timeline: List<AlertEvent>,
    /** Last confirmed in-venue zone, as an estimate. Never a live position after exit. */
    val lastKnownZone: ZoneEstimate? = null,
    val lastKnownAt: Instant? = null,
    /** True once the visitor's exit has been recorded; location is then historical only. */
    val visitorExited: Boolean = false,
) {
    val isOpen: Boolean get() = status != IncidentStatus.RESOLVED

    fun eventAt(type: AlertEventType): AlertEvent? = timeline.lastOrNull { it.eventType == type }
}
