package com.alal.yft.extractor.master.modules.youtube.ladder

import com.alal.yft.extractor.api.ExtractorHttpResult
import com.alal.yft.extractor.api.ExtractorProbeResult
import com.alal.yft.extractor.api.SiteExtractionFailure
import com.alal.yft.extractor.master.modules.youtube.StreamCheck
import com.alal.yft.extractor.master.modules.youtube.YouTubeParseResult
import com.alal.yft.extractor.master.modules.youtube.YouTubePlayerResponseParser
import com.alal.yft.extractor.master.modules.youtube.YouTubeStream
import com.alal.yft.extractor.master.modules.youtube.YouTubeVideo
import com.alal.yft.extractor.master.modules.youtube.ladder.LShapeCases.Expect
import com.alal.yft.extractor.master.modules.youtube.ladder.ShapeNormalizer.None
import com.alal.yft.extractor.master.modules.youtube.ladder.ShapeNormalizer.Rows
import com.alal.yft.extractor.master.modules.youtube.ladder.ShapeNormalizer.Stop
import com.alal.yft.extractor.master.modules.youtube.ladder.ShapeNormalizer.Why
import java.net.URLEncoder
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/**
 * Phase 1.3 D5b: rung L (ShapeLocator + ShapeNormalizer + LRows) on the D1 simulator's answers and
 * on small answers made here; offline, no live calls. V in the simulator: the world's probe
 * (2xx pass, 403 fail, no answer unknown) and the init check on the first bytes it serves.
 * Test substitution: the world's WebM stand-in is a cut EBML header that the init check reads as
 * `unknown`, so WebM files are read as real WebM starts (with the file's encryption) instead.
 */
class ShapeNormalizerTest {
    private val vision = SimAsk(SimClient.VISION_OS, Sim.BASE_VERSION, visitor = false, sts = false)

    private fun answerOf(world: SimWorld): String = SimJson.write(world.answer(vision))

    private class WorldCheck(private val world: SimWorld) : LRowCheck {
        override suspend fun check(row: YouTubeStream): StreamCheck.Verdict {
            val url = row.url ?: return StreamCheck.Verdict.UNKNOWN
            val total = when (val probe = world.http.probe(url, emptyMap(), PROBE_MILLIS)) {
                is ExtractorProbeResult.Answered -> probe.totalBytes
                is ExtractorProbeResult.Refused ->
                    return if (probe.statusCode == FORBIDDEN) StreamCheck.Verdict.FAIL else StreamCheck.Verdict.UNKNOWN
                ExtractorProbeResult.Unsupported -> return StreamCheck.Verdict.UNKNOWN
            }
            val read = world.http.get(url, mapOf("Range" to "bytes=0-4095"), READ_BYTES) as? ExtractorHttpResult.Success
                ?: return StreamCheck.Verdict.UNKNOWN
            val bytes = if (row.mimeType?.endsWith("/webm") == true) {
                val encrypted = world.media[Sim.query(url)["yftsim"]]?.encrypted == true
                InitBytes.webm(codec = if (row.hasVideo) "V_VP9" else "A_OPUS", encodings = if (encrypted) WEBM_ENCRYPTION else null)
            } else {
                read.body.toByteArray(Charsets.ISO_8859_1)
            }
            return InitCheck.check(row.mimeType, row.contentLengthBytes, InitCheck.Observed(read.contentType, bytes, total)).verdict
        }
    }

    private class Reference(val video: YouTubeVideo, val offer: List<YouTubeStream>, val kept: Set<Key>, val complete: Boolean)

    private data class Key(val itag: Int, val url: String?)

    private fun key(row: YouTubeStream) = Key(row.itag, row.url)

    private suspend fun reference(base: String): Reference {
        val world = SimWorld(base)
        val video = (YouTubePlayerResponseParser.parse(answerOf(world), Sim.NOW) as YouTubeParseResult.Success).video
        val offer = ShapeNormalizer.offerSet(video)
        val checked = LRows.check(offer, WorldCheck(world))
        return Reference(video, offer, checked.rows.mapTo(HashSet(), ::key), checked.complete)
    }

    private val order = compareBy<YouTubeStream>({ it.itag }, { it.url }, { it.protectedDescriptor }, { it.bitrate })

