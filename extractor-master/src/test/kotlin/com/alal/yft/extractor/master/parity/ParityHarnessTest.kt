package com.alal.yft.extractor.master.parity

import com.alal.yft.core.model.media.BrowserRequestContext
import com.alal.yft.core.model.media.CandidateSource
import com.alal.yft.core.model.media.CompanionAudio
import com.alal.yft.core.model.media.MediaCandidate
import com.alal.yft.core.model.media.MediaKind
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

class ParityHarnessTest {
    @Test
    fun `the frozen list is public data with the plan's cases`() {
        val path = System.getProperty("yft.parityUrls")
        assumeTrue("parity list not supplied", path != null)
        val cases = ParityUrls.parse(File(checkNotNull(path)).readText())
        assertEquals(ParityUrls.SITES, cases.map { it.site }.toSet())
        val linked = cases.filter { it.url != null }
        assertEquals(14, linked.size)
        assertTrue(linked.all { ParityUrls.publicLink(checkNotNull(it.url)) })
        assertTrue(cases.filter { it.url == null }.all { it.purpose.startsWith("Owner picks") })
        assertTrue(cases.any { !it.positive && "LOGIN_REQUIRED" in it.expect })
        assertEquals(
            "https://www.tiktok.com/@nasa/video/7670337149526379789",
            cases.single { it.id == "tiktok-nasa" }.url,
        )
    }

    @Test
    fun `signed private or malformed entries fail the whole list`() {
        fun list(url: String?, expect: String = "\"video\"", id: String = "a-case") =
            """{"version":1,"cases":[{"id":"$id","site":"generic",""" +
                """"url":${url?.let { "\"$it\"" } ?: "null"},"expect":[$expect],""" +
                """"purpose":"Fixture"}]}"""
        assertEquals(1, ParityUrls.parse(list("https://example.test/watch?v=1")).size)
        assertEquals(1, ParityUrls.parse(list(null)).size)
        listOf(
            list("http://example.test/watch"),
            list("https://user:pass@example.test/watch"),
            list("https://cdn.example.test/v.mp4?oh=abc&oe=123"),
            list("https://example.test/v.mp4?token=abc"),
            list("https://example.test/v.mp4?X-Amz-Signature=abc"),
            list("https://example.test/watch#t=1"),
            list("https://example.test/watch", expect = "\"MAYBE\""),
            list("https://example.test/watch", id = "Bad Id"),
            """{"version":1,"cases":[]}""",
            "{broken",
        ).forEach { text ->
            assertTrue(text, runCatching { ParityUrls.parse(text) }.isFailure)
        }
    }

