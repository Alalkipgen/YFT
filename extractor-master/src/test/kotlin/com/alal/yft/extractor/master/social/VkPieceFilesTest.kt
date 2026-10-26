package com.alal.yft.extractor.master.social

import com.alal.yft.core.model.media.BrowserRequestContext
import com.alal.yft.core.model.media.CandidateSource
import com.alal.yft.core.model.media.MediaKind
import com.alal.yft.extractor.api.SiteExtractionFailure
import com.alal.yft.extractor.master.CapturedRequest
import com.alal.yft.extractor.master.MasterRequest
import com.alal.yft.extractor.master.NOW
import com.alal.yft.extractor.master.PageSnapshot
import com.alal.yft.extractor.master.ValidationResult
import com.alal.yft.extractor.master.RecordingValidator
import com.alal.yft.extractor.master.contract.AGENT
import com.alal.yft.extractor.master.lookup.LookupRecord
import com.alal.yft.extractor.master.modules.youtube.ladder.MediaStart
import com.alal.yft.extractor.master.modules.youtube.ladder.MediaStartReader
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * VK's pack proof (V3's retest 2 of f22c2217, 2026-10-26): the phone's capture of the 18+ video
 * held 18 observations, all pieces VK's player asked for (`&bytes=A-B`), no page manifest, so no
 * row. Each piece's address without its range is VK's whole file (the manifest's `BaseURL`):
 * [VkPieceFiles] groups the pieces into the picture files and the sound files of their container,
 * the fullest first by VK's `type`; in a VK lookup the row check names a picture file's own codec.
 * JVM only, no network; the addresses are made up.
 */
class VkPieceFilesTest {
    @Test
    fun `VK's pieces give their whole files, pictures and sounds the fullest first`() {
        val files = VkPieceFiles.of(request(), snapshot(phonePieces()))!!

        // 1080p (5), 720p (3), 480p (2: the playing one, three pieces), 360p (1), 240p (0), 144p (4).
        assertEquals(listOf("5", "3", "2", "1", "0", "4").map { whole(it, VIDEO) }, files.pictures.map { it.mediaUrl })
        assertEquals(listOf("3", "2", "1", "0", "4").map { whole(it, SOUND) }, files.sounds.map { it.mediaUrl })
        files.pictures.forEach { row ->
            assertEquals(MediaKind.DIRECT, row.kind)
            assertEquals("video/mp4", row.mimeType)
            assertEquals(setOf(CandidateSource.REQUEST), row.sources)
            assertEquals(PAGE, row.pageUrl)
            assertTrue(row.mediaUrl, "bytes=" !in row.mediaUrl)
        }
        files.sounds.forEach { assertEquals("audio/mp4", it.mimeType) }
        assertEquals("VkPieceFiles.Files(pictures=6, sounds=5, addresses=[omitted])", files.toString())
    }

    @Test
    fun `only VK's typed GET pieces count, MP4 before WebM, no picture piece gives none`() {
        val others = listOf(
            // Another site's piece, a POST, an untyped answer, an open range, a whole file, the manifest.
            CapturedRequest("https://cdn.example/v.mp4?type=5&bytes=0-1407", mimeType = "video/mp4"),
            CapturedRequest(piece("5", VIDEO, "0-1407"), method = "POST", mimeType = "video/mp4"),
            CapturedRequest(piece("3", VIDEO, "0-1407"), mimeType = null),
            CapturedRequest(piece("2", VIDEO, "0-"), mimeType = "video/mp4"),
            CapturedRequest(whole("1", VIDEO), mimeType = "video/mp4"),
            CapturedRequest("$FILES?expires=1900000000&type=1&ct=6&id=7", mimeType = "application/dash+xml"),
        )
        assertNull(VkPieceFiles.of(request(), snapshot(others)))
        // Sound pieces alone: no picture, no files.
        assertNull(VkPieceFiles.of(request(), snapshot(listOf(CapturedRequest(piece("3", SOUND, "0-1300"), mimeType = "audio/mp4")))))

        // WebM pictures only: the WebM sounds go with them, never MP4's.
        val webm = VkPieceFiles.of(
            request(),
            snapshot(
                listOf(
                    CapturedRequest(piece("2", VIDEO, "0-300"), mimeType = "video/webm"),
                    CapturedRequest(piece("3", VIDEO, "0-300"), mimeType = "video/webm"),
                    CapturedRequest(piece("2", SOUND, "0-200"), mimeType = "audio/webm"),
                    CapturedRequest(piece("3", SOUND, "0-200"), mimeType = "audio/mp4"),
                ) + others,
            ),
        )!!
        assertEquals(listOf(whole("3", VIDEO), whole("2", VIDEO)), webm.pictures.map { it.mediaUrl })
        assertEquals(listOf(whole("2", SOUND)), webm.sounds.map { it.mediaUrl })

        // With MP4 pictures too, MP4's are the pictures.
        val both = VkPieceFiles.of(
            request(),
            snapshot(listOf(CapturedRequest(piece("2", VIDEO, "0-300"), mimeType = "video/webm")) + phonePieces()),
        )!!
        assertTrue(both.pictures.all { it.mimeType == "video/mp4" })

        // A piece of another video (its content id) is left out.
        val other = VkPieceFiles.of(
            request(expected = "vk:-100000001_400000001"),
            snapshot(listOf(CapturedRequest(piece("5", VIDEO, "0-1407"), mimeType = "video/mp4", contentId = "vk:-1_2"))),
        )
        assertNull(other)
    }

    @Test
    fun `a piece's whole file is its address without its closed range`() {
        assertEquals("$FILES?a=1&b=2", VkPieceFiles.whole("$FILES?a=1&bytes=0-1407&b=2"))
        assertEquals("$FILES?a=1", VkPieceFiles.whole("$FILES?a=1&bytes=1408-90000#t"))
        assertEquals(FILES, VkPieceFiles.whole("$FILES?bytes=0-1"))
        assertNull(VkPieceFiles.whole("$FILES?a=1&bytes=0-"))
        assertNull(VkPieceFiles.whole("$FILES?a=1"))
        assertNull(VkPieceFiles.whole(FILES))
    }

    @Test
    fun `the row is the picture with its sound to merge`() {
        val files = VkPieceFiles.of(request(), snapshot(phonePieces()))!!
        val sound = files.sounds.first().copy(contentLengthBytes = 3_921_440)
        val row = VkPieceFiles.row(files.pictures.first(), sound)
        assertEquals(files.pictures.first().mediaUrl, row.mediaUrl)
        assertEquals(sound.mediaUrl, row.audioCompanion!!.mediaUrl)
        assertEquals("audio/mp4", row.audioCompanion!!.mimeType)
        assertEquals(3_921_440L, row.audioCompanion!!.contentLengthBytes)
        assertNull(VkPieceFiles.row(files.pictures.first(), null).audioCompanion)
    }

    @Test
    fun `in a VK lookup a picture file row that passes names its file's own codec, elsewhere none`() = runTest {
        val start = PictureBytes.fragmented(PictureBytes.video(1_080, 1_920))
        val reader = MediaStartReader { _, _, _ ->
            MediaStart.Read(206, "video/mp4", "bytes 0-${start.size - 1}/34715177", start.size.toLong(), start)
        }
        val row = VkPieceFiles.of(request(), snapshot(phonePieces()))!!.pictures.first()
        val vk = SocialRowCheck(RecordingValidator(), reader, site = VkSite.SITE)
        val checked = withContext(LookupRecord(labels = VkSite.SITE.labels()) + MainPages(site = VkClient.SITE_ID)) {
            vk.validate(row, NOW)
        }
        val passed = (checked as ValidationResult.Valid).candidate
        assertEquals(listOf("avc1"), passed.codecs)
        assertEquals(1_080 to 1_920, passed.width to passed.height)
        assertEquals(34_715_177L, passed.contentLengthBytes)
        assertEquals(StartDuration.millis(start), passed.durationMillis)
        // A row's own codecs stay.
        val stated = withContext(LookupRecord(labels = VkSite.SITE.labels()) + MainPages(site = VkClient.SITE_ID)) {
            vk.validate(row.copy(codecs = listOf("avc1.64002a")), NOW)
        }
        assertEquals(listOf("avc1.64002a"), (stated as ValidationResult.Valid).candidate.codecs)
        // Another site's lookup: as before, no codec named.
        val facebook = SocialRowCheck(RecordingValidator(), reader, site = SiteWays.FACEBOOK)
        val other = withContext(LookupRecord(labels = SiteWays.FACEBOOK.labels()) + MainPages()) {
            facebook.validate(row.copy(mediaUrl = "https://video.xx.fbcdn.net/v/t42/file.mp4?oe=7FFFFFFF"), NOW)
        }
        assertTrue(other.toString(), other is ValidationResult.Valid)
        assertEquals(emptyList<String>(), (other as ValidationResult.Valid).candidate.codecs)
    }

    /**
     * The owner's retest of 7e101aa4 (2026-10-26): the 1080p row's download read "cannot be
     * combined on this phone", as its sound named no codecs. The sound row (the engine checks the
     * row's companion as a row of its own) names its file's own, `mp4a.40.2`, in a VK lookup.
     */
    @Test
    fun `in a VK lookup a sound file row that passes names its file's own codec`() = runTest {
        val start = PictureBytes.fragmented(SoundBytes.sound(SoundBytes.mp4a(SoundBytes.esds(2))))
        val reader = MediaStartReader { _, _, _ ->
            MediaStart.Read(206, "audio/mp4", "bytes 0-${start.size - 1}/3921440", start.size.toLong(), start)
        }
        val files = VkPieceFiles.of(request(), snapshot(phonePieces()))!!
        val sound = files.sounds.first()
        val vk = SocialRowCheck(RecordingValidator(), reader, site = VkSite.SITE)
        val checked = withContext(LookupRecord(labels = VkSite.SITE.labels()) + MainPages(site = VkClient.SITE_ID)) {
            vk.validate(sound, NOW)
        }
        val passed = (checked as ValidationResult.Valid).candidate
        assertEquals(listOf("mp4a.40.2"), passed.codecs)
        assertEquals(3_921_440L, passed.contentLengthBytes)
        assertNull(passed.width)
        // A sound row's own codecs stay.
        val stated = withContext(LookupRecord(labels = VkSite.SITE.labels()) + MainPages(site = VkClient.SITE_ID)) {
            vk.validate(sound.copy(codecs = listOf("mp4a.40.5")), NOW)
        }
        assertEquals(listOf("mp4a.40.5"), (stated as ValidationResult.Valid).candidate.codecs)
    }

    private companion object {
        const val PAGE = "https://m.vkvideo.ru/video-100000001_400000001"
        const val FILES = "https://vk1-2.vkuser.net/"
        const val VIDEO = "11"
        const val SOUND = "12"

        fun whole(type: String, ct: String) = "$FILES?expires=1900000000&srcIp=192.0.2.1&type=$type&sig=s$type$ct&ct=$ct&id=7"

        fun piece(type: String, ct: String, range: String) = whole(type, ct) + "&bytes=$range"

        /** What VK's phone player asked for: each quality's start and sound's, then the 480p's pieces. */
        fun phonePieces(): List<CapturedRequest> =
            listOf("4", "0", "1", "2", "3", "5").map { CapturedRequest(piece(it, VIDEO, "0-1407"), mimeType = "video/mp4") } +
                listOf("4", "0", "1", "2", "3").map { CapturedRequest(piece(it, SOUND, "0-1300"), mimeType = "audio/mp4") } +
                listOf(
                    CapturedRequest(piece("2", VIDEO, "1408-900000"), mimeType = "video/mp4"),
                    CapturedRequest(piece("2", VIDEO, "900001-1800000"), mimeType = "video/mp4"),
                    CapturedRequest(piece("2", SOUND, "1301-200000"), mimeType = "audio/mp4"),
                )

        fun request(expected: String? = null) = MasterRequest(
            PAGE, 1, NOW, SiteExtractionFailure.NO_MEDIA_FOUND, expectedContentId = expected,
            requestContext = BrowserRequestContext(PAGE, AGENT, null),
        )

        fun snapshot(requests: List<CapturedRequest>) = PageSnapshot(PAGE, 1, requests = requests, authorizedPlayback = true)
    }
}
