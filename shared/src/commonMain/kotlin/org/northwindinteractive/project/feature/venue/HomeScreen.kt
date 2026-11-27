package org.northwindinteractive.project.feature.venue

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource
import org.koin.compose.viewmodel.koinViewModel
import org.northwindinteractive.project.core.designsystem.AngelCard
import org.northwindinteractive.project.core.designsystem.AngelTextButton
import org.northwindinteractive.project.core.designsystem.AngelTheme
import org.northwindinteractive.project.core.designsystem.AngelTopBar
import org.northwindinteractive.project.core.designsystem.IconBadge
import org.northwindinteractive.project.core.designsystem.InfoNote
import org.northwindinteractive.project.core.designsystem.ListRow
import org.northwindinteractive.project.core.designsystem.PrimaryButton
import org.northwindinteractive.project.core.designsystem.Radii
import org.northwindinteractive.project.core.designsystem.SecondaryButton
import org.northwindinteractive.project.core.designsystem.SectionTitle
import org.northwindinteractive.project.core.designsystem.Spacing
import org.northwindinteractive.project.core.designsystem.StateMessage
import org.northwindinteractive.project.core.designsystem.StatusBanner
import org.northwindinteractive.project.core.designsystem.StatusPill
import org.northwindinteractive.project.core.designsystem.Tone
import org.northwindinteractive.project.core.designsystem.background
import org.northwindinteractive.project.core.designsystem.foreground
import org.northwindinteractive.project.core.domain.model.EnrollmentMode
import org.northwindinteractive.project.core.domain.model.Incident
import org.northwindinteractive.project.core.domain.model.LocationPermission
import org.northwindinteractive.project.core.domain.model.PresenceState
import org.northwindinteractive.project.core.domain.model.RegistrationStatus
import org.northwindinteractive.project.core.domain.policy.DenialReason
import org.northwindinteractive.project.core.domain.policy.Eligibility
import org.northwindinteractive.project.core.domain.policy.GracePolicy
import org.northwindinteractive.project.core.domain.policy.UnknownReason
import org.northwindinteractive.project.core.domain.session.SubmissionState
import org.northwindinteractive.project.core.domain.session.VenueSessionState
import org.northwindinteractive.project.core.domain.session.VisitPhase
import org.northwindinteractive.project.core.util.formatAge
import org.northwindinteractive.project.core.util.formatRemaining
import org.northwindinteractive.project.core.util.formatTime
import org.northwindinteractive.project.feature.alert.HoldButtonMode
import org.northwindinteractive.project.feature.alert.HoldToSendButton
import org.northwindinteractive.project.feature.alert.IncidentRow
import org.northwindinteractive.project.resources.Res
import org.northwindinteractive.project.resources.ic_clock
import org.northwindinteractive.project.resources.ic_exit
import org.northwindinteractive.project.resources.ic_globe
import org.northwindinteractive.project.resources.ic_history
import org.northwindinteractive.project.resources.ic_info
import org.northwindinteractive.project.resources.ic_lock
import org.northwindinteractive.project.resources.ic_mail
import org.northwindinteractive.project.resources.ic_map
import org.northwindinteractive.project.resources.ic_map_pin
import org.northwindinteractive.project.resources.ic_map_pin_off
import org.northwindinteractive.project.resources.ic_phone
import org.northwindinteractive.project.resources.ic_radar
import org.northwindinteractive.project.resources.ic_registration
import org.northwindinteractive.project.resources.ic_shield_alert
import org.northwindinteractive.project.resources.ic_status_check_filled
import org.northwindinteractive.project.resources.ic_status_warning_filled
import org.northwindinteractive.project.resources.ic_user
import org.northwindinteractive.project.resources.ic_user_approval
import org.northwindinteractive.project.resources.ic_warning
import org.northwindinteractive.project.resources.ill_no_venue

/**
 * Venue hub. One screen per [VisitPhase]: the location-gated lifecycle decides what the visitor can
 * do, so this screen is a pure function of the session state.
 */
