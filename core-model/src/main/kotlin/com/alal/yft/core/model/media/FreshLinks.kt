package com.alal.yft.core.model.media

import java.net.URI
import kotlin.math.abs

/** P37: who gave the sheet a file's address, for its Details ("Link from: …"). */
enum class LinkOrigin(val label: String) {
    /** The page's own markup or player script named it ([CandidateSource.DOM]). */
    PAGE_SCRIPT("page script"),

    /** The page's player itself asked for it while the page played. */
    PLAYER_REQUEST("player request"),

    /** The sheet's quiet second read of the page found it ([CandidateSource.PAGE_REREAD]). */
    PAGE_READ_AGAIN("page read again"),

    /** The user pasted the file's own address. */
    PASTED_LINK("pasted link"),
}

/** P37: whether a link's own expiry time (a query value) has passed by the phone's clock. */
enum class LinkExpiry(val label: String) {
    PASSED("passed"),
    NOT_PASSED("not passed"),

    /** The link states no expiry time. */
    NONE("none"),
}

/**
 * P37 (FIX_ADD_PLAN R17, R18): on a site without an adapter the links the page's script names
 * can be dead for YFT while the page's player plays: the addresses the player itself asked for
 * are the freshest proof of a working link, so they come first. Pure, so the rules are
 * unit-tested; nothing here returns an address's query.
 */
object FreshLinks {
    private val PLAYER_SOURCES = setOf(
        CandidateSource.REQUEST,
        CandidateSource.REDIRECT,
        CandidateSource.DOWNLOAD_LISTENER,
    )

    /** Query names that carry a link's expiry time (seconds or milliseconds since 1970). */
    private val EXPIRY_NAMES =
        setOf("validto", "valid_to", "expires", "expire", "exp", "e", "x-expires")

    /** Times before 2001-09-09 are a duration or something else, never an expiry time. */
    private const val EARLIEST_EXPIRY_SECONDS = 1_000_000_000L
    private const val MILLIS_TIMESTAMP = 100_000_000_000L
    private const val MILLIS_PER_SECOND = 1_000L
    private const val LENGTH_MATCH_MILLIS = 2_000L

    /** Whether the page's player itself asked for [candidate]'s address. */
    fun isPlayerRequest(candidate: MediaCandidate): Boolean =
        candidate.sources.any { it in PLAYER_SOURCES }

    fun origin(candidate: MediaCandidate): LinkOrigin = when {
        CandidateSource.PAGE_REREAD in candidate.sources -> LinkOrigin.PAGE_READ_AGAIN
        isPlayerRequest(candidate) -> LinkOrigin.PLAYER_REQUEST
        candidate.sources == setOf(CandidateSource.PASTED_URL) -> LinkOrigin.PASTED_LINK
        else -> LinkOrigin.PAGE_SCRIPT
    }

    /**
     * Whether [candidate]'s link has expired at [nowMillis]: the time the source stated, else a
     * numeric expiry-like query value (`validto`, `expires`, `exp`, `e`, `x-expires`).
     */
    fun expiry(candidate: MediaCandidate, nowMillis: Long): LinkExpiry {
        val expiresAt = candidate.expiresAtEpochMs ?: expiryOf(candidate.mediaUrl)
            ?: return LinkExpiry.NONE
        return if (expiresAt <= nowMillis) LinkExpiry.PASSED else LinkExpiry.NOT_PASSED
    }

    /** The expiry time [url]'s query states, in milliseconds; null when it states none. */
    fun expiryOf(url: String): Long? {
        val query = runCatching { URI(url).rawQuery }.getOrNull() ?: return null
        return query.split('&').firstNotNullOfOrNull { parameter ->
            val name = parameter.substringBefore('=').lowercase()
            if (name !in EXPIRY_NAMES) return@firstNotNullOfOrNull null
            val value = parameter.substringAfter('=', "").toLongOrNull()
                ?.takeIf { it >= EARLIEST_EXPIRY_SECONDS }
                ?: return@firstNotNullOfOrNull null
            if (value >= MILLIS_TIMESTAMP) value else value * MILLIS_PER_SECOND
        }
    }

