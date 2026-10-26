package com.alal.yft.extractor.master.social

import com.alal.yft.extractor.api.SiteExtractionFailure.GEO_RESTRICTED
import com.alal.yft.extractor.api.SiteExtractionFailure.LOGIN_REQUIRED
import com.alal.yft.extractor.api.SiteExtractionFailure.NO_MEDIA_FOUND
import com.alal.yft.extractor.api.SiteExtractionFailure.PRIVATE_OR_UNAVAILABLE

/**
 * VK-Master plan §6, §7: VK's own values for the shared tables (agent D registers them:
 * `SocialBudget.VK` in SocialStops.kt, `SiteWays.ALL`).
 */
object VkSite {
    /**
     * VK's budget per lookup (§6), counted as the four sites' are (`SocialStops`: the lookup's whole
     * record — main's asks, the ladder's and V's reads). The worst case, both values healed after
     * main's most (agent D's wiring, the owner's numbers with the live check's changes):
     * - main: at most 6 asks in a lookup it fails — the embed on its own host, once more after VK's
     *   shell, on the other host; path D's `video.get` with the token in memory, one new token and
     *   one more `video.get`. Main reads a manifest (at most 2 an answer) only for an answer it
     *   answers with, so Master never runs after one.
     * - V1: none (main's answers re-read).
     * - V2: at most 5 — one heal read ([VkClientSource.REQUESTS_PER_READ]: vkvideo.ru's page, one
     *   lookup ask however many redirects it takes, and its common script, which carries both
     *   values), one token and two `video.get`s (the second after error 8, its API version healed
     *   from the same script, no new read).
     * - V: at most 8 reads ([SocialRowCheck.MAX_READS]); V2 asks only while they still fit.
     *
     * So 6 + 2 + 1 + 2 + 8 = 19 requests (one value broken: 18). Bytes, each ask at its bound: the
     * page ([VkClientSource.PAGE_MAX_BYTES], 1 MiB), the common script
     * ([VkClientSource.COMMON_MAX_BYTES], 4 MiB; ~1.8 MB today), the token
     * ([VkClient.MAX_TOKEN_BYTES], 16 KiB) and two `video.get`s ([VkClient.MAX_API_BYTES], 1 MiB
     * each): 7,356,416 (the owner's 6,569,984 counted `error_monitoring`'s 256 KiB, which the live
     * check dropped: today's client id is in the common script, and a second `video.get`, 1 MiB).
     * Time: main's one 15 s deadline (`VkExtractor.DEADLINE_MS`; a Master lookup is never slower
     * than main's, A's census §6), so V2 asks only in what main left of it; out of time or bytes,
     * V2 stops and V3 answers.
     */
    val BUDGET = SocialBudget(
        requests = MAIN_ASKS + VkClientSource.REQUESTS_PER_READ + TOKEN_ASKS + VIDEO_GET_ASKS + SocialRowCheck.MAX_READS,
        bytes = VkClientSource.PAGE_MAX_BYTES + VkClientSource.COMMON_MAX_BYTES + VkClient.MAX_TOKEN_BYTES +
            VIDEO_GET_ASKS * VkClient.MAX_API_BYTES,
        millis = 15_000,
    )

    /** Main's most asks in a lookup it fails (see [BUDGET]). */
    const val MAIN_ASKS = 6

    /** V2's token asks (one) and `video.get`s (two: the second after error 8) in a lookup. */
    const val TOKEN_ASKS = 1
    const val VIDEO_GET_ASKS = 2

    /**
     * VK in the census's site table (§7, §10): main's name for it (`VkExtractor.displayName`),
     * main's ways in main's order (the embed page; after its adult answer, path D's token and
     * `video.get`), Master's own (V2's `video.get`), VK's labels ([VkLabels]), and the media hosts
     * V may ask: VK's video servers only (`vk{N}-{N}.vkuser.net`, `vkvd{N}.okcdn.ru`,
     * [VkClient.mediaFile]), never okcdn.ru's image host (the poster, the trailer's pictures).
     * In `SiteWays.ALL` (agent D), so a VK lookup's row check asks VK's hosts only.
     *
     * The slots are A's census kinds (`docs/VK_MASTER_CENSUS.md` §7.1; the owner's own public links,
     * kept on the phone only; slot 1 is W's [SiteWeekly.MASTER_SLOT]): two 18+ videos linked with
     * `list=` and one without, two videos, a clip and a finished live (`postlive`); removed is VK's
     * final answer (Master's deleted icon) or main's "isn't available" (`NO_MEDIA_FOUND`, VK5's
     * end); private or sign-in-only downloads with VK's session (`remixsid`) and answers sign-in
     * without it — signed out, VK's embed says "not found" for deleted, private and list-only alike,
     * so main's "isn't available" and a private answer say nothing about it then ("ambiguous (no
     * session)", 0.4); region-limited is VK's region answer (API error 13300; with no such link the
     * owner uses a second normal video).
     */
    val SITE = Site(
        id = VkClient.SITE_ID,
        name = "VK",
        mainWays = listOf(VkLabels.EMBED, VkLabels.TOKEN, VkLabels.VIDEO_GET),
        masterWay = VkLabels.VIDEO_GET,
        mediaHosts = listOf("vkuser.net", "okcdn.ru"),
        slots = List(2) { Kind("18+ video with list", Expect(rows = true)) } + listOf(
            Kind("18+ video", Expect(rows = true)),
            Kind("video", Expect(rows = true)),
            Kind("video", Expect(rows = true)),
            Kind("clip", Expect(rows = true)),
            Kind("finished live", Expect(rows = true)),
            Kind("removed", Expect(rows = false, reasons = setOf(PRIVATE_OR_UNAVAILABLE, NO_MEDIA_FOUND))),
            Kind(
                "private or sign-in-only",
                Expect(
                    rows = false,
                    reasons = setOf(LOGIN_REQUIRED),
                    rowsWithSession = true,
                    ambiguousWithoutSession = setOf(PRIVATE_OR_UNAVAILABLE, NO_MEDIA_FOUND),
                ),
            ),
            Kind("region-limited", Expect(rows = false, reasons = setOf(GEO_RESTRICTED))),
        ),
        mediaPath = { uri -> VkClient.mediaFile(uri.toString()) },
        newLabels = ::VkLabels,
    )
}
