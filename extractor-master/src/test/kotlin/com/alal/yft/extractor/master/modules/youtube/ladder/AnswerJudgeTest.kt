package com.alal.yft.extractor.master.modules.youtube.ladder

import com.alal.yft.extractor.api.ExtractorHttpResult
import com.alal.yft.extractor.api.SiteExtractionFailure
import com.alal.yft.extractor.api.json.BoundedJsonParser
import com.alal.yft.extractor.api.json.get
import com.alal.yft.extractor.master.modules.youtube.YouTubeClientProfile
import com.alal.yft.extractor.master.modules.youtube.YouTubeClientProfiles
import com.alal.yft.extractor.master.modules.youtube.YouTubePageSignals
import com.alal.yft.extractor.master.modules.youtube.testing.Fixtures
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/**
 * Phase 1.3 D2 (MASTER_KEY_PHASE1_3_PLAN.md §4 D2): the answer judge's class for every kind of
 * answer — main's fixtures, the simulator's L layout changes, V7's one-client refusals, the
 * definite markers that keep a verdict terminal, HTTP failures, V's 403 and the HLS host rule.
 * No request; every answer is a fixture or made in code.
 */
class AnswerJudgeTest {
    @Test
    fun `main's fixtures get the class the census and the ladder act on`() {
        val expected = listOf(
            Case("player_visionos.json", VISION_OS, CensusClass.DIRECT),
            // main's ok and 4k answers carry formats without a size (itag 22; ok's 251 too):
            // INCOMPLETE, as v1's census classes them; with every size they are DIRECT (below).
            Case("player_ok.json", VISION_OS, CensusClass.INCOMPLETE),
            Case("player_4k.json", VISION_OS, CensusClass.INCOMPLETE),
            Case("player_android.json", ANDROID, CensusClass.SABR_ONLY),
            Case("player_cipher.json", VISION_OS, CensusClass.NEEDS_SCRIPT),
            Case("player_cipher.json", MWEB, CensusClass.NEEDS_SCRIPT),
            Case("player_bot_check.json", VISION_OS, CensusClass.BOT_CHECK),
            Case("player_age_gate.json", VISION_OS, CensusClass.AGE_CHECK),
            Case("player_sabr_only.json", VISION_OS, CensusClass.SABR_ONLY),
            Case("player_malformed.json", VISION_OS, CensusClass.REFUSED),
            Case("player_private.json", VISION_OS, CensusClass.VIDEO_VERDICT),
            Case("player_geo.json", VISION_OS, CensusClass.VIDEO_VERDICT),
            Case("player_drm.json", VISION_OS, CensusClass.VIDEO_VERDICT),
            Case("player_live.json", VISION_OS, CensusClass.VIDEO_VERDICT),
            Case("player_embed_refused.json", EMBEDDED, CensusClass.REFUSED),
            Case("player_unavailable.json", VISION_OS, CensusClass.REFUSED),
            Case("player_no_status.json", VISION_OS, CensusClass.SHAPE_CHANGED),
        )
        for (case in expected) {
            val verdict = judge(fixture(case.file), case.profile)
            val id = "${case.file} as ${case.profile.id}"
            assertEquals(id, case.answerClass, verdict.answerClass)
            assertEquals(id, case.answerClass == CensusClass.AGE_CHECK || case.answerClass == CensusClass.VIDEO_VERDICT, verdict.terminal)
            assertFalse(id, verdict.toString().contains("://"))
        }
        for (file in listOf("player_ok.json", "player_4k.json")) {
            assertEquals(file, CensusClass.DIRECT, judge(withSizes(fixture(file)), VISION_OS).answerClass)
        }
        // The census's MWEB answer: direct addresses whose n only the player script computes.
        assertEquals(CensusClass.NEEDS_SCRIPT, judge(Census.mobileWeb(VIDEO), MWEB).answerClass)
        // The same answer from a client that needs no script is read as it is.
        assertEquals(CensusClass.DIRECT, judge(Census.mobileWeb(VIDEO), VISION_OS).answerClass)
    }

