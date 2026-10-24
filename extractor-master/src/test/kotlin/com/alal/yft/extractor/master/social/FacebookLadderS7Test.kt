package com.alal.yft.extractor.master.social

import com.alal.yft.extractor.api.ExtractorHttpClient
import com.alal.yft.extractor.api.ExtractorHttpResult
import com.alal.yft.extractor.api.ExtractorProbeResult
import com.alal.yft.extractor.api.SiteExtractionFailure
import com.alal.yft.extractor.api.SiteExtractionResult
import com.alal.yft.extractor.api.SitePageIdentity
import com.alal.yft.extractor.master.MasterFallbackEngine
import com.alal.yft.extractor.master.MasterPolicy
import com.alal.yft.extractor.master.MasterResult
import com.alal.yft.extractor.master.MasterStage
import com.alal.yft.extractor.master.RecordingValidator
import com.alal.yft.extractor.master.contract.CountingCapture
import com.alal.yft.extractor.master.contract.SiteContracts
import com.alal.yft.extractor.master.contract.answering
import com.alal.yft.extractor.master.contract.contractRequest
import com.alal.yft.extractor.master.contract.mainRequest
import com.alal.yft.extractor.master.lookup.AnswerMark
import com.alal.yft.extractor.master.lookup.AskLabels
import com.alal.yft.extractor.master.lookup.AskReading
import com.alal.yft.extractor.master.lookup.LookupAsk
import com.alal.yft.extractor.master.lookup.LookupRecord
import com.alal.yft.extractor.master.lookup.LookupRecorder
import com.alal.yft.extractor.master.modules.youtube.testing.FakeExtractorHttpClient
import com.alal.yft.extractor.sites.facebook.FacebookExtractor
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Social plan S1a (5), S7: stops and budgets for Facebook's ladder. No endpoint is asked twice in
 * a lookup, by main or by Master; a 429 or a bot check (the plugin player's, main's, a checkpoint
 * address) cools F1 for 30 minutes across lookups while F2 still reads main's pages; a bot check
 * main met stops F1's host in the lookup; the lookup's request, time and byte budgets (main's asks
 * and time included) stop F1 before it asks, and F1 reads at most the bytes and waits at most the
 * time left. Lines: short labels, never a link, an ID or a page's words. Main's real Facebook
 * adapter over synthetic pages, a fake clock; no live calls.
 */
class FacebookLadderS7Test {
    @Test
    fun `no endpoint is asked twice in a lookup, by main or by Master`() = runTest {
        val watch = identify(WATCH)
        val main = answering(watch.canonicalPageUrl to movedKeyPage(VIDEO), contentType = HTML)
        val plugin = answering(embedUrl(watch) to NO_DATA, contentType = HTML)
        val ladder = ladder(plugin)
        val record = record()

        // Two stages in one lookup: the plugin player is asked once, the second ask refused.
        val (first, again) = withContext(record + MainPages()) {
            val request = contractRequest(WATCH, watch, main(main), COOKIE)
            engine(plugin, ladder).extract(request) to engine(plugin, ladder).extract(request)
        }
        assertEquals(listOf(embedUrl(watch)), plugin.requestedUrls)
        assertEquals(1, record.masterAsks)
        assertTrue(details(first).toString(), "$LADDER after RESPONSE_CHANGED; replayed 0; new 1" in details(first))
        assertTrue(details(again).toString(), "$LADDER after RESPONSE_CHANGED; replayed 0; new 0; refused 1" in details(again))
        // F2 still reads main's page each time (no request).
        listOf(first, again).forEach { assertEquals(MasterStage.PAGE_DATA, (it as MasterResult.Success).stage) }

        // Main asked the plugin player's endpoint earlier in the lookup (with the user's session):
        // Master does not ask it again.
        val unasked = answering(embedUrl(watch) to NO_DATA, contentType = HTML)
        val mains = record()
        val after = withContext(mains + MainPages()) {
            val failure = main(main)
            LookupRecorder(answering(embedUrl(watch) to NO_DATA, contentType = HTML))
                .get(embedUrl(watch), mapOf("Cookie" to COOKIE), 1024)
            engine(unasked, ladder(unasked)).extract(contractRequest(WATCH, watch, failure, COOKIE))
        }
        assertTrue(unasked.requestedUrls.isEmpty())
        assertEquals(0, mains.masterAsks)
        assertTrue(details(after).toString(), "$LADDER after RESPONSE_CHANGED; replayed 0; new 0; refused 1" in details(after))
        (listOf(first, again, after)).forEach { assertBare(details(it)) }
    }

