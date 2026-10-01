package com.alal.yft.core.browser.policy

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BrowserAddressNormalizerTest {
    @Test
    fun addsHttpsToHostAndAcceptsHttpsAddress() {
        assertEquals(
            BrowserAddressResult.Valid("https://example.test/watch"),
            BrowserAddressNormalizer.normalize("example.test/watch"),
        )
        assertTrue(BrowserAddressNormalizer.isAllowedTopLevelUrl("https://example.test"))
        assertTrue(BrowserAddressNormalizer.isAllowedTopLevelUrl("about:blank"))
    }

    @Test
    fun rejectsCleartextScriptsCredentialsAndMalformedAddresses() {
        listOf(
            "http://example.test",
            "javascript:alert(1)",
            "https://user:pass@example.test",
            "not a host",
        ).forEach { input ->
            assertTrue(BrowserAddressNormalizer.normalize(input) is BrowserAddressResult.Invalid)
            assertFalse(BrowserAddressNormalizer.isAllowedTopLevelUrl(input))
        }
    }
}