    @Test
    fun `a protected, SABR-only or ciphered answer is never DIRECT, whoever asked`() {
        val answers = listOf(
            "player_cipher.json",
            "player_cipher_ladder.json",
            "player_sabr_only.json",
            "player_android.json",
            "player_drm.json",
            "player_live.json",
        ).map(::fixture) + Census.visionOsCiphered(VIDEO)
        for ((index, body) in answers.withIndex()) {
            for (profile in listOf(VISION_OS, ANDROID, EMBEDDED, MWEB)) {
                assertNotEquals("answer $index as ${profile.id}", CensusClass.DIRECT, judge(body, profile).answerClass)
            }
        }
        val drm = judge(fixture("player_drm.json"))
        assertTrue(drm.terminal)
        assertNull(drm.video)
    }

    @Test
    fun `the simulator's layout changes are SHAPE_CHANGED with their kind and verdicts stay verdicts`() {
        val seen = LinkedHashMap<String, Int>()
        for (base in Sim.BASES) {
            for (case in LShapeCases.ALL) {
                val world = SimWorld(base).apply(case.setup)
                val body = SimJson.write(world.answer(SimAsk(SimClient.VISION_OS, Sim.BASE_VERSION, visitor = false, sts = false)))
                val verdict = AnswerJudge.judge(VISION_OS, body, Sim.VIDEO_ID, Sim.NOW)
                val id = "$case on $base"
                val klass = case.name.substringBefore(' ')
                assertNotEquals(id, CensusClass.DIRECT, verdict.answerClass)
                when {
                    klass in MOVED_CLASSES -> assertShape(id, AnswerJudge.ShapeKind.MOVED, verdict)
                    klass in PARTIAL_CLASSES -> assertShape(id, AnswerJudge.ShapeKind.PARTIAL, verdict)
                    case.name == "L11 age" -> assertTerminal(id, CensusClass.AGE_CHECK, verdict)
                    klass == "L11" || klass == "L12" -> assertTerminal(id, CensusClass.VIDEO_VERDICT, verdict)
                    klass == "L13" -> {
                        assertShape(id, AnswerJudge.ShapeKind.MOVED, verdict)
                        assertTrue(id, world.hls.present)
                        assertEquals(id, world.hls.masterUrl, verdict.hls)
                    }
                    klass == "L14" -> {
                        assertEquals(id, CensusClass.NEEDS_SCRIPT, verdict.answerClass)
                        assertNull(id, verdict.shapeKind)
                    }
                    else -> fail("$id: no expectation")
                }
                seen.merge(klass, 1, Int::plus)
            }
        }
        // Every L class of the simulator (L1–L16, with L4's and L11's variants) on every base.
        assertEquals((1..16).map { "L$it" }.toSet(), seen.keys)
        assertEquals(LShapeCases.ALL.size * Sim.BASES.size, seen.values.sum())
    }

    @Test
    fun `V7 - an error screen without a definite marker is one client's refusal, not a verdict`() {
        val answers = mapOf(
            // SimCases' V7: visionOS refuses the content on this app.
            "not on this app" to refusal(
                "UNPLAYABLE",
                "The following content is not available on this app.",
                subreason = "Watch this content on the latest version of YouTube.",
            ),
            // Video 6 (made for kids) as visionOS answered it in valid runs 1–3, built in code.
            "video 6" to VIDEO_6_LIKE,
            // The embedded player's error 152-18 (census videos 2 and 6).
            "embedded 152-18" to refusal(
                "ERROR",
                "This video is unavailable",
                subreason = "Error code: 152 - 18",
                button = "Watch video on YouTube",
            ),
            "player_unavailable.json" to fixture("player_unavailable.json"),
            "player_embed_refused.json" to fixture("player_embed_refused.json"),
        )
        for ((name, body) in answers) {
            for (profile in listOf(VISION_OS, EMBEDDED, MWEB)) {
                val verdict = judge(body, profile)
                assertEquals("$name as ${profile.id}", CensusClass.REFUSED, verdict.answerClass)
                assertFalse("$name as ${profile.id}", verdict.terminal)
                assertNull(verdict.shapeKind)
            }
        }
    }

