package com.alal.yft.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.alal.yft.ui.theme.YftShapes
import com.alal.yft.ui.theme.YftTheme

/** White (Night card) surface with a 1dp border and no shadow, the YFT card. */
@Composable
fun YftCard(
    modifier: Modifier = Modifier,
    shape: Shape = YftShapes.card,
    onClick: (() -> Unit)? = null,
    contentPadding: PaddingValues = PaddingValues(16.dp),
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = YftTheme.colors
    val border = BorderStroke(1.dp, colors.border)
    val body: @Composable () -> Unit = {
        Column(modifier = Modifier.padding(contentPadding), content = content)
    }
    if (onClick == null) {
        Surface(
            modifier = modifier,
            shape = shape,
            color = colors.card,
            contentColor = colors.textPrimary,
            border = border,
            content = body,
        )
    } else {
        Surface(
            onClick = onClick,
            modifier = modifier,
            shape = shape,
            color = colors.card,
            contentColor = colors.textPrimary,
            border = border,
            content = body,
        )
    }
}

/**
 * Big Display 32 Bold screen title (Downloads, Library, Settings) with trailing actions. The
 * default end padding suits 48dp icon buttons; pills such as Pause all pass a wider one so their
 * edge lines up with the cards below.
 */
@Composable
fun YftScreenHeader(
    title: String,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues =
        PaddingValues(start = 16.dp, top = 12.dp, end = 8.dp, bottom = 4.dp),
    actions: @Composable RowScope.() -> Unit = {},
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .padding(contentPadding),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            modifier = Modifier
                .weight(1f)
                .semantics { heading() },
            color = YftTheme.colors.textPrimary,
            style = MaterialTheme.typography.headlineMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Row(
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically,
            content = actions,
        )
    }
}

/** Title 22 SemiBold section heading with an optional link action such as Edit or See all. */
@Composable
fun YftSectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
    actionTestTag: String? = null,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            modifier = Modifier
                .weight(1f)
                .semantics { heading() },
            color = YftTheme.colors.textPrimary,
            style = MaterialTheme.typography.titleLarge,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (actionLabel != null && onAction != null) {
            YftTextButton(
                text = actionLabel,
                onClick = onAction,
                modifier = if (actionTestTag != null) {
                    Modifier.testTag(actionTestTag)
                } else {
                    Modifier
                },
            )
        }
    }
}

/** Uppercase group label above a settings card (APPEARANCE, DOWNLOADS…). */
@Composable
fun YftGroupLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text.uppercase(),
        modifier = modifier
            .padding(start = 8.dp, top = 16.dp, bottom = 8.dp)
            .semantics { heading() },
        color = YftTheme.colors.textSecondary,
        style = MaterialTheme.typography.labelLarge,
    )
}

@Composable
fun YftDivider(modifier: Modifier = Modifier) {
    HorizontalDivider(modifier = modifier, thickness = 1.dp, color = YftTheme.colors.border)
}

/** Drag handle drawn at the top of YFT bottom sheets. */
@Composable
fun YftSheetHandle(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size(width = 36.dp, height = 4.dp)
                .clip(YftShapes.pill)
                .background(YftTheme.colors.textSecondary.copy(alpha = 0.4f)),
        )
    }
}