    @Test
    fun `the report keeps host and content ID only`() {
        val signed = "https://cdn.example.test/v/t42/clip.mp4?oh=SECRET_SIG&oe=6F00&_nc_ht=x"
        val candidates = listOf(
            MediaCandidate(
                pageUrl = "https://www.instagram.com/reel/Dcwk7e1yHaY/?igsh=SHARE_TOKEN",
                mediaUrl = signed,
                sources = setOf(CandidateSource.REQUEST),
                kind = MediaKind.DIRECT,
                mimeType = "video/mp4",
                title = "Clip https://evil.test/?cookie=SESSION_VALUE",
                thumbnailUrl = "https://cdn.example.test/thumb.jpg?sig=THUMB_SIG",
                durationMillis = 30_000,
                contentLengthBytes = 4_000_000,
                codecs = listOf("avc1.64001f"),
                audioCompanion = CompanionAudio(
                    "https://cdn.example.test/audio.m4a?sig=AUDIO_SIG", "audio/mp4",
                    listOf("mp4a.40.2"), BrowserRequestContext(null, null, "s=SESSION_VALUE"),
                ),
                videoId = "instagram:Dcwk7e1yHaY",
                width = 720,
                height = 1280,
            ),
            MediaCandidate(
                pageUrl = "https://www.instagram.com/reel/Dcwk7e1yHaY/",
                mediaUrl = "https://cdn.example.test/audio.m4a?sig=AUDIO_SIG",
                sources = setOf(CandidateSource.REQUEST),
                kind = MediaKind.DIRECT,
                mimeType = "audio/mp4",
                codecs = listOf("mp4a.40.2"),
                videoId = "https://evil.test/id?sig=ID_SIG",
            ),
        )
        val found = ParityReport.found(ParityArm.B, candidates, opened = { true }, totalMs = 900)
        assertEquals("Dcwk7e1yHaY", found.contentId)
        assertEquals(listOf("1280p", "audio"), found.rows.map { it.label })
        assertTrue(found.rows[0].hasAudio)
        assertTrue(found.rows[1].audioOnly)
        val json = ParityReport.toJson(
            listOf(
                ParityEntry(
                    "instagram-reel", "instagram",
                    ParityReport.host("https://user@WWW.Instagram.com:443/reel/x?igsh=T"),
                    listOf(found, found.copy(arm = ParityArm.A, contentId = "https://evil.test/a")),
                    problems = listOf("B lacks A row 720p", "see https://evil.test/leak"),
                ),
            ),
            generatedAt = "2026-10-10T00:00:00Z",
        )
        listOf(
            "://", "SECRET_SIG", "SHARE_TOKEN", "SESSION_VALUE", "THUMB_SIG", "AUDIO_SIG",
            "ID_SIG", "evil.test", "cdn.example.test", "Clip",
        ).forEach { assertFalse(it, it in json) }
        listOf("\"www.instagram.com\"", "\"Dcwk7e1yHaY\"", "\"1280p\"", "\"avc1.64001f\"")
            .forEach { assertTrue(it, it in json) }
        assertEquals(json, ParityReport.toJson(
            listOf(ParityEntry(
                "instagram-reel", "instagram", "www.instagram.com",
                listOf(found, found.copy(arm = ParityArm.A, contentId = "https://evil.test/a")),
                listOf("B lacks A row 720p", "see https://evil.test/leak"),
            )),
            "2026-10-10T00:00:00Z",
        ))
        assertNull(ParityReport.host("ftp://example.test/"))
    }

    @Test
    fun `verdict applies the plan's pass criteria`() {
        val positive = ParityCase("c", "x", "https://x.com/a/status/1", setOf("video"), "p")
        val r720 = ParityRow("720p", 720, "avc1.4d", true, false, 1, "DIRECT", true)
        val r1080 = r720.copy(label = "1080p", height = 1080)
        val a = ArmObservation(ParityArm.A, "video", "1", 30_000, rows = listOf(r720, r1080))
        val same = a.copy(arm = ParityArm.B)
        assertEquals(emptyList<String>(), ParityVerdict.problems(positive, a, same))
        val b = a.copy(
            arm = ParityArm.B, contentId = "2", durationMillis = 33_000,
            rows = listOf(r720.copy(opened = false), r720.copy(label = "x", height = null)),
        )
        val problems = ParityVerdict.problems(positive, a, b)
        listOf(
            "content ID differs", "duration differs by more than 2 s", "B row 720p not opened",
            "B row x has no stated height", "B lacks A row 1080p",
        ).forEach { assertTrue(it, it in problems) }
        assertEquals(
            listOf("B found no video where A did"),
            ParityVerdict.problems(positive, a, ArmObservation(ParityArm.B, "NO_MEDIA_FOUND")),
        )
        val negative = positive.copy(expect = setOf("LOGIN_REQUIRED"))
        val login = ArmObservation(ParityArm.A, "LOGIN_REQUIRED")
        assertEquals(emptyList<String>(), ParityVerdict.problems(negative, login, login))
        assertTrue(
            "reason differs: A=LOGIN_REQUIRED B=BOT_CHECK" in ParityVerdict.problems(
                negative, login, ArmObservation(ParityArm.B, "BOT_CHECK"),
            ),
        )
        assertTrue(
            ParityVerdict.problems(negative, login, login.copy(afterTerminal = 1)).isNotEmpty(),
        )
        assertTrue(ParityVerdict.timeOk(1_000, 1_700))
        assertFalse(ParityVerdict.timeOk(1_000, 1_701))
        assertEquals(2L, ParityVerdict.median(listOf(3, 1, 2)))
        assertNull(ParityVerdict.median(emptyList()))
    }
}
