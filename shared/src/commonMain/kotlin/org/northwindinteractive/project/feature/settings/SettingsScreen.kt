package org.northwindinteractive.project.feature.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.koin.compose.viewmodel.koinViewModel
import org.northwindinteractive.project.core.data.demo.DemoControls
import org.northwindinteractive.project.core.designsystem.AngelCard
import org.northwindinteractive.project.core.designsystem.AngelTheme
import org.northwindinteractive.project.core.designsystem.AngelTopBar
import org.northwindinteractive.project.core.designsystem.Divider
import org.northwindinteractive.project.core.designsystem.InfoNote
import org.northwindinteractive.project.core.designsystem.ListRow
import org.northwindinteractive.project.core.designsystem.Radii
import org.northwindinteractive.project.core.designsystem.SecondaryButton
import org.northwindinteractive.project.core.designsystem.SectionTitle
import org.northwindinteractive.project.core.designsystem.Spacing
import org.northwindinteractive.project.core.designsystem.StatusPill
import org.northwindinteractive.project.core.designsystem.Tone
import org.northwindinteractive.project.core.domain.model.LocationPermission
import org.northwindinteractive.project.core.domain.model.RegistrationStatus
import org.northwindinteractive.project.core.domain.model.ThemeMode
import org.northwindinteractive.project.core.platform.SimulatedPresenceService.Scenario
import org.northwindinteractive.project.resources.Res
import org.northwindinteractive.project.resources.ic_building
import org.northwindinteractive.project.resources.ic_info
import org.northwindinteractive.project.resources.ic_lock
import org.northwindinteractive.project.resources.ic_map_pin
import org.northwindinteractive.project.resources.ic_phone
import org.northwindinteractive.project.resources.ic_radar
import org.northwindinteractive.project.resources.ic_shield
import org.northwindinteractive.project.resources.ic_tap
import org.northwindinteractive.project.resources.ic_user

/** Preferences and privacy (spec: account, permissions, privacy, help, authorized sessions). */
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    viewModel: SettingsViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    Column(Modifier.fillMaxSize().background(AngelTheme.colors.canvas).statusBarsPadding()) {
        AngelTopBar(title = "Preferences & Privacy", onBack = onBack)
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Spacing.md)
                .navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            // Account
            SectionTitle("Account")
            AngelCard(contentPadding = PaddingValues(vertical = Spacing.xxs)) {
                ListRow(
                    title = state.profile?.displayAlias ?: "Signed in",
                    subtitle = state.profile?.email,
                    icon = Res.drawable.ic_user,
                )
            }

            // Permissions
            SectionTitle("Permissions")
            AngelCard(contentPadding = PaddingValues(vertical = Spacing.xxs)) {
                ListRow(
                    title = "Location",
                    subtitle = permissionCopy(state.permission),
                    icon = Res.drawable.ic_map_pin,
                    onClick = viewModel::requestLocation,
                    trailing = { StatusPill(permissionPill(state.permission), permissionTone(state.permission)) },
                )
                Divider()
                ListRow(
                    title = "Exit detection (optional)",
                    subtitle = "Allow location \"Always\" so alerts lock when you leave a venue, even if the app is closed. No continuous tracking.",
                    icon = Res.drawable.ic_radar,
                    onClick = if (state.permission == LocationPermission.GRANTED_FOREGROUND) viewModel::requestBackgroundLocation else viewModel::openAppSettings,
                    trailing = {
                        StatusPill(
                            if (state.permission == LocationPermission.GRANTED_BACKGROUND) "On" else "Off",
                            if (state.permission == LocationPermission.GRANTED_BACKGROUND) Tone.SUCCESS else Tone.NEUTRAL,
                        )
                    },
                )
            }

            // Authorized sessions
            SectionTitle("Venue sessions")
            AngelCard(contentPadding = PaddingValues(vertical = Spacing.xxs)) {
                if (state.registrations.isEmpty()) {
                    ListRow(title = "No venue registrations", subtitle = "Registrations are temporary and venue-scoped.", icon = Res.drawable.ic_building, tone = Tone.NEUTRAL)
                } else {
                    state.registrations.forEach { (name, registration) ->
                        ListRow(
                            title = name,
                            subtitle = registration.mode.name.lowercase().replaceFirstChar { it.uppercase() } + " enrollment",
                            icon = Res.drawable.ic_building,
                            trailing = { StatusPill(registration.status.label(), registration.status.tone()) },
                        )
                    }
                }
            }

            // Discreet shortcuts
            SectionTitle("Discreet shortcuts")
            AngelCard {
                ListRowCompact(Res.drawable.ic_tap, "Trigger an alert without opening the app")
                Spacer(Modifier.height(Spacing.xs))
                Text(
                    shortcutGuidance(viewModel.platformName),
                    style = MaterialTheme.typography.bodySmall,
                    color = AngelTheme.colors.contentMuted,
                )
                Spacer(Modifier.height(Spacing.xs))
                InfoNote("Shortcuts never bypass the on-site check. Screen-off and locked-device gestures depend on your phone and OS version and aren't guaranteed.")
            }

            // Appearance & accessibility
            SectionTitle("Appearance & accessibility")
            AngelCard {
                Text("Theme", style = MaterialTheme.typography.titleSmall, color = AngelTheme.colors.content)
                Spacer(Modifier.height(Spacing.xs))
                ThemeSelector(state.preferences.themeMode, viewModel::setTheme)
                Spacer(Modifier.height(Spacing.xs))
                ToggleRow("Reduce motion", "Replace pulsing and animated effects with static states.", state.preferences.reducedMotion, viewModel::setReducedMotion)
                ToggleRow("Haptic confirmation", "Feel a distinct vibration when your alert is received, without looking.", state.preferences.hapticConfirmation, viewModel::setHaptics)
            }

            // Privacy
            SectionTitle("Privacy")
            AngelCard {
                PrivacyLine(Res.drawable.ic_map_pin, "Location is used only to confirm you're inside a participating venue.")
                PrivacyLine(Res.drawable.ic_radar, "Outside a venue we don't track you. A single exit event may update an open alert.")
                PrivacyLine(Res.drawable.ic_shield, "When you send an alert we share the venue, your estimated zone, observation age and accuracy.")
                PrivacyLine(Res.drawable.ic_lock, "Alerts are kept for venue security's response and audit under a defined retention policy.")
            }

            // Help
            SectionTitle("Help")
            AngelCard(contentPadding = PaddingValues(vertical = Spacing.xxs)) {
                ListRow("Project Angel isn't an emergency service", subtitle = "In an emergency, call your local emergency number.", icon = Res.drawable.ic_phone, tone = Tone.DANGER)
                Divider()
                ListRow("How it works", subtitle = "Register on site, hold the alert button for 2 seconds, and venue security is notified.", icon = Res.drawable.ic_info)
            }

            viewModel.demoControls?.let { demo -> DemoSection(state, demo, viewModel) }

            SecondaryButton(
                "Sign out",
                onClick = viewModel::signOut,
                contentColor = AngelTheme.colors.danger,
            )
            Text(
                "Project Angel ${viewModel.appVersion} · ${viewModel.platformName}${if (viewModel.isDemo) " · Demo backend" else ""}",
                style = MaterialTheme.typography.bodySmall,
                color = AngelTheme.colors.contentMuted,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(Spacing.md))
        }
    }
}

