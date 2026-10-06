package com.alal.yft.core.model.media

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
     * The video a page with several opens first (P12): the one playing ([playingUrl], the
     * address its player reads), else the largest by stated size, then picture height, then
     * length; the earlier one on a tie.
     */
    fun mainVideo(videos: List<MediaGroup>, playingUrl: String? = null): MediaGroup? {
        playingUrl?.let { url ->
            videos.firstOrNull { video -> video.candidates.any { it.mediaUrl == url } }
                ?.let { return it }
        }
        return videos.withIndex().maxWithOrNull(
            compareBy<IndexedValue<MediaGroup>>(
                { (_, video) -> video.candidates.maxOf { it.contentLengthBytes ?: -1L } },
                { (_, video) -> video.candidates.maxOf { it.height ?: -1 } },
                { (_, video) -> video.durationMillis ?: -1L },
                { (index, _) -> -index },
            ),
        )?.value
    }

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
}
