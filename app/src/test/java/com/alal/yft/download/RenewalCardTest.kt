package com.alal.yft.download

import com.alal.yft.core.download.DownloadDestinationKind
import com.alal.yft.core.model.media.MediaKind
import com.alal.yft.core.model.media.MediaTrackType
import com.alal.yft.feature.detectedmedia.LookupOwner
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Fresh link renewal: the card a download keeps with its record. It holds the page's address and
 * the facts that name the same file there, plus where the bytes are, so a Retry after the app
 * closed looks the file up again and goes on from them. Titles and addresses are the site's, so
 * no character in them may break the card.
 */
class RenewalCardTest {
    @Test
    fun `a card holds every fact the same file is found by`() {
        val card = RenewalCard.encode(source())
        val (read, saved) = requireNotNull(RenewalCard.decode(card))

        assertEquals(source(), read)
        assertEquals(SAVED, saved)
        assertEquals(source(), read)
    }

    @Test
    fun `an address or title with separators and other scripts reads back unchanged`() {
        val awkward = source().copy(
            pageUrl = "https://vk.example.test/video-1_2?t=1\u001F2&list=2f4e2c62#comment",
            title = "ကွက်တိ 100% — clip|2\u001F~title",
            label = "720p ~ \u001F",
            fileName = "ကွက်တိ 100% — clip|2.mp4",
        )

        val (read, _) = requireNotNull(RenewalCard.decode(RenewalCard.encode(awkward)))

        assertEquals(awkward, read)
    }

    @Test
    fun `a card this version does not read is no card`() {
        val card = RenewalCard.encode(source())

        // A card written by another version of the app: its first field says which.
        assertNull(RenewalCard.decode("2" + card.drop(1)))
        assertNull(RenewalCard.decode(""))
        assertNull(RenewalCard.decode("v1"))
    }

    @Test
    fun `a source with no saved file reads back without one`() {
        val (read, saved) = requireNotNull(
            RenewalCard.decode(RenewalCard.encode(source().copy(saved = null))),
        )

        assertNull(read.saved)
        assertNull(saved)
    }

    private fun source() = DownloadSource(
        pageUrl = PAGE,
        videoId = "vk:-1_2",
        candidateKind = MediaKind.DIRECT,
        owner = LookupOwner.BROWSER,
        userAgent = "Mozilla/5.0 (fixture)",
        session = true,
        quality = DownloadQualityKey(
            kind = MediaKind.DIRECT,
            trackType = MediaTrackType.VIDEO,
            merged = true,
            height = 720,
            width = 1280,
            framesPerSecond = 30,
            container = "mp4",
            codecs = setOf("avc1"),
            soundCodecs = setOf("mp4a"),
            label = "720p",
            bitrateBitsPerSecond = 1_500_000,
        ),
        conversion = DownloadConversion(audioFromVideo = true, mp3Kbps = 192),
        label = "720p",
        title = "Fixture",
        durationMillis = 221_000,
        fileName = "Fixture 720p.mp4",
        networkChanges = 3,
        mediaPath = "https://cdn.example.test/video/1.mp4",
        saved = SAVED,
    )

    private companion object {
        const val PAGE = "https://vk.example.test/video-1_2"
        val SAVED = SavedDestination(
            kind = DownloadDestinationKind.APP_PRIVATE,
            uri = null,
            displayName = "Fixture 720p.mp4",
            partialName = "Fixture 720p.mp4.ab12cd.part",
        )
    }
}
