package com.alal.yft.core.download

import com.alal.yft.core.model.download.AudioVideoMuxDownloadPlan
import com.alal.yft.core.model.download.DashDownloadPlan
import com.alal.yft.core.model.download.DirectDownloadPlan
import com.alal.yft.core.model.download.DownloadFailure
import com.alal.yft.core.model.download.DownloadFailureReason
import com.alal.yft.core.model.download.DownloadFailureStage
import com.alal.yft.core.model.download.DownloadPlan
import com.alal.yft.core.model.download.HlsDownloadPlan
import java.net.URI
import java.net.URLDecoder

/**
 * F6 (debug spike, route B trial): what a failed YouTube media link was, for the download's
 * Failure details, for example "WEB itag 136+140 · pot no · sig yes · ends 352 min (app 352) ·
 * 0 KB · 3 s".
 *
 * That is the YouTube client the link was made for (its `c` value), the stream numbers (the
 * video's first), whether the link carries a proof-of-origin token (`pot`) and a solved
 * signature (`sig` or `signature`), the minutes the link's own `expire` time and the app's plan
 * had left when the download failed, the bytes downloaded and how old the download was. Names
 * and numbers only: never the link, its host or a value that could be a secret. Null for links
 * of other servers.
 */
internal object GoogleVideoLinkFacts {
    fun describe(
        plan: DownloadPlan,
        nowEpochMs: Long,
        downloadedBytes: Long,
        ageMs: Long,
    ): String? {
        val links = urls(plan).map { url -> parameters(url) ?: return null }
        val client = links.joined { link -> link["c"]?.takeIf(CLIENT::matches) ?: UNKNOWN }
        val itags = links.joinToString("+") { link ->
            link["itag"]?.takeIf(ITAG::matches) ?: UNKNOWN
        }
        val pot = links.joined { link -> yesNo("pot" in link) }
        val sig = links.joined { link -> yesNo("sig" in link || "signature" in link) }
        val linkEnds = links.mapNotNull { link -> link["expire"]?.toLongOrNull() }
            .minOrNull()
            ?.let { expire -> minutesLeft(expire * MILLIS_PER_SECOND, nowEpochMs).toString() }
            ?: UNKNOWN
        val appEnds = plan.expiresAtEpochMs
            ?.let { expires -> minutesLeft(expires, nowEpochMs).toString() }
            ?: "none"
        return "$client itag $itags · pot $pot · sig $sig · ends $linkEnds min (app $appEnds) · " +
            "${downloadedBytes.coerceAtLeast(0) / BYTES_PER_KB} KB · ${age(ageMs)}"
    }

    /**
     * Item 7 Track B1: true when [failure] is YouTube's media host refusing [plan]'s link while
     * it still had time left — HTTP 403 while connecting ([DownloadFailureReason.ACCESS_DENIED])
     * with at least a minute left on each link's own `expire` and on the app's end for it, when
     * known. Such a link did not expire: YouTube refused this session's download, so "Link
     * expired" and "reopen the page" would be wrong (D7 runs 13 / 13b: `ends 359 min (app 358)`).
     * False for other servers, a link without its `expire`, and every other failure.
     */
    fun refusedWithTimeLeft(plan: DownloadPlan, failure: DownloadFailure, nowEpochMs: Long): Boolean {
        if (failure.reason != DownloadFailureReason.ACCESS_DENIED ||
            failure.httpStatusCode != HTTP_FORBIDDEN ||
            failure.stage != DownloadFailureStage.CONNECT
        ) {
            return false
        }
        val ends = urls(plan).map { url ->
            parameters(url)?.get("expire")?.toLongOrNull() ?: return false
        }
        val linkLeft = ends.all { expire ->
            expire >= (nowEpochMs + MIN_TIME_LEFT_MS).ceilDiv(MILLIS_PER_SECOND)
        }
        val appLeft = plan.expiresAtEpochMs?.let { it - nowEpochMs >= MIN_TIME_LEFT_MS } ?: true
        return linkLeft && appLeft
    }

    private fun urls(plan: DownloadPlan): List<String> = when (plan) {
        is DirectDownloadPlan -> listOf(plan.sourceUrl)
        is AudioVideoMuxDownloadPlan -> listOf(plan.video.manifestUrl, plan.audio.manifestUrl)
        is DashDownloadPlan -> listOf(plan.manifestUrl)
        is HlsDownloadPlan -> listOf(plan.playlistUrl)
    }

    private fun Long.ceilDiv(other: Long): Long = -Math.floorDiv(-this, other)

    /** The link's query names and values, or null when it is not a YouTube media link. */
    private fun parameters(url: String): Map<String, String>? {
        val uri = runCatching { URI(url) }.getOrNull() ?: return null
        val host = uri.host?.lowercase() ?: return null
        if (host != MEDIA_HOST && !host.endsWith(".$MEDIA_HOST")) return null
        val query = uri.rawQuery ?: return emptyMap()
        return query.split('&')
            .filter(String::isNotEmpty)
            .associate { pair ->
                val name = pair.substringBefore('=')
                val value = pair.substringAfter('=', missingDelimiterValue = "")
                decode(name) to decode(value)
            }
    }

    private fun decode(text: String): String =
        runCatching { URLDecoder.decode(text, "UTF-8") }.getOrDefault(text)

    private fun List<Map<String, String>>.joined(fact: (Map<String, String>) -> String): String =
        map(fact).distinct().joinToString("+")

    private fun yesNo(value: Boolean): String = if (value) "yes" else "no"

    private fun minutesLeft(atEpochMs: Long, nowEpochMs: Long): Long =
        (atEpochMs - nowEpochMs).floorDiv(MILLIS_PER_MINUTE)

    private fun age(ageMs: Long): String {
        val seconds = ageMs.coerceAtLeast(0) / MILLIS_PER_SECOND
        return if (seconds < SECONDS_SHOWN) "$seconds s" else "${seconds / SECONDS_PER_MINUTE} min"
    }

    private const val MEDIA_HOST = "googlevideo.com"
    private const val HTTP_FORBIDDEN = 403
    private const val MIN_TIME_LEFT_MS = 60_000L
    private const val UNKNOWN = "?"
    private const val BYTES_PER_KB = 1_024L
    private const val MILLIS_PER_SECOND = 1_000L
    private const val MILLIS_PER_MINUTE = 60_000L
    private const val SECONDS_SHOWN = 600L
    private const val SECONDS_PER_MINUTE = 60L
    private val CLIENT = Regex("[A-Z][A-Z_]{0,31}")
    private val ITAG = Regex("\\d{1,4}")
}
