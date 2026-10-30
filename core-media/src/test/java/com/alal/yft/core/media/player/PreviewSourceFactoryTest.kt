package com.alal.yft.core.media.player

import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.dash.DashMediaSource
import androidx.media3.exoplayer.hls.HlsMediaSource
import androidx.media3.exoplayer.source.MergingMediaSource
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import com.alal.yft.core.model.media.BrowserRequestContext
import com.alal.yft.core.model.media.CompanionAudio
import com.alal.yft.core.model.media.MediaKind
import com.alal.yft.core.model.media.MediaTrackType
import com.alal.yft.core.model.media.MediaVariant
import com.alal.yft.core.model.media.VariantSupport
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@UnstableApi
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class PreviewSourceFactoryTest {
    private val factory = PreviewSourceFactory(OkHttpClient())

    @Test
    fun `creates the explicit Media3 source for each supported kind`() {
        assertTrue(factory.create(variant(MediaKind.DIRECT)) is ProgressiveMediaSource)
        assertTrue(factory.create(variant(MediaKind.HLS)) is HlsMediaSource)
        assertTrue(factory.create(variant(MediaKind.DASH)) is DashMediaSource)
    }

    @Test
    fun `rejects cleartext and credential-bearing URLs`() {
        assertThrows(IllegalArgumentException::class.java) {
            factory.create(variant(MediaKind.DIRECT, "http://media.example.test/video.mp4"))
        }
        assertThrows(IllegalArgumentException::class.java) {
            factory.create(
                variant(
                    MediaKind.DIRECT,
                    "https://user:password@media.example.test/video.mp4",
                ),
            )
        }
    }

    @Test
    fun `plays a video-only variant together with its companion audio`() {
        val merged = variant(MediaKind.DIRECT).copy(audioCompanion = companion())

        assertTrue(factory.create(merged) is MergingMediaSource)
        assertThrows(IllegalArgumentException::class.java) {
            factory.create(
                merged.copy(audioCompanion = companion("http://media.example.test/audio.m4a")),
            )
        }
    }

    @Test
    fun `plays an HLS video's audio rendition as HLS and a file's companion as a file`() {
        val hls = variant(MediaKind.HLS).copy(
            trackType = MediaTrackType.AUDIO_VIDEO,
            audioCompanion = companion("https://media.example.test/audio/index.m3u8?token=a"),
        )
        // P50: the rendition played as one progressive file, so the preview never started.
        val sources = factory.sources(hls)
        assertEquals(2, sources.size)
        assertTrue(sources[0] is HlsMediaSource)
        assertTrue(sources[1] is HlsMediaSource)
        assertTrue(factory.create(hls) is MergingMediaSource)

        // A playlist by its MIME type alone, as the HLS pairing names it.
        val byMime = companion("https://media.example.test/audio/rendition?id=7")
            .copy(mimeType = "application/x-mpegURL")
        assertTrue(factory.sources(hls.copy(audioCompanion = byMime))[1] is HlsMediaSource)

        val file = variant(MediaKind.DIRECT).copy(audioCompanion = companion())
        assertTrue(factory.sources(file)[1] is ProgressiveMediaSource)
        assertEquals(1, factory.sources(variant(MediaKind.HLS)).size)
    }

    @Test
    fun `plays a DASH page's sound from the manifest itself`() {
        // v1.0.2 item 59: the sound is another representation of the same manifest, which
        // plays its own sound; a second source would fetch the manifest twice.
        val dash = variant(MediaKind.DASH).copy(
            audioCompanion = companion("https://media.example.test/manifest.mpd")
                .copy(mimeType = "audio/mp4", representationId = "audio-1"),
        )
        val sources = factory.sources(dash)
        assertEquals(1, sources.size)
        assertTrue(sources.single() is DashMediaSource)
        assertTrue(factory.create(dash) is DashMediaSource)
    }

    @Test
    fun `tells a playlist companion from a file by its MIME type or its path`() {
        fun kind(url: String, mime: String?) =
            PreviewSourceFactory.companionKind("https://media.example.test/$url", mime)

        assertEquals(MediaKind.HLS, kind("a/index.m3u8", "audio/mp4"))
        assertEquals(MediaKind.HLS, kind("a/INDEX.M3U8?x=1.m4a", null))
        assertEquals(MediaKind.HLS, kind("a/audio", "application/vnd.apple.mpegurl"))
        assertEquals(MediaKind.HLS, kind("a/audio", " Application/X-MpegURL; charset=utf-8"))
        assertEquals(MediaKind.HLS, kind("a/audio", "audio/mpegurl"))
        assertEquals(MediaKind.HLS, kind("a/audio", "audio/x-mpegurl"))
        // Only the path counts, not a query that names a playlist.
        assertEquals(MediaKind.DIRECT, kind("a/audio?next=index.m3u8", "audio/mp4"))
        assertEquals(MediaKind.DIRECT, kind("a/audio.m4a", "audio/mp4"))
        assertEquals(MediaKind.DIRECT, kind("a/audio.m4a", null))
        assertEquals(MediaKind.DIRECT, kind("a/audio.mp3", "audio/mpeg"))
    }

    @Test
    fun `rejects a variant marked with unsupported codec`() {
        assertThrows(IllegalArgumentException::class.java) {
            factory.create(
                variant(MediaKind.HLS).copy(
                    support = VariantSupport.UNSUPPORTED_CODEC,
                ),
            )
        }
    }

    @Test
    fun `header policy retains same origin context and strips it cross origin`() {
        val policy = PreviewHttpPolicy(
            credentialOrigin = "https://media.example.test/master.m3u8".toHttpUrl(),
            requestContext = BrowserRequestContext(
                pageUrl = "https://page.example.test/watch",
                userAgent = "fixture-agent",
                cookie = "session=private",
                observedHeaders = mapOf(
                    "Authorization" to "Bearer private",
                    "Accept-Language" to "en",
                ),
            ),
        )

        val sameOrigin = policy.headersFor(
            "https://media.example.test/segment.ts".toHttpUrl(),
        )
        val crossOrigin = policy.headersFor(
            "https://cdn.example.test/segment.ts".toHttpUrl(),
        )

        assertEquals("session=private", sameOrigin["Cookie"])
        assertEquals("Bearer private", sameOrigin["Authorization"])
        assertFalse(crossOrigin.keys.any { it.equals("Cookie", ignoreCase = true) })
        assertFalse(crossOrigin.keys.any { it.equals("Authorization", ignoreCase = true) })
        assertEquals("fixture-agent", crossOrigin["User-Agent"])
        assertEquals("en", crossOrigin["Accept-Language"])
    }

    private fun companion(
        url: String = "https://media.example.test/audio.m4a",
    ): CompanionAudio = CompanionAudio(
        mediaUrl = url,
        mimeType = "audio/mp4",
        codecs = listOf("mp4a.40.2"),
        requestContext = BrowserRequestContext(
            pageUrl = "https://page.example.test/watch",
            userAgent = "fixture-agent",
            cookie = null,
        ),
    )

    private fun variant(
        kind: MediaKind,
        url: String = when (kind) {
            MediaKind.HLS -> "https://media.example.test/master.m3u8"
            MediaKind.DASH -> "https://media.example.test/manifest.mpd"
            else -> "https://media.example.test/video.mp4"
        },
    ): MediaVariant = MediaVariant(
        id = "fixture-$kind",
        playbackUrl = url,
        kind = kind,
        trackType = MediaTrackType.AUDIO_VIDEO,
        requestContext = BrowserRequestContext(
            pageUrl = "https://page.example.test/watch",
            userAgent = "fixture-agent",
            cookie = "session=private",
        ),
    )
}