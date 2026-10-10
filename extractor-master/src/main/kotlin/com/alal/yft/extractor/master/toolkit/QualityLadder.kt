/*
 * Provenance (Master toolkit T7, copied, not moved; main 34a41890):
 *   extractor-sites/.../facebook/FacebookDashOffers.kt  videos, audio, merged/repeats, shortSide,
 *                                                       qualityName, MAX_VIDEO_OFFERS
 *   extractor-sites/.../tiktok/TikTokExtractor.kt       joinedTo, ROW_ORDER
 *   extractor-sites/.../youtube/YouTubeExtractor.kt     bestAudio (original mix, highest bitrate)
 * Adapted: over MediaCandidate; the mergeable codec families are a parameter (MergeSupport).
 */
package com.alal.yft.extractor.master.toolkit

import com.alal.yft.core.model.media.MediaCandidate
import java.util.Locale
import kotlin.math.abs

/**
 * T7: which tracks become rows. Each picture size keeps one video (the best mergeable family,
 * AVC first, then its highest bitrate), the best AAC track is the sound, every encoding appears
 * once across pages, and joined answers add only sizes/codecs not listed yet. Pure: it never
 * invents a row and never probes.
 */
internal object QualityLadder {
    const val MAX_VIDEO_ROWS = 6
    private val STANDARD_HEIGHTS = listOf(144, 240, 360, 480, 720, 1_080, 1_440, 2_160, 4_320)

    enum class Family(val rank: Int) { AVC(0), HEVC(1), VP9(2), AV1(3), AAC(10), OPUS(11), OTHER(99) }

    /** The phone merges AVC everywhere; the caller widens this from its MergeSupport. */
    val AVC_ONLY: (Family) -> Boolean = { it == Family.AVC }

    fun family(codec: String?): Family {
        val lower = codec?.trim()?.lowercase(Locale.US).orEmpty()
        return when {
            lower.startsWith("avc1") || lower.startsWith("avc3") || lower == "h264" -> Family.AVC
            lower.startsWith("av01") || lower == "av1" -> Family.AV1
            lower.startsWith("vp09") || lower.startsWith("vp9") -> Family.VP9
            lower.startsWith("hev1") || lower.startsWith("hvc1") || lower == "h265" -> Family.HEVC
            lower.startsWith("mp4a") || lower == "aac" -> Family.AAC
            lower.startsWith("opus") -> Family.OPUS
            else -> Family.OTHER
        }
    }

    fun isAudio(track: MediaCandidate): Boolean =
        track.mimeType?.lowercase(Locale.US)?.startsWith("audio/") == true ||
            (track.width == null && track.height == null && track.codecs.isNotEmpty() &&
                track.codecs.all { family(it).rank >= Family.AAC.rank && family(it) != Family.OTHER })

    /** The video family of [track] (its first video codec); AVC when it names none (MP4 files). */
    fun videoFamily(track: MediaCandidate): Family =
        track.codecs.map(::family).firstOrNull { it.rank < Family.AAC.rank }
            ?: if (track.codecs.isEmpty()) Family.AVC else Family.OTHER

    /** The AAC track with the highest bitrate, or null. */
    fun bestAudio(tracks: List<MediaCandidate>): MediaCandidate? = tracks
        .filter { isAudio(it) && it.codecs.any { codec -> family(codec) == Family.AAC } }
        .maxByOrNull { it.bitrateBitsPerSecond ?: 0L }

    /** One mergeable video per picture size, the tallest short side first, at most [cap]. */
    fun videos(
        tracks: List<MediaCandidate>,
        mergeable: (Family) -> Boolean = AVC_ONLY,
        cap: Int = MAX_VIDEO_ROWS,
    ): List<MediaCandidate> = tracks
        .filter { !isAudio(it) && mergeable(videoFamily(it)) }
        .groupBy { (it.width ?: 0) to (it.height ?: 0) }
        .values
        .map { sameSize ->
            sameSize.sortedWith(
                compareBy<MediaCandidate> { videoFamily(it).rank }
                    .thenByDescending { it.bitrateBitsPerSecond ?: 0L },
            ).first()
        }
        .sortedWith(rowOrder())
        .take(cap)

    /** Every encoding once; the page read first keeps its tracks. */
    fun merged(vararg pages: List<MediaCandidate>): List<MediaCandidate> {
        val kept = mutableListOf<MediaCandidate>()
        pages.forEach { page -> page.forEach { if (kept.none { k -> repeats(k, it) }) kept += it } }
        return kept
    }

    /** [rows] plus the [added] rows whose height and codec family they do not list yet. */
    fun joined(rows: List<MediaCandidate>, added: List<MediaCandidate>): List<MediaCandidate> {
        fun slot(it: MediaCandidate) = shortSide(it) to videoFamily(it)
        val listed = rows.map(::slot).toSet()
        val fresh = added.filter { slot(it) !in listed }
        return if (fresh.isEmpty()) rows else (rows + fresh).sortedWith(rowOrder())
    }

    /** Highest first, AVC first on a tie, then the higher bitrate (TikTok's ROW_ORDER). */
    fun rowOrder(): Comparator<MediaCandidate> =
        compareByDescending<MediaCandidate> { shortSide(it) }
            .thenBy { videoFamily(it).rank }
            .thenByDescending { it.bitrateBitsPerSecond ?: 0L }

    /** The picture's short side, which names its quality: 1660 × 1078 is a 1078-line picture. */
    fun shortSide(track: MediaCandidate): Int {
        val width = track.width ?: 0
        val height = track.height ?: 0
        return if (width == 0 || height == 0) maxOf(width, height) else minOf(width, height)
    }

    /** The nearest standard height to the short side: 1660 × 1078 is "1080p"; null without one. */
    fun qualityName(track: MediaCandidate): String? {
        val side = shortSide(track).takeIf { it > 0 } ?: return null
        return "${STANDARD_HEIGHTS.minBy { abs(it - side) }}p"
    }

    private fun repeats(earlier: MediaCandidate, track: MediaCandidate): Boolean {
        if (UrlPolicy.whole(earlier.mediaUrl) == UrlPolicy.whole(track.mediaUrl)) return true
        if (isAudio(earlier) != isAudio(track) || videoFamily(earlier) != videoFamily(track)) {
            return false
        }
        val bitrate = track.bitrateBitsPerSecond ?: return false
        return track.height == earlier.height && bitrate == earlier.bitrateBitsPerSecond
    }
}