    @Test
    fun `F1's 429 or checkpoint cools F1 for 30 minutes across lookups, F2 still reads`() = runTest {
        val watch = identify(WATCH)
        val main = answering(watch.canonicalPageUrl to movedKeyPage(VIDEO), contentType = HTML)
        val answers = listOf(
            ExtractorHttpResult.Failure(SiteExtractionFailure.RATE_LIMITED, 429) to
                "$F1: RATE_LIMITED (status 429); next: STOP_SITE",
            ExtractorHttpResult.Success(200, CHECKPOINT_PAGE, CHECKPOINT, HTML) to
                "$F1: BOT_CHECK (final address: checkpoint); next: STOP_SITE",
        )
        for ((answer, line) in answers) {
            val clock = Clock()
            val plugin = FakeExtractorHttpClient(responses = mapOf(embedUrl(watch) to answer))
            val ladder = ladder(plugin, SocialCooldown({ clock.now }))

            val stopped = lookup(main, plugin, ladder, record(clock))
            assertTrue(details(stopped).toString(), line in details(stopped))

            // 29 minutes later, another lookup: F1 cools, F2 reads main's page.
            clock.now += 29 * MINUTE
            val cooling = lookup(main, plugin, ladder, record(clock)) as MasterResult.Success
            assertEquals(MasterStage.PAGE_DATA, cooling.stage)
            assertEquals(setOf(hd(VIDEO), sd(VIDEO)), cooling.result.candidates.map { it.mediaUrl }.toSet())
            assertEquals(1, plugin.requestedUrls.size)
            assertTrue(cooling.result.details.toString(), "$F1: cooling down after a 429 or a bot check; not asked" in cooling.result.details)
            assertFalse(cooling.result.details.any { it.startsWith("$LADDER after") })

            // 31 minutes after the stop: F1 asks again.
            clock.now += 2 * MINUTE
            val again = lookup(main, plugin, ladder, record(clock))
            assertEquals(2, plugin.requestedUrls.size)
            listOf(stopped, cooling, again).forEach { assertBare(details(it)) }
        }
    }

    @Test
    fun `main's 429 in a lookup cools F1 in the next ones, and a bot check main met stops F1 in its own`() = runTest {
        val watch = identify(WATCH)
        val clock = Clock()
        val plugin = answering(embedUrl(watch) to NO_DATA, contentType = HTML)
        val ladder = ladder(plugin, SocialCooldown({ clock.now }))
        val limited = FakeExtractorHttpClient(fallback = ExtractorHttpResult.Failure(SiteExtractionFailure.RATE_LIMITED, 429))
        val main = answering(watch.canonicalPageUrl to movedKeyPage(VIDEO), contentType = HTML)

        // Main's 429 in the lookup (an ask after its page; a 429 main fails with is final, before
        // Master: the app notes it): nothing new goes out in its lookup (route B's rule), nor 10
        // minutes later.
        val first = withContext(record(clock) + MainPages()) {
            val failure = main(main)
            LookupRecorder(limited).get(LIMITED_PAGE, emptyMap(), 1024)
            engine(plugin, ladder).extract(contractRequest(WATCH, watch, failure, COOKIE))
        }
        assertTrue(details(first).toString(), "$LADDER after RESPONSE_CHANGED; replayed 0; new 0; refused 1" in details(first))
        clock.now += 10 * MINUTE
        val next = lookup(main, plugin, ladder, record(clock)) as MasterResult.Success
        assertTrue(next.result.details.toString(), "$F1: cooling down after a 429 or a bot check; not asked" in next.result.details)
        assertEquals(MasterStage.PAGE_DATA, next.stage)
        assertTrue(plugin.requestedUrls.isEmpty())

        // A bot check main met in this lookup (labels that read main's page so): F1's host is
        // stopped here, nothing asked; then it cools for the next lookup.
        val botCheck = object : AskLabels {
            override fun way(ask: LookupAsk): String = "page"

            override fun read(ask: LookupAsk, way: String?, result: ExtractorHttpResult.Success) = AskReading(AnswerMark.BOT_CHECK)
        }
        val other = Clock()
        val fresh = answering(embedUrl(watch) to NO_DATA, contentType = HTML)
        val freshLadder = ladder(fresh, SocialCooldown({ other.now }))
        val checked = record(other, botCheck)
        val stopped = lookup(main, fresh, freshLadder, checked) as MasterResult.Success
        assertEquals(1, checked.botChecks)
        assertTrue(stopped.result.details.toString(), "$F1: the host stopped in this lookup (a bot check); not asked" in stopped.result.details)
        other.now += 29 * MINUTE
        val later = lookup(main, fresh, freshLadder, record(other))
        assertTrue(details(later).toString(), "$F1: cooling down after a 429 or a bot check; not asked" in details(later))
        assertTrue(fresh.requestedUrls.isEmpty())
        listOf(first, next, stopped, later).forEach { assertBare(details(it)) }
    }