    private fun sorted(video: YouTubeVideo) = video.copy(progressive = video.progressive.sortedWith(order), adaptive = video.adaptive.sortedWith(order))

    @Test
    fun `L round-trip - canonical JSON of each unchanged base gives the direct parse's rows`() = runTest {
        for (base in Sim.BASES) {
            val world = SimWorld(base)
            val text = answerOf(world)
            val direct = (YouTubePlayerResponseParser.parse(text, Sim.NOW) as YouTubeParseResult.Success).video
            val rows = ShapeNormalizer.normalize(text, Sim.VIDEO_ID, Sim.NOW) as? Rows ?: fail("$base: no rows")
            rows as Rows
            assertEquals(base, direct, rows.video)
            assertEquals(base, direct, (YouTubePlayerResponseParser.parse(rows.canonicalJson, Sim.NOW) as YouTubeParseResult.Success).video)
            assertEquals(base, ShapeNormalizer.offerSet(direct), rows.offer)
            assertEquals(base, world.lengthSeconds, rows.lengthSeconds)
            assertEquals(base, world.hls.masterUrl, rows.hls)
            assertEquals(base, setOf("streamingData.adaptiveFormats[]") + (if (direct.progressive.isEmpty()) emptySet() else setOf("streamingData.formats[]")), rows.paths)
            val checked = LRows.check(rows.offer, WorldCheck(world))
            assertTrue(base, checked.complete)
            assertEquals(base, rows.offer.size, checked.rows.size)
        }
    }

    @Test
    fun `L matrix - L1 to L16 x 5 bases x V pass, fail and unknown - never a wrong row`() = runTest {
        var wrong = 0
        var runs = 0
        val table = LinkedHashMap<String, IntArray>() // case -> runs, L rows shown, complete
        for (base in Sim.BASES) {
            val ref = reference(base)
            assertTrue(base, ref.complete)
            for (case in LShapeCases.ALL) {
                for (mode in VMode.entries) {
                    val world = SimWorld(base, mode).apply(case.setup)
                    val text = answerOf(world)
                    val label = "$base ${case.name} $mode"
                    val outcome = ShapeNormalizer.normalize(text, Sim.VIDEO_ID, Sim.NOW)
                    val cell = table.getOrPut(case.name) { IntArray(3) }
                    runs += 1
                    cell[0] += 1
                    when (case.expect) {
                        Expect.ROWS -> {
                            if (outcome !is Rows) fail("$label: $outcome")
                            outcome as Rows
                            assertEquals(label, sorted(ref.video), sorted(outcome.video))
                            assertEquals(label, ref.offer.mapTo(HashSet(), ::key), outcome.offer.mapTo(HashSet(), ::key))
                            assertEquals(label, world.lengthSeconds, outcome.lengthSeconds)
                            val checked = LRows.check(outcome.offer, WorldCheck(world))
                            assertTrue(label, checked.checks <= ShapeNormalizer.MAX_OFFER)
                            wrong += checked.rows.count { world.truth(it.url) != null }
                            if (mode == VMode.PASS || case.readableByR0) {
                                assertEquals(label, ref.kept, checked.rows.mapTo(HashSet(), ::key))
                                assertEquals(label, ref.complete, checked.complete)
                            } else {
                                assertFalse(label, checked.complete)
                            }
                            cell[1] += checked.rows.size
                            if (checked.complete) cell[2] += 1
                        }

                        Expect.PROTECTED -> {
                            if (outcome !is Rows) fail("$label: $outcome")
                            outcome as Rows
                            assertTrue(label, outcome.offer.isNotEmpty() && outcome.offer.all { it.url == null && it.protectedDescriptor != null })
                            assertEquals(label, ref.offer.mapTo(HashSet()) { it.itag }, outcome.offer.mapTo(HashSet()) { it.itag })
                            val checked = LRows.check(outcome.offer, WorldCheck(world))
                            assertEquals(label, 0, checked.checks)
                            assertTrue(label, checked.rows.isEmpty() && !checked.complete)
                        }

                        Expect.STOP -> {
                            if (outcome !is Stop) fail("$label: $outcome")
                            outcome as Stop
                            val own = YouTubePlayerResponseParser.parse(text, Sim.NOW)
                            if (case.name == "L12") {
                                assertEquals(label, SiteExtractionFailure.DRM_PROTECTED, outcome.failure.reason)
                                assertTrue(label, outcome.failure.definite)
                            } else {
                                assertEquals(label, own, outcome.failure)
                            }
                        }

                        Expect.AMBIGUOUS -> assertEquals(label, Why.AMBIGUOUS_GROUPS, (outcome as? None)?.why)

                        Expect.HLS_ONLY -> {
                            assertEquals(label, Why.NO_FORMATS, (outcome as? None)?.why)
                            assertEquals(label, world.hls.masterUrl, outcome.hls)
                            assertEquals(label, world.lengthSeconds, outcome.lengthSeconds)
                        }
                    }
                }
            }
        }
        println("L matrix (5 bases x V pass/fail/unknown): " + table.entries.joinToString("; ") { (name, cell) -> "$name runs ${cell[0]} rows ${cell[1]} complete ${cell[2]}" })
        assertEquals(0, wrong)
        assertEquals(Sim.BASES.size * LShapeCases.ALL.size * VMode.entries.size, runs)
        // Complete with V pass on every base (fail / unknown too for L6, whose files R0 reads).
        for (case in LShapeCases.ALL.filter { it.expect == Expect.ROWS }) {
            val expected = Sim.BASES.size * if (case.readableByR0) 3 else 1
            assertEquals(case.name, expected, table.getValue(case.name)[2])
        }
    }

