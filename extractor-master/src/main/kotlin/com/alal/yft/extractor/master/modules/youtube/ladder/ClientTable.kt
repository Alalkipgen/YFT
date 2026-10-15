package com.alal.yft.extractor.master.modules.youtube.ladder

import com.alal.yft.extractor.master.modules.youtube.YouTubeClientProfile
import com.alal.yft.extractor.master.modules.youtube.YouTubeClientProfiles
import com.alal.yft.extractor.master.modules.youtube.YouTubePageSignals

/**
 * Phase 1.3 D2 — the client table v2 (MASTER_KEY_PHASE1_3_PLAN.md §4 D2), data only: the identities
 * the ladder may ask, each with its identity values (the copied profiles, unchanged), where they
 * come from, the class the census measured, its census status with the date (live, provisional:
 * wired as a fallback with V on every row but asked on fewer than 4 distinct videos, §9 item 9, or
 * measured, not active), the variants it allows (A: a self-healed version; S: the own solver; H:
 * the HLS check), its version pattern and its D0 version-source status.
 *
 * Guardrail 5 (the test checks it): at most 4 identities (visionOS, Android, embedded, MWEB); at
 * most 2 bundled visionOS alternates, each from a public reference with source and date (none
 * today: yt-dlp 2026.08.19 and YouTube.js list 1.02 only); a version source is live only on
 * raw.githubusercontent.com (owner, 2026-10-15: no redirect, ≤ 64 KiB, text only) or YouTube's own
 * page. Status from the D0 census (valid runs 1–3, weekly checks 1–2; §5).
 */
internal object MasterClientTable {
    const val VERSION: Int = 2
    const val DATE: String = "2026-10-15"

    /** R6's own first request. */
    const val R0: String = "R0"

    /** Another identity, asked after visionOS failed. */
    const val O: String = "O"

    enum class Status(val label: String) {
        LIVE("live"),

        /** Wired as a fallback, V on every row, but asked on fewer than 4 distinct videos (§9 item 9). */
        PROVISIONAL("provisional"),
        NOT_ACTIVE("measured, not active"),
    }

    enum class Variant { A, S, H }

    class Allowed(val variant: Variant, val status: Status, val note: String) {
        override fun toString(): String = "$variant ${status.label}"
    }

    class Alternate(val version: String, val source: String, val date: String)

    class VersionSource(val id: String, val host: String, val status: Status, val date: String, val note: String) {
        override fun toString(): String = "$id ${status.label}"
    }

    class Entry(
        val id: String,
        val rung: String,
        val profile: YouTubeClientProfile,
        /** The version comes from the watch page (main's rule); [profile] is built without one. */
        val versionFromPage: Boolean,
        val valuesSource: String,
        val expectedClass: CensusClass,
        val status: Status,
        val date: String,
        val note: String,
        val variants: List<Allowed>,
        val versionPattern: Regex,
        val alternates: List<Alternate>,
        val versionSources: List<VersionSource>,
    ) {
        override fun toString(): String = "$id $rung ${status.label}"
    }

    private const val RAW = "raw.githubusercontent.com"
    private const val WATCH_HOST = "www.youtube.com"
    private const val MOBILE_HOST = "m.youtube.com"
    private const val PAGE_VERSION_SEEN = "2.20261014.01.00"

    private val NO_PAGE = YouTubePageSignals(
        playerResponseJson = null,
        playerId = null,
        apiKey = null,
        clientName = null,
        clientVersion = null,
        visitorData = null,
        signatureTimestamp = null,
    )

