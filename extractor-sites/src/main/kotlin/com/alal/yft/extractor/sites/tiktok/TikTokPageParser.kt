package com.alal.yft.extractor.sites.tiktok

import com.alal.yft.extractor.api.SiteExtractionFailure
import com.alal.yft.extractor.api.json.BoundedJsonParser
import com.alal.yft.extractor.api.json.JsonValue
import com.alal.yft.extractor.api.json.asArrayOrEmpty
import com.alal.yft.extractor.api.json.asBooleanOrNull
import com.alal.yft.extractor.api.json.asLongOrNull
import com.alal.yft.extractor.api.json.asStringOrNull
import com.alal.yft.extractor.api.json.get
import com.alal.yft.extractor.api.json.path
import java.util.Locale

/** One playable rendition TikTok exposed for a post. */
internal data class TikTokRendition(
    val url: String,
    val label: String?,
    val bitrateBitsPerSecond: Long?,
    val sizeBytes: Long?,
)

/** Non-sensitive description of a TikTok post plus its renditions. */
internal data class TikTokPost(
    val videoId: String,
    val authorHandle: String?,
    val title: String?,
    val thumbnailUrl: String?,
    val durationMillis: Long?,
    val renditions: List<TikTokRendition>,
)

internal sealed interface TikTokParseResult {
    data class Success(val post: TikTokPost) : TikTokParseResult

    data class Failure(val reason: SiteExtractionFailure) : TikTokParseResult
}

/**
 * Reads the JSON TikTok embeds in its own page.
 *
 * Both the current `__UNIVERSAL_DATA_FOR_REHYDRATION__` payload and the older `SIGI_STATE` payload
 * are supported, because a site can serve either to different clients. When neither payload is
 * present or its shape no longer matches, the parser reports
 * [SiteExtractionFailure.RESPONSE_CHANGED] instead of scraping an arbitrary URL out of the HTML.
 */
internal object TikTokPageParser {
    private const val UNIVERSAL_SCRIPT_ID = "__UNIVERSAL_DATA_FOR_REHYDRATION__"
    private const val SIGI_SCRIPT_ID = "SIGI_STATE"
    private const val VIDEO_DETAIL_KEY = "webapp.video-detail"

    /** TikTok status codes that describe an access outcome rather than a transport error. */
    private val STATUS_FAILURES = mapOf(
        10101L to SiteExtractionFailure.PRIVATE_OR_UNAVAILABLE,
        10102L to SiteExtractionFailure.PRIVATE_OR_UNAVAILABLE,
        10204L to SiteExtractionFailure.PRIVATE_OR_UNAVAILABLE,
        10216L to SiteExtractionFailure.PRIVATE_OR_UNAVAILABLE,
        10217L to SiteExtractionFailure.PRIVATE_OR_UNAVAILABLE,
        10218L to SiteExtractionFailure.PRIVATE_OR_UNAVAILABLE,
        10221L to SiteExtractionFailure.PRIVATE_OR_UNAVAILABLE,
        10222L to SiteExtractionFailure.LOGIN_REQUIRED,
        10223L to SiteExtractionFailure.LOGIN_REQUIRED,
        10231L to SiteExtractionFailure.GEO_RESTRICTED,
    )

    fun parse(html: String, expectedVideoId: String?): TikTokParseResult {
        val item = universalItem(html)
            ?: sigiItem(html, expectedVideoId)
            ?: return universalStatusFailure(html)
                ?: TikTokParseResult.Failure(SiteExtractionFailure.RESPONSE_CHANGED)
        return post(item)
    }

    private fun universalItem(html: String): JsonValue? {
        val root = BoundedJsonParser.parse(scriptJson(html, UNIVERSAL_SCRIPT_ID) ?: return null)
        val detail = root.path("__DEFAULT_SCOPE__", VIDEO_DETAIL_KEY) ?: return null
        if (statusFailure(detail) != null) return null
        return detail.path("itemInfo", "itemStruct")
    }

    private fun universalStatusFailure(html: String): TikTokParseResult.Failure? {
        val root = BoundedJsonParser.parse(scriptJson(html, UNIVERSAL_SCRIPT_ID) ?: return null)
        val detail = root.path("__DEFAULT_SCOPE__", VIDEO_DETAIL_KEY) ?: return null
        return statusFailure(detail)?.let(TikTokParseResult::Failure)
    }

    private fun statusFailure(detail: JsonValue?): SiteExtractionFailure? {
        val status = detail["statusCode"].asLongOrNull
            ?: detail["statusCodeV2"].asLongOrNull
            ?: return null
        if (status == 0L) return null
        return STATUS_FAILURES[status] ?: SiteExtractionFailure.PRIVATE_OR_UNAVAILABLE
    }

    private fun sigiItem(html: String, expectedVideoId: String?): JsonValue? {
        val root = BoundedJsonParser.parse(scriptJson(html, SIGI_SCRIPT_ID) ?: return null)
        val module = root["ItemModule"] as? JsonValue.Object ?: return null
        val entry = expectedVideoId?.let { module.entries[it] }
            ?: module.entries.values.firstOrNull()
        return entry
    }

