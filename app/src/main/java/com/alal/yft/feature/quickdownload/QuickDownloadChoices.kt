package com.alal.yft.feature.quickdownload

import com.alal.yft.core.model.media.AudioFromVideo
import com.alal.yft.core.model.media.MediaAsset
import com.alal.yft.core.model.media.MediaCandidate
import com.alal.yft.core.model.media.MediaGroup
import com.alal.yft.core.model.media.MediaGroups
import com.alal.yft.core.model.media.MediaKind
import com.alal.yft.core.model.media.MediaTrackType
import com.alal.yft.core.model.media.MediaVariant
import com.alal.yft.core.model.media.Mp3Variants
import com.alal.yft.core.model.media.VariantResolutionFailure
import com.alal.yft.core.model.settings.QualityPreference
import com.alal.yft.feature.preview.sizeText
import java.net.URI
import java.util.Locale
import kotlin.math.roundToInt

/** One candidate of the video the sheet shows, with the variants its lookup found. */
data class SheetSource(
    val candidate: MediaCandidate,
    /** Null when the lookup failed; [failure] then says why. */
    val asset: MediaAsset?,
    /** False when [asset] was built from what the site stated: Download looks it up first. */
    val resolved: Boolean,
    val failure: VariantResolutionFailure? = null,
)

enum class OptionSection { VIDEO, AUDIO }

/** One format the sheet can download: a variant of one source, maybe converted on the phone. */
data class SheetOption(
    val id: String,
    val section: OptionSection,
    /** "720p · HD", "M4A · 128 kbps", "MP3 · 320 kbps"; never the page title. */
    val title: String,
    /** "1280 × 720 · 30 fps · MP4", "From the video's sound"; may be empty. */
    val detail: String,
    /** "25 MB", "~4.6 MB", or null when unknown. */
    val size: String?,
    /** "Video + audio", "No sound", "Slow" (made on the phone). */
    val chips: List<String>,
    val source: SheetSource,
    val variant: MediaVariant,
    /** "720p", "1080p60", or a site's own "HD"/"SD" for a file it could not be measured. */
    val quality: String? = null,
    /** The height Fast/High and the order use: the real one, else the site's HD 720 / SD 480. */
    val rankHeight: Int? = null,
)

enum class QuickRowKind {
    /** The highest video with sound at or below 480p. */
    FAST,

    /** The highest video with sound above 480p and at or below 720p. */
    HIGH,

    /** The video, when no quality falls in the Fast or High range or none is known. */
    VIDEO,

    /** The audio: an M4A file, else the video's own sound kept as M4A. */
    MUSIC,

    /** The Music row's AAC converted to MP3 on the phone at [QuickDownloadChoices.MP3_KBPS]. */
    MP3,
}

/** One quick row of the sheet: a shortcut to one [SheetOption]. */
data class QuickRow(
    val kind: QuickRowKind,
    /** "Fast", "High", "Video", "M4A · Fast" or "MP3". */
    val title: String,
    /** The real quality and size, for example "480p · 30 fps · 18 MB". */
    val detail: String,
    val option: SheetOption,
) {
    val id: String get() = kind.name.lowercase(Locale.US)

    /** Only "Slow" is worth a chip on a quick row; More formats shows every chip. */
    val chips: List<String> get() = option.chips.filter { it == QuickDownloadChoices.SLOW }
}

/** The sheet's content for one video. */
data class QuickChoices(
    val title: String,
    /** The site, for example "facebook.com". */
    val source: String?,
    val durationMillis: Long?,
    val music: List<QuickRow>,
    val video: List<QuickRow>,
    /** Every format, video first: More formats lists them inside the sheet. */
    val more: List<SheetOption>,
) {
    val rows: List<QuickRow> get() = music + video

    val isAudioOnly: Boolean get() = more.none { it.section == OptionSection.VIDEO }

    /** The option behind a quick row id ("fast") or a More formats id ("more:…"). */
    fun option(selectionId: String?): SheetOption? {
        if (selectionId == null) return null
        rows.firstOrNull { it.id == selectionId }?.let { return it.option }
        if (!selectionId.startsWith(MORE_PREFIX)) return null
        val id = selectionId.removePrefix(MORE_PREFIX)
        return more.firstOrNull { it.id == id }
    }

    companion object {
        const val MORE_PREFIX = "more:"

        fun moreId(option: SheetOption): String = MORE_PREFIX + option.id
    }
}

