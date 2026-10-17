package com.alal.yft.extractor.master.modules.youtube

import com.alal.yft.core.model.media.BrowserRequestContext
import com.alal.yft.core.model.media.MediaCandidate
import com.alal.yft.extractor.api.ExtractorHttpClient
import com.alal.yft.extractor.api.ExtractorProbeResult
import com.alal.yft.extractor.api.NoPoTokenProvider
import com.alal.yft.extractor.api.PlayerScriptChallengeKind
import com.alal.yft.extractor.api.PlayerScriptRequest
import com.alal.yft.extractor.api.PlayerScriptResult
import com.alal.yft.extractor.api.PlayerScriptRunner
import com.alal.yft.extractor.api.SiteExtractionFailure
import com.alal.yft.extractor.api.SiteExtractionRequest
import com.alal.yft.extractor.api.SiteExtractionResult
import com.alal.yft.extractor.api.SiteExtractor
import com.alal.yft.extractor.api.json.BoundedJsonParser
import com.alal.yft.extractor.api.json.asStringOrNull
import com.alal.yft.extractor.api.json.get
import com.alal.yft.extractor.api.json.path
import com.alal.yft.extractor.master.modules.youtube.testing.FakeExtractorHttpClient
import com.alal.yft.extractor.master.modules.youtube.testing.Fixtures
import com.alal.yft.extractor.master.solver.OwnPlayerScriptRunner
import com.alal.yft.extractor.master.solver.OwnSolverBreaker.Step
import com.alal.yft.extractor.master.solver.OwnSolverLocator
import com.alal.yft.extractor.master.solver.OwnSolverLocator.appendPart
import com.alal.yft.extractor.master.solver.OwnSolverLocators
import com.alal.yft.extractor.master.solver.OwnSolverTextFile
import com.alal.yft.extractor.master.solver.OwnSolverVLedger
import com.alal.yft.extractor.master.solver.PendingCheck
import com.alal.yft.extractor.master.solver.ScriptedOwnEngine
import java.net.URLDecoder
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import com.alal.yft.extractor.sites.youtube.YouTubeExtractor as MainYouTubeExtractor

/**
 * Phase 1.2 N5b A.8: the app's YouTube path as `SiteAdapterModule.provideSiteExtractors` builds
 * it: main's reader (extractor-sites) through [YouTubeSignatureGuard], with the runner the app
 * builds (for ownSolverNext its stores on files). The core and the media server are scripted;
 * the reader, the guard, the gate, StreamCheck and the runner are the real ones.
 */
class YouTubeSignatureGuardTest {
    @Test
    fun `ownSolverNext - a right signature passes V once, its pass is in the ledger and its part is cached`() =
        runTest {
            val files = Files()
            val core = Core(parts = true)
            val probes = Probes(core)
            val reader = appReader(nextRunner(files, core), youtube(probes))

            val result = reader.extract(request()) as SiteExtractionResult.Success

            assertTrue(result.candidates.any(core::signed))
            assertEquals(1, probes.signed)
            assertTrue(files.ledger().passed(check(PendingCheck.NO_LOCATOR).cacheKey))
            assertNotNull(files.locators().peek(PLAYER)?.signature)
        }

    @Test
    fun `ownSolverNext - a ledger hit skips V, after a restart too, and other player text is checked again`() =
        runTest {
            val files = Files()
            val core = Core()
            val probes = Probes(core)

            assertTrue(signedRows(appReader(nextRunner(files, core), youtube(probes)), core) > 0)
            assertEquals(1, probes.signed)
            assertTrue(files.ledger().passed(check(PendingCheck.NO_LOCATOR).cacheKey))
            // A restart: new stores on the same files. The ledger's pass holds for this text.
            files.restart()
            assertTrue(signedRows(appReader(nextRunner(files, core), youtube(probes)), core) > 0)
            assertEquals(1, probes.signed)
            // Other player text under the same player ID: another hash, another key, V again.
            files.restart()
            val other = nextRunner(files, core, players(OTHER_TEXT))
            assertTrue(signedRows(appReader(other, youtube(probes)), core) > 0)
            assertEquals(2, probes.signed)
        }

