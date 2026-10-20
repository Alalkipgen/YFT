package com.alal.yft.extractor.master.lookup

import com.alal.yft.extractor.api.ExtractorHttpResult
import com.alal.yft.extractor.api.ExtractorProbeResult
import com.alal.yft.extractor.api.SiteExtractionFailure
import com.alal.yft.extractor.master.modules.youtube.YouTubeUrls
import com.alal.yft.extractor.master.modules.youtube.ladder.LookupMemo
import java.net.URI
import java.nio.ByteBuffer
import java.security.MessageDigest
import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.CancellationException

/**
 * Phase 1.4 item 7 (YouTube routing B, `MASTER_KEY_PHASE1_4_ROUTING_PLAN.md` §3 R1; social plan
 * decision 8): the **per-lookup request record**. One record per user lookup (main + Master),
 * carried in the lookup's coroutine context, in memory only and dropped with the lookup.
 * [LookupRecorder] writes main's asks into it: it wraps the client main's adapters share and,
 * with no record in the context, only passes through.
 *
 * Per ask ([Ask]) it lists who asked, the short hash (method, address, body, authorization
 * state), the HTTP status or failure, whether it was a 429, when it started from the lookup's
 * start and how long it took ([elapsedMillis], [timeLeftMillis]: no time budget of its own). It
 * knows each endpoint asked ([asked]: method, address and body) and whether any 429 came
 * ([rateLimited]; also one of main's player-script fetches, which are not listed: [unlisted]).
 * F2: when main's answer reached the lookup without main asking in it (P17's remembered answer, or
 * another caller's running lookup, which records into that caller's record), it notes how
 * ([reuse]), so a skipped route B can say why.
 *
 * Kept for replay ([replay]; route B's Master wrapper, R2): only main's unauthenticated answers
 * ([LookupAsk.authorized] false) to an HTTPS address that is not a media host
 * ([LookupMemo.memoable]) — a success without the cookies it set, or a failure (no body). A file
 * check is never kept. Each kept answer serves one identical request (method, address, body and
 * headers in order) once. Bounded: at most [maxKept] answers and [maxKeptChars] characters, at
 * most [MAX_LISTED] asks listed (more are counted). Nothing is logged; [toString] and the counts
 * show no address, body or header.
 *
 * F5 (the owner's cookie decision, `MASTER_KEY_PHASE1_4_ROUTING_PLAN.md` §8): a record that names
 * the lookup's video ([watchPageVideoId], set by the app's route-B build for a YouTube lookup) also
 * keeps main's answer to that video's watch page GET (`YouTubeUrls.watchPageFetchUrl`, no body)
 * when main's request carried the tab's cookie and no other credential — main's first such GET in
 * the lookup only, inside the same caps, without the cookies the answer set, and keyed by the same
 * request without its `Cookie` header ([LookupAsk.withoutCookie]): the cookie's value is neither
 * kept nor compared, and only a request that carries no credential is ever answered from it.
 * Every other authorized answer is still never kept. Without a name (every other record) nothing
 * changes.
 *
 * Social plan S0a (`MASTER_KEY_SOCIAL_PLAN.md` §7.1, the site census): a record given [AskLabels]
 * also writes each listed ask's way ([Ask.way]) and its 2xx answer's reading ([Ask.mark],
 * [Ask.shape]: a mark and a short fingerprint, never a value), and counts [repeatedAsks] (an
 * endpoint already asked in this lookup), [asksAfterStop] (an ask to a host that had stopped: it
 * answered 429 or a bot check) and [botChecks], and names the hosts that stopped ([stopHosts]).
 * The labels are read outside the lock and a label that throws is none. A bot check is not a
 * rate limit here: [rateLimited] stays the 429 (route B's rule 3). Without labels (every lookup of
 * the app, route B included) the record is as before: no label, nothing more counted.
 *
 * Social plan 0.4 (§7.3, the census fixes): a labelled record also splits the asks main sent
 * again by a report key of its own — the method, address, body and identity class (whether the
 * ask carries the user's credentials, and the agent it names; never a cookie's value), hashed: an
 * ask whose key was seen is a repeat ([Ask.repeat], [repeatAsks]); one whose method and address
 * were asked with another body or identity class is a variant ([Ask.variant], [variantAsks]).
 * The key is the report's only: [LookupAsk.endpoint] (route B's R2 key), the endpoint rule, its
 * enforcement and the host stops stay as they are, and no key or hash leaves the record.
 */
