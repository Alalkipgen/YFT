package com.alal.yft.extractor.sites.vk

import com.alal.yft.extractor.api.json.BoundedJsonParser
import com.alal.yft.extractor.api.json.JsonValue
import com.alal.yft.extractor.api.json.asLongOrNull
import com.alal.yft.extractor.api.json.asStringOrNull
import com.alal.yft.extractor.api.json.get
import java.util.concurrent.atomic.AtomicReference

/**
 * Path D's anonymous token (VK5): VK's web client token for signed-out viewers, never a user's.
 * It lives in memory only — never stored, logged, shown or put into an address — and is used
 * until 60 s before VK's expiry (24 h at VK5's recon), at most 24 h by the phone's clock.
 */
internal class VkApiToken(val value: String, val usableUntilEpochMs: Long) {
    fun usableAt(nowEpochMs: Long): Boolean = nowEpochMs < usableUntilEpochMs

    override fun toString(): String = "VkApiToken(usableUntilEpochMs=$usableUntilEpochMs)"
}

/** The token one extractor keeps between lookups (the app's adapter is a singleton). */
internal class VkApiTokenCache {
    private val current = AtomicReference<VkApiToken?>(null)

    fun usable(nowEpochMs: Long): VkApiToken? = current.get()?.takeIf { it.usableAt(nowEpochMs) }

    /** Keeps [token] only while it is usable; a token without a known life serves one lookup. */
    fun keep(token: VkApiToken, nowEpochMs: Long) {
        if (token.usableAt(nowEpochMs)) current.set(token)
    }

    /** Drops [token] after VK refused it (API error 5); a newer token stays. */
    fun drop(token: VkApiToken) {
        current.compareAndSet(token, null)
    }
}

/** login.vk.ru's `get_anonym_token` answer: `{"type":"okay","data":{…token, expiry…}}`. */
internal object VkApiTokenParser {
    const val SLACK_MS: Long = 60_000
    const val MAX_LIFE_MS: Long = 24L * 3600 * 1000
    private const val MIN_TOKEN_CHARS = 16
    private const val MAX_TOKEN_CHARS = 2_048
    private const val TOKEN_KEY = "access_token"

    /** The token, or null for any other answer (VK's error, a changed shape, a bad value). */
    fun parse(json: String, nowEpochMs: Long): VkApiToken? {
        val root = BoundedJsonParser.parse(json) as? JsonValue.Object ?: return null
        if (root["type"].asStringOrNull != "okay") return null
        val data = root["data"]
        val value = data[TOKEN_KEY].asStringOrNull
            ?.takeIf { it.length in MIN_TOKEN_CHARS..MAX_TOKEN_CHARS }
            ?.takeIf { token -> token.all { it in '\u0021'..'\u007e' } }
            ?: return null
        val expiresAt = epochMillisOf(data["expires"].asLongOrNull, nowEpochMs)
            ?: epochMillisOf(data["expired_at"].asLongOrNull, nowEpochMs)
            ?: nowEpochMs
        val until = minOf(expiresAt, nowEpochMs + MAX_LIFE_MS) - SLACK_MS
        return VkApiToken(value, until)
    }

    /** VK's epoch seconds (10 digits), epoch ms (13 digits) or, below that, seconds from now. */
    private fun epochMillisOf(value: Long?, nowEpochMs: Long): Long? = when {
        value == null || value <= 0 -> null
        value >= 1_000_000_000_000L -> value
        value >= 1_000_000_000L -> value * 1_000
        else -> nowEpochMs + value * 1_000
    }
}
