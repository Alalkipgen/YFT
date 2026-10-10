package com.alal.yft.extractor.master.recipes

import com.alal.yft.core.model.media.MediaCandidate
import com.alal.yft.extractor.api.json.JsonValue
import com.alal.yft.extractor.api.json.asArrayOrEmpty
import com.alal.yft.extractor.api.json.asBooleanOrNull
import com.alal.yft.extractor.api.json.asLongOrNull
import com.alal.yft.extractor.api.json.asStringOrNull
import com.alal.yft.extractor.api.json.get
import com.alal.yft.extractor.api.json.path
import com.alal.yft.extractor.master.toolkit.CandidateFactory

/**
 * YouTube `streamingData` that the page already delivered: addressed formats only, never
 * n/sig work. Since Phase 1 R2 the standard stack reads only its DRM signal ([drm]); rows stay
 * off (never on YouTube hosts) until R6 moves YouTube into its own module.
 */
internal object YoutubeStreamingRecipe {
    const val FIELD = "streamingData"

    data class Result(val streams: List<MediaCandidate>, val drm: Boolean = false)

    /**
     * The page's own DRM statement, final whatever the playability says. Same signals as main's
     * `YouTubePlayerResponseParser.isDrmProtected` (rule copied, not code): `drmParams`, a DRM
     * session, or any format with DRM families or a DRM track type.
     */
    fun drm(node: JsonValue): Boolean {
        val data = node[FIELD]
        if (data["drmParams"].asStringOrNull != null) return true
        if (node.path("playbackTracking", "drmSessionId").asStringOrNull != null) return true
        if (data["drmFamilies"].asArrayOrEmpty.isNotEmpty()) return true
        val formats = data["formats"].asArrayOrEmpty + data["adaptiveFormats"].asArrayOrEmpty
        return formats.any {
            it["drmFamilies"].asArrayOrEmpty.isNotEmpty() ||
                it["drmTrackType"].asStringOrNull != null
        }
    }

    fun read(node: JsonValue, id: String?, key: String, factory: CandidateFactory): Result {
        if (drm(node)) return Result(emptyList(), drm = true)
        val status = node["playabilityStatus"]["status"].asStringOrNull
        if (status != null && status != "OK") return Result(emptyList())
        if (node["videoDetails"]["isLive"].asBooleanOrNull == true) return Result(emptyList())
        val data = node[FIELD]
        val progressive = data["formats"].asArrayOrEmpty.mapNotNull {
            factory.candidate(it["url"].asStringOrNull, it["mimeType"].asStringOrNull, id,
                node = it, key = key)
        }
        val adaptive = data["adaptiveFormats"].asArrayOrEmpty.mapNotNull {
            factory.candidate(it["url"].asStringOrNull, it["mimeType"].asStringOrNull, id,
                node = it, key = key)
        }
        val audio = adaptive.filter { it.mimeType?.startsWith("audio/mp4") == true }
            .filter { it.codecs.any { codec -> codec.startsWith("mp4a") } }
            .maxByOrNull { it.bitrateBitsPerSecond ?: 0 }
        val streams = (progressive + adaptive).map { stream ->
            val companion = audio?.takeIf {
                stream in adaptive && stream.mimeType?.startsWith("video/mp4") == true &&
                    stream.codecs.any { codec -> codec.startsWith("avc1") }
            }?.let(CandidateFactory::companion)
            stream.copy(
                title = node["videoDetails"]["title"].asStringOrNull,
                durationMillis = node["videoDetails"]["lengthSeconds"].asLongOrNull
                    ?.takeIf { it in 1..Long.MAX_VALUE / 1_000 }?.times(1_000),
                audioCompanion = companion,
            )
        }
        return Result(streams)
    }
}
