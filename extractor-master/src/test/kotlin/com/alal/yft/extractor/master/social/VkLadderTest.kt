package com.alal.yft.extractor.master.social

import com.alal.yft.core.model.logging.DiagnosticTextSanitizer
import com.alal.yft.extractor.api.ExtractorHttpClient
import com.alal.yft.extractor.api.ExtractorHttpResult
import com.alal.yft.extractor.api.SiteExtractionFailure
import com.alal.yft.extractor.api.SiteExtractionResult
import com.alal.yft.extractor.api.SitePageIdentity
import com.alal.yft.extractor.master.MasterFallbackEngine
import com.alal.yft.extractor.master.MasterPolicy
import com.alal.yft.extractor.master.MasterRequest
import com.alal.yft.extractor.master.MasterResult
import com.alal.yft.extractor.master.MasterStage
import com.alal.yft.extractor.master.NOW
import com.alal.yft.extractor.master.RecordingValidator
import com.alal.yft.extractor.master.contract.AGENT
import com.alal.yft.extractor.master.contract.CountingCapture
import com.alal.yft.extractor.master.contract.SiteContracts
import com.alal.yft.extractor.master.contract.contractRequest
import com.alal.yft.extractor.master.contract.mainRequest
import com.alal.yft.extractor.master.fixture
import com.alal.yft.extractor.master.lookup.LookupRecord
import com.alal.yft.extractor.master.lookup.LookupRecorder
import com.alal.yft.extractor.master.modules.youtube.testing.FakeExtractorHttpClient
import com.alal.yft.extractor.master.recipes.ContractMedia
import com.alal.yft.extractor.master.recipes.ContractRecipe
import com.alal.yft.extractor.master.recipes.ContractRecipes
import com.alal.yft.extractor.sites.vk.VkExtractor
import java.net.URI
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * VK-Master plan §4 (V1–V3), Appendix B step 3: VK's ladder in Master's engine, after main's real
 * VK adapter (`extractor-sites`, as the app builds it) failed in the same lookup over made-up
 * answers (`fixtures/vk-pack/`). V1 re-reads main's kept answers with no new ask: rows bound to the
 * video (a renamed key by the shape scan; never the trailer, a picture or another video's file)
 * checked by V as VK's answer; VK's deleted icon final, any other restriction not; a bot check or a
 * 429 stops VK and cools its host. V2 heals only after the adult answer and a refused value: one
 * token held in memory and one `video.get`, with the lookup's user agent and no cookie, within VK's
 * budget and the lookup's time, under route B's rules (main's identical token answer replayed,
 * main's `video.get` never asked again). Rungs off by the config; claims and guards; every line
 * short labels that the sanitizer keeps whole. JVM only, no network; VK's recipe is the test's own
 * until step 4's.
 */
class VkLadderTest {
    @Test
    fun `V1 reads main's video get with no new ask - renamed keys by the shape scan, rows checked as VK's answer`() = runTest {
        val master = vk()
        val lookup = lookup(vk(api = answers("video_get_renamed.json")), ladder(master))

        assertEquals(SiteExtractionFailure.NO_MEDIA_FOUND, lookup.mainFailure)
        val success = lookup.result as MasterResult.Success
        assertEquals(MasterStage.PAGE_DATA, success.stage)
        val rows = success.result.candidates
        assertEquals(
            setOf("$API_FILES/hd.mp4", "$API_FILES/sd.mp4", "$API_FILES/video.m3u8"),
            rows.map { it.mediaUrl.substringBefore('?') }.toSet(),
        )
        rows.forEach { row ->
            assertEquals(KEY, row.videoId)
            assertEquals("Fixture renamed", row.title)
            assertEquals(61_000L, row.durationMillis)
            assertEquals("https://iv.okcdn.ru/fixture/large.jpg", row.thumbnailUrl)
            assertEquals(1_900_000_000_000L, row.expiresAtEpochMs)
        }
        assertEquals(3, lookup.validator.seen.size)
        assertEquals(0, lookup.capture.calls)
        assertNothingAsked(master)
        assertEquals(3, lookup.record.mainAsks)
        assertEquals(0, lookup.record.masterAsks)
        assertEquals(
            listOf(
                "$LADDER after NO_MEDIA_FOUND; main's answers re-read, no new ask",
                ADULT,
                "$V1 video.get: PARTIAL (rows 3 by the shape scan, bound to the video); next: CHECK_ROWS",
                "$ITEM: MP4 files 2, HLS 1, DASH 0 (the shape scan); title known; length known",
            ),
            lookup.lines,
        )
        assertTrue(lookup.details.toString(), "master: vk contract: 3 files from the key table" in lookup.details)
        assertBare(lookup)
    }

    @Test
    fun `V1 reads main's embed pages - B and B2 alike, the deciding one shown, never the trailer or another video's file`() = runTest {
        val renamed = fixture("vk-pack/embed_files.html")
            .replace("\"mp4_", "\"stream_")
            .replace("\"hls\"", "\"stream_list\"")
            .replace("\"dash_sep\"", "\"stream_dash\"")
        val master = vk()
        val lookup = lookup(vk(embed = { page(renamed, it) }), ladder(master))

        assertEquals(SiteExtractionFailure.NO_MEDIA_FOUND, lookup.mainFailure)
        val success = lookup.result as MasterResult.Success
        assertEquals(MasterStage.PAGE_DATA, success.stage)
        assertEquals(
            setOf("$EMBED_FILES/720.mp4", "$EMBED_FILES/360.mp4", "$EMBED_FILES/video.m3u8", "$EMBED_FILES/video.mpd"),
            success.result.candidates.map { it.mediaUrl.substringBefore('?') }.toSet(),
        )
        success.result.candidates.forEach { row ->
            assertEquals(KEY, row.videoId)
            assertEquals("Fixture embed \"title\"", row.title)
            assertEquals(42_000L, row.durationMillis)
        }
        assertNothingAsked(master)
        assertEquals(2, lookup.record.mainAsks)
        assertEquals(
            listOf(
                "$LADDER after NO_MEDIA_FOUND; main's answers re-read, no new ask",
                "$V1 embed: PARTIAL (rows 4 by the shape scan, bound to the video); next: CHECK_ROWS; " +
                    "2 answers, the deciding one shown",
                "$ITEM: MP4 files 2, HLS 1, DASH 1 (the shape scan); title known; length known",
            ),
            lookup.lines,
        )
        assertBare(lookup)
    }

    @Test
    fun `VK's deleted icon is the lookup's final answer, another restriction never`() = runTest {
        val master = vk()
        val deleted = lookup(vk(api = answers("video_get_deleted.json")), ladder(master))

        assertEquals(SiteExtractionFailure.NO_MEDIA_FOUND, deleted.mainFailure)
        val failure = deleted.result as MasterResult.Failure
        assertEquals(SiteExtractionFailure.PRIVATE_OR_UNAVAILABLE, failure.reason)
        assertEquals(
            listOf(
                "$LADDER after NO_MEDIA_FOUND; main's answers re-read, no new ask",
                ADULT,
                "$V1 video.get: REMOVED (restriction: can_play 0, VK's deleted icon); next: FINAL",
            ),
            deleted.lines,
        )
        assertTrue(failure.details.toString(), "master: page reports PRIVATE_OR_UNAVAILABLE" in failure.details)
        assertEquals(0, deleted.capture.calls)

        val locked = fixture("vk-pack/video_get_deleted.json").replace("delete_outline", "lock_outline")
        val other = lookup(vk(api = { json(locked, it) }), ladder(master))

        assertTrue(other.result.toString(), other.result is MasterResult.NeedsPlayback)
        assertEquals(
            listOf(
                "$LADDER after NO_MEDIA_FOUND; main's answers re-read, no new ask",
                ADULT,
                "$V1 video.get: TRANSIENT (restriction: can_play 0, its kind unknown); next: NEXT_RUNG",
            ),
            other.lines,
        )
        assertEquals(1, other.capture.calls)
        assertNothingAsked(master)
        assertBare(deleted)
        assertBare(other)
    }

    @Test
    fun `a bot check or a 429 in main's answers stops VK - its host cools, V2 asks nothing`() = runTest {
        val master = vk()
        val cooldown = SocialCooldown()
        val captcha = lookup(vk(api = answers("video_get_captcha.json")), ladder(master, cooldown = cooldown))

        assertEquals(SiteExtractionFailure.NO_MEDIA_FOUND, captcha.mainFailure)
        assertTrue(captcha.result.toString(), captcha.result is MasterResult.NeedsPlayback)
        assertEquals(
            listOf(
                "$LADDER after NO_MEDIA_FOUND; main's answers re-read, no new ask",
                ADULT,
                "$V1 video.get: BOT_CHECK (API error 14 (captcha)); next: STOP_SITE",
            ),
            captcha.lines,
        )
        assertTrue(cooldown.cooling(VkClient.API_HOST))
        assertFalse(cooldown.cooling(VkClient.TOKEN_HOST))

        // vk.com's 429, then vk.ru's adult answer and a refused token: the stop decides, vk.com cools.
        val hosts = SocialCooldown()
        val limited = lookup(
            vk(
                embed = { url ->
                    if (URI(url).host == VkClient.COM_HOST) {
                        ExtractorHttpResult.Failure(SiteExtractionFailure.RATE_LIMITED, 429)
                    } else {
                        page(fixture("vk-pack/embed_adult.html"), url)
                    }
                },
                token = refused(401),
            ),
            ladder(master, cooldown = hosts),
        )

        assertEquals(SiteExtractionFailure.NO_MEDIA_FOUND, limited.mainFailure)
        assertTrue(limited.record.rateLimited)
        assertEquals(
            listOf(
                "$LADDER after NO_MEDIA_FOUND; main's answers re-read, no new ask",
                "$V1 embed: RATE_LIMITED (status 429); next: STOP_SITE; 2 answers, the deciding one shown",
                TOKEN_REFUSED,
            ),
            limited.lines,
        )
        assertTrue(hosts.cooling(VkClient.COM_HOST))
        assertFalse(hosts.cooling(VkClient.RU_HOST))
        assertNothingAsked(master)
        assertBare(captcha)
        assertBare(limited)
    }

    @Test
    fun `V2 heals after main's token was refused - one token in memory, one video get, no cookie, its rows checked`() = runTest {
        val master = vk()
        val lookup = lookup(vk(token = refused(401)), ladder(master), cookie = COOKIE)

        assertEquals(SiteExtractionFailure.NO_MEDIA_FOUND, lookup.mainFailure)
        val success = lookup.result as MasterResult.Success
        assertEquals(MasterStage.CONTRACT, success.stage)
        assertEquals(
            setOf("$API_FILES/720.mp4", "$API_FILES/360.mp4", "$API_FILES/video.m3u8", "$API_FILES/video.mpd"),
            success.result.candidates.map { it.mediaUrl.substringBefore('?') }.toSet(),
        )
        success.result.candidates.forEach { assertEquals(KEY, it.videoId) }
        assertEquals(4, lookup.validator.seen.size)

        assertEquals(listOf(VkClient.TOKEN_URL), master.postedUrls)
        assertTrue("the healed values' body", master.postedBodies.single() == HEALED.tokenBody)
        assertEquals(listOf(VkClient.headers(AGENT, null).toList()), master.postedHeaders.map { it.toList() })
        assertEquals(listOf(HEALED.videoGetUrl(LINK)), master.requestedUrls)
        val token = checkNotNull(VkClient.token(fixture("vk-pack/token_okay.json"), NOW))
        assertEquals(listOf(VkClient.headers(AGENT, token).toList()), master.requestedHeaders.map { it.toList() })
        (master.postedHeaders + master.requestedHeaders).forEach { headers ->
            assertFalse(headers.keys.any { it.equals("Cookie", ignoreCase = true) })
        }
        assertEquals(2, lookup.record.mainAsks)
        assertEquals(2, lookup.record.masterAsks)
        assertEquals(
            listOf(
                "$LADDER after NO_MEDIA_FOUND; main's answers re-read, no new ask",
                ADULT,
                TOKEN_REFUSED,
                "$V2 login.vk.ru (debug values of ${VkClient.BUNDLED_DATE}): answered, held in memory for this ask only",
                "$V2 video.get: DIRECT (rows 4, bound to the video); next: CHECK_ROWS",
                "$V2 item: MP4 files 2, HLS 1, DASH 1 (VK's key table); title known; length known",
            ),
            lookup.lines,
        )
        assertBare(lookup)
    }

    @Test
    fun `V2 asks nothing without another value, over VK's budget or the lookup's time, or while a host cools`() = runTest {
        val failing = object : VkClientValues {
            override suspend fun values(): List<VkClient.Values> = error("no source")
        }
        val cooling = SocialCooldown().apply { note(listOf(VkClient.API_HOST)) }
        val cases = listOf(
            Case("main's values only", VkClientValues.bundled(), "no value other than main's"),
            Case("a failing source", failing, "no value other than main's"),
            Case("a request budget of 3", HEAL, "over the lookup's request budget", budget = VkSite.BUDGET.copy(requests = 3)),
            Case("a byte budget of 1 KiB", HEAL, "over the lookup's byte budget", budget = VkSite.BUDGET.copy(bytes = 1_024)),
            Case("the host cooling", HEAL, "cooling down after a 429 or a bot check", cooldown = cooling),
        )
        for (case in cases) {
            val master = vk()
            val lookup = lookup(
                vk(token = refused(401)),
                ladder(master, case.values, cooldown = case.cooldown, budget = case.budget),
            )

            assertEquals(case.name, SiteExtractionFailure.NO_MEDIA_FOUND, lookup.mainFailure)
            assertTrue(case.name, lookup.result is MasterResult.NeedsPlayback)
            assertEquals(case.name, "$V2: ${case.why}; not asked", lookup.lines.last())
            assertNothingAsked(master)
            assertBare(lookup)
        }

        // Main's asks took the lookup's time: 0.4 s left of VK's 30 s, under two asks' least.
        var now = 0L
        val record = LookupRecord(clock = { now })
        val master = vk()
        val late = lookup(
            vk(token = { now = 29_600; ExtractorHttpResult.Failure(SiteExtractionFailure.HTTP_STATUS, 401) }),
            ladder(master),
            record = record,
        )
        assertEquals("$V2: over the lookup's time budget; not asked", late.lines.last())
        assertNothingAsked(master)
    }

    @Test
    fun `V1's rows stay within VK's budget - over it they are not checked`() = runTest {
        val master = vk()
        val lookup = lookup(
            vk(api = answers("video_get_renamed.json")),
            ladder(master, budget = VkSite.BUDGET.copy(requests = 3)),
        )

        assertTrue(lookup.result.toString(), lookup.result is MasterResult.NeedsPlayback)
        assertEquals("$V1: over the lookup's request budget; not checked", lookup.lines.last())
        assertTrue(lookup.validator.seen.isEmpty())

        val small = lookup(
            vk(api = answers("video_get_renamed.json")),
            ladder(master, budget = VkSite.BUDGET.copy(bytes = 64)),
        )
        assertEquals("$V1: over the lookup's byte budget; not checked", small.lines.last())
        assertNothingAsked(master)
        assertBare(lookup)
        assertBare(small)
    }

    @Test
    fun `route B's rules hold for V2 - main's token answer replayed, main's video get never asked again`() = runTest {
        // Main's token was fine and video.get refused the values (error 5): a new client secret
        // changes only the token, and main's video.get is not asked again, so nothing is asked.
        val refusedApi = vk(api = answers("video_get_error_5.json"))
        val master = vk()
        val same = lookup(refusedApi, ladder(master))

        assertEquals(
            listOf(
                "$LADDER after NO_MEDIA_FOUND; main's answers re-read, no new ask",
                ADULT,
                "$V1 video.get: VALUE_REJECTED (API error 5 (the web client's values)); next: NEXT_RUNG",
                "$V2: video.get with these values asked in this lookup already; not asked",
            ),
            same.lines,
        )
        assertNothingAsked(master)

        // A new API version: main's identical token ask is answered from the lookup, video.get is new.
        val version = VkClient.BUNDLED.copy(apiVersion = "5.290")
        val healer = vk()
        val healed = lookup(
            vk(api = answers("video_get_error_5.json")),
            ladder(healer, VkClientValues.bundled { version }),
        )

        val success = healed.result as MasterResult.Success
        assertEquals(MasterStage.CONTRACT, success.stage)
        assertTrue(healer.postedUrls.isEmpty())
        assertEquals(listOf(version.videoGetUrl(LINK)), healer.requestedUrls)
        val token = checkNotNull(VkClient.token(fixture("vk-pack/token_okay.json"), NOW))
        assertEquals(listOf(VkClient.headers(AGENT, token).toList()), healer.requestedHeaders.map { it.toList() })
        assertEquals(1, healed.record.replayedCount)
        assertEquals(1, healed.record.masterAsks)
        assertTrue(
            healed.lines.toString(),
            "$V2 login.vk.ru (debug values of ${VkClient.BUNDLED_DATE}): answered, held in memory for this ask only; " +
                "main's identical answer replayed" in healed.lines,
        )
        assertTrue(healed.lines.toString(), "$V2 video.get: DIRECT (rows 4, bound to the video); next: CHECK_ROWS" in healed.lines)
        assertBare(same)
        assertBare(healed)
    }

    @Test
    fun `V2's own 429, bot check or slow answer ends it - the host cools, nothing more is asked`() = runTest {
        val limitedToken = vk(token = refused(429, SiteExtractionFailure.RATE_LIMITED))
        val cooldown = SocialCooldown()
        val limited = lookup(vk(token = refused(401)), ladder(limitedToken, cooldown = cooldown))

        assertEquals(
            "$V2 login.vk.ru (debug values of ${VkClient.BUNDLED_DATE}): RATE_LIMITED (status 429); next: STOP_SITE",
            limited.lines.last(),
        )
        assertEquals(listOf(VkClient.TOKEN_URL), limitedToken.postedUrls)
        assertTrue(limitedToken.requestedUrls.isEmpty())
        assertTrue(cooldown.cooling(VkClient.TOKEN_HOST))

        val captchaApi = vk(api = answers("video_get_captcha.json"))
        val apiCooldown = SocialCooldown()
        val captcha = lookup(vk(token = refused(401)), ladder(captchaApi, cooldown = apiCooldown))

        assertTrue(captcha.result.toString(), captcha.result is MasterResult.NeedsPlayback)
        assertEquals("$V2 video.get: BOT_CHECK (API error 14 (captcha)); next: STOP_SITE", captcha.lines.last())
        assertTrue(apiCooldown.cooling(VkClient.API_HOST))
        assertFalse(apiCooldown.cooling(VkClient.TOKEN_HOST))

        // A second lookup while login.vk.ru cools: V2 asks nothing.
        val again = vk()
        val cooling = lookup(vk(token = refused(401)), ladder(again, cooldown = cooldown))
        assertEquals("$V2: cooling down after a 429 or a bot check; not asked", cooling.lines.last())
        assertNothingAsked(again)

        val slow = Slow(vk())
        val late = lookup(vk(token = refused(401)), ladder(slow))
        assertEquals("$V2 login.vk.ru: no answer in time", late.lines.last())
        assertTrue(late.result.toString(), late.result is MasterResult.NeedsPlayback)
        assertBare(limited)
        assertBare(captcha)
        assertBare(cooling)
        assertBare(late)
    }

    @Test
    fun `rungs off - V1 stages nothing and V2 still decides, V2 off asks nothing, V3 off ends with its line`() = runTest {
        val master = vk()
        val v1Off = lookup(vk(api = answers("video_get_renamed.json")), ladder(master, off = setOf("V1")))

        assertTrue(v1Off.result.toString(), v1Off.result is MasterResult.NeedsPlayback)
        assertEquals(
            listOf("$LADDER after NO_MEDIA_FOUND; main's answers re-read, no new ask", "$V1: off (config); not run"),
            v1Off.lines,
        )
        assertEquals(1, v1Off.capture.calls)
        assertNothingAsked(master)

        val healer = vk()
        val healed = lookup(vk(token = refused(401)), ladder(healer, off = setOf("V1")))
        assertEquals(MasterStage.CONTRACT, (healed.result as MasterResult.Success).stage)
        assertEquals("$V1: off (config); not run", healed.lines[1])
        assertEquals(1, healer.postedUrls.size)

        val v2Off = lookup(vk(token = refused(401)), ladder(master, off = setOf("V2")))
        assertEquals(
            listOf(
                "$LADDER after NO_MEDIA_FOUND; main's answers re-read, no new ask",
                ADULT,
                TOKEN_REFUSED,
                "$V2: off (config); not run",
            ),
            v2Off.lines,
        )
        assertNothingAsked(master)

        val locked = fixture("vk-pack/video_get_deleted.json").replace("delete_outline", "lock_outline")
        val v3Off = lookup(vk(api = { json(locked, it) }), ladder(master, off = setOf("V3")))
        assertTrue(v3Off.result.toString(), v3Off.result is MasterResult.NeedsPlayback)
        assertEquals(0, v3Off.capture.calls)
        assertEquals("$V3: off (config); not run", v3Off.details.last())
        assertFalse(v3Off.details.toString(), v3Off.details.any { it == "master: one browser-assisted capture requested" })
        assertBare(v1Off)
        assertBare(healed)
        assertBare(v2Off)
        assertBare(v3Off)
    }

    @Test
    fun `claims only a VK lookup inside its own record, and reads nothing for a sign-in failure, another video or a link without a VK video`() = runTest {
        val master = vk()
        val ladder = ladder(master)
        val identity = identify(PAGE)
        val request = contractRequest(PAGE, identity, SiteExtractionFailure.NO_MEDIA_FOUND)
        val policy = MasterPolicy(enabled = true)

        assertFalse("no lookup record", ladder.claims(request))
        withContext(LookupRecord() + MainPages(site = SITE)) {
            assertTrue(ladder.claims(request))
            val other = SitePageIdentity("x", "1000000000000000001", "https://x.com/fixture/status/1000000000000000001")
            assertFalse(ladder.claims(contractRequest(other.canonicalPageUrl, other)))
            assertNull(ladder.captureOff(request))

            val details = mutableListOf<String>()
            assertNull(ladder.stage(contractRequest(PAGE, identity, SiteExtractionFailure.LOGIN_REQUIRED), policy, details))
            assertNull(ladder.stage(request.copy(expectedContentId = "-100000001_400000002"), policy, details))
            assertTrue(details.toString(), details.isEmpty())

            assertNull(ladder.stage(request, policy, details))
            assertEquals(
                listOf(
                    "$LADDER after NO_MEDIA_FOUND; main's answers re-read, no new ask",
                    "$V1: none kept for the video; nothing to read",
                ),
                details,
            )

            val odd = SitePageIdentity(SITE, "fixture", PAGE)
            val oddDetails = mutableListOf<String>()
            assertNull(ladder.stage(request.copy(identity = odd, expectedContentId = odd.contentId), policy, oddDetails))
            assertEquals("$V1: no VK video in the link; nothing to read", oddDetails.last())
            assertBare(details + oddDetails)
        }
        assertNothingAsked(master)
    }

    /** One lookup: main's real VK adapter over [main] (its pages and asks kept in the lookup), then Master's engine with [ladder]. */
    private suspend fun lookup(
        main: FakeExtractorHttpClient,
        ladder: VkLadder,
        record: LookupRecord = LookupRecord(),
        cookie: String? = null,
    ): Lookup {
        val identity = identify(PAGE)
        val validator = RecordingValidator()
        val capture = CountingCapture()
        val stage = Recording(ladder)
        return withContext(record + MainPages(site = SITE)) {
            val answer = VkExtractor(MainPageRecorder(LookupRecorder(main)), clockMillis = { 0L })
                .extract(mainRequest(identity, cookie))
            val failure = (answer as SiteExtractionResult.Failure).reason
            val engine = MasterFallbackEngine(
                validator,
                capture,
                MasterPolicy(enabled = true),
                contracts = contracts(FakeExtractorHttpClient()),
                social = stage,
            )
            val result = engine.extract(contractRequest(PAGE, identity, failure, cookie))
            Lookup(failure, result, record, validator, capture, stage.lines)
        }
    }

    private class Lookup(
        val mainFailure: SiteExtractionFailure,
        val result: MasterResult,
        val record: LookupRecord,
        val validator: RecordingValidator,
        val capture: CountingCapture,
        /** The ladder's lines as it wrote them (before the engine's sanitizer). */
        val lines: List<String>,
    ) {
        /** The answer's lines, as the engine gives them. */
        val details: List<String>
            get() = when (result) {
                is MasterResult.Success -> result.result.details
                is MasterResult.Failure -> result.details
                is MasterResult.NeedsPlayback -> result.details
                is MasterResult.NeedsSelection -> result.details
                is MasterResult.Skipped -> emptyList()
            }
    }

    /** The ladder as the engine sees it, keeping the lines it writes as written. */
    private class Recording(private val ladder: VkLadder) : SocialStage by ladder {
        val lines = mutableListOf<String>()

        override suspend fun stage(
            request: MasterRequest,
            policy: MasterPolicy,
            details: MutableList<String>,
        ): SocialStage.Staged? {
            val before = details.size
            try {
                return ladder.stage(request, policy, details)
            } finally {
                lines += details.drop(before)
            }
        }

        override suspend fun captureOff(request: MasterRequest): String? = ladder.captureOff(request)?.also { lines += it }
    }

    /** [answers], but every token ask hangs until it is cancelled. */
    private class Slow(private val answers: FakeExtractorHttpClient) : ExtractorHttpClient by answers {
        override suspend fun postJson(
            url: String,
            body: String,
            headers: Map<String, String>,
            maxBodyBytes: Long,
        ): ExtractorHttpResult = awaitCancellation()
    }

    private class Case(
        val name: String,
        val values: VkClientValues,
        val why: String,
        val budget: SocialBudget = VkSite.BUDGET,
        val cooldown: SocialCooldown = SocialCooldown(),
    )

    /** Nothing Master asked: no page, no token, no file check. */
    private fun assertNothingAsked(http: FakeExtractorHttpClient) {
        assertTrue(http.requestedUrls.toString(), http.requestedUrls.isEmpty())
        assertTrue(http.postedUrls.isEmpty())
        assertTrue(http.probedUrls.isEmpty())
    }

    private fun assertBare(lookup: Lookup) {
        assertBare(lookup.lines)
        assertBare(lookup.details)
    }

    /** Short labels only: the sanitizer keeps each line whole; no address, ID, value or page words. */
    private fun assertBare(lines: List<String>) {
        lines.forEach { line ->
            assertEquals(line, listOf(line), DiagnosticTextSanitizer.details(listOf(line)))
            listOf(
                "http", "100000001", "400000001", "ixture", "IXTURE", "vkuser", "okcdn", "remixsid", "not-real",
                SECRET, VkClient.CLIENT_SECRET, VkClient.CLIENT_ID, "adult content",
            ).forEach { assertFalse("$line has $it", line.contains(it)) }
        }
    }

    private companion object {
        const val SITE = "vk"
        const val PAGE = "https://vk.com/video-100000001_400000001"
        const val KEY = "vk:-100000001_400000001"
        const val COOKIE = "remixsid=FIXTURE"
        const val SECRET = "FixtureSecret0123"
        const val API_FILES = "https://vk1-1.vkuser.net/fixture"
        const val EMBED_FILES = "https://vkvd1.okcdn.ru/fixture"
        const val LADDER = "master: vk ladder"
        const val V1 = "master: vk V1 main's answers"
        const val V2 = "master: vk V2 heal"
        const val V3 = "master: vk V3"
        const val ITEM = "master: vk V1 item"
        const val ADULT = "$V1 embed: VIDEO_WITHOUT_FILES (the embed's adult answer (callback 9)); next: NEXT_RUNG"
        const val TOKEN_REFUSED = "$V1 login.vk.ru: VALUE_REJECTED (status 401, the web client's values refused); next: NEXT_RUNG"
        val LINK = VkClient.Link(-100000001, 400000001)

        /** The owner's debug hook: one value changed (a made-up client secret of VK's shape). */
        val HEALED: VkClient.Values = VkClient.BUNDLED.copy(clientSecret = SECRET, source = VkClient.Source.DEBUG)
        val HEAL: VkClientValues = VkClientValues.bundled { it.copy(clientSecret = SECRET) }

        /** VK's recipe for V1's and V2's answers (VkReader's JSON); step 4's VkRecipe replaces it. */
        val TEST_RECIPE = ContractRecipe(
            site = SITE,
            endpoint = "https://${VkClient.API_HOST}${VkClient.VIDEO_GET_PATH}?v={version}&videos={id}",
            answerHosts = setOf(VkClient.API_HOST, VkClient.COM_HOST, VkClient.RU_HOST),
            idPaths = listOf(listOf("id")),
            media = listOf(ContractMedia(path = listOf("files", "*"), mimeField = "type")),
            titlePaths = listOf(listOf("title")),
            durationSecondsPaths = listOf(listOf("duration")),
            thumbnailPaths = listOf(listOf("image")),
            expiryPath = listOf("expires"),
        )

        fun contracts(http: ExtractorHttpClient) = SiteContracts(http, ContractRecipes.ALL + TEST_RECIPE)

        fun ladder(
            master: ExtractorHttpClient,
            values: VkClientValues = HEAL,
            cooldown: SocialCooldown = SocialCooldown(),
            budget: SocialBudget = VkSite.BUDGET,
            off: Set<String> = emptySet(),
        ) = VkLadder(master, ::contracts, values, cooldown, budget, rungsOff = { off })

        fun identify(url: String): SitePageIdentity = checkNotNull(VkExtractor(FakeExtractorHttpClient()).identify(url))

        /**
         * VK as one client answers it: each embed page by [embed] (its address), each token ask by
         * [token] (its body), each `video.get` by [api] (its address). Main's defaults: the embed's
         * adult answer, a token, the video's files under VK's keys.
         */
        fun vk(
            embed: (String) -> ExtractorHttpResult = { page(fixture("vk-pack/embed_adult.html"), it) },
            token: (String) -> ExtractorHttpResult = { json(fixture("vk-pack/token_okay.json"), VkClient.TOKEN_URL) },
            api: (String) -> ExtractorHttpResult = answers("video_get_mp4.json"),
        ) = FakeExtractorHttpClient(
            getResponder = { url, _ ->
                val uri = URI(url)
                when {
                    uri.path == VkClient.EMBED_PATH -> embed(url)
                    uri.host == VkClient.API_HOST -> api(url)
                    else -> null
                }
            },
            postResponder = { url, body -> if (url == VkClient.TOKEN_URL) token(body) else null },
        )

        fun page(body: String, url: String) = FakeExtractorHttpClient.html(body, url)

        fun json(body: String, url: String) = FakeExtractorHttpClient.json(body, url)

        /** Each ask answered with the fixture [name] from its own address. */
        fun answers(name: String): (String) -> ExtractorHttpResult = { url -> json(fixture("vk-pack/$name"), url) }

        fun refused(
            status: Int,
            reason: SiteExtractionFailure = SiteExtractionFailure.HTTP_STATUS,
        ): (String) -> ExtractorHttpResult = { ExtractorHttpResult.Failure(reason, status) }
    }
}
