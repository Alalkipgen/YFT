package com.alal.yft.extractor.master.modules.youtube.ladder

import com.alal.yft.extractor.api.json.BoundedJsonParser
import com.alal.yft.extractor.api.json.JsonValue
import com.alal.yft.extractor.api.json.asArrayOrEmpty
import com.alal.yft.extractor.api.json.asStringOrNull
import com.alal.yft.extractor.api.json.get

/**
 * Phase 1.3 D0: the public version sources the census measures (MASTER_KEY_PHASE1_3_PLAN.md §4 D0
 * table, guardrail 5). Each is read at most once per run, ≤ [MAX_SOURCE_BYTES], with no token and
 * no cookie, as data only: one pattern-matched value, never code. A host Master does not use yet
 * (Apple, Google Play, GitHub raw) is read by the census tool only; using one in lookups needs
 * the owner's yes (§9 item 3).
 */
internal object CensusSources {
    const val MAX_SOURCE_BYTES: Long = 64L * 1024

    const val YT_DLP_URL =
        "https://raw.githubusercontent.com/yt-dlp/yt-dlp/master/yt_dlp/extractor/youtube/_base.py"
    const val YOUTUBE_JS_URL =
        "https://raw.githubusercontent.com/LuanRT/YouTube.js/main/src/utils/Constants.ts"

    /** The App Store's public lookup for YouTube's app (its visionOS build, if listed). */
    const val APP_STORE_URL = "https://itunes.apple.com/lookup?bundleId=com.google.ios.youtube"
    const val PLAY_STORE_URL =
        "https://play.google.com/store/apps/details?id=com.google.android.youtube&hl=en_US&gl=US"

    /** The strict version pattern per identity (guardrail 5). */
    val VISION_OS_VERSION = Regex("^\\d{1,2}\\.\\d{2}$")
    val ANDROID_VERSION = Regex("^\\d{2}\\.\\d{2}\\.\\d{2,3}$")
    val WEB_VERSION = Regex("^2\\.\\d{8}\\.\\d{2}\\.\\d{2}$")

    private val YT_DLP_VISION_OS =
        Regex("'clientName':\\s*'VISIONOS',[^}]{0,400}?'clientVersion':\\s*'([^'\\n]{1,40})'")
    private val YT_DLP_ANDROID =
        Regex("'clientName':\\s*'ANDROID',[^}]{0,400}?'clientVersion':\\s*'([^'\\n]{1,40})'")
    private val YOUTUBE_JS_VISION_OS = Regex("NAME:\\s*'VISIONOS',\\s*VERSION:\\s*'([^'\\n]{1,40})'")
    private val YOUTUBE_JS_ANDROID = Regex("NAME:\\s*'ANDROID',\\s*VERSION:\\s*'([^'\\n]{1,40})'")
    private val PLAY_STORE_VERSION = Regex("\\[\\[\\[\"(\\d{1,3}\\.\\d{1,3}\\.\\d{1,4})\"]]")
    private val VISION_DEVICE = Regex("\"AppleVision[A-Za-z0-9-]{0,40}\"")

    class Reader(val identity: String, val values: (String) -> List<String>)

    class Document(val id: String, val url: String, val readers: List<Reader>)

    val DOCUMENTS: List<Document> = listOf(
        Document(
            "ytdlp",
            YT_DLP_URL,
            listOf(Reader("visionos", matches(YT_DLP_VISION_OS)), Reader("android", matches(YT_DLP_ANDROID))),
        ),
        Document(
            "youtubejs",
            YOUTUBE_JS_URL,
            listOf(
                Reader("visionos", matches(YOUTUBE_JS_VISION_OS)),
                Reader("android", matches(YOUTUBE_JS_ANDROID)),
            ),
        ),
        Document("appstore", APP_STORE_URL, listOf(Reader("visionos", ::appStoreValues))),
        Document("playstore", PLAY_STORE_URL, listOf(Reader("android", matches(PLAY_STORE_VERSION)))),
    )

    fun patternOf(identity: String): Regex = when (identity) {
        "visionos" -> VISION_OS_VERSION
        "android" -> ANDROID_VERSION
        else -> WEB_VERSION
    }

    private fun matches(pattern: Regex): (String) -> List<String> = { text ->
        pattern.findAll(text).map { it.groupValues[1] }.toList()
    }

    /** `results[].version` of the App Store's lookup answer. */
    fun appStoreValues(text: String): List<String> {
        val root = BoundedJsonParser.parse(text, maxDepth = 16, maxNodes = 50_000) ?: return emptyList()
        return root["results"].asArrayOrEmpty.mapNotNull { it["version"].asStringOrNull }
    }

    /** Whether the App Store's answer lists a visionOS device (a note, not a value). */
    fun appStoreListsVision(text: String): Boolean = VISION_DEVICE.containsMatchIn(text)

    /**
     * The `cver` values of an answer's `responseContext.serviceTrackingParams` (YouTube's own
     * answer naming a client version), found anywhere within depth 16 and 100 000 nodes.
     */
    fun answerValues(root: JsonValue): List<String> {
        val values = ArrayList<String>()
        var nodes = 0
        fun walk(value: JsonValue, depth: Int) {
            nodes += 1
            if (nodes > 100_000 || depth > 16) return
            when (value) {
                is JsonValue.Object -> {
                    if (value.entries["key"].asStringOrNull == "cver") {
                        value.entries["value"].asStringOrNull?.let(values::add)
                    }
                    value.entries.values.forEach { walk(it, depth + 1) }
                }
                is JsonValue.Array -> value.items.forEach { walk(it, depth + 1) }
                else -> Unit
            }
        }
        walk(root, 0)
        return values
    }

    /** -1, 0 or 1 comparing dotted numbers part by part; null when either is not numeric. */
    fun compare(left: String, right: String): Int? {
        val a = left.split('.').map { it.toIntOrNull() ?: return null }
        val b = right.split('.').map { it.toIntOrNull() ?: return null }
        for (index in 0 until maxOf(a.size, b.size)) {
            val difference = a.getOrElse(index) { 0 }.compareTo(b.getOrElse(index) { 0 })
            if (difference != 0) return difference.coerceIn(-1, 1)
        }
        return 0
    }

    /** One source's values judged against [bundled] (exactly one value, the pattern, newer). */
    fun fact(
        identity: String,
        source: String,
        reachable: Boolean,
        note: String?,
        values: List<String>,
        bundled: String,
    ): CensusVersion {
        val distinct = values.map(String::trim).filter(String::isNotEmpty).distinct()
        val value = distinct.singleOrNull()?.takeIf { it.length <= 40 }
        val pattern = value != null && patternOf(identity).matches(value)
        val newer = if (pattern) compare(value!!, bundled)?.let { it > 0 } else null
        val same = value?.let { it == bundled }
        return CensusVersion(
            identity = identity,
            source = source,
            reachable = reachable,
            note = note ?: if (distinct.size > 1) "${distinct.size} values" else null,
            values = distinct.size,
            value = if (pattern || value == null) value else value.filter { it.isLetterOrDigit() || it in ".-_ " },
            pattern = pattern,
            newer = newer,
            sameAsBundled = same,
            accepted = when {
                same == true -> "equal"
                newer == true -> "not-tested"
                else -> null
            },
        )
    }
}