    @Test
    fun `ownSolverNext - a wrong signature fails V, nothing is kept, and the next lookup goes to the next step`() =
        runTest {
            val files = Files()
            val core = Core(wrong = true, parts = true)
            val probes = Probes(core)
            val reader = appReader(nextRunner(files, core), youtube(probes, mixed()))

            val first = reader.extract(request()) as SiteExtractionResult.Success

            assertTrue(first.candidates.isNotEmpty())
            assertFalse(first.candidates.any(core::signed))
            assertEquals(1, probes.signed)
            assertEquals(1, probes.control)
            assertFalse(files.ledger().passed(check(PendingCheck.NO_LOCATOR).cacheKey))
            assertNull(files.locators().peek(PLAYER)?.signature)
            assertTrue(core.engine.jobs.none { it.isB })

            reader.extract(request())
            assertTrue(core.engine.jobs.any { it.isB && it.sig.isNotEmpty() })
        }

    @Test
    fun `ownSolver without Next - Phase 1-1 signatures wait for V too, one pass per player version`() = runTest {
        val core = Core()
        val probes = Probes(core)
        val reader = appReader(OwnPlayerScriptRunner(players(), core.engine), youtube(probes))

        assertTrue(signedRows(reader, core) > 0)
        assertEquals(1, probes.signed)
        assertTrue(signedRows(reader, core) > 0)
        assertEquals(1, probes.signed)
    }

    @Test
    fun `ownSolver without Next - a signature V proved wrong is dropped and the player's signatures stay off`() =
        runTest {
            val core = Core(wrong = true)
            val probes = Probes(core)
            val reader = appReader(OwnPlayerScriptRunner(players(), core.engine), youtube(probes, mixed()))

            val first = reader.extract(request()) as SiteExtractionResult.Success
            assertTrue(first.candidates.isNotEmpty())
            assertFalse(first.candidates.any(core::signed))
            assertEquals(1, probes.signed)
            assertEquals(1, probes.control)

            val second = reader.extract(request()) as SiteExtractionResult.Success
            assertTrue(second.candidates.isNotEmpty())
            assertFalse(second.candidates.any(core::signed))
            assertEquals(1, probes.signed)
        }

    @Test
    fun `flag off - main's runner gets main's reader as is, and no lookup is checked`() = runTest {
        val main = MainRunner()
        val built = mutableListOf<PlayerScriptRunner>()
        val probes = Probes(Core())
        val http = youtube(probes)

        val reader = YouTubeSignatureGuard.guard(http, main) { runner ->
            built += runner
            MainYouTubeExtractor(http, runner, NoPoTokenProvider)
        }

        assertSame(main, built.single())
        assertTrue(reader is MainYouTubeExtractor)
        val result = reader.extract(request()) as SiteExtractionResult.Success
        assertTrue(result.candidates.isNotEmpty())
        assertEquals(0, probes.signed + probes.control)
    }

    /** The app's line: main's reader with the runner the guard hands it. */
    private fun appReader(runner: PlayerScriptRunner, http: ExtractorHttpClient): SiteExtractor =
        YouTubeSignatureGuard.guard(http, runner) { MainYouTubeExtractor(http, it, NoPoTokenProvider) }

    private suspend fun signedRows(reader: SiteExtractor, core: Core): Int =
        (reader.extract(request()) as SiteExtractionResult.Success).candidates.count(core::signed)

    private fun nextRunner(files: Files, core: Core, http: FakeExtractorHttpClient = players()) =
        OwnPlayerScriptRunner(
            http,
            core.engine,
            next = OwnPlayerScriptRunner.Next(locators = files.locators(), ledger = files.ledger()),
        )

    private fun check(locatorHash: String) = PendingCheck(PLAYER, Step.WORKER_P, SCRIPT, locatorHash)