class LookupRecord(
    private val clock: () -> Long = System::currentTimeMillis,
    private val maxKept: Int = MAX_KEPT,
    private val maxKeptChars: Long = MAX_KEPT_CHARS,
    /** F5: the lookup's YouTube video ID; null (or not an ID): no watch page is kept. */
    watchPageVideoId: String? = null,
    /** S0a: the site census's labels for each ask; null (every lookup of the app): none. */
    private val labels: AskLabels? = null,
) : AbstractCoroutineContextElement(LookupRecord) {
    init {
        require(maxKept in 1..MAX_KEPT)
        require(maxKeptChars in 1..MAX_KEPT_CHARS)
    }

    /** Who asked: main's adapter (through [LookupRecorder]) or Master (route B's new requests). */
    enum class Asker { MAIN, MASTER }

    /**
     * F2 (`MASTER_KEY_PHASE1_4_ROUTING_PLAN.md` §8): how main's answer reached this lookup without
     * main asking in it (P17's shared lookup): its remembered answer, or another caller's running
     * lookup, whose requests go to that caller's record.
     */
    enum class Reuse(val label: String) {
        REMEMBERED("its remembered answer (P17)"),
        JOINED("another caller's running lookup (P17)"),
    }

    /** One ask as listed: no address, body or header. */
    data class Ask(
        val asker: Asker,
        val method: String,
        val hash: String,
        val authorized: Boolean,
        /** False when no answer came (the ask was cancelled). */
        val answered: Boolean,
        /** The answer's HTTP status, or null (a network error, a refused file check). */
        val statusCode: Int?,
        /** Null for an answer that came (2xx, a file that answered); otherwise the failure. */
        val failure: SiteExtractionFailure?,
        val rateLimited: Boolean,
        /** Kept for one replay. */
        val kept: Boolean,
        /** Milliseconds from the lookup's start to the ask, and how long it took. */
        val atMillis: Long,
        val tookMillis: Long,
        /** S0a: the way that asked ([AskLabels.way]); null without labels or when none fits. */
        val way: String? = null,
        /** S0a: what the 2xx answer held ([AskLabels.read]); null without labels or not read. */
        val mark: AnswerMark? = null,
        /** S0a: the answer's short shape fingerprint; null when not read. */
        val shape: String? = null,
        /** S0a: the endpoint was already asked in this lookup (a labelled record only). */
        val repeated: Boolean = false,
        /** S0a: the host had stopped (a 429 or a bot check) before this ask (a labelled record only). */
        val afterStop: Boolean = false,
        /** 0.4: the same report key (method, address, body, identity class) was asked before. */
        val repeat: Boolean = false,
        /** 0.4: the same method and address were asked before with another body or identity class. */
        val variant: Boolean = false,
    )

    /**
     * S0a: an ask's labels, read outside the lock; null: the record has no labels. 0.4: [key] is
     * the ask's report key and [target] its method and address, both hashed.
     */
    private class Label(val way: String?, val reading: AskReading?, val key: String, val target: String)

    private val lock = Any()
    private val startedAt = clock()
    private val listed = ArrayList<Ask>()
    private val counts = IntArray(Asker.entries.size)
    private val endpoints = HashSet<String>()
    private val kept = HashMap<LookupAsk, ArrayDeque<ExtractorHttpResult>>()
    private var keptEntries = 0
    private var keptChars = 0L
    private var keptTotal = 0
    private var replayedTotal = 0
    private var rateLimitedTotal = 0
    private var reused: Reuse? = null

    /** S0a: each host that stopped (a 429 or a bot check) and when, in the order they stopped. */
    private val stopAt = LinkedHashMap<String, Long>()
    private var repeatedTotal = 0
    private var afterStopTotal = 0
    private var botCheckTotal = 0

    /** 0.4: the report keys and the targets (method and address) asked so far (a labelled record only). */
    private val reportKeys = HashSet<String>()
    private val targets = HashSet<String>()
    private var repeatTotal = 0
    private var variantTotal = 0

    /** F5: the address of the lookup's watch page, or null when this record keeps none. */
    private val watchPage: String? = watchPageVideoId?.takeIf(VIDEO_ID::matches)?.let(YouTubeUrls::watchPageFetchUrl)

    /** F5: main's first cookie GET of [watchPage] came (kept or over the caps). */
    private var watchPageTaken = false

    /** F5 (guardrail 7): watch pages asked in this lookup, main's or Master's ([LookupAsk.watchPage]). */
    private var watchPagesAsked = 0

    /** The lookup's clock, for an ask's start. */
    fun now(): Long = clock()

    /** Milliseconds since the lookup began. */
    fun elapsedMillis(): Long = (clock() - startedAt).coerceAtLeast(0)

    /** What is left of [budgetMillis] counted from the lookup's start (never below 0). */
    fun timeLeftMillis(budgetMillis: Long): Long = (budgetMillis - elapsedMillis()).coerceAtLeast(0)

    /** Requests main's adapters sent in this lookup. */
    val mainAsks: Int get() = synchronized(lock) { counts[Asker.MAIN.ordinal] }

    /** New requests Master sent in this lookup (route B). */
    val masterAsks: Int get() = synchronized(lock) { counts[Asker.MASTER.ordinal] }

    /** Whether any ask of this lookup (main's or Master's) got HTTP 429 or a rate limit. */
    val rateLimited: Boolean get() = synchronized(lock) { rateLimitedTotal > 0 }

    /** F2: how main's answer reached this lookup without main asking in it; null: not noted. */
    val reuse: Reuse? get() = synchronized(lock) { reused }

    /** F2: main's answer reached this lookup [how] (P17), without main asking in it. */
    fun noteReuse(how: Reuse) {
        synchronized(lock) { reused = how }
    }

    /** Answers kept so far (replayed ones included). */
    val keptCount: Int get() = synchronized(lock) { keptTotal }

    /** Requests answered from this record. */
    val replayedCount: Int get() = synchronized(lock) { replayedTotal }

    /** The listed asks, in order (at most [MAX_LISTED]). */
    fun asks(): List<Ask> = synchronized(lock) { listed.toList() }

    /** Whether [ask]'s endpoint (method, address and body, whatever the headers) was asked. */
    fun asked(ask: LookupAsk): Boolean = synchronized(lock) { ask.endpoint in endpoints }

    /** F5 (guardrail 7): whether a watch page was asked in this lookup, by main or by Master. */
    val watchPageAsked: Boolean get() = synchronized(lock) { watchPagesAsked > 0 }

    /** S0a: asks of an endpoint already asked in this lookup; 0 without labels. */
    val repeatedAsks: Int get() = synchronized(lock) { repeatedTotal }

    /** S0a: asks to a host after it stopped (a 429 or a bot check); 0 without labels. */
    val asksAfterStop: Int get() = synchronized(lock) { afterStopTotal }

    /** S0a: answers read as a bot check ([AnswerMark.BOT_CHECK]); 0 without labels. */
    val botChecks: Int get() = synchronized(lock) { botCheckTotal }

    /** S0a: the hosts that stopped (a 429 or a bot check), in order; empty without labels. */
    fun stopHosts(): List<String> = synchronized(lock) { stopAt.keys.toList() }

    /** 0.4: asks whose report key was asked before in this lookup (repeats); 0 without labels. */
    val repeatAsks: Int get() = synchronized(lock) { repeatTotal }

    /** 0.4: asks of a method and address asked before with another body or identity class; 0 without labels. */
    val variantAsks: Int get() = synchronized(lock) { variantTotal }

    /**
     * [ask]'s answer [result]; main's unauthenticated answer is kept for one replay, and (F5) main's
     * first cookie GET of the named watch page, keyed without the cookie.
     */
    fun record(ask: LookupAsk, result: ExtractorHttpResult, startedAtMillis: Long, asker: Asker = Asker.MAIN) {
        val page = asker == Asker.MAIN && isNamedPage(ask)
        val keep = asker == Asker.MAIN && (keepable(ask) || page)
        val key = if (page) ask.withoutCookie() else ask
        val copy = when (result) {
            is ExtractorHttpResult.Success -> result.copy(cookies = emptyList())
            is ExtractorHttpResult.Failure -> result
        }
        val chars = (copy as? ExtractorHttpResult.Success)?.body?.length?.toLong() ?: 0L
        val (status, failure) = when (result) {
            is ExtractorHttpResult.Success -> result.statusCode to null
            is ExtractorHttpResult.Failure -> result.statusCode to result.reason
        }
        val limited = limited(status, failure)
        val label = label(ask, result as? ExtractorHttpResult.Success)
        synchronized(lock) {
            val first = !page || !watchPageTaken
            if (page) watchPageTaken = true
            val stored = keep && first && keptEntries < maxKept && keptChars + chars <= maxKeptChars
            if (stored) {
                kept.getOrPut(key) { ArrayDeque() }.addLast(copy)
                keptEntries++
                keptChars += chars
                keptTotal++
            }
            add(ask, asker, answered = true, status, failure, limited, stored, startedAtMillis, label)
        }
    }

    /** [ask]'s file check answer [result]; never kept. */
    fun record(ask: LookupAsk, result: ExtractorProbeResult, startedAtMillis: Long, asker: Asker = Asker.MAIN) {
        val (status, failure) = when (result) {
            is ExtractorProbeResult.Answered -> result.statusCode to null
            is ExtractorProbeResult.Refused -> result.statusCode to result.reason
            ExtractorProbeResult.Unsupported -> null to null
        }
        val limited = limited(status, failure)
        val label = label(ask, null)
        synchronized(lock) { add(ask, asker, answered = true, status, failure, limited, false, startedAtMillis, label) }
    }

    /**
     * Route B (the player script): [result] of a request of this lookup that no ask lists — main's
     * player-script fetch, which the runner sends with its own client ([RouteBScriptClient]).
     * Nothing is listed, counted or kept; a 429 (or a rate limit) still makes [rateLimited] true, so
     * route B sends nothing new after it.
     */
    fun unlisted(result: ExtractorHttpResult) {
        if (result is ExtractorHttpResult.Failure && limited(result.statusCode, result.reason)) {
            synchronized(lock) { rateLimitedTotal++ }
        }
    }

    /** [unlisted] for a file check's answer. */
    fun unlisted(result: ExtractorProbeResult) {
        if (result is ExtractorProbeResult.Refused && limited(result.statusCode, result.reason)) {
            synchronized(lock) { rateLimitedTotal++ }
        }
    }

    /** [ask] went out but no answer came (cancelled): its endpoint still counts as asked. */
    fun unanswered(ask: LookupAsk, startedAtMillis: Long, asker: Asker = Asker.MAIN) {
        val label = label(ask, null)
        synchronized(lock) { add(ask, asker, answered = false, null, null, false, false, startedAtMillis, label) }
    }

    /**
     * Main's kept answer to a request identical to [ask] (method, address, body and headers in
     * order), once; null when none waits. A kept body over [maxBodyBytes] answers
     * `RESPONSE_TOO_LARGE`, as the client itself would.
     */
    fun replay(ask: LookupAsk, maxBodyBytes: Long): ExtractorHttpResult? {
        if (!keepable(ask)) return null
        val result = synchronized(lock) {
            val queue = kept[ask] ?: return null
            val first = queue.removeFirst()
            if (queue.isEmpty()) kept.remove(ask)
            keptEntries--
            keptChars -= (first as? ExtractorHttpResult.Success)?.body?.length ?: 0
            replayedTotal++
            first
        }
        if (result is ExtractorHttpResult.Success && result.body.toByteArray(Charsets.UTF_8).size > maxBodyBytes) {
            return ExtractorHttpResult.Failure(SiteExtractionFailure.RESPONSE_TOO_LARGE, result.statusCode)
        }
        return result
    }

    /** The lookup's end: every kept answer is dropped (the counts stay). */
    fun clear() {
        synchronized(lock) {
            kept.clear()
            keptEntries = 0
            keptChars = 0
        }
    }

    override fun toString(): String = synchronized(lock) {
        "LookupRecord(main=${counts[Asker.MAIN.ordinal]}, master=${counts[Asker.MASTER.ordinal]}, " +
            "kept=$keptTotal, replayed=$replayedTotal, waiting=$keptEntries, rateLimited=${rateLimitedTotal > 0})"
    }

    private fun keepable(ask: LookupAsk): Boolean =
        !ask.authorized && ask.method != LookupAsk.PROBE && LookupMemo.memoable(ask.url)

    /** F5: a GET of the named watch page (no body) that carries the tab's cookie and no other credential. */
    private fun isNamedPage(ask: LookupAsk): Boolean = watchPage != null && ask.method == LookupAsk.GET &&
        ask.url == watchPage && ask.body == null && ask.cookieOnly && LookupMemo.memoable(ask.url)

    /** An answer that is a 429 or a rate limit. */
    private fun limited(status: Int?, failure: SiteExtractionFailure?): Boolean =
        failure != null && (status == HTTP_TOO_MANY || failure == SiteExtractionFailure.RATE_LIMITED)

    /**
     * S0a: [ask]'s way and, for a 2xx [success], its reading; null without labels. Outside the
     * lock; a label that throws is none.
     */
    private fun label(ask: LookupAsk, success: ExtractorHttpResult.Success?): Label? {
        val labels = labels ?: return null
        val way = guarded { labels.way(ask) }
        val reading = success?.let { answer -> guarded { labels.read(ask, way, answer) } }
        return Label(way, reading, reportKey(ask), digest(ask.method, ask.url))
    }

    /** Under [lock]. */
    private fun add(
        ask: LookupAsk,
        asker: Asker,
        answered: Boolean,
        status: Int?,
        failure: SiteExtractionFailure?,
        limited: Boolean,
        stored: Boolean,
        startedAtMillis: Long,
        label: Label? = null,
    ) {
        // S0a: only a labelled record counts repeats and stops (before this ask is noted).
        var labelledAt: Long? = null
        var repeated = false
        var afterStop = false
        var repeat = false
        var variant = false
        if (label != null) {
            val at = clock()
            labelledAt = at
            repeated = ask.endpoint in endpoints
            // 0.4: the report's split (its own key; the endpoint rule above is untouched).
            repeat = !reportKeys.add(label.key)
            variant = !repeat && !targets.add(label.target)
            if (repeat) repeatTotal++
            if (variant) variantTotal++
            val host = hostOf(ask.url)
            afterStop = host != null && stopAt[host]?.let { startedAtMillis >= it } == true
            if (repeated) repeatedTotal++
            if (afterStop) afterStopTotal++
            val mark = label.reading?.mark
            if (mark == AnswerMark.BOT_CHECK) botCheckTotal++
            if (host != null && (limited || mark?.stops == true) && host !in stopAt) stopAt[host] = at
        }
        counts[asker.ordinal]++
        endpoints += ask.endpoint
        if (ask.watchPage) watchPagesAsked++
        if (limited) rateLimitedTotal++
        if (listed.size >= MAX_LISTED) return
        val now = labelledAt ?: clock()
        listed += Ask(
            asker = asker,
            method = ask.method,
            hash = ask.hash,
            authorized = ask.authorized,
            answered = answered,
            statusCode = status,
            failure = failure,
            rateLimited = limited,
            kept = stored,
            atMillis = (startedAtMillis - startedAt).coerceAtLeast(0),
            tookMillis = (now - startedAtMillis).coerceAtLeast(0),
            way = label?.way,
            mark = label?.reading?.mark,
            shape = label?.reading?.shape,
            repeated = repeated,
            afterStop = afterStop,
            repeat = repeat,
            variant = variant,
        )
    }

    companion object Key : CoroutineContext.Key<LookupRecord> {
        /** More than main's YouTube asks in one lookup (visionOS, page, page client, fallbacks). */
        const val MAX_KEPT = 16
        const val MAX_KEPT_CHARS = 8L * 1024 * 1024
        const val MAX_LISTED = 64
        private const val HTTP_TOO_MANY = 429
        private val VIDEO_ID = Regex("^[A-Za-z0-9_-]{11}$")

        private const val USER_AGENT = "user-agent"

        /**
         * 0.4: [ask]'s report key: its method, address and body and its identity class (whether it
         * carries the user's credentials, and the agent it names), hashed. A cookie's value is not
         * in it: a cookie the site renewed between two asks is still the same identity class.
         */
        private fun reportKey(ask: LookupAsk): String {
            val agent = ask.headers.lastOrNull { (name, _) -> name.trim().lowercase() == USER_AGENT }?.second?.trim()
            return digest(ask.method, ask.url, ask.body, ask.authorized.toString(), agent)
        }

        /** 0.4: SHA-256 over length-prefixed parts (a missing part differs from an empty one). */
        private fun digest(vararg parts: String?): String {
            val sha = MessageDigest.getInstance("SHA-256")
            for (part in parts) {
                if (part == null) {
                    sha.update(0.toByte())
                } else {
                    val bytes = part.toByteArray(Charsets.UTF_8)
                    sha.update(1.toByte())
                    sha.update(ByteBuffer.allocate(Int.SIZE_BYTES).putInt(bytes.size).array())
                    sha.update(bytes)
                }
            }
            return sha.digest().joinToString("") { "%02x".format(it) }
        }

        /** S0a: [url]'s host, lowercase without a trailing dot; null when it has none. */
        private fun hostOf(url: String): String? = runCatching { URI(url).host }.getOrNull()
            ?.lowercase()?.trimEnd('.')?.takeIf(String::isNotEmpty)

        /** S0a: [block]'s value, or null when it throws (a cancellation still ends the lookup). */
        private inline fun <T> guarded(block: () -> T): T? = try {
            block()
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: Exception) {
            null
        }
    }
}