    private fun post(item: JsonValue?): TikTokParseResult {
        val videoId = item["id"].asStringOrNull
            ?: return TikTokParseResult.Failure(SiteExtractionFailure.RESPONSE_CHANGED)
        if (item["imagePost"] != null && item["imagePost"] !is JsonValue.Null) {
            return TikTokParseResult.Failure(SiteExtractionFailure.NO_MEDIA_FOUND)
        }
        if (item.path("video", "isDrm").asBooleanOrNull == true) {
            return TikTokParseResult.Failure(SiteExtractionFailure.DRM_PROTECTED)
        }
        val video = item["video"]
            ?: return TikTokParseResult.Failure(SiteExtractionFailure.RESPONSE_CHANGED)

        val renditions = renditions(video)
        if (renditions.isEmpty()) {
            return TikTokParseResult.Failure(SiteExtractionFailure.NO_MEDIA_FOUND)
        }

        return TikTokParseResult.Success(
            TikTokPost(
                videoId = videoId,
                authorHandle = item.path("author", "uniqueId").asStringOrNull
                    ?: item["author"].asStringOrNull,
                title = item["desc"].asStringOrNull,
                thumbnailUrl = listOf("cover", "originCover", "dynamicCover")
                    .firstNotNullOfOrNull { key -> video[key].asStringOrNull?.httpsOrNull() },
                durationMillis = video["duration"].asLongOrNull
                    ?.takeIf { it > 0 }
                    ?.let { seconds -> seconds * 1_000 },
                renditions = renditions,
            ),
        )
    }

    /**
     * Collects renditions in a stable order: the explicit bitrate ladder first, then the plain
     * play address. Only HTTPS URLs survive, and duplicates collapse on first occurrence so the
     * highest-quality entry keeps its metadata.
     */
    private fun renditions(video: JsonValue?): List<TikTokRendition> {
        val collected = LinkedHashMap<String, TikTokRendition>()

        video["bitrateInfo"].asArrayOrEmpty.forEach { entry ->
            val url = entry.path("PlayAddr", "UrlList").asArrayOrEmpty
                .firstNotNullOfOrNull { it.asStringOrNull?.httpsOrNull() }
                ?: return@forEach
            collected.putIfAbsent(
                url,
                TikTokRendition(
                    url = url,
                    label = entry["GearName"].asStringOrNull?.readableGear(),
                    bitrateBitsPerSecond = entry["Bitrate"].asLongOrNull?.takeIf { it > 0 },
                    sizeBytes = entry.path("PlayAddr", "DataSize").asLongOrNull
                        ?: entry["DataSize"].asLongOrNull,
                ),
            )
        }

        listOf("playAddr", "downloadAddr").forEach { key ->
            val url = video[key].asStringOrNull?.httpsOrNull() ?: return@forEach
            collected.putIfAbsent(
                url,
                TikTokRendition(
                    url = url,
                    label = if (key == "downloadAddr") "Download" else null,
                    bitrateBitsPerSecond = video["bitrate"].asLongOrNull?.takeIf { it > 0 },
                    sizeBytes = null,
                ),
            )
        }

        return collected.values.toList()
    }

    /**
     * Extracts the JSON body of a `<script id="...">` block.
     *
     * The search is anchored on the script ID and stops at the first closing tag, so no other part
     * of the page can be mistaken for the payload.
     */
    private fun scriptJson(html: String, scriptId: String): String? {
        val idIndex = html.indexOf("\"$scriptId\"").takeIf { it >= 0 }
            ?: html.indexOf("'$scriptId'").takeIf { it >= 0 }
            ?: return null
        val tagStart = html.lastIndexOf("<script", idIndex).takeIf { it >= 0 } ?: return null
        val bodyStart = html.indexOf('>', idIndex).takeIf { it >= 0 } ?: return null
        if (bodyStart < tagStart) return null
        val bodyEnd = html.indexOf("</script", bodyStart).takeIf { it >= 0 } ?: return null
        return html.substring(bodyStart + 1, bodyEnd).trim().takeIf(String::isNotEmpty)
    }

    private fun String.httpsOrNull(): String? =
        takeIf { it.startsWith("https://", ignoreCase = true) }

    /** Turns gear names such as `normal_720_0` into a short, honest label. */
    private fun String.readableGear(): String? {
        val height = Regex("(\\d{3,4})").find(this)?.groupValues?.get(1)?.toIntOrNull()
        val lowered = lowercase(Locale.US)
        val tier = when {
            lowered.startsWith("lower") -> "Low"
            lowered.startsWith("low") -> "Low"
            lowered.startsWith("normal") -> "Standard"
            lowered.startsWith("adapt") -> "Adaptive"
            else -> null
        }
        return when {
            height != null && tier != null -> "$tier ${height}p"
            height != null -> "${height}p"
            tier != null -> tier
            else -> null
        }
    }
}
