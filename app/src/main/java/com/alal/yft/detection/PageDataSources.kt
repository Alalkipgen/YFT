package com.alal.yft.detection

import com.alal.yft.extractor.api.SiteExtractionFailure
import com.alal.yft.extractor.api.SitePageData

/**
 * P40: what the user's browser tab holds for one post of a site, read when a lookup starts:
 * [pageData] when the tab has the post's data, [cookie] the site's cookies from the browser's
 * cookie store taken with it (never logged), [details] the step's Details lines.
 */
class TabData(
    val pageData: SitePageData?,
    val details: List<String>,
    val cookie: String? = null,
) {
    override fun toString(): String {
        val session = if (cookie == null) "none" else "set"
        return "TabData(pageData=$pageData, details=$details, cookie=$session)"
    }
}

/** P40: reads the browser tab's own data for a post; the browser's lookups pass one. */
fun interface TabDataSource {
    /** The tab's data for [postId] of [siteId] (at most about 1 s); null when it has no reader. */
    suspend fun read(siteId: String, postId: String): TabData?
}

/**
 * P40 (G5, `TT_HIDDEN_PAGE`): opens a site's page in a hidden WebView for a link that is not
 * open in a tab and takes the post's data from it, the way the site's own player gets it.
 */
interface HiddenPageReader {
    /** Whether this reader opens pages of [siteId]. */
    fun handles(siteId: String): Boolean

    /** The post's data from [link]'s page; [postId] when the link names one. */
    suspend fun read(link: String, postId: String?): HiddenPageResult

    companion object {
        /** No hidden page for any site. */
        val None: HiddenPageReader = object : HiddenPageReader {
            override fun handles(siteId: String): Boolean = false

            override suspend fun read(link: String, postId: String?): HiddenPageResult =
                HiddenPageResult.Off
        }
    }
}

/** What the hidden page gave; [details] never hold cookies, tokens or signed addresses. */
sealed interface HiddenPageResult {
    val details: List<String>

    /** The post's data, from the page at [finalUrl] asked with [userAgent] and [cookie]. */
    class Found(
        val data: SitePageData,
        val finalUrl: String,
        val userAgent: String?,
        val cookie: String?,
        override val details: List<String>,
    ) : HiddenPageResult {
        override fun toString(): String = "Found(data=$data, details=$details)"
    }

    /** No data for the post; [reason] and [message] when the page said why (a check, a status). */
    data class NotFound(
        val reason: SiteExtractionFailure?,
        val message: String?,
        override val details: List<String>,
    ) : HiddenPageResult

    /** The hidden page is turned off. */
    data object Off : HiddenPageResult {
        override val details: List<String> = listOf("hidden page: turned off")
    }
}
