package com.alal.yft.detection.tiktok

/**
 * P40: the owner's answers for TikTok's own pages (FIX_ADD_PLAN §3).
 *
 * [hiddenPage] is G5 (`TT_HIDDEN_PAGE`): when reading the page fails, open the video's TikTok
 * page in a hidden WebView and take TikTok's own data from it. [homeCookies] is G3
 * (`TT_HOME_COOKIES`): Home's TikTok lookups and the hidden page use the cookies YFT's own
 * browser has for `tiktok.com`; when off, Home stays cookie-free and the hidden page clears
 * the TikTok cookies it set when it finishes.
 */
data class TikTokPageSettings(
    val hiddenPage: Boolean = true,
    val homeCookies: Boolean = true,
) {
    companion object {
        /** The owner's answers: `TT_HIDDEN_PAGE=ON`, `TT_HOME_COOKIES=ON` (the defaults). */
        val OWNER: TikTokPageSettings = TikTokPageSettings()
    }
}