/**
 * Builds the download sheet from the resolved candidates of one video (P3). Pure, so the table
 * of cases is unit-tested.
 *
 * Music offers an M4A file, else the sound of an MP4 kept as M4A, plus MP3; Video offers Fast
 * (≈ 480p) and High (≈ 720p) with sound. More formats lists every quality and audio option.
 * Labels come from the variants' real heights and bitrates, never from the page title.
 */
object QuickDownloadChoices {
    const val FAST_MAX_HEIGHT = 480
    const val HIGH_MAX_HEIGHT = 720

    /** The quick MP3 row's bitrate; More formats offers every [Mp3Variants.BITRATES_KBPS]. */
    const val MP3_KBPS = 192

    const val VIDEO_AND_AUDIO = "Video + audio"
    const val NO_SOUND = "No sound"
    const val SLOW = "Slow"

    fun of(group: MediaGroup, sources: List<SheetSource>): QuickChoices? {
        val video = mutableListOf<SheetOption>()
        val audio = mutableListOf<SheetOption>()
        sources.forEachIndexed { index, source ->
            val asset = source.asset ?: return@forEachIndexed
            asset.variants
                .filter { it.isPreviewable && it.mp3 == null && !it.audioFromVideo }
                .forEach { variant ->
                    if (variant.trackType == MediaTrackType.AUDIO) {
                        audio += audioOption(index, source, variant)
                    } else {
                        video += videoOption(index, source, variant)
                    }
                }
        }
        val videos = video.sortedWith(VIDEO_ORDER)
        val files = audio.sortedWith(AUDIO_ORDER)
        val wholeFiles = files.filter { it.variant.kind == MediaKind.DIRECT }
        val fromVideo = if (wholeFiles.isEmpty()) soundOf(videos) else null
        val aac = wholeFiles.firstOrNull { Mp3Variants.isAacSource(it.variant) } ?: fromVideo
        val mp3 = aac?.let(::mp3Options).orEmpty()
        val audios = wholeFiles + listOfNotNull(fromVideo) + mp3 + (files - wholeFiles.toSet())
        if (videos.isEmpty() && audios.isEmpty()) return null
        val durationMillis = group.durationMillis
            ?: sources.firstNotNullOfOrNull { it.asset?.durationMillis?.takeIf { d -> d > 0 } }
        return QuickChoices(
            title = group.title
                ?: sources.firstNotNullOfOrNull { MediaGroups.baseTitle(it.asset?.title) }
                ?: if (videos.isEmpty()) "Audio" else "Video",
            source = host(group.pageUrl),
            durationMillis = durationMillis,
            music = musicRows(wholeFiles.firstOrNull() ?: fromVideo ?: files.firstOrNull(), mp3),
            video = videoRows(videos),
            more = videos + audios,
        )
    }

    /** The row the user's default quality points at; Music only when there is no video. */
    fun preselect(choices: QuickChoices, quality: QualityPreference): QuickRow? {
        val ceiling = quality.maxHeight
        val fast = choices.video.firstOrNull { it.kind == QuickRowKind.FAST }
        val high = choices.video.firstOrNull { it.kind == QuickRowKind.HIGH }
        val preferred = if (ceiling != null && ceiling <= FAST_MAX_HEIGHT) fast else high
        return preferred ?: choices.video.firstOrNull() ?: choices.music.firstOrNull()
    }

    private fun videoRows(videos: List<SheetOption>): List<QuickRow> {
        val withSound = videos.filter { it.variant.trackType == MediaTrackType.AUDIO_VIDEO }
            .ifEmpty { videos }
        if (withSound.isEmpty()) return emptyList()
        val known = withSound.filter { it.rankHeight != null }
        val fast = known.firstOrNull { it.height() <= FAST_MAX_HEIGHT }
        val high = known.firstOrNull { it.height() in (FAST_MAX_HEIGHT + 1)..HIGH_MAX_HEIGHT }
        if (fast == null && high == null) {
            // Every known quality is above 720p: the smallest of them, else the first file.
            val only = known.minByOrNull { it.height() } ?: withSound.first()
            return listOf(QuickRow(QuickRowKind.VIDEO, "Video", rowDetail(only), only))
        }
        return listOfNotNull(
            fast?.let { QuickRow(QuickRowKind.FAST, "Fast", rowDetail(it), it) },
            high?.let { QuickRow(QuickRowKind.HIGH, "High", rowDetail(it), it) },
        )
    }

