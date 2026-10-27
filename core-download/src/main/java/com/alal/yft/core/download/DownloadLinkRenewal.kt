package com.alal.yft.core.download

import com.alal.yft.core.model.download.DownloadFailureReason
import com.alal.yft.core.model.download.DownloadFailureStage
import com.alal.yft.core.model.download.DownloadPlan
import com.alal.yft.core.model.download.RemoteFileMetadata
import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext

/**
 * Track E: a fresh link for a download whose file link died after it started (401, 403, 410,
 * an expired link; VK's 400 after the link gave bytes). The queue asks, in the task's own job
 * and outside its lock; the app looks the page up again and answers with the same file in the
 * same quality, or says why it cannot. The queue never sends a link: the app keeps the page.
 */
interface DownloadLinkRenewal {
    suspend fun renew(request: LinkRenewalRequest): LinkRenewal

    /**
     * Where a fresh link's run went on, for the Details' line: the first bytes it reported
     * ([firstBytes]) against the [bytesKept] when the queue asked. At or above them the download
     * resumed; below them it started again (the file changed). Called once per fresh link from
     * the engine's callback, so it must return at once and never throw.
     */
    fun wentOn(taskId: String, bytesKept: Long, firstBytes: Long) = Unit

    /** No renewal source: every failure goes on as it did before Track E. */
    object None : DownloadLinkRenewal {
        override suspend fun renew(request: LinkRenewalRequest): LinkRenewal = LinkRenewal.Declined

        override fun toString(): String = "DownloadLinkRenewal.None"
    }
}

/**
 * Why the queue asks for a fresh link of [taskId]. No address, cookie or header: only the
 * failure's facts and the bytes.
 */
data class LinkRenewalRequest(
    val taskId: String,
    val planType: DownloadPlanType,
    val reason: DownloadFailureReason,
    val httpStatusCode: Int?,
    val stage: DownloadFailureStage?,
    /** The bytes the download keeps (its checkpoint) when it asks. */
    val bytesKept: Long,
    /** The bytes the link that died gave before it died; 0 when it gave none. */
    val bytesFromThisLink: Long,
    /** A Retry tap (E-D6): one lookup, not counted in the automatic ones. */
    val byUser: Boolean,
    /** The fresh links this download already got without a tap (at most 3, E-D3). */
    val automaticRenewals: Int,
)

/** The app's answer to a [LinkRenewalRequest]. */
sealed interface LinkRenewal {
    /**
     * The same file again: a plan of the same task id and plan type with a fresh link, and a
     * direct file's [metadata] from its probe. The engine's own same-file check decides whether
     * the download resumes or starts again at byte 0.
     */
    data class Fresh(
        val plan: DownloadPlan,
        val metadata: RemoteFileMetadata? = null,
    ) : LinkRenewal

    /**
     * No fresh link for this file: [why] in the Details' words, without an address (for
     * example "this quality is no longer offered"). The download stops as "Link expired".
     */
    data class Unavailable(val why: String) : LinkRenewal

    /** Not renewed here (no source for this download, or a failure the app does not renew). */
    data object Declined : LinkRenewal
}

/**
 * Which download, and which of its links, an HTTP request belongs to: the queue runs each
 * transfer with it in the coroutine context and [SecureDownloadHttp] tags every request with it
 * (`Request.tag(DownloadRequestTag::class.java)`). [link] is 0 for the link the download started
 * with and goes up by one with each fresh link.
 */
data class DownloadRequestTag(
    val taskId: String,
    val link: Int,
) : AbstractCoroutineContextElement(DownloadRequestTag) {
    init {
        require(taskId.isNotBlank())
        require(link >= 0)
    }

    companion object Key : CoroutineContext.Key<DownloadRequestTag>
}
