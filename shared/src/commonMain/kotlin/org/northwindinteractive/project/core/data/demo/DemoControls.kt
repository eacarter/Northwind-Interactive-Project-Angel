package org.northwindinteractive.project.core.data.demo

import kotlinx.coroutines.flow.StateFlow
import org.northwindinteractive.project.core.domain.model.LocationPermission
import org.northwindinteractive.project.core.domain.model.VenueSummary
import org.northwindinteractive.project.core.platform.SimulatedPresenceService
import kotlin.time.Duration.Companion.minutes

/** QA / demo surface shown in Settings when running without Firebase. */
class DemoControls(
    private val backend: DemoBackend,
    /** Null when the demo backend is paired with real device location. */
    val presence: SimulatedPresenceService?,
) {
    val controls: StateFlow<DemoBackend.Controls> = backend.controls
    val venues: List<VenueSummary> get() = backend.venues
    val inviteCode: String = DemoBackend.DEMO_INVITE_CODE

    fun setScenario(scenario: SimulatedPresenceService.Scenario) {
        presence?.setScenario(scenario)
    }

    fun setPermission(permission: LocationPermission) {
        presence?.setPermission(permission)
    }

    fun setOffline(offline: Boolean) = backend.updateControls { it.copy(offline = offline) }
    fun setAutoApprove(enabled: Boolean) = backend.updateControls { it.copy(autoApprove = enabled) }
    fun setSimulateStaff(enabled: Boolean) = backend.updateControls { it.copy(simulateStaff = enabled) }
    fun setShortGrace(enabled: Boolean) = backend.updateControls { it.copy(graceOverride = if (enabled) 2.minutes else null) }

    suspend fun resolveIncident(incidentId: String) = backend.resolveIncident(incidentId)
}