    private fun musicRows(music: SheetOption?, mp3: List<SheetOption>): List<QuickRow> {
        music ?: return emptyList()
        val format = formatName(music.variant) ?: "Audio"
        val quick = mp3.firstOrNull { it.variant.mp3?.bitrateKbps == MP3_KBPS }
        return listOfNotNull(
            QuickRow(QuickRowKind.MUSIC, "$format · Fast", audioDetail(music), music),
            quick?.let { QuickRow(QuickRowKind.MP3, "MP3", audioDetail(it), it) },
        )
    }

    private fun videoOption(index: Int, source: SheetSource, variant: MediaVariant): SheetOption {
        val height = variant.height
        val hint = if (height == null) qualityHint(source.candidate) else null
        val quality = height?.let { "${it}p${variant.highFrameRate()}" } ?: hint
        val title = when {
            height != null -> listOfNotNull(quality, resolutionName(height)).joinToString(" · ")
            hint != null -> listOfNotNull(hint, formatName(variant)).joinToString(" · ")
            else -> listOfNotNull(formatName(variant), "Quality unknown").joinToString(" · ")
        }
        val detail = listOfNotNull(
            if (variant.width != null && height != null) "${variant.width} × $height" else null,
            variant.framesPerSecond?.let { "${it.roundToInt()} fps" },
            formatName(variant).takeIf { height != null },
        ).joinToString(" · ")
        val chips = if (variant.trackType == MediaTrackType.VIDEO) NO_SOUND else VIDEO_AND_AUDIO
        return SheetOption(
            id = optionId(index, variant),
            section = OptionSection.VIDEO,
            title = title,
            detail = detail,
            size = variant.sizeText(),
            chips = listOf(chips),
            source = source,
            variant = variant,
            quality = quality,
            rankHeight = height ?: hint?.let(::hintHeight),
        )
    }

    /**
     * A site adapter's own quality word for a file the lookup could not measure, such as
     * Facebook's "HD" and "SD": only from a named video's " — label", and only a known word, so
     * a page title is never read as a quality.
     */
    private fun qualityHint(candidate: MediaCandidate): String? {
        if (candidate.videoId == null) return null
        return MediaGroups.titleLabel(candidate.title)?.takeIf { QUALITY_WORD.matches(it) }
    }

    /** HD and SD rank as 720 and 480 without being shown as a height. */
    private fun hintHeight(hint: String): Int? = when (hint.uppercase(Locale.US)) {
        "HD" -> HIGH_MAX_HEIGHT
        "SD" -> FAST_MAX_HEIGHT
        else -> hint.lowercase(Locale.US).substringBefore('p').toIntOrNull()
    }

    private fun audioOption(index: Int, source: SheetSource, variant: MediaVariant): SheetOption {
        val converted = variant.mp3 != null || variant.audioFromVideo
        val title = listOfNotNull(formatName(variant) ?: "Audio", variant.kbpsText())
            .joinToString(" · ")
        val detail = when {
            variant.mp3 != null -> "Made on the phone"
            variant.audioFromVideo -> "The video's own sound"
            else -> variant.language?.trim()?.takeIf(String::isNotEmpty).orEmpty()
        }
        return SheetOption(
            id = optionId(index, variant),
            section = OptionSection.AUDIO,
            title = title,
            detail = detail,
            size = variant.sizeText(),
            chips = if (converted) listOf(SLOW) else emptyList(),
            source = source,
            variant = variant,
        )
    }

    /**
     * The sound of a video that has no audio file: the MP4 with AAC whose sound has the highest
     * stated bitrate, and the smallest file among equals.
     */
    private fun soundOf(videos: List<SheetOption>): SheetOption? {
        val best = videos.filter { AudioFromVideo.canExtract(it.variant) }
            .sortedWith(
                compareByDescending<SheetOption> { it.variant.audioBitrateBitsPerSecond ?: 0L }
                    .thenBy { it.variant.sizeBytes ?: Long.MAX_VALUE },
            )
            .firstOrNull() ?: return null
        val asset = best.source.asset
        val m4a = AudioFromVideo.of(best.variant, asset?.durationMillis) ?: return null
        return audioOption(sourceIndexOf(best), best.source, m4a)
    }

