package com.alal.yft.extractor.master.modules.youtube.ladder

import com.alal.yft.core.model.media.BrowserRequestContext
import com.alal.yft.extractor.api.SiteExtractionRequest
import com.alal.yft.extractor.api.SiteExtractionResult
import com.alal.yft.extractor.master.modules.youtube.MasterYouTubeModule
import com.alal.yft.extractor.master.modules.youtube.YouTubeUrls
import com.alal.yft.extractor.master.modules.youtube.ladder.ClientCensus.Mode
import com.alal.yft.extractor.master.modules.youtube.testing.FakeExtractorHttpClient
import com.alal.yft.extractor.master.modules.youtube.testing.FakePlayerScriptRunner
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Phase 1.3 D0, the bot-check rule (owner, 2026-10-15): the census sends R6's requests. Its first
 * request is R6's first request, byte for byte (address, headers in order, body). After a bot
 * check on it, the census sends R6's watch page GET and, when the page gave visitor data, R6's
 * visionOS request with that visitor data — nothing else to YouTube for that video — and V reads
 * one byte of a row that answer returned (a row the download reads). R6 is Master's module as the
 * app runs it: the copied extractor, the user's cookie removed, no solver.
 */
class CensusR6ParityTest {
    @Test
    fun `the census's first request is R6's first request`() = runTest {
        val r6 = CensusWorld().standard(Census.V1).http()
        val census = CensusWorld().standard(Census.V1).http()

        val result = MasterYouTubeModule(r6).extract(request(Census.V1))
        census(census).run(Mode.FULL, mapOf(1 to Census.V1), null)

        assertTrue(result is SiteExtractionResult.Success)
        assertEquals(listOf("VISIONOS"), r6.postedBodies.map(CensusWorld::clientOf))
        assertEquals(posts(r6).single(), posts(census).first())
        assertNoSignIn(r6, census)
    }

    @Test
    fun `after a bot check the census sends R6's watch page and R6's visionOS request with visitor data`() = runTest {
        val r6 = CensusWorld().standard(Census.V1).visionOsBotCheck(Census.V1).http()
        val census = CensusWorld().standard(Census.V1).visionOsBotCheck(Census.V1).sources().http()

        val result = MasterYouTubeModule(r6).extract(request(Census.V1))
        val outcome = census(census).run(Mode.FULL, mapOf(1 to Census.V1), null)

        // R6 today: visionOS, the watch page, the page's own client (MWEB), visionOS with visitor data.
        assertTrue(result is SiteExtractionResult.Success)
        assertEquals(listOf("VISIONOS", "MWEB", "VISIONOS"), r6.postedBodies.map(CensusWorld::clientOf))
        val r6Posts = posts(r6)
        assertEquals(listOf(r6Posts[0], r6Posts[2]), posts(census))
        assertEquals(Census.VISITOR, census.postedHeaders[1]["X-Goog-Visitor-Id"])
        assertEquals(gets(r6), youTubeGets(census))
        assertEquals(CensusSources.DOCUMENTS.map { it.url }, gets(census).map(Sent::url) - youTubeGets(census).map(Sent::url).toSet())
        assertEquals(1, census.probedUrls.size)
        assertTrue(census.probedUrls.single().startsWith("https://${Census.HOST}/videoplayback?"))
        assertNull(outcome.stopped)
        assertNoSignIn(r6, census)
    }

    @Test
    fun `when visionOS refuses again the census stops where R6 goes on, having sent only R6's requests`() = runTest {
        val r6 = CensusWorld().standard(Census.V1).visionOsBotCheck(Census.V1, always = true).http()
        val census = CensusWorld().standard(Census.V1).visionOsBotCheck(Census.V1, always = true).sources().http()

        MasterYouTubeModule(r6).extract(request(Census.V1))
        val outcome = census(census).run(Mode.FULL, mapOf(1 to Census.V1), null)

        // R6's chain runs as today (guardrail 4); the census stops after R6's third request.
        val r6Posts = posts(r6)
        assertEquals("VISIONOS", CensusWorld.clientOf(r6Posts[2].body!!))
        assertTrue(r6Posts.size > 3)
        assertEquals(listOf(r6Posts[0], r6Posts[2]), posts(census))
        assertEquals(gets(r6), gets(census))
        assertTrue(census.probedUrls.isEmpty())
        assertEquals("bot-check", outcome.stopped)
        assertNoSignIn(r6, census)
    }

    @Test
    fun `without visitor data on the page neither R6 nor the census asks visionOS again`() = runTest {
        fun world() = CensusWorld().standard(Census.V1).visionOsBotCheck(Census.V1).apply {
            get(Census.pageUrl(Census.V1), Census.page().replace("\"VISITOR_DATA\":\"${Census.VISITOR}\",", ""))
        }
        val r6 = world().http()
        val census = world().http()

        MasterYouTubeModule(r6).extract(request(Census.V1))
        val outcome = census(census).run(Mode.FULL, mapOf(1 to Census.V1), null)

        assertEquals(1, r6.postedBodies.count { CensusWorld.clientOf(it) == "VISIONOS" })
        assertEquals(listOf(posts(r6).first()), posts(census))
        assertEquals(gets(r6), youTubeGets(census))
        assertTrue(census.probedUrls.isEmpty())
        val notes = CensusJson.readState(outcome.stateJson).runs.single().notes
        assertTrue(notes.toString(), notes.contains("video 1: the watch page gave no visitor data, so visionOS was not asked again (as R6)"))
        assertTrue(outcome.report.contains("CHANGED visionos R0 video 1: bot check, answered as R6 (visionOS not asked again) → §7 step 8"))
    }

    private data class Sent(val url: String, val headers: List<Pair<String, String>>, val body: String? = null)

    private fun posts(http: FakeExtractorHttpClient): List<Sent> =
        http.postedUrls.indices.map { Sent(http.postedUrls[it], http.postedHeaders[it].toList(), http.postedBodies[it]) }

    private fun gets(http: FakeExtractorHttpClient): List<Sent> =
        http.requestedUrls.indices.map { Sent(http.requestedUrls[it], http.requestedHeaders[it].toList()) }

    private fun youTubeGets(http: FakeExtractorHttpClient): List<Sent> = gets(http).filter { it.url.startsWith(YOUTUBE) }

    private fun assertNoSignIn(vararg clients: FakeExtractorHttpClient) {
        for (http in clients) {
            for (headers in http.postedHeaders + http.requestedHeaders + http.probedHeaders) {
                assertTrue(headers.keys.toString(), headers.keys.none { it.equals("Cookie", true) || it.equals("Authorization", true) })
            }
        }
    }

    private fun census(http: FakeExtractorHttpClient) = ClientCensus(http, FakePlayerScriptRunner(), Census.UA, clock = { START })

    private fun request(id: String): SiteExtractionRequest {
        val page = YouTubeUrls.canonicalUrl(id)
        return SiteExtractionRequest(
            identity = requireNotNull(YouTubeUrls.identify(page)),
            requestContext = BrowserRequestContext(pageUrl = page, userAgent = Census.UA, cookie = COOKIE),
            nowEpochMs = START,
        )
    }

    private companion object {
        const val START = 1_791_000_000_000L
        const val COOKIE = "SID=census-cookie"
        const val YOUTUBE = "https://www.youtube.com/"
    }
}