    @Test
    fun `the lookup's request and time budgets stop F1 before it asks, F1 waits at most the time left`() = runTest {
        val watch = identify(WATCH)
        val main = answering(watch.canonicalPageUrl to movedKeyPage(VIDEO), contentType = HTML)

        // Main's page and three more of main's asks: four asks, the lookup's request budget.
        val plugin = answering(embedUrl(watch) to NO_DATA, contentType = HTML)
        val record = record()
        val busy = withContext(record + MainPages()) {
            val failure = main(main)
            repeat(3) { LookupRecorder(main).get("https://www.facebook.com/fixture/$it", emptyMap(), 1024) }
            engine(plugin, ladder(plugin)).extract(contractRequest(WATCH, watch, failure, COOKIE)) as MasterResult.Success
        }
        assertEquals(4, record.mainAsks)
        assertTrue(plugin.requestedUrls.isEmpty())
        assertTrue(busy.result.details.toString(), "$F1: over the lookup's request budget; not asked" in busy.result.details)
        assertEquals(MasterStage.PAGE_DATA, busy.stage)

        // Main took 21 seconds: over the lookup's 20.
        val clock = Clock()
        val late = withContext(record(clock) + MainPages()) {
            val failure = main(main)
            clock.now += 21_000
            engine(plugin, ladder(plugin)).extract(contractRequest(WATCH, watch, failure, COOKIE))
        }
        assertTrue(details(late).toString(), "$F1: over the lookup's time budget; not asked" in details(late))
        assertTrue(plugin.requestedUrls.isEmpty())

        // Main took 19 seconds: F1 waits one second, not its six.
        val slow = Slow(2_000)
        val waited = withContext(record(clock) + MainPages()) {
            val failure = main(main)
            clock.now += 19_000
            engine(slow, ladder(slow)).extract(contractRequest(WATCH, watch, failure, COOKIE))
        }
        assertEquals(1, slow.asked)
        assertTrue(details(waited).toString(), "$F1: no answer in time" in details(waited))
        listOf(busy, late, waited).forEach { assertBare(details(it)) }
    }

    @Test
    fun `F1 reads at most the lookup's bytes left, then the byte budget stops it`() = runTest {
        val watch = identify(WATCH)
        val main = answering(watch.canonicalPageUrl to movedKeyPage(VIDEO), contentType = HTML)
        // The answer is over the 100 characters left: the client stops at the bound.
        val plugin = FakeExtractorHttpClient(
            responses = mapOf(embedUrl(watch) to ExtractorHttpResult.Failure(SiteExtractionFailure.RESPONSE_TOO_LARGE)),
        )
        val ladder = ladder(plugin, budget = SocialBudget(requests = 4, bytes = 100, millis = 20_000))
        val record = record()

        val (first, again) = withContext(record + MainPages()) {
            val request = contractRequest(WATCH, watch, main(main), COOKIE)
            engine(plugin, ladder).extract(request) to engine(plugin, ladder).extract(request)
        }
        assertEquals(listOf(100L), plugin.requestedBodyLimits)
        assertEquals(1, record.masterAsks)
        assertTrue(details(first).toString(), "$F1: TRANSIENT (failed: RESPONSE_TOO_LARGE); next: NEXT_RUNG" in details(first))
        assertTrue(details(again).toString(), "$F1: over the lookup's byte budget; not asked" in details(again))
        // F2 reads main's page both times (no request).
        listOf(first, again).forEach {
            assertEquals(MasterStage.PAGE_DATA, (it as MasterResult.Success).stage)
            assertBare(details(it))
        }

        // Facebook's own budget: the plugin player's bound (its recipe's) is not cut.
        val roomy = answering(embedUrl(watch) to NO_DATA, contentType = HTML)
        lookup(main, roomy, ladder(roomy), record())
        assertEquals(listOf(6L * 1024 * 1024), roomy.requestedBodyLimits)
    }

    /** One lookup: main's real adapter over [main] (its pages kept), then Master's engine. */
    private suspend fun lookup(
        main: FakeExtractorHttpClient,
        plugin: ExtractorHttpClient,
        ladder: FacebookLadder,
        record: LookupRecord,
        capture: CountingCapture = CountingCapture(),
    ): MasterResult = withContext(record + MainPages()) {
        engine(plugin, ladder, capture).extract(contractRequest(WATCH, identify(WATCH), main(main), COOKIE))
    }

    /** Main's real Facebook adapter over [main] in the current lookup: its failure. */
    private suspend fun main(main: FakeExtractorHttpClient): SiteExtractionFailure {
        val answer = FacebookExtractor(MainPageRecorder(LookupRecorder(main))).extract(mainRequest(identify(WATCH), COOKIE))
        return (answer as SiteExtractionResult.Failure).reason
    }

