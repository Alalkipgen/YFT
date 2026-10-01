package com.alal.yft.core.model.media

import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
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