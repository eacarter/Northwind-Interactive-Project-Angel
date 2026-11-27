package org.northwindinteractive.project.feature.venue

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.northwindinteractive.project.core.domain.model.ActivationMethod
import org.northwindinteractive.project.core.domain.model.EnrollmentMode
import org.northwindinteractive.project.core.domain.model.LocationPermission
import org.northwindinteractive.project.core.domain.model.toAngelError
import org.northwindinteractive.project.core.domain.repository.PreferencesRepository
import org.northwindinteractive.project.core.domain.session.AlertSubmitter
import org.northwindinteractive.project.core.domain.session.SubmissionState
import org.northwindinteractive.project.core.domain.session.VenueSessionCoordinator
import org.northwindinteractive.project.core.domain.session.VenueSessionState
import org.northwindinteractive.project.core.platform.Haptics
import org.northwindinteractive.project.core.platform.PlatformInfo

data class EnrollmentUiState(
    val selectedMode: EnrollmentMode? = null,
    val inviteCode: String = "",
    val inFlight: Boolean = false,
    val error: String? = null,
)

data class HomeUiState(
    val session: VenueSessionState,
    val submission: SubmissionState,
    val enrollment: EnrollmentUiState,
    val primerDismissed: Boolean,
    val backgroundPromptDismissed: Boolean,
    val hapticsEnabled: Boolean,
)

class HomeViewModel(
    private val coordinator: VenueSessionCoordinator,
    private val alertSubmitter: AlertSubmitter,
    private val preferences: PreferencesRepository,
    private val haptics: Haptics,
    private val platformInfo: PlatformInfo,
) : ViewModel() {

    private val enrollment = MutableStateFlow(EnrollmentUiState())
    private val backgroundPromptDismissed = MutableStateFlow(false)

    val state: StateFlow<HomeUiState> = combine(
        coordinator.state,
        alertSubmitter.state,
        enrollment,
        combine(preferences.hasSeenLocationPrimer, backgroundPromptDismissed, preferences.preferences, ::Triple),
    ) { session, submission, enroll, (primer, bgDismissed, prefs) ->
        HomeUiState(
            session = session,
            submission = submission,
            enrollment = enroll.withDefaultMode(session),
            primerDismissed = primer,
            backgroundPromptDismissed = bgDismissed,
            hapticsEnabled = prefs.hapticConfirmation,
        )
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        HomeUiState(
            session = coordinator.state.value,
            submission = alertSubmitter.state.value,
            enrollment = EnrollmentUiState(),
            primerDismissed = preferences.hasSeenLocationPrimer.value,
            backgroundPromptDismissed = false,
            hapticsEnabled = true,
        ),
    )

    private fun EnrollmentUiState.withDefaultMode(session: VenueSessionState): EnrollmentUiState {
        val modes = session.venue?.enrollmentModes.orEmpty()
        return if (selectedMode == null || selectedMode !in modes) copy(selectedMode = modes.firstOrNull()) else this
    }

    // region Location

    fun requestLocationPermission() {
        preferences.markLocationPrimerSeen()
        viewModelScope.launch { coordinator.requestLocationPermission() }
    }

    fun dismissPrimer() = preferences.markLocationPrimerSeen()

    fun requestBackgroundPermission() {
        backgroundPromptDismissed.value = true
        viewModelScope.launch { coordinator.requestBackgroundPermission() }
    }

    fun dismissBackgroundPrompt() {
        backgroundPromptDismissed.value = true
    }

    fun refresh() = coordinator.refresh()

    fun openSettings(permission: LocationPermission) {
        if (permission == LocationPermission.DENIED_PERMANENTLY || permission == LocationPermission.DENIED) {
            platformInfo.openAppSettings()
        } else {
            platformInfo.openLocationSettings()
        }
    }

    // endregion

    // region Enrollment

    fun selectMode(mode: EnrollmentMode) = enrollment.update { it.copy(selectedMode = mode, error = null) }

    fun onInviteCodeChange(code: String) =
        enrollment.update { it.copy(inviteCode = code.uppercase().take(32), error = null) }

    fun enroll() {
        val current = state.value
        val mode = current.enrollment.selectedMode ?: return
        if (current.enrollment.inFlight) return
        if (mode == EnrollmentMode.INVITATION && current.enrollment.inviteCode.isBlank()) {
            enrollment.update { it.copy(error = "Enter your invitation code") }
            return
        }
        viewModelScope.launch {
            enrollment.update { it.copy(inFlight = true, error = null) }
            coordinator.enroll(mode, current.enrollment.inviteCode)
                .onSuccess { enrollment.value = EnrollmentUiState() }
                .onFailure { e -> enrollment.update { it.copy(inFlight = false, error = e.toAngelError().message) } }
        }
    }

    // endregion

    // region Alert

    fun onHoldTick() {
        if (state.value.hapticsEnabled) haptics.tick()
    }

    fun sendAlert(method: ActivationMethod = ActivationMethod.IN_APP_HOLD) {
        viewModelScope.launch { alertSubmitter.submit(method) }
    }

    fun retryAlert() {
        viewModelScope.launch { alertSubmitter.retry() }
    }

    fun acknowledgeSubmission() = alertSubmitter.reset()

    // endregion
}
