package com.alal.yft.extractor.master.android

import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class CaptureFocusGuardTest {
    private val page = "https://www.tiktok.com/foryou"
    private val expected = "1111111111111111111"
    private fun result(id: String) =
        """{"url":"https://www.tiktok.com/@fixture/video/$id","source":"playing"}"""

    @Test
    fun existingFocusParserAcceptsOnlyTheRequestedObservedContentId() {
        assertTrue(CaptureFocusGuard.matches(result(expected), page, expected, ::idOf))
        assertFalse(
            CaptureFocusGuard.matches(result("2222222222222222222"), page, expected, ::idOf),
        )
    }

    @Test
    fun unknownFocusAndAnotherOriginNeverBorrowTheRequestedId() {
        assertFalse(CaptureFocusGuard.matches(null, page, expected, ::idOf))
        val foreign = """{"url":"https://other.test/video/$expected","source":"playing"}"""
        assertFalse(CaptureFocusGuard.matches(foreign, page, expected, ::idOf))
    }

    @Test
    fun malformedDeepAndOversizedAnswersAreRejectedBeforeTheProductionParser() {
        for (text in listOf("{", "[".repeat(30) + "]".repeat(30), " ".repeat(65537))) {
            assertFalse(CaptureFocusGuard.matches(text, page, expected, ::idOf))
        }
    }

    private fun idOf(url: String): String = url.substringAfterLast('/')
}