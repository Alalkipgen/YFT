package com.alal.yft.extractor.master.social

import com.alal.yft.extractor.master.modules.youtube.ladder.MediaStart
import com.alal.yft.extractor.master.modules.youtube.ladder.MediaStartReader
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.coroutineContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.CookieJar
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okio.BufferedSource

/**
 * Social plan S1a (6), S6: V's read of a row's file for [SocialRowCheck]: one GET with
 * `Range: bytes=0-(n - 1)` and `Accept-Encoding: identity`, HTTPS on one of the read's site's media
 * hosts only ([RowReadSite.address]: [Site.mediaHost]), the host the answer gave: **no redirect
 * followed** (a 3xx is no 2xx answer), no cookie and no credentials (dropped from the headers
 * given), at most `maxBytes` (≤ 64 KiB) read, a 10 s call timeout, no retry. Nothing is logged.
 *
 * Task 9: one reader for every site. It holds no site of its own: each read's site is the one
 * [SocialRowCheck] chose for the read's lookup (its [MainPages]' site), named by the read's
 * [RowReadSite]. A read without one, or of an address off that site's media hosts, sends nothing
 * (a failure with no status); [SocialRowCheck] refuses such an address before route B lists the ask.
 */
class OkHttpRowReader(client: OkHttpClient) : MediaStartReader {
    private val http = client.newBuilder()
        .cookieJar(CookieJar.NO_COOKIES)
        .followRedirects(false)
        .followSslRedirects(false)
        .retryOnConnectionFailure(false)
        .callTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .build()

    override suspend fun read(url: String, headers: Map<String, String>, maxBytes: Int): MediaStart {
        require(maxBytes in 1..MAX_BYTES)
        // Task 9: the read's own site (the lookup's, chosen by the check), never one of the reader's.
        val address = coroutineContext[RowReadSite]?.address(url) ?: return MediaStart.Failed(null)
        val builder = Request.Builder().url(address).get()
        headers.forEach { (name, value) ->
            // A header OkHttp cannot send (a bad name or value) is left out.
            if (name.trim().lowercase() !in DROPPED) runCatching { builder.header(name, value) }
        }
        builder.header("Range", "bytes=0-${maxBytes - 1}")
        builder.header("Accept-Encoding", "identity")
        return withContext(Dispatchers.IO) {
            try {
                http.newCall(builder.build()).execute().use { response ->
                    if (!response.isSuccessful) {
                        MediaStart.Failed(response.code)
                    } else {
                        MediaStart.Read(
                            statusCode = response.code,
                            contentType = response.header("Content-Type"),
                            contentRange = response.header("Content-Range"),
                            contentLength = response.header("Content-Length")?.toLongOrNull(),
                            bytes = response.body?.source()?.let { readAtMost(it, maxBytes) } ?: ByteArray(0),
                        )
                    }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: IOException) {
                MediaStart.Failed(null)
            }
        }
    }

    override fun toString(): String = "OkHttpRowReader"

    private fun readAtMost(source: BufferedSource, maxBytes: Int): ByteArray {
        source.request(maxBytes.toLong())
        return source.buffer.readByteArray(minOf(source.buffer.size, maxBytes.toLong()))
    }

    companion object {
        const val MAX_BYTES = 64 * 1024
        const val TIMEOUT_SECONDS = 10L
        private val DROPPED = setOf("cookie", "authorization", "proxy-authorization", "range", "accept-encoding")
    }
}

/**
 * Task 9: the site whose media hosts a row's read may ask, in the read's context: the one
 * [SocialRowCheck] chose for the read's lookup (its [MainPages]' site), so one reader serves every
 * site's lookups. [address] is the one rule both apply: [SocialRowCheck] before route B lists an
 * ask (another host is refused there, never sent), [OkHttpRowReader] before it sends.
 */
class RowReadSite(val site: Site) : AbstractCoroutineContextElement(Key) {
    /**
     * [url] as the reader sends it when it is HTTPS on one of [site]'s media hosts and paths
     * ([Site.mediaHost]), read as given and as sent; else null: refused, nothing is sent.
     */
    fun address(url: String): HttpUrl? =
        url.toHttpUrlOrNull()?.takeIf { it.isHttps && site.mediaHost(url) && site.mediaHost(it.toString()) }

    override fun toString(): String = "RowReadSite(${site.id})"

    companion object Key : CoroutineContext.Key<RowReadSite>
}
