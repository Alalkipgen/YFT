package com.alal.yft.update

import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.alal.yft.ui.theme.YftTypography
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * v1.0.2 fixes N2 and R1: the release notes' headings stand out from their text: `#`
 * headlineSmall (24 sp), `##` titleLarge (22 sp), `###` and below titleMedium (17 sp), all bold
 * (the text is bodyMedium, 14 sp), with more room above them: 24, 20 and 16 dp (12 dp before).
 */
class ReleaseNotesHeadingTest {
    @Test
    fun `each heading level is a size up and bold`() {
        val sizes = (1..4).map { level -> headingStyle(level, YftTypography) }

        assertEquals(listOf(24.sp, 22.sp, 17.sp, 17.sp), sizes.map { it.fontSize })
        assertEquals(List(4) { FontWeight.Bold }, sizes.map { it.fontWeight })
        assertEquals(14.sp, YftTypography.bodyMedium.fontSize)
    }

    @Test
    fun `a heading has more room above it, the most for the first level`() {
        val text = NoteBlock(NoteKind.PARAGRAPH, NoteText("Text"))
        fun heading(level: Int) = NoteBlock(NoteKind.HEADING, NoteText("Heading"), level = level)

        assertEquals(24.dp, gapBefore(text, heading(1)))
        assertEquals(20.dp, gapBefore(text, heading(2)))
        assertEquals(16.dp, gapBefore(text, heading(3)))
        // After a heading and between paragraphs, as before.
        assertEquals(4.dp, gapBefore(heading(2), text))
        assertEquals(8.dp, gapBefore(text, text))
    }
}
