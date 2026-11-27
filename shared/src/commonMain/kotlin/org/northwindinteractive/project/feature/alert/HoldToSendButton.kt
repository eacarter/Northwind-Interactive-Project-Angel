package org.northwindinteractive.project.feature.alert

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.painterResource
import org.northwindinteractive.project.core.designsystem.AngelTheme
import org.northwindinteractive.project.resources.Res
import org.northwindinteractive.project.resources.ic_lock
import org.northwindinteractive.project.resources.ic_shield_check

enum class HoldButtonMode { READY, SENDING, LOCKED, ACTIVE_ALERT }

/**
 * Large central alert affordance (spec: "Main alert": press and hold ~2 s with progress ring;
 * cancel if finger released early; repeated triggers deduplicated).
 *
 * Screen readers get a custom action that routes through [onAccessibilityRequest] (a confirmation
 * dialog) so the intentional-activation requirement still holds.
 */
@Composable
fun HoldToSendButton(
    mode: HoldButtonMode,
    onHoldComplete: () -> Unit,
    onHoldProgressTick: () -> Unit,
    onAccessibilityRequest: () -> Unit,
    onActiveAlertClick: () -> Unit,
    modifier: Modifier = Modifier,
    holdMillis: Int = 2_000,
    diameter: Dp = 208.dp,
) {
    val colors = AngelTheme.colors
    val reducedMotion = AngelTheme.reducedMotion
    val progress = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    val currentMode by rememberUpdatedState(mode)
    val complete by rememberUpdatedState(onHoldComplete)
    val tick by rememberUpdatedState(onHoldProgressTick)

    LaunchedEffect(mode) {
        if (mode != HoldButtonMode.READY) progress.snapTo(0f)
    }

    val pulse = if (!reducedMotion && mode == HoldButtonMode.READY) {
        val transition = rememberInfiniteTransition(label = "halo")
        transition.animateFloat(
            initialValue = 0.96f,
            targetValue = 1.04f,
            animationSpec = infiniteRepeatable(tween(1_600), RepeatMode.Reverse),
            label = "haloScale",
        ).value
    } else {
        1f
    }

    val core = when (mode) {
        HoldButtonMode.READY, HoldButtonMode.SENDING -> Brush.linearGradient(listOf(Color(0xFF7A78FF), colors.primary, Color(0xFF3F3CC9)))
        HoldButtonMode.ACTIVE_ALERT -> Brush.linearGradient(listOf(colors.danger, colors.danger))
        HoldButtonMode.LOCKED -> Brush.linearGradient(listOf(colors.contentMuted.copy(alpha = 0.55f), colors.contentMuted.copy(alpha = 0.4f)))
    }
    val haloColor = when (mode) {
        HoldButtonMode.ACTIVE_ALERT -> colors.danger
        HoldButtonMode.LOCKED -> colors.contentMuted
        else -> colors.primary
    }

    val (title, subtitle) = when (mode) {
        HoldButtonMode.READY -> "Send Alert" to "DISCREETLY"
        HoldButtonMode.SENDING -> "Sending…" to "PLEASE WAIT"
        HoldButtonMode.LOCKED -> "Alerts Locked" to "UNAVAILABLE"
        HoldButtonMode.ACTIVE_ALERT -> "Alert Active" to "VIEW STATUS"
    }
    val a11yLabel = when (mode) {
        HoldButtonMode.READY -> "Send discreet alert to venue security. Press and hold for 2 seconds."
        HoldButtonMode.SENDING -> "Sending alert"
        HoldButtonMode.LOCKED -> "Venue alerts are unavailable"
        HoldButtonMode.ACTIVE_ALERT -> "Your alert is active. Open alert status."
    }

    Box(
        modifier = modifier
            .size(diameter + 56.dp)
            .semantics {
                contentDescription = a11yLabel
                role = Role.Button
                if (progress.value > 0f) stateDescription = "${(progress.value * 100).toInt()} percent"
                if (mode == HoldButtonMode.LOCKED || mode == HoldButtonMode.SENDING) disabled()
                customActions = when (mode) {
                    HoldButtonMode.READY -> listOf(
                        CustomAccessibilityAction("Send discreet alert") { onAccessibilityRequest(); true },
                    )
                    HoldButtonMode.ACTIVE_ALERT -> listOf(
                        CustomAccessibilityAction("Open alert status") { onActiveAlertClick(); true },
                    )
                    else -> emptyList()
                }
            }
            .pointerInput(Unit) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    when (currentMode) {
                        HoldButtonMode.ACTIVE_ALERT -> {
                            if (waitForUpOrCancellation() != null) onActiveAlertClick()
                        }
                        HoldButtonMode.READY -> {
                            var job: Job? = null
                            job = scope.launch {
                                var lastQuarter = 0
                                progress.snapTo(0f)
                                progress.animateTo(1f, tween(holdMillis, easing = LinearEasing)) {
                                    val quarter = (value * 4).toInt()
                                    if (quarter > lastQuarter) {
                                        lastQuarter = quarter
                                        tick()
                                    }
                                }
                                complete()
                            }
                            waitForUpOrCancellation()
                            if (progress.value < 1f) {
                                job.cancel()
                                scope.launch { progress.animateTo(0f, tween(180)) }
                            }
                        }
                        else -> waitForUpOrCancellation()
                    }
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        // Halo rings
        Box(
            Modifier
                .size(diameter + 48.dp)
                .scale(pulse)
                .clip(CircleShape)
                .background(haloColor.copy(alpha = if (colors.isDark) 0.14f else 0.10f)),
        )
        Box(
            Modifier
                .size(diameter + 20.dp)
                .clip(CircleShape)
                .background(haloColor.copy(alpha = if (colors.isDark) 0.22f else 0.16f)),
        )
        // Progress ring
        val ringTrack = colors.card.copy(alpha = 0.35f)
        Canvas(Modifier.size(diameter + 12.dp)) {
            val stroke = 6.dp.toPx()
            val inset = stroke / 2
            val arcSize = Size(size.width - stroke, size.height - stroke)
            if (progress.value > 0f) {
                drawArc(
                    color = ringTrack,
                    startAngle = 0f,
                    sweepAngle = 360f,
                    useCenter = false,
                    topLeft = Offset(inset, inset),
                    size = arcSize,
                    style = Stroke(width = stroke),
                )
                drawArc(
                    color = haloColor,
                    startAngle = -90f,
                    sweepAngle = 360f * progress.value,
                    useCenter = false,
                    topLeft = Offset(inset, inset),
                    size = arcSize,
                    style = Stroke(width = stroke, cap = StrokeCap.Round),
                )
            }
        }
        Box(
            modifier = Modifier.size(diameter - 8.dp).clip(CircleShape).background(core),
            contentAlignment = Alignment.Center,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                if (mode == HoldButtonMode.SENDING) {
                    CircularProgressIndicator(color = colors.onPrimary, strokeWidth = 3.dp, modifier = Modifier.size(40.dp))
                } else {
                    Icon(
                        painterResource(if (mode == HoldButtonMode.LOCKED) Res.drawable.ic_lock else Res.drawable.ic_shield_check),
                        contentDescription = null,
                        tint = colors.onPrimary,
                        modifier = Modifier.size(44.dp),
                    )
                }
                Spacer(Modifier.height(10.dp))
                Text(
                    title,
                    color = colors.onPrimary,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    subtitle,
                    color = colors.onPrimary.copy(alpha = 0.85f),
                    style = MaterialTheme.typography.labelSmall,
                    fontSize = 11.sp,
                )
            }
        }
    }
}
