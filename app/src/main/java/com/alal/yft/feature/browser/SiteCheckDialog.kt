package com.alal.yft.feature.browser

import android.annotation.SuppressLint
import android.content.Context
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.alal.yft.detection.SiteAdapterOutcome
import com.alal.yft.detection.tiktok.TikTokPageScript
import com.alal.yft.extractor.api.SiteExtractionFailure
import com.alal.yft.extractor.sites.tiktok.TikTokAgents
import com.alal.yft.ui.components.YftTextButton
import com.alal.yft.ui.theme.YftTheme
import java.net.URI
import java.util.Locale

/**
 * P46: TikTok's check, shown to the user. YFT's hidden page asks TikTok's desktop page (P40),
 * and TikTok sometimes answers it with a check only a person can pass. This page opens the
 * same video's address with the same desktop agent and the browser's shared cookies, so once
 * the user passes the check, TikTok's answer cookies reach the hidden page and the page read
 * too. Done closes it and YFT asks again. YFT never answers the check itself; only TikTok's
 * own `https` pages load here.
 */
@Composable
fun SiteCheckDialog(url: String, onDone: () -> Unit) {
    val colors = YftTheme.colors
    Dialog(
        onDismissRequest = onDone,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(colors.background)
                .systemBarsPadding()
                .testTag("site-check"),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = CHECK_HINT,
                    color = colors.textPrimary,
                    modifier = Modifier.weight(1f),
                )
                YftTextButton(
                    text = "Done",
                    onClick = onDone,
                    modifier = Modifier.testTag("site-check-done"),
                )
            }
            CheckPage(url = url, modifier = Modifier.fillMaxSize())
        }
    }
}

@Composable
private fun CheckPage(url: String, modifier: Modifier) {
    val context = LocalContext.current
    val webView = remember { WebView(context).also { it.configureForCheck(context) } }
    DisposableEffect(webView, url) {
        if (isCheckPage(url)) webView.loadUrl(url)
        onDispose {
            CookieManager.getInstance().flush()
            webView.stopLoading()
            webView.destroy()
        }
    }
    AndroidView(factory = { webView }, modifier = modifier)
}

@SuppressLint("SetJavaScriptEnabled")
private fun WebView.configureForCheck(context: Context) {
    layoutParams = ViewGroup.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT,
        ViewGroup.LayoutParams.MATCH_PARENT,
    )
    val own = runCatching { WebSettings.getDefaultUserAgent(context) }.getOrNull()
    settings.apply {
        javaScriptEnabled = true
        domStorageEnabled = true
        allowFileAccess = false
        allowContentAccess = false
        javaScriptCanOpenWindowsAutomatically = false
        mediaPlaybackRequiresUserGesture = true
        mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
        setSupportMultipleWindows(false)
        setGeolocationEnabled(false)
        // The hidden page's agent, so the check's answer is for the same visitor.
        userAgentString = TikTokAgents().desktop(own)
        useWideViewPort = true
        loadWithOverviewMode = true
        builtInZoomControls = true
        displayZoomControls = false
        setSupportZoom(true)
    }
    CookieManager.getInstance().apply {
        setAcceptCookie(true)
        setAcceptThirdPartyCookies(this@configureForCheck, false)
    }
    webViewClient = object : WebViewClient() {
        override fun shouldOverrideUrlLoading(
            view: WebView,
            request: WebResourceRequest,
        ): Boolean = request.isForMainFrame && !isCheckPage(request.url.toString())
    }
}

/** Only TikTok's own `https` pages open in the check. */
internal fun isCheckPage(url: String): Boolean {
    val uri = runCatching { URI(url) }.getOrNull() ?: return false
    if (!uri.scheme.equals("https", ignoreCase = true)) return false
    val host = uri.host?.lowercase(Locale.US)?.removeSuffix(".") ?: return false
    return host == CHECK_HOST || host.endsWith(".$CHECK_HOST")
}

private const val CHECK_HOST = "tiktok.com"

/** The line above TikTok's check. */
const val CHECK_HINT = "Answer TikTok's check below (zoom in if it is small), then tap Done."

/** P46: when the browser offers TikTok's check, and its words. */
object SiteCheck {
    /** The browser's notice for TikTok's check, which the user answers with Show check. */
    const val NOTICE =
        "TikTok wants to check that this is not a bot. Tap Show check, answer it, then " +
            "tap Done: YFT looks again."

    /** The sheet's words for it: the sheet covers the notice's Show check. */
    const val SHEET =
        "TikTok wants to check that this is not a bot. Close this, tap Show check at the " +
            "top of the page, answer it, then tap Done."

    /**
     * The address of TikTok's check for a failed lookup of [pageUrl]: a TikTok post whose
     * lookup ended on a check. Null for every other answer.
     */
    fun urlFor(outcome: SiteAdapterOutcome.Failed, pageUrl: String): String? =
        pageUrl.takeIf {
            outcome.adapterId == TikTokPageScript.SITE_ID &&
                outcome.reason == SiteExtractionFailure.BOT_CHECK &&
                isCheckPage(it)
        }
}