    @Test
    fun `a 300 KB answer normalizes within 20 ms on the JVM`() {
        val world = SimWorld("player_4k.json")
        world.edits += { _, json ->
            LShapeCases.deepMove(json)
            json["noise"] = LShapeCases.noise(TIMING_NOISE)
        }
        val text = answerOf(world)
        assertTrue(text.length.toString(), text.length in 300_000..400_000)
        repeat(WARM_UP) { assertTrue(ShapeNormalizer.normalize(text, Sim.VIDEO_ID, Sim.NOW) is Rows) }
        val millis = (1..RUNS).map {
            val start = System.nanoTime()
            val outcome = ShapeNormalizer.normalize(text, Sim.VIDEO_ID, Sim.NOW)
            val took = (System.nanoTime() - start) / 1_000_000.0
            assertTrue(outcome is Rows)
            took
        }.sorted()
        val median = millis[millis.size / 2]
        println("L time for a ${text.length / 1024} KiB answer: median ${"%.2f".format(median)} ms")
        assertTrue("median $median ms", median <= 20.0)
    }

    // --- rules, on small answers made here ---

    @Test
    fun `playability - the answer's own verdict, else exactly one located status`() {
        val private = answer(status("LOGIN_REQUIRED", "This video is private"), details(), moved(avc(), aac()))
        assertEquals(YouTubePlayerResponseParser.parse(private, Sim.NOW), (ShapeNormalizer.normalize(private, VIDEO, Sim.NOW) as Stop).failure)

        val movedUnplayable = answer(details(), "playback" to linkedMapOf("status" to linkedMapOf("status" to "UNPLAYABLE", "reason" to "Video unavailable"), "streamingData" to streaming(avc(), aac())))
        val stop = ShapeNormalizer.normalize(movedUnplayable, VIDEO, Sim.NOW) as Stop
        assertEquals(SiteExtractionFailure.PRIVATE_OR_UNAVAILABLE, stop.failure.reason)
        assertTrue(stop.failure.definite)

        // The status word under another key name still stops, with its reason (an age check).
        val renamedAge = answer(details(), "state" to linkedMapOf("value" to "LOGIN_REQUIRED", "reason" to "Sign in to confirm your age"), moved(avc(), aac()))
        val age = ShapeNormalizer.normalize(renamedAge, VIDEO, Sim.NOW) as Stop
        assertTrue(age.failure.ageCheck)

        assertEquals(Why.NO_STATUS, (ShapeNormalizer.normalize(answer(details(), moved(avc(), aac())), VIDEO, Sim.NOW) as None).why)
        val twoOk = answer(details(), "a" to linkedMapOf("status" to "OK"), "b" to linkedMapOf("state" to "OK"), moved(avc(), aac()))
        assertEquals(Why.AMBIGUOUS_STATUS, (ShapeNormalizer.normalize(twoOk, VIDEO, Sim.NOW) as None).why)
        val okAndLogin = answer(details(), "a" to linkedMapOf("status" to "OK"), "b" to linkedMapOf("status" to "LOGIN_REQUIRED"), moved(avc(), aac()))
        assertTrue(ShapeNormalizer.normalize(okAndLogin, VIDEO, Sim.NOW) is Stop)
        val oneOk = answer(details(), "a" to linkedMapOf("status" to "OK"), moved(avc(), aac()))
        assertTrue(ShapeNormalizer.normalize(oneOk, VIDEO, Sim.NOW) is Rows)
    }

