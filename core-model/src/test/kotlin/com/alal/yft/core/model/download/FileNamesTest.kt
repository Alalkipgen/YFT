package com.alal.yft.core.model.download

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FileNamesTest {
    @Test
    fun myanmarThaiAndHindiTitlesKeepTheirMarks() {
        // D3 (the owner): these come back exactly; the marks were spaces before.
        listOf(
            "အချစ်ရူး",
            "ထွဏ်းထွဏ်း",
            "ဒါဟာအချစ်လား",
            "ဇော်ပိုင် ချောစုခင်",
            "สวัสดีครับ",
            "नमस्ते दुनिया",
        ).forEach { assertEquals(it, it, FileNames.keepWords(it, ALLOWED)) }
    }

    @Test
    fun myanmarWordSignsAreKeptAndItsSentenceMarksBecomeSpaces() {
        // D3: ၌ ၍ ၎ ၏ (U+104C–104F) are kept like letters; ၊ and ။ are punctuation.
        assertEquals("သူ၏ အိမ်", FileNames.keepWords("သူ၏ အိမ်", ALLOWED))
        assertEquals("၎င်း", FileNames.keepWords("၎င်း", ALLOWED))
        assertEquals("ဟုတ်ကဲ့ ဟုတ်", FileNames.keepWords("ဟုတ်ကဲ့။ဟုတ်၊", ALLOWED))
    }

    @Test
    fun aMarkWithNoKeptBaseIsDroppedSoNoInvisibleCharacterIsLeft() {
        assertEquals("I Yangon", FileNames.keepWords("I \u2764\uFE0F Yangon", ALLOWED))
        assertEquals("Love", FileNames.keepWords("Love\u2764\uFE0F", ALLOWED))
        assertEquals("", FileNames.keepWords("\u2764\uFE0F", ALLOWED))
        assertEquals("abc", FileNames.keepWords("\u0301abc", ALLOWED))
        // A skin-toned thumb (a symbol and a modifier) with VS16: nothing of it is left.
        assertEquals("a b", FileNames.keepWords("a \uD83D\uDC4D\uD83C\uDFFD\uFE0F b", ALLOWED))
    }

    @Test
    fun zawgyiKeepsTheSpacingMarksItTypesBeforeTheirConsonant() {
        // D3: Zawgyi writes ေ (U+1031) before its consonant, so it has no base before it.
        val zawgyi = "\u1031\u1019\u102C\u1004\u1039"
        assertEquals(zawgyi, FileNames.keepWords(zawgyi, ALLOWED))
        assertEquals("ကို $zawgyi", FileNames.keepWords("ကို $zawgyi", ALLOWED))
    }

    @Test
    fun reservedInvisibleAndDirectionChangingCharactersBecomeSpaces() {
        assertEquals(
            "a b c d e f g h i j",
            FileNames.keepWords("a\\b/c:d*e?f\"g<h>i|j", ALLOWED),
        )
        assertEquals(
            "clip gnp exe",
            FileNames.keepWords("clip\u202Egnp.exe\u200B\u0000 \uD83C\uDFAC", ALLOWED),
        )
        assertEquals("(a) [b] c-d_e", FileNames.keepWords("  (a)  [b]\tc-d_e  ", ALLOWED))
    }

    @Test
    fun aStackedConsonantIsNeverSplitAfterItsVirama() {
        // D3: သတ္တဝါ cuts only after သ, after သတ္တ or whole: never after ္, never before ါ.
        val expected = listOf("", SA, SA, SA, WORD.take(4), WORD.take(4), WORD)
        assertEquals(expected, (0..6).map { FileNames.cut(WORD, it) })
        assertEquals(SA, FileNames.cut(WORD, 100, maxBytes = 11))
        assertEquals(WORD.take(4), FileNames.cut(WORD, 100, maxBytes = 12))
        // Devanagari: स्ते stays one piece.
        assertEquals(listOf("नम", "नम", "नमस्ते"), (4..6).map { FileNames.cut("नमस्ते", it) })
    }

    @Test
    fun aLongMyanmarTitleIsCutAtAGraphemeWithinTheByteBudget() {
        val title = WORD.repeat(30)

        val cut = FileNames.cut(title, 96, maxBytes = 219)

        // 219 bytes are 73 Myanmar code points: 12 words, then သ.
        assertEquals(WORD.repeat(12) + SA, cut)
        assertTrue(FileNames.utf8Bytes(cut) <= 219)
        assertFalse(cut.endsWith(VIRAMA))
    }

    @Test
    fun emojiSequencesFlagsAndHangulStayTogether() {
        val flags = "\uD83C\uDDF2\uD83C\uDDF2\uD83C\uDDF9\uD83C\uDDED"
        assertEquals(flags.take(4), FileNames.cut(flags, 6))
        assertEquals(flags.take(4), FileNames.cut(flags, 7))
        val thumbs = "\uD83D\uDC4D\uD83C\uDFFD"
        assertEquals(thumbs, FileNames.cut(thumbs + thumbs, 6))
        val family = "\uD83D\uDC68\u200D\uD83D\uDC69\u200D\uD83D\uDC67"
        assertEquals(family, FileNames.cut("$family ok", 8))
        val england = "\uD83C\uDFF4\uDB40\uDC67\uDB40\uDC62\uDB40\uDC65\uDB40\uDC6E\uDB40\uDC67" +
            "\uDB40\uDC7F"
        assertEquals(england, FileNames.cut(england + "x", 14))
        val jamo = "\u1112\u1161\u11AB\u1100\u116E\u11A8"
        assertEquals(jamo.take(3), FileNames.cut(jamo, 5))
        assertEquals("한국", FileNames.cut("한국어", 2))
    }

    @Test
    fun whenNotEvenOneGraphemeFitsTheCutFallsBetweenCodePoints() {
        val zalgo = "Z" + "\u0335".repeat(50) + " ok"
        assertEquals("Z" + "\u0335".repeat(9), FileNames.cut(zalgo, 10))
        assertEquals("", FileNames.cut("\uD83D\uDC4D", 1))
    }

    @Test
    fun cutAtWordEndsWithAWholeWordWhenOneEndsNear() {
        // N9 (v1.0.2): a title that fits is kept whole, its end's dots and all.
        assertEquals("Mr. Bean.", FileNames.cutAtWord("Mr. Bean.", 20))
        // Cut inside "Video": back to the space before it; at a space already: kept there.
        assertEquals("Song [Lyric", FileNames.cutAtWord("Song [Lyric Video]", 14))
        assertEquals("Song [Lyric", FileNames.cutAtWord("Song [Lyric Video]", 12))
        // No space within reach: the grapheme cut stays.
        assertEquals("Song [Lyr", FileNames.cutAtWord("Song [Lyric Video]", 9, reach = 3))
        assertEquals("Abcdefgh", FileNames.cutAtWord("Abcdefghij klm", 8))
    }

    @Test
    fun cutAtWordKeepsMyanmarGraphemesWholeWithinTheByteBudget() {
        // ဘယ်ဆီမှာ: 8 code points, 24 bytes; with its space 25.
        val word = "\u1018\u101A\u103A\u1006\u102E\u1019\u103E\u102C"
        val title = List(12) { word }.joinToString(" ")

        val cut = FileNames.cutAtWord(title, maxChars = 200, maxBytes = 214)

        assertEquals(List(8) { word }.joinToString(" "), cut)
        assertTrue(FileNames.utf8Bytes(cut) <= 214)
        // Without a space, the cut is still never before a mark nor after the stacking virama.
        val stacked = FileNames.cutAtWord(WORD.repeat(20), maxChars = 200, maxBytes = 214)
        assertEquals(WORD.repeat(11) + "\u101E\u1010\u1039\u1010", stacked)
    }

    @Test
    fun fitKeepsAShortExtensionAndShortensOnlyTheName() {
        val myanmar = FileNames.fit(WORD.repeat(40) + ".mp4", 180)
        assertEquals(WORD.repeat(12) + SA + ".mp4", myanmar)
        assertTrue(FileNames.utf8Bytes(myanmar) <= FileNames.MAX_NAME_BYTES)
        assertEquals("x".repeat(175) + ".webm", FileNames.fit("x".repeat(300) + ".webm", 180))
        assertEquals("y".repeat(180), FileNames.fit("y".repeat(300), 180))
        assertEquals("name.eeeee", FileNames.fit("name." + "e".repeat(11), 10))
        assertEquals("ab.mp4", FileNames.fit("ab cd.mp4", 7))
        assertEquals("short.mp4", FileNames.fit("short.mp4", 180))
    }

    @Test
    fun theByteBudgetLeavesRoomForWhatIsAddedToASavedName() {
        assertEquals(223, FileNames.MAX_NAME_BYTES)
        val texts = listOf("a", "\u00E9", "\u1000", "\uD83D\uDC4D", "\uD800")
        // An unpaired surrogate counts as much as U+FFFD would.
        assertEquals(listOf(1, 2, 3, 4, 3), texts.map(FileNames::utf8Bytes))
    }

    private companion object {
        const val ALLOWED = "-_()[] "
        const val SA = "\u101E"
        const val VIRAMA = "\u1039"

        /** သတ္တဝါ: သ, တ, ္, တ, ဝ, ါ. */
        const val WORD = "\u101E\u1010\u1039\u1010\u101D\u102B"
    }
}
