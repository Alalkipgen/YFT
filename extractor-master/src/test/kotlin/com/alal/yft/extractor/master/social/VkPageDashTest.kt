package com.alal.yft.extractor.master.social

import com.alal.yft.core.model.media.BrowserRequestContext
import com.alal.yft.core.model.media.CandidateSource
import com.alal.yft.core.model.media.MediaCandidate
import com.alal.yft.core.model.media.MediaKind
import com.alal.yft.extractor.api.SiteExtractionFailure.DRM_PROTECTED
import com.alal.yft.extractor.api.SiteExtractionFailure.HTTP_STATUS
import com.alal.yft.extractor.api.SiteExtractionFailure.NO_MEDIA_FOUND
import com.alal.yft.extractor.master.NOW
import com.alal.yft.extractor.master.RecordingValidator
import com.alal.yft.extractor.master.ValidationResult
import com.alal.yft.extractor.master.contract.AGENT
import com.alal.yft.extractor.master.lookup.LookupAsk
import com.alal.yft.extractor.master.lookup.LookupRecord
import com.alal.yft.extractor.master.modules.youtube.ladder.InitBytes
import com.alal.yft.extractor.master.modules.youtube.ladder.MediaStart
import com.alal.yft.extractor.master.modules.youtube.ladder.MediaStartReader
import java.io.ByteArrayOutputStream
import java.util.zip.GZIPOutputStream
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * VK's pack proof (V3's retest, 2026-10-26): the DASH manifest VK's player fetched on its page is
 * the row. In a VK lookup the shared row check reads it whole (one ask), checks its tallest picture
 * file and that file's sound (one ask each, V and the init check) and passes the row of both whole
 * files, which the app downloads and merges; its pieces are never rows (F1). A ladder's manifest
 * row, an HLS manifest and another site's manifest row stay unknown, unasked. JVM only, no network.
 */
class VkPageDashTest {
    @Test
    fun `VK's page manifest is the row of its tallest picture file and its best sound, three asks`() = runTest {
        val files = Files(::answer)
        val check = SocialRowCheck(RecordingValidator(), files, site = VkSite.SITE)
        val record = LookupRecord(labels = VkSite.SITE.labels())

        val checked = withContext(record + MainPages(site = VkClient.SITE_ID)) { check.validate(manifestRow(), NOW) }

        val row = (checked as ValidationResult.Valid).candidate
        assertEquals("$FILES?expires=1900000000&type=3&id=7", row.mediaUrl)
        assertEquals(MediaKind.DIRECT, row.kind)
        assertEquals("video/mp4", row.mimeType)
        assertEquals(listOf("avc1.64001f"), row.codecs)
        assertEquals(720 to 1280, row.width to row.height)
        assertEquals(118_000L, row.durationMillis)
        assertEquals(PICTURE_TOTAL, row.contentLengthBytes)
        assertEquals(PAGE, row.pageUrl)
        assertTrue(row.sources.containsAll(setOf(CandidateSource.REQUEST, CandidateSource.MANIFEST)))
        val sound = row.audioCompanion!!
        assertEquals("$FILES?expires=1900000000&type=11&id=7", sound.mediaUrl)
        assertEquals("audio/mp4", sound.mimeType)
        assertEquals(SOUND_TOTAL, sound.contentLengthBytes)
        // The download's own headers: the page as Referer, the lookup's agent, no cookie.
        assertEquals(PAGE, row.requestContext.pageUrl)
        assertEquals(AGENT, row.requestContext.userAgent)
        assertNull(row.requestContext.cookie)
        assertEquals(PAGE, sound.requestContext.pageUrl)

        // One whole read of the manifest (at most 64 KiB), then each file's start (4 KiB).
        assertEquals(
            listOf(MANIFEST to 65_536, row.mediaUrl to 4_096, sound.mediaUrl to 4_096),
            files.asked,
        )
        files.headers.forEach { headers ->
            assertEquals(PAGE, headers["Referer"])
            assertTrue(headers.keys.none { it.equals("Cookie", true) || it.equals("Range", true) })
        }
        val asks = record.asks()
        assertEquals(3, asks.size)
        asks.forEach { ask ->
            assertEquals(LookupRecord.Asker.MASTER, ask.asker)
            assertEquals(LookupAsk.GET, ask.method)
            assertFalse(ask.authorized)
        }

        // The engine's check of the row's sound (its own length) asks nothing new.
        val soundRow = row.copy(
            mediaUrl = sound.mediaUrl, mimeType = sound.mimeType, codecs = sound.codecs,
            contentLengthBytes = sound.contentLengthBytes, requestContext = sound.requestContext,
            width = null, height = null, audioCompanion = null,
        )
        val soundChecked = withContext(record + MainPages(site = VkClient.SITE_ID)) { check.validate(soundRow, NOW) }
        assertTrue(soundChecked.toString(), soundChecked is ValidationResult.Valid)
        assertEquals(3, files.asked.size)
        assertEquals(3, record.asks().size)
    }