    /**
     * [group] with the addresses the page's player asked for before the links the page's script
     * names: a script link gives way to the player's request of the same file (the same address
     * without its query; the newest request first), and within the group to a player request of
     * the same picture height. What the script stated (title, length, height, the setup's key)
     * stays with the player's address. A site adapter's video is never touched.
     */
    fun playerFirst(group: MediaGroup, pageCandidates: List<MediaCandidate>): MediaGroup {
        if (group.candidates.any { it.videoId != null }) return group
        val requests = pageCandidates.filter { isPlayerRequest(it) && it.isPlayable() }
            .sortedByDescending { it.observedAtEpochMs }
        val members = group.candidates
        val replaced = members.map { candidate ->
            if (isPlayerRequest(candidate)) return@map candidate
            val request = requests.firstOrNull { request ->
                request.mediaUrl != candidate.mediaUrl &&
                    unsigned(request.mediaUrl) == unsigned(candidate.mediaUrl)
            } ?: return@map candidate
            request.stating(candidate)
        }
        val heightsByPlayer = replaced.filter(::isPlayerRequest).mapNotNull { it.height }.toSet()
        val kept = replaced.filterNot { candidate ->
            !isPlayerRequest(candidate) && candidate.height != null &&
                candidate.height in heightsByPlayer
        }.distinctBy { it.mediaUrl }
        val ordered = kept.filter(::isPlayerRequest) + kept.filterNot(::isPlayerRequest)
        return if (ordered == members) group else group.copy(candidates = ordered)
    }

    /**
     * The video the page's player asked for, to try when [failed]'s script links are gone,
     * before P29's next video: a group of [videos] with a player request that shares no
     * address with [tried], is not marked or shaped like an ad or a preview, and has
     * [failed]'s length (or the length the page states, [facts]) when both are known; the
     * matching length first, then the newest request. Null when the player asked for none.
     */
    fun playerVideo(
        failed: MediaGroup,
        videos: List<MediaGroup>,
        tried: Set<String>,
        facts: PageVideoFacts? = null,
    ): MediaGroup? {
        if (failed.candidates.any { it.videoId != null }) return null
        val length = failed.durationMillis ?: facts?.durationMillis
        val offered = videos.filter { video ->
            video.candidates.none { it.videoId != null || it.mediaUrl in tried } &&
                video.candidates.any { isPlayerRequest(it) && it.isPlayable() } &&
                !video.looksLikeAd(facts) &&
                (length == null || video.durationMillis == null ||
                    abs(video.durationMillis!! - length) <= LENGTH_MATCH_MILLIS)
        }
        return offered.maxWithOrNull(
            compareBy<MediaGroup>(
                { video -> if (length != null && video.durationMillis != null) 1 else 0 },
                { video -> video.newestRequestAt() },
            ),
        )?.let { video ->
            video.copy(candidates = video.candidates.filter(::isPlayerRequest))
        }
    }

    /** The same file's address without its query or fragment. */
    fun unsigned(url: String): String = url.substringBefore('#').substringBefore('?')

    private fun MediaCandidate.isPlayable(): Boolean =
        pageRole != PageMediaRole.PREVIEW && !MediaGroups.isPreviewAddress(mediaUrl) &&
            drmHint != true

    private fun MediaGroup.newestRequestAt(): Long =
        candidates.filter(::isPlayerRequest).maxOf { it.observedAtEpochMs }

    private fun MediaGroup.looksLikeAd(facts: PageVideoFacts?): Boolean =
        candidates.all { it.pageRole == PageMediaRole.PREVIEW } ||
            candidates.all { MediaGroups.isPreviewAddress(it.mediaUrl) } ||
            facts?.isFarShorter(durationMillis) == true

    /** This player request with what [script] stated about the same file. */
    private fun MediaCandidate.stating(script: MediaCandidate): MediaCandidate = copy(
        title = title ?: script.title,
        thumbnailUrl = thumbnailUrl ?: script.thumbnailUrl,
        durationMillis = durationMillis ?: script.durationMillis,
        width = width ?: script.width,
        height = height ?: script.height,
        framesPerSecond = framesPerSecond ?: script.framesPerSecond,
        bitrateBitsPerSecond = bitrateBitsPerSecond ?: script.bitrateBitsPerSecond,
        codecs = codecs.ifEmpty { script.codecs },
        pageRole = pageRole ?: script.pageRole,
        pageVideoKey = pageVideoKey ?: script.pageVideoKey,
        mimeType = mimeType ?: script.mimeType,
    )
}