    @Test
    fun `binding - one video length, durations within 1_5 s, one parent`() {
        fun rows(vararg top: Pair<String, Any?>) = ShapeNormalizer.normalize(answer(ok(), *top), VIDEO, Sim.NOW)
        fun why(vararg top: Pair<String, Any?>) = (rows(*top) as? None)?.why

        assertTrue(rows(details(), moved(avc(dur = "213.500"), aac(dur = "210.500"))) is Rows)
        assertEquals(Why.NO_FORMATS, why(details(), moved(avc(dur = "213.501"), aac(dur = "210.499"))))
        // Without dur: approxDurationMs, else an integer field of milliseconds; none → dropped.
        val viaField = rows(details(), moved(avc("approxDurationMs" to "212400", dur = null), aac("x" to 211_000, dur = null)))
        assertEquals(2, (viaField as Rows).located)
        assertEquals(Why.NO_FORMATS, why(details(), moved(avc(dur = null), aac(dur = null))))
        // Another video's formats do not bind; another video's details are not a video object.
        val decoy = "endScreen" to linkedMapOf("videoId" to "Other000000", "lengthSeconds" to "30", "list" to listOf(avc(dur = "30.000", host = DECOY_HOST)))
        val bound = rows(details(), moved(avc(), aac()), decoy) as Rows
        assertTrue(bound.video.adaptive.none { it.url!!.contains(DECOY_HOST) })
        assertEquals(Why.NO_VIDEO, why(details(id = "Other000000"), moved(avc(), aac())))
        // Copies of the video object must agree (a microformat-like copy is fine).
        val copy = "microformat" to linkedMapOf("externalVideoId" to VIDEO, "lengthSeconds" to "212")
        assertTrue(rows(details(), copy, moved(avc(), aac())) is Rows)
        val disagree = "microformat" to linkedMapOf("externalVideoId" to VIDEO, "lengthSeconds" to "213")
        assertEquals(Why.AMBIGUOUS_VIDEO, why(details(), disagree, moved(avc(), aac())))
        // Two parents that both bind → ambiguous; the same formats twice → one.
        val second = "related" to linkedMapOf("list" to listOf(avc(itag = 136, host = DECOY_HOST)))
        assertEquals(Why.AMBIGUOUS_GROUPS, why(details(), moved(avc(), aac()), second))
        assertTrue(rows(details(), moved(avc(), aac()), "copy" to linkedMapOf("list" to listOf(avc(), aac()))) is Rows)
        // Lists and maps under one parent are one group.
        val split = "playback" to linkedMapOf("data" to linkedMapOf("video" to listOf(avc()), "audio" to linkedMapOf("140" to aac())))
        assertEquals(2, (rows(details(), split) as Rows).located)
    }

    @Test
    fun `DRM markers and live videos stop as the copied parser does`() {
        val drmFamilies = ShapeNormalizer.normalize(answer(ok(), details(), moved(avc("drmFamilies" to listOf("WIDEVINE")), aac())), VIDEO, Sim.NOW) as Stop
        assertEquals(SiteExtractionFailure.DRM_PROTECTED, drmFamilies.failure.reason)
        val drmParams = answer(ok(), details(), "playback" to linkedMapOf("streamingData" to linkedMapOf("drmParams" to "x", "list" to listOf(avc(), aac()))))
        assertEquals(SiteExtractionFailure.DRM_PROTECTED, (ShapeNormalizer.normalize(drmParams, VIDEO, Sim.NOW) as Stop).failure.reason)
        val emptyFamilies = answer(ok(), details(), moved(avc("drmFamilies" to emptyList<String>()), aac()))
        assertTrue(ShapeNormalizer.normalize(emptyFamilies, VIDEO, Sim.NOW) is Rows)
        val live = answer(ok(), details("isLive" to true), moved(avc(), aac()))
        val stop = ShapeNormalizer.normalize(live, VIDEO, Sim.NOW) as Stop
        assertEquals(SiteExtractionFailure.NO_MEDIA_FOUND, stop.failure.reason)
        assertTrue(stop.failure.definite)
    }

