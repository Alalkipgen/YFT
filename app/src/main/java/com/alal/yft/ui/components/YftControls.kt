package com.alal.yft.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.alal.yft.ui.theme.YftIcons
import com.alal.yft.ui.theme.YftPalette
import com.alal.yft.ui.theme.YftShapes
import com.alal.yft.ui.theme.YftTheme

/** Mint switch with a white thumb that shows a check while on. */
@Composable
fun YftSwitch(
    checked: Boolean,
    onCheckedChange: ((Boolean) -> Unit)?,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val colors = YftTheme.colors
    Switch(
        checked = checked,
        onCheckedChange = onCheckedChange,
        modifier = modifier,
        enabled = enabled,
        thumbContent = if (checked) {
            {
                YftIcon(
                    icon = YftIcons.Check,
                    contentDescription = null,
                    tint = YftPalette.DeepTeal,
                    size = 16.dp,
                )
            }
        } else {
            null
        },
        colors = SwitchDefaults.colors(
            checkedThumbColor = Color.White,
            checkedTrackColor = colors.accent,
            checkedBorderColor = colors.accent,
            uncheckedThumbColor = MaterialTheme.colorScheme.outline,
            uncheckedTrackColor = colors.chipOnBackground,
            uncheckedBorderColor = MaterialTheme.colorScheme.outline,
        ),
    )
}

/** Rounded Mint progress bar on a soft track; `null` progress shows the indeterminate bar. */
@Composable
fun YftProgressBar(
    progress: Float?,
    modifier: Modifier = Modifier,
) {
    val colors = YftTheme.colors
    val track = if (colors.isDark) colors.chip else colors.border
    val barModifier = modifier
        .fillMaxWidth()
        .height(6.dp)
        .clip(YftShapes.pill)
    if (progress == null) {
        LinearProgressIndicator(
            modifier = barModifier,
            color = colors.accent,
            trackColor = track,
            strokeCap = StrokeCap.Round,
            gapSize = 0.dp,
        )
    } else {
        LinearProgressIndicator(
            progress = { progress.coerceIn(0f, 1f) },
            modifier = barModifier,
            color = colors.accent,
            trackColor = track,
            strokeCap = StrokeCap.Round,
            gapSize = 0.dp,
            drawStopIndicator = {},
        )
    }
}

/** − value + stepper, e.g. "Downloads at the same time". */
@Composable
fun YftStepper(
    value: Int,
    range: IntRange,
    onValueChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
    valueDescription: String = value.toString(),
    decrementLabel: String = "Decrease",
    incrementLabel: String = "Increase",
    testTagPrefix: String? = null,
) {
    val colors = YftTheme.colors
    Row(
        modifier = modifier
            .clip(YftShapes.pill)
            .background(colors.chip),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        YftIconButton(
            icon = YftIcons.Remove,
            contentDescription = decrementLabel,
            onClick = { onValueChange(value - 1) },
            enabled = value > range.first,
            modifier = testTagPrefix?.let { Modifier.testTag("$it-decrease") } ?: Modifier,
        )
        Text(
            text = value.toString(),
            modifier = Modifier
                .widthIn(min = 20.dp)
                .semantics { contentDescription = valueDescription },
            color = colors.textPrimary,
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center,
        )
        YftIconButton(
            icon = YftIcons.Add,
            contentDescription = incrementLabel,
            onClick = { onValueChange(value + 1) },
            enabled = value < range.last,
            modifier = testTagPrefix?.let { Modifier.testTag("$it-increase") } ?: Modifier,
        )
    }
}

/**
 * Round selection mark for list rows (quality choices): a filled Deep Teal (Mint on Night)
 * circle with a check when selected, an outline ring otherwise. Purely visual; the row owns the
 * radio semantics.
 */
@Composable
fun YftRadioMark(selected: Boolean, modifier: Modifier = Modifier) {
    val colors = YftTheme.colors
    Box(
        modifier = modifier
            .size(24.dp)
            .clearAndSetSemantics {}
            .then(
                if (selected) {
                    Modifier
                        .clip(CircleShape)
                        .background(colors.link)
                } else {
                    Modifier.border(2.dp, MaterialTheme.colorScheme.outline, CircleShape)
                },
            ),
        contentAlignment = Alignment.Center,
    ) {
        if (selected) {
            YftIcon(
                icon = YftIcons.Check,
                contentDescription = null,
                tint = if (colors.isDark) colors.onAccent else Color.White,
                size = 16.dp,
            )
        }
    }
}

/** Short bold inline label, e.g. a percentage beside a progress bar. */
@Composable
fun YftValueText(text: String, modifier: Modifier = Modifier, color: Color = YftTheme.colors.link) {
    Text(
        text = text,
        modifier = modifier.padding(start = 8.dp),
        color = color,
        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
    )
}
