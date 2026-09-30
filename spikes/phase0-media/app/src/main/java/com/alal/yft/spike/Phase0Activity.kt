package com.alal.yft.spike

import android.annotation.SuppressLint
import android.app.Activity
import android.os.Bundle
import android.webkit.CookieManager
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView

/** Compileable feasibility harness; it is not the production browser or final UI. */
@UnstableApi
class Phase0Activity : Activity() {
    private lateinit var player: ExoPlayer
    private lateinit var playerView: PlayerView
    private lateinit var webView: WebView
    private lateinit var mediaUrl: EditText
    private lateinit var pageUrl: EditText
    private lateinit var status: TextView
    private var lastObservedHeaders: Map<String, String> = emptyMap()

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        player = ExoPlayer.Builder(this).build()
        playerView = PlayerView(this).apply { player = this@Phase0Activity.player }
        mediaUrl = EditText(this).apply { hint = "HTTPS direct, .m3u8 or .mpd URL" }
        pageUrl = EditText(this).apply { hint = "HTTPS web page URL" }
        status = TextView(this).apply { text = "Phase 0 feasibility harness" }
        webView = WebView(this)

        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            allowFileAccess = false
            allowContentAccess = false
            mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
            mediaPlaybackRequiresUserGesture = true
        }
        CookieManager.getInstance().apply {
            setAcceptCookie(true)
            setAcceptThirdPartyCookies(webView, true)
        }
        webView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean =
                request.url.scheme !in setOf("https", "about")

            override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? {
                if (request.method == "GET") lastObservedHeaders = request.requestHeaders.toMap()
                return null
            }

            override fun onPageFinished(view: WebView, url: String?) {
                view.evaluateJavascript(DomMediaProbe.script) { result -> status.text = "DOM media candidates: $result" }
            }
        }

        val loadPage = Button(this).apply {
            text = "Load page and probe DOM"
            setOnClickListener { pageUrl.text.toString().takeIf { it.startsWith("https://") }?.let(webView::loadUrl) }
        }
        val preview = Button(this).apply {
            text = "Preview media"
            setOnClickListener {
                val url = mediaUrl.text.toString()
                val context = BrowserRequestContext(
                    pageUrl = webView.url,
                    userAgent = webView.settings.userAgentString,
                    cookie = CookieManager.getInstance().getCookie(url),
                    observedHeaders = lastObservedHeaders,
                )
                runCatching {
                    player.setMediaSource(PreviewSourceFactory.create(url, requestContext = context))
                    player.prepare()
                    player.playWhenReady = false
                }.onSuccess {
                    status.text = "Prepared ${PreviewKind.fromUrl(url)} preview source"
                }.onFailure {
                    status.text = "Preview setup failed: ${it.message}"
                }
            }
        }

        setContentView(LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(pageUrl)
            addView(loadPage)
            addView(mediaUrl)
            addView(preview)
            addView(status)
            addView(playerView, LinearLayout.LayoutParams(-1, 0, 1f))
            addView(webView, LinearLayout.LayoutParams(-1, 0, 1f))
        })
    }

    override fun onDestroy() {
        playerView.player = null
        player.release()
        webView.stopLoading()
        webView.destroy()
        super.onDestroy()
    }
}
