package com.alal.yft.core.browser.detection

import com.alal.yft.core.model.media.MediaCandidate
import org.json.JSONArray
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
        val json = decodeJavascriptResult(javascriptResult) ?: return emptyList()
        val array = runCatching { JSONArray(json) }.getOrNull() ?: return emptyList()
        return buildList {
            repeat(minOf(array.length(), maxEntries)) { index ->
                val item = array.optJSONObject(index) ?: return@repeat
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
                )
                BrowserObservationMapper.fromDom(observation)?.let(::add)
            }
        }
    }

    private fun decodeJavascriptResult(result: String?): String? {
        val trimmed = result?.trim()?.takeIf { it.isNotEmpty() && it != "null" } ?: return null
        return if (trimmed.startsWith('"')) {
            runCatching { JSONTokener(trimmed).nextValue() as? String }.getOrNull()
        } else {
            trimmed
        }
    }

    private fun org.json.JSONObject.optNullableString(name: String): String? =
        optString(name, "").trim().takeIf(String::isNotEmpty)
}
