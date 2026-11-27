package org.northwindinteractive.project.core.designsystem

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource
import org.northwindinteractive.project.resources.Res
import org.northwindinteractive.project.resources.ic_arrow_back
import org.northwindinteractive.project.resources.ic_chevron_right
import org.northwindinteractive.project.resources.ic_info

@Composable
fun AngelCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    onClickLabel: String? = null,
    contentPadding: PaddingValues = PaddingValues(Spacing.md),
    borderColor: Color = AngelTheme.colors.cardBorder,
    containerColor: Color = AngelTheme.colors.card,
    content: @Composable ColumnScope.() -> Unit,
) {
    val clickable = if (onClick != null) {
        Modifier.clickable(onClickLabel = onClickLabel, role = Role.Button, onClick = onClick)
    } else {
        Modifier
    }
    Column(
        modifier = modifier
            .clip(Radii.card)
            .background(containerColor)
            .border(1.dp, borderColor, Radii.card)
            .then(clickable)
            .padding(contentPadding),
        content = content,
    )
}

@Composable
fun PrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    loading: Boolean = false,
    trailingIcon: DrawableResource? = null,
    containerColor: Color = AngelTheme.colors.primary,
) {
    Button(
        onClick = onClick,
        enabled = enabled && !loading,
        modifier = modifier.fillMaxWidth().heightIn(min = 52.dp),
        shape = Radii.button,
        colors = ButtonDefaults.buttonColors(
            containerColor = containerColor,
            contentColor = AngelTheme.colors.onPrimary,
            disabledContainerColor = containerColor.copy(alpha = 0.35f),
            disabledContentColor = AngelTheme.colors.onPrimary.copy(alpha = 0.8f),
        ),
    ) {
        if (loading) {
            CircularProgressIndicator(
                modifier = Modifier.size(20.dp),
                strokeWidth = 2.dp,
                color = AngelTheme.colors.onPrimary,
            )
            Spacer(Modifier.width(Spacing.xs))
        }
        Text(text, style = MaterialTheme.typography.labelLarge)
        if (trailingIcon != null && !loading) {
            Spacer(Modifier.width(Spacing.xs))
            Icon(painterResource(trailingIcon), contentDescription = null, modifier = Modifier.size(20.dp))
        }
    }
}

@Composable
fun SecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    contentColor: Color = AngelTheme.colors.primary,
    borderColor: Color = AngelTheme.colors.cardBorder,
) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.fillMaxWidth().heightIn(min = 52.dp),
        shape = Radii.button,
        border = BorderStroke(1.dp, borderColor),
        colors = ButtonDefaults.outlinedButtonColors(
            containerColor = AngelTheme.colors.card,
            contentColor = contentColor,
        ),
    ) {
        Text(text, style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
fun AngelTextButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    TextButton(onClick = onClick, modifier = modifier.heightIn(min = Spacing.minTouch)) {
        Text(text, color = AngelTheme.colors.primary, style = MaterialTheme.typography.labelLarge)
    }
}

/** Center-titled top bar with optional back navigation, as in the mockups. */
@Composable
fun AngelTopBar(
    title: String,
    onBack: (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .padding(horizontal = Spacing.xxs),
    ) {
        if (onBack != null) {
            IconButton(onClick = onBack, modifier = Modifier.align(Alignment.CenterStart)) {
                Icon(
                    painterResource(Res.drawable.ic_arrow_back),
                    contentDescription = "Back",
                    tint = AngelTheme.colors.content,
                )
            }
        }
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = AngelTheme.colors.content,
            modifier = Modifier.align(Alignment.Center).semantics { heading() },
        )
        Row(modifier = Modifier.align(Alignment.CenterEnd), content = actions)
    }
}

enum class Tone { NEUTRAL, PRIMARY, SUCCESS, WARNING, DANGER }

@Composable
fun Tone.foreground(): Color = when (this) {
    Tone.NEUTRAL -> AngelTheme.colors.contentMuted
    Tone.PRIMARY -> AngelTheme.colors.primary
    Tone.SUCCESS -> AngelTheme.colors.success
    Tone.WARNING -> AngelTheme.colors.warning
    Tone.DANGER -> AngelTheme.colors.danger
}

@Composable
fun Tone.background(): Color = when (this) {
    Tone.NEUTRAL -> AngelTheme.colors.canvas
    Tone.PRIMARY -> AngelTheme.colors.primarySoft
    Tone.SUCCESS -> AngelTheme.colors.successSoft
    Tone.WARNING -> AngelTheme.colors.warningSoft
    Tone.DANGER -> AngelTheme.colors.dangerSoft
}

