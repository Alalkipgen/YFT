package com.alal.yft.core.download

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

class Id3v2TagTest {
    @Test
    fun aTitleBecomesAnId3v23TagWithOneUtf16Tit2Frame() {
        val tag = Id3v2Tag.title("Ocean")

        val text = "Ocean".toByteArray(Charsets.UTF_16LE)
        val frameBody = 3 + text.size
        val frameSize = 10 + frameBody
        assertArrayEquals("ID3".toByteArray() + byteArrayOf(3, 0, 0), tag.copyOfRange(0, 6))
        assertArrayEquals(byteArrayOf(0, 0, 0, frameSize.toByte()), tag.copyOfRange(6, 10))
        assertEquals("TIT2", String(tag, 10, 4, Charsets.ISO_8859_1))
        assertArrayEquals(byteArrayOf(0, 0, 0, frameBody.toByte()), tag.copyOfRange(14, 18))
        val flagsAndBom = byteArrayOf(0, 0, 1, 0xFF.toByte(), 0xFE.toByte())
        assertArrayEquals(flagsAndBom, tag.copyOfRange(18, 23))
        assertEquals("Ocean", String(tag, 23, text.size, Charsets.UTF_16LE))
        assertEquals(10 + frameSize, tag.size)
    }

    @Test
    fun anyScriptReadsBack() {
        val title = "မြန်မာ သီချင်း"

        val tag = Id3v2Tag.title(title)

        assertEquals(title, String(tag, 23, tag.size - 23, Charsets.UTF_16LE))
    }

    @Test
    fun theTagSizeIsSyncsafe() {
        // 200 characters: a 403-byte frame body, 413 frame bytes, two 7-bit groups.
        val tag = Id3v2Tag.title("a".repeat(200))

        val frameSize = 10 + 3 + 400
        assertArrayEquals(
            byteArrayOf(0, 0, (frameSize shr 7).toByte(), (frameSize and 0x7F).toByte()),
            tag.copyOfRange(6, 10),
        )
        assertEquals(10 + frameSize, tag.size)
    }

    @Test
    fun blankTitlesWriteNoTagAndControlCharactersBecomeSpaces() {
        assertEquals(0, Id3v2Tag.title(null).size)
        assertEquals(0, Id3v2Tag.title(" \n\t ").size)

        val tag = Id3v2Tag.title("Line one\nline two")

        assertEquals("Line one line two", String(tag, 23, tag.size - 23, Charsets.UTF_16LE))
    }

    @Test
    fun longTitlesAreCutWithoutSplittingACharacter() {
        val emoji = "\uD83C\uDFB5"
        val title = "a".repeat(Id3v2Tag.MAX_TITLE_CHARS - 1) + emoji + "tail"

        val tag = Id3v2Tag.title(title)

        val text = String(tag, 23, tag.size - 23, Charsets.UTF_16LE)
        assertEquals("a".repeat(Id3v2Tag.MAX_TITLE_CHARS - 1), text)
    }
}
