package com.alal.yft.extractor.sites.tiktok

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TikTokAgentsTest {
    @Test
    fun `the tab's own browser agent is used as it is`() = runTest {
        var asked = false
        val agents = TikTokAgents(webViewAgent = { asked = true; WEBVIEW_AGENT })

        assertEquals(TAB_AGENT, agents.phone(" $TAB_AGENT "))
        assertFalse("the WebView is only asked without a browser agent", asked)
    }

    @Test
    fun `the headless YFT agent and a missing agent are replaced by the WebView's own`() =
        runTest {
            val agents = TikTokAgents(webViewAgent = { WEBVIEW_AGENT })

            assertEquals(WEBVIEW_AGENT, agents.phone(HEADLESS_AGENT))
            assertEquals(WEBVIEW_AGENT, agents.phone(""))
            assertEquals(WEBVIEW_AGENT, agents.phone(null))
        }

    @Test
    fun `without a usable WebView agent the lookup keeps the agent it was given`() = runTest {
        assertEquals(HEADLESS_AGENT, TikTokAgents().phone(HEADLESS_AGENT))
        assertNull(TikTokAgents().phone(null))
        assertEquals(
            HEADLESS_AGENT,
            TikTokAgents(webViewAgent = { error("no WebView") }).phone(HEADLESS_AGENT),
        )
        assertEquals(
            HEADLESS_AGENT,
            TikTokAgents(webViewAgent = { "Mozilla/5.0 YFT/2.0" }).phone(HEADLESS_AGENT),
        )
    }

    @Test
    fun `the desktop agent is Windows Chrome with the phone's Chrome version and no YFT`() {
        val agents = TikTokAgents()

        val desktop = agents.desktop(WEBVIEW_AGENT)
        assertEquals(
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
                "(KHTML, like Gecko) Chrome/137.0.0.0 Safari/537.36",
            desktop,
        )
        assertFalse(desktop.contains("YFT"))
        assertFalse(desktop.contains("Android"))
        assertTrue(agents.desktop(HEADLESS_AGENT).contains("Chrome/131.0.0.0"))
        assertTrue(agents.desktop(null).contains("Chrome/131.0.0.0"))
    }

    private companion object {
        const val TAB_AGENT = "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 " +
            "(KHTML, like Gecko) Chrome/136.0.7103.60 Mobile Safari/537.36"
        const val WEBVIEW_AGENT = "Mozilla/5.0 (Linux; Android 15; wv) AppleWebKit/537.36 " +
            "(KHTML, like Gecko) Version/4.0 Chrome/137.0.7151.89 Mobile Safari/537.36"
        const val HEADLESS_AGENT = "Mozilla/5.0 (Linux; Android 15) YFT/1.0"
    }
}