    @Test
    fun `a definite marker keeps an error screen a terminal verdict about the video`() {
        val answers = listOf(
            refusal("ERROR", "This video has been removed by the uploader"),
            refusal(
                "ERROR",
                "This video is no longer available because the YouTube account associated with this video has been terminated.",
            ),
            refusal("UNPLAYABLE", "Join this channel to get access to members-only content like this video, and other exclusive perks."),
            refusal("UNPLAYABLE", "This video is no longer available due to a copyright claim by Fixture Rights"),
            refusal("UNPLAYABLE", "Private video", subreason = "This video is private"),
            refusal("UNPLAYABLE", "Sign in to watch this video"),
            refusal("LOGIN_REQUIRED", "Sign in to watch this video"),
            refusal("UNPLAYABLE", "Video unavailable", subreason = "The uploader has not made this video available in your country"),
            refusal("UNPLAYABLE", "This live event will begin in a few moments."),
        )
        for ((index, body) in answers.withIndex()) {
            for (profile in listOf(VISION_OS, EMBEDDED, MWEB)) {
                val verdict = judge(body, profile)
                assertEquals("answer $index as ${profile.id}", CensusClass.VIDEO_VERDICT, verdict.answerClass)
                assertTrue("answer $index as ${profile.id}", verdict.terminal)
            }
        }
        // An age check stays AGE_CHECK, however it is worded.
        for (body in listOf(fixture("player_age_gate.json"), refusal("AGE_CHECK_REQUIRED", "Sign in to confirm your age"))) {
            val verdict = judge(body)
            assertEquals(CensusClass.AGE_CHECK, verdict.answerClass)
            assertTrue(verdict.terminal)
        }
    }

    @Test
    fun `YouTube asking for a newer app is VERSION_REJECTED and its bot check is BOT_CHECK under any status`() {
        for (status in listOf("UNPLAYABLE", "ERROR", "LOGIN_REQUIRED")) {
            assertEquals(
                status,
                CensusClass.VERSION_REJECTED,
                judge(refusal(status, "Please update to the latest version of the app to watch this video.")).answerClass,
            )
            for (apostrophe in listOf("'", "\u2019")) {
                val verdict = judge(refusal(status, "Sign in to confirm you${apostrophe}re not a bot", subreason = "This helps protect our community."))
                assertEquals(status, CensusClass.BOT_CHECK, verdict.answerClass)
                assertFalse(verdict.terminal)
            }
        }
        assertEquals(CensusClass.VERSION_REJECTED, judge(refusal("UNPLAYABLE", "This app version is no longer supported")).answerClass)
    }

    @Test
    fun `HTTP failures - 400 is the version, anything else a refusal of this request`() {
        val cases = listOf(
            ExtractorHttpResult.Failure(SiteExtractionFailure.HTTP_STATUS, 400) to CensusClass.VERSION_REJECTED,
            ExtractorHttpResult.Failure(SiteExtractionFailure.HTTP_STATUS, 403) to CensusClass.REFUSED,
            ExtractorHttpResult.Failure(SiteExtractionFailure.HTTP_STATUS, 500) to CensusClass.REFUSED,
            ExtractorHttpResult.Failure(SiteExtractionFailure.NETWORK) to CensusClass.REFUSED,
            ExtractorHttpResult.Failure(SiteExtractionFailure.RESPONSE_TOO_LARGE) to CensusClass.REFUSED,
        )
        for ((failure, expected) in cases) {
            val verdict = AnswerJudge.ofFailure(failure)
            assertEquals("$failure", expected, verdict.answerClass)
            assertFalse(verdict.terminal)
            assertNull(verdict.hls)
            assertNull(verdict.shapeKind)
            assertNull(verdict.video)
        }
    }

