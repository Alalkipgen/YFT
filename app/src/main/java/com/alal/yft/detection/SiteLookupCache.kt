package com.alal.yft.detection

import com.alal.yft.core.download.StoredDownloadTask
import com.alal.yft.core.model.download.DownloadFailureReason
import com.alal.yft.core.model.download.DownloadTaskStatus
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Deferred

/**
 * P17: what a site adapter answered for one video, kept in memory for a few minutes so Home, the
 * browser and a reopened sheet share one lookup instead of asking the site again.
 *
 * An entry is the video's site and content ID plus whether the user's session was used; at most
 * [MAX_ENTRIES] are kept, each until the earliest expiry its links state or [MAX_AGE_MS],
 * whichever comes first. A lookup that asks again (Try again), a lookup that failed and a
 * download that got HTTP 403 or 410 drop the video's entries. Nothing is written to disk, and
 * neither keys nor addresses are ever logged (their `toString` names no video).
 */
@Singleton
class SiteLookupCache @Inject constructor() {
    private val lock = Any()
    private val entries = object : LinkedHashMap<SiteLookupKey, Entry>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<SiteLookupKey, Entry>) =
            size > MAX_ENTRIES
    }
    private val running = mutableMapOf<SiteLookupKey, RunningLookup>()
    private val downloads = object : LinkedHashMap<String, String>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, String>) =
            size > MAX_DOWNLOADS
    }

    /** The video's answer still valid at [nowEpochMs], or null. */
    internal fun get(key: SiteLookupKey, nowEpochMs: Long): SiteAdapterOutcome.Detected? =
        synchronized(lock) {
            val entry = entries[key] ?: return null
            if (nowEpochMs >= entry.keptUntilEpochMs) {
                entries.remove(key)
                null
            } else {
                entry.outcome
            }
        }

    internal fun put(key: SiteLookupKey, outcome: SiteAdapterOutcome.Detected, nowEpochMs: Long) {
        val linksExpire = outcome.candidates.flatMap { candidate ->
            listOfNotNull(candidate.expiresAtEpochMs, candidate.audioCompanion?.expiresAtEpochMs)
        }.minOrNull()
        val keptUntil = minOf(nowEpochMs + MAX_AGE_MS, linksExpire ?: Long.MAX_VALUE)
        if (keptUntil <= nowEpochMs) return
        val videoIds = outcome.candidates.mapNotNullTo(mutableSetOf()) { it.videoId }
        synchronized(lock) { entries[key] = Entry(outcome, keptUntil, videoIds) }
    }

    /** Drops every entry of the video [key] names, with or without the session. */
    internal fun forget(key: SiteLookupKey) {
        synchronized(lock) {
            entries.keys.removeAll { it.siteId == key.siteId && it.contentId == key.contentId }
        }
    }

    /** Drops the entries of the video with this "site:contentId" or candidate video ID. */
    fun forgetVideo(videoId: String) {
        synchronized(lock) {
            entries.entries.removeAll { (key, entry) ->
                key.videoKey == videoId || videoId in entry.videoIds
            }
        }
    }

    /** Remembers which video the download [taskId] came from, so its failure can drop it. */
    fun rememberDownload(taskId: String, videoId: String) {
        synchronized(lock) { downloads[taskId] = videoId }
    }

    /** The download [taskId] got HTTP 403 or 410: its video's links are no longer good. */
    fun forgetDownload(taskId: String) {
        val videoId = synchronized(lock) { downloads.remove(taskId) } ?: return
        forgetVideo(videoId)
    }

    /** Downloads among [tasks] whose links stopped working (HTTP 403, 410) drop their video. */
    fun forgetBrokenDownloads(tasks: List<StoredDownloadTask>) {
        tasks.filter { task ->
            task.status == DownloadTaskStatus.NEEDS_REFRESH ||
                task.status == DownloadTaskStatus.FAILED && task.failureReason in LINK_FAILURES
        }.forEach { forgetDownload(it.id) }
    }

    /** Clear browsing data: answers read with the old session must not outlive it. */
    fun clear() {
        synchronized(lock) {
            entries.clear()
            downloads.clear()
        }
    }

    /**
     * Joins the running lookup of [key], or starts one with [start]. Each caller must
     * [leave] once it has its answer or was cancelled.
     */
    internal fun join(
        key: SiteLookupKey,
        start: () -> Deferred<SiteAdapterOutcome>,
    ): RunningLookup = synchronized(lock) {
        val lookup = running[key]?.takeIf { it.work.isActive }
            ?: RunningLookup(start()).also { running[key] = it }
        lookup.waiters += 1
        lookup
    }

    /** The last caller to leave a running lookup stops it: nobody waits for its answer. */
    internal fun leave(key: SiteLookupKey, lookup: RunningLookup) {
        synchronized(lock) {
            lookup.waiters -= 1
            if (lookup.waiters > 0) return
            if (running[key] === lookup) running.remove(key)
            if (lookup.work.isActive) lookup.work.cancel()
        }
    }

    internal class RunningLookup(val work: Deferred<SiteAdapterOutcome>) {
        var waiters = 0
    }

    private class Entry(
        val outcome: SiteAdapterOutcome.Detected,
        val keptUntilEpochMs: Long,
        val videoIds: Set<String>,
    )

    internal companion object {
        const val MAX_ENTRIES = 20
        const val MAX_AGE_MS = 10 * 60 * 1_000L
        private const val MAX_DOWNLOADS = 50

        /** HTTP 403 and 410, and a link the site said has expired. */
        val LINK_FAILURES = setOf(
            DownloadFailureReason.ACCESS_DENIED,
            DownloadFailureReason.GONE,
            DownloadFailureReason.EXPIRED_URL,
        )
    }
}

/** P17: one video's lookup as the cache keeps it; [session] is whether cookies were sent. */
internal data class SiteLookupKey(
    val siteId: String,
    val contentId: String,
    val session: Boolean,
) {
    val videoKey: String get() = "$siteId:$contentId"

    override fun toString(): String = "SiteLookupKey(site=$siteId, session=$session)"
}
