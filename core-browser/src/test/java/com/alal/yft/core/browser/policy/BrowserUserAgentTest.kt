package com.alal.yft.core.browser.policy

import org.junit.Assert.assertEquals
import org.junit.Test

class BrowserUserAgentTest {
    @Test
    fun theWebViewIdentityBecomesChromesOnTheSamePhone() {
        assertEquals(
            "Mozilla/5.0 (Linux; Android 14; Pixel 7 Build/UQ1A.240205.004) " +
                "AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.6613.146 " +
                "Mobile Safari/537.36",
            BrowserUserAgent.from(
                "Mozilla/5.0 (Linux; Android 14; Pixel 7 Build/UQ1A.240205.004; wv) " +
                    "AppleWebKit/537.36 (KHTML, like Gecko) Version/4.0 " +
                    "Chrome/128.0.6613.146 Mobile Safari/537.36",
            ),
        )
    }

    @Test
    fun anIdentityWithoutTheEmbeddedMarksStaysAsItIs() {
        val chrome = "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 " +
            "(KHTML, like Gecko) Chrome/128.0.0.0 Mobile Safari/537.36"

        assertEquals(chrome, BrowserUserAgent.from(chrome))
        assertEquals(chrome, BrowserUserAgent.from(BrowserUserAgent.from(chrome)))
        assertEquals("", BrowserUserAgent.from(""))
    }
}
