package com.alal.yft.extractor.sites.facebook

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

    /** Whether [tracks] hold video but none an older phone can merge, so AVC is worth asking. */
    fun lacksAvcVideo(tracks: List<FacebookDashTrack>): Boolean {
        val videos = tracks.filter { it.kind == FacebookTrackKind.VIDEO }
        return videos.isNotEmpty() && videos.none(::isAvc)
    }

    fun isAvc(track: FacebookDashTrack): Boolean =
        track.kind == FacebookTrackKind.VIDEO && familyRank(track.codec) == AVC_RANK

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