/** Status banner: icon + text, never color alone (e.g. "Inside venue · Registration active"). */
@Composable
fun StatusBanner(
    title: String,
    subtitle: String?,
    icon: DrawableResource,
    tone: Tone,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
) {
    val fg = tone.foreground()
    val clickable = if (onClick != null) Modifier.clickable(role = Role.Button, onClick = onClick) else Modifier
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(Radii.card)
            .background(tone.background())
            .border(1.dp, fg.copy(alpha = 0.25f), Radii.card)
            .then(clickable)
            .heightIn(min = 64.dp)
            .padding(horizontal = Spacing.md, vertical = Spacing.sm)
            .semantics(mergeDescendants = true) {},
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier.size(36.dp).clip(CircleShape).background(fg),
            contentAlignment = Alignment.Center,
        ) {
            Icon(painterResource(icon), contentDescription = null, tint = tone.background(), modifier = Modifier.size(20.dp))
        }
        Spacer(Modifier.width(Spacing.sm))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall, color = fg)
            if (subtitle != null) {
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = AngelTheme.colors.contentMuted)
            }
        }
        if (onClick != null) {
            Icon(
                painterResource(Res.drawable.ic_chevron_right),
                contentDescription = null,
                tint = AngelTheme.colors.contentMuted,
            )
        }
    }
}

@Composable
fun StatusPill(text: String, tone: Tone, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.SemiBold,
        color = tone.foreground(),
        modifier = modifier
            .clip(CircleShape)
            .background(tone.background())
            .padding(horizontal = 10.dp, vertical = 4.dp),
    )
}

/** Pale indigo note with an info icon, used for policy and privacy explanations. */
@Composable
fun InfoNote(text: String, modifier: Modifier = Modifier, tone: Tone = Tone.PRIMARY) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(Radii.chip)
            .background(if (tone == Tone.PRIMARY) AngelTheme.colors.infoSoft else tone.background())
            .padding(Spacing.sm),
        verticalAlignment = Alignment.Top,
    ) {
        Icon(
            painterResource(Res.drawable.ic_info),
            contentDescription = null,
            tint = if (tone == Tone.PRIMARY) AngelTheme.colors.info else tone.foreground(),
            modifier = Modifier.size(20.dp),
        )
        Spacer(Modifier.width(Spacing.xs))
        Text(text, style = MaterialTheme.typography.bodySmall, color = AngelTheme.colors.content)
    }
}

/** Small tinted circle with an icon (used in feature lists and info cards). */
@Composable
fun IconBadge(icon: DrawableResource, tone: Tone = Tone.PRIMARY, size: Int = 40) {
    Box(
        modifier = Modifier.size(size.dp).clip(CircleShape).background(tone.background()),
        contentAlignment = Alignment.Center,
    ) {
        Icon(painterResource(icon), contentDescription = null, tint = tone.foreground(), modifier = Modifier.size((size * 0.5).dp))
    }
}

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier, action: (@Composable () -> Unit)? = null) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text,
            style = MaterialTheme.typography.titleSmall,
            color = AngelTheme.colors.content,
            modifier = Modifier.semantics { heading() },
        )
        action?.invoke()
    }
}

/** Row used in settings and detail cards: icon, title, optional subtitle and trailing content. */
@Composable
fun ListRow(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    icon: DrawableResource? = null,
    tone: Tone = Tone.PRIMARY,
    onClick: (() -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    val clickable = if (onClick != null) Modifier.clickable(role = Role.Button, onClick = onClick) else Modifier
    Row(
        modifier = modifier
            .fillMaxWidth()
            .then(clickable)
            .sizeIn(minHeight = 56.dp)
            .padding(horizontal = Spacing.md, vertical = Spacing.sm)
            .semantics(mergeDescendants = true) {},
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            IconBadge(icon, tone, size = 36)
            Spacer(Modifier.width(Spacing.sm))
        }
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, color = AngelTheme.colors.content)
            if (subtitle != null) {
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = AngelTheme.colors.contentMuted)
            }
        }
        if (trailing != null) {
            trailing()
        } else if (onClick != null) {
            Icon(painterResource(Res.drawable.ic_chevron_right), contentDescription = null, tint = AngelTheme.colors.contentMuted)
        }
    }
}

/** Full-screen centered empty / locked state (outside coverage, location unknown, expired...). */
@Composable
fun StateMessage(
    illustration: DrawableResource,
    title: String,
    body: String,
    modifier: Modifier = Modifier,
    illustrationTint: Color? = null,
) {
    Column(
        modifier = modifier.fillMaxWidth().padding(vertical = Spacing.lg),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (illustrationTint != null) {
            Icon(painterResource(illustration), contentDescription = null, tint = illustrationTint, modifier = Modifier.size(96.dp))
        } else {
            androidx.compose.foundation.Image(painterResource(illustration), contentDescription = null, modifier = Modifier.size(120.dp))
        }
        Spacer(Modifier.height(Spacing.md))
        Text(
            title,
            style = MaterialTheme.typography.headlineSmall,
            color = AngelTheme.colors.content,
            textAlign = TextAlign.Center,
            modifier = Modifier.semantics { heading() },
        )
        Spacer(Modifier.height(Spacing.xs))
        Text(
            body,
            style = MaterialTheme.typography.bodyMedium,
            color = AngelTheme.colors.contentMuted,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
fun Divider() {
    Box(Modifier.fillMaxWidth().height(1.dp).background(AngelTheme.colors.divider))
}
