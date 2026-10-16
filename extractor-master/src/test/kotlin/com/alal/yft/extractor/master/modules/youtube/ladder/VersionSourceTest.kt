package com.alal.yft.extractor.master.modules.youtube.ladder

import com.alal.yft.extractor.api.ExtractorHttpResult
import com.alal.yft.extractor.api.SiteExtractionFailure
import com.alal.yft.extractor.master.modules.youtube.YouTubeClientProfiles
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Phase 1.3 D5a — A's versions (MASTER_KEY_PHASE1_3_PLAN.md guardrail 5, §9 item 3): one rule per
 * test, with a fake source reader and the health's clock; no live call.
 */
class VersionSourceTest {
    private var now = START
    private val day = ClientHealth.DAY_MILLIS
    private val entry = requireNotNull(MasterClientTable.entry("visionos"))
    private val bundled = entry.profile.clientVersion

    private class Reader(var reply: (String) -> ExtractorHttpResult) : VersionSourceReader {
        class Read(val url: String, val headers: Map<String, String>, val maxBodyBytes: Long)

        val reads = mutableListOf<Read>()

        override suspend fun read(url: String, headers: Map<String, String>, maxBodyBytes: Long): ExtractorHttpResult {
            reads += Read(url, headers, maxBodyBytes)
            return reply(url)
        }
    }

    private fun text(body: String, type: String? = "text/plain; charset=utf-8", finalUrl: String? = null): (String) -> ExtractorHttpResult =
        { url -> ExtractorHttpResult.Success(statusCode = 200, body = body, finalUrl = finalUrl ?: url, contentType = type) }

    private fun health() = ClientHealth(null) { now }

    private fun entryWith(
        alternates: List<MasterClientTable.Alternate> = entry.alternates,
        sources: List<MasterClientTable.VersionSource> = entry.versionSources,
    ) = with(entry) {
        MasterClientTable.Entry(
            id, rung, profile, versionFromPage, valuesSource, expectedClass, status, date, note, variants, versionPattern,
            alternates, sources,
        )
    }

    @Test
    fun `one read a day, today's value from memory, never stored before OK and V`() = runTest {
        val health = health()
        val reader = Reader(text(Census.ytDlp("1.03")))
        val versions = VersionSource(health, reader)
        val first = versions.selfHealed(entry, bundled, "UA/1")
        assertEquals("1.03", first.candidate?.version)
        assertEquals(VersionSource.Kind.SOURCE, first.candidate?.kind)
        assertEquals("yt-dlp", first.candidate?.label)
        assertEquals(CensusSources.YT_DLP_URL, reader.reads.single().url)
        assertEquals(mapOf("User-Agent" to "UA/1"), reader.reads.single().headers)
        assertEquals(64L * 1024, reader.reads.single().maxBodyBytes)
        now += day - 1
        val second = versions.selfHealed(entry, bundled, "UA/1")
        assertEquals("1.03", second.candidate?.version)
        assertEquals("read today", second.why)
        assertEquals(1, reader.reads.size)
        assertNull(health.learnedVersion("visionos"))
        now += 1
        assertEquals("read", versions.selfHealed(entry, bundled, null).why)
        assertEquals(2, reader.reads.size)
        assertEquals(emptyMap<String, String>(), reader.reads.last().headers)
    }

    @Test
    fun `a failed read spends the day`() = runTest {
        val reader = Reader { ExtractorHttpResult.Failure(SiteExtractionFailure.NETWORK) }
        val versions = VersionSource(health(), reader)
        assertEquals("read failed (NETWORK)", versions.selfHealed(entry, bundled, null).why)
        reader.reply = text(Census.ytDlp("1.03"))
        val again = versions.selfHealed(entry, bundled, null)
        assertNull(again.candidate)
        assertEquals("not read (read in the last day)", again.why)
        assertEquals(1, reader.reads.size)
        now += day
        assertEquals("1.03", versions.selfHealed(entry, bundled, null).candidate?.version)
        assertEquals(2, reader.reads.size)
    }