    /**
     * The scripted core: n and sig for every input asked ([wrong]: a signature that is not the
     * player's), no init errors, and with [parts] P's parts when the job locates. [given] holds
     * every signature it gave (memory only).
     */
    private class Core(private val wrong: Boolean = false, private val parts: Boolean = false) {
        val given = HashSet<String>()
        val engine = ScriptedOwnEngine { job ->
            ScriptedOwnEngine.result(
                n = job.n.associateWith(ScriptedOwnEngine::rate),
                sig = job.sig.associateWith { input ->
                    val value = if (wrong) "W$input" else ScriptedOwnEngine.signature(input)
                    value.also { synchronized(given) { given += it } }
                },
                prepared = ScriptedOwnEngine.preparedFor(job),
                initErrors = 0,
                locator = if (parts && job.locator != null) LOCATED else null,
            )
        }

        fun signed(candidate: MediaCandidate): Boolean =
            listOfNotNull(candidate.mediaUrl, candidate.audioCompanion?.mediaUrl).any(::carries)

        /** True when a query value of [url], decoded, is a signature this core gave. */
        fun carries(url: String): Boolean = url.substringAfter('?', "").substringBefore('#').split('&').any { part ->
            val raw = part.substringAfter('=', "")
            raw.isNotEmpty() &&
                runCatching { URLDecoder.decode(raw, Charsets.UTF_8.name()) }.getOrNull()
                    ?.let { synchronized(given) { it in given } } == true
        }
    }

    /** The media server: a stream with [core]'s right signature opens, a wrong one is refused. */
    private class Probes(private val core: Core) {
        var signed = 0
        var control = 0

        fun answer(url: String): ExtractorProbeResult {
            if (!core.carries(url)) {
                control++
                return ExtractorProbeResult.Answered(206, 1_000_000L)
            }
            signed++
            val wrong = url.substringAfter('?', "").split('&').any { part ->
                val name = part.substringBefore('=')
                (name == "sig" || name == "signature") && part.substringAfter('=', "").startsWith("W")
            }
            return if (wrong) {
                ExtractorProbeResult.Refused(SiteExtractionFailure.HTTP_STATUS, 403, null)
            } else {
                ExtractorProbeResult.Answered(206, 1_000_000L)
            }
        }
    }

    /** Main's ejs runner as far as the guard sees it: a runner that is no [SignatureVerdicts]. */
    private class MainRunner : PlayerScriptRunner {
        override val isAvailable: Boolean = true

        override suspend fun resolve(request: PlayerScriptRequest): PlayerScriptResult =
            PlayerScriptResult.Success(
                request.challenges.associate { challenge ->
                    challenge.key to when (challenge.kind) {
                        PlayerScriptChallengeKind.RATE_PARAM -> ScriptedOwnEngine.rate(challenge.input)
                        PlayerScriptChallengeKind.SIGNATURE -> ScriptedOwnEngine.signature(challenge.input)
                    }
                },
            )
    }

    private class Files {
        val locatorFile = MemoryTextFile()
        val ledgerFile = MemoryTextFile()
        private var locators: OwnSolverLocators? = null
        private var ledger: OwnSolverVLedger? = null

        fun locators(): OwnSolverLocators = locators ?: OwnSolverLocators(locatorFile).also { locators = it }

        fun ledger(): OwnSolverVLedger = ledger ?: OwnSolverVLedger(ledgerFile).also { ledger = it }

        fun restart() {
            locators = null
            ledger = null
        }
    }

    private class MemoryTextFile(var text: String? = null) : OwnSolverTextFile {
        override fun read(): String? = text

        override fun write(text: String): Boolean {
            this.text = text
            return true
        }
    }

    private fun youtube(probes: Probes, embedded: String = fixture("player_cipher.json")) = FakeExtractorHttpClient(
        responses = mapOf(PAGE_FETCH to FakeExtractorHttpClient.html(watchPage(BOT_CHECK), MOBILE_PAGE)),
        postResponder = { url, body ->
            if (clientOf(body) == "WEB_EMBEDDED_PLAYER") {
                FakeExtractorHttpClient.json(onMediaServer(embedded), url)
            } else {
                null
            }
        },
        probeResponder = { url, _ -> probes.answer(url) },
    )

