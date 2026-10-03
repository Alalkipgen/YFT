package com.alal.yft.ui.components

import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.alal.yft.ui.theme.YftTheme

/**
 * A 3dp Mint progress line in a taller touch strip that seeks on tap or at the end of a drag,
 * with a progress action for TalkBack. [handle] adds the round thumb drawn in the mini player;
 * [lineAtBottom] puts the line on the strip's lower edge, as over a video frame.
 */
@Composable
fun YftSeekBar(
    fraction: Float,
    onSeek: (Float) -> Unit,
    contentDescription: String,
    modifier: Modifier = Modifier,
    trackColor: Color = YftTheme.colors.border,
    handle: Boolean = false,
    lineAtBottom: Boolean = false,
    height: Dp = 24.dp,
) {
    val currentOnSeek by rememberUpdatedState(onSeek)
    var scrub by remember { mutableStateOf<Float?>(null) }
    val shown = scrub ?: fraction.coerceIn(0f, 1f)
    val accent = YftTheme.colors.accent
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .pointerInput(Unit) {
                detectTapGestures { offset -> currentOnSeek(fractionOf(offset.x, size.width)) }
            }
            .pointerInput(Unit) {
                detectHorizontalDragGestures(
                    onDragStart = { offset -> scrub = fractionOf(offset.x, size.width) },
                    onDragEnd = {
                        scrub?.let(currentOnSeek)
                        scrub = null
                    },
                    onDragCancel = { scrub = null },
                ) { change, _ ->
                    scrub = fractionOf(change.position.x, size.width)
                }
            }
            .semantics(mergeDescendants = true) {
                this.contentDescription = contentDescription
                progressBarRangeInfo = ProgressBarRangeInfo(shown, 0f..1f)
                setProgress { target ->
                    currentOnSeek(target.coerceIn(0f, 1f))
                    true
                }
            }
            .drawBehind {
                val stroke = LINE_WIDTH.toPx()
                val radius = HANDLE_RADIUS.toPx()
                val inset = if (handle) radius else 0f
                val y = if (lineAtBottom) size.height - stroke / 2 else size.height / 2
                val start = Offset(inset, y)
                val end = Offset(size.width - inset, y)
                val at = Offset(inset + (size.width - 2 * inset) * shown, y)
                val cap = if (lineAtBottom) StrokeCap.Butt else StrokeCap.Round
                drawLine(trackColor, start, end, strokeWidth = stroke, cap = cap)
                if (shown > 0f) drawLine(accent, start, at, strokeWidth = stroke, cap = cap)
                if (handle) drawCircle(accent, radius = radius, center = at)
            },
    )
}

private fun fractionOf(x: Float, width: Int): Float =
    if (width <= 0) 0f else (x / width).coerceIn(0f, 1f)

private val LINE_WIDTH = 3.dp
private val HANDLE_RADIUS = 5.dp
