package com.alal.yft.core.model.settings

import java.net.URI

/**
 * Sites that Home shows with their own logo. A site matches by its host or any subdomain of it,
 * so `m.youtube.com` and `youtu.be` are both YouTube.
 */
enum class SiteBrand(val slug: String, private val domains: Set<String>) {
    YOUTUBE("youtube", setOf("youtube.com", "youtu.be", "youtube-nocookie.com")),
    FACEBOOK("facebook", setOf("facebook.com", "fb.watch", "fb.com")),
    TIKTOK("tiktok", setOf("tiktok.com")),
    INSTAGRAM("instagram", setOf("instagram.com")),
    X("x", setOf("x.com", "twitter.com")),
    ;

    companion object {
        /** The brand of [url]'s host, or `null` for any other site or an unreadable address. */
        fun of(url: String): SiteBrand? {
            val host = runCatching { URI(url.trim()).host }.getOrNull()
                ?.lowercase()
                ?.trimEnd('.')
                ?: return null
            return entries.firstOrNull { brand ->
                brand.domains.any { domain -> host == domain || host.endsWith(".$domain") }
            }
        }
    }
}
