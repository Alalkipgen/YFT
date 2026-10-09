package com.alal.yft.extractor.master.android

import com.alal.yft.extractor.api.json.BoundedJsonParser
import com.alal.yft.extractor.api.json.JsonValue
import com.alal.yft.extractor.api.json.asArrayOrEmpty
import com.alal.yft.extractor.api.json.asBooleanOrNull
import com.alal.yft.extractor.api.json.asDoubleOrNull
import com.alal.yft.extractor.api.json.asLongOrNull
import com.alal.yft.extractor.api.json.asStringOrNull
import com.alal.yft.extractor.api.json.get

internal data class CaptureFrame(
    val pageUrl: String,
    val generation: Long,
    val html: String?,
    val payloads: List<String>,
    val requests: List<FrameRequest>,
    val player: FramePlayer?,
    val protected: Boolean,
) {
    override fun toString(): String =
        "CaptureFrame(generation=$generation, requests=${requests.size}, protected=$protected)"
}

internal data class FrameRequest(val url: String, val mime: String?, val preview: Boolean) {
    override fun toString(): String = "FrameRequest(preview=$preview, address=[omitted])"
}

internal data class FramePlayer(
    val key: String,
    val url: String?,
    val time: Double,
    val ready: Long,
    val paused: Boolean,
    val visible: Boolean,
    val duration: Double? = null,
    val width: Int? = null,
    val height: Int? = null,
) {
    override fun toString(): String = "FramePlayer(ready=$ready, paused=$paused)"
}

internal object CaptureFrameReader {
    const val MAX_CHARS = 256 * 1024
    const val MAX_BODY_CHARS = 64 * 1024
    const val MAX_URL_CHARS = 8 * 1024

    /** evaluateJavascript encodes its returned JSON string once more. Never use unbounded JSON. */
    fun read(encoded: String?): CaptureFrame? {
        if (encoded == null || encoded.length > MAX_CHARS * 6 + 2) return null
        val outer = BoundedJsonParser.parse(encoded, maxDepth = 24, maxNodes = 8_000)
            ?: return null
        val root = if (outer is JsonValue.Text) {
            if (outer.value.length > MAX_CHARS) return null
            BoundedJsonParser.parse(outer.value, maxDepth = 24, maxNodes = 8_000)
        } else {
            if (encoded.length > MAX_CHARS) return null
            outer
        }
        if (root !is JsonValue.Object) return null
        val page = root["pageUrl"].asStringOrNull?.takeIf { it.length <= MAX_URL_CHARS }
            ?: return null
        val generation = root["generation"].asLongOrNull?.takeIf { it >= 0 } ?: return null
        val player = root["player"]?.let { value ->
            val key = value["key"].asStringOrNull?.takeIf { it.length <= 128 }
            val time = value["time"].asDoubleOrNull?.takeIf { it >= 0 }
            if (key == null || time == null) null else FramePlayer(
                key = key,
                url = value["url"].asStringOrNull?.takeIf { it.length <= MAX_URL_CHARS },
                time = time,
                ready = value["ready"].asLongOrNull ?: 0,
                paused = value["paused"].asBooleanOrNull != false,
                visible = value["visible"].asBooleanOrNull == true,
                duration = value["duration"].asDoubleOrNull?.takeIf {
                    it.isFinite() && it > 0 && it <= 172_800
                },
                width = value["width"].asLongOrNull?.takeIf { it in 1..16_384 }?.toInt(),
                height = value["height"].asLongOrNull?.takeIf { it in 1..16_384 }?.toInt(),
            )
        }
        return CaptureFrame(
            pageUrl = page,
            generation = generation,
            html = root["html"].asStringOrNull?.takeIf { it.length <= MAX_BODY_CHARS },
            payloads = root["payloads"].asArrayOrEmpty.take(16).mapNotNull {
                it.asStringOrNull?.takeIf { text -> text.length <= MAX_BODY_CHARS }
            },
            requests = root["requests"].asArrayOrEmpty.take(64).mapNotNull { value ->
                val url = value["url"].asStringOrNull?.takeIf { it.length <= MAX_URL_CHARS }
                    ?: return@mapNotNull null
                FrameRequest(
                    url,
                    value["mime"].asStringOrNull?.takeIf { it.length <= 128 },
                    value["preview"].asBooleanOrNull == true,
                )
            },
            player = player,
            protected = root["protected"].asBooleanOrNull == true,
        )
    }
}