    @Test
    fun `fields - required ones by value, the rest by canonical name with types normalized`() {
        val format = linkedMapOf<String, Any?>(
            "id" to "137",
            "address" to media(137, AVC_MIME),
            "kind" to AVC_MIME,
            "width" to "1920",
            "height" to SimJson.Num("1080"),
            "bitrate" to "4400000",
            "contentLength" to SimJson.Num("88000000"),
            "fps" to "30",
            "label" to "1080p60",
        )
        val audio = aac("sampleRate" to "44100", "audioSampleRate" to SimJson.Num("48000"), "isDrc" to "false", "audioTrack" to linkedMapOf("audioIsDefault" to "true"), "q" to "AUDIO_QUALITY_MEDIUM")
        val rows = ShapeNormalizer.normalize(answer(ok(), details(), moved(format, audio)), VIDEO, Sim.NOW) as Rows
        val video = rows.video.adaptive.single { it.itag == 137 }
        assertEquals(1920, video.width)
        assertEquals(1080, video.height)
        assertEquals(4_400_000L, video.bitrate)
        assertEquals(88_000_000L, video.contentLengthBytes)
        assertEquals(30, video.fps)
        assertEquals("1080p60", video.qualityLabel)
        assertEquals(listOf("avc1.640028"), video.codecs)
        val sound = rows.video.adaptive.single { it.itag == 140 }
        assertTrue(sound.hasAudio)
        assertEquals(true, sound.isDefaultAudio)
        assertFalse(sound.isDrc)
        assertTrue(rows.canonicalJson.contains("\"audioSampleRate\":\"48000\""))
        assertTrue(rows.canonicalJson.contains("\"audioQuality\":\"AUDIO_QUALITY_MEDIUM\""))
        // Muxed codecs go to formats (progressive), the rest to adaptiveFormats.
        val muxed = format(18, "video/mp4; codecs=\"avc1.42001E, mp4a.40.2\"", "height" to 360)
        val withFile = ShapeNormalizer.normalize(answer(ok(), details(), moved(muxed, format, audio)), VIDEO, Sim.NOW) as Rows
        assertEquals(listOf(18), withFile.video.progressive.map { it.itag })
        assertEquals(listOf(137, 140), withFile.video.adaptive.map { it.itag })
    }

    @Test
    fun `the offer set mirrors the app's selection and holds at most 8 rows`() {
        fun row(itag: Int, mime: String, codec: String, height: Int?, bitrate: Long, video: Boolean, audio: Boolean) = YouTubeStream(
            itag = itag, url = "https://rr1---sn-test.googlevideo.com/videoplayback?itag=$itag", protectedDescriptor = null,
            mimeType = mime, codecs = listOf(codec), qualityLabel = height?.let { "${it}p" }, bitrate = bitrate, width = null,
            height = height, contentLengthBytes = 1_000L, hasVideo = video, hasAudio = audio, isDefaultAudio = null, isDrc = false,
        )
        val avc = listOf(144, 240, 360, 480, 720, 1080).mapIndexed { index, height -> row(160 + index, "video/mp4", "avc1.4d401e", height, 100L + index, true, false) }
        val vp9 = listOf(1440, 2160).mapIndexed { index, height -> row(271 + index, "video/webm", "vp9", height, 9_000L + index, true, false) }
        val aac = row(140, "audio/mp4", "mp4a.40.2", null, 128, false, true)
        val opus = row(251, "audio/webm", "opus", null, 160, false, true)
        val video = YouTubeVideo(VIDEO, "t", "a", 212_000L, null, emptyList(), avc + vp9 + listOf(aac, opus), null)
        val offer = ShapeNormalizer.offerSet(video)
        assertEquals(listOf(2160, 1440, 1080, 720, 480, 360, null, null), offer.map { it.height })
        assertEquals(listOf(272, 271, 165, 164, 163, 162, 140, 251), offer.map { it.itag })

        val file = YouTubeStream(18, "https://rr1---sn-test.googlevideo.com/videoplayback?itag=18", null, "video/mp4", listOf("avc1.42001E", "mp4a.40.2"), "360p", 500, 640, 360, null, true, true, null, false)
        val small = YouTubeVideo(VIDEO, "t", "a", 212_000L, null, listOf(file), listOf(avc[5], aac), null)
        assertEquals(listOf(18, 165, 140), ShapeNormalizer.offerSet(small).map { it.itag })
    }

