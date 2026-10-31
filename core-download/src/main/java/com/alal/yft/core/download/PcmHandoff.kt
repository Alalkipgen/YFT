package com.alal.yft.core.download

import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.TimeUnit

/** R5 (v1.0.2): what the MP3 decoder's thread hands to LAME's, in order. */
internal sealed interface PcmItem {
    /** [count] shorts of interleaved 16-bit PCM at the start of [pcm]. */
    class Sound(
        val pcm: ShortArray,
        val count: Int,
        val sampleRate: Int,
        val channels: Int,
    ) : PcmItem

    /** The decoder's end of stream: every sound came before it. */
    data object End : PcmItem

    /** The decoder failed: [error] is thrown on LAME's thread; none is an incompatible track. */
    class Failed(val error: Throwable?) : PcmItem
}

/**
 * R5 (v1.0.2): decoded sound between the MP3 decoder's thread and LAME's. At most [capacity]
 * items wait, so the decoder runs at most that far ahead of LAME and waits for room ([put]);
 * the arrays LAME is done with come back through [recycle]. [close] ends the hand-off: a
 * waiting [put] returns false, and every later one at once.
 */
internal class PcmHandoff(capacity: Int) {
    private val items = ArrayBlockingQueue<PcmItem>(capacity)
    private val spares = ArrayBlockingQueue<ShortArray>(capacity + SPARE_SLACK)

    @Volatile
    private var closed = false

    init {
        require(capacity > 0)
    }

    /** A spare array of at least [size] shorts, or a new one. */
    fun array(size: Int): ShortArray {
        val spare = spares.poll()
        return if (spare != null && spare.size >= size) spare else ShortArray(size)
    }

    /** Hands [item] over, waiting while the hand-off is full; false once closed. */
    fun put(item: PcmItem): Boolean {
        while (!closed) {
            if (!items.offer(item, PUT_WAIT_MS, TimeUnit.MILLISECONDS)) continue
            if (!closed) return true
            // The room came from close() emptying the hand-off: nobody takes this item.
            items.clear()
            return false
        }
        return false
    }

    /** The next item, or null when none comes within [timeoutMs]. */
    fun take(timeoutMs: Long): PcmItem? = items.poll(timeoutMs, TimeUnit.MILLISECONDS)

    /** Gives back a [PcmItem.Sound]'s array once it is encoded. */
    fun recycle(pcm: ShortArray) {
        if (!closed) spares.offer(pcm)
    }

    fun close() {
        closed = true
        items.clear()
        spares.clear()
    }

    private companion object {
        /** A [put] looks at [close] this often while it waits for room. */
        const val PUT_WAIT_MS = 20L

        /** The arrays in LAME's hands and the decoder's, beyond the waiting ones. */
        const val SPARE_SLACK = 2
    }
}