    val ENTRIES: List<Entry> = listOf(
        Entry(
            id = "visionos",
            rung = R0,
            profile = YouTubeClientProfiles.VISION_OS,
            versionFromPage = false,
            valuesSource = YouTubeClientProfiles.DEVICE_VALUES_SOURCE,
            expectedClass = CensusClass.DIRECT,
            status = Status.LIVE,
            date = DATE,
            note = "R6's own first request (not gated); R0 DIRECT on 3 of 30, bot check on 24, R0+vd 24 of 24",
            variants = listOf(
                Allowed(Variant.A, Status.PROVISIONAL, "0 asked: no alternate bundled, no newer value in any source"),
                Allowed(Variant.S, Status.PROVISIONAL, "0 asked: no cipher in any visionOS answer"),
                Allowed(Variant.H, Status.PROVISIONAL, "4 of 4 passed, 1 distinct video (< 4)"),
            ),
            versionPattern = CensusSources.VISION_OS_VERSION,
            alternates = emptyList(),
            versionSources = listOf(
                VersionSource("yt-dlp", RAW, Status.LIVE, DATE, "1.02 = bundled in 3 of 3 runs"),
                VersionSource("youtube.js", RAW, Status.LIVE, DATE, "1.02 = bundled in 3 of 3 runs"),
                VersionSource("app-store", "itunes.apple.com", Status.NOT_ACTIVE, DATE, "no visionOS device listed"),
                VersionSource("answer", WATCH_HOST, Status.NOT_ACTIVE, DATE, "no value in any run"),
            ),
        ),
        Entry(
            id = "android",
            rung = O,
            profile = YouTubeClientProfiles.ANDROID,
            versionFromPage = false,
            valuesSource = YouTubeClientProfiles.DEVICE_VALUES_SOURCE,
            expectedClass = CensusClass.SABR_ONLY,
            status = Status.PROVISIONAL,
            date = DATE,
            note = "6 of 6 passed on videos 2 and 6 (< 4 distinct): the 360p progressive row only",
            variants = listOf(Allowed(Variant.A, Status.PROVISIONAL, "0 asked: yt-dlp = bundled")),
            versionPattern = CensusSources.ANDROID_VERSION,
            alternates = emptyList(),
            versionSources = listOf(
                VersionSource("yt-dlp", RAW, Status.LIVE, DATE, "21.26.364 = bundled in 3 of 3 runs"),
                VersionSource("youtube.js", RAW, Status.NOT_ACTIVE, DATE, "an older value"),
                VersionSource("play-store", "play.google.com", Status.NOT_ACTIVE, DATE, "over 64 KiB"),
            ),
        ),
        Entry(
            id = "embedded",
            rung = O,
            profile = YouTubeClientProfiles.embedded(NO_PAGE),
            versionFromPage = true,
            valuesSource = "main's embedded player (the watch page's version)",
            expectedClass = CensusClass.REFUSED,
            status = Status.NOT_ACTIVE,
            date = DATE,
            note = "0 of 6 (error 152-18 on videos 2 and 6)",
            variants = listOf(Allowed(Variant.S, Status.NOT_ACTIVE, "no row to solve")),
            versionPattern = CensusSources.WEB_VERSION,
            alternates = emptyList(),
            versionSources = listOf(
                VersionSource("page", WATCH_HOST, Status.NOT_ACTIVE, DATE, "the page's value, as main asks; refused 0 of 6"),
            ),
        ),
        Entry(
            id = "mweb",
            rung = O,
            profile = YouTubeClientProfiles.mobileWeb(),
            versionFromPage = false,
            valuesSource = YouTubeClientProfiles.DEVICE_VALUES_SOURCE,
            expectedClass = CensusClass.NEEDS_SCRIPT,
            status = Status.PROVISIONAL,
            date = DATE,
            note = "6 of 6 passed on videos 2 and 6 (< 4 distinct); 2 of 6 answers 360p only",
            variants = listOf(
                Allowed(Variant.S, Status.PROVISIONAL, "6 of 6: sig and n solved, V pass"),
                Allowed(Variant.A, Status.NOT_ACTIVE, "the page's newer value never tried"),
            ),
            versionPattern = CensusSources.WEB_VERSION,
            alternates = emptyList(),
            versionSources = listOf(
                VersionSource("page", MOBILE_HOST, Status.NOT_ACTIVE, DATE, "$PAGE_VERSION_SEEN, newer, never tried"),
            ),
        ),
    )

    fun entry(id: String): Entry? = ENTRIES.firstOrNull { it.id == id }

    /** The O rungs wired as fallbacks after visionOS fails, in table order (not the inactive ones). */
    fun oRungs(): List<Entry> = ENTRIES.filter { it.rung == O && it.status != Status.NOT_ACTIVE }

    /** One line for the census report: identities, rungs and statuses; never an address or value. */
    fun describe(): String = "client table v$VERSION ($DATE): " + ENTRIES.joinToString(" · ") { entry ->
        buildString {
            append(entry.id).append(' ').append(entry.rung).append(' ').append(entry.status.label)
            entry.variants.forEach { append(", ").append(it.variant.name).append(' ').append(it.status.label) }
        }
    }
}