    @Test
    fun `L rows - companions first, a merged row only after its companion passes, unknown is never pass`() = runTest {
        fun stream(itag: Int, mime: String, video: Boolean, audio: Boolean, url: Boolean = true) = YouTubeStream(
            itag, if (url) "https://rr1---sn-test.googlevideo.com/videoplayback?itag=$itag" else null, if (url) null else "s=x&url=y",
            mime, listOf("c"), null, 1, null, null, 1, video, audio, null, false,
        )
        val file = stream(18, "video/mp4", video = true, audio = true)
        val mp4 = stream(137, "video/mp4", video = true, audio = false)
        val webm = stream(271, "video/webm", video = true, audio = false)
        val aac = stream(140, "audio/mp4", video = false, audio = true)
        val opus = stream(251, "audio/webm", video = false, audio = true)
        val offer = listOf(file, mp4, webm, aac, opus)

        suspend fun run(verdicts: Map<Int, StreamCheck.Verdict>, rows: List<YouTubeStream> = offer): Pair<LRows.Checked, List<Int>> {
            val asked = mutableListOf<Int>()
            val checked = LRows.check(rows) { row ->
                asked += row.itag
                verdicts[row.itag] ?: error("no V")
            }
            return checked to asked
        }
        val pass = StreamCheck.Verdict.PASS
        val all = run(offer.associate { it.itag to pass })
        assertEquals(listOf(140, 251, 137, 271, 18), all.second)
        assertEquals(offer, all.first.rows)
        assertTrue(all.first.complete)

        val noAac = run(mapOf(140 to StreamCheck.Verdict.FAIL, 251 to pass, 271 to pass, 18 to pass))
        assertEquals(listOf(140, 251, 271, 18), noAac.second)
        assertEquals(listOf(file, webm, opus), noAac.first.rows)
        assertTrue(noAac.first.complete)
        assertEquals(1, noAac.first.skipped)

        val unknown = run(mapOf(140 to StreamCheck.Verdict.UNKNOWN, 251 to StreamCheck.Verdict.FAIL, 18 to StreamCheck.Verdict.UNKNOWN))
        assertTrue(unknown.first.rows.isEmpty())
        assertFalse(unknown.first.complete)
        assertEquals(2, unknown.first.unknown)

        // A merged row that fails leaves the audio, not complete; a file alone is not complete.
        val videoFails = run(mapOf(140 to pass, 251 to pass, 137 to StreamCheck.Verdict.FAIL, 271 to StreamCheck.Verdict.UNKNOWN, 18 to pass))
        assertEquals(listOf(file, aac, opus), videoFails.first.rows)
        assertFalse(videoFails.first.complete)

        // Protected rows are S's: not checked, not shown. A check that throws is unknown.
        val guarded = run(mapOf(140 to pass), listOf(stream(137, "video/mp4", true, false, url = false), aac))
        assertEquals(listOf(140), guarded.second)
        assertEquals(1, guarded.first.protectedRows)
        val throwing = LRows.check(listOf(aac)) { error("boom") }
        assertTrue(throwing.rows.isEmpty())
        assertEquals(1, throwing.unknown)

        // Never more than 8 checks.
        val many = (1..12).map { stream(100 + it, "video/mp4", video = true, audio = true) }
        assertEquals(ShapeNormalizer.MAX_OFFER, run(many.associate { it.itag to pass }, many).second.size)
    }

