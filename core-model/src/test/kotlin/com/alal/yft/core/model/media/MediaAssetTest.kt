package com.alal.yft.core.model.media

import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class MediaAssetTest {
    private val requestContext = BrowserRequestContext(
        pageUrl = "https://example.test/watch",
        userAgent = "fixture-agent",
        cookie = "session=private",
    )

    @Test
    fun `variant string does not expose signed playback URL or cookie`() {
        val variant = MediaVariant(
            id = "direct",
            playbackUrl = "https://cdn.example.test/video.mp4?token=secret",
            kind = MediaKind.DIRECT,
            trackType = MediaTrackType.AUDIO_VIDEO,
            requestContext = requestContext,
        )

        val rendered = variant.toString()

        assertFalse(rendered.contains("secret"))
        assertFalse(rendered.contains("session=private"))
        assertFalse(rendered.contains("cdn.example.test"))
    }

    @Test
    fun `companion audio never prints its address and needs a merged variant`() {
        val companion = CompanionAudio(
            mediaUrl = "https://rr1.example.test/audio.m4a?sig=secret",
            mimeType = "audio/mp4",
            codecs = listOf("mp4a.40.2"),
            requestContext = requestContext,
            contentLengthBytes = 1_000,
        )
        val variant = MediaVariant(
            id = "merged",
            playbackUrl = "https://rr1.example.test/video.mp4?sig=secret",
            kind = MediaKind.DIRECT,
            trackType = MediaTrackType.AUDIO_VIDEO,
            requestContext = requestContext,
            audioCompanion = companion,
        )
        val candidate = MediaCandidate(
            pageUrl = "https://example.test/watch",
            mediaUrl = "https://rr1.example.test/video.mp4?sig=secret",
            sources = setOf(CandidateSource.REQUEST),
            kind = MediaKind.DIRECT,
            requestContext = requestContext,
            codecs = listOf("avc1.64001F"),
            audioCompanion = companion,
        )

        listOf(companion.toString(), variant.toString(), candidate.toString()).forEach {
            assertFalse(it.contains("secret"))
            assertFalse(it.contains("session=private"))
        }
        assertFalse(companion.toString().contains("rr1.example.test"))
        assertTrue(candidate.toString().contains("audioCompanion=true"))
        assertTrue(variant.toString().contains("audioCompanion=true"))
        assertThrows(IllegalArgumentException::class.java) {
            variant.copy(trackType = MediaTrackType.VIDEO)
        }
        assertThrows(IllegalArgumentException::class.java) {
            companion.copy(contentLengthBytes = 0)
        }
    }

    @Test
    fun `size and accuracy must either both be present or both be unknown`() {
        assertThrows(IllegalArgumentException::class.java) {
            MediaVariant(
                id = "invalid",
                playbackUrl = "https://example.test/video.mp4",
                kind = MediaKind.DIRECT,
                trackType = MediaTrackType.AUDIO_VIDEO,
                requestContext = requestContext,
                sizeBytes = 100,
            )
        }
    }

    @Test
    fun `only a DASH representation that is not one file is in pieces`() {
        // v1.0.2 fix R3: a SegmentTemplate or SegmentList representation.
        val pieces = MediaVariant(
            id = "dash:bbb_a64k",
            playbackUrl = "https://cdn.example.test/stream.mpd?token=secret",
            kind = MediaKind.DASH,
            trackType = MediaTrackType.AUDIO,
            requestContext = requestContext,
            dashPieces = true,
        )

        assertTrue(pieces.dashPieces)
        assertFalse(pieces.dashOneFile)
        assertThrows(IllegalArgumentException::class.java) {
            pieces.copy(kind = MediaKind.HLS)
        }
        assertThrows(IllegalArgumentException::class.java) {
            pieces.copy(dashOneFile = true)
        }
    }

    @Test
    fun `only a DASH representation is one file and a manifest's sound names its representation`() {
        val dash = MediaVariant(
            id = "dash:5",
            playbackUrl = "https://cdn.example.test/stream.mpd?token=secret",
            kind = MediaKind.DASH,
            trackType = MediaTrackType.VIDEO,
            requestContext = requestContext,
            dashOneFile = true,
        )
        val sound = CompanionAudio(
            mediaUrl = "https://cdn.example.test/stream.mpd?token=secret",
            mimeType = "audio/mp4",
            codecs = listOf("mp4a.40.5"),
            requestContext = requestContext,
            representationId = "1",
        )

        assertTrue(dash.dashOneFile)
        assertFalse(sound.toString().contains("secret"))
        assertTrue(sound.toString().contains("representationId=[REDACTED]"))
        assertThrows(IllegalArgumentException::class.java) {
            dash.copy(kind = MediaKind.HLS)
        }
        assertThrows(IllegalArgumentException::class.java) {
            sound.copy(representationId = " ")
        }
    }

    @Test
    fun `asset rejects duplicate variant IDs`() {
        val variant = MediaVariant(
            id = "duplicate",
            playbackUrl = "https://example.test/video.mp4",
            kind = MediaKind.DIRECT,
            trackType = MediaTrackType.AUDIO_VIDEO,
            requestContext = requestContext,
        )

        assertThrows(IllegalArgumentException::class.java) {
            MediaAsset(
                sourcePageUrl = "https://example.test/watch",
                title = null,
                thumbnailUrl = null,
                durationMillis = null,
                variants = listOf(variant, variant),
                resolvedAtEpochMs = 1,
            )
        }
    }
}