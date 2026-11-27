package org.northwindinteractive.project.feature.alert

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.jetbrains.compose.resources.painterResource
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf
import org.northwindinteractive.project.core.designsystem.AngelCard
import org.northwindinteractive.project.core.designsystem.AngelTheme
import org.northwindinteractive.project.core.designsystem.AngelTopBar
import org.northwindinteractive.project.core.designsystem.InfoNote
import org.northwindinteractive.project.core.designsystem.ListRow
import org.northwindinteractive.project.core.designsystem.Spacing
import org.northwindinteractive.project.core.designsystem.StateMessage
import org.northwindinteractive.project.core.designsystem.StatusPill
import org.northwindinteractive.project.core.designsystem.Tone
import org.northwindinteractive.project.core.designsystem.background
import org.northwindinteractive.project.core.designsystem.foreground
import org.northwindinteractive.project.core.domain.model.AlertEventType
import org.northwindinteractive.project.core.domain.model.Incident
import org.northwindinteractive.project.core.domain.model.IncidentStatus
import org.northwindinteractive.project.core.util.formatAge
import org.northwindinteractive.project.core.util.formatDateTime
import org.northwindinteractive.project.core.util.formatTime
import org.northwindinteractive.project.feature.venue.GeofenceMapCard
import org.northwindinteractive.project.resources.Res
import org.northwindinteractive.project.resources.ic_check
import org.northwindinteractive.project.resources.ic_history
import org.northwindinteractive.project.resources.ic_map_pin
import org.northwindinteractive.project.resources.ic_phone
import org.northwindinteractive.project.resources.ic_shield_alert
import org.northwindinteractive.project.resources.ic_shield_check
import kotlin.time.Instant

@Composable
fun AlertDetailsScreen(
    incidentId: String,
    onBack: () -> Unit,
    viewModel: AlertDetailsViewModel = koinViewModel(key = incidentId) { parametersOf(incidentId) },
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    Column(Modifier.fillMaxSize().background(AngelTheme.colors.canvas).statusBarsPadding()) {
        AngelTopBar(title = "Alert Details", onBack = onBack)
        when (val s = state) {
            IncidentUiState.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = AngelTheme.colors.primary)
            }
            IncidentUiState.NotFound -> Column(Modifier.padding(Spacing.md)) {
                StateMessage(
                    illustration = Res.drawable.ic_history,
                    illustrationTint = AngelTheme.colors.contentMuted,
                    title = "Alert unavailable",
                    body = "We couldn't load this alert. Check your connection and try again.",
                )
            }
            is IncidentUiState.Loaded -> IncidentDetails(s)
        }
    }
}

