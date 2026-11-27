package org.northwindinteractive.project.feature.venue

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.painterResource
import org.northwindinteractive.project.core.designsystem.AngelCard
import org.northwindinteractive.project.core.designsystem.AngelTheme
import org.northwindinteractive.project.core.designsystem.CaptionStyle
import org.northwindinteractive.project.core.designsystem.Radii
import org.northwindinteractive.project.core.designsystem.Spacing
import org.northwindinteractive.project.core.domain.model.VenueSummary
import org.northwindinteractive.project.resources.Res
import org.northwindinteractive.project.resources.ic_map_pin
import org.northwindinteractive.project.resources.ill_venue_lounge
import org.northwindinteractive.project.resources.ill_venue_map

/** Venue identity card: thumbnail, name, tagline and public address. */
@Composable
fun VenueIdentityCard(venue: VenueSummary, modifier: Modifier = Modifier) {
    AngelCard(modifier = modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Image(
                painterResource(Res.drawable.ill_venue_lounge),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.size(width = 76.dp, height = 64.dp).clip(Radii.chip),
            )
            Spacer(Modifier.width(Spacing.sm))
            Column(Modifier.weight(1f)) {
                Text(venue.displayName, style = MaterialTheme.typography.titleMedium, color = AngelTheme.colors.content)
                if (venue.tagline.isNotBlank()) {
                    Text(venue.tagline, style = MaterialTheme.typography.bodySmall, color = AngelTheme.colors.contentMuted)
                }
                Spacer(Modifier.height(Spacing.xxs))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        painterResource(Res.drawable.ic_map_pin),
                        contentDescription = null,
                        tint = AngelTheme.colors.primary,
                        modifier = Modifier.size(14.dp),
                    )
                    Spacer(Modifier.width(Spacing.xxs))
                    Text(venue.publicAddress, style = MaterialTheme.typography.bodySmall, color = AngelTheme.colors.contentMuted)
                }
            }
        }
    }
}

/**
 * Stylised geofence overview. Production rule from the spec: label it as a location *estimate*,
 * never an exact indoor position.
 */
@Composable
fun GeofenceMapCard(
    label: String?,
    caption: String,
    modifier: Modifier = Modifier,
    showMarker: Boolean = true,
    height: Int = 168,
) {
    val colors = AngelTheme.colors
    val pulse = if (!AngelTheme.reducedMotion && showMarker) {
        rememberInfiniteTransition(label = "geofence").animateFloat(
            initialValue = 0.92f,
            targetValue = 1.06f,
            animationSpec = infiniteRepeatable(tween(1_800), RepeatMode.Reverse),
            label = "geofencePulse",
        ).value
    } else {
        1f
    }
    Column(modifier = modifier.fillMaxWidth().semantics(mergeDescendants = true) { contentDescription = "$caption. ${label.orEmpty()}" }) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(height.dp)
                .clip(Radii.card)
                .border(1.dp, colors.cardBorder, Radii.card),
            contentAlignment = Alignment.Center,
        ) {
            Image(
                painterResource(Res.drawable.ill_venue_map),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
            Box(
                Modifier
                    .size(120.dp)
                    .scale(pulse)
                    .clip(CircleShape)
                    .background(colors.primary.copy(alpha = 0.18f))
                    .border(1.5.dp, colors.primary.copy(alpha = 0.6f), CircleShape),
            )
            if (showMarker) {
                Box(
                    Modifier
                        .size(18.dp)
                        .clip(CircleShape)
                        .background(colors.card)
                        .padding(3.dp)
                        .clip(CircleShape)
                        .background(colors.primary),
                )
            }
            if (label != null) {
                Text(
                    label,
                    style = MaterialTheme.typography.labelMedium,
                    color = colors.onPrimary,
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = Spacing.sm)
                        .clip(CircleShape)
                        .background(colors.primary)
                        .padding(horizontal = 10.dp, vertical = 4.dp),
                )
            }
        }
        Text(
            caption,
            style = CaptionStyle,
            color = colors.contentMuted,
            modifier = Modifier.padding(top = Spacing.xxs, start = Spacing.xxs),
        )
    }
}
