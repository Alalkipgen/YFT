package com.alal.yft.core.browser.detection

import com.alal.yft.core.model.media.MediaCandidate
import com.alal.yft.core.model.media.MediaKind
import com.alal.yft.core.model.media.PageMediaRole
import com.alal.yft.core.model.media.PageVideoFacts
import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener

class DomProbeResultParser(
    private val maxEntries: Int = 100,
) {
    init {
        require(maxEntries > 0)
    }

    fun parse(
        pageUrl: String,
        javascriptResult: String?,
        observedAtEpochMs: Long,
    ): List<MediaCandidate> {
        val array = array(javascriptResult) ?: return emptyList()
        return buildList {
            repeat(minOf(array.length(), maxEntries)) { index ->
                val item = array.optJSONObject(index) ?: return@repeat
                if (item.optNullableString("element") == FACTS) {
                    addAll(playerSetup(pageUrl, item, observedAtEpochMs))
                    return@repeat
                }
                val mediaUrl = item.optNullableString("url") ?: return@repeat
                val seconds = item.optDouble("duration", Double.NaN)
                val observation = DomMediaObservation(
                    pageUrl = pageUrl,
                    mediaUrl = mediaUrl,
                    mimeType = item.optNullableString("type"),
                    title = item.optNullableString("title"),
                    thumbnailUrl = item.optNullableString("poster"),
                    durationMillis = seconds
                        .takeIf { it.isFinite() && it > 0 }
                        ?.times(1000)
                        ?.toLong(),
                    observedAtEpochMs = observedAtEpochMs,
                    pageRole = PageMediaRole.PREVIEW.takeIf { item.looksLikePreview() },
                )
                val candidate = BrowserObservationMapper.fromDom(observation) ?: return@repeat
                // P24: an Open Graph or Twitter stream that is a media file is the page's video.
                val named = item.optNullableString("element") == "meta" &&
                    candidate.kind != MediaKind.UNKNOWN && candidate.pageRole == null
                add(if (named) candidate.copy(pageRole = PageMediaRole.MAIN) else candidate)
            }
        }
    }

    /**
     * P28: what the page states about its video ([PageFactsReader]), from the probe's `facts`
     * entry; null when it states nothing or the probe sent none.
     */
    fun facts(pageUrl: String, javascriptResult: String?): PageVideoFacts? {
        val array = array(javascriptResult) ?: return null
        val item = (0 until minOf(array.length(), maxEntries)).asSequence()
            .mapNotNull { array.optJSONObject(it) }
            .firstOrNull { it.optNullableString("element") == FACTS }
            ?: return null
        val parts = item.optJSONObject("facts") ?: return null
        val meta = buildMap {
            val tags = parts.optJSONObject("meta") ?: JSONObject()
            tags.keys().asSequence().take(MAX_META).forEach { key ->
                val value = tags.optString(key, "").trim().take(MAX_META_LENGTH)
                if (value.isNotEmpty()) put(key.trim().lowercase(), value)
            }
        }
        val blocks = parts.optJSONArray("jsonLd") ?: JSONArray()
        val jsonLd = (0 until minOf(blocks.length(), MAX_JSON_LD)).mapNotNull { index ->
            blocks.optString(index, "").takeIf { it.isNotBlank() && it.length <= MAX_JSON_LENGTH }
        }
        val facts = PageFactsReader.fromParts(
            meta = meta,
            jsonLd = jsonLd,
            documentTitle = parts.optNullableString("documentTitle"),
            pageUrl = pageUrl,
        )
        return facts.takeUnless { it.isEmpty }
    }

    /** P28: the files of the page's player setups, each setup's qualities one video. */
    private fun playerSetup(
        pageUrl: String,
        item: JSONObject,
        observedAtEpochMs: Long,
    ): List<MediaCandidate> {
        val scripts = item.optJSONArray("scripts") ?: return emptyList()
        var setups = 0
        return (0 until minOf(scripts.length(), MAX_SCRIPTS)).flatMap { index ->
            val script = scripts.optString(index, "").take(MAX_SCRIPT_LENGTH)
            val sources = PlayerSetupScanner.sources(script, pageUrl)
            if (sources.isEmpty()) return@flatMap emptyList()
            val key = "player-${setups++}"
            sources.mapNotNull { source ->
                BrowserObservationMapper.fromDom(
                    DomMediaObservation(
                        pageUrl = pageUrl,
                        mediaUrl = source.url,
                        mimeType = source.mimeType,
                        title = null,
                        thumbnailUrl = null,
                        durationMillis = null,
                        observedAtEpochMs = observedAtEpochMs,
                        pageRole = PageMediaRole.MAIN,
                    ),
                )?.copy(height = source.height, pageVideoKey = key)
            }
        }
    }

    private fun array(javascriptResult: String?): JSONArray? {
        val json = decodeJavascriptResult(javascriptResult) ?: return null
        return runCatching { JSONArray(json) }.getOrNull()
    }

    private fun decodeJavascriptResult(result: String?): String? {
        val trimmed = result?.trim()?.takeIf { it.isNotEmpty() && it != "null" } ?: return null
        return if (trimmed.startsWith('"')) {
            runCatching { JSONTokener(trimmed).nextValue() as? String }.getOrNull()
        } else {
            trimmed
        }
    }

    private fun JSONObject.optNullableString(name: String): String? =
        optString(name, "").trim().takeIf(String::isNotEmpty)

    /** P24: a muted loop, or a clip in a link to another page or a thumbnail box. */
    private fun JSONObject.looksLikePreview(): Boolean =
        optBoolean("muted", false) && optBoolean("loop", false) ||
            optBoolean("thumbnail", false)

    private companion object {
        /** P28: the probe's entry with the page's facts and player scripts. */
        const val FACTS = "facts"
        const val MAX_META = 20
        const val MAX_META_LENGTH = 2_000
        const val MAX_JSON_LD = 5
        const val MAX_JSON_LENGTH = 30_000
        const val MAX_SCRIPTS = 40
        const val MAX_SCRIPT_LENGTH = 100_000
    }
}
