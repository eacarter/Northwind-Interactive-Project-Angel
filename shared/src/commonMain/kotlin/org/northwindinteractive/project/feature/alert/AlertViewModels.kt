package org.northwindinteractive.project.feature.alert

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import org.northwindinteractive.project.core.domain.model.Incident
import org.northwindinteractive.project.core.domain.repository.AlertRepository
import org.northwindinteractive.project.core.domain.session.VenueSessionCoordinator
import kotlin.time.Clock
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

sealed interface IncidentUiState {
    data object Loading : IncidentUiState
    data object NotFound : IncidentUiState
    data class Loaded(
        val incident: Incident,
        /** True when the visitor is no longer confirmed inside that venue: location is historical. */
        val visitorAway: Boolean,
        val now: Instant,
    ) : IncidentUiState
}

class AlertDetailsViewModel(
    incidentId: String,
    alertRepository: AlertRepository,
    coordinator: VenueSessionCoordinator,
    clock: Clock,
) : ViewModel() {

    private val ticker = flow {
        while (true) {
            emit(clock.now())
            delay(15.seconds)
        }
    }

    /** Realtime listener: status moves RECEIVED → ACKNOWLEDGED → RESPONDING as staff act. */
    val state: StateFlow<IncidentUiState> = combine(
        alertRepository.observeIncident(incidentId).catch { emit(null) },
        coordinator.state,
        ticker,
    ) { incident, session, now ->
        if (incident == null) {
            IncidentUiState.NotFound
        } else {
            val inside = session.presence?.presence?.let {
                it.venueId == incident.venueId &&
                    it.state == org.northwindinteractive.project.core.domain.model.PresenceState.CONFIRMED_INSIDE
            } ?: false
            IncidentUiState.Loaded(incident, visitorAway = incident.visitorExited || !inside, now = now)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), IncidentUiState.Loading)
}

class AlertHistoryViewModel(
    coordinator: VenueSessionCoordinator,
) : ViewModel() {
    val incidents: StateFlow<List<Incident>> = coordinator.state
        .map { it.incidents }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), coordinator.state.value.incidents)
}