    @Test
    fun `outcomes and scans print no address, cipher or video ID`() = runTest {
        val world = SimWorld("player_visionos.json").apply(LShapeCases.ALL.first { it.name == "L1" }.setup)
        val text = answerOf(world)
        val rows = ShapeNormalizer.normalize(text, Sim.VIDEO_ID, Sim.NOW) as Rows
        val checked = LRows.check(rows.offer, WorldCheck(world))
        val scan = ShapeLocator.scan(ShapeLocator.parse(text)!!, Sim.VIDEO_ID, text)
        val printed = listOf(rows, checked, scan, ShapeNormalizer.normalize(answer(ok(), details(id = "Other000000"), moved(avc())), VIDEO, Sim.NOW)) +
            scan.candidates + scan.videos + scan.statuses
        for (item in printed) {
            val text2 = item.toString()
            assertFalse(text2, text2.contains("googlevideo") || text2.contains(Sim.VIDEO_ID) || text2.contains(VIDEO) || text2.contains("http"))
        }
        assertNull(ShapeNormalizer.normalize("{", VIDEO, Sim.NOW).hls)
        assertEquals(Why.UNREADABLE, (ShapeNormalizer.normalize("{", VIDEO, Sim.NOW) as None).why)
        assertEquals(Why.TOO_LARGE, (ShapeNormalizer.normalize(" ".repeat(ShapeLocator.MAX_ANSWER_CHARS + 1), VIDEO, Sim.NOW) as None).why)
    }

    // --- small answers ---

    private fun media(itag: Int, mime: String, dur: String? = "212.000", host: String = HOST): String =
        "https://$host/videoplayback?expire=4102444800&itag=$itag&mime=" + URLEncoder.encode(mime.substringBefore(';'), Charsets.UTF_8) +
            (dur?.let { "&dur=$it" } ?: "")

    private fun format(itag: Int, mime: String, vararg extra: Pair<String, Any?>, dur: String? = "212.000", host: String = HOST): MutableMap<String, Any?> =
        linkedMapOf<String, Any?>("itag" to itag, "url" to media(itag, mime, dur, host), "mimeType" to mime).apply { putAll(extra) }

    private fun avc(vararg extra: Pair<String, Any?>, itag: Int = 137, dur: String? = "212.000", host: String = HOST) =
        format(itag, AVC_MIME, "height" to 1080, "qualityLabel" to "1080p", *extra, dur = dur, host = host)

    private fun aac(vararg extra: Pair<String, Any?>, dur: String? = "212.000") =
        format(140, AAC_MIME, "audioQuality" to "AUDIO_QUALITY_MEDIUM", *extra, dur = dur)

    private fun streaming(vararg formats: Map<String, Any?>) = linkedMapOf<String, Any?>("expiresInSeconds" to "21540", "adaptiveFormats" to formats.toList())

    private fun moved(vararg formats: Map<String, Any?>) = "playback" to linkedMapOf<String, Any?>("streamingData" to streaming(*formats))

    private fun ok() = "playabilityStatus" to linkedMapOf<String, Any?>("status" to "OK")

    private fun status(value: String, reason: String) = "playabilityStatus" to linkedMapOf<String, Any?>("status" to value, "reason" to reason)

    private fun details(vararg extra: Pair<String, Any?>, id: String = VIDEO, length: Any? = "212") =
        "videoDetails" to linkedMapOf<String, Any?>("videoId" to id, "title" to "Title", "lengthSeconds" to length, "author" to "Author").apply { putAll(extra) }

    private fun answer(vararg top: Pair<String, Any?>): String = SimJson.write(linkedMapOf(*top))

    private companion object {
        const val VIDEO = "Yft0Shape01"
        const val HOST = "rr1---sn-shape.googlevideo.com"
        const val DECOY_HOST = "rr9---sn-decoy.googlevideo.com"
        const val AVC_MIME = "video/mp4; codecs=\"avc1.640028\""
        const val AAC_MIME = "audio/mp4; codecs=\"mp4a.40.2\""
        const val FORBIDDEN = 403
        const val PROBE_MILLIS = 5_000L
        const val READ_BYTES = 64L * 1024
        const val TIMING_NOISE = 16_000
        const val WARM_UP = 30
        const val RUNS = 21
        val WEBM_ENCRYPTION: ByteArray = InitBytes.el(0x6D80, InitBytes.el(0x6240, InitBytes.uint(0x5033, 1)))
    }
}
