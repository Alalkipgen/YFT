package com.alal.yft.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.alal.yft.ui.theme.YftIcons
import com.alal.yft.ui.theme.YftShapes
import com.alal.yft.ui.theme.YftTheme

enum class YftMediaKind { Video, Audio }

/**
 * Media tile: the real [image] when one is available, otherwise a Mint/Deep Teal (audio) or
 * dark teal (video) gradient with a white glyph. [muted] swaps the gradient for the quiet chip
 * tile with a Slate glyph, as the audio row of `02` shows; [glyph] overrides the default
 * note/film icon. An optional duration badge sits bottom-left. The tile is decorative; the
 * title next to it carries the meaning for accessibility services.
 */
@Composable
fun YftThumbnail(
    image: ImageBitmap?,
    kind: YftMediaKind,
    modifier: Modifier = Modifier,
    shape: Shape = YftShapes.thumbnail,
    durationLabel: String? = null,
    iconSize: Dp = 32.dp,
    @DrawableRes glyph: Int? = null,
    muted: Boolean = false,
) {
    val colors = YftTheme.colors
    val gradient = when {
        muted -> listOf(colors.chip, colors.chip)
        kind == YftMediaKind.Audio -> listOf(colors.audioTileStart, colors.audioTileEnd)
        else -> listOf(colors.videoTileStart, colors.videoTileEnd)
    }
    Box(
        modifier = modifier
            .clip(shape)
            .background(Brush.linearGradient(gradient)),
        contentAlignment = Alignment.Center,
    ) {
        if (image != null) {
            Image(
                bitmap = image,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
        } else {
            val fallback = if (kind == YftMediaKind.Audio) YftIcons.MusicNote else YftIcons.Movie
            YftIcon(
                icon = glyph ?: fallback,
                contentDescription = null,
                tint = if (muted) colors.textSecondary else colors.onScrim.copy(alpha = 0.92f),
                size = iconSize,
            )
        }
        if (durationLabel != null) {
            Text(
                text = durationLabel,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(8.dp)
                    .clip(YftShapes.badge)
                    .background(colors.scrim)
                    .padding(horizontal = 8.dp, vertical = 3.dp),
                color = colors.onScrim,
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
            )
        }
    }
}
