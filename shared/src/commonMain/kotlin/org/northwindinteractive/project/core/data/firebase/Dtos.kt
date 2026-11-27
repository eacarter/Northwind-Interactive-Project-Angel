package org.northwindinteractive.project.core.data.firebase

import kotlinx.serialization.Serializable
import org.northwindinteractive.project.core.domain.model.AlertEvent
import org.northwindinteractive.project.core.domain.model.AlertEventType
import org.northwindinteractive.project.core.domain.model.AlertRequest
import org.northwindinteractive.project.core.domain.model.BeaconObservation
import org.northwindinteractive.project.core.domain.model.EnrollmentMode
import org.northwindinteractive.project.core.domain.model.GeoPoint
import org.northwindinteractive.project.core.domain.model.Geofence
import org.northwindinteractive.project.core.domain.model.Incident
import org.northwindinteractive.project.core.domain.model.IncidentReceipt
import org.northwindinteractive.project.core.domain.model.IncidentStatus
import org.northwindinteractive.project.core.domain.model.LocationObservation
import org.northwindinteractive.project.core.domain.model.PresenceEvidence
import org.northwindinteractive.project.core.domain.model.RegistrationStatus
import org.northwindinteractive.project.core.domain.model.VenueRegistration
import org.northwindinteractive.project.core.domain.model.VenueSummary
import org.northwindinteractive.project.core.domain.model.ZoneEstimate
import kotlin.time.Instant

/*
 * Wire format shared with the Cloud Functions in /firebase/functions.
 * Timestamps travel as epoch milliseconds (server-issued wherever they matter) so both platforms
 * decode them identically. Enum values are their Kotlin names.
 */

private fun Long?.toInstant(): Instant? = this?.let(Instant::fromEpochMilliseconds)

@Serializable
data class VenueDto(
    val venueId: String,
    val displayName: String,
    val publicAddress: String,
    val tagline: String = "",
    val latitude: Double,
    val longitude: Double,
    val radiusMeters: Double,
    val enrollmentModes: List<String>,
    val graceMinutes: Int = VenueSummary.DEFAULT_GRACE_MINUTES,
    val hasBeaconZones: Boolean = false,
    val beaconNamespace: String? = null,
    val zoneLabels: Map<String, String> = emptyMap(),
) {
    fun toDomain() = VenueSummary(
        venueId = venueId,
        displayName = displayName,
        publicAddress = publicAddress,
        tagline = tagline,
        geofence = Geofence(GeoPoint(latitude, longitude), radiusMeters),
        enrollmentModes = enrollmentModes.mapNotNull { runCatching { EnrollmentMode.valueOf(it) }.getOrNull() },
        graceMinutes = graceMinutes,
        hasBeaconZones = hasBeaconZones,
        beaconNamespace = beaconNamespace,
        zoneLabels = zoneLabels,
    )
}

@Serializable
data class VenueListDto(val venues: List<VenueDto>)

@Serializable
data class RegistrationDto(
    val id: String,
    val venueId: String,
    val uid: String,
    val mode: String,
    val status: String,
    val registeredAtMs: Long? = null,
    val exitedAtMs: Long? = null,
    val expiresAtMs: Long? = null,
    val maxValidUntilMs: Long? = null,
    val version: Long = 0,
) {
    fun toDomain() = VenueRegistration(
        id = id,
        venueId = venueId,
        uid = uid,
        mode = runCatching { EnrollmentMode.valueOf(mode) }.getOrDefault(EnrollmentMode.PUBLIC),
        status = runCatching { RegistrationStatus.valueOf(status) }.getOrDefault(RegistrationStatus.NOT_REGISTERED),
        registeredAt = registeredAtMs.toInstant(),
        exitedAt = exitedAtMs.toInstant(),
        expiresAt = expiresAtMs.toInstant(),
        maxValidUntil = maxValidUntilMs.toInstant(),
        version = version,
    )
}

@Serializable
data class LocationDto(
    val latitude: Double,
    val longitude: Double,
    val accuracyMeters: Double,
    val observedAtMs: Long,
    val source: String,
)