@Composable
fun HomeScreen(
    onOpenIncident: (String) -> Unit,
    onOpenHistory: () -> Unit,
    onOpenSettings: () -> Unit,
    viewModel: HomeViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val session = state.session

    // A server receipt moves the visitor straight to the status timeline.
    LaunchedEffect(state.submission) {
        val submission = state.submission
        if (submission is SubmissionState.Received) {
            onOpenIncident(submission.receipt.incidentId)
            viewModel.acknowledgeSubmission()
        }
    }

    val presence = session.presence?.presence
    val expiredOnSite = session.phase == VisitPhase.EXPIRED &&
        presence?.state == PresenceState.CONFIRMED_INSIDE &&
        presence.venueId == session.venue?.venueId
    val title = when {
        session.phase == VisitPhase.INSIDE_NOT_JOINED || session.phase == VisitPhase.REJECTED || expiredOnSite -> "Venue Registration"
        else -> "Project Angel"
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(AngelTheme.colors.canvas)
            .statusBarsPadding(),
    ) {
        AngelTopBar(title = title) {
            IconButton(onClick = onOpenSettings) {
                Icon(painterResource(Res.drawable.ic_user), contentDescription = "Account and settings", tint = AngelTheme.colors.content)
            }
        }
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Spacing.md)
                .navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            val open = session.openIncident
            if (open != null && session.phase != VisitPhase.INSIDE_ACTIVE) {
                ActiveIncidentBanner(open, onClick = { onOpenIncident(open.incidentId) })
            }
            when (session.phase) {
                VisitPhase.PERMISSION_REQUIRED -> PermissionContent(state, viewModel)
                VisitPhase.LOCATING -> LocatingContent()
                VisitPhase.LOCATION_UNKNOWN -> LocationUnknownContent(session, viewModel)
                VisitPhase.OUTSIDE_COVERAGE -> OutsideCoverageContent(session, viewModel)
                VisitPhase.INSIDE_NOT_JOINED -> RegistrationContent(state, viewModel, expired = false)
                VisitPhase.EXPIRED -> if (expiredOnSite) {
                    RegistrationContent(state, viewModel, expired = true)
                } else {
                    ExpiredContent(session)
                }
                VisitPhase.PENDING_APPROVAL -> PendingContent(session, viewModel)
                VisitPhase.REJECTED -> RejectedContent(session)
                VisitPhase.INSIDE_ACTIVE -> ActiveVenueContent(state, viewModel, onOpenIncident, onOpenHistory)
                VisitPhase.EXITED_GRACE -> GraceContent(session)
                VisitPhase.REENTRY_VERIFYING -> ReentryContent(session)
                VisitPhase.BLOCKED -> BlockedContent(session)
            }
            if (session.phase != VisitPhase.INSIDE_ACTIVE && session.incidents.isNotEmpty()) {
                RecentAlerts(session.incidents, onOpenIncident, onOpenHistory)
            }
            EmergencyFootnote()
            Spacer(Modifier.height(Spacing.md))
        }
    }
}

// region Phases