@Composable
private fun IncidentDetails(state: IncidentUiState.Loaded) {
    val incident = state.incident
    val (headline, sub, tone) = headerFor(incident.status)
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = Spacing.md)
            .navigationBarsPadding(),
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        // Header
        Row(
            Modifier
                .fillMaxWidth()
                .clip(MaterialTheme.shapes.large)
                .background(tone.background())
                .border(1.dp, tone.foreground().copy(alpha = 0.3f), MaterialTheme.shapes.large)
                .padding(Spacing.md)
                .semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.size(44.dp).clip(CircleShape).background(tone.foreground()), contentAlignment = Alignment.Center) {
                Icon(
                    painterResource(if (incident.status == IncidentStatus.RECEIVED) Res.drawable.ic_shield_alert else Res.drawable.ic_shield_check),
                    contentDescription = null,
                    tint = tone.background(),
                    modifier = Modifier.size(24.dp),
                )
            }
            Spacer(Modifier.width(Spacing.sm))
            Column {
                Text(headline, style = MaterialTheme.typography.titleMedium, color = tone.foreground())
                Text(sub, style = MaterialTheme.typography.bodySmall, color = AngelTheme.colors.content)
            }
        }

        // Status timeline
        AngelCard {
            StatusTimeline(incident)
        }

        // Receipt
        AngelCard(contentPadding = PaddingValues(vertical = Spacing.xxs)) {
            ListRow(
                title = "Incident ID",
                subtitle = incident.incidentId,
                trailing = { StatusPill(incident.status.label(), incident.status.tone()) },
            )
            ListRow(
                title = "Server receipt",
                subtitle = (incident.eventAt(AlertEventType.RECEIVED)?.serverTime ?: incident.createdAt).formatDateTime(),
            )
            ListRow(title = "Venue", subtitle = incident.venueName)
        }

        // Last known location: never presented as a live fix.
        val zoneLabel = incident.lastKnownZone?.label ?: "Zone unknown"
        val at = incident.lastKnownAt ?: incident.createdAt
        AngelCard {
            Row(verticalAlignment = Alignment.Top) {
                Icon(painterResource(Res.drawable.ic_map_pin), contentDescription = null, tint = AngelTheme.colors.primary, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(Spacing.xs))
                Column(Modifier.weight(1f)) {
                    Text("Last known location", style = MaterialTheme.typography.titleSmall, color = AngelTheme.colors.content)
                    Text("$zoneLabel · ${incident.venueName}", style = MaterialTheme.typography.bodySmall, color = AngelTheme.colors.content)
                    Text("${at.formatTime()} (${(state.now - at).formatAge()}) · estimate", style = MaterialTheme.typography.bodySmall, color = AngelTheme.colors.contentMuted)
                }
                if (state.visitorAway) StatusPill("Historical", Tone.NEUTRAL)
            }
            Spacer(Modifier.height(Spacing.sm))
            GeofenceMapCard(
                label = incident.venueName,
                caption = if (state.visitorAway) "Historical snapshot · your current position is not tracked" else "Location estimate at the time of your alert",
                showMarker = !state.visitorAway,
                height = 150,
            )
        }

        InfoNote(
            if (incident.visitorExited) {
                "You've left the venue. Your alert stays active for venue security, but your current location is not tracked."
            } else {
                "If you leave the venue, this alert will remain active, but your current location may become unknown."
            },
        )

        Row(
            Modifier.fillMaxWidth().clip(MaterialTheme.shapes.small).background(AngelTheme.colors.dangerSoft).padding(Spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(painterResource(Res.drawable.ic_phone), contentDescription = null, tint = AngelTheme.colors.danger, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(Spacing.xs))
            Text(
                "In immediate danger? Contact venue staff or call your local emergency number.",
                style = MaterialTheme.typography.bodySmall,
                color = AngelTheme.colors.content,
            )
        }
        Spacer(Modifier.height(Spacing.md))
    }
}

private data class TimelineStep(val title: String, val detail: String, val time: Instant?, val reached: Boolean)

/**
 * Distinguishes sent, delivered to backend, acknowledged and responding. Backend receipt is never
 * shown as staff acknowledgement.
 */
@Composable
private fun StatusTimeline(incident: Incident) {
    val acknowledged = incident.eventAt(AlertEventType.ACKNOWLEDGED)
    val responding = incident.eventAt(AlertEventType.RESPONDING)
    val resolved = incident.eventAt(AlertEventType.RESOLVED)
    val steps = buildList {
        add(TimelineStep("Sent", "Alert sent from your phone", incident.eventAt(AlertEventType.SUBMITTED)?.serverTime ?: incident.createdAt, true))
        add(TimelineStep("Received by Project Angel", "Server receipt issued", incident.eventAt(AlertEventType.RECEIVED)?.serverTime ?: incident.createdAt, true))
        add(TimelineStep("Acknowledged", if (acknowledged != null) "Venue security acknowledged your alert" else "Waiting for venue security", acknowledged?.serverTime, acknowledged != null))
        add(TimelineStep("Responding", if (responding != null) "Security team is on their way" else "Not yet responding", responding?.serverTime, responding != null))
        if (resolved != null) add(TimelineStep("Closed", "Incident closed by venue security", resolved.serverTime, true))
    }
    val current = steps.indexOfLast { it.reached }
    Column {
        steps.forEachIndexed { index, step ->
            TimelineRow(step, isCurrent = index == current, isLast = index == steps.lastIndex)
        }
    }
}

