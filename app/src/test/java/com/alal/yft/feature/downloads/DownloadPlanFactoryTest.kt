package com.alal.yft.feature.downloads

import com.alal.yft.core.model.download.DownloadFailureReason
import com.alal.yft.core.model.download.Mp3Encoding
import com.alal.yft.core.model.download.WholeFileTrack
import com.alal.yft.core.model.media.AudioFromVideo
import com.alal.yft.core.model.media.BrowserRequestContext
import com.alal.yft.core.model.media.CompanionAudio
import com.alal.yft.core.model.media.MediaAsset
import com.alal.yft.core.model.media.MediaKind
import com.alal.yft.core.model.media.MediaSizeAccuracy
import com.alal.yft.core.model.media.MediaTrackType
import com.alal.yft.core.model.media.MediaVariant
import com.alal.yft.core.model.media.Mp3Variants
import com.alal.yft.core.model.media.VariantSupport
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
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
    fun `an mp3 choice downloads its aac file and is named and typed as mp3`() {
        val source = variant(
            id = "m4a",
            trackType = MediaTrackType.AUDIO,
            container = "m4a",
            mimeType = "audio/mp4",
            size = 4_000L,
            sizeAccuracy = MediaSizeAccuracy.EXACT,
        ).copy(codecs = listOf("mp4a.40.2"), durationMillis = 10_000)
        val mp3 = Mp3Variants.of(source, 192)!!

        val request = (factory(mp3) as DownloadPlanResult.Ready).request as DownloadRequest.Direct

        assertEquals("Fixture MP3 192 kbps.mp3", request.fileName)
        assertEquals("audio/mpeg", request.mimeType)
        assertEquals(Mp3Encoding(192, "Fixture"), request.plan.mp3)
        assertEquals(source.playbackUrl, request.plan.sourceUrl)
        // The MP3 size is an estimate; the AAC length comes from the probe.
        assertNull(request.plan.expectedBytes)
        val plain = (factory(source) as DownloadPlanResult.Ready).request as DownloadRequest.Direct
        assertNull(plain.plan.mp3)
        assertEquals("Fixture.m4a", plain.fileName)
    }

    @Test
    fun `the sound of an mp4 downloads the video and keeps its aac track as m4a`() {
        val video = variant(
            id = "mp4",
            trackType = MediaTrackType.AUDIO_VIDEO,
            container = "mp4",
            mimeType = "video/mp4",
            size = 9_000L,
            sizeAccuracy = MediaSizeAccuracy.EXACT,
        ).copy(codecs = listOf("avc1.42001e", "mp4a.40.2"), durationMillis = 10_000)
        val m4a = AudioFromVideo.of(video)!!.copy(label = null)
        val mp3 = Mp3Variants.of(AudioFromVideo.of(video)!!, 192)!!

        val sound = (factory(m4a) as DownloadPlanResult.Ready).request as DownloadRequest.Direct
        val converted = (factory(mp3) as DownloadPlanResult.Ready).request
            as DownloadRequest.Direct
        val plain = (factory(video) as DownloadPlanResult.Ready).request as DownloadRequest.Direct

        assertTrue(sound.plan.audioOnly)
        assertNull(sound.plan.mp3)
        assertEquals(video.playbackUrl, sound.plan.sourceUrl)
        assertEquals("Fixture.m4a", sound.fileName)
        assertEquals("audio/mp4", sound.mimeType)
        // MP3 reads the AAC track of the downloaded MP4 itself.
        assertFalse(converted.plan.audioOnly)
        assertEquals(Mp3Encoding(192, "Fixture"), converted.plan.mp3)
        assertEquals(video.playbackUrl, converted.plan.sourceUrl)
        assertFalse(plain.plan.audioOnly)
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
    fun `video with companion audio becomes a merge of two whole-file tracks`() {
        val merged = variant(
            url = "https://rr3---sn-a.googlevideo.com/videoplayback?itag=136&sig=private",
            label = "720p",
            container = "mp4",
            expiresAt = 9_000L,
        ).copy(codecs = listOf("avc1.4d401f"), audioCompanion = companion(expiresAt = 5_000L))

        val request = (factory(merged) as DownloadPlanResult.Ready).request as DownloadRequest.Mux

        val plan = request.plan
        assertEquals("task-1", plan.taskId)
        assertEquals("Fixture 720p.mp4", request.fileName)
        assertEquals("video/mp4", request.mimeType)
        assertEquals(merged.playbackUrl, plan.video.manifestUrl)
        assertEquals(MediaTrackType.VIDEO, plan.video.trackType)
        assertEquals(listOf("avc1.4d401f"), plan.video.codecs)
        assertEquals(WholeFileTrack(), plan.video.wholeFile)
        assertEquals(companion().mediaUrl, plan.audio.manifestUrl)
        assertEquals(MediaTrackType.AUDIO, plan.audio.trackType)
        assertEquals(listOf("mp4a.40.2"), plan.audio.codecs)
        assertEquals(WholeFileTrack(), plan.audio.wholeFile)
        assertEquals(5_000L, plan.expiresAtEpochMs)
        assertTrue(plan.video.taskId != plan.audio.taskId)
    }

    @Test
    fun `merges the phone cannot write and expired audio are refused before queueing`() {
        val base = variant(container = "mp4").copy(codecs = listOf("avc1.4d401f"))
        val vp9 = base.copy(codecs = listOf("vp09.00.40.08"), audioCompanion = companion())
        val opus = base.copy(
            audioCompanion = companion().copy(mimeType = "audio/webm", codecs = listOf("opus")),
        )
        val expiredAudio = base.copy(audioCompanion = companion(expiresAt = 1_000L))
        val insecureAudio = base.copy(
            audioCompanion = companion().copy(mediaUrl = "http://media.example.test/audio"),
        )

        listOf(vp9, opus).forEach { incompatible ->
            val rejected = factory(incompatible) as DownloadPlanResult.Rejected
            assertEquals(DownloadFailureReason.INCOMPATIBLE_TRACKS, rejected.reason)
        }
        assertEquals(
            DownloadFailureReason.EXPIRED_URL,
            (factory(expiredAudio) as DownloadPlanResult.Rejected).reason,
        )
        assertEquals(
            DownloadFailureReason.INVALID_URL,
            (factory(insecureAudio) as DownloadPlanResult.Rejected).reason,
        )
    }

    @Test
    fun `an AV1 video merges with HE-AAC from Android 14 and is refused before`() {
        val av1 = variant(container = "mp4").copy(
            codecs = listOf("av01.0.08M.08"),
            audioCompanion = companion().copy(codecs = listOf("mp4a.40.5")),
        )

        val android13 = factory(av1, sdkInt = 33) as DownloadPlanResult.Rejected
        val android14 = (factory(av1, sdkInt = 34) as DownloadPlanResult.Ready).request
            as DownloadRequest.Mux

        assertEquals(DownloadFailureReason.INCOMPATIBLE_TRACKS, android13.reason)
        assertEquals(listOf("av01.0.08M.08"), android14.plan.video.codecs)
        assertEquals(listOf("mp4a.40.5"), android14.plan.audio.codecs)
        assertEquals("video/mp4", android14.plan.outputMimeType)
    }

    @Test
    fun `youtube media files are fetched in bounded requests`() {
        val youTube = variant(url = "https://rr1---sn-b.googlevideo.com/videoplayback?itag=18")
        val elsewhere = variant(url = "https://googlevideo.com.example.test/clip.mp4")

        val youTubePlan = ((factory(youTube) as DownloadPlanResult.Ready).request
            as DownloadRequest.Direct).plan
        val otherPlan = ((factory(elsewhere) as DownloadPlanResult.Ready).request
            as DownloadRequest.Direct).plan

        assertEquals(WholeFileTrack.DEFAULT_MAX_REQUEST_BYTES, youTubePlan.maxRequestBytes)
        assertNull(otherPlan.maxRequestBytes)
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
    fun `invisible and direction-changing characters cannot disguise the extension`() {
        val asset = asset("clip\u202Egnp.exe\u200B\u0000 \uD83C\uDFAC")

        val name = DownloadPlanFactory.fileName(asset, variant(label = null, container = "mp4"))

        assertEquals("clip gnp exe.mp4", name)
        assertTrue(name.all { it.code in 0x20..0x7E })
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

    private fun companion(expiresAt: Long? = null) = CompanionAudio(
        mediaUrl = "https://rr3---sn-a.googlevideo.com/videoplayback?itag=140",
        mimeType = "audio/mp4",
        codecs = listOf("mp4a.40.2"),
        requestContext = BrowserRequestContext(
            pageUrl = "https://page.example.test/watch",
            userAgent = "fixture-agent",
            cookie = null,
        ),
        contentLengthBytes = 1_024,
        expiresAtEpochMs = expiresAt,
    )

    private fun factory(variant: MediaVariant, sdkInt: Int = 24): DownloadPlanResult =
        DownloadPlanFactory.create(
            asset = asset(),
            variant = variant,
            taskId = "task-1",
            nowEpochMs = 1_000L,
            sdkInt = sdkInt,
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