    @Test
    fun `after V a 403 on the checked row means a token, anything else keeps the class`() {
        assertEquals(CensusClass.TOKEN_NEEDED, AnswerJudge.afterV(CensusClass.DIRECT, targetForbidden = true))
        assertEquals(CensusClass.TOKEN_NEEDED, AnswerJudge.afterV(CensusClass.NEEDS_SCRIPT, targetForbidden = true))
        for (answerClass in CensusClass.entries) {
            assertEquals(answerClass, AnswerJudge.afterV(answerClass, targetForbidden = false))
        }
    }

    @Test
    fun `the HLS address is taken only on a googlevideo host over HTTPS`() {
        val base = fixture("player_visionos.json")
        // The fixture's own address sits on a test host, not on googlevideo.com.
        assertNull(judge(base).hls)
        assertEquals(CensusClass.DIRECT, judge(base).answerClass)
        val good = "https://manifest.googlevideo.com/api/manifest/hls_variant/id/fixture/file/index.m3u8"
        val withHls = { address: String -> base.replace(Regex("\"hlsManifestUrl\": *\"[^\"]*\""), "\"hlsManifestUrl\": \"$address\"") }
        assertEquals(good, judge(withHls(good)).hls)
        for (other in listOf(
            "https://manifest.example-cdn.test/api/manifest/hls_variant/id/fixture/file/index.m3u8",
            "http://manifest.googlevideo.com/api/manifest/hls_variant/id/fixture/file/index.m3u8",
            "https://googlevideo.com.example-cdn.test/api/manifest/hls_variant/id/fixture/file/index.m3u8",
        )) {
            assertNull(other, judge(withHls(other)).hls)
        }
        // The answer's own field on another host is not replaced by an address located elsewhere.
        val elsewhere = withHls("https://manifest.example-cdn.test/api/manifest/hls_variant/id/fixture/file/index.m3u8")
            .replaceFirst("\"videoDetails\"", "\"playback\":{\"hls\":{\"manifestUrl\":\"$good\"}},\"videoDetails\"")
        assertTrue(elsewhere.contains(good))
        assertNull(judge(elsewhere).hls)
        // With no field, the one located address on a googlevideo host is taken (L13).
        val located = base.replace(Regex("\"hlsManifestUrl\": *\"[^\"]*\",?"), "")
            .replaceFirst("\"videoDetails\"", "\"playback\":{\"hls\":{\"manifestUrl\":\"$good\"}},\"videoDetails\"")
        assertEquals(good, judge(located).hls)
    }

    @Test
    fun `an OK answer without streaming data is a refusal, not a verdict`() {
        val verdict = judge(
            "{\"playabilityStatus\":{\"status\":\"OK\"},\"videoDetails\":{\"videoId\":\"$VIDEO\",\"title\":\"Fixture\",\"lengthSeconds\":\"212\"}}",
        )
        assertEquals(CensusClass.REFUSED, verdict.answerClass)
        assertFalse(verdict.terminal)
    }