    @Test
    fun `a redirect, another type or more than 64 KiB is not read`() = runTest {
        val cases = listOf(
            text(Census.ytDlp("1.03"), finalUrl = "https://raw.githubusercontent.com/other/file.py") to "redirected, not read",
            text(Census.ytDlp("1.03"), type = "text/html; charset=utf-8") to "not text",
            text(Census.ytDlp("1.03"), type = null) to "not text",
            // 33 000 two-byte characters: fewer characters than the limit, more bytes.
            text(Census.ytDlp("1.03") + "é".repeat(33_000)) to "over 64 KiB",
            { _: String -> ExtractorHttpResult.Failure(SiteExtractionFailure.HTTP_STATUS, 302) } to "read failed (HTTP_STATUS 302)",
        )
        for ((reply, why) in cases) {
            val read = VersionSource(health(), Reader(reply)).selfHealed(entry, bundled, null)
            assertNull(why, read.candidate)
            assertEquals(why, read.why)
        }
    }

    @Test
    fun `only one value, the pattern and newer than bundled`() = runTest {
        val two = Census.ytDlp("1.03") + Census.ytDlp("1.04")
        val cases = mapOf(
            "<html>no client table here</html>" to "no newer version (no value)",
            two to "no newer version (2 values)",
            Census.ytDlp("1.03b") to "no newer version (not the version pattern)",
            Census.ytDlp("1.3") to "no newer version (not the version pattern)",
            Census.ytDlp("1.01") to "no newer version (not newer)",
            Census.ytDlp(bundled) to "no newer version (the bundled value)",
        )
        for ((body, why) in cases) {
            val read = VersionSource(health(), Reader(text(body))).selfHealed(entry, bundled, null)
            assertNull(why, read.candidate)
            assertEquals(why, read.why)
        }
        assertEquals("10.40", VersionSource(health(), Reader(text(Census.ytDlp("10.40")))).selfHealed(entry, bundled, null).candidate?.version)
    }

    @Test
    fun `after a bot check or without a reader nothing is read and the day is not spent`() = runTest {
        val health = health().apply { record("android", CensusClass.BOT_CHECK) }
        val reader = Reader(text(Census.ytDlp("1.03")))
        assertEquals("not read (bot check)", VersionSource(health, reader).selfHealed(entry, bundled, null).why)
        assertTrue(reader.reads.isEmpty())
        assertTrue(health.maySourceRead("visionos"))
        val other = health()
        assertEquals("not read (no source reader)", VersionSource(other, null).selfHealed(entry, bundled, null).why)
        assertTrue(other.maySourceRead("visionos"))
    }

    @Test
    fun `a source that is not live is never read`() = runTest {
        val reader = Reader(text(Census.ytDlp("1.03")))
        val closed = entryWith(sources = entry.versionSources.filter { it.status != MasterClientTable.Status.LIVE })
        assertEquals("no live source", VersionSource(health(), reader).selfHealed(closed, bundled, null).why)
        val elsewhere = entryWith(sources = listOf(MasterClientTable.VersionSource("yt-dlp", "example.com", MasterClientTable.Status.LIVE, "2026-10-16", "test")))
        assertEquals("not on an approved host", VersionSource(health(), reader).selfHealed(elsewhere, bundled, null).why)
        assertTrue(reader.reads.isEmpty())
    }

    @Test
    fun `a rejected version is dropped, a rejected learned version forgotten`() = runTest {
        val health = health()
        val reader = Reader(text(Census.ytDlp("1.03")))
        val versions = VersionSource(health, reader)
        val read = requireNotNull(versions.selfHealed(entry, bundled, null).candidate)
        versions.rejected("visionos", read)
        assertEquals("not read (read in the last day)", versions.selfHealed(entry, bundled, null).why)
        health.learn("visionos", "1.04", "yt-dlp")
        val learned = requireNotNull(versions.learned(entry, bundled))
        assertEquals(VersionSource.Kind.LEARNED, learned.kind)
        assertEquals("learned", learned.label)
        assertEquals("yt-dlp", learned.source)
        versions.rejected("visionos", learned)
        assertNull(health.learnedVersion("visionos"))
        assertNull(versions.learned(entry, bundled))
    }

    @Test
    fun `a learned version that is no longer newer or valid is forgotten`() {
        for (version in listOf(bundled, "1.01", "1.3", "1.2.3")) {
            val health = health().apply { learn("visionos", version, "yt-dlp") }
            assertEquals(version, health.learnedVersion("visionos"))
            assertNull(version, VersionSource(health, null).learned(entry, bundled))
            assertNull(version, health.learnedVersion("visionos"))
        }
    }