@Composable
private fun ColumnScope.ActiveVenueContent(
    state: HomeUiState,
    viewModel: HomeViewModel,
    onOpenIncident: (String) -> Unit,
    onOpenHistory: () -> Unit,
) {
    val session = state.session
    val venue = session.venue ?: return
    val presence = session.presence?.presence
    var showMap by rememberSaveable { mutableStateOf(false) }
    var confirmA11ySend by remember { mutableStateOf(false) }

    StatusBanner(
        title = "Inside venue",
        subtitle = "Registration active",
        icon = Res.drawable.ic_status_check_filled,
        tone = Tone.SUCCESS,
    )

    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(venue.displayName, style = MaterialTheme.typography.headlineSmall, color = AngelTheme.colors.content)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(painterResource(Res.drawable.ic_map_pin), contentDescription = null, tint = AngelTheme.colors.primary, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(Spacing.xxs))
                Text(
                    presence?.zone?.let { "${it.label} (estimate)" } ?: "Zone unknown",
                    style = MaterialTheme.typography.bodyMedium,
                    color = AngelTheme.colors.contentMuted,
                )
            }
        }
        StatusPill(text = if (showMap) "Hide map" else "View map", tone = Tone.PRIMARY, modifier = Modifier.clickable(role = Role.Button) { showMap = !showMap })
    }
    if (showMap) {
        GeofenceMapCard(label = "You are inside the venue", caption = "Location estimate · not an exact indoor position")
    }

    val open = session.openIncident
    val mode = when {
        state.submission is SubmissionState.Sending -> HoldButtonMode.SENDING
        open != null && open.venueId == venue.venueId -> HoldButtonMode.ACTIVE_ALERT
        session.canSendAlert -> HoldButtonMode.READY
        else -> HoldButtonMode.LOCKED
    }
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        HoldToSendButton(
            mode = mode,
            onHoldComplete = { viewModel.sendAlert() },
            onHoldProgressTick = viewModel::onHoldTick,
            onAccessibilityRequest = { confirmA11ySend = true },
            onActiveAlertClick = { open?.let { onOpenIncident(it.incidentId) } },
        )
    }
    Text(
        text = when (mode) {
            HoldButtonMode.READY -> "Press and hold for 2 seconds"
            HoldButtonMode.SENDING -> "Sending your alert…"
            HoldButtonMode.ACTIVE_ALERT -> "Venue security has your alert. Tap to view status."
            HoldButtonMode.LOCKED -> lockedCopy(session.alertEligibility)
        },
        style = MaterialTheme.typography.bodyMedium,
        color = AngelTheme.colors.contentMuted,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth().semantics { liveRegion = LiveRegionMode.Polite },
    )

    SubmissionFeedback(state.submission, onRetry = viewModel::retryAlert, onDismiss = viewModel::acknowledgeSubmission)

    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        MiniStatusCard(
            icon = Res.drawable.ic_map_pin,
            title = "Location Status",
            value = "Inside venue",
            detail = presence?.observedAt?.let { "Checked ${(session.now - it).formatAge()}" } ?: "",
            tone = Tone.SUCCESS,
            modifier = Modifier.weight(1f),
        )
        MiniStatusCard(
            icon = Res.drawable.ic_clock,
            title = "Grace Period",
            value = graceLabel(venue.graceMinutes),
            detail = "after leaving venue",
            tone = Tone.WARNING,
            modifier = Modifier.weight(1f),
        )
    }

    AngelCard(contentPadding = PaddingValues(0.dp)) {
        ListRow(
            title = "Registration",
            subtitle = "Active · Expires after extended absence",
            icon = Res.drawable.ic_registration,
            tone = Tone.PRIMARY,
            trailing = { StatusPill("Active", Tone.SUCCESS) },
        )
    }

    if (session.permission == LocationPermission.GRANTED_FOREGROUND && !state.backgroundPromptDismissed) {
        AngelCard {
            Row(verticalAlignment = Alignment.Top) {
                IconBadge(Res.drawable.ic_radar, Tone.PRIMARY, size = 36)
                Spacer(Modifier.width(Spacing.sm))
                Column(Modifier.weight(1f)) {
                    Text("Lock alerts when you leave", style = MaterialTheme.typography.titleSmall, color = AngelTheme.colors.content)
                    Text(
                        "Optional: allow location \"Always\" so your phone can tell Project Angel when you exit this venue, even if the app is closed. We never track you continuously.",
                        style = MaterialTheme.typography.bodySmall,
                        color = AngelTheme.colors.contentMuted,
                    )
                }
            }
            Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                TextButton(onClick = viewModel::dismissBackgroundPrompt) { Text("Not now", color = AngelTheme.colors.contentMuted) }
                TextButton(onClick = viewModel::requestBackgroundPermission) { Text("Allow", color = AngelTheme.colors.primary) }
            }
        }
    }

    RecentAlerts(session.incidents, onOpenIncident, onOpenHistory, showEmpty = true)

    if (confirmA11ySend) {
        AlertDialog(
            onDismissRequest = { confirmA11ySend = false },
            title = { Text("Send discreet alert?") },
            text = { Text("Venue security at ${venue.displayName} will be notified with your estimated location in the venue.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmA11ySend = false
                    viewModel.sendAlert()
                }) { Text("Send alert", color = AngelTheme.colors.danger) }
            },
            dismissButton = { TextButton(onClick = { confirmA11ySend = false }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun ColumnScope.RegistrationContent(state: HomeUiState, viewModel: HomeViewModel, expired: Boolean) {
    val session = state.session
    val venue = session.venue ?: return
    val enrollment = state.enrollment

    VenueIdentityCard(venue)
    GeofenceMapCard(
        label = "You are inside the venue",
        caption = "You are within this venue's coverage area. Location estimate.",
    )

    SectionTitle("Registration Type")
    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        EnrollmentOption(
            icon = Res.drawable.ic_globe,
            title = "Public",
            subtitle = "Open to all visitors",
            mode = EnrollmentMode.PUBLIC,
            allowed = EnrollmentMode.PUBLIC in venue.enrollmentModes,
            selected = enrollment.selectedMode == EnrollmentMode.PUBLIC,
            onSelect = viewModel::selectMode,
            modifier = Modifier.weight(1f),
        )
        EnrollmentOption(
            icon = Res.drawable.ic_mail,
            title = "Invite",
            subtitle = "With invitation code",
            mode = EnrollmentMode.INVITATION,
            allowed = EnrollmentMode.INVITATION in venue.enrollmentModes,
            selected = enrollment.selectedMode == EnrollmentMode.INVITATION,
            onSelect = viewModel::selectMode,
            modifier = Modifier.weight(1f),
        )
        EnrollmentOption(
            icon = Res.drawable.ic_user_approval,
            title = "Approval",
            subtitle = "Requires venue approval",
            mode = EnrollmentMode.APPROVAL,
            allowed = EnrollmentMode.APPROVAL in venue.enrollmentModes,
            selected = enrollment.selectedMode == EnrollmentMode.APPROVAL,
            onSelect = viewModel::selectMode,
            modifier = Modifier.weight(1f),
        )
    }

    if (enrollment.selectedMode == EnrollmentMode.INVITATION) {
        OutlinedTextField(
            value = enrollment.inviteCode,
            onValueChange = viewModel::onInviteCodeChange,
            label = { Text("Invitation code") },
            singleLine = true,
            shape = MaterialTheme.shapes.medium,
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = AngelTheme.colors.card,
                unfocusedContainerColor = AngelTheme.colors.card,
                unfocusedBorderColor = AngelTheme.colors.cardBorder,
            ),
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters, imeAction = ImeAction.Done),
            modifier = Modifier.fillMaxWidth(),
        )
    }

    InfoNote("Registration is only available while you are inside the venue geofence.")

    enrollment.error?.let {
        Text(it, color = AngelTheme.colors.danger, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
    }

    val canEnroll = session.presence?.presence?.state == PresenceState.CONFIRMED_INSIDE &&
        enrollment.selectedMode != null
    PrimaryButton(
        text = when (enrollment.selectedMode) {
            EnrollmentMode.INVITATION -> "Redeem invitation"
            EnrollmentMode.APPROVAL -> "Request access"
            else -> "Register for ${venue.displayName}"
        },
        onClick = viewModel::enroll,
        enabled = canEnroll,
        loading = enrollment.inFlight,
    )

    AngelCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconBadge(Res.drawable.ic_clock, Tone.NEUTRAL, size = 40)
            Spacer(Modifier.width(Spacing.sm))
            Column {
                Text("Registration Status", style = MaterialTheme.typography.titleSmall, color = AngelTheme.colors.content)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        painterResource(if (expired) Res.drawable.ic_status_warning_filled else Res.drawable.ic_info),
                        contentDescription = null,
                        tint = if (expired) AngelTheme.colors.warning else AngelTheme.colors.contentMuted,
                        modifier = Modifier.size(14.dp),
                    )
                    Spacer(Modifier.width(Spacing.xxs))
                    Text(if (expired) "Registration expired" else "Not registered", style = MaterialTheme.typography.bodySmall, color = AngelTheme.colors.content)
                }
                Text(
                    if (expired) "Your previous visit expired. Register again to restore venue alerts." else "Choose a registration type above to activate venue alerts.",
                    style = MaterialTheme.typography.bodySmall,
                    color = AngelTheme.colors.contentMuted,
                )
            }
        }
    }
}

