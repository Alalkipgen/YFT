package com.alal.yft.extractor.sites.tiktok

import com.alal.yft.extractor.api.ExtractorHttpClient
import com.alal.yft.extractor.api.ExtractorHttpResult
import com.alal.yft.extractor.api.json.BoundedJsonParser
import com.alal.yft.extractor.api.json.JsonReadResult
import com.alal.yft.extractor.api.json.JsonValue
import com.alal.yft.extractor.api.json.asLongOrNull
import com.alal.yft.extractor.api.json.asStringOrNull
import com.alal.yft.extractor.api.json.get
import java.net.URLEncoder
import java.util.Locale

/**
 * P46 (owner's choice B): a public TikTok download service, the last way for a post TikTok's
 * own pages do not give YFT (the phone page shows "This page isn't available — use the app"
 * for some public posts). Only the post's public address is sent, with a plain Chrome agent:
 * never a cookie. Its answer is one post's watermark-free files (HD and normal) and the watermarked
 * one, read like a page answer and checked like one before they become rows.
 */
internal class TikTokDownloadService(
    private val http: ExtractorHttpClient,
    private val endpoint: String = ENDPOINT,
) {
    sealed interface Answer {
        class Found(val post: TikTokPost) : Answer

        /** [note] is a short non-sensitive line for Details. */
        class NotFound(val note: String) : Answer
    }

    /** [userAgent]: a plain Chrome agent, never the user's own. */
    suspend fun lookUp(postUrl: String, expectedVideoId: String?, userAgent: String?): Answer {
        val url = endpoint + "?url=" + URLEncoder.encode(postUrl, Charsets.UTF_8.name()) + "&hd=1"
        val headers = buildMap {
            put("Accept", "application/json")
            userAgent?.takeIf(String::isNotBlank)?.let { put("User-Agent", it) }
        }
        val response = when (val result = http.get(url, headers, MAX_ANSWER_BYTES)) {
            is ExtractorHttpResult.Failure -> return Answer.NotFound(
                result.statusCode?.let { "HTTP $it" } ?: result.reason.name.lowercase(Locale.US),
            )

            is ExtractorHttpResult.Success -> result
        }
        val read = BoundedJsonParser.read(response.body.trim())
        val root = (read as? JsonReadResult.Read)?.value ?: return Answer.NotFound("not JSON")
        val code = root["code"].asLongOrNull
        if (code != 0L) return Answer.NotFound("answer code ${code ?: "missing"}")
        val data = root["data"] as? JsonValue.Object ?: return Answer.NotFound("no data")
        val id = data["id"].asStringOrNull ?: return Answer.NotFound("no post id")
        if (expectedVideoId != null && id != expectedVideoId) return Answer.NotFound("other post")
        return parse(data, id)?.let(Answer::Found) ?: Answer.NotFound("no file")
    }

    private fun parse(data: JsonValue, id: String): TikTokPost? {
        val hd = address(data["hdplay"])
        val normal = address(data["play"])?.takeIf { it != hd }
        val qualities = listOfNotNull(
            hd?.let { quality(it, data["hd_size"].asLongOrNull, HD_NAME) },
            normal?.let { quality(it, data["size"].asLongOrNull, null) },
        )
        val watermarked = address(data["wmplay"])?.let {
            TikTokQuality(
                heightLabel = null,
                codec = TikTokCodec.UNKNOWN,
                addresses = listOf(it),
                bitrateBitsPerSecond = null,
                sizeBytes = data["wm_size"].asLongOrNull?.takeIf { size -> size > 0 },
                width = null,
                height = null,
                source = TikTokQualitySource.DOWNLOAD_ADDRESS,
            )
        }
        if (qualities.isEmpty() && watermarked == null) return null
        val author = data["author"]
        return TikTokPost(
            videoId = id,
            authorHandle = author["unique_id"].asStringOrNull ?: author["uniqueId"].asStringOrNull,
            title = data["title"].asStringOrNull,
            thumbnailUrl = address(data["cover"]) ?: address(data["origin_cover"]),
            durationMillis = data["duration"].asLongOrNull?.takeIf { it > 0 }?.let { it * 1_000 },
            qualities = qualities,
            watermarked = watermarked,
            hasPlayAddress = normal != null || hd != null,
        )
    }

    private fun quality(address: String, size: Long?, name: String?) = TikTokQuality(
        heightLabel = null,
        codec = TikTokCodec.UNKNOWN,
        addresses = listOf(address),
        bitrateBitsPerSecond = null,
        sizeBytes = size?.takeIf { it > 0 },
        width = null,
        height = null,
        source = TikTokQualitySource.PLAY_ADDRESS,
        name = name,
    )

    /** An https address; the service's own paths are made absolute on its host. */
    private fun address(value: JsonValue?): String? {
        val text = value.asStringOrNull?.trim()?.takeIf(String::isNotEmpty) ?: return null
        return when {
            text.startsWith("https://", ignoreCase = true) -> text
            text.startsWith("/") && !text.startsWith("//") -> SERVICE_ORIGIN + text
            else -> null
        }
    }

    companion object {
        const val SERVICE_ORIGIN = "https://www.tikwm.com"
        const val ENDPOINT = "$SERVICE_ORIGIN/api/"
        const val HD_NAME = "HD"
        const val LABEL = "download service"
        private const val MAX_ANSWER_BYTES = 256L * 1024
    }
}
