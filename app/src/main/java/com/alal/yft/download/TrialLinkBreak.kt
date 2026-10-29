package com.alal.yft.download

import com.alal.yft.core.download.DownloadRequestTag
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import okhttp3.Interceptor
import okhttp3.MediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import okio.BufferedSource
import okio.ForwardingSource
import okio.buffer

/**
 * Track E (E-D9): the trial's tool for the phone test. Downloads ⋮ breaks a download's current
 * link: every request of that link (the queue's [DownloadRequestTag]) gets this tool's own 403,
 * without the network, and a body still being read stops, so the download's next request meets
 * the 403 as a dead link's would. The download's next link, a fresh one, passes. Off in every
 * build but the trial ([available]); a request without a download's tag (a lookup, a probe, a
 * picture) is never touched.
 */
class TrialLinkBreak(val available: Boolean) : Interceptor {
    /** Each download's newest link its requests carried. */
    private val links = ConcurrentHashMap<String, Int>()

    /** Each download's broken link. */
    private val broken = ConcurrentHashMap<String, Int>()

    /** The downloads' [client] with this tool in the trial; [client] itself elsewhere. */
    fun around(client: OkHttpClient): OkHttpClient =
        if (available) client.newBuilder().addInterceptor(this).build() else client

    /** Breaks [taskId]'s current link: the newest its requests carried (the first before any). */
    fun breakLink(taskId: String) {
        if (!available) return
        broken[taskId] = links[taskId] ?: 0
    }

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val tag = request.tag(DownloadRequestTag::class.java)
        if (!available || tag == null) return chain.proceed(request)
        links.merge(tag.taskId, tag.link) { seen, now -> maxOf(seen, now) }
        if (isBroken(tag)) return refused(request)
        val response = chain.proceed(request)
        val body = response.body ?: return response
        return response.newBuilder().body(BreakableBody(body, tag)).build()
    }

    private fun isBroken(tag: DownloadRequestTag): Boolean = broken[tag.taskId] == tag.link

    private fun refused(request: Request): Response = Response.Builder()
        .request(request)
        .protocol(Protocol.HTTP_1_1)
        .code(FORBIDDEN)
        .message("Forbidden")
        .body(ByteArray(0).toResponseBody())
        .build()

    /** A body that stops once its link is broken, as a dropped connection would. */
    private inner class BreakableBody(
        private val body: ResponseBody,
        private val tag: DownloadRequestTag,
    ) : ResponseBody() {
        private val source: BufferedSource by lazy {
            object : ForwardingSource(body.source()) {
                override fun read(sink: Buffer, byteCount: Long): Long {
                    if (isBroken(tag)) throw IOException(BROKEN)
                    return super.read(sink, byteCount)
                }
            }.buffer()
        }

        override fun contentType(): MediaType? = body.contentType()

        override fun contentLength(): Long = body.contentLength()

        override fun source(): BufferedSource = source

        override fun close() = body.close()
    }

    private companion object {
        const val FORBIDDEN = 403
        const val BROKEN = "the trial broke this link"
    }
}
