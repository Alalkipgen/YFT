package com.alal.yft.detection

import android.webkit.CookieManager
import com.alal.yft.core.browser.detection.HeadlessPageFetcher
import com.alal.yft.core.browser.detection.HtmlMediaScanner
import com.alal.yft.core.model.media.BrowserRequestContext
import com.alal.yft.core.model.media.CandidateSource
import com.alal.yft.core.model.media.MediaCandidate
import com.alal.yft.core.model.media.PageVideoFacts
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okhttp3.OkHttpClient

/** P37: what a quiet second read of the browser tab's page gave. */
sealed interface PageReread {
    /** The page's player data: its files, marked [CandidateSource.PAGE_REREAD]. */
    data class Found(
        val candidates: List<MediaCandidate>,
        val facts: PageVideoFacts?,
    ) : PageReread {
        override fun toString(): String = "Found(candidates=${candidates.size})"
    }

    /** The page answered without player data (a notice, a check, a changed page): not used. */
    data object NoPlayer : PageReread

    /** The page could not be read: the network, an HTTP status, or no page at all. */
    data object Failed : PageReread
}

/**
 * P37 (FIX_ADD_PLAN step 3, G7): reads the browser tab's page again in the background when its
 * links are gone, for the fresh ones the site writes into a new copy of the page.
 */
interface BrowserPageReader {
    /** How many quiet re-reads one failed video may use (`REREAD`; 0 = none). */
    val rereads: Int get() = DEFAULT_REREADS

    suspend fun read(pageUrl: String, userAgent: String?): PageReread

    companion object {
        /** G7's default, `REREAD=2`. */
        const val DEFAULT_REREADS = 2

        /** No re-read (`REREAD=0`): the sheet offers "Reload page and try again" at once. */
        val None: BrowserPageReader = object : BrowserPageReader {
            override val rereads: Int = 0

            override suspend fun read(pageUrl: String, userAgent: String?): PageReread =
                PageReread.Failed
        }
    }
}

/**
 * Reads the page the way the browser tab would ask for it again: the same address, no cached
 * answer, the WebView's user agent and the tab's cookies for that site from the browser's
 * cookie store ([HeadlessPageFetcher.TabSession]; never logged). The cookies carry only what
 * the user did on the site himself; nothing here taps, skips or fakes a notice, and a page
 * without player data is not used. One read at a time. Home's cookie-free lookup is separate.
 */
@Singleton
class TabPageReader internal constructor(
    private val fetch: suspend (
        url: String,
        session: HeadlessPageFetcher.TabSession,
    ) -> HeadlessPageFetcher.Result,
    private val cookies: (url: String) -> String?,
    private val clock: () -> Long = System::currentTimeMillis,
) : BrowserPageReader {
    @Inject
    constructor(client: OkHttpClient) : this(
        fetch = HeadlessPageFetcher(
            client,
            userAgent = HeadlessIdentity.USER_AGENT,
            navigationHeaders = HeadlessIdentity.NAVIGATION_HEADERS,
        )::fetchForTab,
        cookies = ::browserCookie,
    )

    private val scanner = HtmlMediaScanner(maxCandidates = MAX_CANDIDATES)
    private val oneAtATime = Mutex()

    override suspend fun read(pageUrl: String, userAgent: String?): PageReread =
        oneAtATime.withLock {
            val session = HeadlessPageFetcher.TabSession(userAgent, cookies)
            when (val page = fetch(pageUrl, session)) {
                is HeadlessPageFetcher.Result.Page -> found(page.html, pageUrl, userAgent)
                is HeadlessPageFetcher.Result.Media,
                is HeadlessPageFetcher.Result.Failed,
                -> PageReread.Failed
            }
        }

    private fun found(html: String, pageUrl: String, userAgent: String?): PageReread {
        val scan = scanner.scan(html, pageUrl, clock())
        if (scan.candidates.isEmpty()) return PageReread.NoPlayer
        return PageReread.Found(
            candidates = scan.candidates.map { candidate ->
                candidate.copy(
                    sources = candidate.sources + CandidateSource.PAGE_REREAD,
                    title = candidate.title ?: scan.title,
                    requestContext = BrowserRequestContext(pageUrl, userAgent, cookie = null),
                )
            },
            facts = scan.facts.takeUnless { it.isEmpty },
        )
    }

    private companion object {
        const val MAX_CANDIDATES = 50

        /** The tab's cookies for [url] from the browser's cookie store; never logged. */
        fun browserCookie(url: String): String? =
            kotlin.runCatching { CookieManager.getInstance().getCookie(url) }.getOrNull()
    }
}

@Module
@InstallIn(SingletonComponent::class)
abstract class BrowserPageReaderModule {
    @Binds
    abstract fun bindBrowserPageReader(reader: TabPageReader): BrowserPageReader
}
