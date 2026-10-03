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
import androidx.compose.ui.graphics.Color
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
 * segments, such as Audio when a page has no separate audio track.
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
) {
    val colors = YftTheme.colors
    Row(
        modifier = modifier
            .clip(YftShapes.pill)
            .background(containerColor)
            .border(1.dp, colors.border, YftShapes.pill)
            .padding(horizontal = 2.dp)
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
                    .widthIn(min = 64.dp)
                    .selectable(
                        selected = isSelected,
                        enabled = optionEnabled,
                        role = Role.RadioButton,
                        onClick = { onSelect(option) },
                    )
                    .then(if (testTag != null) Modifier.testTag(testTag(option)) else Modifier)
                    .padding(vertical = 4.dp)
                    .clip(YftShapes.pill)
                    .background(if (isSelected) colors.accent else Color.Transparent)
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = label(option),
                    color = when {
                        isSelected -> colors.onAccent
                        optionEnabled -> colors.textPrimary
                        else -> colors.textSecondary
                    },
                    style = MaterialTheme.typography.labelLarge.copy(
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                        fontSize = MaterialTheme.typography.bodyLarge.fontSize,
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}