    @Test
    fun `a WebM manifest gives its tallest WebM picture with its WebM sound, a packed answer is unpacked`() = runTest {
        val webm = mpd(
            video = representation("0", "video/webm", "av01.0.04M.08", 852, 480, 331_200, "type=2") +
                representation("1", "video/webm", "av01.0.08M.08", 1920, 1080, 1_136_470, "type=5"),
            audio = audioSet("audio/webm", "opus", "type=10" to 35_629, "type=12" to 205_341, "type=11" to 133_256),
        )
        val files = Files { url ->
            when {
                url == MANIFEST -> manifest(gzip(webm.toByteArray()), packedLength = true)
                "type=12" in url -> start(InitBytes.webm("A_OPUS"), "audio/webm", SOUND_TOTAL)
                else -> start(InitBytes.webm("V_AV1"), "video/webm", PICTURE_TOTAL)
            }
        }
        val check = SocialRowCheck(RecordingValidator(), files, site = VkSite.SITE)
        val record = LookupRecord(labels = VkSite.SITE.labels())

        val checked = withContext(record + MainPages(site = VkClient.SITE_ID)) { check.validate(manifestRow(), NOW) }

        val row = (checked as ValidationResult.Valid).candidate
        assertEquals("$FILES?expires=1900000000&type=5&id=7", row.mediaUrl)
        assertEquals("video/webm", row.mimeType)
        assertEquals("$FILES?expires=1900000000&type=12&id=7", row.audioCompanion?.mediaUrl)
        assertEquals("audio/webm", row.audioCompanion?.mimeType)
    }

    @Test
    fun `a protected manifest is DRM, a cut, live or piece-only one is unknown, each after its one ask`() = runTest {
        val cases = listOf(
            DRM_PROTECTED to manifest(mpd(protection = true).toByteArray()),
            NO_MEDIA_FOUND to manifest(mpd().toByteArray().copyOf(400), total = mpd().length.toLong()),
            NO_MEDIA_FOUND to manifest(mpd(dynamic = true).toByteArray()),
            NO_MEDIA_FOUND to manifest(
                mpd(
                    video = """<Representation id="1" mimeType="video/mp4" codecs="avc1.64001f" width="720" height="1280" bandwidth="1">""" +
                        """<SegmentTemplate media="${'$'}Number${'$'}.m4s" initialization="init.mp4" duration="4" startNumber="1"/></Representation>""",
                    audio = "",
                ).toByteArray(),
            ),
            HTTP_STATUS to MediaStart.Read(204, "application/dash+xml", null, 0, ByteArray(0)),
        )
        cases.forEach { (reason, answer) ->
            val files = Files { url -> if (url == MANIFEST) answer else start(InitBytes.mp4(), "video/mp4", PICTURE_TOTAL) }
            val check = SocialRowCheck(RecordingValidator(), files, site = VkSite.SITE)
            val record = LookupRecord(labels = VkSite.SITE.labels())
            val checked = withContext(record + MainPages(site = VkClient.SITE_ID)) { check.validate(manifestRow(), NOW) }
            assertEquals(answer.toString(), reason, (checked as ValidationResult.Rejected).reason)
            assertEquals(listOf(MANIFEST), files.asked.map { it.first })
        }
    }