@Composable
private fun DemoSection(state: SettingsUiState, demo: DemoControls, viewModel: SettingsViewModel) {
    val controls = state.demo ?: return
    SectionTitle("Demo controls")
    AngelCard {
        InfoNote("Firebase isn't connected, so venues, staff and location are simulated. Invitation code: ${demo.inviteCode}")
        if (demo.presence != null) {
            Spacer(Modifier.height(Spacing.sm))
            Text("Simulated location", style = MaterialTheme.typography.titleSmall, color = AngelTheme.colors.content)
            Column(Modifier.selectableGroup()) {
                demo.venues.forEach { venue ->
                    val selected = (state.demoScenario as? Scenario.InsideVenue)?.venue?.venueId == venue.venueId
                    RadioRow("Inside ${venue.displayName}", selected) { demo.setScenario(Scenario.InsideVenue(venue)) }
                }
                RadioRow("Outside coverage", state.demoScenario == Scenario.OutsideCoverage) { demo.setScenario(Scenario.OutsideCoverage) }
                RadioRow("Imprecise GPS (indoor)", state.demoScenario is Scenario.Imprecise) {
                    demo.setScenario(Scenario.Imprecise(demo.venues.first()))
                }
                RadioRow("No signal", state.demoScenario == Scenario.NoSignal) { demo.setScenario(Scenario.NoSignal) }
            }
            ToggleRow(
                "Location permission denied",
                "Simulate the user refusing location access.",
                state.permission == LocationPermission.DENIED_PERMANENTLY,
            ) { denied ->
                demo.setPermission(if (denied) LocationPermission.DENIED_PERMANENTLY else LocationPermission.GRANTED_FOREGROUND)
            }
        }
        ToggleRow("Network offline", "Backend calls fail as if the phone lost connectivity.", controls.offline, demo::setOffline)
        ToggleRow("Auto-approve access requests", "Approval-mode requests are approved after ~10 s.", controls.autoApprove, demo::setAutoApprove)
        ToggleRow("Simulate security staff", "Acknowledge after ~6 s, respond after ~16 s.", controls.simulateStaff, demo::setSimulateStaff)
        ToggleRow("Short grace period (2 min)", "Test expiry without waiting five hours.", controls.graceOverride != null, demo::setShortGrace)
        if (state.session.openIncident != null) {
            Spacer(Modifier.height(Spacing.xs))
            SecondaryButton("Close open alert (staff action)", onClick = viewModel::resolveOpenIncident)
        }
    }
}

