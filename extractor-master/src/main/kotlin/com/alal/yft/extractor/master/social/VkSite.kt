package com.alal.yft.extractor.master.social

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
}
