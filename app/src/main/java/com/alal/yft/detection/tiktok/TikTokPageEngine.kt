package com.alal.yft.detection.tiktok

import android.content.Context
import com.alal.yft.detection.HiddenPageReader
import com.alal.yft.detection.HiddenPageResult
import com.alal.yft.extractor.api.SiteExtractionFailure
import com.alal.yft.extractor.api.SitePageData
import com.alal.yft.extractor.api.SitePageDataSource
import java.util.Locale
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/** P40: one hidden page; it loads a link, answers scripts, and is destroyed at the end. */
interface HiddenPageWindow {
    /** The agent the page is asked with. */
    val userAgent: String?

    /** The page's address now, after redirects; null before it started. */
    val currentUrl: String?

    /** Why the page's main frame stopped loading, when it did (no address in it). */
    val error: String?

    suspend fun load(url: String)

    /** The script's result as `evaluateJavascript` gives it; null when the page did not answer. */
    suspend fun evaluate(script: String): String?

    /** Destroys the page; a second call does nothing. */
    suspend fun destroy()
}

/** P40: makes hidden pages that run [storeScript] at document start; null without a WebView. */
fun interface HiddenPageWindows {
    suspend fun open(storeScript: String): HiddenPageWindow?
}

/** P40: TikTok's cookies in the browser's cookie store; values are never logged. */
interface TikTokCookies {
    /** The cookie header the browser sends to [url]; null when none. */
    fun header(url: String): String?

    /** Expires the TikTok cookies named [names]. */
    fun clear(names: Set<String>)
}

/**
 * P40 step 4 (G5, `TT_HIDDEN_PAGE=ON`): TikTok's own page for a link no tab shows, opened in a
 * hidden WebView the way `WebViewBotGuardEngine` runs YouTube's script (ADR-006).
 *
 * The page is asked with desktop Chrome, JavaScript on, images off and media answered empty,
 * and the browser's shared cookie store (G3, `TT_HOME_COOKIES=ON`), so a check cookie and the
 * media token stay for the next lookups. The page script's store runs at document start; the
 * item script is polled every [pollMillis] until the post's data appears or [timeoutMillis]
 * pass, then the page is destroyed. One page at a time: a second lookup waits. A check TikTok's
 * own script passes by itself passes here; one that stays (a puzzle needs a person) ends the
 * read as [SiteExtractionFailure.BOT_CHECK]; nothing here ever answers a check. With
 * `TT_HOME_COOKIES=OFF` the TikTok cookies the page set are cleared when it finishes.
 */
