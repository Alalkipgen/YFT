package com.alal.yft.core.model.media

import kotlin.math.abs

/**
 * Everything a page found for one video: its qualities and its audio (P3).
 *
 * [key] is stable while the page keeps the same candidates, so a list can use it as an item key.
 * [title] is the video's title without an adapter's " — 720p" quality suffix.
 */
data class MediaGroup(
    val key: String,
    val title: String?,
    val candidates: List<MediaCandidate>,
) {
    init {
        require(key.isNotBlank())
        require(candidates.isNotEmpty())
    }

    val pageUrl: String get() = candidates.first().pageUrl

    val durationMillis: Long?
        get() = candidates.firstNotNullOfOrNull { candidate ->
            candidate.durationMillis?.takeIf { it > 0 }
        }

    override fun toString(): String =
        "MediaGroup(title=$title, candidateCount=${candidates.size})"
}

/**
 * P24: a page's videos, and apart from them the previews and ads around them
 * ([MediaGroups.ofPage]). The found lists count [videos]; [previews] are listed under "Other
 * videos on this page".
 */
data class PageVideoList(
    val videos: List<MediaGroup>,
    val previews: List<MediaGroup> = emptyList(),
) {
    /** Videos first, then previews: the order the found lists show. */
    val all: List<MediaGroup> get() = videos + previews
}

/**
 * Groups a page's candidates by video. Pure, so the rules are unit-tested.
 *
 * Candidates are one video when a site adapter named the same video ([MediaCandidate.videoId]),
 * or when they come from the same page and state the same length. Anything else stays on its
 * own: two files of unknown length on one page may well be two videos. Order follows the first
 * candidate of each video.
 */
object MediaGroups {
    fun of(candidates: List<MediaCandidate>): List<MediaGroup> {
        val groups = LinkedHashMap<String, MutableList<MediaCandidate>>()
        candidates.forEachIndexed { index, candidate ->
            groups.getOrPut(keyOf(candidate, index)) { mutableListOf() }.add(candidate)
        }
        return groups.map { (key, members) ->
            MediaGroup(key = key, title = titleOf(members), candidates = members)
        }
    }

    /**
     * The videos a page offers: what the Download button, the found lists and the sheet count.
     *
     * When a site adapter named the page's video, only named videos count. The files the site's
     * own player fetched while playing it (byte ranges, its separate picture and sound tracks)
     * are parts of that video, not more videos (P3-FIX: Facebook's story page listed 26 of them
     * instead of opening the sheet). A page no adapter named keeps every group, unless it is on
     * a site an adapter reads ([adapterSite]): there only the adapter's video counts, even
     * while its lookup runs or after it failed, so the page never lists its player's files
     * (P12).
     */
    fun pageVideos(
        candidates: List<MediaCandidate>,
        adapterSite: Boolean = false,
    ): List<MediaGroup> {
        val groups = of(candidates)
        val named = groups.filter { group -> group.candidates.any { it.videoId != null } }
        return if (adapterSite) named else named.ifEmpty { groups }
    }

    /**
     * P24: [groups] (a page's videos, [pageVideos]) split into the page's own videos and what
     * only looks like a preview or an ad around them ([looksLikePreview]). When everything looks
     * like a preview, nothing is set apart: the page's videos are all it has.
     */
    fun ofPage(groups: List<MediaGroup>): PageVideoList {
        val (previews, videos) = groups.partition { looksLikePreview(it, groups) }
        return if (videos.isEmpty()) PageVideoList(groups) else PageVideoList(videos, previews)
    }

    /**
     * P24: whether [video] looks like a preview or an ad rather than [page]'s own video: the page
     * marked all its files so ([PageMediaRole.PREVIEW]: a thumbnail's clip, a muted loop, an
     * ad's file), their addresses say so (`preview`, `thumb`, `teaser`, `sprite`), or it is not
     * known to be a minute or more long while the page names another video as its own
     * ([PageMediaRole.MAIN]) or, being shorter, has a video of a minute or more. A video a site
     * adapter named, or one the page names as its own, never is.
     */
    fun looksLikePreview(video: MediaGroup, page: List<MediaGroup>): Boolean {
        val files = video.candidates
        if (files.any { it.videoId != null || it.pageRole == PageMediaRole.MAIN }) return false
        if (files.all { it.pageRole == PageMediaRole.PREVIEW }) return true
        if (files.all { isPreviewAddress(it.mediaUrl) }) return true
        val length = video.durationMillis
        if (length != null && length >= LONG_VIDEO_MILLIS) return false
        return page.any { other ->
            other !== video && (
                other.candidates.any { it.pageRole == PageMediaRole.MAIN } ||
                    length != null && (other.durationMillis ?: 0L) >= LONG_VIDEO_MILLIS
                )
        }
    }

    /**
     * The video a page with several opens first (P12): the one playing ([playingUrl], the
     * address its player reads), else the best ranked ([mainVideo] with a [PlayingVideo]).
     */
    fun mainVideo(videos: List<MediaGroup>, playingUrl: String? = null): MediaGroup? =
        mainVideo(videos, playingUrl?.let { PlayingVideo(url = it) })