@Composable
private fun ThemeSelector(selected: ThemeMode, onSelect: (ThemeMode) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(Radii.chip)
            .border(1.dp, AngelTheme.colors.cardBorder, Radii.chip)
            .selectableGroup(),
    ) {
        ThemeMode.entries.forEach { mode ->
            val isSelected = mode == selected
            Text(
                text = when (mode) {
                    ThemeMode.SYSTEM -> "System"
                    ThemeMode.LIGHT -> "Light"
                    ThemeMode.DARK -> "Dark"
                },
                style = MaterialTheme.typography.labelLarge,
                color = if (isSelected) AngelTheme.colors.onPrimary else AngelTheme.colors.content,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .weight(1f)
                    .background(if (isSelected) AngelTheme.colors.primary else AngelTheme.colors.card)
                    .selectable(selected = isSelected, role = Role.RadioButton) { onSelect(mode) }
                    .heightIn(min = Spacing.minTouch)
                    .padding(vertical = 14.dp),
            )
        }
    }
}

@Composable
private fun ToggleRow(title: String, subtitle: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .selectable(selected = checked, role = Role.Switch) { onChange(!checked) }
            .heightIn(min = Spacing.minTouch)
            .padding(vertical = Spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, color = AngelTheme.colors.content)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = AngelTheme.colors.contentMuted)
        }
        Switch(
            checked = checked,
            onCheckedChange = null,
            colors = SwitchDefaults.colors(checkedTrackColor = AngelTheme.colors.primary),
        )
    }
}

@Composable
private fun RadioRow(text: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .heightIn(min = Spacing.minTouch),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        androidx.compose.material3.RadioButton(selected = selected, onClick = null)
        Text(text, style = MaterialTheme.typography.bodyMedium, color = AngelTheme.colors.content, modifier = Modifier.padding(start = Spacing.xs))
    }
}

@Composable
private fun PrivacyLine(icon: org.jetbrains.compose.resources.DrawableResource, text: String) {
    ListRowCompact(icon, text)
    Spacer(Modifier.height(Spacing.xs))
}

@Composable
private fun ListRowCompact(icon: org.jetbrains.compose.resources.DrawableResource, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        org.northwindinteractive.project.core.designsystem.IconBadge(icon, Tone.PRIMARY, size = 32)
        Spacer(Modifier.padding(start = Spacing.sm))
        Text(text, style = MaterialTheme.typography.bodyMedium, color = AngelTheme.colors.content)
    }
}

private fun permissionCopy(permission: LocationPermission) = when (permission) {
    LocationPermission.NOT_DETERMINED -> "Not requested yet. Tap to allow."
    LocationPermission.DENIED -> "Denied. Venue features are locked."
    LocationPermission.DENIED_PERMANENTLY -> "Off. Change it in system settings."
    LocationPermission.GRANTED_FOREGROUND -> "While using the app"
    LocationPermission.GRANTED_BACKGROUND -> "Always (exit detection on)"
}

private fun permissionPill(permission: LocationPermission) = when (permission) {
    LocationPermission.GRANTED_FOREGROUND, LocationPermission.GRANTED_BACKGROUND -> "Allowed"
    LocationPermission.NOT_DETERMINED -> "Not set"
    else -> "Off"
}

private fun permissionTone(permission: LocationPermission) = when (permission) {
    LocationPermission.GRANTED_FOREGROUND, LocationPermission.GRANTED_BACKGROUND -> Tone.SUCCESS
    LocationPermission.NOT_DETERMINED -> Tone.NEUTRAL
    else -> Tone.WARNING
}

private fun RegistrationStatus.label() = when (this) {
    RegistrationStatus.NOT_REGISTERED -> "None"
    RegistrationStatus.PENDING_APPROVAL -> "Pending"
    RegistrationStatus.ACTIVE_INSIDE -> "Active"
    RegistrationStatus.GRACE_OUTSIDE -> "Grace"
    RegistrationStatus.EXPIRED -> "Expired"
    RegistrationStatus.REJECTED -> "Declined"
    RegistrationStatus.SUSPENDED -> "Suspended"
    RegistrationStatus.REVOKED -> "Revoked"
}

private fun RegistrationStatus.tone() = when (this) {
    RegistrationStatus.ACTIVE_INSIDE -> Tone.SUCCESS
    RegistrationStatus.PENDING_APPROVAL, RegistrationStatus.GRACE_OUTSIDE -> Tone.WARNING
    RegistrationStatus.REJECTED, RegistrationStatus.SUSPENDED, RegistrationStatus.REVOKED -> Tone.DANGER
    else -> Tone.NEUTRAL
}

private fun shortcutGuidance(platformName: String): String =
    if (platformName.startsWith("iOS", ignoreCase = true)) {
        "Open the Shortcuts app and add \"Send Discreet Alert\" from Project Angel. You can then assign it to Back Tap in Settings → Accessibility → Touch → Back Tap, or to the Action button."
    } else {
        "Long-press the Project Angel icon and drag \"Send discreet alert\" to your home screen, or add the \"Angel alert\" tile to Quick Settings (swipe down twice → edit)."
    }
