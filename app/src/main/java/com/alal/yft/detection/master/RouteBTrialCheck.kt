package com.alal.yft.detection.master

import com.alal.yft.core.model.media.BrowserRequestContext
import com.alal.yft.core.model.media.MediaCandidate
import java.io.IOException
import java.net.URI
import java.net.URLDecoder
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response

/**
 * Phase 1.4 item 7 (YouTube routing B) F7, the route-B trial APK only (D7 `routeb`): Master's rows
 * checked against main's rows the trial set aside in the same lookup, as one segment of route B's
 * line (the browser's notice), for example
 * `; check: 403×2; same as main's: 3 of 3 rows, headers same`.
 *
 * D7 run 10's V3 download failed at once (403 on a fresh VISIONOS link, which main's links never
 * get a proof-of-origin token for either). So the segment says whether YouTube takes the rows'
 * files now ([FileCheck]: a one-byte ranged GET like the download's first, with the row's own
 * headers), and whether the rows are main's own — the same links (file and audio) and the same
 * download headers. When a link is not main's, it names the query names whose values differ or
 * that only one side has (against main's link of the same stream, `itag`) and checks main's link
 * too. Names, statuses and counts only: never a link, a host, a query value or a header value.
 */
object RouteBTrialCheck {
    fun interface FileCheck {
        /** The HTTP status of a one-byte ranged GET of [url] with [context]'s headers; null: none. */
        suspend fun status(url: String, context: BrowserRequestContext): Int?
    }

    /** The segment for Master's [rows] against main's set-aside [mains]; [check] null: no check. */
    suspend fun segment(
        rows: List<MediaCandidate>,
        mains: List<MediaCandidate>,
        check: FileCheck?,
    ): String {
        val mainLinks = mains.flatMap(::links).distinctBy(Link::url)
        val mainByUrl = mainLinks.associateBy(Link::url)
        val mainByItag = mainLinks.groupBy { itagOf(it.url) }
        val masterLinks = rows.flatMap(::links).distinctBy(Link::url)

        fun counterpart(link: Link): Link? = mainByUrl[link.url]
            ?: itagOf(link.url)?.let { itag -> mainByItag[itag]?.firstOrNull() }

        val sameRows = rows.count { row -> links(row).all { it.url in mainByUrl } }
        val headers = linkedSetOf<String>()
        val differ = linkedSetOf<String>()
        val masterOnly = linkedSetOf<String>()
        val mainOnly = linkedSetOf<String>()
        var matched = 0
        var unmatched = 0
        for (link in masterLinks) {
            val main = counterpart(link)
            if (main == null) {
                unmatched += 1
                continue
            }
            matched += 1
            headers += headerDifference(link.context, main.context)
            if (main.url != link.url) queryDifference(link.url, main.url, differ, masterOnly, mainOnly)
        }

        val checked = masterLinks.take(MAX_CHECKED)
        val mainChecked = checked.filter { it.url !in mainByUrl }
            .mapNotNull(::counterpart)
            .distinctBy(Link::url)
            .take(MAX_CHECKED)
        val statuses = check?.let { statuses(checked, it) }
        val mainStatuses = check?.takeIf { mainChecked.isNotEmpty() }?.let { statuses(mainChecked, it) }

        return buildString {
            statuses?.let { append("; check: ").append(it) }
            append("; same as main's: ").append(sameRows).append(" of ").append(rows.size).append(" rows")
            when {
                matched == 0 -> append(", no main's stream to compare")
                headers.isEmpty() -> append(", headers same")
                else -> append(", headers differ (").append(names(headers)).append(')')
            }
            if (differ.isNotEmpty()) append("; differ: ").append(names(differ))
            if (masterOnly.isNotEmpty()) append("; Master's only: ").append(names(masterOnly))
            if (mainOnly.isNotEmpty()) append("; main's only: ").append(names(mainOnly))
            if (unmatched > 0) append("; streams not in main's: ").append(unmatched)
            mainStatuses?.let { append("; main's check: ").append(it) }
        }.take(MAX_SEGMENT_CHARS)
    }