class TikTokPageEngine(
    private val settings: TikTokPageSettings,
    private val script: () -> String?,
    private val windows: HiddenPageWindows,
    private val cookies: TikTokCookies,
    private val clock: () -> Long = System::currentTimeMillis,
    private val timeoutMillis: Long = TIMEOUT_MILLIS,
    private val pollMillis: Long = POLL_MILLIS,
) : HiddenPageReader {
    constructor(
        context: Context,
        settings: TikTokPageSettings,
        isMedia: (String) -> Boolean,
    ) : this(
        settings = settings,
        script = TikTokPageScript.Source(context)::text,
        windows = WebViewHiddenPages(context, isMedia = isMedia),
        cookies = CookieManagerTikTokCookies,
    )

    private val oneAtATime = Mutex()

    override fun handles(siteId: String): Boolean = siteId == TikTokPageScript.SITE_ID

    override suspend fun read(link: String, postId: String?): HiddenPageResult {
        if (!settings.hiddenPage) return HiddenPageResult.Off
        val asked = clock()
        return oneAtATime.withLock {
            val details = mutableListOf<String>()
            val waited = clock() - asked
            if (waited >= WAIT_NOTE_MILLIS) {
                details += "hidden page: waited ${seconds(waited)} for the lookup before"
            }
            open(link, postId, details)
        }
    }

    private suspend fun open(
        link: String,
        postId: String?,
        details: MutableList<String>,
    ): HiddenPageResult {
        val source = script()
            ?: return notFound(details, "hidden page: page script missing from this build")
        val before = if (settings.homeCookies) null else cookieNames()
        val window = windows.open(TikTokPageScript.store(source))
            ?: return notFound(details, "hidden page: no WebView on this phone")
        val state = PollState()
        val poll = try {
            withTimeoutOrNull(timeoutMillis) { pollItem(window, link, postId, source, state) }
        } finally {
            withContext(NonCancellable) {
                window.destroy()
                before?.let { details += clearNewCookies(it) }
            }
        }
        return result(poll, state, window, link, details)
    }

    /** What the last poll saw, kept for a read that times out. */
    private class PollState {
        var last: TikTokPageScript.Answer? = null
    }

    private suspend fun pollItem(
        window: HiddenPageWindow,
        link: String,
        postId: String?,
        source: String,
        state: PollState,
    ): Poll {
        val started = clock()
        window.load(link)
        val itemScript = TikTokPageScript.item(source, postId)
        var checkSince: Long? = null
        var statusSince: Long? = null
        while (true) {
            delay(pollMillis)
            window.error?.let { return Poll.Error(it, clock() - started) }
            val answer = TikTokPageScript.parse(window.evaluate(itemScript))
            state.last = answer
            val now = clock()
            when (answer) {
                is TikTokPageScript.Answer.Item -> return Poll.Item(
                    answer = answer,
                    // Taken now: the rows carry the cookies the page used (never logged).
                    cookie = cookies.header(TikTokPageScript.COOKIE_PAGE),
                    url = window.currentUrl,
                    elapsed = now - started,
                )

                is TikTokPageScript.Answer.None -> {
                    checkSince = if (answer.check) checkSince ?: now else null
                    statusSince = if (answer.status != null) statusSince ?: now else null
                    if (checkSince != null && now - checkSince >= CHECK_MILLIS) {
                        return Poll.Check(now - started)
                    }
                    val status = answer.status
                    val since = statusSince
                    if (status != null && since != null && now - since >= STATUS_MILLIS) {
                        return Poll.Status(status, now - started)
                    }
                }

                TikTokPageScript.Answer.Unreadable -> Unit
            }
        }
    }

    private fun result(
        poll: Poll?,
        state: PollState,
        window: HiddenPageWindow,
        link: String,
        details: MutableList<String>,
    ): HiddenPageResult = when (poll) {
        is Poll.Item -> {
            val answer = poll.answer
            val data = SitePageData.of(answer.json, SitePageDataSource.HIDDEN_PAGE)
            val from = if (answer.fromApi) "API answer" else "page script"
            if (data == null) {
                notFound(details, "hidden page: the post's data is larger than 64 KB")
            } else {
                details.add(
                    0,
                    "hidden page: found in ${seconds(poll.elapsed)} · $from · " +
                        "API answers kept: ${answer.answers}",
                )
                HiddenPageResult.Found(
                    data = data,
                    finalUrl = poll.url ?: link,
                    userAgent = window.userAgent,
                    cookie = poll.cookie,
                    details = details.toList(),
                )
            }
        }

        is Poll.Check -> {
            details.add(0, "hidden page: a check stays on the page · ${seconds(poll.elapsed)}")
            HiddenPageResult.NotFound(
                SiteExtractionFailure.BOT_CHECK,
                CHECK_MESSAGE,
                details.toList(),
            )
        }

        is Poll.Status -> {
            details.add(0, "hidden page: TikTok status ${poll.status} · ${seconds(poll.elapsed)}")
            HiddenPageResult.NotFound(statusFailure(poll.status), null, details.toList())
        }

        is Poll.Error -> notFound(
            details,
            "hidden page: the page did not load (${poll.error}) after ${seconds(poll.elapsed)}",
        )

        null -> {
            val last = (state.last as? TikTokPageScript.Answer.None)
                ?.let { " · " + TikTokPageScript.noneNote(it) }
                .orEmpty()
            notFound(details, "hidden page: no data after ${seconds(timeoutMillis)}$last")
        }
    }

    private fun notFound(details: MutableList<String>, line: String): HiddenPageResult {
        details.add(0, line)
        return HiddenPageResult.NotFound(null, null, details.toList())
    }

    private fun cookieNames(): Set<String> =
        cookies.header(TikTokPageScript.COOKIE_PAGE).orEmpty()
            .split(';')
            .mapNotNull { pair -> pair.substringBefore('=').trim().takeIf(String::isNotEmpty) }
            .toSet()

    /** `TT_HOME_COOKIES=OFF`: the TikTok cookies this page set go when it finishes. */
    private fun clearNewCookies(before: Set<String>): String {
        val added = cookieNames() - before
        if (added.isNotEmpty()) runCatching { cookies.clear(added) }
        return "hidden page: TikTok cookies it set cleared: ${added.size}"
    }

    private fun statusFailure(status: Long): SiteExtractionFailure = when (status) {
        GEO_STATUS -> SiteExtractionFailure.GEO_RESTRICTED
        in LOGIN_STATUSES -> SiteExtractionFailure.LOGIN_REQUIRED
        else -> SiteExtractionFailure.PRIVATE_OR_UNAVAILABLE
    }

    private sealed interface Poll {
        class Item(
            val answer: TikTokPageScript.Answer.Item,
            val cookie: String?,
            val url: String?,
            val elapsed: Long,
        ) : Poll

        class Check(val elapsed: Long) : Poll

        class Status(val status: Long, val elapsed: Long) : Poll

        class Error(val error: String, val elapsed: Long) : Poll
    }

    companion object {
        const val TIMEOUT_MILLIS = 15_000L
        const val POLL_MILLIS = 300L

        /** A check still shown after this long needs a person. */
        const val CHECK_MILLIS = 3_000L

        /** TikTok's status for the post, unchanged this long, is its answer. */
        const val STATUS_MILLIS = 2_000L

        const val CHECK_MESSAGE =
            "TikTok wants a check. Open the video in YFT's browser and tap Show check."

        private const val WAIT_NOTE_MILLIS = 500L
        private const val GEO_STATUS = 10231L
        private val LOGIN_STATUSES = setOf(10222L, 10223L)

        private fun seconds(millis: Long): String =
            String.format(Locale.US, "%.1f s", millis / 1_000.0)
    }
}