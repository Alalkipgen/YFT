package com.alal.yft.ui.components

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints

/**
 * v1.0.2 fix batch after #149: one line of text that is drawn smaller when it does not fit its
 * room in large text (the phone's font size), down to [minScale] of [style]'s size; only below
 * that does it end in an ellipsis. Compose's own auto-size text comes with a later Compose than
 * the app's, so the size is measured here once per text, style and room.
 */
@Composable
internal fun YftFitText(
    text: String,
    style: TextStyle,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
    textAlign: TextAlign? = null,
    minScale: Float = FIT_MIN_SCALE,
) {
    val measurer = rememberTextMeasurer()
    BoxWithConstraints(modifier = modifier) {
        val room = constraints.maxWidth
        val fitted = remember(text, style, room, minScale, measurer) {
            fittedStyle(measurer, text, style, room, minScale)
        }
        Text(
            text = text,
            color = color,
            style = fitted,
            textAlign = textAlign,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * [style], or a smaller copy whose one line of [text] is at most [room] pixels wide; never
 * smaller than [minScale] of [style]'s font size. A style without a size in sp is kept.
 */
internal fun fittedStyle(
    measurer: TextMeasurer,
    text: String,
    style: TextStyle,
    room: Int,
    minScale: Float,
): TextStyle {
    if (room == Constraints.Infinity || room <= 0 || !style.fontSize.isSp) return style
    var scale = 1f
    var current = style
    repeat(FIT_STEPS) {
        val width = measurer.measure(text, current, softWrap = false, maxLines = 1).size.width
        if (width <= room || scale <= minScale) return current
        // The width follows the size closely; a little under it, for the rounding of glyphs.
        scale = (scale * room / width * FIT_MARGIN).coerceAtLeast(minScale)
        current = style.copy(fontSize = style.fontSize * scale)
    }
    return current
}

private const val FIT_MIN_SCALE = 0.75f
private const val FIT_STEPS = 4
private const val FIT_MARGIN = 0.98f
