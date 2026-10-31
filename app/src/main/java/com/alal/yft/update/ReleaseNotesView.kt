package com.alal.yft.update

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.alal.yft.ui.theme.YftTheme

/**
 * The release notes on the Updates screen (§4.5, v1.0.2), as [ReleaseNotes.blocks]: headings,
 * paragraphs, list items with their marker in a hanging column, and code on a card-coloured
 * block. Bold, italic and code inside a block are AnnotatedString spans. Nothing here can be
 * tapped: a link shows its text only (U3).
 */
@Composable
internal fun ReleaseNotesView(notes: String, modifier: Modifier = Modifier) {
    val colors = YftTheme.colors
    val blocks = remember(notes) { ReleaseNotes.blocks(notes) }
    if (blocks.isEmpty()) {
        Text(
            text = NO_NOTES,
            modifier = modifier,
            color = colors.textPrimary,
            style = MaterialTheme.typography.bodyMedium,
        )
        return
    }
    Column(modifier = modifier.fillMaxWidth()) {
        blocks.forEachIndexed { index, block ->
            val gap = if (index == 0) 0.dp else gapBefore(blocks[index - 1], block)
            NoteBlockView(block, Modifier.padding(top = gap))
        }
    }
}

@Composable
private fun NoteBlockView(block: NoteBlock, modifier: Modifier) {
    val colors = YftTheme.colors
    val typography = MaterialTheme.typography
    val text = remember(block.text) { block.text.annotated() }
    when (block.kind) {
        NoteKind.HEADING -> Text(
            text = text,
            modifier = modifier.semantics { heading() },
            color = colors.textPrimary,
            // v1.0.2 fix N2: a size up for each level, all bold, so a heading stands out.
            style = headingStyle(block.level, typography),
        )
        NoteKind.PARAGRAPH -> Text(
            text = text,
            modifier = modifier,
            color = colors.textPrimary,
            style = typography.bodyMedium,
        )
        NoteKind.ITEM -> Row(
            modifier = modifier.padding(start = INDENT * block.depth.atLeastZero()),
        ) {
            val marker = block.marker
            if (marker == null) {
                // An item's later paragraph lines up with the item's text.
                Spacer(modifier = Modifier.width(INDENT))
            } else {
                Text(
                    text = marker,
                    modifier = Modifier.widthIn(min = INDENT).padding(end = 4.dp),
                    color = colors.textPrimary,
                    style = typography.bodyMedium,
                )
            }
            Text(
                text = text,
                modifier = Modifier.weight(1f),
                color = colors.textPrimary,
                style = typography.bodyMedium,
            )
        }
        NoteKind.CODE -> Text(
            text = text,
            modifier = modifier
                .padding(start = if (block.depth < 0) 0.dp else INDENT * (block.depth + 1))
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(colors.card)
                .padding(8.dp),
            color = colors.textPrimary,
            style = typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
        )
    }
}

/**
 * v1.0.2 fixes N2 and R1: `#` headlineSmall (24 sp), `##` titleLarge (22 sp), `###` and below
 * titleMedium (17 sp); all bold. R1: `###` was titleSmall (15 sp), which a large text size drew
 * as large as bold body text (bodyMedium, 14 sp).
 */
internal fun headingStyle(level: Int, typography: Typography): TextStyle = when (level) {
    1 -> typography.headlineSmall
    2 -> typography.titleLarge
    else -> typography.titleMedium
}.copy(fontWeight = FontWeight.Bold)

/**
 * More space before a heading (v1.0.2 fix N2: more again, the most before `#`), less after one
 * and between list items.
 */
internal fun gapBefore(previous: NoteBlock, block: NoteBlock): Dp = when {
    block.kind == NoteKind.HEADING -> when (block.level) {
        1 -> 24.dp
        2 -> 20.dp
        else -> 16.dp
    }
    previous.kind == NoteKind.HEADING -> 4.dp
    block.kind == NoteKind.ITEM && block.marker != null && previous.kind == NoteKind.ITEM -> 4.dp
    else -> 8.dp
}

/** The text with its marks as spans; a mark outside the text is left out rather than shown. */
internal fun NoteText.annotated(): AnnotatedString = AnnotatedString(
    text = text,
    spanStyles = marks
        .filter { it.start in 0..it.end && it.end <= text.length }
        .map { AnnotatedString.Range(it.style.span(), it.start, it.end) },
)

private fun NoteStyle.span(): SpanStyle = when (this) {
    NoteStyle.BOLD -> SpanStyle(fontWeight = FontWeight.Bold)
    NoteStyle.ITALIC -> SpanStyle(fontStyle = FontStyle.Italic)
    NoteStyle.CODE -> SpanStyle(fontFamily = FontFamily.Monospace)
}

private fun Int.atLeastZero(): Int = if (this < 0) 0 else this

private val INDENT = 24.dp

internal const val NO_NOTES = "No release notes."
