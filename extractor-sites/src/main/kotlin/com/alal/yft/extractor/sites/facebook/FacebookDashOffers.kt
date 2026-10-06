package com.alal.yft.extractor.sites.facebook

import java.net.URI
import java.util.Locale
import kotlin.math.abs

/**
 * Chooses which manifest tracks become download rows (P4).
 *
 * A video track is offered merged with the best AAC track; only AVC and AV1 qualify because the
 * phone's MP4 muxer writes nothing else (AV1 only from Android 14, which the app checks). A
 * Facebook ladder repeats one picture size at several bitrates, so each size keeps its highest
 * bitrate, and AVC wins over AV1 at the same size because every phone plays it.
 */
internal object FacebookDashOffers {
    /** Room for the page's progressive files and the audio row within the sheet's 12 sources. */
    const val MAX_VIDEO_OFFERS = 6

    private val STANDARD_HEIGHTS = listOf(144, 240, 360, 480, 720, 1_080, 1_440, 2_160, 4_320)
    private const val AAC_PREFIX = "mp4a.40."
    private val AVC_PREFIXES = listOf("avc1", "avc3")
    private const val AV1_PREFIX = "av01"

    /** The AAC track with the highest bitrate, or null. */
    fun audio(tracks: List<FacebookDashTrack>): FacebookDashTrack? = tracks
        .filter { it.kind == FacebookTrackKind.AUDIO && it.codec.lower().startsWith(AAC_PREFIX) }
        .maxByOrNull { it.bandwidthBitsPerSecond ?: 0L }

    /** One mergeable video per picture size, the tallest short side first. */
    fun videos(tracks: List<FacebookDashTrack>): List<FacebookDashTrack> = tracks
        .filter { it.kind == FacebookTrackKind.VIDEO && familyRank(it.codec) != null }
        .groupBy { it.width to it.height }
        .values
        .map { sameSize ->
            sameSize.sortedWith(
                compareBy<FacebookDashTrack> { familyRank(it.codec) }
                    .thenByDescending { it.bandwidthBitsPerSecond ?: 0L },
            ).first()
        }
        .sortedWith(
            compareByDescending<FacebookDashTrack> { shortSide(it) }
                .thenByDescending { it.bandwidthBitsPerSecond ?: 0L },
        )
        .take(MAX_VIDEO_OFFERS)

    /**
     * Whether Safari's AVC ladder is worth asking (P23): no AVC video at all, an empty list too,
     * or the best AVC picture smaller than the best one offered in any codec or as a whole file
     * ([fileSides]: the HD/SD files' short sides, from their manifest track or their name). A
     * page with AVC 360p next to AV1 720p and 1080p lost 720p while AV1 merges are off (owner's
     * phone, R4).
     */
    fun needsAvcLadder(
        tracks: List<FacebookDashTrack>,
        fileSides: List<Int> = emptyList(),
    ): Boolean {
        val videos = tracks.filter { it.kind == FacebookTrackKind.VIDEO }
        val bestAvc = videos.filter(::isAvc).maxOfOrNull(::shortSide) ?: return true
        return bestAvc < (videos.map(::shortSide) + fileSides).max()
    }

    /** AVC video every phone merges and an AAC track for its sound: a page that is enough. */
    fun hasAvcWithSound(tracks: List<FacebookDashTrack>): Boolean =
        tracks.any(::isAvc) && audio(tracks) != null

    /**
     * The tracks of every page read for one video, each encoding once (P23). Every page lists an
     * encoding at a new address, so a track repeats an earlier one with the same address, the
     * same representation ID, or the same codec family, picture height and bandwidth. The page
     * read first keeps its tracks.
     */
    fun merged(vararg pages: List<FacebookDashTrack>): List<FacebookDashTrack> {
        val kept = mutableListOf<FacebookDashTrack>()
        pages.forEach { page ->
            page.forEach { track -> if (kept.none { repeats(it, track) }) kept += track }
        }
        return kept
    }

