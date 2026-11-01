package com.alal.yft.extractor.master.social

import com.alal.yft.core.model.media.BrowserRequestContext
import com.alal.yft.core.model.media.CandidateSource
import com.alal.yft.core.model.media.CompanionAudio
import com.alal.yft.core.model.media.MediaCandidate
import com.alal.yft.core.model.media.MediaKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

/** L2 (S6): [RowPick.bestFirst] — of one height's versions, the highest rate first; nothing dropped or changed. */
class RowPickTest {
    @Test
    fun `one height's versions - the highest rate first, in the places they held, every other row in its place`() {
        // Slot 5's case: three 720 x 1280 versions without a bitrate, each with its file's total (V's read).
        val low = row("v1", 720, 1280, bytes = 918_528)
        val sound = row("a", mime = "audio/mp4", codecs = listOf("mp4a.40.2"))
        val best = row("v2", 720, 1280, bytes = 1_992_294)
        val small = row("s", 360, 640, bytes = 400_000)
        val middle = row("v3", 720, 1280, bytes = 1_200_000)
        val picked = RowPick.bestFirst(listOf(low, sound, best, small, middle))
        assertEquals(listOf("v2", "a", "v3", "s", "v1"), picked.map { it.mediaUrl.substringAfterLast('/') })
        // The rows themselves, unchanged.
        assertSame(best, picked[0])
        assertSame(sound, picked[1])
        assertSame(small, picked[3])
    }

    @Test
    fun `a stated bitrate counts first, equal rates keep their order, a height with a row without a rate keeps its order`() {
        val stated = row("b1", 720, 1280, bytes = 100, bitrate = 3_000_000)
        val read = row("b2", 720, 1280, bytes = 1_992_294)
        assertEquals(listOf("b1", "b2"), names(RowPick.bestFirst(listOf(read, stated))))
        val same = listOf(row("e1", 720, 1280, bytes = 1_000_000), row("e2", 720, 1280, bytes = 1_000_000))
        assertEquals(listOf("e1", "e2"), names(RowPick.bestFirst(same)))
        val unknown = listOf(row("u1", 720, 1280, bytes = 100_000), row("u2", 720, 1280, bytes = null))
        assertEquals(listOf("u1", "u2"), names(RowPick.bestFirst(unknown)))
        val noLength = listOf(row("n1", 720, 1280, bytes = 100_000), row("n2", 720, 1280, bytes = 900_000, millis = null))
        assertEquals(listOf("n1", "n2"), names(RowPick.bestFirst(noLength)))
    }

    @Test
    fun `rows of one height with another sound, another family, a manifest or no height are never swapped`() {
        val merged = row("m", 720, 1280, bytes = 100_000, sound = true)
        val whole = row("w", 720, 1280, bytes = 1_000_000)
        assertEquals(listOf("m", "w"), names(RowPick.bestFirst(listOf(merged, whole))))
        val vp9 = row("vp9", 720, 1280, bytes = 100_000, codecs = listOf("vp09.00.31.08"))
        val avc = row("avc", 720, 1280, bytes = 1_000_000, codecs = listOf("avc1.64001F"))
        assertEquals(listOf("vp9", "avc"), names(RowPick.bestFirst(listOf(vp9, avc))))
        val dash = row("d", 720, 1280, bytes = 100_000, kind = MediaKind.DASH)
        val file = row("f", 720, 1280, bytes = 1_000_000)
        assertEquals(listOf("d", "f"), names(RowPick.bestFirst(listOf(dash, file))))
        val noSize = row("x", null, null, bytes = 100_000)
        val sized = row("y", 720, 1280, bytes = 1_000_000)
        assertEquals(listOf("x", "y"), names(RowPick.bestFirst(listOf(noSize, sized))))
        // A 1280 x 720 picture and a 720 x 1280 one are one height (the short side), as the sheet names them.
        val wide = row("wide", 1280, 720, bytes = 100_000)
        val tall = row("tall", 720, 1280, bytes = 1_000_000)
        assertEquals(listOf("tall", "wide"), names(RowPick.bestFirst(listOf(wide, tall))))
        assertEquals(emptyList<MediaCandidate>(), RowPick.bestFirst(emptyList()))
    }

    private fun names(rows: List<MediaCandidate>) = rows.map { it.mediaUrl.substringAfterLast('/') }

    private fun row(
        name: String,
        width: Int? = null,
        height: Int? = null,
        bytes: Long? = null,
        bitrate: Long? = null,
        millis: Long? = 30_000,
        mime: String = "video/mp4",
        codecs: List<String> = emptyList(),
        kind: MediaKind = MediaKind.DIRECT,
        sound: Boolean = false,
    ) = MediaCandidate(
        pageUrl = PAGE,
        mediaUrl = "https://cdn.example.test/v/$name",
        sources = setOf(CandidateSource.MANIFEST),
        kind = kind,
        mimeType = mime,
        durationMillis = millis,
        contentLengthBytes = bytes,
        codecs = codecs,
        width = width,
        height = height,
        bitrateBitsPerSecond = bitrate,
        audioCompanion = if (sound) {
            CompanionAudio("https://cdn.example.test/v/sound", "audio/mp4", listOf("mp4a.40.2"), BrowserRequestContext(PAGE, null, null))
        } else {
            null
        },
    )

    private companion object {
        const val PAGE = "https://www.instagram.com/reel/C9fixtREEL1/"
    }
}
