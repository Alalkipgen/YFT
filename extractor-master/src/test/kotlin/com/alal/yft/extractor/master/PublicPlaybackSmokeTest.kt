package com.alal.yft.extractor.master

import com.alal.yft.core.model.media.BrowserRequestContext
import com.alal.yft.extractor.api.SiteExtractionFailure
import com.alal.yft.extractor.api.json.BoundedJsonParser
import com.alal.yft.extractor.api.json.asArrayOrEmpty
import com.alal.yft.extractor.api.json.asBooleanOrNull
import com.alal.yft.extractor.api.json.asDoubleOrNull
import com.alal.yft.extractor.api.json.asStringOrNull
import com.alal.yft.extractor.api.json.get
import java.nio.file.Files
import java.nio.file.Path
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

/**
 * Opt-in real HTTPS smoke from an actual browser-produced snapshot, not committed live data.
 * Default offline runs skip this one test. Set YFT_MASTER_SMOKE_SNAPSHOT to run it.
 */
class PublicPlaybackSmokeTest {
    @Test
    fun `actual public playback capture produces a checked fallback candidate`() = runBlocking {
        val input = System.getenv("YFT_MASTER_SMOKE_SNAPSHOT")
        assumeTrue("Optional real browser snapshot was not supplied", !input.isNullOrBlank())
        val path = Path.of(checkNotNull(input))
        require(Files.size(path) <= 64 * 1024)
        val data = checkNotNull(BoundedJsonParser.parse(Files.readString(path)))
        val page = checkNotNull(data["pageUrl"].asStringOrNull)
        val playing = checkNotNull(data["playingMediaUrl"].asStringOrNull)
        require(page == "https://interactive-examples.mdn.mozilla.net/pages/tabbed/video.html")
        require(
            playing == "https://interactive-examples.mdn.mozilla.net/media/cc0-videos/flower.mp4",
        )
        require(data["authorizedPlayback"].asBooleanOrNull == true)
        require(data["playback"]["played"].asBooleanOrNull == true)
        require(checkNotNull(data["playback"]["currentTime"].asDoubleOrNull) > 0)
        val store = InMemoryCaptureStore()
        store.navigate(page, 1)
        data["requests"].asArrayOrEmpty.forEach { observed ->
            val url = checkNotNull(observed["url"].asStringOrNull)
            require(url == playing)
            val context = observed["context"]
            val referer = context["observedHeaders"]["Referer"].asStringOrNull
            store.recordRequest(
                1,
                CapturedRequest(
                    url = url,
                    mimeType = observed["mimeType"].asStringOrNull,
                    context = BrowserRequestContext(
                        context["pageUrl"].asStringOrNull,
                        context["userAgent"].asStringOrNull,
                        cookie = null,
                        observedHeaders = referer?.let { mapOf("Referer" to it) }.orEmpty(),
                    ),
                    observedAtEpochMs = System.currentTimeMillis(),
                ),
            )
        }
        store.playing(1, playing, true)
        val result = MasterFallbackEngine(
            OkHttpMediaValidator(OkHttpClient()), store, MasterPolicy(enabled = true),
        ).extract(
            MasterRequest(
                page, 1, System.currentTimeMillis(), SiteExtractionFailure.RESPONSE_CHANGED,
                snapshot = PageSnapshot(page, 1, apiResponses = listOf("{}")),
            ),
        ) as MasterResult.Success
        assertEquals(MasterStage.PLAYBACK_CAPTURE, result.stage)
        assertEquals(playing, result.result.candidates.single().mediaUrl)
        assertTrue(checkNotNull(result.result.candidates.single().contentLengthBytes) > 0)
        assertTrue(result.result.candidates.single().mimeType?.startsWith("video/mp4") == true)
        // No live URL, body or session is logged or committed.
        println("Public playback capture + independent HTTPS prefix validation: PASS")
    }
}