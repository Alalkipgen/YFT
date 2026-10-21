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
import com.alal.yft.extractor.master.contract.contractRequest
import com.alal.yft.extractor.master.contract.mainRequest
import com.alal.yft.extractor.master.lookup.LookupRecord
import com.alal.yft.extractor.master.lookup.LookupRecorder
import com.alal.yft.extractor.master.modules.youtube.ladder.InitBytes
import com.alal.yft.extractor.master.modules.youtube.ladder.MediaStart
import com.alal.yft.extractor.master.modules.youtube.ladder.MediaStartReader
import com.alal.yft.extractor.master.modules.youtube.testing.FakeExtractorHttpClient
import com.alal.yft.extractor.sites.facebook.FacebookExtractor
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Social plan S1a (8): the simulator world. One made-up Facebook (a video's watch and reel pages
 * with a related video beside it, the plugin player, the media host's files) on a fake clock,
 * asked in the app's order: main's real Facebook adapter first, Master's engine after main's
 * failure, each lookup in its own record and main's pages, the ladder, S6's row check and S7's
 * cooldown built once and shared by the world's lookups (as the app holds them). Mutations: a
 * renamed key, a moved block, a 429, a sign-in wall, the unavailable page in two languages.
 * Every lookup keeps the world's rules ([assertRules]): rows only the video's files, bound to its
 * ID, each read once (its first bytes, never the session); a decoy's file never read nor offered;
 * main's and Master's page asks within the lookup's 4 and its 20 s, the player's bytes within
 * 8 MiB, at most 8 file reads; no endpoint asked twice, none after a stop; the engine's own check
 * not asked; lines without a link, an ID or the page's words. No live calls.
 */
class FacebookWorldTest {
    @Test
    fun `the world as main reads it - main's rows, Master never asks`() = runTest {
        for (link in LINKS) {
            val world = World()
            val lookup = world.lookup(link)

            val rows = (lookup.main as SiteExtractionResult.Success).candidates
            assertEquals(link, setOf(hd(VIDEO), sd(VIDEO)), rows.map { it.mediaUrl }.toSet())
            assertNull(lookup.master)
            // Main's pages only: the watch page once, a reel's twice (without, then with the session).
            assertEquals(List(if (link == REEL) 2 else 1) { Kind.PAGE }, lookup.asks.map { it.kind })
            assertEquals(0, lookup.record.masterAsks)
            assertRules(world, lookup)
        }
    }

    @Test
    fun `a renamed key - the player's or main's page's rows, only the video's, each file checked once`() = runTest {
        for (link in LINKS) {
            for (shape in listOf(Shape.FILE_KEYS, Shape.ALL_KEYS)) {
                val world = World(shape)
                // The player names the video without files (needs playback): F2 reads main's page.
                world.player = { ok(playerPage(files = false), it) }
                val fromPage = world.lookup(link)
                val expected = if (shape == Shape.FILE_KEYS) SiteExtractionFailure.NO_MEDIA_FOUND else SiteExtractionFailure.RESPONSE_CHANGED
                assertEquals("$link $shape", expected, fromPage.mainFailure)
                assertEquals(MasterStage.PAGE_DATA, (fromPage.master as MasterResult.Success).stage)
                assertOwnRows(fromPage)
                assertLines(
                    fromPage,
                    "$F1: NEEDS_PLAYBACK (the plugin player: needs playback); next: PLAYBACK",
                    "$F2: page 1: DIRECT (rows 2, bound to the video ID)",
                    "$F2: next: CHECK_ROWS",
                )
                assertEquals(1, fromPage.of(Kind.PLAYER).size)
                assertRules(world, fromPage)

                // The player with the video's files (a related video's item beside them): F1's rows.
                world.player = { ok(playerPage(files = true), it) }
                val fromPlayer = world.lookup(link)
                assertEquals(MasterStage.CONTRACT, (fromPlayer.master as MasterResult.Success).stage)
                assertOwnRows(fromPlayer)
                assertFalse(fromPlayer.details.any { it.startsWith(F2) })
                assertRules(world, fromPlayer)

                // A file's start proves it is no video (a page): that row is not offered, the other is.
                world.player = { ok(playerPage(files = false), it) }
                world.file = { url -> if ("-hd" in url) start(bytes = "<html><body>sign in</body></html>".toByteArray()) else start() }
                val wrong = world.lookup(link)
                assertEquals(listOf(sd(VIDEO)), wrong.rows.map { it.mediaUrl })
                assertEquals(setOf(hd(VIDEO), sd(VIDEO)), wrong.of(Kind.FILE).map { it.url }.toSet())
                assertRules(world, wrong)
                assertEquals(0, world.capture.calls)
            }
        }
    }

    @Test
    fun `a moved block - rows while it stays in the video's object, none outside it`() = runTest {
        for (link in LINKS) {
            // The video's files moved one level down in its object (its delivery block left empty).
            val world = World(Shape.NESTED)
            val nested = world.lookup(link)
            assertEquals(SiteExtractionFailure.NO_MEDIA_FOUND, nested.mainFailure)
            assertEquals(MasterStage.PAGE_DATA, (nested.master as MasterResult.Success).stage)
            assertOwnRows(nested)
            assertLines(nested, "$F1: SHAPE_CHANGED (no rows; no route); next: NEXT_RUNG", "$F2: next: CHECK_ROWS")
            assertRules(world, nested)

            // Moved out of the video's object, into one with no ID: not the video's; nothing read.
            world.page = { url, _ -> ok(pageOf(Shape.OUTSIDE), url) }
            val outside = world.lookup(link)
            assertEquals(SiteExtractionFailure.NO_MEDIA_FOUND, outside.mainFailure)
            assertTrue(outside.master is MasterResult.NeedsPlayback)
            assertLines(outside, "$F2: page 1: SHAPE_CHANGED (no rows; no route)", "$F2: next: PLAYBACK")
            assertTrue(outside.of(Kind.FILE).isEmpty())
            assertEquals(1, outside.captures)
            assertRules(world, outside)

            // Moved into the related video's object: that video's, not this one's.
            world.page = { url, _ -> ok(pageOf(Shape.UNDER_RELATED), url) }
            val related = world.lookup(link)
            assertTrue(related.master is MasterResult.NeedsPlayback)
            assertTrue(related.of(Kind.FILE).isEmpty())
            assertRules(world, related)
        }
    }

    @Test
    fun `a 429 at the player stops the site's asks, cools F1 for 30 minutes, F2 still reads`() = runTest {
        for (link in LINKS) {
            val world = World(Shape.FILE_KEYS)
            world.player = { ExtractorHttpResult.Failure(SiteExtractionFailure.RATE_LIMITED, 429) }
            val stopped = world.lookup(link)
            assertLines(stopped, "$F1: RATE_LIMITED (status 429); next: STOP_SITE")
            assertTrue(stopped.record.rateLimited)
            assertEquals(listOf(PAGE_HOST), stopped.record.stopHosts())
            // Nothing new after the 429 in its lookup: no file read, so no row offered.
            assertEquals(1, stopped.of(Kind.PLAYER).size)
            assertTrue(stopped.of(Kind.FILE).isEmpty())
            assertTrue(stopped.rows.isEmpty())
            assertFalse(stopped.master is MasterResult.Success)
            assertRules(world, stopped)

            // 10 minutes later: F1 cools (not asked), F2's rows from main's page are checked.
            world.now += 10 * MINUTE
            val cooling = world.lookup(link)
            assertLines(cooling, "$F1: cooling down after a 429 or a bot check; not asked", "$F2: next: CHECK_ROWS")
            assertTrue(cooling.of(Kind.PLAYER).isEmpty())
            assertEquals(MasterStage.PAGE_DATA, (cooling.master as MasterResult.Success).stage)
            assertOwnRows(cooling)
            assertRules(world, cooling)

            // 31 minutes after the stop: F1 asks again (its files now).
            world.now += 21 * MINUTE
            world.player = { ok(playerPage(files = true), it) }
            val again = world.lookup(link)
            assertEquals(1, again.of(Kind.PLAYER).size)
            assertEquals(MasterStage.CONTRACT, (again.master as MasterResult.Success).stage)
            assertOwnRows(again)
            assertRules(world, again)
        }
    }

    @Test
    fun `a sign-in wall - main's is the user's own playback, the player's leaves main's page to F2`() = runTest {
        for (link in LINKS) {
            // Main's page redirected to the login, or a login form in its place: Master asks nothing.
            val walls = listOf<(String) -> ExtractorHttpResult>(
                { ExtractorHttpResult.Success(200, SIGN_IN_PAGE, LOGIN, HTML) },
                { url -> ok(LOGIN_FORM_PAGE, url) },
            )
            for (wall in walls) {
                val world = World()
                world.page = { url, _ -> wall(url) }
                val lookup = world.lookup(link)
                assertEquals(SiteExtractionFailure.LOGIN_REQUIRED, lookup.mainFailure)
                assertTrue(lookup.master is MasterResult.NeedsPlayback)
                assertEquals(0, lookup.record.masterAsks)
                assertTrue(lookup.asks.all { it.kind == Kind.PAGE })
                assertEquals(1, lookup.captures)
                assertRules(world, lookup)
            }

            // The player's sign-in wall: the browser's, not final; main's page still gives the rows.
            val world = World(Shape.FILE_KEYS)
            world.player = { ExtractorHttpResult.Success(200, SIGN_IN_PAGE, LOGIN, HTML) }
            val rows = world.lookup(link)
            assertLines(rows, "$F1: LOGIN_WALL (final address: login); next: BROWSER", "$F2: next: CHECK_ROWS")
            assertOwnRows(rows)
            assertRules(world, rows)

            // With a page that has no data: no rows, nothing final, the tab's own playback.
            world.page = { url, _ -> ok(NO_DATA, url) }
            val none = world.lookup(link)
            assertTrue(none.master is MasterResult.NeedsPlayback)
            assertLines(none, "$F1: LOGIN_WALL (final address: login); next: BROWSER", "$F2: next: PLAYBACK")
            assertEquals(1, none.captures)
            assertRules(world, none)
        }
    }

    @Test
    fun `the unavailable page in two languages - final on its route, never on its words`() = runTest {
        for (link in LINKS) {
            // Main's English words: main's own final answer; Master asks nothing.
            val english = World()
            english.page = { url, _ -> ok(routePage(FacebookAnswerJudge.ERROR_ROUTE, privacy = true, title = MAIN_WORDS), url) }
            val mains = english.lookup(link)
            assertEquals(SiteExtractionFailure.PRIVATE_OR_UNAVAILABLE, mains.mainFailure)
            assertEquals(MasterResult.Skipped(SiteExtractionFailure.PRIVATE_OR_UNAVAILABLE), mains.master)
            assertTrue(mains.asks.all { it.kind == Kind.PAGE })
            assertRules(english, mains)

            // Words main does not know (another English, Burmese): the page's route, final.
            val routes = listOf(
                routePage(FacebookAnswerJudge.ERROR_ROUTE, privacy = true, title = ENGLISH) to "PRIVATE (route: error (privacy))",
                routePage(FacebookAnswerJudge.ERROR_ROUTE, privacy = true, title = BURMESE) to "PRIVATE (route: error (privacy))",
                routePage(FacebookAnswerJudge.NOT_FOUND_ROUTE, title = BURMESE) to "REMOVED (route: video not found)",
            )
            for ((body, line) in routes) {
                val world = World()
                world.page = { url, _ -> ok(body, url) }
                val lookup = world.lookup(link)
                assertEquals(SiteExtractionFailure.RESPONSE_CHANGED, lookup.mainFailure)
                assertEquals(SiteExtractionFailure.PRIVATE_OR_UNAVAILABLE, (lookup.master as MasterResult.Failure).reason)
                assertLines(lookup, "$F2: page 1: $line", "$F2: next: FINAL")
                assertTrue(lookup.of(Kind.FILE).isEmpty())
                assertEquals(0, lookup.captures)
                assertRules(world, lookup)
            }

            // The words alone (no route), or the error route without its privacy flag: not final.
            val unproven = listOf(
                "<html><head><title>Facebook</title></head><body><h1>$BURMESE</h1></body></html>",
                routePage(FacebookAnswerJudge.ERROR_ROUTE, title = BURMESE),
            )
            for (body in unproven) {
                val world = World()
                world.page = { url, _ -> ok(body, url) }
                val lookup = world.lookup(link)
                assertTrue(lookup.master is MasterResult.NeedsPlayback)
                assertLines(lookup, "$F2: next: PLAYBACK")
                assertEquals(1, lookup.captures)
                assertRules(world, lookup)
            }
        }
    }

    @Test
    fun `every mutation, every link form - wrong rows 0, the world's rules in each lookup`() = runTest {
        val cases = listOf(
            Case("main's shape", Shape.MAIN, Player.FILES, Outcome.MAIN),
            Case("renamed file keys", Shape.FILE_KEYS, Player.NONE, Outcome.ROWS),
            Case("renamed file keys, player's files", Shape.FILE_KEYS, Player.FILES, Outcome.ROWS),
            Case("renamed block and file keys", Shape.ALL_KEYS, Player.PLAYBACK, Outcome.ROWS),
            Case("moved block, in the video's object", Shape.NESTED, Player.PLAYBACK, Outcome.ROWS),
            Case("moved block, outside it", Shape.OUTSIDE, Player.PLAYBACK, Outcome.PLAYBACK),
            Case("moved block, under the related video", Shape.UNDER_RELATED, Player.NONE, Outcome.PLAYBACK),
            Case("a 429 at the player", Shape.FILE_KEYS, Player.LIMITED, Outcome.NO_ROWS),
            Case("the player's sign-in wall", Shape.FILE_KEYS, Player.SIGN_IN, Outcome.ROWS),
            Case("main's sign-in wall", Shape.SIGN_IN, Player.FILES, Outcome.PLAYBACK),
            Case("private, Burmese", Shape.PRIVATE, Player.NONE, Outcome.FINAL),
            Case("removed, Burmese", Shape.REMOVED, Player.NONE, Outcome.FINAL),
            Case("private, main's English", Shape.MAIN_WORDS, Player.NONE, Outcome.MAIN_FINAL),
            Case("the words alone", Shape.WORDS, Player.NONE, Outcome.PLAYBACK),
        )
        var lookups = 0
        var rows = 0
        var wrong = 0
        for (case in cases) {
            for (link in LINKS) {
                val world = World(case.shape)
                world.player = { url -> case.player.answer(url) }
                val lookup = world.lookup(link)
                val name = "${case.name}, $link"
                val outcome = when (val master = lookup.master) {
                    null -> Outcome.MAIN
                    is MasterResult.Skipped -> Outcome.MAIN_FINAL
                    is MasterResult.Success -> Outcome.ROWS
                    is MasterResult.Failure -> if (master.reason == SiteExtractionFailure.PRIVATE_OR_UNAVAILABLE) Outcome.FINAL else Outcome.NO_ROWS
                    is MasterResult.NeedsPlayback -> Outcome.PLAYBACK
                    else -> error("$name: $master")
                }
                assertEquals(name, case.outcome, outcome)
                val offered = lookup.rows + (lookup.main as? SiteExtractionResult.Success)?.candidates.orEmpty()
                lookups++
                rows += offered.size
                wrong += offered.count { it.mediaUrl !in OWN_FILES }
                assertRules(world, lookup)
            }
        }
        assertEquals(cases.size * LINKS.size, lookups)
        assertEquals(0, wrong)
        assertTrue(rows > 0)
    }

    /** The world's rules for each lookup (every test, every mutation). */
    private fun assertRules(world: World, lookup: Lookup) {
        val name = lookup.link
        // Rows: only the video's files, bound to its ID (main's and Master's).
        (lookup.main as? SiteExtractionResult.Success)?.candidates?.forEach { assertTrue(name, it.mediaUrl in OWN_FILES) }
        lookup.rows.forEach { row ->
            assertTrue(name, row.mediaUrl in OWN_FILES)
            assertEquals(name, "facebook:$VIDEO", row.videoId)
        }
        // Reads: only the video's files, each once (its first bytes), never with the session; every
        // row Master offers was read.
        val reads = lookup.of(Kind.FILE)
        reads.forEach { read ->
            assertTrue(name, read.url in OWN_FILES)
            assertEquals(name, SocialRowCheck.START_BYTES.toLong(), read.limit)
            assertTrue(name, read.headers.keys.none { it.equals("Cookie", ignoreCase = true) })
        }
        assertEquals(name, reads.map { it.url }.distinct(), reads.map { it.url })
        assertTrue(name, reads.size <= SocialRowCheck.MAX_READS)
        lookup.rows.forEach { row -> assertTrue(name, reads.any { it.url == row.mediaUrl }) }
        // Budgets: main's and Master's page asks (the reads aside), the time, the player's bytes.
        assertTrue(name, lookup.asks.count { it.kind != Kind.FILE } <= SocialBudget.FACEBOOK.requests)
        assertTrue(name, lookup.record.elapsedMillis() <= SocialBudget.FACEBOOK.millis)
        lookup.of(Kind.PLAYER).forEach { assertTrue(name, it.limit <= SocialBudget.FACEBOOK.bytes) }
        assertTrue(name, lookup.of(Kind.PLAYER).size <= 1)
        assertTrue(name, lookup.of(Kind.OTHER).isEmpty())
        // No ask repeated, none after a stop (main's and Master's): main's reel page with the
        // session after the one without it (the same address, another identity class: today's)
        // is the only address asked twice.
        assertEquals(name, 0, lookup.record.repeatAsks)
        val pages = lookup.of(Kind.PAGE).map { it.url }
        assertEquals(name, pages.size - pages.distinct().size, lookup.record.repeatedAsks)
        assertEquals(name, lookup.record.repeatedAsks, lookup.record.variantAsks)
        assertEquals(name, 0, lookup.record.asksAfterStop)
        // The engine's own check is never asked in a Facebook lookup: S6's is.
        assertTrue(name, world.validator.seen.isEmpty())
        assertBare(lookup.details)
    }

    private fun assertOwnRows(lookup: Lookup) {
        assertEquals(lookup.link, OWN_FILES, lookup.rows.map { it.mediaUrl }.toSet())
    }

    private fun assertLines(lookup: Lookup, vararg lines: String) {
        lines.forEach { assertTrue(lookup.details.toString(), it in lookup.details) }
    }

    /** No ID, link or page words in the lines. */
    private fun assertBare(details: List<String>) {
        details.forEach { line ->
            listOf(VIDEO, OTHER_VIDEO, "facebook.com", "fbcdn", "fixture", ENGLISH, BURMESE, MAIN_WORDS).forEach {
                assertFalse("$line has $it", line.contains(it))
            }
        }
    }

    /** What the world was asked. */
    private enum class Kind(val millis: Long) { PAGE(1_200), PLAYER(600), FILE(150), OTHER(1_000) }

    private class Ask(val kind: Kind, val url: String, val headers: Map<String, String>, val limit: Long)

    /** Where main's page carries the video's files, or what the page is instead. */
    private enum class Shape { MAIN, FILE_KEYS, ALL_KEYS, NESTED, OUTSIDE, UNDER_RELATED, SIGN_IN, PRIVATE, REMOVED, MAIN_WORDS, WORDS }

    /** The plugin player's answers. */
    private enum class Player(val answer: (String) -> ExtractorHttpResult) {
        NONE({ ok(NO_DATA, it) }),
        PLAYBACK({ ok(playerPage(files = false), it) }),
        FILES({ ok(playerPage(files = true), it) }),
        LIMITED({ ExtractorHttpResult.Failure(SiteExtractionFailure.RATE_LIMITED, 429) }),
        SIGN_IN({ ExtractorHttpResult.Success(200, SIGN_IN_PAGE, LOGIN, HTML) }),
    }

    /** A lookup's end: main's rows, Master's, a final answer (Master's or main's), no rows, the tab's own playback. */
    private enum class Outcome { MAIN, ROWS, FINAL, MAIN_FINAL, NO_ROWS, PLAYBACK }

    private class Case(val name: String, val shape: Shape, val player: Player, val outcome: Outcome)

    /** One lookup's end and what it asked. */
    private class Lookup(
        val link: String,
        val main: SiteExtractionResult,
        val master: MasterResult?,
        val record: LookupRecord,
        val asks: List<Ask>,
        val captures: Int,
    ) {
        val mainFailure: SiteExtractionFailure? get() = (main as? SiteExtractionResult.Failure)?.reason
        val rows get() = (master as? MasterResult.Success)?.result?.candidates.orEmpty()
        val details: List<String>
            get() = when (master) {
                is MasterResult.Success -> master.result.details
                is MasterResult.Failure -> master.details
                is MasterResult.NeedsPlayback -> master.details
                else -> emptyList()
            }

        fun of(kind: Kind): List<Ask> = asks.filter { it.kind == kind }
    }

    /**
     * The world: Facebook's pages ([page]: main's page for the video's addresses, by whether the
     * session went with the ask), the plugin player ([player]) and the media host ([file]), on a
     * fake clock ([now]; each ask takes its time). The engine's parts are built once, as the app
     * holds them: S7's cooldown and S6's row check are shared by the world's lookups.
     */
    private class World(shape: Shape = Shape.MAIN) {
        var now = START
        var page: (url: String, session: Boolean) -> ExtractorHttpResult = { url, _ -> ok(pageOf(shape), url) }
        var player: (url: String) -> ExtractorHttpResult = { ok(NO_DATA, it) }
        var file: (url: String) -> MediaStart = { start() }
        val validator = RecordingValidator()
        val capture = CountingCapture()
        private val asks = mutableListOf<Ask>()
        private val web = Web()
        private val engine = MasterFallbackEngine(
            SocialRowCheck(validator, Files()), capture, MasterPolicy(enabled = true),
            contracts = SiteContracts(web),
            social = FacebookLadder(web, ::identifyOrNull, cooldown = SocialCooldown({ now })),
        )

        /** One lookup of [link] as the app runs it: main first, Master after main's failure. */
        suspend fun lookup(link: String): Lookup {
            val identity = identify(link)
            val from = asks.size
            val captures = capture.calls
            val record = LookupRecord(clock = { now }, labels = SiteWays.FACEBOOK.labels())
            val (main, master) = withContext(record + MainPages()) {
                val answer = FacebookExtractor(MainPageRecorder(LookupRecorder(web))).extract(mainRequest(identity, COOKIE))
                answer to (answer as? SiteExtractionResult.Failure)?.let {
                    engine.extract(contractRequest(link, identity, it.reason, COOKIE))
                }
            }
            return Lookup(link, main, master, record, asks.drop(from), capture.calls - captures)
        }

        private fun ask(kind: Kind, url: String, headers: Map<String, String>, limit: Long) {
            asks += Ask(kind, url, headers, limit)
            now += kind.millis
        }

        private inner class Web : ExtractorHttpClient {
            override suspend fun get(url: String, headers: Map<String, String>, maxBodyBytes: Long): ExtractorHttpResult {
                val kind = when {
                    url in PAGES -> Kind.PAGE
                    url.startsWith(PLAYER) -> Kind.PLAYER
                    else -> Kind.OTHER
                }
                ask(kind, url, headers, maxBodyBytes)
                return when (kind) {
                    Kind.PAGE -> page(url, headers.keys.any { it.equals("Cookie", ignoreCase = true) })
                    Kind.PLAYER -> player(url)
                    else -> ExtractorHttpResult.Failure(SiteExtractionFailure.HTTP_STATUS, 404)
                }
            }

            override suspend fun postJson(
                url: String,
                body: String,
                headers: Map<String, String>,
                maxBodyBytes: Long,
            ): ExtractorHttpResult {
                ask(Kind.OTHER, url, headers, maxBodyBytes)
                return ExtractorHttpResult.Failure(SiteExtractionFailure.HTTP_STATUS, 404)
            }

            override suspend fun probe(url: String, headers: Map<String, String>, timeoutMillis: Long): ExtractorProbeResult {
                ask(Kind.OTHER, url, headers, 0)
                return ExtractorProbeResult.Unsupported
            }
        }

        private inner class Files : MediaStartReader {
            override suspend fun read(url: String, headers: Map<String, String>, maxBytes: Int): MediaStart {
                ask(Kind.FILE, url, headers, maxBytes.toLong())
                return if (url in ALL_FILES) file(url) else MediaStart.Failed(404)
            }
        }
    }

    private companion object {
        const val START = 1_800_000_000_000L
        const val MINUTE = 60_000L
        const val VIDEO = "1000000000000003"
        const val OTHER_VIDEO = "1000000000000009"
        const val WATCH = "https://www.facebook.com/watch/?v=$VIDEO"
        const val REEL = "https://www.facebook.com/reel/$VIDEO"
        const val PAGE_HOST = "www.facebook.com"
        const val PLAYER = "https://www.facebook.com/plugins/video.php?href="
        const val LOGIN = "https://www.facebook.com/login/?next=fixture"
        const val COOKIE = "c_user=0; xs=fixture-cookie"
        const val HTML = "text/html"
        const val F1 = "master: facebook F1 plugin player"
        const val F2 = "master: facebook F2 main's pages"
        const val MAIN_WORDS = "This content isn't available right now"
        const val ENGLISH = "This content is not here for you now"
        const val BURMESE = "ဒီအကြောင်းအရာကို အခု ကြည့်လို့ မရပါ"
        const val NO_DATA = "<html><head><title>Facebook</title></head><body>no data</body></html>"
        const val SIGN_IN_PAGE = "<html><head><title>Facebook</title></head><body>sign in</body></html>"
        const val LOGIN_FORM_PAGE = "<html><head><title>Facebook</title></head><body>" +
            "<form id=\"loginform\" method=\"post\"><input name=\"email\"></form></body></html>"

        val LINKS = listOf(WATCH, REEL)
        val PAGES = LINKS.map { identify(it).canonicalPageUrl }.toSet()
        val OWN_FILES = setOf(hd(VIDEO), sd(VIDEO))
        val ALL_FILES = OWN_FILES + setOf(hd(OTHER_VIDEO), sd(OTHER_VIDEO))

        fun identifyOrNull(url: String): SitePageIdentity? = FacebookExtractor(FakeExtractorHttpClient()).identify(url)

        fun identify(url: String): SitePageIdentity = checkNotNull(identifyOrNull(url))

        fun hd(video: String) = "https://video.fixture.fbcdn.net/v/$video-hd.mp4?oe=F2A52380&oh=fixture"

        fun sd(video: String) = "https://video.fixture.fbcdn.net/v/$video-sd.mp4?oe=F2A52380&oh=fixture"

        fun ok(body: String, url: String) = ExtractorHttpResult.Success(200, body, url, HTML)

        /** A file's first bytes as the media host answers them: an MP4 start of a 32 KiB file unless named. */
        fun start(bytes: ByteArray = InitBytes.mp4()) =
            MediaStart.Read(206, "video/mp4", "bytes 0-${bytes.size - 1}/32768", bytes.size.toLong(), bytes)

        /** A video's files under [hd] and [sd] keys. */
        fun files(video: String, hd: String, sd: String) = "\"$hd\":\"${hd(video)}\",\"$sd\":\"${sd(video)}\""

        /**
         * Main's page for the video in [shape]: one data script with the video's object and a
         * related video beside it (its own ID and files); or the page Facebook shows instead.
         */
        fun pageOf(shape: Shape): String {
            val main = files(VIDEO, "browser_native_hd_url", "browser_native_sd_url")
            val renamed = files(VIDEO, "browser_native_hd_url_v2", "browser_native_sd_url_v2")
            val related = "{\"video_id\":\"$OTHER_VIDEO\",\"videoDeliveryLegacyFields\":{" +
                files(OTHER_VIDEO, "browser_native_hd_url", "browser_native_sd_url") + "}}"
            val renamedRelated = "{\"video_id\":\"$OTHER_VIDEO\",\"videoDeliveryLegacyFields\":{" +
                files(OTHER_VIDEO, "browser_native_hd_url_v2", "browser_native_sd_url_v2") + "}}"
            val payload = when (shape) {
                Shape.MAIN -> "\"video\":{\"video_id\":\"$VIDEO\",\"videoDeliveryLegacyFields\":{$main}},\"related\":[$related]"
                Shape.FILE_KEYS -> "\"video\":{\"video_id\":\"$VIDEO\",\"videoDeliveryLegacyFields\":{$renamed}},\"related\":[$renamedRelated]"
                Shape.ALL_KEYS -> "\"video\":{\"video_id\":\"$VIDEO\",\"videoDeliveryLegacyFields_v2\":{$renamed}}," +
                    "\"related\":[${renamedRelated.replace("videoDeliveryLegacyFields", "videoDeliveryLegacyFields_v2")}]"
                Shape.NESTED -> "\"video\":{\"video_id\":\"$VIDEO\",\"videoDeliveryLegacyFields\":{},\"playback\":{\"sources\":{$main}}}," +
                    "\"related\":[$related]"
                Shape.OUTSIDE -> "\"video\":{\"video_id\":\"$VIDEO\",\"videoDeliveryLegacyFields\":{}},\"playback\":{\"sources\":{$main}}," +
                    "\"related\":[$related]"
                Shape.UNDER_RELATED -> "\"video\":{\"video_id\":\"$VIDEO\",\"videoDeliveryLegacyFields\":{}}," +
                    "\"related\":[{\"video_id\":\"$OTHER_VIDEO\",\"playback\":{\"sources\":{$main}}}]"
                Shape.SIGN_IN -> return LOGIN_FORM_PAGE
                Shape.PRIVATE -> return routePage(FacebookAnswerJudge.ERROR_ROUTE, privacy = true, title = BURMESE)
                Shape.REMOVED -> return routePage(FacebookAnswerJudge.NOT_FOUND_ROUTE, title = BURMESE)
                Shape.MAIN_WORDS -> return routePage(FacebookAnswerJudge.ERROR_ROUTE, privacy = true, title = MAIN_WORDS)
                Shape.WORDS -> return "<html><head><title>Facebook</title></head><body><h1>$BURMESE</h1></body></html>"
            }
            return "<html><head><title>Facebook</title></head><body>" +
                "<script type=\"application/json\" data-sjs>{\"require\":[[\"VideoPlayerRelay\",null,null,[{$payload}]]]}</script>" +
                "</body></html>"
        }

        /**
         * The plugin player's shape (FacebookContractTest's) naming the video; [files]: its files
         * and a related video's item beside it; else needs playback (no file).
         */
        fun playerPage(files: Boolean): String {
            fun item(video: String, withFiles: Boolean): String {
                val hd = if (withFiles) "\"${hd(video).replace("/", "\\/")}\"" else "null"
                val sd = if (withFiles) "\"${sd(video).replace("/", "\\/")}\"" else "null"
                return "{\"is_hls\":false,\"video_id\":\"$video\",\"hd_src\":$hd,\"sd_src\":$sd,\"dash_manifest\":null," +
                    "\"original_height\":720,\"original_width\":1280,\"videoLicenseUriMap\":{}," +
                    "\"graphApiVideoLicenseUri\":null,\"OzDrmHelper\":null}"
            }
            val items = listOfNotNull(item(VIDEO, files), if (files) item(OTHER_VIDEO, true) else null).joinToString(",")
            return "<!DOCTYPE html><html><head><title>Facebook</title></head><body>" +
                "<script nonce=\"fixture\">requireLazy([\"TimeSliceImpl\",\"ServerJS\"],function(TimeSlice,ServerJS){" +
                "var s=(new ServerJS());s.handle({\"instances\":[[\"__inst_fixture\",[\"VideoConfig\"],[{" +
                "\"ad_client_token\":null,\"video_id\":\"$VIDEO\",\"videoData\":[$items]}],1]]});});</script></body></html>"
        }

        /** A page of the route's structure (SiteAnswerJudgeTest's): the route's name, its root's props. */
        fun routePage(route: String, privacy: Boolean? = null, title: String): String {
            val props = buildList {
                add("\"title\":\"$title\"")
                privacy?.let { add("\"privacy\":$it") }
            }.joinToString(",")
            return "<html><head><title>Facebook</title></head><body>" +
                "<script type=\"application/json\" data-sjs>{\"require\":[[\"RouteInfo\",null,null,[{\"initialRouteInfo\":" +
                "{\"route\":{\"actorID\":\"0\",\"rootView\":{\"resource\":{\"__dr\":\"Root.react\"},\"props\":{$props}}," +
                "\"tracePolicy\":\"comet.test\",\"canonicalRouteName\":\"$route\"}}}]]]}</script></body></html>"
        }
    }
}