    private fun ladder(
        http: ExtractorHttpClient,
        cooldown: SocialCooldown = SocialCooldown(),
        budget: SocialBudget = SocialBudget.FACEBOOK,
    ) = FacebookLadder(http, ::identifyOrNull, cooldown = cooldown, budget = budget)

    private fun engine(http: ExtractorHttpClient, ladder: FacebookLadder, capture: CountingCapture = CountingCapture()) =
        MasterFallbackEngine(
            RecordingValidator(), capture, MasterPolicy(enabled = true),
            contracts = SiteContracts(http),
            social = ladder,
        )

    private fun record(clock: Clock? = null, labels: AskLabels = SiteWays.FACEBOOK.labels()): LookupRecord =
        if (clock == null) LookupRecord(labels = labels) else LookupRecord(clock = { clock.now }, labels = labels)

    private fun details(result: MasterResult): List<String> = when (result) {
        is MasterResult.Success -> result.result.details
        is MasterResult.Failure -> result.details
        is MasterResult.NeedsPlayback -> result.details
        else -> error("unexpected $result")
    }

    /** A fake clock (milliseconds). */
    private class Clock(var now: Long = START)

    /** The plugin player answering after [millis] (the test's virtual time). */
    private class Slow(private val millis: Long) : ExtractorHttpClient {
        var asked = 0
            private set

        override suspend fun get(url: String, headers: Map<String, String>, maxBodyBytes: Long): ExtractorHttpResult {
            asked++
            delay(millis)
            return ExtractorHttpResult.Success(200, NO_DATA, url, HTML)
        }

        override suspend fun postJson(
            url: String,
            body: String,
            headers: Map<String, String>,
            maxBodyBytes: Long,
        ): ExtractorHttpResult = ExtractorHttpResult.Failure(SiteExtractionFailure.NETWORK)

        override suspend fun probe(url: String, headers: Map<String, String>, timeoutMillis: Long): ExtractorProbeResult =
            ExtractorProbeResult.Unsupported
    }

    /** No ID, link or page words in the lines. */
    private fun assertBare(details: List<String>) {
        details.forEach { line ->
            listOf(VIDEO, OTHER_VIDEO, "facebook.com", "example-cdn", "checkpoint/", "fixture").forEach {
                assertFalse("$line has $it", line.contains(it))
            }
        }
    }

    private companion object {
        const val START = 1_800_000_000_000L
        const val MINUTE = 60_000L
        const val VIDEO = "1000000000000003"
        const val OTHER_VIDEO = "1000000000000009"
        const val WATCH = "https://www.facebook.com/watch/?v=$VIDEO"
        const val CHECKPOINT = "https://www.facebook.com/checkpoint/fixture/"
        const val LIMITED_PAGE = "https://www.facebook.com/fixture/limited"
        const val COOKIE = "c_user=0; xs=fixture-cookie"
        const val HTML = "text/html"
        const val LADDER = "master: facebook ladder"
        const val F1 = "master: facebook F1 plugin player"
        const val NO_DATA = "<html><head><title>Facebook</title></head><body>no data</body></html>"
        const val CHECKPOINT_PAGE = "<html><head><title>Facebook</title></head><body>security check</body></html>"

        fun identifyOrNull(url: String): SitePageIdentity? = FacebookExtractor(FakeExtractorHttpClient()).identify(url)

        fun identify(url: String): SitePageIdentity = checkNotNull(identifyOrNull(url))

        fun encode(value: String): String = URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20")

        fun embedUrl(identity: SitePageIdentity) =
            "https://www.facebook.com/plugins/video.php?href=" + encode(identity.canonicalPageUrl)

        fun hd(video: String) = "https://video.example-cdn.test/v/$video-hd.mp4?oe=F2A52380&oh=fixture"

        fun sd(video: String) = "https://video.example-cdn.test/v/$video-sd.mp4?oe=F2A52380&oh=fixture"

        /** Main's page with the video's files under keys main does not know, a related video's beside them. */
        fun movedKeyPage(video: String): String =
            "<html><head><title>Facebook</title></head><body>" +
                "<script type=\"application/json\" data-sjs>{\"require\":[[\"VideoPlayerRelay\",null,null,[{" +
                "\"video\":{\"video_id\":\"$video\",\"browser_native_hd_url_v2\":\"${hd(video)}\"," +
                "\"browser_native_sd_url_v2\":\"${sd(video)}\"},\"related\":[{\"video_id\":\"$OTHER_VIDEO\"," +
                "\"browser_native_hd_url_v2\":\"${hd(OTHER_VIDEO)}\"}]}]]]}</script></body></html>"
    }
}