@Composable
private fun EnrollmentOption(
    icon: DrawableResource,
    title: String,
    subtitle: String,
    mode: EnrollmentMode,
    allowed: Boolean,
    selected: Boolean,
    onSelect: (EnrollmentMode) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = AngelTheme.colors
    val borderColor = if (selected) colors.primary else colors.cardBorder
    Column(
        modifier = modifier
            .heightIn(min = 112.dp)
            .clip(Radii.chip)
            .background(if (selected) colors.primarySoft else colors.card)
            .border(if (selected) 2.dp else 1.dp, borderColor, Radii.chip)
            .selectable(selected = selected, enabled = allowed, role = Role.RadioButton) { onSelect(mode) }
            .padding(Spacing.xs),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            painterResource(icon),
            contentDescription = null,
            tint = if (allowed) colors.primary else colors.contentMuted.copy(alpha = 0.5f),
            modifier = Modifier.size(24.dp),
        )
        Spacer(Modifier.height(Spacing.xxs))
        Text(title, style = MaterialTheme.typography.titleSmall, color = if (allowed) colors.content else colors.contentMuted)
        Text(
            if (allowed) subtitle else "Not offered here",
            style = MaterialTheme.typography.bodySmall,
            color = colors.contentMuted,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun ColumnScope.PendingContent(session: VenueSessionState, viewModel: HomeViewModel) {
    val venue = session.venue
    StatusBanner(
        title = "Access pending",
        subtitle = "Waiting for venue approval",
        icon = Res.drawable.ic_status_warning_filled,
        tone = Tone.WARNING,
    )
    venue?.let { VenueIdentityCard(it) }
    StateMessage(
        illustration = Res.drawable.ic_user_approval,
        illustrationTint = AngelTheme.colors.warning,
        title = "Request submitted",
        body = "${venue?.displayName ?: "The venue"} is reviewing your access request. You can't send venue alerts until you're approved and on site.",
    )
    AngelCard(contentPadding = PaddingValues(0.dp)) {
        ListRow(
            title = "Requested",
            subtitle = session.registration?.registeredAt?.formatTime() ?: "Just now",
            icon = Res.drawable.ic_registration,
            trailing = { StatusPill("PENDING", Tone.WARNING) },
        )
    }
    SecondaryButton("Refresh status", onClick = viewModel::refresh)
}

@Composable
private fun ColumnScope.RejectedContent(session: VenueSessionState) {
    session.venue?.let { VenueIdentityCard(it) }
    StateMessage(
        illustration = Res.drawable.ic_lock,
        illustrationTint = AngelTheme.colors.danger,
        title = "Access request declined",
        body = "${session.venue?.displayName ?: "This venue"} didn't approve your request, so venue alerts aren't available. Speak to venue staff if you need help.",
    )
}

@Composable
private fun ColumnScope.GraceContent(session: VenueSessionState) {
    val registration = session.registration
    val venue = session.venue
    val expiresAt = registration?.expiresAt
    StatusBanner(
        title = "Outside venue",
        subtitle = "New alerts are unavailable",
        icon = Res.drawable.ic_exit,
        tone = Tone.WARNING,
    )
    StateMessage(
        illustration = Res.drawable.ill_no_venue,
        title = "You are outside the venue",
        body = buildString {
            append("New alerts are unavailable.")
            if (expiresAt != null) {
                append(" Registration retained until ${expiresAt.formatTime()}.")
            } else {
                append(" We're recording your exit with ${venue?.displayName ?: "the venue"}.")
            }
        },
    )
    if (registration != null && expiresAt != null) {
        val remaining = GracePolicy.remaining(registration, session.now)
        AngelCard(contentPadding = PaddingValues(0.dp)) {
            ListRow(
                title = "Grace period",
                subtitle = remaining?.let { "Expires in ${it.formatRemaining()}" },
                icon = Res.drawable.ic_clock,
                tone = Tone.WARNING,
                trailing = { StatusPill("RETAINED", Tone.WARNING) },
            )
        }
    }
    InfoNote("Return to ${venue?.displayName ?: "the venue"} before your registration expires to restore access without registering again. Access returns only after we verify you're back inside.")
}

@Composable
private fun ColumnScope.ReentryContent(session: VenueSessionState) {
    Column(Modifier.fillMaxWidth().padding(vertical = Spacing.xl), horizontalAlignment = Alignment.CenterHorizontally) {
        CircularProgressIndicator(color = AngelTheme.colors.primary)
        Spacer(Modifier.height(Spacing.md))
        Text("Welcome back", style = MaterialTheme.typography.headlineSmall, color = AngelTheme.colors.content)
        Text(
            "Verifying you're inside ${session.venue?.displayName ?: "the venue"}. Alerts unlock once verification completes.",
            style = MaterialTheme.typography.bodyMedium,
            color = AngelTheme.colors.contentMuted,
            textAlign = TextAlign.Center,
            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
        )
    }
}

@Composable
private fun ColumnScope.ExpiredContent(session: VenueSessionState) {
    StateMessage(
        illustration = Res.drawable.ic_history,
        illustrationTint = AngelTheme.colors.contentMuted,
        title = "Registration expired",
        body = "Your visit registration for ${session.venue?.displayName ?: "the venue"} has expired. It doesn't renew automatically — register again once you're back on site.",
    )
}

@Composable
private fun ColumnScope.BlockedContent(session: VenueSessionState) {
    StateMessage(
        illustration = Res.drawable.ic_lock,
        illustrationTint = AngelTheme.colors.danger,
        title = "Venue access unavailable",
        body = "${session.venue?.displayName ?: "This venue"} has disabled Project Angel access for your account. Speak to venue staff if you need help.",
    )
}

@Composable
private fun ColumnScope.LocatingContent() {
    Column(Modifier.fillMaxWidth().padding(vertical = Spacing.xxl), horizontalAlignment = Alignment.CenterHorizontally) {
        CircularProgressIndicator(color = AngelTheme.colors.primary)
        Spacer(Modifier.height(Spacing.md))
        Text("Checking your location…", style = MaterialTheme.typography.titleMedium, color = AngelTheme.colors.content)
        Text(
            "Venue features unlock when we confirm you're inside a participating venue.",
            style = MaterialTheme.typography.bodyMedium,
            color = AngelTheme.colors.contentMuted,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun ColumnScope.PermissionContent(state: HomeUiState, viewModel: HomeViewModel) {
    val permission = state.session.permission
    val firstAsk = permission == LocationPermission.NOT_DETERMINED && !state.primerDismissed
    if (firstAsk) {
        // Contextual explanation shown before the OS prompt (never at cold launch).
        StateMessage(
            illustration = Res.drawable.ic_map_pin,
            illustrationTint = AngelTheme.colors.primary,
            title = "Confirm you're on site",
            body = "Project Angel uses your location only to confirm you're inside a participating venue before enabling registration and alerts.",
        )
        AngelCard(contentPadding = PaddingValues(vertical = Spacing.xs)) {
            ListRow("Only checked while the app is open", icon = Res.drawable.ic_radar)
            ListRow("No continuous tracking after you leave", icon = Res.drawable.ic_map_pin_off)
            ListRow("Shared with venue security only when you send an alert", icon = Res.drawable.ic_shield_alert)
        }
        PrimaryButton("Allow location access", onClick = viewModel::requestLocationPermission)
        AngelTextButton("Not now", onClick = viewModel::dismissPrimer, modifier = Modifier.align(Alignment.CenterHorizontally))
    } else {
        StatusBanner(
            title = "Location is off",
            subtitle = "Venue alerts and registration are locked",
            icon = Res.drawable.ic_lock,
            tone = Tone.NEUTRAL,
        )
        StateMessage(
            illustration = Res.drawable.ic_map_pin_off,
            illustrationTint = AngelTheme.colors.contentMuted,
            title = "We can't confirm you're inside a venue",
            body = "Project Angel needs location access to check you're on site. Your account and settings remain available.",
        )
        if (permission == LocationPermission.NOT_DETERMINED) {
            PrimaryButton("Allow location access", onClick = viewModel::requestLocationPermission)
        } else {
            PrimaryButton("Open settings", onClick = { viewModel.openSettings(permission) })
            Troubleshooting(
                listOf(
                    "Open Settings → Project Angel → Location",
                    "Choose \"While Using the App\" and turn on Precise Location",
                    "Return here and we'll check again",
                ),
            )
        }
    }
}

@Composable
private fun ColumnScope.LocationUnknownContent(session: VenueSessionState, viewModel: HomeViewModel) {
    val reason = session.presence?.reason
    val venueName = session.presence?.venue?.displayName
    StatusBanner(
        title = "Location unknown",
        subtitle = if (session.registration?.status == RegistrationStatus.ACTIVE_INSIDE) "Alerts paused until we verify you're on site" else "Venue features are locked",
        icon = Res.drawable.ic_warning,
        tone = Tone.WARNING,
    )
    StateMessage(
        illustration = Res.drawable.ic_map_pin_off,
        illustrationTint = AngelTheme.colors.warning,
        title = "We can't confirm you're inside a venue",
        body = when (reason) {
            UnknownReason.IMPRECISE_FIX -> "Your location is too imprecise right now. Verification is pending."
            UnknownReason.NEAR_BOUNDARY -> "You appear to be at the edge of ${venueName ?: "a venue"}. Move further inside and retry."
            UnknownReason.STALE_FIX -> "Your last location reading is too old to rely on."
            else -> "We haven't received a location reading yet."
        },
    )
    PrimaryButton("Retry", onClick = viewModel::refresh)
    Troubleshooting(
        listOf(
            "Make sure Location Services and Precise Location are on",
            "Move away from thick walls or basements, or closer to a window",
            "Indoor signal can be weak — we never guess your position",
        ),
    )
    SecondaryButton("Open location settings", onClick = { viewModel.openSettings(session.permission) })
}

@Composable
private fun ColumnScope.OutsideCoverageContent(session: VenueSessionState, viewModel: HomeViewModel) {
    StatusBanner(
        title = "Outside coverage",
        subtitle = "No participating venue detected",
        icon = Res.drawable.ic_map_pin_off,
        tone = Tone.NEUTRAL,
    )
    StateMessage(
        illustration = Res.drawable.ill_no_venue,
        title = "Not at a participating venue",
        body = "Project Angel works only inside participating venues. Registration and discreet alerts unlock automatically when you're on site.",
    )
    if (session.venues.isNotEmpty()) {
        SectionTitle("Participating venues nearby")
        AngelCard(contentPadding = PaddingValues(vertical = Spacing.xxs)) {
            session.venues.values.sortedBy { it.displayName }.forEach { venue ->
                ListRow(
                    title = venue.displayName,
                    subtitle = venue.publicAddress,
                    icon = Res.drawable.ic_map,
                )
            }
        }
    }
    session.venuesError?.let { InfoNote("Couldn't load venues: ${it.message}", tone = Tone.WARNING) }
    SecondaryButton("Check again", onClick = viewModel::refresh)
}

// endregion

// region Shared pieces

@Composable
private fun SubmissionFeedback(submission: SubmissionState, onRetry: () -> Unit, onDismiss: () -> Unit) {
    when (submission) {
        is SubmissionState.Unsent -> AngelCard(borderColor = AngelTheme.colors.danger.copy(alpha = 0.4f), containerColor = AngelTheme.colors.dangerSoft) {
            Text("Alert not sent", style = MaterialTheme.typography.titleSmall, color = AngelTheme.colors.danger)
            Text(
                "${submission.error.message}. Your alert has not been delivered. If you need help now, contact venue staff or call emergency services.",
                style = MaterialTheme.typography.bodySmall,
                color = AngelTheme.colors.content,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Assertive },
            )
            Spacer(Modifier.height(Spacing.xs))
            PrimaryButton("Retry sending", onClick = onRetry, containerColor = AngelTheme.colors.danger)
        }
        is SubmissionState.Blocked -> AngelCard(containerColor = AngelTheme.colors.warningSoft, borderColor = AngelTheme.colors.warning.copy(alpha = 0.4f)) {
            Text("Alert not sent", style = MaterialTheme.typography.titleSmall, color = AngelTheme.colors.warning)
            Text(
                denialCopy(submission.reason),
                style = MaterialTheme.typography.bodySmall,
                color = AngelTheme.colors.content,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Assertive },
            )
            AngelTextButton("Dismiss", onClick = onDismiss)
        }
        else -> Unit
    }
}

@Composable
private fun MiniStatusCard(
    icon: DrawableResource,
    title: String,
    value: String,
    detail: String,
    tone: Tone,
    modifier: Modifier = Modifier,
) {
    AngelCard(modifier = modifier.semantics(mergeDescendants = true) {}, contentPadding = PaddingValues(Spacing.sm)) {
        Row(verticalAlignment = Alignment.Top) {
            IconBadge(icon, tone, size = 32)
            Spacer(Modifier.width(Spacing.xs))
            Column {
                Text(title, style = MaterialTheme.typography.labelMedium, color = AngelTheme.colors.content)
                Text(value, style = MaterialTheme.typography.bodySmall, color = tone.foreground())
                if (detail.isNotBlank()) Text(detail, style = MaterialTheme.typography.bodySmall, color = AngelTheme.colors.contentMuted)
            }
        }
    }
}

@Composable
private fun ActiveIncidentBanner(incident: Incident, onClick: () -> Unit) {
    StatusBanner(
        title = "Your alert is active",
        subtitle = "${incident.venueName} · ${incident.status.name.lowercase().replaceFirstChar { it.uppercase() }}",
        icon = Res.drawable.ic_shield_alert,
        tone = Tone.DANGER,
        onClick = onClick,
    )
}

@Composable
private fun RecentAlerts(
    incidents: List<Incident>,
    onOpenIncident: (String) -> Unit,
    onOpenHistory: () -> Unit,
    showEmpty: Boolean = false,
) {
    if (incidents.isEmpty() && !showEmpty) return
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        SectionTitle("Recent Alerts") {
            if (incidents.isNotEmpty()) AngelTextButton("View all", onClick = onOpenHistory)
        }
        AngelCard(contentPadding = PaddingValues(vertical = Spacing.xxs)) {
            if (incidents.isEmpty()) {
                ListRow(
                    title = "No recent alerts",
                    subtitle = "Your alerts will appear here.",
                    icon = Res.drawable.ic_history,
                    tone = Tone.NEUTRAL,
                )
            } else {
                incidents.take(3).forEach { incident ->
                    IncidentRow(incident, onClick = { onOpenIncident(incident.incidentId) })
                }
            }
        }
    }
}

@Composable
private fun Troubleshooting(steps: List<String>) {
    AngelCard {
        Text("Troubleshooting", style = MaterialTheme.typography.titleSmall, color = AngelTheme.colors.content)
        Spacer(Modifier.height(Spacing.xs))
        steps.forEachIndexed { index, step ->
            Row(Modifier.padding(vertical = 2.dp)) {
                Text("${index + 1}.", style = MaterialTheme.typography.bodySmall, color = AngelTheme.colors.primary, modifier = Modifier.width(20.dp))
                Text(step, style = MaterialTheme.typography.bodySmall, color = AngelTheme.colors.contentMuted)
            }
        }
    }
}

@Composable
private fun EmergencyFootnote() {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(Radii.chip)
            .background(Tone.NEUTRAL.background())
            .padding(Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(painterResource(Res.drawable.ic_phone), contentDescription = null, tint = AngelTheme.colors.contentMuted, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(Spacing.xs))
        Text(
            "Project Angel is not a replacement for emergency services. In an emergency, call your local emergency number.",
            style = MaterialTheme.typography.bodySmall,
            color = AngelTheme.colors.contentMuted,
        )
    }
}

private fun graceLabel(minutes: Int): String =
    if (minutes % 60 == 0) "${minutes / 60} hours" else "$minutes min"

private fun lockedCopy(eligibility: Eligibility): String = when (eligibility) {
    Eligibility.Allowed -> ""
    is Eligibility.Denied -> denialCopy(eligibility.reason)
}

internal fun denialCopy(reason: DenialReason): String = when (reason) {
    DenialReason.NOT_REGISTERED -> "Register with this venue to enable alerts."
    DenialReason.PENDING_APPROVAL -> "Waiting for venue approval."
    DenialReason.OUTSIDE_VENUE -> "You are outside the venue. New alerts are unavailable."
    DenialReason.IN_GRACE_PERIOD -> "You are outside the venue. Alerts return after verified reentry."
    DenialReason.REGISTRATION_EXPIRED -> "Your registration expired. Register again on site."
    DenialReason.REJECTED -> "The venue declined your access request."
    DenialReason.SUSPENDED_OR_REVOKED -> "Venue access has been disabled."
    DenialReason.LOCATION_UNKNOWN -> "We can't confirm you're inside a venue. Check location and retry."
    DenialReason.LOCATION_STALE -> "Refreshing your location. Try again in a moment."
    DenialReason.REENTRY_VERIFYING -> "Verifying you're back inside the venue."
    DenialReason.DIFFERENT_VENUE -> "You're registered with a different venue."
    DenialReason.SESSION_LIMIT_REACHED -> "This visit session has reached its time limit. Register again."
}

// endregion
