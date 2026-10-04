package com.alal.yft.feature.home

import java.lang.reflect.Modifier
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CopiedLinkWatcherTest {
    private val clipboard = FakeClipboard()
    private val watcher = CopiedLinkWatcher(clipboard)

    @Test
    fun theSettingOffOrNoWindowFocusReadsNothing() {
        clipboard.copy("Watch https://m.youtube.com/watch?v=abc", timestamp = 1)

        assertNull(watcher.poll(enabled = false, windowFocused = true))
        assertNull(watcher.poll(enabled = true, windowFocused = false))
        assertEquals(0, clipboard.peeks)
        assertEquals(0, clipboard.reads)
    }

    @Test
    fun nonTextAndClipsClassifiedAsNotALinkAreNotRead() {
        clipboard.copy("photo", timestamp = 1, isText = false)
        assertNull(watcher.poll(enabled = true, windowFocused = true))

        clipboard.copy("call me", timestamp = 2, urlConfidence = 0.1f)
        assertNull(watcher.poll(enabled = true, windowFocused = true))

        assertEquals(0, clipboard.reads)
    }

    @Test
    fun theFirstWebLinkIsEmittedOncePerClip() {
        clipboard.copy("Watch this: https://m.youtube.com/watch?v=abc.", timestamp = 1)

        assertEquals(
            "https://m.youtube.com/watch?v=abc",
            watcher.poll(enabled = true, windowFocused = true),
        )
        assertNull(watcher.poll(enabled = true, windowFocused = true))
        assertEquals(1, clipboard.reads)

        // Copying the same text again is the same clip for YFT: it is read, not looked up.
        clipboard.copy("Watch this: https://m.youtube.com/watch?v=abc.", timestamp = 2)
        assertNull(watcher.poll(enabled = true, windowFocused = true))

        clipboard.copy("https://www.tiktok.com/@a/video/1", timestamp = 3, urlConfidence = 0.9f)
        assertEquals(
            "https://www.tiktok.com/@a/video/1",
            watcher.poll(enabled = true, windowFocused = true),
        )
    }

    @Test
    fun textWithoutALinkIsReadOnceAndNeverLookedUp() {
        clipboard.copy("shopping list: milk", timestamp = 1)

        assertNull(watcher.poll(enabled = true, windowFocused = true))
        assertNull(watcher.poll(enabled = true, windowFocused = true))
        assertEquals(1, clipboard.reads)
    }

    @Test
    fun noCopiedTextIsKept() {
        clipboard.copy("https://m.facebook.com/reel/1", timestamp = 1)
        watcher.poll(enabled = true, windowFocused = true)

        val fields = CopiedLinkWatcher::class.java.declaredFields
            .filterNot { it.name == "clipboard" || Modifier.isStatic(it.modifiers) }
        assertTrue(fields.isNotEmpty())
        fields.forEach { field ->
            field.isAccessible = true
            val value = field.get(watcher)
            assertTrue(field.name, value == null || value is Int || value is Long)
        }
    }

    private class FakeClipboard : ClipboardAccess {
        private var peek: ClipPeek? = null
        private var text: String? = null
        var peeks = 0
        var reads = 0

        fun copy(
            text: String,
            timestamp: Long,
            isText: Boolean = true,
            urlConfidence: Float? = null,
        ) {
            this.text = text
            peek = ClipPeek(isText = isText, urlConfidence = urlConfidence, timestamp = timestamp)
        }

        override fun peek(): ClipPeek? {
            peeks += 1
            return peek
        }

        override fun readText(): CharSequence? {
            reads += 1
            return text
        }
    }
}
