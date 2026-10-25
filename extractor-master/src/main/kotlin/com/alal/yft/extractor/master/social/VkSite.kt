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
     * VK's budget per lookup (§6), **placeholder numbers, A finalizes** from the recon (docs/VK_PLAN.md
     * §13: main's one 15 s deadline, each of main's asks at most 8 s, `video.get` at most 1 MB, the
     * token's answer a few hundred characters). Main asks at most 9 in a lookup (the embed on both
     * hosts and once again after VK's shell, two tokens, two `video.get`, two manifests), V2 at most
     * 2 (one token, one `video.get`) and V's reads at most 8, so 20 requests; Master's own answers
     * are at most 16 KiB + 1 MiB, so 2 MiB; main's 15 s and V2's two asks of at most 8 s, so 30 s.
     */
    val BUDGET = SocialBudget(requests = 20, bytes = 2L * 1024 * 1024, millis = 30_000)

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