@Serializable
data class BeaconDto(val beaconId: String, val zoneId: String?, val rssi: Int, val observedAtMs: Long)

@Serializable
data class EvidenceDto(
    val venueId: String,
    val location: LocationDto?,
    val beacons: List<BeaconDto>,
    val clientState: String,
    val capturedAtMs: Long,
)

fun LocationObservation.toDto() = LocationDto(
    latitude = point.latitude,
    longitude = point.longitude,
    accuracyMeters = accuracyMeters,
    observedAtMs = observedAt.toEpochMilliseconds(),
    source = source.name,
)

fun BeaconObservation.toDto() = BeaconDto(beaconId, zoneId, rssi, observedAt.toEpochMilliseconds())

fun PresenceEvidence.toDto() = EvidenceDto(
    venueId = venueId,
    location = location?.toDto(),
    beacons = beacons.map { it.toDto() },
    clientState = clientState.name,
    capturedAtMs = capturedAt.toEpochMilliseconds(),
)

@Serializable
data class GetEligibleVenuesRequest(val latitude: Double? = null, val longitude: Double? = null)

@Serializable
data class VenueEvidenceRequest(val venueId: String, val evidence: EvidenceDto)

@Serializable
data class RedeemInviteRequest(val venueId: String, val token: String, val evidence: EvidenceDto)

@Serializable
data class SubmitAlertRequestDto(
    val requestId: String,
    val venueId: String,
    val evidence: EvidenceDto,
    val activationMethod: String,
    val platform: String,
    val osVersion: String,
    val appVersion: String,
)

fun AlertRequest.toDto() = SubmitAlertRequestDto(
    requestId = requestId,
    venueId = venueId,
    evidence = evidence.toDto(),
    activationMethod = activationMethod.name,
    platform = deviceContext.platform,
    osVersion = deviceContext.osVersion,
    appVersion = deviceContext.appVersion,
)

@Serializable
data class ReceiptDto(val incidentId: String, val requestId: String, val receivedAtMs: Long, val status: String) {
    fun toDomain() = IncidentReceipt(
        incidentId = incidentId,
        requestId = requestId,
        receivedAt = Instant.fromEpochMilliseconds(receivedAtMs),
        status = runCatching { IncidentStatus.valueOf(status) }.getOrDefault(IncidentStatus.RECEIVED),
    )
}

@Serializable
data class IncidentIdRequest(val incidentId: String)

@Serializable
data class TimelineEventDto(val actor: String, val eventType: String, val serverTimeMs: Long)

@Serializable
data class ZoneDto(val zoneId: String, val label: String, val observedAtMs: Long)

@Serializable
data class IncidentDto(
    val incidentId: String,
    val requestId: String,
    val venueId: String,
    val venueName: String,
    val status: String,
    val createdAtMs: Long,
    val timeline: List<TimelineEventDto> = emptyList(),
    val lastKnownZone: ZoneDto? = null,
    val lastKnownAtMs: Long? = null,
    val visitorExited: Boolean = false,
) {
    fun toDomain() = Incident(
        incidentId = incidentId,
        requestId = requestId,
        venueId = venueId,
        venueName = venueName,
        status = runCatching { IncidentStatus.valueOf(status) }.getOrDefault(IncidentStatus.RECEIVED),
        createdAt = Instant.fromEpochMilliseconds(createdAtMs),
        timeline = timeline.mapNotNull { event ->
            runCatching { AlertEventType.valueOf(event.eventType) }.getOrNull()?.let { type ->
                AlertEvent(incidentId, event.actor, type, Instant.fromEpochMilliseconds(event.serverTimeMs))
            }
        }.sortedBy { it.serverTime },
        lastKnownZone = lastKnownZone?.let { ZoneEstimate(it.zoneId, it.label, Instant.fromEpochMilliseconds(it.observedAtMs)) },
        lastKnownAt = lastKnownAtMs.toInstant(),
        visitorExited = visitorExited,
    )
}

@Serializable
data class UserProfileDto(
    val uid: String,
    val email: String? = null,
    val displayAlias: String? = null,
    val createdAtMs: Long? = null,
)
