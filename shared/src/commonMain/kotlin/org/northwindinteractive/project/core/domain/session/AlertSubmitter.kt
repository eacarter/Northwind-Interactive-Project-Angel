package org.northwindinteractive.project.core.domain.session

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.northwindinteractive.project.core.domain.model.ActivationMethod
import org.northwindinteractive.project.core.domain.model.AlertRequest
import org.northwindinteractive.project.core.domain.model.AngelError
import org.northwindinteractive.project.core.domain.model.DeviceContext
import org.northwindinteractive.project.core.domain.model.IncidentReceipt
import org.northwindinteractive.project.core.domain.model.toAngelError
import org.northwindinteractive.project.core.domain.policy.DenialReason
import org.northwindinteractive.project.core.domain.policy.Eligibility
import org.northwindinteractive.project.core.domain.policy.EligibilityPolicy
import org.northwindinteractive.project.core.domain.repository.AlertRepository
import org.northwindinteractive.project.core.platform.Haptics
import org.northwindinteractive.project.core.platform.PlatformInfo
import kotlin.time.Clock
import kotlin.uuid.Uuid

/** Client-side submission state (spec: SENDING → RECEIVED BY SERVER → ACKNOWLEDGED). */
sealed interface SubmissionState {
    data object Idle : SubmissionState
    data class Sending(val requestId: String, val method: ActivationMethod) : SubmissionState
    data class Received(val receipt: IncidentReceipt) : SubmissionState

    /** Not delivered. Never claimed as delivered; a retry reuses [requestId]. */
    data class Unsent(val requestId: String, val method: ActivationMethod, val error: AngelError) : SubmissionState

    /** Refused locally before anything was sent (outside venue, stale location, not registered). */
    data class Blocked(val reason: DenialReason) : SubmissionState
}

/**
 * Alert submission sequence (spec steps 13–18):
 * 1. Record a UUID request id. 2. Check local eligibility with fresh evidence; if outside or stale,
 * do not create an alert. 3–5. Call the privileged function and surface the server receipt.
 * 6. Retries reuse the same request id so the server deduplicates.
 */
class AlertSubmitter(
    private val coordinator: VenueSessionCoordinator,
    private val alertRepository: AlertRepository,
    private val platformInfo: PlatformInfo,
    private val haptics: Haptics,
    private val clock: Clock,
) {
    private val mutex = Mutex()
    private val _state = MutableStateFlow<SubmissionState>(SubmissionState.Idle)
    val state: StateFlow<SubmissionState> = _state.asStateFlow()

    /** Starts a new alert. Ignored while another submission is in flight (duplicate taps). */
    suspend fun submit(method: ActivationMethod): SubmissionState {
        if (mutex.isLocked) return _state.value
        return mutex.withLock { send(requestId = Uuid.random().toString(), method = method) }
    }

    /** Retries an unsent alert with the same request id, after revalidating entitlement. */
    suspend fun retry(): SubmissionState {
        val unsent = _state.value as? SubmissionState.Unsent ?: return _state.value
        if (mutex.isLocked) return _state.value
        return mutex.withLock { send(requestId = unsent.requestId, method = unsent.method) }
    }

    fun reset() {
        if (!mutex.isLocked) _state.value = SubmissionState.Idle
    }

    private suspend fun send(requestId: String, method: ActivationMethod): SubmissionState {
        _state.value = SubmissionState.Sending(requestId, method)

        val venueId = coordinator.state.value.registration?.venueId
            ?: return blocked(DenialReason.NOT_REGISTERED)
        val evidence = coordinator.freshEvidence(venueId)
        val snapshot = coordinator.state.value
        val presence = snapshot.presence?.presence ?: return blocked(DenialReason.LOCATION_UNKNOWN)
        val eligibility = EligibilityPolicy.canSubmitAlert(snapshot.registration, presence, clock.now())
        if (eligibility is Eligibility.Denied) return blocked(eligibility.reason)

        val request = AlertRequest(
            requestId = requestId,
            venueId = venueId,
            evidence = evidence,
            activationMethod = method,
            deviceContext = DeviceContext(
                platform = platformInfo.platformName,
                osVersion = platformInfo.osVersion,
                appVersion = platformInfo.appVersion,
            ),
        )
        val next = alertRepository.submitAlert(request).fold(
            onSuccess = { receipt ->
                haptics.confirm()
                SubmissionState.Received(receipt)
            },
            onFailure = { error ->
                haptics.warning()
                when (val angelError = error.toAngelError()) {
                    is AngelError.PresenceRejected -> SubmissionState.Blocked(DenialReason.LOCATION_UNKNOWN)
                    is AngelError.NotEntitled -> SubmissionState.Blocked(DenialReason.NOT_REGISTERED)
                    else -> SubmissionState.Unsent(requestId, method, angelError)
                }
            },
        )
        _state.value = next
        return next
    }

    private fun blocked(reason: DenialReason): SubmissionState {
        haptics.warning()
        return SubmissionState.Blocked(reason).also { _state.value = it }
    }
}