    @Test
    fun `at most two bundled alternates, valid and not the bundled one`() {
        val alternate = { version: String -> MasterClientTable.Alternate(version, "yt-dlp 2026.08.19", "2026-10-16") }
        val versions = VersionSource(health(), null)
        assertEquals(emptyList<String>(), versions.alternates(entry, bundled).map { it.version })
        val listed = entryWith(alternates = listOf(alternate("1.01"), alternate(bundled), alternate("1.04")))
        val got = versions.alternates(listed, bundled)
        assertEquals(listOf("1.01"), got.map { it.version })
        assertEquals(listOf("bundled"), got.map { it.label })
        assertEquals(listOf("1.05"), versions.alternates(entryWith(alternates = listOf(alternate("1.5"), alternate("1.05"))), bundled).map { it.version })
    }

    @Test
    fun `withVersion changes only the version field and the version header`() {
        val profile = YouTubeClientProfiles.VISION_OS
        val body = profile.playerRequestBody(Sim.VIDEO_ID, visitorData = "Cgt2aXNpdG9y", signatureTimestamp = null)
        val headers = linkedMapOf(
            "User-Agent" to requireNotNull(profile.userAgent),
            "Accept" to "application/json",
            "X-YouTube-Client-Name" to "101",
            "X-YouTube-Client-Version" to bundled,
            "X-Goog-Visitor-Id" to "Cgt2aXNpdG9y",
        )
        val edited = requireNotNull(VersionSource.withVersion(body, headers, "VISIONOS", bundled, "1.03"))
        assertEquals(body.replace("\"clientVersion\":\"$bundled\"", "\"clientVersion\":\"1.03\""), edited.body)
        assertEquals(body.length + "1.03".length - bundled.length, edited.body.length)
        assertEquals("1.03", VersionSource.versionOf(edited.body, "VISIONOS"))
        assertEquals(headers.keys.toList(), edited.headers.keys.toList())
        assertEquals(headers + ("X-YouTube-Client-Version" to "1.03"), edited.headers)
        // A header naming another version stays as it is.
        val other = VersionSource.withVersion(body, headers + ("X-YouTube-Client-Version" to "9.99"), "VISIONOS", bundled, "1.03")
        assertEquals("9.99", other?.headers?.get("X-YouTube-Client-Version"))
        // Nothing changes when it cannot be one exact field.
        assertNull(VersionSource.withVersion(body, headers, "VISIONOS", bundled, bundled))
        assertNull(VersionSource.withVersion(body, headers, "VISIONOS", bundled, "1.03\",\"x\":\"y"))
        assertNull(VersionSource.withVersion(body, headers, "VISIONOS", "1.01", "1.03"))
        assertNull(VersionSource.withVersion(body, headers, "ANDROID", bundled, "1.03"))
        val twice = body.replace("\"hl\":\"en\"", "\"hl\":\"en\",\"clientVersion\":\"$bundled\"")
        assertNull(VersionSource.withVersion(twice, headers, "VISIONOS", bundled, "1.03"))
        assertNull(VersionSource.versionOf("not json", "VISIONOS"))
    }

    @Test
    fun `only HTTPS addresses on the approved host`() {
        val host = "raw.githubusercontent.com"
        assertEquals(setOf(host), VersionSource.SOURCE_HOSTS)
        assertTrue(VersionSource.approved(CensusSources.YT_DLP_URL, host))
        assertTrue(VersionSource.approved(CensusSources.YOUTUBE_JS_URL, host))
        for (url in listOf(
            "http://$host/yt-dlp/yt-dlp/master/yt_dlp/extractor/youtube/_base.py",
            "https://$host:8443/a.py",
            "https://user@$host/a.py",
            "https://$host/a.py#top",
            "https://example.com/a.py",
            "not a url",
        )) {
            assertFalse(url, VersionSource.approved(url, host))
        }
        assertFalse(VersionSource.approved(CensusSources.APP_STORE_URL, "itunes.apple.com"))
        // Every live visionOS source of the table is one A may read.
        val live = entry.versionSources.filter { it.status == MasterClientTable.Status.LIVE }
        assertEquals(listOf("yt-dlp", "youtube.js"), live.map { it.id })
        assertTrue(live.all { source -> VersionSource.urlOf(source.id)?.let { VersionSource.approved(it, source.host) } == true })
        assertNull(VersionSource.urlOf("app-store"))
    }

    private companion object {
        const val START = 1_791_000_000_000L
    }
}
