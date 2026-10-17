package com.alal.yft.extractor.master.modules.youtube.ladder

import com.alal.yft.extractor.master.modules.youtube.YouTubeClientProfiles
import com.alal.yft.extractor.master.modules.youtube.YouTubePageSignals
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Phase 1.3 D2 (MASTER_KEY_PHASE1_3_PLAN.md §4 D2): the client table v2 keeps guardrail 5 — at most
 * 4 identities, all main's own profiles unchanged, at most 2 bundled alternates with source and
 * date, version sources live only on the approved hosts, no O rung live before the gate.
 */
class ClientTableTest {
    private val entries = MasterClientTable.ENTRIES

    @Test
    fun `at most 4 identities, each once, all already in main's code`() {
        val ids = entries.map(MasterClientTable.Entry::id)
        assertTrue(ids.size <= 4)
        assertEquals(ids.size, ids.toSet().size)
        assertEquals(listOf("visionos", "android", "embedded", "mweb"), ids)
        for (entry in entries) {
            assertEquals(entry.id, entry.profile.id)
            assertTrue(entry.id, entry.valuesSource.isNotBlank())
            assertEquals(entry, MasterClientTable.entry(entry.id))
        }
        assertEquals(null, MasterClientTable.entry("tv"))
    }

    @Test
    fun `the profiles are the copied ones, unchanged`() {
        assertEquals(YouTubeClientProfiles.VISION_OS, profile("visionos"))
        assertEquals(YouTubeClientProfiles.ANDROID, profile("android"))
        assertEquals(YouTubeClientProfiles.mobileWeb(), profile("mweb"))
        val noPage = YouTubePageSignals(null, null, null, null, null, null, null)
        assertEquals(YouTubeClientProfiles.embedded(noPage), profile("embedded"))
        // Without a page the embedded player sends main's fallback version; with one, the page's (main's rule).
        assertEquals(YouTubeClientProfiles.FALLBACK_CLIENT_VERSION, profile("embedded").clientVersion)
        assertTrue(MasterClientTable.entry("embedded")!!.versionFromPage)
        assertFalse(entries.filter { it.id != "embedded" }.any(MasterClientTable.Entry::versionFromPage))
        assertEquals(listOf(YouTubeClientProfiles.VISION_OS.id), entries.filter { it.rung == MasterClientTable.R0 }.map { it.id })
    }

    @Test
    fun `every bundled version matches its identity's pattern and no alternate is invented`() {
        for (entry in entries) {
            assertTrue("${entry.id} ${entry.profile.clientVersion}", entry.versionPattern.matches(entry.profile.clientVersion))
            assertTrue(entry.id, entry.alternates.size <= 2)
            for (alternate in entry.alternates) {
                assertTrue(entry.id, alternate.source.isNotBlank())
                assertIsoDate(alternate.date)
                assertTrue(entry.id, entry.versionPattern.matches(alternate.version))
                assertNotEquals(entry.id, entry.profile.clientVersion, alternate.version)
            }
        }
        assertTrue(CensusSources.VISION_OS_VERSION.matches(YouTubeClientProfiles.VISION_OS.clientVersion))
        assertTrue(CensusSources.ANDROID_VERSION.matches(YouTubeClientProfiles.ANDROID.clientVersion))
        assertTrue(CensusSources.WEB_VERSION.matches(YouTubeClientProfiles.MOBILE_CLIENT_VERSION))
        assertTrue(CensusSources.WEB_VERSION.matches(YouTubeClientProfiles.FALLBACK_CLIENT_VERSION))
    }

    @Test
    fun `visionOS has one bundled alternate - the App Store's 1_05 after run 5's census - and R0 stays 1_02`() {
        val vision = MasterClientTable.entry("visionos")!!
        val alternate = vision.alternates.single()
        assertSame(MasterClientTable.VISION_OS_ALTERNATE, alternate)
        assertEquals("1.05", alternate.version)
        assertEquals("App Store listing com.google.visionosyoutube 1.05.34", alternate.source)
        assertEquals("2026-09-19", alternate.date)
        assertEquals("1.02", vision.profile.clientVersion)
        assertEquals("1.02", YouTubeClientProfiles.VISION_OS.clientVersion)
        assertTrue(entries.filter { it.id != "visionos" }.all { it.alternates.isEmpty() })
    }

    @Test
    fun `a version source is live only on an approved host, and every date is an ISO date`() {
        assertIsoDate(MasterClientTable.DATE)
        assertEquals(3, MasterClientTable.VERSION)
        for (entry in entries) {
            assertIsoDate(entry.date)
            assertEquals(entry.id, entry.versionSources.size, entry.versionSources.map { it.id }.toSet().size)
            for (source in entry.versionSources) {
                assertIsoDate(source.date)
                assertTrue("${entry.id} ${source.id}", source.host.isNotBlank() && "/" !in source.host && ":" !in source.host)
                if (source.status == MasterClientTable.Status.LIVE) {
                    assertTrue("${entry.id} ${source.id} on ${source.host}", source.host in LIVE_SOURCE_HOSTS)
                }
            }
        }
        val live = entries.flatMap { entry -> entry.versionSources.filter { it.status == MasterClientTable.Status.LIVE }.map { "${entry.id} ${it.id}" } }
        assertEquals(listOf("visionos yt-dlp", "visionos youtube.js", "android yt-dlp"), live)
    }

    @Test
    fun `no O rung is live before the gate, and the O rungs are the provisional ones in table order`() {
        val others = entries.filter { it.rung == MasterClientTable.O }
        assertEquals(listOf("android", "embedded", "mweb"), others.map { it.id })
        assertTrue(others.none { it.status == MasterClientTable.Status.LIVE })
        assertEquals(MasterClientTable.Status.LIVE, MasterClientTable.entry("visionos")!!.status)
        assertEquals(MasterClientTable.Status.NOT_ACTIVE, MasterClientTable.entry("embedded")!!.status)
        assertEquals(listOf("android", "mweb"), MasterClientTable.oRungs().map { it.id })
        assertTrue(MasterClientTable.oRungs().all { it.status == MasterClientTable.Status.PROVISIONAL })
        for (entry in entries) {
            assertEquals(entry.id, entry.variants.size, entry.variants.map { it.variant }.toSet().size)
            assertNotNull(entry.expectedClass)
        }
    }

    @Test
    fun `describe is one line for the census report, without an address`() {
        val line = MasterClientTable.describe()
        assertFalse(line.contains('\n'))
        assertFalse(line.contains("://"))
        assertTrue(line, line.startsWith("client table v3 (2026-10-15): "))
        for (entry in entries) assertTrue(entry.id, line.contains("${entry.id} ${entry.rung} ${entry.status.label}"))
        assertEquals(
            "client table v3 (2026-10-15): visionos R0 live, A provisional, S provisional, H live · " +
                "android O provisional, A provisional · embedded O measured, not active, S measured, not active · " +
                "mweb O provisional, S provisional, A measured, not active",
            line,
        )
    }

    private fun profile(id: String) = requireNotNull(MasterClientTable.entry(id)).profile

    private fun assertIsoDate(date: String) {
        assertTrue(date, ISO_DATE.matches(date))
        assertEquals(date, LocalDate.parse(date).toString())
    }

    private companion object {
        val ISO_DATE = Regex("^\\d{4}-\\d{2}-\\d{2}$")
        val LIVE_SOURCE_HOSTS = setOf("raw.githubusercontent.com", "www.youtube.com", "m.youtube.com")
    }
}
