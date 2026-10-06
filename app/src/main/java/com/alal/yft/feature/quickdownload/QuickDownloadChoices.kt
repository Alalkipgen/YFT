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
import com.alal.yft.detection.VideoPlaybackSupport
import com.alal.yft.feature.preview.sizeText
import java.net.URI
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

/** One candidate of the video the sheet shows, with the variants its lookup found. */
data class SheetSource(
    val candidate: MediaCandidate,
    /** Null before resolution; stated whole-file metadata can still make a row (P11). */
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
    /** "480p", "720p · HD", "M4A · 128 kbps", "MP3 · 320 kbps"; never the page title. */
    val title: String,
    /** The real picture, "848 × 478 · 30 fps · MP4", or "The video's own sound"; may be empty. */
    val detail: String,
    /** "25 MB", "~4.6 MB", or null when unknown. */
    val size: String?,
    /** "No sound" for a silent video, "Slow" for audio made on the phone. */
    val chips: List<String>,
    val source: SheetSource,
    val variant: MediaVariant,
    /** "480p", "1080p60", or a site's own "HD"/"SD" for a file it could not be measured. */
    val quality: String? = null,
    /** The standard height the row is named after and ordered by; HD ranks 720, SD 480. */
    val rankHeight: Int? = null,
)

/**
 * The sheet's content for one video (P3-FIX, owner's phone check): exactly two sections.
 * **Audio** holds an M4A and MP3 at each bitrate; **Video** one row per standard resolution.
 */
data class QuickChoices(
    val title: String,
    /** The site, for example "facebook.com". */
    val source: String?,
    val durationMillis: Long?,
    /** An M4A (a file, else the video's own sound), then MP3 at each bitrate. */
    val audio: List<SheetOption>,
    /** One row per standard resolution, highest first. */
    val video: List<SheetOption>,
) {
    val options: List<SheetOption> get() = audio + video

    val isAudioOnly: Boolean get() = video.isEmpty()

    /** The option with [id], or null. */
    fun option(id: String?): SheetOption? =
        id?.let { wanted -> options.firstOrNull { it.id == wanted } }
}

/**
 * Builds the download sheet from the resolved candidates of one video. Pure, so the table of
 * cases is unit-tested.
 *
 * Audio offers the best M4A file, else the sound of an MP4 kept as M4A, and that AAC converted
 * to MP3 at every [Mp3Variants.BITRATES_KBPS]; another audio file only when there is no M4A.
 * Video offers one row per standard resolution (240p, 360p, 480p, 720p, 1080p and higher):
 * a row is named after the nearest standard height of the picture's short side, so 848 × 478 is
 * "480p" while its detail keeps the real "848 × 478 · 30 fps". Of the files at one resolution
 * the row is the one with sound, a complete file before one merged on the phone (P4: Facebook's
 * HD file before its 720p video and audio tracks), a whole file before a stream, MP4 before
 * other containers, then the higher frame rate and bitrate. Labels never come from the page
 * title. A 2K or higher row the phone has no decoder for keeps a [MAY_NOT_PLAY] chip (P6).
 */
object QuickDownloadChoices {
    /** The heights rows are named after, from 144p to 8K. */
    val STANDARD_HEIGHTS = listOf(144, 240, 360, 480, 720, 1_080, 1_440, 2_160, 4_320)

    const val NO_SOUND = "No sound"
    const val SLOW = "Slow"

    /** P6: on a 2K or 4K row whose picture this phone has no decoder for. */
    const val MAY_NOT_PLAY = "May not play on this phone"

    fun of(
        group: MediaGroup,
        sources: List<SheetSource>,
        playback: VideoPlaybackSupport = VideoPlaybackSupport.ANY,
    ): QuickChoices? {
        val video = mutableListOf<SheetOption>()
        val audio = mutableListOf<SheetOption>()
        sources.forEachIndexed { index, source ->
            val asset = source.asset ?: QuickDownloadMetadata.asset(source.candidate)
                ?: return@forEachIndexed
            asset.variants
                .filter { it.isPreviewable && it.mp3 == null && !it.audioFromVideo }
                .forEach { variant ->
                    if (variant.trackType == MediaTrackType.AUDIO) {
                        audio += audioOption(index, source, variant)
                    } else {
                        video += videoOption(index, source, variant, playback)
                    }
                }
        }
        val videos = videoRows(video)
        val audios = audioRows(audio, video)
        if (videos.isEmpty() && audios.isEmpty()) return null
        val durationMillis = group.durationMillis
            ?: sources.firstNotNullOfOrNull { it.asset?.durationMillis?.takeIf { d -> d > 0 } }
        return QuickChoices(
            title = group.title
                ?: sources.firstNotNullOfOrNull { MediaGroups.baseTitle(it.asset?.title) }
                ?: if (videos.isEmpty()) "Audio" else "Video",
            source = host(group.pageUrl),
            durationMillis = durationMillis,
            audio = audios,
            video = videos,
        )
    }