    /**
     * F7: the trial's file check over [client] — the download's own first request, one byte. The
     * whole call runs off the caller's thread, and the answer is closed on OkHttp's thread as soon
     * as its status is known: route B's lookup runs on the main thread, where closing an answer
     * whose body has not all arrived reads the network, which Android stops with a crash (D7 run
     * 11's first trial APK closed its answers there). Any failure is "no answer", never an error.
     */
    class OkHttpFileCheck(client: OkHttpClient) : FileCheck {
        private val client = client.newBuilder()
            .callTimeout(CHECK_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .build()

        override suspend fun status(url: String, context: BrowserRequestContext): Int? =
            withContext(Dispatchers.IO) {
                val request = try {
                    Request.Builder()
                        .url(url)
                        .apply {
                            context.replayHeaders().forEach { (name, value) -> runCatching { header(name, value) } }
                            header("Accept-Encoding", "identity")
                            header("Range", "bytes=0-0")
                        }
                        .build()
                } catch (_: IllegalArgumentException) {
                    return@withContext null
                }
                try {
                    client.newCall(request).status()
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    null
                }
            }

        /** The answer's status; the answer itself is closed on OkHttp's thread, unread. */
        private suspend fun Call.status(): Int = suspendCancellableCoroutine { continuation ->
            continuation.invokeOnCancellation { cancel() }
            enqueue(
                object : Callback {
                    override fun onFailure(call: Call, e: IOException) {
                        if (continuation.isActive) continuation.resumeWithException(e)
                    }

                    override fun onResponse(call: Call, response: Response) {
                        val code = response.code
                        runCatching { response.close() }
                        if (continuation.isActive) continuation.resume(code)
                    }
                },
            )
        }
    }

    private data class Link(val url: String, val context: BrowserRequestContext)

    private fun links(row: MediaCandidate): List<Link> = listOfNotNull(
        Link(row.mediaUrl, row.requestContext),
        row.audioCompanion?.let { Link(it.mediaUrl, it.requestContext) },
    )

    private suspend fun statuses(links: List<Link>, check: FileCheck): String = coroutineScope {
        val answers = links.map { link -> async { check.status(link.url, link.context) } }.awaitAll()
        if (answers.isEmpty()) return@coroutineScope "none"
        answers.groupingBy { it?.toString() ?: "no answer" }
            .eachCount()
            .entries
            .joinToString(", ") { (status, count) -> "$status×$count" }
    }

    /** Header names whose values differ or that one side lacks (case aside); never a value. */
    private fun headerDifference(master: BrowserRequestContext, main: BrowserRequestContext): Set<String> {
        val ours = master.replayHeaders().mapKeys { it.key.lowercase(Locale.US) }
        val theirs = main.replayHeaders().mapKeys { it.key.lowercase(Locale.US) }
        val names = master.replayHeaders().keys + main.replayHeaders().keys
        return names.distinctBy { it.lowercase(Locale.US) }
            .filter { ours[it.lowercase(Locale.US)] != theirs[it.lowercase(Locale.US)] }
            .toSet()
    }

    private fun queryDifference(
        master: String,
        main: String,
        differ: MutableSet<String>,
        masterOnly: MutableSet<String>,
        mainOnly: MutableSet<String>,
    ) {
        val ours = query(master)
        val theirs = query(main)
        if (host(master) != host(main)) differ += HOST
        if (path(master) != path(main)) differ += PATH
        ours.keys.filter { it in theirs && ours[it] != theirs[it] }.forEach { differ += it }
        ours.keys.filter { it !in theirs }.forEach { masterOnly += it }
        theirs.keys.filter { it !in ours }.forEach { mainOnly += it }
    }

    private fun itagOf(url: String): String? = query(url)["itag"]?.takeIf { it.isNotEmpty() }

    private fun query(url: String): Map<String, String> {
        val raw = runCatching { URI(url).rawQuery }.getOrNull() ?: return emptyMap()
        return raw.split('&')
            .filter(String::isNotEmpty)
            .associate { pair ->
                decode(pair.substringBefore('=')) to decode(pair.substringAfter('=', missingDelimiterValue = ""))
            }
    }

    private fun host(url: String): String? = runCatching { URI(url).host?.lowercase(Locale.US) }.getOrNull()

    private fun path(url: String): String? = runCatching { URI(url).rawPath }.getOrNull()

    private fun decode(text: String): String =
        runCatching { URLDecoder.decode(text, "UTF-8") }.getOrDefault(text)

    /** At most [MAX_NAMES] plain names; another name (it could carry anything) is a `?`. */
    private fun names(found: Set<String>): String {
        val shown = found.map { name -> if (SAFE_NAME.matches(name)) name else "?" }.distinct()
        val listed = shown.take(MAX_NAMES).joinToString(", ")
        return if (shown.size > MAX_NAMES) "$listed, …" else listed
    }

    private const val HOST = "host"
    private const val PATH = "path"
    private const val MAX_CHECKED = 8
    private const val MAX_NAMES = 6
    private const val MAX_SEGMENT_CHARS = 220
    private const val CHECK_TIMEOUT_SECONDS = 8L
    private val SAFE_NAME = Regex("[A-Za-z][A-Za-z0-9_-]{0,23}")
}
