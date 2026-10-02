package com.alal.yft.feature.downloads

import com.alal.yft.core.model.download.DownloadFailureReason
import com.alal.yft.core.model.media.BrowserRequestContext
import com.alal.yft.core.model.media.MediaAsset
import com.alal.yft.core.model.media.MediaKind
import com.alal.yft.core.model.media.MediaSizeAccuracy
import com.alal.yft.core.model.media.MediaTrackType
import com.alal.yft.core.model.media.MediaVariant
import com.alal.yft.core.model.media.VariantSupport
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DownloadPlanFactoryTest {
    @Test
    fun `direct selection becomes a direct plan with exact size only`() {
        val exact = variant(
            kind = MediaKind.DIRECT,
            container = "mp4",
            size = 2_048L,
            sizeAccuracy = MediaSizeAccuracy.EXACT,
        )
        val estimated = exact.copy(
            id = "estimated",
            sizeAccuracy = MediaSizeAccuracy.ESTIMATED,
        )

        val ready = factory(exact) as DownloadPlanResult.Ready
        val request = ready.request as DownloadRequest.Direct
        assertEquals(2_048L, request.plan.expectedBytes)
        assertEquals("task-1", request.plan.taskId)
        assertEquals("https://media.example.test/clip", request.plan.sourceUrl)

        val estimatedReady = factory(estimated) as DownloadPlanResult.Ready
        val estimatedRequest = estimatedReady.request as DownloadRequest.Direct
        assertEquals(null, estimatedRequest.plan.expectedBytes)
    }

    @Test
    fun `hls and dash selections keep their manifest identity`() {
        val hls = variant(kind = MediaKind.HLS, container = null, mimeType = null)
        val dash = variant(
            kind = MediaKind.DASH,
            container = null,
            mimeType = "video/mp4",
        ).copy(manifestVariantId = "video-720", codecs = listOf("avc1.64001f", ""))

        val hlsRequest = (factory(hls) as DownloadPlanResult.Ready)
            .request as DownloadRequest.Hls
        assertEquals("https://media.example.test/clip", hlsRequest.plan.playlistUrl)

        val dashRequest = (factory(dash) as DownloadPlanResult.Ready)
            .request as DownloadRequest.Dash
        assertEquals("video-720", dashRequest.plan.representationId)
        assertEquals(listOf("avc1.64001f"), dashRequest.plan.codecs)
    }

    @Test
    fun `dash without a representation is rejected instead of guessed`() {
        val dash = variant(kind = MediaKind.DASH)

        val rejected = factory(dash) as DownloadPlanResult.Rejected

        assertEquals(DownloadFailureReason.UNSUPPORTED_SOURCE, rejected.reason)
        assertTrue(rejected.message.contains("representation"))
    }

    @Test
    fun `unsupported codec insecure and expired selections are rejected explicitly`() {
        val unsupported = variant(support = VariantSupport.UNSUPPORTED_CODEC)
        val insecure = variant(url = "http://media.example.test/clip")
        val expired = variant(expiresAt = 500L)

        assertEquals(
            DownloadFailureReason.UNSUPPORTED_SOURCE,
            (factory(unsupported) as DownloadPlanResult.Rejected).reason,
        )
        assertEquals(
            DownloadFailureReason.INVALID_URL,
            (factory(insecure) as DownloadPlanResult.Rejected).reason,
        )
        assertEquals(
            DownloadFailureReason.EXPIRED_URL,
            (factory(expired) as DownloadPlanResult.Rejected).reason,
        )
        assertEquals(
            DownloadFailureReason.UNSUPPORTED_SOURCE,
            (factory(variant(kind = MediaKind.UNKNOWN)) as DownloadPlanResult.Rejected).reason,
        )
    }

    @Test
    fun `a selection expiring later than now is still accepted`() {
        val stillValid = variant(kind = MediaKind.DIRECT, expiresAt = 1_001L)

        assertTrue(factory(stillValid) is DownloadPlanResult.Ready)
    }

    @Test
    fun `file names come from metadata only and never from the signed url`() {
        val variant = variant(
            url = "https://media.example.test/secret-token/clip.mp4?sig=abc123",
            label = "720p/HD\\stream",
            container = "MP4",
        )
        val asset = asset("My ../Holiday: Video?")

        val name = DownloadPlanFactory.fileName(asset, variant)

        assertEquals("My Holiday Video 720p HD stream.mp4", name)
        assertFalse(name.contains("abc123"))
        assertFalse(name.contains("secret-token"))
        assertFalse(name.substringBeforeLast('.').contains('.'))
    }

    @Test
    fun `file names stay bounded and fall back when metadata is unusable`() {
        val longTitle = asset("x".repeat(400))
        val bounded = DownloadPlanFactory.fileName(
            longTitle,
            variant(container = "mp4"),
        )
        assertTrue(bounded.length <= DownloadPlanFactory.MAX_FILE_NAME_LENGTH)
        assertTrue(bounded.endsWith(".mp4"))

        val blank = DownloadPlanFactory.fileName(
            asset("   "),
            variant(label = "***", container = null, mimeType = null),
        )
        assertEquals("yft-download.mp4", blank)
    }

    @Test
    fun `extensions follow the container then the mime type then the track type`() {
        assertEquals(
            "webm",
            DownloadPlanFactory.extensionFor(variant(container = ".WEBM")),
        )
        assertEquals(
            "m4a",
            DownloadPlanFactory.extensionFor(
                variant(container = null, mimeType = "audio/mp4"),
            ),
        )
        assertEquals(
            "mp3",
            DownloadPlanFactory.extensionFor(
                variant(container = null, mimeType = "audio/mpeg"),
            ),
        )
        assertEquals(
            "m4a",
            DownloadPlanFactory.extensionFor(
                variant(
                    container = null,
                    mimeType = null,
                    trackType = MediaTrackType.AUDIO,
                ),
            ),
        )
        assertEquals(
            "mp4",
            DownloadPlanFactory.extensionFor(variant(container = null, mimeType = null)),
        )
    }

    private fun factory(variant: MediaVariant): DownloadPlanResult =
        DownloadPlanFactory.create(
            asset = asset(),
            variant = variant,
            taskId = "task-1",
            nowEpochMs = 1_000L,
        )

    private fun asset(title: String? = "Fixture") = MediaAsset(
        sourcePageUrl = "https://page.example.test/watch",
        title = title,
        thumbnailUrl = null,
        durationMillis = null,
        variants = listOf(variant()),
        resolvedAtEpochMs = 1,
    )

    private fun variant(
        id: String = "variant",
        url: String = "https://media.example.test/clip",
        kind: MediaKind = MediaKind.DIRECT,
        trackType: MediaTrackType = MediaTrackType.AUDIO_VIDEO,
        label: String? = null,
        container: String? = "mp4",
        mimeType: String? = "video/mp4",
        size: Long? = null,
        sizeAccuracy: MediaSizeAccuracy? = null,
        support: VariantSupport = VariantSupport.SUPPORTED,
        expiresAt: Long? = null,
    ) = MediaVariant(
        id = id,
        playbackUrl = url,
        kind = kind,
        trackType = trackType,
        requestContext = BrowserRequestContext(
            pageUrl = "https://page.example.test/watch",
            userAgent = "fixture-agent",
            cookie = null,
        ),
        label = label,
        mimeType = mimeType,
        container = container,
        sizeBytes = size,
        sizeAccuracy = sizeAccuracy,
        support = support,
        expiresAtEpochMs = expiresAt,
    )
}
