package com.alal.yft.extractor.sites.tiktok

import kotlin.coroutines.cancellation.CancellationException

/**
 * P39 (TT_AGENT=CHROME, R26): the user agents TikTok's pages are asked with.
 *
 * TikTok answers a client it does not recognise as a browser (the headless "YFT/" agent) with a
 * page that holds no post, so the phone page always uses the WebView's own agent: the tab's,
 * or [webViewAgent] for a lookup that starts outside a tab (Home). The desktop page uses desktop
 * Chrome with the same Chrome version, and never the "YFT" word.
 */
class TikTokAgents(
    private val webViewAgent: suspend () -> String? = { null },
) {
    /** The tab's own agent; else the WebView's; else whatever the lookup was given. */
    suspend fun phone(contextAgent: String?): String? {
        contextAgent?.trim()?.takeIf(::isBrowserAgent)?.let { return it }
        val own = try {
            webViewAgent()
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: Exception) {
            null
        }
        return own?.trim()?.takeIf(::isBrowserAgent) ?: contextAgent
    }

    /** Desktop Chrome on Windows with [phoneAgent]'s Chrome major version. */
    fun desktop(phoneAgent: String?): String {
        val major = phoneAgent?.let { CHROME_MAJOR.find(it)?.groupValues?.get(1) }
            ?: FALLBACK_CHROME_MAJOR
        return "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
            "(KHTML, like Gecko) Chrome/$major.0.0.0 Safari/537.36"
    }

    private fun isBrowserAgent(agent: String): Boolean =
        agent.isNotEmpty() && !agent.contains("YFT/")

    companion object {
        private val CHROME_MAJOR = Regex("Chrome/(\\d{2,4})\\.")
        private const val FALLBACK_CHROME_MAJOR = "131"
    }
}
