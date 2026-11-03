package com.alal.yft.detection.tiktok

/**
 * TikTok page reader: the owner's choices for TikTok's own pages.
 *
 * [hiddenPage] (`TT_HIDDEN_PAGE`): when reading the page fails, open the video's TikTok
 * page in a hidden WebView and take TikTok's own data from it. [homeCookies]
 * (`TT_HOME_COOKIES`): Home's TikTok lookups and the hidden page use the cookies YFT's own
 * browser has for `tiktok.com`; when off, Home stays cookie-free and the hidden page clears
 * the TikTok cookies it set when it finishes.
 */
data class TikTokPageSettings(
    val hiddenPage: Boolean = true,
    val homeCookies: Boolean = true,
) {
    companion object {
        /** The owner's choices: `TT_HIDDEN_PAGE=ON`, `TT_HOME_COOKIES=ON` (the defaults). */
        val OWNER: TikTokPageSettings = TikTokPageSettings()
    }
}