    /**
     * The row the user's default quality points at: the highest resolution at or below its
     * ceiling, else the lowest; a row with sound before a silent one. Audio only when there is
     * no video.
     */
    fun preselect(choices: QuickChoices, quality: QualityPreference): SheetOption? {
        val videos = choices.video.filter { it.variant.height != null }
            .ifEmpty { choices.video }
        if (videos.isEmpty()) return choices.audio.firstOrNull()
        val ceiling = quality.maxHeight ?: return withSound(videos)
        val known = videos.filter { it.rankHeight != null }
        val fitting = known.filter { (it.rankHeight ?: 0) <= ceiling }
        return when {
            fitting.isNotEmpty() -> withSound(fitting)
            known.isNotEmpty() -> known.last()
            else -> videos.first()
        }
    }

    /**
     * P9: two audio and two video rows. The full choices and their IDs stay untouched, so
     * expanding/collapsing never changes the selection or lists an option twice.
     * If no lower video exists, the nearest higher one is the second choice.
     */
    fun compact(choices: QuickChoices, quality: QualityPreference): QuickChoices {
        val measured = choices.video.filter { it.variant.height != null }
        val videos = measured.ifEmpty { choices.video }
        val preferred = preselect(choices.copy(video = videos), quality)
            ?.takeIf { it.section == OptionSection.VIDEO }
        val lower = preferred?.rankHeight?.let { height ->
            videos.firstOrNull { (it.rankHeight ?: Int.MAX_VALUE) < height }
        }
        val next = lower ?: videos.filter { it.id != preferred?.id }
            .minByOrNull { it.rankHeight ?: Int.MAX_VALUE }
        return choices.copy(
            audio = listOfNotNull(
                choices.audio.firstOrNull(),
                choices.audio.firstOrNull { it.variant.mp3?.bitrateKbps == 128 },
            ).distinctBy(SheetOption::id),
            video = listOfNotNull(preferred, next).distinctBy(SheetOption::id),
        )
    }

    /** P11: update only sizes/source status, never titles, IDs, row order or chosen formats. */
    fun updateSizes(choices: QuickChoices, source: SheetSource): QuickChoices {
        fun update(option: SheetOption): SheetOption {
            if (option.source.candidate != source.candidate) return option
            val asset = source.asset ?: return option.copy(source = source)
            var variant = asset.variants.firstOrNull { it.mp3 == null && !it.audioFromVideo }
                ?: return option.copy(source = source)
            if (option.variant.audioFromVideo) {
                variant = AudioFromVideo.of(variant, asset.durationMillis)
                    ?: return option.copy(source = source)
            }
            option.variant.mp3?.let { mp3 ->
                variant = Mp3Variants.of(variant, mp3.bitrateKbps, asset.durationMillis)
                    ?: return option.copy(source = source)
            }
            return option.copy(
                source = source,
                size = variant.sizeText(),
                variant = option.variant.copy(
                    sizeBytes = variant.sizeBytes,
                    sizeAccuracy = variant.sizeAccuracy,
                ),
            )
        }
        return choices.copy(
            audio = choices.audio.map(::update),
            video = choices.video.map(::update),
        )
    }

    /** The standard height for a picture of [width] × [height]: nearest to its short side. */
    fun standardHeight(width: Int?, height: Int): Int {
        val side = if (width != null && width > 0) minOf(width, height) else height
        // On a tie the lower name wins, so a row never claims more than the picture has.
        return STANDARD_HEIGHTS.minBy { standard -> abs(standard - side) }
    }

    private fun withSound(options: List<SheetOption>): SheetOption =
        options.firstOrNull { it.variant.trackType == MediaTrackType.AUDIO_VIDEO }
            ?: options.first()

    /** One row per standard resolution, highest first; files of unknown quality stay apart. */
    private fun videoRows(options: List<SheetOption>): List<SheetOption> {
        val rows = LinkedHashMap<String, SheetOption>()
        options.sortedWith(VIDEO_ORDER).forEach { option ->
            val hint = option.quality?.uppercase(Locale.US)?.takeIf { it == "HD" || it == "SD" }
            val key = when {
                hint != null && option.variant.height == null -> "hint:$hint"
                option.rankHeight != null -> "height:${option.rankHeight}"
                else -> "file:${option.id}"
            }
            rows.putIfAbsent(key, option)
        }
        return rows.values.toList()
    }

    /**
     * The best M4A file, else the video's own sound kept as M4A, then MP3 made from it at every
     * bitrate; with no AAC at all the best other audio file alone.
     */
    private fun audioRows(audio: List<SheetOption>, video: List<SheetOption>): List<SheetOption> {
        val files = audio.sortedWith(AUDIO_ORDER)
        val m4a = files.firstOrNull {
            it.variant.kind == MediaKind.DIRECT && Mp3Variants.isAacSource(it.variant)
        } ?: soundOf(video)
        if (m4a == null) return listOfNotNull(files.firstOrNull())
        return listOf(m4a) + mp3Options(m4a)
    }

