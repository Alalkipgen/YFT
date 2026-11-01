package com.alal.yft.detection.master

import com.alal.yft.core.model.media.BrowserRequestContext
import com.alal.yft.core.model.media.CandidateSource
import com.alal.yft.core.model.media.MediaCandidate
import com.alal.yft.core.model.media.MediaKind
import com.alal.yft.detection.MergeSupport
import com.alal.yft.detection.SiteAdapterCoordinator
import com.alal.yft.detection.SiteAdapterOutcome
import com.alal.yft.extractor.api.ExtractorHttpClient
import com.alal.yft.extractor.api.ExtractorHttpResult
import com.alal.yft.extractor.api.ExtractorProbeResult
import com.alal.yft.extractor.api.SiteAdapterFlags
import com.alal.yft.extractor.api.SiteExtractionFailure
import com.alal.yft.extractor.api.SiteExtractorRegistry
import com.alal.yft.extractor.master.CaptureResult
import com.alal.yft.extractor.master.MasterFallbackEngine
import com.alal.yft.extractor.master.MasterMediaValidator
import com.alal.yft.extractor.master.MasterPolicy
import com.alal.yft.extractor.master.MasterRequest
import com.alal.yft.extractor.master.PlaybackCaptureProvider
import com.alal.yft.extractor.master.ValidationResult
import com.alal.yft.extractor.master.android.WebViewPlaybackCapture
import com.alal.yft.extractor.master.contract.SiteContracts
import com.alal.yft.extractor.master.lookup.LookupAsk
import com.alal.yft.extractor.master.lookup.LookupRecord
import com.alal.yft.extractor.master.lookup.LookupRecorder
import com.alal.yft.extractor.master.lookup.RouteBHttpClient
import com.alal.yft.extractor.master.modules.youtube.ladder.MediaStart
import com.alal.yft.extractor.master.modules.youtube.ladder.MediaStartReader
import com.alal.yft.extractor.master.social.SiteRowCheck
import com.alal.yft.extractor.master.social.SocialCooldown
import com.alal.yft.extractor.master.social.SocialFill
import com.alal.yft.extractor.sites.facebook.FacebookExtractor
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Weak-answer fill (`B+` in Lookup details; docs/ARCHITECTURE.md) in the app's lookup: a Facebook
 * page main found with weak rows (no HD row) keeps main's rows exactly as main gave them, first,
 * and gets only the HD row Master's ladder finds that they lack, after V read its file and the
 * census's one-row check passed it (never with the user's session); a strong answer, a build
 * without the fill and every other case return main's answer at once, nothing asked; whatever stops
 * the fill (a 429 in the lookup, a failed file check, the page moving on) leaves main's answer with
 * the fill's line saying why. Main's real Facebook adapter over synthetic pages with made-up IDs,
 * fake files and file checks; each case passes the switches explicitly. No live calls.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class SocialLadderFillTest {
    @Test
    fun aWeakAnswerKeepsMainsRowsFirstAndGetsOnlyTheHdRowItLacks() = runTest {
        val fixture = Fixture(socialTrial = true)
        val primary = fixture.inspect() as SiteAdapterOutcome.Detected
        assertEquals(listOf(mainSd(VIDEO)), primary.candidates.map { it.mediaUrl })
        assertEquals("$TITLE — SD", primary.candidates.single().title)
        assertEquals(setOf(SocialFill.Weak.NO_HD), SocialFill.weakness(primary.candidates))
        assertTrue(fixture.bridge.fills(WATCH, primary))
        // A fill build's weak answer goes to the fill, not the trial.
        assertSame(primary, fixture.bridge.trialPrimary(WATCH, primary))

        val filled = fixture.recover(primary) as SiteAdapterOutcome.Detected

        // Main's row first, exactly as main gave it; Master's HD row after it, under main's video.
        assertEquals("facebook", filled.adapterId)
        assertEquals(primary.candidates, filled.candidates.take(primary.candidates.size))
        val added = filled.candidates.drop(primary.candidates.size).single()
        assertEquals(hd(VIDEO), added.mediaUrl)
        assertEquals(720, added.height)
        assertEquals("facebook:$VIDEO", added.videoId)
        // Listed with main's video under its name (the sheet groups by the key and the base title).
        assertTrue(added.title.toString(), added.title?.startsWith(TITLE) == true)
        // Main's details first, then the fill's line, then Master's own.
        assertEquals(primary.details, filled.details.take(primary.details.size))
        assertEquals(LINE, filled.details[primary.details.size])
        assertTrue(filled.details.toString(), filled.details.any { it.startsWith("master: facebook ladder after NO_MEDIA_FOUND") })
        assertTrue(SocialLadder.trialNotice(filled).toString(), SocialLadder.trialNotice(filled)?.startsWith(LINE) == true)
        assertTrue(filled.details.toString(), filled.details.none { "://" in it || VIDEO in it })

        // The plugin player once; each of its files' start read once (their sizes unknown before),
        // without the user's session; only the HD file checked, once, without the session; no capture.
        assertEquals(listOf(embedUrl(watchPage)), fixture.master.asked)
        assertEquals(setOf(hd(VIDEO), sd(VIDEO)), fixture.files.asked.toSet())
        assertTrue(fixture.files.headers.all { headers -> headers.keys.none { it.equals("Cookie", ignoreCase = true) } })
        assertEquals(listOf(hd(VIDEO)), fixture.master.probed)
        assertTrue(fixture.master.probeHeaders.all { headers -> headers.keys.none { it.equals("Cookie", ignoreCase = true) } })
        assertEquals(0, fixture.captures.get())
    }

    @Test
    fun aStrongAnswerOrABuildWithoutBPlusReturnsMainsAnswerAtOnce() = runTest {
        // Main's HD and SD rows: not weak; Master is not asked.
        val strong = Fixture(page = mainPage(VIDEO, hd = true), socialTrial = false)
        val found = strong.inspect() as SiteAdapterOutcome.Detected
        assertEquals(2, found.candidates.size)
        assertTrue(SocialFill.weakness(found.candidates).isEmpty())
        assertFalse(strong.bridge.fills(WATCH, found))
        assertSame(found, strong.recover(found))
        strong.assertNothingAsked()

        // A weak answer in a build without the fill (no fill run: every build but
        // -Pyft.socialFill's debug one), without the social ladder, or with Master off: main's
        // answer, nothing asked.
        for (off in listOf(Fixture(fill = false), Fixture(socialLadder = false), Fixture(enabledBuild = false))) {
            val weak = off.inspect() as SiteAdapterOutcome.Detected
            assertEquals(1, weak.candidates.size)
            assertFalse(off.bridge.fills(WATCH, weak))
            assertSame(weak, off.recover(weak))
            off.assertNothingAsked()
        }
        assertFalse(BrowserMasterFallback.None.fills(WATCH, Fixture().inspect()))
        assertFalse(SocialLadder.fill) // no unit-test build passes -Pyft.socialFill
    }

    @Test
    fun whenNoRowCanBeAddedMainsAnswerStandsWithBPlusLineSayingWhy() = runTest {
        // A 429 earlier in the lookup (main's): nothing new is asked, main's answer stands.
        val limited = Fixture()
        val weak = limited.inspect() as SiteAdapterOutcome.Detected
        val stopped = limited.recover(weak) { record ->
            val ask = LookupAsk(LookupAsk.GET, "https://www.facebook.com/fixture-limited", null, emptyMap())
            record.record(ask, ExtractorHttpResult.Failure(SiteExtractionFailure.RATE_LIMITED, 429), record.now())
        } as SiteAdapterOutcome.Detected
        assertEquals(weak.candidates, stopped.candidates)
        assertTrue(stopped.details.toString(), stopped.details.any { it.startsWith("$NONE(") })
        assertTrue(limited.master.asked.toString(), limited.master.asked.isEmpty())
        assertTrue(limited.files.asked.isEmpty())
        assertTrue(limited.master.probed.isEmpty())

        // The HD file's check answers 429: none added, no other check after it.
        val refused = Fixture(probe = ExtractorProbeResult.Refused(SiteExtractionFailure.RATE_LIMITED, 429))
        val refusedAnswer = refused.recover(refused.inspect()) as SiteAdapterOutcome.Detected
        assertEquals(1, refusedAnswer.candidates.size)
        assertTrue(refusedAnswer.details.toString(), "${NONE}(the file check passed none of Master's 1)" in refusedAnswer.details)
        assertEquals(1, refused.master.probed.size)

        // The plugin player has no files: Master found no row.
        val empty = Fixture(plugin = embed(VIDEO, files = false))
        val emptyAnswer = empty.recover(empty.inspect()) as SiteAdapterOutcome.Detected
        assertEquals(1, emptyAnswer.candidates.size)
        assertTrue(emptyAnswer.details.toString(), emptyAnswer.details.any { it.startsWith("${NONE}(Master found no row") })
        assertTrue(empty.master.probed.isEmpty())

        // Master's files are no taller than main's: nothing they lack.
        val small = Fixture(picture = { 640 to 360 })
        val smallAnswer = small.recover(small.inspect()) as SiteAdapterOutcome.Detected
        assertEquals(1, smallAnswer.candidates.size)
        assertTrue(smallAnswer.details.toString(), "${NONE}(no row of Master's has what main's lack)" in smallAnswer.details)
        assertTrue(small.master.probed.isEmpty())

        // The page moved on during the lookup: main's answer stands.
        val moved = Fixture()
        moved.duringAsk = { moved.capture.session.navigate(OTHER_WATCH) }
        val movedAnswer = moved.recover(moved.inspect()) as SiteAdapterOutcome.Detected
        assertEquals(1, movedAnswer.candidates.size)
        assertTrue(movedAnswer.details.toString(), movedAnswer.details.any { it.startsWith("${NONE}(the page changed during the lookup)") })
        assertTrue(moved.master.probed.isEmpty())
        listOf(stopped, refusedAnswer, emptyAnswer, smallAnswer, movedAnswer).forEach { answer ->
            assertTrue(answer.details.toString(), answer.details.none { "://" in it || VIDEO in it })
        }
    }

    @Test
    fun theFileCheckNeverSendsTheUsersSession() {
        val row = MediaCandidate(
            WATCH, hd(VIDEO), setOf(CandidateSource.MANIFEST), MediaKind.DIRECT, mimeType = "video/mp4",
            requestContext = BrowserRequestContext(
                WATCH, "Browser", "c_user=fixture",
                mapOf("Authorization" to "Bearer fixture", "Proxy-Authorization" to "Basic fixture", "Referer" to WATCH),
            ),
        )
        val sent = SocialFillRun.withoutSession(row)
        assertEquals(mapOf("Referer" to WATCH, "User-Agent" to "Browser", "Accept" to "*/*"), sent.requestContext.replayHeaders())
        // Only the check's copy: the row itself (the download's) is not changed.
        assertEquals("c_user=fixture", row.requestContext.cookie)
        assertEquals(row, sent.copy(requestContext = row.requestContext))
    }

    /** Synthetic answers by address (main's page or the plugin player), anything else a 404; file checks. */
    private class Answers(
        private val probeAnswer: () -> ExtractorProbeResult = { ExtractorProbeResult.Answered(206, FILE_BYTES) },
        private val answer: (String) -> ExtractorHttpResult?,
    ) : ExtractorHttpClient {
        val asked = CopyOnWriteArrayList<String>()
        val probed = CopyOnWriteArrayList<String>()
        val probeHeaders = CopyOnWriteArrayList<Map<String, String>>()

        override suspend fun get(url: String, headers: Map<String, String>, maxBodyBytes: Long): ExtractorHttpResult {
            asked += url
            return answer(url) ?: ExtractorHttpResult.Failure(SiteExtractionFailure.HTTP_STATUS, 404)
        }

        override suspend fun postJson(
            url: String,
            body: String,
            headers: Map<String, String>,
            maxBodyBytes: Long,
        ): ExtractorHttpResult = ExtractorHttpResult.Failure(SiteExtractionFailure.NETWORK)

        override suspend fun probe(url: String, headers: Map<String, String>, timeoutMillis: Long): ExtractorProbeResult {
            probed += url
            probeHeaders += headers
            return probeAnswer()
        }
    }

    /** A fake file reader: an MP4 start stating the file's length and its picture ([picture], by address). */
    private class Files(private val picture: (String) -> Pair<Int, Int>) : MediaStartReader {
        val asked = CopyOnWriteArrayList<String>()
        val headers = CopyOnWriteArrayList<Map<String, String>>()

        override suspend fun read(url: String, headers: Map<String, String>, maxBytes: Int): MediaStart {
            asked += url
            this.headers += headers
            val bytes = mp4Start(MILLIS, picture(url)).copyOf(maxBytes.coerceAtMost(4_096))
            return MediaStart.Read(206, "video/mp4", "bytes 0-${bytes.size - 1}/$FILE_BYTES", bytes.size.toLong(), bytes)
        }
    }

    private class Fixture(
        socialLadder: Boolean = true,
        fill: Boolean = true,
        socialTrial: Boolean = false,
        enabledBuild: Boolean = true,
        page: String = mainPage(VIDEO, hd = false),
        plugin: String = embed(VIDEO),
        probe: ExtractorProbeResult = ExtractorProbeResult.Answered(206, FILE_BYTES),
        picture: (String) -> Pair<Int, Int> = { url -> if ("-hd.mp4" in url) 1280 to 720 else 640 to 360 },
    ) {
        var duringAsk: () -> Unit = {}
        val main = Answers { url -> if (url == watchPage) ok(page, url) else null }
        val master = Answers({ probe }) { url ->
            if (url == embedUrl(watchPage)) {
                duringAsk()
                ok(plugin, url)
            } else {
                null
            }
        }
        val files = Files(picture)
        val captures = AtomicInteger()
        val capture = WebViewPlaybackCapture(enabledBuild)
        val sites = SiteAdapterCoordinator(
            SiteExtractorRegistry(
                listOf(FacebookExtractor(SocialLadder.pages(LookupRecorder(main), socialLadder))),
                SiteAdapterFlags { true },
            ),
            MergeSupport { true },
        )
        private val scope = capture.session.navigate(WATCH)!!

        /** The test's own cooldown (the app's stays untouched). */
        private val cooldown = SocialCooldown()
        private val validator = SocialLadder.validator(
            MasterMediaValidator { found, _ -> ValidationResult.Valid(found.copy(contentLengthBytes = 4096)) },
            social = socialLadder,
        ) { files }
        private val contracts = SiteContracts(master)
        private val stage = SocialLadder.stage(master, sites::identity, social = socialLadder, cooldown = cooldown)
        val bridge = AndroidBrowserMasterFallback(
            capture,
            sites,
            MasterFallbackEngine(
                validator,
                PlaybackCaptureProvider {
                    captures.incrementAndGet()
                    CaptureResult.Unavailable
                },
                MasterPolicy(enabled = true),
                contracts = contracts,
                social = stage,
            ),
            MergeSupport { true },
            // The page's own context, the user's session in it (the fill never sends it with a
            // file).
            requestFactory = { reason, at, expected ->
                MasterRequest(WATCH, scope.generation, at, reason, expected, BrowserRequestContext(WATCH, "Browser", "c_user=fixture"))
            },
            socialLadder = socialLadder,
            socialCooldown = cooldown,
            socialTrial = socialTrial,
            fillRun = if (fill && stage != null) {
                SocialFillRun(stage, validator, contracts, SiteRowCheck(RouteBHttpClient(master)))
            } else {
                null
            },
        )

        /** Main's answer for the page, asked afresh. */
        suspend fun inspect(): SiteAdapterOutcome =
            sites.inspect(WATCH, BrowserRequestContext(WATCH, "Browser", "c_user=fixture"), NOW, fresh = true)

        /** The app's lookup of a found page: main asks first, [before] sees the record, then Master's part. */
        suspend fun recover(primary: SiteAdapterOutcome, before: (LookupRecord) -> Unit = {}): SiteAdapterOutcome =
            withContext(bridge.lookupContext(WATCH)) {
                inspect()
                currentCoroutineContext()[LookupRecord]?.let(before)
                bridge.recover(WATCH, primary, NOW)
            }

        fun assertNothingAsked() {
            assertTrue(master.asked.toString(), master.asked.isEmpty())
            assertTrue(files.asked.isEmpty())
            assertTrue(master.probed.isEmpty())
            assertEquals(0, captures.get())
        }
    }

    private companion object {
        const val NOW = 1_800_000_000_000L
        const val VIDEO = "1000000000000021"
        const val OTHER_VIDEO = "1000000000000022"
        const val WATCH = "https://www.facebook.com/watch/?v=$VIDEO"
        const val OTHER_WATCH = "https://www.facebook.com/watch/?v=$OTHER_VIDEO"
        const val MILLIS = 61_500L
        const val FILE_BYTES = 9_437_184L
        const val MEDIA_HOST = "video.fixture.fbcdn.net"
        const val LINE = "master: facebook B+ (main's rows: no HD): Master added 1: 720p"
        const val NONE = "master: facebook B+ (main's rows: no HD): Master added none "

        /** The address main's adapter reads for the video. */
        val watchPage: String = checkNotNull(FacebookExtractor(Answers { null }).identify(WATCH)).canonicalPageUrl

        fun ok(body: String, url: String) = ExtractorHttpResult.Success(200, body, url, "text/html")

        fun embedUrl(page: String) = "https://www.facebook.com/plugins/video.php?href=" +
            URLEncoder.encode(page, StandardCharsets.UTF_8).replace("+", "%20")

        fun hd(video: String) = "https://$MEDIA_HOST/v/$video-hd.mp4?oe=F2A52380&oh=fixture"

        fun sd(video: String) = "https://$MEDIA_HOST/v/$video-sd.mp4?oe=F2A52380&oh=fixture"

        fun mainHd(video: String) = "https://video.example-cdn.test/m/$video-hd.mp4?oe=F2A52380&oh=main"

        fun mainSd(video: String) = "https://video.example-cdn.test/m/$video-sd.mp4?oe=F2A52380&oh=main"

        /** The made-up video's title: main's rows are named `<title> — SD` (the sheet reads the word). */
        const val TITLE = "Fixture clip"

        /** Main's page for [video]: one data script with the video's object, its title, SD file (and HD with [hd]) and duration. */
        fun mainPage(video: String, hd: Boolean): String {
            val files = listOfNotNull(
                "\"browser_native_hd_url\":\"${mainHd(video)}\"".takeIf { hd },
                "\"browser_native_sd_url\":\"${mainSd(video)}\"",
            ).joinToString(",")
            return "<html><head><title>Facebook</title></head><body>" +
                "<script type=\"application/json\" data-sjs>{\"require\":[[\"VideoPlayerRelay\",null,null,[{" +
                "\"video\":{\"video_id\":\"$video\",\"title\":{\"text\":\"$TITLE\"},\"playable_duration_in_ms\":$MILLIS," +
                "\"videoDeliveryLegacyFields\":{$files}}}]]]}</script>" +
                "</body></html>"
        }

        /** The plugin player's shape (FacebookContractTest's) for [video]: its two files ([files]), no length. */
        fun embed(video: String, files: Boolean = true): String {
            val hd = if (files) "\"${hd(video).replace("/", "\\/")}\"" else "null"
            val sd = if (files) "\"${sd(video).replace("/", "\\/")}\"" else "null"
            return "<!DOCTYPE html><html><head><title>Facebook</title></head><body>" +
                "<script nonce=\"fixture\">requireLazy([\"TimeSliceImpl\",\"ServerJS\"],function(TimeSlice,ServerJS){" +
                "var s=(new ServerJS());s.handle({\"instances\":[[\"__inst_fixture\",[\"VideoConfig\"],[{" +
                "\"ad_client_token\":null,\"video_id\":\"$video\",\"videoData\":[{\"is_hls\":false," +
                "\"video_id\":\"$video\",\"hd_src\":$hd,\"sd_src\":$sd,\"dash_manifest\":null," +
                "\"original_height\":720,\"original_width\":1280,\"videoLicenseUriMap\":{}," +
                "\"graphApiVideoLicenseUri\":null,\"OzDrmHelper\":null}]}],1]]});});</script></body></html>"
        }

        /**
         * A progressive MP4 file's first 4096 bytes (SocialTrialTest's): the file type, then a `moov`
         * whose movie header (timescale 1000) states [millis] and whose picture track's header states
         * [picture] (width to height).
         */
        fun mp4Start(millis: Long, picture: Pair<Int, Int>): ByteArray {
            fun u32(value: Long) = ByteArray(4) { ((value ushr (24 - 8 * it)) and 0xFF).toByte() }
            fun box(type: String, payload: ByteArray) = u32(8L + payload.size) + type.toByteArray() + payload
            val ftyp = box("ftyp", "isom".toByteArray() + u32(512) + "isom".toByteArray() + "mp41".toByteArray())
            val mvhd = box("mvhd", u32(0) + u32(0) + u32(0) + u32(1_000) + u32(millis) + ByteArray(80))
            val identity = listOf(0x10000L, 0L, 0L, 0L, 0x10000L, 0L, 0L, 0L, 0x40000000L).fold(ByteArray(0)) { bytes, n -> bytes + u32(n) }
            val (width, height) = picture
            val track = box("trak", box("tkhd", ByteArray(40) + identity + u32(width.toLong() shl 16) + u32(height.toLong() shl 16)))
            val moov = u32(65_536) + "moov".toByteArray() + mvhd + track + u32(60_000) + "trak".toByteArray()
            return (ftyp + moov).copyOf(4_096)
        }
    }
}
