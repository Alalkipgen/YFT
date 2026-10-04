package com.alal.yft.feature.quickdownload

import com.alal.yft.core.model.media.MediaCandidate
import com.alal.yft.core.model.media.MediaKind
import com.alal.yft.core.model.settings.QualityPreference
import com.alal.yft.feature.detectedmedia.DetectedMediaStore
import com.alal.yft.ui.components.formatLabel
import com.alal.yft.ui.components.isAudio
import com.alal.yft.ui.components.isSavable
import com.alal.yft.ui.format.YftFormat

enum class QuickRowKind {
    /** The highest video at or below 480p. */
    FAST,

    /** The highest video above 480p and at or below 720p. */
    HIGH,

    /** The only video, when it has no stated height or every height is above 720p. */
    VIDEO,

    /** The audio-only stream, M4A when there is one. */
    MUSIC,
}

/** One row of "Video you copied": what the user taps and the candidate it downloads. */
data class QuickRow(
    val kind: QuickRowKind,
    /** "Fast", "High", "Video" or "M4A · Fast". */
    val title: String,
    /** The real label and size, for example "480p · 18 MB"; never a made-up height. */
    val detail: String,
    val candidate: MediaCandidate,
) {
    val id: String get() = kind.name.lowercase()
}

/** The sheet's content for one copied video. */
data class QuickChoices(
    val title: String,
    val durationMillis: Long?,
    val music: QuickRow?,
    val video: List<QuickRow>,
    /** Savable candidates the lookup found; More formats lists them all. */
    val candidateCount: Int,
) {
    val rows: List<QuickRow> get() = listOfNotNull(music) + video
}

/**
 * Picks the quick rows for a Home lookup. Pure, so the table of cases is unit-tested.
 *
 * Only a lookup that found one video qualifies: every savable candidate is a whole file
 * (adaptive HLS/DASH streams pick their quality in Download as) and shares one title before
 * the extractor's " — label" suffix. Several videos need a stated height or an HD/SD label so
 * they can be ranked; otherwise Home keeps the Found list.
 */
object QuickDownloadChoices {
    const val FAST_MAX_HEIGHT = 480
    const val HIGH_MAX_HEIGHT = 720

    fun of(candidates: List<MediaCandidate>): QuickChoices? {
        val savable = candidates.take(DetectedMediaStore.MAX_CANDIDATES).filter { it.isSavable }
        if (savable.isEmpty()) return null
        if (savable.any { it.kind == MediaKind.HLS || it.kind == MediaKind.DASH }) return null
        val parsed = savable.map(::Parsed)
        if (parsed.map(Parsed::base).distinct().size != 1) return null
        val videos = parsed.filterNot { it.candidate.isAudio() }
        if (videos.size > 1 && videos.any { it.rank == null }) return null
        val videoRows = videoRows(videos)
        val music = musicRow(parsed.filter { it.candidate.isAudio() })
        if (videoRows.isEmpty() && music == null) return null
        return QuickChoices(
            title = parsed.first().base ?: if (videoRows.isEmpty()) "Audio" else "Video",
            durationMillis = savable.firstNotNullOfOrNull { candidate ->
                candidate.durationMillis?.takeIf { it > 0 }
            },
            music = music,
            video = videoRows,
            candidateCount = savable.size,
        )
    }

    /** The row the user's default quality points at; Music only when there is no video. */
    fun preselect(choices: QuickChoices, quality: QualityPreference): QuickRow? {
        val ceiling = quality.maxHeight
        val fast = choices.video.firstOrNull { it.kind == QuickRowKind.FAST }
        val high = choices.video.firstOrNull { it.kind == QuickRowKind.HIGH }
        val preferred = if (ceiling != null && ceiling <= FAST_MAX_HEIGHT) fast else high
        return preferred ?: choices.video.firstOrNull() ?: choices.music
    }

    private fun videoRows(videos: List<Parsed>): List<QuickRow> {
        if (videos.isEmpty()) return emptyList()
        if (videos.size == 1 && videos.single().rank == null) {
            return listOf(videos.single().row(QuickRowKind.VIDEO, "Video"))
        }
        // Stable sort: within one height the extractor's order (best first) decides.
        val ranked = videos.sortedByDescending { it.rank ?: 0 }
        val fast = ranked.firstOrNull { (it.rank ?: 0) <= FAST_MAX_HEIGHT }
        val high = ranked.firstOrNull { (it.rank ?: 0) in (FAST_MAX_HEIGHT + 1)..HIGH_MAX_HEIGHT }
        if (fast == null && high == null) {
            return listOf(ranked.last().row(QuickRowKind.VIDEO, "Video"))
        }
        return listOfNotNull(
            fast?.row(QuickRowKind.FAST, "Fast"),
            high?.row(QuickRowKind.HIGH, "High"),
        )
    }

    private fun musicRow(audios: List<Parsed>): QuickRow? {
        val m4a = audios.filter { it.candidate.formatLabel() == "M4A" }
        val best = m4a.maxByOrNull { it.kbps ?: 0 } ?: audios.firstOrNull() ?: return null
        val format = best.candidate.formatLabel() ?: "Audio"
        return best.row(QuickRowKind.MUSIC, "$format · Fast")
    }

    /** A candidate's title split into the video title and the extractor's quality label. */
    private class Parsed(val candidate: MediaCandidate) {
        private val title = candidate.title?.trim()?.takeIf(String::isNotEmpty)
        val base: String? = title?.let {
            if (LABEL_SEPARATOR in it) it.substringBeforeLast(LABEL_SEPARATOR).trim() else it
        }?.take(MAX_TITLE)?.takeIf(String::isNotEmpty)
        val label: String? = title?.takeIf { LABEL_SEPARATOR in it }
            ?.substringAfterLast(LABEL_SEPARATOR)?.trim()?.takeIf(String::isNotEmpty)
            ?.take(MAX_LABEL)

        /** The stated height, else HD/SD ranked at 720/480 for ordering only (not shown). */
        val rank: Int? = label?.let { text ->
            HEIGHT.find(text)?.groupValues?.get(1)?.toIntOrNull()
                ?: when (text.uppercase()) {
                    "HD" -> HIGH_MAX_HEIGHT
                    "SD" -> FAST_MAX_HEIGHT
                    else -> null
                }
        }
        val kbps: Int? = label?.let { KBPS.find(it)?.groupValues?.get(1)?.toIntOrNull() }

        fun row(kind: QuickRowKind, name: String): QuickRow {
            val facts = buildList {
                label?.let(::add) ?: candidate.formatLabel()?.let(::add)
                candidate.contentLengthBytes?.takeIf { it > 0 }?.let { add(YftFormat.bytes(it)) }
            }
            return QuickRow(
                kind = kind,
                title = name,
                detail = facts.joinToString(" · ").ifEmpty { "Size unknown" },
                candidate = candidate,
            )
        }
    }

    private const val LABEL_SEPARATOR = " — "
    private const val MAX_TITLE = 120
    private const val MAX_LABEL = 40
    private val HEIGHT = Regex("(?<![0-9])([0-9]{3,4})p")
    private val KBPS = Regex("([0-9]{1,4}) kbps")
}
