package com.alal.yft.core.download

import java.util.Collections
import java.util.concurrent.TimeUnit
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.RecordedRequest
import okhttp3.mockwebserver.SocketPolicy
import okio.Buffer

/**
 * Serves one file in byte ranges for the start tests (P41), like YouTube's media servers: a
 * 206 with Content-Range for each range, optionally slowed down per range ([throttle], bytes
 * per period) and with an answer cut off once per range ([cutOnce]).
 */
internal class RangeFileDispatcher(
    private val bytes: ByteArray,
    private val path: String = "/video.mp4",
    private val cutOnce: (start: Long) -> Boolean = { false },
    private val throttle: (start: Long) -> Throttle? = { null },
) : Dispatcher() {
    data class Throttle(val bytesPerPeriod: Long, val periodMillis: Long)

    private val startedAt = System.nanoTime()
    private val received = Collections.synchronizedList(mutableListOf<Pair<Long, String>>())
    private val cut = Collections.synchronizedSet(mutableSetOf<Long>())

    /** The Range header of every request, in arrival order ("none" without one). */
    fun ranges(): List<String> = synchronized(received) { received.map { it.second } }

    /** Milliseconds after this dispatcher was made when each request arrived. */
    fun arrivals(): List<Long> = synchronized(received) { received.map { it.first } }

    override fun dispatch(request: RecordedRequest): MockResponse {
        val elapsed = (System.nanoTime() - startedAt) / 1_000_000
        val header = request.getHeader("Range")
        received += elapsed to (header ?: "none")
        if (request.requestUrl?.encodedPath != path) return MockResponse().setResponseCode(404)
        val match = header?.let(RANGE::matchEntire)
            ?: return MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "video/mp4")
                .setBody(Buffer().write(bytes))
        val start = match.groupValues[1].toLong()
        val end = minOf(match.groupValues[2].toLong(), bytes.size - 1L)
        val response = MockResponse()
            .setResponseCode(206)
            .setHeader("Content-Type", "video/mp4")
            .setHeader("Content-Range", "bytes $start-$end/${bytes.size}")
            .setBody(Buffer().write(bytes, start.toInt(), (end - start + 1).toInt()))
        throttle(start)?.let { slow ->
            response.throttleBody(slow.bytesPerPeriod, slow.periodMillis, TimeUnit.MILLISECONDS)
        }
        if (cutOnce(start) && cut.add(start)) {
            response.socketPolicy = SocketPolicy.DISCONNECT_DURING_RESPONSE_BODY
        }
        return response
    }

    private companion object {
        val RANGE = Regex("""bytes=(\d+)-(\d+)""")
    }
}

internal const val MIB = 1_024 * 1_024

/** Bytes that differ from one position to the next, so a misplaced range shows. */
internal fun fileBytes(size: Int): ByteArray = ByteArray(size) { index -> (index % 251).toByte() }