    @Test
    fun `a picture file that fails or is encrypted fails the row, a file off VK's hosts is never asked`() = runTest {
        val refused = Files { url -> if (url == MANIFEST) MediaStart.Failed(403) else error("asked") }
        val forbidden = Files { url ->
            when (url) {
                MANIFEST -> manifest(mpd().toByteArray())
                else -> MediaStart.Failed(403)
            }
        }
        val encrypted = Files { url ->
            when {
                url == MANIFEST -> manifest(mpd().toByteArray())
                else -> start(InitBytes.mp4(encrypted = true), "video/mp4", PICTURE_TOTAL)
            }
        }
        val offHost = mpd(
            video = representation("1", "video/mp4", "avc1.64001f", 720, 1280, 1_000_000, "type=3")
                .replace("<BaseURL>?", "<BaseURL>https://cdn.other.test/v.mp4?"),
        )
        val other = Files { url -> if (url == MANIFEST) manifest(offHost.toByteArray()) else error("asked") }
        listOf(
            refused to HTTP_STATUS, forbidden to HTTP_STATUS, encrypted to DRM_PROTECTED, other to NO_MEDIA_FOUND,
        ).forEach { (files, reason) ->
            val check = SocialRowCheck(RecordingValidator(), files, site = VkSite.SITE)
            val record = LookupRecord(labels = VkSite.SITE.labels())
            val checked = withContext(record + MainPages(site = VkClient.SITE_ID)) { check.validate(manifestRow(), NOW) }
            assertEquals(reason, (checked as ValidationResult.Rejected).reason)
        }
        assertEquals(listOf(MANIFEST), other.asked.map { it.first })
    }

    @Test
    fun `a ladder's manifest row, an HLS manifest and another site's manifest row stay unknown, unasked`() = runTest {
        val files = Files { error("asked") }
        val check = SocialRowCheck(RecordingValidator(), files, site = VkSite.SITE)
        val ladderRow = manifestRow().copy(sources = setOf(CandidateSource.MANIFEST))
        val hlsRow = manifestRow().copy(
            mediaUrl = "$FILES?expires=1900000000&type=8&id=7", kind = MediaKind.HLS,
            mimeType = "application/vnd.apple.mpegurl",
        )
        val vkRecord = LookupRecord(labels = VkSite.SITE.labels())
        val vk = withContext(vkRecord + MainPages(site = VkClient.SITE_ID)) {
            listOf(ladderRow, hlsRow).map { check.validate(it, NOW) }
        }
        val facebookRow = manifestRow().copy(
            pageUrl = "https://www.facebook.com/watch/?v=1",
            mediaUrl = "https://video.xx.fbcdn.net/v/manifest.mpd?oe=7A000000",
        )
        val facebook = withContext(LookupRecord(labels = SiteWays.FACEBOOK.labels()) + MainPages(site = "facebook")) {
            check.validate(facebookRow, NOW)
        }
        (vk + facebook).forEach { assertEquals(it.toString(), NO_MEDIA_FOUND, (it as ValidationResult.Rejected).reason) }
        assertTrue(files.asked.isEmpty())
        assertTrue(vkRecord.asks().isEmpty())
    }

    @Test
    fun `the reader picks MP4 before WebM, the tallest picture and the sound with the most bandwidth`() {
        val both = mpd(
            video = representation("0", "video/mp4", "avc1.4d401e", 478, 852, 331_200, "type=2") +
                representation("1", "video/webm", "av01.0.12M.08", 2160, 3840, 6_273_319, "type=7"),
        )
        val read = VkPageDash.read(both, MANIFEST, NOW) as VkPageDash.Read.Files
        assertEquals("$FILES?expires=1900000000&type=2&id=7", read.picture.mediaUrl)
        assertEquals("$FILES?expires=1900000000&type=11&id=7", read.sound?.mediaUrl)
        assertEquals(118_000L, read.durationMillis)
        // A manifest without a sound of the picture's container: the picture alone.
        val silent = VkPageDash.read(mpd(audio = ""), MANIFEST, NOW) as VkPageDash.Read.Files
        assertNull(silent.sound)
        assertEquals(VkPageDash.Read.None(NO_MEDIA_FOUND), VkPageDash.read("<html></html>", MANIFEST, NOW))
        assertNull(VkPageDash.text(byteArrayOf(0x1f, 0x8b.toByte(), 1, 2, 3)))
        assertFalse(VkPageDash.pageManifest(manifestRow().copy(sources = setOf(CandidateSource.MANIFEST))))
        assertTrue(VkPageDash.pageManifest(manifestRow()))
    }

    private class Files(private val answer: (String) -> MediaStart) : MediaStartReader {
        val asked = mutableListOf<Pair<String, Int>>()
        val headers = mutableListOf<Map<String, String>>()

        override suspend fun read(url: String, headers: Map<String, String>, maxBytes: Int): MediaStart {
            asked += url to maxBytes
            this.headers += headers
            return answer(url)
        }
    }