    /** The cipher answer plus the plain answer's direct adaptive rows: a direct URL is V's control. */
    private fun mixed(): String {
        val cipher = fixture("player_cipher.json")
        val rows = arrayBody(fixture("player_ok.json"), "\"adaptiveFormats\"")
        val at = arrayOpen(cipher, "\"adaptiveFormats\"")
        return cipher.substring(0, at) + rows + "," + cipher.substring(at)
    }

    private fun arrayOpen(json: String, key: String): Int {
        val named = json.indexOf(key)
        require(named >= 0) { "no $key" }
        return json.indexOf('[', named) + 1
    }

    private fun arrayBody(json: String, key: String): String {
        val start = arrayOpen(json, key)
        var depth = 1
        var index = start
        var quoted = false
        while (depth > 0) {
            val c = json[index]
            if (quoted) {
                if (c == '\\') index++ else if (c == '"') quoted = false
            } else {
                when (c) {
                    '"' -> quoted = true
                    '[', '{' -> depth++
                    ']', '}' -> depth--
                }
            }
            index++
        }
        return json.substring(start, index - 1)
    }

    /** V checks only media server URLs; the shared fixtures' streams sit on a test host. */
    private fun onMediaServer(body: String): String = body.replace("googlevideo.example-cdn.test", "googlevideo.com")

    private fun players(text: String = PLAYER_TEXT) = FakeExtractorHttpClient(
        responses = mapOf(PHONE_PLAYER to FakeExtractorHttpClient.json(text, PHONE_PLAYER)),
    )

    private fun clientOf(body: String): String? = BoundedJsonParser.parse(body, maxNodes = 1_000)
        .path("context", "client")["clientName"].asStringOrNull

    private fun request() = SiteExtractionRequest(
        identity = requireNotNull(YouTubeUrls.identify("https://youtu.be/$VIDEO_ID?si=fixture")),
        requestContext = BrowserRequestContext(pageUrl = MOBILE_PAGE, userAgent = USER_AGENT, cookie = null),
        nowEpochMs = NOW,
    )

    private fun watchPage(playerResponse: String): String =
        fixture("watch_page.html").replace("__PLAYER_RESPONSE__", playerResponse)

    private fun fixture(name: String): String = Fixtures.read("youtube/$name")

    private companion object {
        const val VIDEO_ID = "Yft0Fixture"
        const val NOW = 1_791_000_000_000L
        const val PAGE_FETCH = "https://www.youtube.com/watch?v=$VIDEO_ID&bpctr=9999999999&has_verified=1"
        const val MOBILE_PAGE = "https://m.youtube.com/watch?v=$VIDEO_ID"
        const val USER_AGENT = "Mozilla/5.0 (Linux; Android 14; Fixture) Mobile Safari/537.36"
        const val PLAYER = "f1x7ure0"
        const val PHONE_PLAYER =
            "https://www.youtube.com/s/player/f1x7ure0/player-plasma-ias-phone-en_US.vflset/base.js"
        const val PLAYER_TEXT = "var _yt_player = {}; (function (g) { /* fixture */ })(_yt_player);"
        const val OTHER_TEXT = "var _yt_player = {}; (function (g) { /* other build */ })(_yt_player);"
        val SCRIPT: String = OwnSolverLocator.sha256Hex(PLAYER_TEXT)
        val P_PARTS = OwnSolverLocator.Parts(
            rate = OwnSolverLocator.PPart(listOf(2), "0000aaaa"),
            signature = OwnSolverLocator.PPart(listOf(5, 6), "0000bbbb"),
        )
        val LOCATED: String = buildString {
            append("{\"v\":1,\"n\":")
            appendPart(P_PARTS.rate!!)
            append(",\"sig\":")
            appendPart(P_PARTS.signature!!)
            append('}')
        }
        val BOT_CHECK: String = Fixtures.read("youtube/player_bot_check.json")
    }
}
