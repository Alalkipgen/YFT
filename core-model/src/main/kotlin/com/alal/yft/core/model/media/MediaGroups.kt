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
 * when the page's player setup lists them as qualities of one video
 * ([MediaCandidate.pageVideoKey], P28), or when they come from the same page and state the same
 * length. Anything else stays on its
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
     * like a preview, nothing is set apart: the page's videos are all it has. P43: with
     * [hideAds], a video proven an ad by a sign only ads get ([PageVideoProof.isProvenAd]: an ad
     * network, an ad address, an ad break) is not listed at all.
     */
    fun ofPage(
        groups: List<MediaGroup>,
        facts: PageVideoFacts? = null,
        hideAds: Boolean = false,
    ): PageVideoList {
        val listed = if (hideAds) {
            groups.filterNot { PageVideoProof.isProvenAd(it, facts) }
        } else {
            groups
        }
        val (previews, videos) = listed.partition { looksLikePreview(it, listed, facts) }
        return if (videos.isEmpty()) PageVideoList(listed) else PageVideoList(videos, previews)
    }

    /**
     * P28: [candidates] with the page's word on each file when it states its video's length
     * ([facts]): a file of that length is the page's video ([PageMediaRole.MAIN]) even when an
     * ad list marked it, and a file far shorter is an ad or a preview
     * ([PageMediaRole.PREVIEW]). A site adapter's files, the files the page itself names and
     * files of unknown length keep their role; without a stated length nothing changes.
     */
    fun withPageRoles(
        candidates: List<MediaCandidate>,
        facts: PageVideoFacts?,
    ): List<MediaCandidate> {
        if (facts?.durationMillis == null) return candidates
        return candidates.map { candidate ->
            when {
                candidate.videoId != null || candidate.pageRole == PageMediaRole.MAIN -> candidate
                facts.matchesLength(candidate.durationMillis) ->
                    candidate.copy(pageRole = PageMediaRole.MAIN)
                facts.isFarShorter(candidate.durationMillis) ->
                    candidate.copy(pageRole = PageMediaRole.PREVIEW)
                else -> candidate
            }
        }
    }

    /**
     * P28: whether [video] may be the ad a page's player shows before the page's video: the
     * page states a video of two minutes or more ([PageVideoFacts.statesLongVideo]) and [video]
     * is not of that length, being far shorter or marked as an ad ([PageMediaRole.PREVIEW]).
     * A site adapter's video never is, nor one of unknown length that nothing marked.
     */
    fun mayBeAdBefore(video: MediaGroup, facts: PageVideoFacts?): Boolean {
        if (facts == null || !facts.statesLongVideo) return false
        val files = video.candidates
        if (files.any { it.videoId != null }) return false
        val length = video.durationMillis
        if (facts.matchesLength(length)) return false
        return facts.isFarShorter(length) || files.all { it.pageRole == PageMediaRole.PREVIEW }
    }

    /**
     * P28: [group] with the page's title and picture ([facts]) where its files name none, so the
     * sheet never says "Video" with a blank picture for a page that names its video.
     */
    fun withPageFacts(group: MediaGroup, facts: PageVideoFacts?): MediaGroup {
        if (facts == null || facts.title == null && facts.thumbnailUrl == null) return group
        if (group.candidates.any { it.videoId != null }) return group
        val candidates = group.candidates.map { candidate ->
            candidate.copy(
                title = candidate.title ?: facts.title,
                thumbnailUrl = candidate.thumbnailUrl ?: facts.thumbnailUrl,
            )
        }
        return group.copy(title = group.title ?: facts.title, candidates = candidates)
    }

    /**
     * P29/P45: the page's own video once the video first tapped ([skipped]) proved an ad: the
     * best of the page's other [videos] in [mainVideo]'s order, never one that looks like an ad
     * or a preview. Null when the page has none. Videos that share a file with [skipped] are
     * left out. Never used after a video that failed: the sheet shows only the tapped video.
     */
    fun pageVideoAfterAd(
        videos: List<MediaGroup>,
        skipped: Collection<MediaGroup>,
        facts: PageVideoFacts? = null,
    ): MediaGroup? {
        val ads = skipped.flatMap { group -> group.candidates.map { it.mediaUrl } }.toSet()
        val others = videos.filter { video -> video.candidates.none { it.mediaUrl in ads } }
        val playable = others.filterNot { looksLikePreview(it, videos, facts) }
        return mainVideo(playable, playing = null, facts = facts)
    }

    /**
     * P29: the page's current files of [group], for Try again: the video in [videos] that shares
     * a file with it, the same address without its signed query, or else its length (within
     * 2 s). Null when the page no longer has it.
     */
    fun refreshed(group: MediaGroup, videos: List<MediaGroup>): MediaGroup? {
        val urls = group.candidates.map { it.mediaUrl }.toSet()
        videos.firstOrNull { video -> video.candidates.any { it.mediaUrl in urls } }
            ?.let { return it }
        val paths = group.candidates.map { unsigned(it.mediaUrl) }.toSet()
        videos.firstOrNull { video -> video.candidates.any { unsigned(it.mediaUrl) in paths } }
            ?.let { return it }
        val length = group.durationMillis ?: return null
        return videos.filter { it.lengthGap(length) <= LENGTH_MATCH_MILLIS }
            .minByOrNull { it.lengthGap(length) }
    }

    private fun unsigned(url: String): String = url.substringBefore('?').substringBefore('#')

    /**
     * P24: whether [video] looks like a preview or an ad rather than [page]'s own video: the page
     * marked all its files so ([PageMediaRole.PREVIEW]: a thumbnail's clip, a muted loop, an
     * ad's file), their addresses say so (`preview`, `thumb`, `teaser`, `sprite`), or it is not
     * known to be a minute or more long while the page names another video as its own
     * ([PageMediaRole.MAIN]) or, being shorter, has a video of a minute or more. A video a site
     * adapter named, or one the page names as its own, never is. P28: with the page's [facts], a
     * video of the stated length never is, and one under half of it is.
     */
    fun looksLikePreview(
        video: MediaGroup,
        page: List<MediaGroup>,
        facts: PageVideoFacts? = null,
    ): Boolean {
        val files = video.candidates
        if (files.any { it.videoId != null || it.pageRole == PageMediaRole.MAIN }) return false
        val length = video.durationMillis
        // P28: the length the page states is its video's, whatever an ad list says.
        if (facts?.matchesLength(length) == true) return false
        if (files.all { it.pageRole == PageMediaRole.PREVIEW }) return true
        if (files.all { isPreviewAddress(it.mediaUrl) }) return true
        // P28: far shorter than the length the page states: the ad before its video.
        if (facts?.isFarShorter(length) == true) return true
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
     *
     * P28: when the page states its video's length ([facts]): (a) the video of that length
     * first; (b) the videos the page or its player setup names ([PageMediaRole.MAIN]) before
     * the rest; (c) the playing element only when its length is unknown or matches — a 0:30
     * element on a 16:24 page is the ad before its video; (d) the rules above. With or without
     * facts, a file marked as an ad ([PageMediaRole.PREVIEW]) is not taken for the player.
     */
    fun mainVideo(
        videos: List<MediaGroup>,
        playing: PlayingVideo?,
        facts: PageVideoFacts? = null,
    ): MediaGroup? {
        if (videos.isEmpty()) return null
        if (facts?.durationMillis != null) {
            // P28 (a): the video of the length the page states.
            videos.filter { facts.matchesLength(it.durationMillis) }
                .minByOrNull { video -> video.lengthGap(facts.durationMillis) }
                ?.let { return it }
            // P28 (b): the videos the page or its player setup names, before the rest.
            val named = videos.filter { video ->
                video.candidates.any { it.pageRole == PageMediaRole.MAIN }
            }
            if (named.isNotEmpty()) return ranked(named, videos, facts)
        }
        // P28 (c): an element far from the stated length is the ad before the page's video.
        val player = playing?.takeUnless { it.looksLikePreview }
            ?.takeUnless { facts?.durationMillis != null && it.durationMillis != null &&
                !facts.matchesLength(it.durationMillis) }
        // P28: a file the page or an ad list marks as an ad is not taken for the page's player.
        val playable = videos.filterNot { video ->
            video.candidates.all { it.pageRole == PageMediaRole.PREVIEW }
        }
        player?.url?.let { url ->
            playable.firstOrNull { video -> video.candidates.any { it.mediaUrl == url } }
                ?.let { return it }
        }
        player?.durationMillis?.let { length ->
            playable.filter { video -> video.lengthGap(length) <= LENGTH_MATCH_MILLIS }
                .minByOrNull { video -> video.lengthGap(length) }
                ?.let { return it }
        }
        if (player?.pageBuilt == true) {
            startedWith(videos, player.startedAtEpochMs, facts)?.let { return it }
        }
        return ranked(videos, videos, facts)
    }

    /** P24's order without a match: see [mainVideo]. */
    private fun ranked(
        videos: List<MediaGroup>,
        page: List<MediaGroup>,
        facts: PageVideoFacts?,
    ): MediaGroup? =
        videos.withIndex().maxWithOrNull(
            compareBy<IndexedValue<MediaGroup>>(
                { (_, video) -> if (looksLikePreview(video, page, facts)) 0 else 1 },
                { (_, video) -> video.durationMillis?.takeIf { it >= LONG_VIDEO_MILLIS } ?: -1L },
                { (_, video) -> video.candidates.maxOf { it.height ?: -1 } },
                { (_, video) -> video.candidates.maxOf { it.contentLengthBytes ?: -1L } },
                { (_, video) -> video.durationMillis ?: -1L },
                { (index, _) -> -index },
            ),
        )?.value

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
    private fun startedWith(
        videos: List<MediaGroup>,
        startedAt: Long?,
        facts: PageVideoFacts?,
    ): MediaGroup? {
        val manifests = videos.mapNotNull { video ->
            if (looksLikePreview(video, videos, facts)) return@mapNotNull null
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
        // P28: the qualities a page's player setup lists are one video.
        candidate.pageVideoKey?.trim()?.takeIf(String::isNotEmpty)?.let {
            return "setup:${candidate.pageUrl}#$it"
        }
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
