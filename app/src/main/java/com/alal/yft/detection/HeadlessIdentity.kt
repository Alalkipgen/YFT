package com.alal.yft.detection

import com.alal.yft.BuildConfig
import com.alal.yft.core.model.media.PageNavigationHeaders

/** Home's session-free identity, not a Chrome/device impersonation or the WebView's identity. */
internal object HeadlessIdentity {
    val USER_AGENT: String =
        "Mozilla/5.0 (X11; Linux x86_64) YFT/${BuildConfig.VERSION_NAME}"
    val NAVIGATION_HEADERS: Map<String, String> = PageNavigationHeaders.DEFAULTS
}