    /**
     * The manifest video track an HD/SD file was made from (P23), or null. Facebook serves the
     * HD file at the path of the DASH video it was made from, sound added: on reel
     * 1545617074260365 (sandbox, 2026-10-06) the HD file's path was the 1280 × 720 AVC track's,
     * and its stated bitrate that track's plus the audio track's. Several picture sizes at one
     * path are ambiguous, so none is taken.
     */
    fun encodingOf(fileUrl: String, tracks: List<FacebookDashTrack>): FacebookDashTrack? {
        val path = pathOf(fileUrl) ?: return null
        val same = tracks.filter { it.kind == FacebookTrackKind.VIDEO && pathOf(it.url) == path }
        val first = same.firstOrNull() ?: return null
        return first.takeIf { same.all { it.width == first.width && it.height == first.height } }
    }

    /**
     * What [tracks] hold, for the lookup's details: `AVC 360/720, AV1 1080 + audio`, `audio
     * only`, or `no DASH tracks`.
     */
    fun summary(tracks: List<FacebookDashTrack>): String {
        val videos = tracks.filter { it.kind == FacebookTrackKind.VIDEO }
            .groupBy { family(it.codec) }
            .map { (family, sameFamily) ->
                val sizes = sameFamily.map { qualityName(it).removeSuffix("p").toInt() }
                "$family ${sizes.distinct().sorted().joinToString("/")}"
            }
        val sound = audio(tracks) != null
        return when {
            videos.isEmpty() && sound -> "audio only"
            videos.isEmpty() -> "no DASH tracks"
            sound -> videos.joinToString(", ") + " + audio"
            else -> videos.joinToString(", ")
        }
    }

    /** The best AVC picture's name, such as `AVC 720`, or `no AVC`. */
    fun bestAvc(tracks: List<FacebookDashTrack>): String =
        tracks.filter(::isAvc).maxByOrNull(::shortSide)
            ?.let { "AVC ${qualityName(it).removeSuffix("p")}" } ?: "no AVC"

    fun isAvc(track: FacebookDashTrack): Boolean =
        track.kind == FacebookTrackKind.VIDEO && familyRank(track.codec) == AVC_RANK

    private fun repeats(earlier: FacebookDashTrack, track: FacebookDashTrack): Boolean {
        if (earlier.url == track.url) return true
        if (earlier.kind != track.kind || family(earlier.codec) != family(track.codec)) {
            return false
        }
        if (track.representationId != null && track.representationId == earlier.representationId) {
            return true
        }
        return track.bandwidthBitsPerSecond != null && track.height == earlier.height &&
            track.bandwidthBitsPerSecond == earlier.bandwidthBitsPerSecond
    }

    /** The codec family a details line names: AVC, AV1, VP9, HEVC, AAC, or the codec's head. */
    private fun family(codec: String): String {
        val lower = codec.lower()
        return when {
            AVC_PREFIXES.any(lower::startsWith) -> "AVC"
            lower.startsWith(AV1_PREFIX) -> "AV1"
            lower.startsWith("vp09") || lower.startsWith("vp9") -> "VP9"
            lower.startsWith("hev1") || lower.startsWith("hvc1") -> "HEVC"
            lower.startsWith("mp4a") -> "AAC"
            else -> lower.substringBefore('.').uppercase(Locale.US)
        }
    }

    private fun pathOf(url: String): String? = runCatching { URI(url.trim()) }.getOrNull()
        ?.takeIf { it.scheme.equals("https", ignoreCase = true) && it.host != null }
        ?.rawPath?.takeIf { it.length > 1 }

    /** The picture's short side, which names its quality: 1660 × 1078 is a 1078-line picture. */
    fun shortSide(track: FacebookDashTrack): Int =
        minOf(track.width ?: 0, track.height ?: 0)

    /**
     * The standard name the download sheet gives the picture (P3-FIX): the nearest standard
     * height to its short side, so 1660 × 1078 is "1080p" and 552 × 358 is "360p".
     */
    fun qualityName(track: FacebookDashTrack): String {
        val side = shortSide(track)
        return "${STANDARD_HEIGHTS.minBy { standard -> abs(standard - side) }}p"
    }

    private fun familyRank(codec: String): Int? {
        val lower = codec.lower()
        return when {
            AVC_PREFIXES.any(lower::startsWith) -> AVC_RANK
            lower.startsWith(AV1_PREFIX) -> AV1_RANK
            else -> null
        }
    }

    private fun String.lower(): String = trim().lowercase(Locale.US)

    private const val AVC_RANK = 0
    private const val AV1_RANK = 1
}
