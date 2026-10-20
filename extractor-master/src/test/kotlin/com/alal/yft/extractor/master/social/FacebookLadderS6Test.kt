package com.alal.yft.extractor.master.social

import com.alal.yft.core.model.media.BrowserRequestContext
import com.alal.yft.core.model.media.CandidateSource
import com.alal.yft.core.model.media.MediaCandidate
import com.alal.yft.core.model.media.MediaKind
import com.alal.yft.core.model.media.PageMediaRole
import com.alal.yft.extractor.api.SiteExtractionFailure
import com.alal.yft.extractor.api.SiteExtractionResult
import com.alal.yft.extractor.api.SitePageIdentity
import com.alal.yft.extractor.master.MasterFallbackEngine
import com.alal.yft.extractor.master.MasterPolicy
import com.alal.yft.extractor.master.MasterResult
import com.alal.yft.extractor.master.MasterStage
import com.alal.yft.extractor.master.NOW
import com.alal.yft.extractor.master.RecordingValidator
import com.alal.yft.extractor.master.ValidationResult
import com.alal.yft.extractor.master.contract.AGENT
import com.alal.yft.extractor.master.contract.CountingCapture
import com.alal.yft.extractor.master.contract.SiteContracts
import com.alal.yft.extractor.master.contract.answering
import com.alal.yft.extractor.master.contract.contractRequest
import com.alal.yft.extractor.master.contract.mainRequest
import com.alal.yft.extractor.master.lookup.LookupRecord
import com.alal.yft.extractor.master.lookup.LookupRecorder
import com.alal.yft.extractor.master.modules.youtube.ladder.InitBytes
import com.alal.yft.extractor.master.modules.youtube.ladder.MediaStart
import com.alal.yft.extractor.master.modules.youtube.ladder.MediaStartReader
import com.alal.yft.extractor.master.modules.youtube.testing.FakeExtractorHttpClient
import com.alal.yft.extractor.sites.facebook.FacebookExtractor
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Social plan S1a (6), S6: row checks in Facebook's lookups. Every row Master offers in a social
 * lookup passes V (one read of its file's first 4096 bytes with the download's own headers, never
 * the user's session, on the host the answer gave when it is a media host) and the init check on
 * the same bytes; `unknown` is never a pass; a row the bytes prove wrong is not offered; an
 * encryption marker is DRM (final); at most 8 files a lookup, each read once; the link's lifetime
 * (`oe`). Every other lookup: the engine's own V, unchanged. Main's real Facebook adapter over
 * synthetic pages and a fake file reader; no live calls.
 */
class FacebookLadderS6Test {
    @Test
    fun `every row Master offers passes V and the init check, with the download's headers`() = runTest {
        val files = Files { start() }
        val validator = RecordingValidator()
        val record = record()
        val result = lookup(record, files, validator)

        val success = result as MasterResult.Success
        assertEquals(MasterStage.PAGE_DATA, success.stage)
        val rows = success.result.candidates
        assertEquals(setOf(hd(VIDEO), sd(VIDEO)), rows.map { it.mediaUrl }.toSet())
        // Each file read once: its first 4096 bytes, with the download's own headers.
        assertEquals(setOf(hd(VIDEO), sd(VIDEO)), files.urls.toSet())
        assertEquals(2, files.asked.size)
        rows.forEach { row ->
            val asked = files.asked.single { it.url == row.mediaUrl }
            assertEquals(SocialRowCheck.START_BYTES, asked.maxBytes)
            assertEquals(row.requestContext.replayHeaders(), asked.headers)
            assertEquals(AGENT, asked.headers["User-Agent"])
            // The row's lifetime (oe), the file's size and type.
            assertEquals(OE_MILLIS, row.expiresAtEpochMs)
            assertEquals(TOTAL, row.contentLengthBytes)
            assertEquals("video/mp4", row.mimeType)
        }
        // The engine's own V is not asked; the reads are Master's asks in the lookup's record (F1's one too).
        assertTrue(validator.seen.isEmpty())
        assertEquals(3, record.masterAsks)
    }

    @Test
    fun `unknown is never a pass, and what is no media file of the site is not asked`() = runTest {
        val unknown = listOf(
            "a generic type" to start(type = "application/octet-stream"),
            "no type" to start(type = null),
            "too few bytes" to start(bytes = InitBytes.ftyp().copyOf(4)),
        )
        for ((case, answer) in unknown) {
            val files = Files { answer }
            assertEquals(case, SiteExtractionFailure.NO_MEDIA_FOUND, reasonOf(checked(SocialRowCheck(RecordingValidator(), files), row())))
            assertEquals(case, 1, files.asked.size)
        }
        // No answer, and a size with no total.
        val silent = Files { MediaStart.Failed(null) }
        assertEquals(SiteExtractionFailure.NETWORK, reasonOf(checked(SocialRowCheck(RecordingValidator(), silent), row())))
        val untotalled = Files { start(range = null) }
        assertEquals(
            SiteExtractionFailure.NO_MEDIA_FOUND,
            reasonOf(checked(SocialRowCheck(RecordingValidator(), untotalled), row(size = TOTAL))),
        )

        // Not asked: another host, no HTTPS, a manifest, a type the check cannot read.
        val files = Files { start() }
        val check = SocialRowCheck(RecordingValidator(), files)
        val unasked = listOf(
            row(url = "https://video.example-cdn.test/v/$VIDEO-hd.mp4?oe=$OE"),
            row(url = "http://video.fixture.fbcdn.net/v/$VIDEO-hd.mp4?oe=$OE"),
            row(url = "https://video.fixture.fbcdn.net/v/$VIDEO.m3u8?oe=$OE", kind = MediaKind.HLS),
            row(url = "https://video.fixture.fbcdn.net/v/$VIDEO.mpd?oe=$OE", kind = MediaKind.DASH),
            row(url = "https://video.fixture.fbcdn.net/v/$VIDEO-hd.flv?oe=$OE"),
            row(mime = "video/x-flv"),
        )
        unasked.forEach { assertEquals(SiteExtractionFailure.NO_MEDIA_FOUND, reasonOf(checked(check, it))) }
        assertTrue(files.asked.isEmpty())

        // In the lookup: no row is offered when the files answer a generic type.
        val generic = Files { start(type = "application/octet-stream") }
        val validator = RecordingValidator()
        val result = lookup(record(), generic, validator)
        assertFalse(result.toString(), result is MasterResult.Success)
        assertEquals(2, generic.asked.size)
        assertTrue(validator.seen.isEmpty())
    }

    @Test
    fun `a row the bytes prove wrong is not offered, and an encryption marker is DRM`() = runTest {
        val wrong = listOf(
            "a page, no MP4 start" to (row() to start(bytes = "<html><body>fixture</body></html>".toByteArray())),
            "another size" to (row(size = 1_000) to start()),
            "another type" to (row() to start(type = "video/webm")),
        )
        for ((case, pair) in wrong) {
            val (row, answer) = pair
            val reason = reasonOf(checked(SocialRowCheck(RecordingValidator(), Files { answer }), row))
            assertEquals(case, SiteExtractionFailure.RESPONSE_CHANGED, reason)
        }
        assertEquals(
            SiteExtractionFailure.HTTP_STATUS,
            reasonOf(checked(SocialRowCheck(RecordingValidator(), Files { MediaStart.Failed(403) }), row())),
        )

        // An encryption marker in the bytes, or the row's own DRM sign (not asked): DRM.
        val encrypted = Files { start(bytes = InitBytes.mp4(encrypted = true)) }
        assertEquals(SiteExtractionFailure.DRM_PROTECTED, reasonOf(checked(SocialRowCheck(RecordingValidator(), encrypted), row())))
        val hinted = Files { start() }
        assertEquals(
            SiteExtractionFailure.DRM_PROTECTED,
            reasonOf(checked(SocialRowCheck(RecordingValidator(), hinted), row().copy(drmHint = true))),
        )
        assertTrue(hinted.asked.isEmpty())

        // In the lookup: DRM is final (the engine's rule); nothing after it, capture included.
        val capture = CountingCapture()
        val result = lookup(record(), Files { start(bytes = InitBytes.mp4(encrypted = true)) }, capture = capture)
        assertEquals(SiteExtractionFailure.DRM_PROTECTED, (result as MasterResult.Failure).reason)
        assertEquals(0, capture.calls)
    }

    @Test
    fun `at most eight files a lookup, each read once`() = runTest {
        val files = Files { start() }
        val check = SocialRowCheck(RecordingValidator(), files)
        val record = record()
        val urls = (1..10).map { "https://video.fixture.fbcdn.net/v/$VIDEO-$it.mp4?oe=$OE&oh=fixture" }
        val results = withContext(record + MainPages()) { urls.map { check.validate(row(url = it), NOW) } }
        assertEquals(urls.take(SocialRowCheck.MAX_READS), files.urls)
        results.take(8).forEach { assertTrue(it.toString(), it is ValidationResult.Valid) }
        results.drop(8).forEach { assertEquals(SiteExtractionFailure.NO_MEDIA_FOUND, reasonOf(it)) }
        assertEquals(8, record.masterAsks)

        // Another row of a file read (another context): the bytes read, no new read.
        val again = withContext(record + MainPages()) { check.validate(row(url = urls[0], cookie = COOKIE), NOW) }
        assertTrue(again is ValidationResult.Valid)
        assertEquals(8, files.asked.size)

        // A new lookup reads again.
        val next = withContext(record() + MainPages()) { check.validate(row(url = urls[9]), NOW) }
        assertTrue(next is ValidationResult.Valid)
        assertEquals(9, files.asked.size)
    }

    @Test
    fun `the link's lifetime is the row's, and a link about to expire is not asked`() = runTest {
        val files = Files { start() }
        val check = SocialRowCheck(RecordingValidator(), files)
        val expired = row(url = "https://video.fixture.fbcdn.net/v/$VIDEO-hd.mp4?oe=${oe(NOW - MINUTE)}")
        val soon = row(url = "https://video.fixture.fbcdn.net/v/$VIDEO-hd.mp4?oe=${oe(NOW + 30_000)}")
        val rowSoon = row().copy(expiresAtEpochMs = NOW + 10_000)
        for (row in listOf(expired, soon, rowSoon)) {
            assertEquals(SiteExtractionFailure.EXPIRED_LINK, reasonOf(checked(check, row)))
        }
        assertTrue(files.asked.isEmpty())

        // The earlier of the row's own expiry and its link's.
        val valid = checked(check, row()) as ValidationResult.Valid
        assertEquals(OE_MILLIS, valid.candidate.expiresAtEpochMs)
        val sooner = checked(check, row().copy(expiresAtEpochMs = NOW + HOUR)) as ValidationResult.Valid
        assertEquals(NOW + HOUR, sooner.candidate.expiresAtEpochMs)

        // The file's host says the link is gone.
        val gone = Files { MediaStart.Failed(410) }
        assertEquals(SiteExtractionFailure.EXPIRED_LINK, reasonOf(checked(SocialRowCheck(RecordingValidator(), gone), row())))
    }

    @Test
    fun `a sound file answered as video passes as sound, the session is never sent and a 429 stops the reads`() = runTest {
        val sound = Files { start(bytes = InitBytes.mp4(sample = "mp4a", handler = "soun")) }
        val audio = row(url = "https://video.fixture.fbcdn.net/v/$VIDEO-audio.mp4?oe=$OE", mime = "audio/mp4", cookie = COOKIE)
        val passed = checked(SocialRowCheck(RecordingValidator(), sound), audio) as ValidationResult.Valid
        assertEquals("audio/mp4", passed.candidate.mimeType)
        // The row's context has the user's cookie: the read sends the download's other headers only.
        val headers = sound.asked.single().headers
        assertTrue(headers.keys.none { it.equals("Cookie", ignoreCase = true) })
        assertEquals(AGENT, headers["User-Agent"])

        // A 429: that row fails and nothing more is read in the lookup: not the host that stopped
        // (S7), not another media host (route B's rule: nothing new after a 429 in the lookup).
        val files = Files { url -> if ("-hd" in url) MediaStart.Failed(429) else start() }
        val check = SocialRowCheck(RecordingValidator(), files)
        val record = record()
        val results = withContext(record + MainPages()) {
            listOf(row(), row(url = sd(VIDEO)), row(url = "https://other.fixture.fbcdn.net/v/$VIDEO-sd.mp4?oe=$OE"))
                .map { check.validate(it, NOW) }
        }
        assertEquals(
            listOf(SiteExtractionFailure.RATE_LIMITED, SiteExtractionFailure.NO_MEDIA_FOUND, SiteExtractionFailure.NETWORK),
            results.map(::reasonOf),
        )
        assertEquals(listOf(hd(VIDEO)), files.urls)
        assertTrue(record.rateLimited)
        assertEquals(1, record.masterAsks)
    }

    @Test
    fun `every other lookup gets the engine's own check, and nothing is read`() = runTest {
        val files = Files { start() }
        val validator = RecordingValidator()
        val check = SocialRowCheck(validator, files)
        val row = row()
        // No lookup context, a record alone (no main's pages), main's pages alone.
        val outside = listOf(
            check.validate(row, NOW),
            withContext(record()) { check.validate(row, NOW) },
            withContext(MainPages()) { check.validate(row, NOW) },
        )
        outside.forEach { assertEquals(ValidationResult.Valid(row.copy(contentLengthBytes = 32_768)), it) }
        assertEquals(listOf(row, row, row), validator.seen)
        assertTrue(files.asked.isEmpty())
    }

    /** One Facebook lookup in [record] (main's moved-key page, the plugin player with no data) through S6. */
    private suspend fun lookup(
        record: LookupRecord,
        files: MediaStartReader,
        validator: RecordingValidator = RecordingValidator(),
        capture: CountingCapture = CountingCapture(),
    ): MasterResult {
        val watch = identify(WATCH)
        val main = answering(watch.canonicalPageUrl to movedKeyPage(VIDEO), contentType = HTML)
        val plugin = answering(embedUrl(watch) to NO_DATA, contentType = HTML)
        return withContext(record + MainPages()) {
            val failure = main(main)
            MasterFallbackEngine(
                SocialRowCheck(validator, files), capture, MasterPolicy(enabled = true),
                contracts = SiteContracts(plugin),
                social = FacebookLadder(plugin, ::identifyOrNull),
            ).extract(contractRequest(WATCH, watch, failure, COOKIE))
        }
    }

    /** Main's real Facebook adapter over [main] in the current lookup: its failure. */
    private suspend fun main(main: FakeExtractorHttpClient): SiteExtractionFailure {
        val answer = FacebookExtractor(MainPageRecorder(LookupRecorder(main))).extract(mainRequest(identify(WATCH), COOKIE))
        return (answer as SiteExtractionResult.Failure).reason
    }

    /** [check] on [row] in a social lookup of its own (its own record and main's pages). */
    private suspend fun checked(check: SocialRowCheck, row: MediaCandidate): ValidationResult =
        withContext(record() + MainPages()) { check.validate(row, NOW) }

    private fun record(): LookupRecord = LookupRecord(labels = SiteWays.FACEBOOK.labels())

    private fun reasonOf(result: ValidationResult): SiteExtractionFailure = (result as ValidationResult.Rejected).reason

    /** A fake file reader: [answer] for each address; what it was asked, in order. */
    private class Files(private val answer: (String) -> MediaStart) : MediaStartReader {
        class Asked(val url: String, val headers: Map<String, String>, val maxBytes: Int)

        val asked = mutableListOf<Asked>()
        val urls: List<String> get() = asked.map { it.url }

        override suspend fun read(url: String, headers: Map<String, String>, maxBytes: Int): MediaStart {
            asked += Asked(url, headers, maxBytes)
            return answer(url)
        }
    }

    private companion object {
        const val MINUTE = 60_000L
        const val HOUR = 60 * MINUTE
        const val VIDEO = "1000000000000003"
        const val OTHER_VIDEO = "1000000000000009"
        const val WATCH = "https://www.facebook.com/watch/?v=$VIDEO"
        const val COOKIE = "c_user=0; xs=fixture-cookie"
        const val HTML = "text/html"
        const val NO_DATA = "<html><head><title>Facebook</title></head><body>no data</body></html>"

        /** The links' `oe` (hex seconds, in 2099) and the lifetime it gives. */
        const val OE = "F2A52380"
        const val OE_MILLIS = 0xF2A52380L * 1000
        const val TOTAL = 32_768L

        fun oe(millis: Long): String = java.lang.Long.toHexString(millis / 1000).uppercase()

        fun identifyOrNull(url: String): SitePageIdentity? = FacebookExtractor(FakeExtractorHttpClient()).identify(url)

        fun identify(url: String): SitePageIdentity = checkNotNull(identifyOrNull(url))

        fun encode(value: String): String = URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20")

        fun embedUrl(identity: SitePageIdentity) =
            "https://www.facebook.com/plugins/video.php?href=" + encode(identity.canonicalPageUrl)

        fun hd(video: String) = "https://video.fixture.fbcdn.net/v/$video-hd.mp4?oe=$OE&oh=fixture"

        fun sd(video: String) = "https://video.fixture.fbcdn.net/v/$video-sd.mp4?oe=$OE&oh=fixture"

        /** A row Master offers (an MP4 file on Facebook's media host unless named), in the lookup's page. */
        fun row(
            url: String = hd(VIDEO),
            mime: String? = null,
            size: Long? = null,
            cookie: String? = null,
            kind: MediaKind = MediaKind.DIRECT,
        ) = MediaCandidate(
            pageUrl = WATCH,
            mediaUrl = url,
            sources = setOf(CandidateSource.DOM),
            kind = kind,
            mimeType = mime,
            contentLengthBytes = size,
            requestContext = BrowserRequestContext(WATCH, AGENT, cookie),
            observedAtEpochMs = NOW,
            pageRole = PageMediaRole.MAIN,
        )

        /** A file's first bytes as its host answers them: an MP4 start of a 32 KiB file unless named. */
        fun start(
            bytes: ByteArray = InitBytes.mp4(),
            type: String? = "video/mp4",
            range: String? = "bytes 0-${bytes.size - 1}/$TOTAL",
            status: Int = 206,
        ) = MediaStart.Read(status, type, range, bytes.size.toLong(), bytes)

        /** Main's page with the video's files under keys main does not know, a related video's beside them. */
        fun movedKeyPage(video: String): String =
            "<html><head><title>Facebook</title></head><body>" +
                "<script type=\"application/json\" data-sjs>{\"require\":[[\"VideoPlayerRelay\",null,null,[{" +
                "\"video\":{\"video_id\":\"$video\",\"browser_native_hd_url_v2\":\"${hd(video)}\"," +
                "\"browser_native_sd_url_v2\":\"${sd(video)}\"},\"related\":[{\"video_id\":\"$OTHER_VIDEO\"," +
                "\"browser_native_hd_url_v2\":\"${hd(OTHER_VIDEO)}\"}]}]]]}</script></body></html>"
    }
}