    private companion object {
        const val PAGE = "https://m.vkvideo.ru/video-100000001_400000001"
        const val FILES = "https://vkvd1.okcdn.ru/"
        const val MANIFEST = "${FILES}?expires=1900000000&type=4&id=7"
        const val PICTURE_TOTAL = 9_876_543L
        const val SOUND_TOTAL = 1_900_000L

        /** The capture's row: the manifest the page's player fetched. */
        fun manifestRow() = MediaCandidate(
            pageUrl = PAGE,
            mediaUrl = MANIFEST,
            sources = setOf(CandidateSource.REQUEST),
            kind = MediaKind.DASH,
            mimeType = "application/dash+xml",
            requestContext = BrowserRequestContext(PAGE, AGENT, null),
            observedAtEpochMs = NOW,
        )

        fun representation(
            id: String, mime: String, codecs: String, width: Int, height: Int, bandwidth: Long, type: String,
        ) = """<Representation id="$id" mimeType="$mime" codecs="$codecs" width="$width" height="$height" """ +
            """bandwidth="$bandwidth"><BaseURL>?expires=1900000000&amp;$type&amp;id=7</BaseURL>""" +
            """<SegmentBase indexRange="1428-1700"><Initialization range="0-1427"/></SegmentBase></Representation>"""

        fun audioSet(mime: String, codecs: String, vararg reps: Pair<String, Long>) =
            """<AdaptationSet id="1" mimeType="$mime" codecs="$codecs" audioSamplingRate="48000">""" +
                reps.mapIndexed { index, (type, bandwidth) ->
                    """<Representation id="a$index" bandwidth="$bandwidth"><BaseURL>?expires=1900000000&amp;$type&amp;id=7</BaseURL>""" +
                        """<SegmentBase indexRange="1302-1500"><Initialization range="0-1301"/></SegmentBase></Representation>"""
                }.joinToString("") + "</AdaptationSet>"

        /** A manifest as VK's phone page gets it: MP4 qualities, MP4 sounds, subtitles. */
        fun mpd(
            video: String = representation("0", "video/mp4", "avc1.4d401e", 478, 852, 331_200, "type=2") +
                representation("1", "video/mp4", "avc1.64001f", 720, 1280, 681_882, "type=3"),
            audio: String = audioSet("audio/mp4", "mp4a.40.2", "type=10" to 35_629, "type=11" to 133_256),
            protection: Boolean = false,
            dynamic: Boolean = false,
        ) = """<?xml version="1.0" encoding="UTF-8"?>""" +
            """<MPD xmlns="urn:mpeg:DASH:schema:MPD:2011" mediaPresentationDuration="PT118.000S" """ +
            """minBufferTime="PT0.5S" type="${if (dynamic) "dynamic" else "static"}" """ +
            """profiles="urn:mpeg:dash:profile:isoff-on-demand:2011"><Period id="0" start="PT0S">""" +
            """<AdaptationSet id="0">""" +
            (if (protection) """<ContentProtection schemeIdUri="urn:mpeg:dash:mp4protection:2011" value="cenc"/>""" else "") +
            video + "</AdaptationSet>" + audio +
            """<AdaptationSet contentType="text" lang="ru" mimeType="text/vtt"><Representation id="s" bandwidth="1">""" +
            """<BaseURL>?expires=1900000000&amp;type=99&amp;id=7</BaseURL></Representation></AdaptationSet>""" +
            "</Period></MPD>"

        /** The manifest's answer to a 64 KiB range: all of it ([total] names a larger file: cut). */
        fun manifest(bytes: ByteArray, total: Long = bytes.size.toLong(), packedLength: Boolean = false) =
            MediaStart.Read(
                206, "application/dash+xml", "bytes 0-${bytes.size - 1}/${if (packedLength) bytes.size.toLong() else total}",
                bytes.size.toLong(), bytes,
            )

        fun start(bytes: ByteArray, type: String, total: Long) =
            MediaStart.Read(206, type, "bytes 0-${bytes.size - 1}/$total", bytes.size.toLong(), bytes)

        fun answer(url: String): MediaStart = when {
            url == MANIFEST -> manifest(mpd().toByteArray())
            "type=11" in url || "type=10" in url -> start(InitBytes.mp4(sample = "mp4a", handler = "soun"), "audio/mp4", SOUND_TOTAL)
            else -> start(InitBytes.mp4(), "video/mp4", PICTURE_TOTAL)
        }

        fun gzip(bytes: ByteArray): ByteArray =
            ByteArrayOutputStream().also { out -> GZIPOutputStream(out).use { it.write(bytes) } }.toByteArray()
    }
}
