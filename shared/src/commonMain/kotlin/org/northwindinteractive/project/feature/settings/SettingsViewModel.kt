package org.northwindinteractive.project.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.northwindinteractive.project.core.AppConfig
import org.northwindinteractive.project.core.BackendMode
import org.northwindinteractive.project.core.data.demo.DemoBackend
import org.northwindinteractive.project.core.data.demo.DemoControls
import org.northwindinteractive.project.core.domain.model.LocationPermission
import org.northwindinteractive.project.core.domain.model.ThemeMode
import org.northwindinteractive.project.core.domain.model.UserPreferences
import org.northwindinteractive.project.core.domain.model.UserProfile
import org.northwindinteractive.project.core.domain.model.VenueRegistration
import org.northwindinteractive.project.core.domain.repository.AuthRepository
import org.northwindinteractive.project.core.domain.repository.PreferencesRepository
import org.northwindinteractive.project.core.domain.repository.ProfileRepository
import org.northwindinteractive.project.core.domain.session.VenueSessionCoordinator
import org.northwindinteractive.project.core.domain.session.VenueSessionState
import org.northwindinteractive.project.core.platform.PlatformInfo
import org.northwindinteractive.project.core.platform.SimulatedPresenceService

data class SettingsUiState(
    val profile: UserProfile?,
    val preferences: UserPreferences,
    val permission: LocationPermission,
    val registrations: List<Pair<String, VenueRegistration>>,
    val session: VenueSessionState,
    val demo: DemoBackend.Controls?,
    val demoScenario: SimulatedPresenceService.Scenario?,
)

class SettingsViewModel(
    private val authRepository: AuthRepository,
    profileRepository: ProfileRepository,
    private val preferencesRepository: PreferencesRepository,
    private val coordinator: VenueSessionCoordinator,
    private val platformInfo: PlatformInfo,
    private val config: AppConfig,
    val demoControls: DemoControls?,
) : ViewModel() {

    val isDemo: Boolean get() = config.backend == BackendMode.DEMO
    val platformName: String get() = platformInfo.platformName
    val appVersion: String get() = platformInfo.appVersion

    val state: StateFlow<SettingsUiState> = combine(
        profileRepository.observeProfile(),
        preferencesRepository.preferences,
        coordinator.state,
        demoControls?.controls ?: flowOf(null),
        demoControls?.presence?.scenario ?: flowOf(null),
    ) { profile, prefs, session, demo, scenario ->
        SettingsUiState(
            profile = profile,
            preferences = prefs,
            permission = session.permission,
            registrations = session.registrations.values
                .sortedByDescending { it.registeredAt }
                .map { (session.venues[it.venueId]?.displayName ?: it.venueId) to it },
            session = session,
            demo = demo,
            demoScenario = scenario,
        )
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        SettingsUiState(null, preferencesRepository.preferences.value, coordinator.state.value.permission, emptyList(), coordinator.state.value, null, null),
    )

    fun setTheme(mode: ThemeMode) = preferencesRepository.update { it.copy(themeMode = mode) }
    fun setReducedMotion(enabled: Boolean) = preferencesRepository.update { it.copy(reducedMotion = enabled) }
    fun setHaptics(enabled: Boolean) = preferencesRepository.update { it.copy(hapticConfirmation = enabled) }

    fun requestLocation() {
        viewModelScope.launch {
            if (state.value.permission == LocationPermission.NOT_DETERMINED) {
                preferencesRepository.markLocationPrimerSeen()
                coordinator.requestLocationPermission()
            } else {
                platformInfo.openAppSettings()
            }
        }
    }

    fun requestBackgroundLocation() {
        viewModelScope.launch { coordinator.requestBackgroundPermission() }
    }

    fun openAppSettings() = platformInfo.openAppSettings()

    fun signOut() {
        viewModelScope.launch { authRepository.signOut() }
    }

    fun resolveOpenIncident() {
        val incident = state.value.session.openIncident ?: return
        viewModelScope.launch { demoControls?.resolveIncident(incident.incidentId) }
    }
}
