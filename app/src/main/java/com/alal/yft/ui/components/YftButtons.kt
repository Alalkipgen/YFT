package com.alal.yft.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.alal.yft.ui.theme.YftShapes
import com.alal.yft.ui.theme.YftTheme

/** A Material Symbols drawable, tinted with the current content color by default. */
@Composable
fun YftIcon(
    @DrawableRes icon: Int,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    tint: Color = LocalContentColor.current,
    size: Dp = 24.dp,
) {
    Icon(
        painter = painterResource(icon),
        contentDescription = contentDescription,
        modifier = modifier.size(size),
        tint = tint,
    )
}

/** Mint pill with Ink text: Download, Use, View, Preview. [large] is the 56dp sheet action. */
@Composable
fun YftPrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    @DrawableRes icon: Int? = null,
    enabled: Boolean = true,
    large: Boolean = false,
    compact: Boolean = false,
) {
    val colors = YftTheme.colors
    Button(
        onClick = onClick,
        modifier = modifier.heightIn(min = if (large) 56.dp else if (compact) 36.dp else 48.dp),
        enabled = enabled,
        shape = YftShapes.pill,
        colors = ButtonDefaults.buttonColors(
            containerColor = colors.accent,
            contentColor = colors.onAccent,
            disabledContainerColor = colors.chipOnBackground,
            disabledContentColor = colors.textSecondary,
        ),
        contentPadding = PaddingValues(
            horizontal = if (compact) 16.dp else 24.dp,
            vertical = if (compact) 6.dp else 10.dp,
        ),
    ) {
        ButtonContent(text = text, icon = icon, large = large)
    }
}

/** Outlined pill such as "Pause all". */
@Composable
fun YftOutlinedButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    @DrawableRes icon: Int? = null,
    enabled: Boolean = true,
) {
    val colors = YftTheme.colors
    OutlinedButton(
        onClick = onClick,
        modifier = modifier.heightIn(min = 48.dp),
        enabled = enabled,
        shape = YftShapes.pill,
        border = BorderStroke(1.dp, if (enabled) colors.link else colors.border),
        colors = ButtonDefaults.outlinedButtonColors(
            contentColor = colors.link,
            disabledContentColor = colors.textSecondary,
        ),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 10.dp),
    ) {
        ButtonContent(text = text, icon = icon, large = false)
    }
}

/** Tonal pill on a card, such as Paste and Open browser. */
@Composable
fun YftTonalButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    @DrawableRes icon: Int? = null,
    enabled: Boolean = true,
    containerColor: Color = YftTheme.colors.chip,
    compact: Boolean = false,
) {
    val colors = YftTheme.colors
    Button(
        onClick = onClick,
        // Compact is the Promptbox chip size: a 36dp pill inside a 48dp touch target.
        modifier = if (compact) {
            modifier.minimumInteractiveComponentSize().height(36.dp)
        } else {
            modifier.heightIn(min = 48.dp)
        },
        enabled = enabled,
        shape = YftShapes.pill,
        colors = ButtonDefaults.buttonColors(
            containerColor = containerColor,
            contentColor = colors.textPrimary,
            disabledContainerColor = containerColor,
            disabledContentColor = colors.textSecondary,
        ),
        elevation = null,
        contentPadding = if (compact) {
            PaddingValues(start = 12.dp, end = 14.dp)
        } else {
            PaddingValues(horizontal = 18.dp, vertical = 10.dp)
        },
    ) {
        ButtonContent(
            text = text,
            icon = icon,
            large = false,
            weight = FontWeight.Medium,
            iconSize = if (compact) 18.dp else 20.dp,
            gap = if (compact) 6.dp else 8.dp,
            iconTint = if (enabled) colors.icon else colors.textSecondary,
        )
    }
}

/** Deep Teal (Mint on Night) text action: Edit, See all, Retry, Open in browser. */
@Composable
fun YftTextButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    underline: Boolean = false,
    color: Color = YftTheme.colors.link,
) {
    TextButton(
        onClick = onClick,
        modifier = modifier.heightIn(min = 48.dp),
        enabled = enabled,
        shape = YftShapes.pill,
        colors = ButtonDefaults.textButtonColors(contentColor = color),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge.copy(
                fontWeight = FontWeight.SemiBold,
                textDecoration = if (underline) TextDecoration.Underline else null,
            ),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** Round icon action, Mint by default: the Promptbox arrow and the Downloads pause/play. */
@Composable
fun YftCircleButton(
    @DrawableRes icon: Int,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 48.dp,
    enabled: Boolean = true,
    containerColor: Color = YftTheme.colors.accent,
    contentColor: Color = YftTheme.colors.onAccent,
) {
    Box(
        modifier = modifier
            .minimumInteractiveComponentSize()
            .size(size)
            .clip(CircleShape)
            .background(containerColor)
            .alpha(if (enabled) 1f else DISABLED_ALPHA)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .semantics { this.contentDescription = contentDescription },
        contentAlignment = Alignment.Center,
    ) {
        YftIcon(icon = icon, contentDescription = null, tint = contentColor)
    }
}

/** Plain 48dp icon button tinted with the primary text color unless [tint] is given. */
@Composable
fun YftIconButton(
    @DrawableRes icon: Int,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    tint: Color = YftTheme.colors.textPrimary,
) {
    IconButton(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        colors = IconButtonDefaults.iconButtonColors(
            contentColor = tint,
            disabledContentColor = tint.copy(alpha = DISABLED_ALPHA),
        ),
    ) {
        YftIcon(icon = icon, contentDescription = contentDescription)
    }
}

@Composable
private fun ButtonContent(
    text: String,
    @DrawableRes icon: Int?,
    large: Boolean,
    weight: FontWeight = FontWeight.SemiBold,
    iconSize: Dp = 20.dp,
    gap: Dp = 8.dp,
    iconTint: Color = LocalContentColor.current,
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(gap),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            YftIcon(icon = icon, contentDescription = null, tint = iconTint, size = iconSize)
        }
        Text(
            text = text,
            style = if (large) {
                MaterialTheme.typography.titleMedium
            } else {
                MaterialTheme.typography.labelLarge.copy(fontWeight = weight)
            },
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

internal const val DISABLED_ALPHA = 0.38f
