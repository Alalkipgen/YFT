package com.alal.yft.extractor.master.social

import com.alal.yft.extractor.api.SiteExtractionFailure.LOGIN_REQUIRED
import com.alal.yft.extractor.api.SiteExtractionFailure.NO_MEDIA_FOUND
import com.alal.yft.extractor.api.SiteExtractionFailure.PRIVATE_OR_UNAVAILABLE

/**
 * VK-Master plan §6, §7: VK's own values for the shared tables, kept in this file until agent D
 * registers them (SocialStops.kt and SiteWays.kt are shared: D's, not this pack's).
 */
object VkSite {
    /**
     * VK's budget per lookup (§6), counted as the four sites' are (`SocialStops`: the lookup's whole
     * record — main's asks, the ladder's and V's reads; agent A's census §6 proposed 4 / 1 MiB / 15 s,
     * counting Master's own asks and one V read). The worst case, a heal after main's most:
     * - main: at most 6 asks in a lookup it fails — the embed on its own host, once more after VK's
     *   shell, on the other host; path D's `video.get` with the token in memory, one new token and
     *   one more `video.get`. Main reads a manifest (at most 2 an answer) only for an answer it
     *   answers with, so Master never runs after one.
     * - V1: none (main's answers re-read).
     * - V2: at most 4 — the page that names today's script and the script (agent C's live source, A's
     *   census §5; none with the bundled values), then one token and one `video.get`.
     * - V: at most 8 reads ([SocialRowCheck.MAX_READS]).
     *
     * So 6 + 4 + 8 = 18 requests. Bytes: V2's two answers at their bounds (16 KiB + 1 MiB, main's:
     * [VkClient.MAX_TOKEN_BYTES], [VkClient.MAX_API_BYTES]); V1 stages Master's own answer, far
     * smaller; the live source's two reads add their own bounds when agent C wires it (A: the
     * cheapest script ~40 KB). Time: main's one 15 s deadline (a Master lookup is never slower than
     * main's, A's census §6), so V2 asks only in what main left of it. D registers it in SocialStops.
     */
    val BUDGET = SocialBudget(
        requests = 18,
        bytes = VkClient.MAX_TOKEN_BYTES + VkClient.MAX_API_BYTES,
        millis = 15_000,
    )

    /**
     * VK in the census's site table (§7, §10): main's name for it (`VkExtractor.displayName`),
     * main's ways in main's order (the embed page; after its adult answer, path D's token and
     * `video.get`), Master's own (V2's `video.get`), VK's labels ([VkLabels]), and the media hosts
     * V may ask: VK's video servers only (`vk{N}-{N}.vkuser.net`, `vkvd{N}.okcdn.ru`,
     * [VkClient.mediaFile]), never okcdn.ru's image host (the poster, the trailer's pictures).
     * Agent D adds it to `SiteWays.ALL`; until then a row check names it only when built with it
     * (`SocialRowCheck(site = VkSite.SITE)`), else a VK lookup's rows meet the check's own site's
     * hosts (the app's: Facebook's) and are refused.
     *
     * The slots are **placeholders, A finalizes** the kinds from the recon (§10: the owner's own
     * public links, kept on the phone only): three 18+ videos (one linked with `list=`), two videos,
     * a clip and a live replay download; removed is VK's final answer (Master's deleted icon) or
     * main's "isn't available" (`NO_MEDIA_FOUND`, VK5's end); private or sign-in-only downloads
     * with VK's session, else sign-in, private or main's "isn't available" (signed out, VK's embed
     * says "not found" for deleted, private and list-only alike).
     */
    val SITE = Site(
        id = VkClient.SITE_ID,
        name = "VK",
        mainWays = listOf(VkLabels.EMBED, VkLabels.TOKEN, VkLabels.VIDEO_GET),
        masterWay = VkLabels.VIDEO_GET,
        mediaHosts = listOf("vkuser.net", "okcdn.ru"),
        slots = List(2) { Kind("18+ video", Expect(rows = true)) } + listOf(
            Kind("18+ video with list", Expect(rows = true)),
            Kind("video", Expect(rows = true)),
            Kind("video", Expect(rows = true)),
            Kind("clip", Expect(rows = true)),
            Kind("removed", Expect(rows = false, reasons = setOf(PRIVATE_OR_UNAVAILABLE, NO_MEDIA_FOUND))),
            Kind(
                "private or sign-in-only",
                Expect(
                    rows = false,
                    reasons = setOf(LOGIN_REQUIRED, PRIVATE_OR_UNAVAILABLE, NO_MEDIA_FOUND),
                    rowsWithSession = true,
                ),
            ),
            Kind("live replay", Expect(rows = true)),
        ),
        mediaPath = { uri -> VkClient.mediaFile(uri.toString()) },
        newLabels = ::VkLabels,
    )
}