    private fun mp3Options(aac: SheetOption): List<SheetOption> =
        Mp3Variants.BITRATES_KBPS.mapNotNull { kbps ->
            Mp3Variants.of(aac.variant, kbps, aac.source.asset?.durationMillis)
        }.map { audioOption(sourceIndexOf(aac), aac.source, it) }

    private fun sourceIndexOf(option: SheetOption): Int =
        option.id.substringBefore('-').removePrefix("s").toIntOrNull() ?: 0

    private fun optionId(index: Int, variant: MediaVariant): String = "s$index-${variant.id}"

    private fun rowDetail(option: SheetOption): String {
        val variant = option.variant
        val quality = option.quality
        return listOfNotNull(
            quality ?: formatName(variant),
            variant.framesPerSecond?.takeIf { quality != null }?.let { "${it.roundToInt()} fps" },
            option.size ?: SIZE_UNKNOWN,
        ).joinToString(" · ")
    }

    private fun audioDetail(option: SheetOption): String =
        listOfNotNull(option.variant.kbpsText(), option.size ?: SIZE_UNKNOWN).joinToString(" · ")

    private fun MediaVariant.kbpsText(): String? =
        bitrateBitsPerSecond?.takeIf { it > 0 }?.let { "${(it / 1_000.0).roundToInt()} kbps" }

    private fun MediaVariant.highFrameRate(): String =
        framesPerSecond?.takeIf { it > HIGH_FRAME_RATE }?.roundToInt()?.toString().orEmpty()

    private fun SheetOption.height(): Int = rankHeight ?: 0

    /** "MP4", "M4A", "MP3", "WebM"; the stream kind for adaptive tracks. */
    internal fun formatName(variant: MediaVariant): String? {
        if (variant.mp3 != null) return "MP3"
        val mime = variant.mimeType?.substringBefore(';')?.trim()?.lowercase(Locale.US)
        val container = variant.container?.trim()?.trimStart('.')?.lowercase(Locale.US)
        return when {
            mime == "audio/mp4" || container == "m4a" -> "M4A"
            mime == "audio/mpeg" || container == "mp3" -> "MP3"
            mime?.endsWith("/webm") == true || container == "webm" -> "WebM"
            mime == "video/mp4" || container == "mp4" -> "MP4"
            variant.kind == MediaKind.HLS -> "HLS"
            variant.kind == MediaKind.DASH -> "DASH"
            container != null -> container.uppercase(Locale.US)
            else -> null
        }
    }

    private fun resolutionName(height: Int): String? = when {
        height >= 2_160 -> "4K"
        height >= 1_440 -> "2K"
        height >= 1_080 -> "Full HD"
        height >= 720 -> "HD"
        else -> null
    }

    internal fun host(pageUrl: String): String? =
        runCatching { URI(pageUrl).host }.getOrNull()
            ?.lowercase(Locale.US)
            ?.removePrefix("www.")
            ?.removePrefix("m.")
            ?.takeIf(String::isNotEmpty)

    private fun kindRank(kind: MediaKind): Int = when (kind) {
        MediaKind.DIRECT -> 0
        MediaKind.HLS -> 1
        MediaKind.DASH -> 2
        MediaKind.UNKNOWN -> 3
    }

    /** Highest picture first; with sound before silent, whole files before streams. */
    private val VIDEO_ORDER = compareByDescending<SheetOption> { it.rankHeight ?: -1 }
        .thenByDescending { it.variant.framesPerSecond ?: 0.0 }
        .thenBy { if (it.variant.trackType == MediaTrackType.AUDIO_VIDEO) 0 else 1 }
        .thenBy { kindRank(it.variant.kind) }
        .thenByDescending { it.variant.bitrateBitsPerSecond ?: -1L }

    /** M4A files first, then the highest bitrate. */
    private val AUDIO_ORDER = compareBy<SheetOption> { kindRank(it.variant.kind) }
        .thenBy { if (Mp3Variants.isAacSource(it.variant)) 0 else 1 }
        .thenByDescending { it.variant.bitrateBitsPerSecond ?: -1L }

    private const val HIGH_FRAME_RATE = 31.0
    private const val SIZE_UNKNOWN = "Size unknown"
    private val QUALITY_WORD = Regex("(?i)hd|sd|\\d{3,4}p\\d{0,3}")
}