    /**
     * P24: the video a page with several opens first. The [playing] element's address when a
     * video has it; else the video of the same length (within 2 s) — how a page-built `blob:`
     * stream is told apart; else, for a page-built stream, the manifest the page loaded when
     * that player started. Without a match: the page's own videos before what looks like a
     * preview ([looksLikePreview]), then a known length of a minute or more (the longer first;
     * it beats a stated size), then the picture height, then the stated size; the earlier one
     * on a tie. An element that itself looks like a preview (a muted loop, a thumbnail's clip)
     * is not taken as the page's player.
     */
    fun mainVideo(videos: List<MediaGroup>, playing: PlayingVideo?): MediaGroup? {
        if (videos.isEmpty()) return null
        val player = playing?.takeUnless { it.looksLikePreview }
        player?.url?.let { url ->
            videos.firstOrNull { video -> video.candidates.any { it.mediaUrl == url } }
                ?.let { return it }
        }
        player?.durationMillis?.let { length ->
            videos.filter { video -> video.lengthGap(length) <= LENGTH_MATCH_MILLIS }
                .minByOrNull { video -> video.lengthGap(length) }
                ?.let { return it }
        }
        if (player?.pageBuilt == true) {
            startedWith(videos, player.startedAtEpochMs)?.let { return it }
        }
        return videos.withIndex().maxWithOrNull(
            compareBy<IndexedValue<MediaGroup>>(
                { (_, video) -> if (looksLikePreview(video, videos)) 0 else 1 },
                { (_, video) -> video.durationMillis?.takeIf { it >= LONG_VIDEO_MILLIS } ?: -1L },
                { (_, video) -> video.candidates.maxOf { it.height ?: -1 } },
                { (_, video) -> video.candidates.maxOf { it.contentLengthBytes ?: -1L } },
                { (_, video) -> video.durationMillis ?: -1L },
                { (index, _) -> -index },
            ),
        )?.value
    }

    /** P24: an address whose path names a preview: `preview`, `thumb`, `teaser` or `sprite`. */
    fun isPreviewAddress(url: String): Boolean {
        val path = url.substringAfter("://", missingDelimiterValue = url)
            .substringAfter('/', missingDelimiterValue = "")
            .substringBefore('?')
            .substringBefore('#')
        return PREVIEW_WORDS.containsMatchIn(path)
    }

    /**
     * The manifest group a page-built player most likely reads: the only one, else the one
     * loaded last before the player started ([startedAt], with a little slack for the clock),
     * else the one loaded nearest to that time.
     */
    private fun startedWith(videos: List<MediaGroup>, startedAt: Long?): MediaGroup? {
        val manifests = videos.mapNotNull { video ->
            if (looksLikePreview(video, videos)) return@mapNotNull null
            val loaded = video.candidates
                .filter { it.kind == MediaKind.HLS || it.kind == MediaKind.DASH }
                .minOfOrNull { it.observedAtEpochMs }
                ?: return@mapNotNull null
            video to loaded
        }
        manifests.singleOrNull()?.let { return it.first }
        if (startedAt == null || manifests.isEmpty()) return null
        return (
            manifests.filter { (_, loaded) -> loaded <= startedAt + START_SLACK_MILLIS }
                .maxByOrNull { (_, loaded) -> loaded }
                ?: manifests.minByOrNull { (_, loaded) -> abs(loaded - startedAt) }
            )?.first
    }

    private fun MediaGroup.lengthGap(length: Long): Long =
        durationMillis?.let { abs(it - length) } ?: Long.MAX_VALUE

    /** The group that holds [candidate], when it is one of [candidates]. */
    fun containing(candidates: List<MediaCandidate>, candidate: MediaCandidate): MediaGroup? =
        of(candidates).firstOrNull { group -> candidate in group.candidates }

    /** [title] without the " — label" an adapter adds for one quality, else [title] itself. */
    fun baseTitle(title: String?): String? {
        val text = title?.trim()?.takeIf(String::isNotEmpty) ?: return null
        if (LABEL_SEPARATOR !in text) return text
        return text.substringBeforeLast(LABEL_SEPARATOR).trim().takeIf(String::isNotEmpty) ?: text
    }

    /** The " — label" part of an adapter's title, for example "720p" or "HD". */
    fun titleLabel(title: String?): String? {
        val text = title?.trim() ?: return null
        if (LABEL_SEPARATOR !in text) return null
        return text.substringAfterLast(LABEL_SEPARATOR).trim().takeIf(String::isNotEmpty)
    }

    private fun keyOf(candidate: MediaCandidate, index: Int): String {
        candidate.videoId?.trim()?.takeIf(String::isNotEmpty)?.let { return "id:$it" }
        // P24: two previews of one length are two clips, not two qualities of one video.
        if (candidate.pageRole == PageMediaRole.PREVIEW) {
            return "item:$index:${candidate.mediaUrl.hashCode()}"
        }
        val seconds = candidate.durationMillis?.takeIf { it > 0 }?.let { it / MILLIS_PER_SECOND }
        if (seconds != null) return "page:${candidate.pageUrl}#$seconds"
        return "item:$index:${candidate.mediaUrl.hashCode()}"
    }

    /**
     * A named video drops the adapter's quality suffix, as do qualities that share one base
     * title; a lone file from a page keeps its whole title.
     */
    private fun titleOf(members: List<MediaCandidate>): String? {
        val titled = members.mapNotNull { it.title?.trim()?.takeIf(String::isNotEmpty) }
        val first = titled.firstOrNull() ?: return null
        val named = members.any { it.videoId != null }
        val bases = titled.map(::baseTitle).distinct()
        return if (named || (titled.size > 1 && bases.size == 1)) baseTitle(first) else first
    }

    private const val LABEL_SEPARATOR = " — "
    private const val MILLIS_PER_SECOND = 1_000L

    /** P24: a video of a minute or more is long; a clip under it next to one is a preview. */
    private const val LONG_VIDEO_MILLIS = 60_000L

    /** P24: a page-built player and a file of its length are one video within 2 s. */
    private const val LENGTH_MATCH_MILLIS = 2_000L

    /** P24: the player starts a little after its manifest loads; the clocks are not exact. */
    private const val START_SLACK_MILLIS = 2_000L
    private val PREVIEW_WORDS = Regex("(?i)preview|thumb|teaser|sprite")
}
