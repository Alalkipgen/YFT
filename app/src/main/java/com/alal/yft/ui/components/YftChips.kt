package com.alal.yft.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.alal.yft.ui.theme.YftShapes
import com.alal.yft.ui.theme.YftTheme

/**
 * Mutually exclusive filter such as All / Active / Queued / Done. Selected chips are Mint with a
 * bold label, so the state never relies on color alone; an optional [count] sits in a bubble.
 */
@Composable
fun YftFilterChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    count: Int? = null,
    unselectedColor: Color = YftTheme.colors.chipOnBackground,
) {
    val colors = YftTheme.colors
    Row(
        modifier = modifier
            .minimumInteractiveComponentSize()
            .heightIn(min = 34.dp)
            .clip(YftShapes.pill)
            .background(if (selected) colors.accent else unselectedColor)
            .selectable(selected = selected, role = Role.Tab, onClick = onClick)
            .padding(horizontal = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            color = if (selected) colors.onAccent else colors.textPrimary,
            style = MaterialTheme.typography.labelLarge.copy(
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            ),
            maxLines = 1,
        )
        if (count != null) {
            Box(
                modifier = Modifier
                    .defaultMinSize(minWidth = 18.dp, minHeight = 18.dp)
                    .clip(YftShapes.pill)
                    .background(colors.textPrimary.copy(alpha = 0.08f))
                    .padding(horizontal = 5.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = count.toString(),
                    color = if (selected) colors.onAccent else colors.textPrimary,
                    style = MaterialTheme.typography.labelSmall,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

enum class YftStatusTone { Active, Waiting, Failed, Neutral }

/** Small status pill: Downloading (Mint), Waiting for Wi-Fi and Queued (grey), Failed (Coral). */
@Composable
fun YftStatusChip(
    text: String,
    tone: YftStatusTone,
    modifier: Modifier = Modifier,
    @DrawableRes icon: Int? = null,
) {
    val colors = YftTheme.colors
    val (container, content) = when (tone) {
        YftStatusTone.Active -> colors.accent to colors.onAccent
        YftStatusTone.Waiting -> colors.chipOnBackground to colors.textPrimary
        YftStatusTone.Failed -> colors.coral to colors.onCoral
        YftStatusTone.Neutral -> colors.chipOnBackground to colors.textPrimary
    }
    Row(
        modifier = modifier
            .heightIn(min = 24.dp)
            .clip(YftShapes.pill)
            .background(container)
            .padding(horizontal = 10.dp, vertical = 3.dp),
        horizontalArrangement = Arrangement.spacedBy(5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            YftIcon(icon = icon, contentDescription = null, tint = content, size = 14.dp)
        }
        Text(
            text = text,
            color = content,
            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Medium),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** Small read-only fact such as MP4, 1080p or 186 MB. */
@Composable
fun YftMetaChip(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        modifier = modifier
            .clip(YftShapes.pill)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(horizontal = 10.dp, vertical = 4.dp),
        color = YftTheme.colors.textPrimary,
        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Medium),
        maxLines = 1,
    )
}

/** Outlined read-only label such as the "No ads" shield chip. */
@Composable
fun YftOutlinedChip(
    text: String,
    modifier: Modifier = Modifier,
    @DrawableRes icon: Int? = null,
) {
    val color = YftTheme.colors.link
    Row(
        modifier = modifier
            .heightIn(min = 30.dp)
            .widthIn(min = 48.dp)
            .border(1.dp, color, YftShapes.pill)
            .padding(horizontal = 12.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            YftIcon(icon = icon, contentDescription = null, tint = color, size = 16.dp)
        }
        Text(text = text, color = color, style = MaterialTheme.typography.labelLarge)
    }
}

/** Coral count bubble with Ink digits; nothing is drawn for zero. */
@Composable
fun YftCountBadge(count: Int, modifier: Modifier = Modifier, minSize: Dp = 20.dp) {
    if (count <= 0) return
    val colors = YftTheme.colors
    Box(
        modifier = modifier
            .defaultMinSize(minWidth = minSize, minHeight = minSize)
            .clip(YftShapes.pill)
            .background(colors.coral)
            .padding(horizontal = 5.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = if (count > MAX_BADGE_COUNT) "$MAX_BADGE_COUNT+" else count.toString(),
            color = colors.onCoral,
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
            textAlign = TextAlign.Center,
        )
    }
}

private const val MAX_BADGE_COUNT = 99