    private fun videoOption(
        index: Int,
        source: SheetSource,
        variant: MediaVariant,
        playback: VideoPlaybackSupport,
    ): SheetOption {
        val height = variant.height?.takeIf { it > 0 }
        val standard = height?.let { standardHeight(variant.width, it) }
        val hint = if (standard == null) qualityHint(source.candidate) else null
        val quality = standard?.let { "${it}p${variant.highFrameRate()}" } ?: hint
        val title = when {
            standard != null -> listOfNotNull(quality, resolutionName(standard)).joinToString(" · ")
            hint != null -> hint
            else -> QUALITY_UNKNOWN
        }
        // The real picture: a row named 480p for 848 × 478 still says 848 × 478.
        val width = variant.width?.takeIf { it > 0 }
        val picture = when {
            height == null -> null
            width != null -> "$width × $height"
            else -> "${height}p"
        }
        val detail = listOfNotNull(
            picture,
            variant.framesPerSecond?.takeIf { it > 0 }?.let { "${it.roundToInt()} fps" },
            formatName(variant),
        ).joinToString(" · ")
        val silent = variant.trackType == MediaTrackType.VIDEO
        // Every phone YFT runs on plays Full HD; above it only some decode VP9 or AV1 (P6).
        val unplayable = (standard ?: 0) > FULL_HD_HEIGHT && !playback.canPlay(variant)
        return SheetOption(
            id = optionId(index, variant),
            section = OptionSection.VIDEO,
            title = title,
            detail = detail,
            size = variant.sizeText(),
            chips = listOfNotNull(NO_SOUND.takeIf { silent }, MAY_NOT_PLAY.takeIf { unplayable }),
            source = source,
            variant = variant,
            quality = quality,
            rankHeight = standard ?: hint?.let(::hintHeight),
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
        "HD" -> HD_HEIGHT
        "SD" -> SD_HEIGHT
        else -> hint.lowercase(Locale.US).substringBefore('p').toIntOrNull()
            ?.let { standardHeight(null, it) }
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

    private fun MediaVariant.kbpsText(): String? =
        bitrateBitsPerSecond?.takeIf { it > 0 }?.let { "${(it / 1_000.0).roundToInt()} kbps" }

    private fun MediaVariant.highFrameRate(): String =
        framesPerSecond?.takeIf { it > HIGH_FRAME_RATE }?.roundToInt()?.toString().orEmpty()

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
        height >= 4_320 -> "8K"
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

    /**
     * P16: the link as the waiting sheet shows it before the video's title is known, without
     * its scheme, "www." or fragment ("youtube.com/watch?v=…").
     */
    internal fun shownLink(pageUrl: String): String? {
        val uri = runCatching { URI(pageUrl) }.getOrNull() ?: return null
        val site = host(pageUrl) ?: return null
        val path = uri.rawPath.orEmpty().takeUnless { it == "/" }.orEmpty()
        val query = uri.rawQuery?.let { "?$it" }.orEmpty()
        return (site + path + query).take(MAX_SHOWN_LINK)
    }

    private fun kindRank(kind: MediaKind): Int = when (kind) {
        MediaKind.DIRECT -> 0
        MediaKind.HLS -> 1
        MediaKind.DASH -> 2
        MediaKind.UNKNOWN -> 3
    }

    /**
     * Highest resolution first. At one resolution: with sound before silent, a complete file
     * before a merge, whole files before streams, MP4 before other containers, then the taller
     * real picture, the higher frame rate and the higher bitrate.
     */
    private val VIDEO_ORDER = compareByDescending<SheetOption> { it.rankHeight ?: -1 }
        .thenBy { if (it.variant.height != null) 0 else 1 }
        .thenBy { if (it.variant.trackType == MediaTrackType.AUDIO_VIDEO) 0 else 1 }
        .thenBy { if (it.variant.audioCompanion == null) 0 else 1 }
        .thenBy { kindRank(it.variant.kind) }
        .thenBy { if (formatName(it.variant) == "MP4") 0 else 1 }
        .thenByDescending { it.variant.height ?: 0 }
        .thenByDescending { it.variant.framesPerSecond ?: 0.0 }
        .thenByDescending { it.variant.bitrateBitsPerSecond ?: -1L }

    /** M4A files first, then the highest bitrate. */
    private val AUDIO_ORDER = compareBy<SheetOption> { kindRank(it.variant.kind) }
        .thenBy { if (Mp3Variants.isAacSource(it.variant)) 0 else 1 }
        .thenByDescending { it.variant.bitrateBitsPerSecond ?: -1L }

    private const val HIGH_FRAME_RATE = 31.0
    private const val MAX_SHOWN_LINK = 120
    private const val HD_HEIGHT = 720
    private const val SD_HEIGHT = 480
    private const val FULL_HD_HEIGHT = 1_080
    private const val QUALITY_UNKNOWN = "Quality unknown"
    private val QUALITY_WORD = Regex("(?i)hd|sd|\\d{3,4}p\\d{0,3}")
}