@Composable
private fun TimelineRow(step: TimelineStep, isCurrent: Boolean, isLast: Boolean) {
    val colors = AngelTheme.colors
    val dotColor = if (step.reached) colors.primary else colors.cardBorder
    Row(
        Modifier.fillMaxWidth().semantics(mergeDescendants = true) {
            stateDescription = if (step.reached) "Completed" else "Pending"
        },
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(28.dp)) {
            Box(
                Modifier
                    .size(22.dp)
                    .clip(CircleShape)
                    .background(if (step.reached) dotColor else Color.Transparent)
                    .border(2.dp, dotColor, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                if (step.reached) {
                    Icon(painterResource(Res.drawable.ic_check), contentDescription = null, tint = colors.onPrimary, modifier = Modifier.size(14.dp))
                }
            }
            if (!isLast) {
                Box(Modifier.width(2.dp).height(34.dp).background(if (step.reached) colors.primary.copy(alpha = 0.5f) else colors.cardBorder))
            }
        }
        Spacer(Modifier.width(Spacing.sm))
        Column(Modifier.weight(1f).padding(bottom = if (isLast) 0.dp else Spacing.xs)) {
            Text(
                step.title,
                style = MaterialTheme.typography.titleSmall,
                color = if (step.reached) colors.content else colors.contentMuted,
            )
            Text(
                step.detail,
                style = MaterialTheme.typography.bodySmall,
                color = if (isCurrent) colors.primary else colors.contentMuted,
            )
        }
        step.time?.let {
            Text(it.formatTime(), style = MaterialTheme.typography.bodySmall, color = colors.contentMuted)
        }
    }
}

private fun headerFor(status: IncidentStatus): Triple<String, String, Tone> = when (status) {
    IncidentStatus.RECEIVED -> Triple("Alert received by Project Angel", "Waiting for venue security to acknowledge.", Tone.DANGER)
    IncidentStatus.ACKNOWLEDGED -> Triple("Venue security acknowledged your alert", "They know you need assistance.", Tone.PRIMARY)
    IncidentStatus.RESPONDING -> Triple("Security team is on their way", "Venue security is responding to your alert.", Tone.PRIMARY)
    IncidentStatus.RESOLVED -> Triple("Incident closed", "Venue security has closed this alert.", Tone.SUCCESS)
}

fun IncidentStatus.label(): String = when (this) {
    IncidentStatus.RECEIVED -> "Received"
    IncidentStatus.ACKNOWLEDGED -> "Acknowledged"
    IncidentStatus.RESPONDING -> "Responding"
    IncidentStatus.RESOLVED -> "Closed"
}

fun IncidentStatus.tone(): Tone = when (this) {
    IncidentStatus.RECEIVED -> Tone.DANGER
    IncidentStatus.ACKNOWLEDGED, IncidentStatus.RESPONDING -> Tone.PRIMARY
    IncidentStatus.RESOLVED -> Tone.SUCCESS
}

@Composable
fun IncidentRow(incident: Incident, onClick: () -> Unit) {
    ListRow(
        title = incident.venueName,
        subtitle = "${incident.createdAt.formatDateTime()} · ${incident.incidentId}",
        icon = Res.drawable.ic_shield_alert,
        tone = incident.status.tone(),
        onClick = onClick,
        trailing = { StatusPill(incident.status.label(), incident.status.tone()) },
    )
}

@Composable
fun AlertHistoryScreen(
    onBack: () -> Unit,
    onOpenIncident: (String) -> Unit,
    viewModel: AlertHistoryViewModel = koinViewModel(),
) {
    val incidents by viewModel.incidents.collectAsStateWithLifecycle()
    Column(Modifier.fillMaxSize().background(AngelTheme.colors.canvas).statusBarsPadding()) {
        AngelTopBar(title = "Your Alerts", onBack = onBack)
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Spacing.md)
                .navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            if (incidents.isEmpty()) {
                StateMessage(
                    illustration = Res.drawable.ic_history,
                    illustrationTint = AngelTheme.colors.contentMuted,
                    title = "No alerts yet",
                    body = "Alerts you send will appear here with their status. History is read-only.",
                )
            } else {
                AngelCard(contentPadding = PaddingValues(vertical = Spacing.xxs)) {
                    incidents.forEach { incident -> IncidentRow(incident) { onOpenIncident(incident.incidentId) } }
                }
                InfoNote("Alert history is read-only. Open alerts remain with venue security even after you leave or your registration expires.")
            }
        }
    }
}