    @Test
    fun `reasonText keeps the words, drops markup and stays bounded`() {
        fun status(json: String) = BoundedJsonParser.parse(json)!!["playabilityStatus"]
        val marked = status(
            "{\"playabilityStatus\":{\"status\":\"ERROR\",\"reason\":\"<a href=\\\"https://www.youtube.com/watch?v=$VIDEO\\\">Watch on YouTube</a>  now\"," +
                "\"messages\":[\"line one\",\"line\\ntwo\"],\"other\":\"not a text key\"}}",
        )
        assertEquals("Watch on YouTube now line one line two", AnswerJudge.reasonText(marked))
        val long = status("{\"playabilityStatus\":{\"reason\":\"${"word ".repeat(1_000)}\"}}")
        assertTrue(AnswerJudge.reasonText(long).length <= 2_000)
        val many = status("{\"playabilityStatus\":{\"messages\":[${(1..50).joinToString(",") { "\"part$it\"" }}]}}")
        val parts = AnswerJudge.reasonText(many).split(' ')
        assertEquals(32, parts.size)
        assertEquals("part1", parts.first())
        val deep = status("{\"playabilityStatus\":${"{\"a\":".repeat(12)}{\"reason\":\"too deep\"}${"}".repeat(12)}}")
        assertEquals("", AnswerJudge.reasonText(deep))
        assertEquals("", AnswerJudge.reasonText(null))
    }

    private class Case(val file: String, val profile: YouTubeClientProfile, val answerClass: CensusClass)

    private fun assertShape(id: String, kind: AnswerJudge.ShapeKind, verdict: AnswerJudge.Verdict) {
        assertEquals(id, CensusClass.SHAPE_CHANGED, verdict.answerClass)
        assertEquals(id, kind, verdict.shapeKind)
        assertFalse(id, verdict.terminal)
    }

    private fun assertTerminal(id: String, answerClass: CensusClass, verdict: AnswerJudge.Verdict) {
        assertEquals(id, answerClass, verdict.answerClass)
        assertTrue(id, verdict.terminal)
        assertNull(id, verdict.shapeKind)
    }

    private fun judge(body: String, profile: YouTubeClientProfile = VISION_OS): AnswerJudge.Verdict =
        AnswerJudge.judge(profile, body, VIDEO, Sim.NOW)

    private fun fixture(name: String): String = Fixtures.read("youtube/$name")

    /** [body] with a size on every format that has none. */
    private fun withSizes(body: String): String {
        val json = SimJson.obj(SimJson.parse(body))
        for (list in listOf("formats", "adaptiveFormats")) {
            for (format in (SimJson.at(json, "streamingData", list) as? List<*>).orEmpty()) {
                SimJson.obj(format).putIfAbsent("contentLength", "1000000")
            }
        }
        return SimJson.write(json)
    }

    /** A refusal: [status] with [reason], an error screen's [subreason] and [button], and the video's details. */
    private fun refusal(status: String, reason: String, subreason: String? = null, button: String? = null): String {
        val screen = listOfNotNull(
            subreason?.let { "\"subreason\":{\"runs\":[{\"text\":${CensusJson.quote(it)}}]}" },
            button?.let { "\"proceedButton\":{\"buttonRenderer\":{\"text\":{\"simpleText\":${CensusJson.quote(it)}}}}" },
        ).takeIf { it.isNotEmpty() }?.joinToString(",", ",\"errorScreen\":{\"playerErrorMessageRenderer\":{", "}}").orEmpty()
        return "{\"playabilityStatus\":{\"status\":${CensusJson.quote(status)},\"reason\":${CensusJson.quote(reason)}$screen}," +
            "\"videoDetails\":{\"videoId\":\"$VIDEO\",\"title\":\"Fixture video title\",\"lengthSeconds\":\"212\"}}"
    }

    private companion object {
        const val VIDEO = Sim.VIDEO_ID

        val VISION_OS = YouTubeClientProfiles.VISION_OS
        val ANDROID = YouTubeClientProfiles.ANDROID
        val MWEB = YouTubeClientProfiles.mobileWeb()
        val EMBEDDED = YouTubeClientProfiles.embedded(
            YouTubePageSignals(null, null, null, null, null, null, null),
        )

        /** Video 6 (made for kids) as visionOS answered it in valid runs 1–3: no formats, the video's details. */
        val VIDEO_6_LIKE = "{\"responseContext\":{},\"playabilityStatus\":{\"status\":\"UNPLAYABLE\"," +
            "\"reason\":\"This video is not available\",\"playableInEmbed\":true}," +
            "\"videoDetails\":{\"videoId\":\"$VIDEO\",\"title\":\"Fixture video title\",\"lengthSeconds\":\"212\"," +
            "\"author\":\"Fixture Channel\",\"isLiveContent\":false}}"

        val MOVED_CLASSES = setOf("L1", "L2", "L7", "L8", "L9", "L10", "L15", "L16")
        val PARTIAL_CLASSES = setOf("L3", "L4", "L5", "L6")
    }
}
