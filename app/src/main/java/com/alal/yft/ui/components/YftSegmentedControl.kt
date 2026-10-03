package com.alal.yft.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.alal.yft.ui.theme.YftShapes
import com.alal.yft.ui.theme.YftTheme

/**
 * Pill-shaped single choice (Video / Audio, System / Light / Dark). The selected segment is Mint
 * with a bold Ink label; each segment is a 48dp radio button for accessibility services.
 *
 * With [fillWidth] the segments share the available width equally; otherwise each one wraps its
 * label, which suits a trailing control inside a settings row. [isEnabled] greys out single
 * segments, such as Audio when a page has no separate audio track. [compact] is the settings-row
 * size: 14sp labels and a 36dp pill drawn inside the 48dp touch targets, with the Mint segment
 * filling the pill's height as in `06`.
 */
@Composable
fun <T> YftSegmentedControl(
    options: List<T>,
    selected: T,
    onSelect: (T) -> Unit,
    label: (T) -> String,
    modifier: Modifier = Modifier,
    fillWidth: Boolean = true,
    enabled: Boolean = true,
    containerColor: Color = YftTheme.colors.chip,
    testTag: ((T) -> String)? = null,
    isEnabled: (T) -> Boolean = { true },
    compact: Boolean = false,
) {
    val colors = YftTheme.colors
    val border = colors.border
    val container = if (compact) {
        Modifier.drawBehind {
            val inset = COMPACT_INSET.toPx()
            val stroke = 1.dp.toPx()
            val height = size.height - inset * 2
            drawRoundRect(
                color = containerColor,
                topLeft = Offset(0f, inset),
                size = Size(size.width, height),
                cornerRadius = CornerRadius(height / 2),
            )
            drawRoundRect(
                color = border,
                topLeft = Offset(stroke / 2, inset + stroke / 2),
                size = Size(size.width - stroke, height - stroke),
                cornerRadius = CornerRadius((height - stroke) / 2),
                style = Stroke(width = stroke),
            )
        }
    } else {
        Modifier
            .clip(YftShapes.pill)
            .background(containerColor)
            .border(1.dp, border, YftShapes.pill)
            .padding(horizontal = 2.dp)
    }
    val labelStyle = MaterialTheme.typography.labelLarge
    val fontSize = if (compact) labelStyle.fontSize else MaterialTheme.typography.bodyLarge.fontSize
    Row(
        modifier = modifier
            .then(container)
            .selectableGroup(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        options.forEach { option ->
            val isSelected = option == selected
            val optionEnabled = enabled && isEnabled(option)
            // The whole 48dp cell is the touch target; the Mint pill is drawn 4dp inside it.
            Box(
                modifier = Modifier
                    .then(if (fillWidth) Modifier.weight(1f) else Modifier)
                    .heightIn(min = 48.dp)
                    .widthIn(min = if (compact) 56.dp else 64.dp)
                    .selectable(
                        selected = isSelected,
                        enabled = optionEnabled,
                        role = Role.RadioButton,
                        onClick = { onSelect(option) },
                    )
                    .then(if (testTag != null) Modifier.testTag(testTag(option)) else Modifier)
                    .padding(vertical = if (compact) COMPACT_INSET else 4.dp)
                    .clip(YftShapes.pill)
                    .background(if (isSelected) colors.accent else Color.Transparent)
                    .padding(horizontal = if (compact) 14.dp else 16.dp, vertical = 8.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = label(option),
                    color = when {
                        isSelected -> colors.onAccent
                        optionEnabled -> colors.textPrimary
                        else -> colors.textSecondary
                    },
                    style = labelStyle.copy(
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                        fontSize = fontSize,
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

private val COMPACT_INSET = 6.dp
