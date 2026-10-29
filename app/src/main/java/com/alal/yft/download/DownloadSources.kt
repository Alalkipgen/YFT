package com.alal.yft.download

import com.alal.yft.core.download.StoredDownloadTask
import com.alal.yft.core.model.download.DownloadTaskStatus
import com.alal.yft.core.model.media.CandidateSource
import com.alal.yft.core.model.media.FreshLinks
import com.alal.yft.core.model.media.MediaAsset
import com.alal.yft.core.model.media.MediaCandidate
import com.alal.yft.core.model.media.MediaGroups
import com.alal.yft.core.model.media.MediaKind
import com.alal.yft.core.model.media.MediaTrackType
import com.alal.yft.core.model.media.MediaVariant
import com.alal.yft.download.policy.NetworkSnapshot
import com.alal.yft.download.policy.NetworkStatusSource
import com.alal.yft.feature.detectedmedia.LookupOwner
import java.util.Locale
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Track E: where a download came from, as the sheet (or Download as) knew it when the user
 * tapped Download: the page's file the user chose ([candidate]: its page, video and facts), the
 * file before an MP3 or M4A conversion ([base]), who looked the video up ([owner]; null when the
 * screen does not know: the browser's rule) and the agent of that lookup. Memory only.
 */
data class DownloadOrigin(
    val candidate: MediaCandidate,
    val base: MediaVariant,
    val owner: LookupOwner? = null,
    val userAgent: String? = null,
) {
    override fun toString(): String = "DownloadOrigin(owner=$owner, kind=${candidate.kind})"
}

/** The conversion a download makes on the phone: the video's sound as M4A, an MP3, or none. */
data class DownloadConversion(
    val audioFromVideo: Boolean = false,
    val mp3Kbps: Int? = null,
) {
    companion object {
        fun of(variant: MediaVariant): DownloadConversion = DownloadConversion(
            audioFromVideo = variant.audioFromVideo,
            mp3Kbps = variant.mp3?.bitrateKbps,
        )
    }
}

/**
 * Track E (E0 §6 Q3): the facts that name a download's quality across two lookups of one video.
 * A variant's id is no key (the resolver numbers by position), so these are: the kind, the track
 * type, whether the sound is merged in, the picture's height, width and frame rate, the
 * container, the codecs and the merged sound's codecs (by family, "avc1", "mp4a"), the site's
 * own label (Vimeo's rendition, YouTube's "720p60"), and the bitrate, which only breaks a tie.
 * A fact one side does not state never rules a variant out.
 */
data class DownloadQualityKey(
    val kind: MediaKind,
    val trackType: MediaTrackType,
    val merged: Boolean = false,
    val height: Int? = null,
    val width: Int? = null,
    val framesPerSecond: Int? = null,
    val container: String? = null,
    val codecs: Set<String> = emptySet(),
    val soundCodecs: Set<String> = emptySet(),
    val label: String? = null,
    val bitrateBitsPerSecond: Long? = null,
) {
    /** Whether [other] (a fresh lookup's variant) may be this quality: no stated fact differs. */
    fun accepts(other: DownloadQualityKey): Boolean =
        kind == other.kind &&
            trackType == other.trackType &&
            merged == other.merged &&
            agree(height, other.height) &&
            agree(width, other.width) &&
            (framesPerSecond == null || other.framesPerSecond == null ||
                abs(framesPerSecond - other.framesPerSecond) <= FRAME_RATE_SLACK) &&
            agree(container, other.container) &&
            (codecs.isEmpty() || other.codecs.isEmpty() || codecs == other.codecs) &&
            (soundCodecs.isEmpty() || other.soundCodecs.isEmpty() ||
                soundCodecs == other.soundCodecs) &&
            agree(label, other.label)

    /**
     * Whether a fresh lookup's [candidate] may hold this quality, before it is resolved: its kind
     * (and, for a whole file, its merged sound) and the facts it states. [candidateKind] is the
     * kind of the candidate the download came from.
     */
    fun admits(candidate: MediaCandidate, candidateKind: MediaKind): Boolean {
        if (candidate.kind != candidateKind) return false
        if (candidateKind == MediaKind.DIRECT && (candidate.audioCompanion != null) != merged) {
            return false
        }
        val rate = candidate.framesPerSecond?.takeIf { it > 0 }?.roundToInt()
        return agree(height, candidate.height) &&
            agree(width, candidate.width) &&
            (framesPerSecond == null || rate == null ||
                abs(framesPerSecond - rate) <= FRAME_RATE_SLACK) &&
            (codecs.isEmpty() || candidate.codecs.isEmpty() ||
                codecs == families(candidate.codecs)) &&
            agree(label, labelOf(candidate))
    }

    /** How many stated facts a fresh lookup's [candidate] shares with this key, unresolved. */
    fun sharedWith(candidate: MediaCandidate): Int = listOf(
        height != null && height == candidate.height,
        width != null && width == candidate.width,
        framesPerSecond != null &&
            framesPerSecond == candidate.framesPerSecond?.takeIf { it > 0 }?.roundToInt(),
        codecs.isNotEmpty() && codecs == families(candidate.codecs),
        label != null && label == labelOf(candidate),
        bitrateBitsPerSecond != null && bitrateBitsPerSecond == candidate.bitrateBitsPerSecond,
    ).count { it }

    /** How many stated facts [other] shares with this key; the best match shares the most. */
    fun shared(other: DownloadQualityKey): Int = listOf(
        height != null && height == other.height,
        width != null && width == other.width,
        framesPerSecond != null && framesPerSecond == other.framesPerSecond,
        container != null && container == other.container,
        codecs.isNotEmpty() && codecs == other.codecs,
        soundCodecs.isNotEmpty() && soundCodecs == other.soundCodecs,
        label != null && label == other.label,
        bitrateBitsPerSecond != null && bitrateBitsPerSecond == other.bitrateBitsPerSecond,
    ).count { it }

    /** How far [bitrate] is from this key's; unknown on either side ranks last. */
    fun bitrateGap(bitrate: Long?): Long {
        val own = bitrateBitsPerSecond ?: return UNKNOWN_GAP
        val other = bitrate ?: return UNKNOWN_GAP
        return abs(own - other)
    }

    override fun toString(): String = buildString {
        append("DownloadQualityKey(").append(kind).append(", ").append(trackType)
        if (merged) append(", merged")
        height?.let { append(", ").append(it).append('p') }
        framesPerSecond?.let { append(", ").append(it).append(" fps") }
        container?.let { append(", ").append(it) }
        append(')')
    }

    companion object {
        /** The key of [variant] (a file before any conversion); [label] is the site's own word. */
        fun of(variant: MediaVariant, label: String? = null): DownloadQualityKey =
            DownloadQualityKey(
                kind = variant.kind,
                trackType = variant.trackType,
                merged = variant.audioCompanion != null,
                height = variant.height?.takeIf { it > 0 },
                width = variant.width?.takeIf { it > 0 },
                framesPerSecond = variant.framesPerSecond?.takeIf { it > 0 }?.roundToInt(),
                container = variant.container?.trim()?.trimStart('.')?.lowercase(Locale.US)
                    ?.takeIf(String::isNotEmpty),
                codecs = families(variant.codecs),
                soundCodecs = families(variant.audioCompanion?.codecs.orEmpty()),
                label = label?.trim()?.lowercase(Locale.US)?.takeIf(String::isNotEmpty),
                bitrateBitsPerSecond = variant.bitrateBitsPerSecond?.takeIf { it > 0 },
            )

        /** The site's own quality word in [candidate]'s title ("Title — 720p"), lower case. */
        fun labelOf(candidate: MediaCandidate): String? =
            MediaGroups.titleLabel(candidate.title)?.lowercase(Locale.US)

        /** "avc1.64001F" and "avc1.4d401e" are one family, "avc1"; "mp4a.40.2" is "mp4a". */
        fun families(codecs: List<String>): Set<String> = codecs
            .map { it.trim().substringBefore('.').lowercase(Locale.US) }
            .filter(String::isNotEmpty)
            .toSet()

        private fun <T> agree(first: T?, second: T?): Boolean =
            first == null || second == null || first == second

        /** Frame rates are stated rounded or exact (29.97); within 1 they are one rate. */
        private const val FRAME_RATE_SLACK = 1
        private const val UNKNOWN_GAP = Long.MAX_VALUE / 2
    }
}

/**
 * Track E: what a fresh link of one download needs, in memory only (E-D2): the page the lookup
 * asked for, the video it named, who looked it up and how (the agent; whether the site's
 * sign-in went with it), the quality and the conversion, and what names the file (the variant's
 * label, the title, the name it is saved under). [durationMillis] is the video's stated length;
 * [networkChanges] the phone's network changes when the link was found (E-D5'). [mediaPath] (E3)
 * is the file's address without its signature on a page no adapter reads, to find the same file
 * when the page is read again. Its `toString` names no address.
 */
data class DownloadSource(
    val pageUrl: String,
    val videoId: String?,
    val candidateKind: MediaKind,
    val owner: LookupOwner,
    val userAgent: String?,
    val session: Boolean,
    val quality: DownloadQualityKey,
    val conversion: DownloadConversion,
    val label: String?,
    val title: String?,
    val durationMillis: Long?,
    val fileName: String,
    val networkChanges: Long,
    val mediaPath: String? = null,
) {
    /** E3 (E-D8): a page no adapter reads, read again quietly for the same file (P37). */
    val rereadable: Boolean get() = videoId.isNullOrBlank() && !mediaPath.isNullOrBlank()

    /** E2: a site adapter named the video, so its page can be looked up again; E3: or read. */
    val renewable: Boolean get() = !videoId.isNullOrBlank() || rereadable

    override fun toString(): String =
        "DownloadSource(site=${videoId?.substringBefore(':')}, rereadable=$rereadable, " +
            "owner=$owner, session=$session, quality=$quality, conversion=$conversion)"
}

/**
 * Track E: what Downloads shows of the renewals: the rows that a Retry renews (E-D6: only while
 * the app has the row's source), each download's renewal lines for its Details, and (v1.0.2) the
 * downloads whose page, read again for a fresh link, had no player data ([noPlayer]) and each
 * unfinished download's picked quality ("1080p", [qualities]) for its row.
 */
data class DownloadRenewals(
    val renewable: Set<String> = emptySet(),
    val notes: Map<String, List<String>> = emptyMap(),
    val noPlayer: Set<String> = emptySet(),
    val qualities: Map<String, String> = emptyMap(),
) {
    companion object {
        val None = DownloadRenewals()
    }
}

/**
 * Track E: every download's [DownloadSource], in memory only (E-D2). A source is remembered when
 * the download is queued and dropped when it completes, is cancelled or deleted ([prune]) and by
 * Clear browsing data ([clear]); nothing is written to disk, so after a restart there is none.
 * v1.0.2 (item 50): also the quality each download was picked as ([picked]), for its row until
 * it finishes; it goes with the task, as the source does, but stays through Clear browsing data
 * (a quality's name is no browsing data). After a restart the row reads as before.
 * [adapterPage] says whether a site adapter reads a page: there only the adapter's video is
 * renewed, never a file it did not name (E3).
 */
class DownloadSources(
    private val sessions: LookupSessions = LookupSessions.None,
    private val adapterPage: (pageUrl: String) -> Boolean = { false },
    private val networkChanges: () -> Long = { 0L },
) {
    private val lock = Any()
    private val sources = LinkedHashMap<String, DownloadSource>()

    /** The tasks [prune] saw in the queue, so a source remembered first is not dropped. */
    private val seen = mutableSetOf<String>()
    private val notes = LinkedHashMap<String, List<String>>()

    /** The downloads whose page, read again for their last fresh link, had no player data. */
    private val noPlayer = mutableSetOf<String>()

    /** v1.0.2 (item 50): each unfinished download's picked quality, "1080p" or "4K". */
    private val qualities = LinkedHashMap<String, String>()
    private val mutableRenewals = MutableStateFlow(DownloadRenewals.None)
    val renewals: StateFlow<DownloadRenewals> = mutableRenewals.asStateFlow()

    /**
     * Remembers where [taskId]'s file came from: [origin], the [asset] and [variant] it queued
     * (after the conversion) and the [fileName] it is saved under. Whether the site's sign-in
     * went with the lookup follows the owner's rule now: Home sends only TikTok's or Instagram's
     * sign-in, the browser the site's cookies it holds. A file no adapter named keeps its
     * address without the signature, unless it is a pasted file link or the page itself (E3).
     */
    fun remember(
        taskId: String,
        origin: DownloadOrigin,
        asset: MediaAsset,
        variant: MediaVariant,
        fileName: String,
    ) {
        val candidate = origin.candidate
        val owner = origin.owner ?: LookupOwner.BROWSER
        val pageUrl = candidate.pageUrl
        val videoId = candidate.videoId?.takeIf(String::isNotBlank)
        val source = DownloadSource(
            pageUrl = pageUrl,
            videoId = videoId,
            candidateKind = candidate.kind,
            owner = owner,
            userAgent = origin.userAgent ?: candidate.requestContext.userAgent,
            session = sessions.cookieFor(owner, pageUrl) != null,
            // A page's own title is no quality word: only an adapter's label names the quality.
            quality = DownloadQualityKey.of(
                origin.base,
                DownloadQualityKey.labelOf(candidate).takeIf { videoId != null },
            ),
            conversion = DownloadConversion.of(variant),
            label = variant.label,
            title = asset.title,
            durationMillis = (asset.durationMillis ?: origin.base.durationMillis
                ?: candidate.durationMillis)?.takeIf { it > 0 },
            fileName = fileName,
            networkChanges = networkChanges(),
            mediaPath = if (videoId == null) pagePath(candidate) else null,
        )
        synchronized(lock) {
            sources[taskId] = source
            publishLocked()
        }
    }

    operator fun get(taskId: String): DownloadSource? = synchronized(lock) { sources[taskId] }

    /** v1.0.2 (item 50): [taskId] was picked as [quality] ("1080p"), for its row until done. */
    fun picked(taskId: String, quality: String) {
        synchronized(lock) {
            if (qualities.put(taskId, quality) != quality) publishLocked()
        }
    }

    /** A fresh link came for [taskId]: its network moment is now (E-D5'). */
    fun renewed(taskId: String) {
        synchronized(lock) {
            val source = sources[taskId] ?: return
            sources[taskId] = source.copy(networkChanges = networkChanges())
        }
    }

    /** A Details line for [taskId] ("Fresh link (403 at 41.0 MB): resumed at 41.0 MB"). */
    fun note(taskId: String, line: String) {
        synchronized(lock) {
            if (taskId !in sources) return
            notes[taskId] = (notes[taskId].orEmpty() + line).takeLast(MAX_NOTES)
            publishLocked()
        }
    }

    /**
     * v1.0.2: whether [taskId]'s page, read again for a fresh link, had no player data (a notice,
     * a check). Retry would read the same page again, so Downloads says to reopen it in the
     * browser instead; the next answer of a renewal replaces it.
     */
    fun pageWithoutPlayer(taskId: String, without: Boolean) {
        synchronized(lock) {
            if (taskId !in sources) return
            val changed = if (without) noPlayer.add(taskId) else noPlayer.remove(taskId)
            if (changed) publishLocked()
        }
    }

    fun forget(taskId: String) {
        synchronized(lock) {
            sources.remove(taskId)
            notes.remove(taskId)
            seen.remove(taskId)
            noPlayer.remove(taskId)
            qualities.remove(taskId)
            publishLocked()
        }
    }

    /**
     * Clear browsing data: every source goes, as the lookups read with that session do. The
     * picked qualities stay, and so does what [prune] saw of their tasks.
     */
    fun clear() {
        synchronized(lock) {
            sources.clear()
            notes.clear()
            seen.retainAll(qualities.keys)
            noPlayer.clear()
            publishLocked()
        }
    }

    /**
     * Drops the sources of downloads that completed, were cancelled or deleted, given the
     * queue's [tasks]. A source whose task the queue does not list yet stays: the enqueue
     * remembers it just before the queue gets the task. A completed download keeps its
     * renewal lines (no address) for its Details until it is removed from the list; its picked
     * quality goes, as its row names its file's own quality then.
     */
    fun prune(tasks: List<StoredDownloadTask>) {
        val byId = tasks.associateBy(StoredDownloadTask::id)
        synchronized(lock) {
            var changed = false
            (sources.keys + notes.keys + qualities.keys).toSet().forEach { id ->
                val task = byId[id]
                when {
                    task == null -> if (id in seen) {
                        sources.remove(id)
                        notes.remove(id)
                        seen.remove(id)
                        noPlayer.remove(id)
                        qualities.remove(id)
                        changed = true
                    }
                    task.status == DownloadTaskStatus.CANCELLED -> {
                        sources.remove(id)
                        notes.remove(id)
                        seen.remove(id)
                        noPlayer.remove(id)
                        qualities.remove(id)
                        changed = true
                    }
                    task.status == DownloadTaskStatus.COMPLETED -> {
                        seen += id
                        noPlayer.remove(id)
                        if (sources.remove(id) != null) changed = true
                        if (qualities.remove(id) != null) changed = true
                    }
                    else -> seen += id
                }
            }
            if (changed) publishLocked()
        }
    }

    private fun publishLocked() {
        mutableRenewals.value = DownloadRenewals(
            renewable = sources.filterValues(DownloadSource::renewable).keys.toSet(),
            notes = notes.toMap(),
            noPlayer = noPlayer.toSet(),
            qualities = qualities.toMap(),
        )
    }

    /**
     * E3: [candidate]'s file without its signature, for a page read again; null for a pasted
     * file link, a page that is the file itself, and a page a site adapter reads.
     */
    private fun pagePath(candidate: MediaCandidate): String? {
        if (CandidateSource.PASTED_URL in candidate.sources) return null
        val path = FreshLinks.unsigned(candidate.mediaUrl).takeIf(String::isNotBlank)
            ?: return null
        if (path == FreshLinks.unsigned(candidate.pageUrl)) return null
        // Unknown counts as an adapter's page: no re-read rather than the wrong one.
        val adapters = runCatching { adapterPage(candidate.pageUrl) }.getOrDefault(true)
        return path.takeUnless { adapters }
    }

    override fun toString(): String =
        "DownloadSources(count=${synchronized(lock) { sources.size }})"

    private companion object {
        const val MAX_NOTES = 4
    }
}

/**
 * Track E (E-D5'): how many times the phone's network changed in this process, read only from
 * the app's own [NetworkStatusSource]: Wi-Fi and mobile data swapped while connected, or the
 * network dropped and came back. Never an address check.
 */
class NetworkChanges(source: NetworkStatusSource, scope: CoroutineScope) {
    private val count = AtomicLong(0)

    init {
        scope.launch {
            var previous: NetworkSnapshot? = null
            source.snapshot.collect { snapshot ->
                if (previous?.let { changed(it, snapshot) } == true) count.incrementAndGet()
                previous = snapshot
            }
        }
    }

    val value: Long get() = count.get()

    companion object {
        fun changed(before: NetworkSnapshot, after: NetworkSnapshot): Boolean =
            (!before.connected && after.connected) ||
                (before.connected && after.connected && before.unmetered != after.unmetered)
    }
}
