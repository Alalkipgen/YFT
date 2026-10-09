package com.alal.yft.feature.browser

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.view.View
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import androidx.activity.compose.BackHandler
import androidx.annotation.DrawableRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.zIndex
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.currentStateAsState
import com.alal.yft.core.browser.policy.SecureWebViewPolicy
import com.alal.yft.core.browser.webview.BlockedNavigation
import com.alal.yft.core.browser.webview.BrowserDownloadListener
import com.alal.yft.core.browser.webview.BrowserNavigationGuard
import com.alal.yft.core.browser.webview.BrowserObservationSink
import com.alal.yft.core.browser.webview.BrowserPageUrl
import com.alal.yft.core.browser.webview.SecureBrowserChromeClient
import com.alal.yft.core.browser.webview.SecureBrowserWebViewClient
import com.alal.yft.extractor.master.android.WebViewPlaybackCapture
import com.alal.yft.core.data.history.BrowserHistoryEntry
import com.alal.yft.core.model.media.MediaGroup
import com.alal.yft.core.model.media.MediaGroups
import com.alal.yft.core.model.media.PageVideoList
import com.alal.yft.core.model.settings.HomeSite
import com.alal.yft.core.model.settings.SearchEngine
import com.alal.yft.feature.home.HomeLinks
import com.alal.yft.feature.home.rememberCopiedLinkHint
import com.alal.yft.ui.components.FoundMediaDividerInset
import com.alal.yft.ui.components.SHEET_SCRIM_ALPHA
import com.alal.yft.ui.components.YftAllowedMediaNote
import com.alal.yft.ui.components.YftCountBadge
import com.alal.yft.ui.components.YftDivider
import com.alal.yft.ui.components.YftFoundMediaRow
import com.alal.yft.ui.components.YftIcon
import com.alal.yft.ui.components.YftIconButton
import com.alal.yft.ui.components.YftOtherVideosHeader
import com.alal.yft.ui.components.YftSheetHandle
import com.alal.yft.ui.components.YftTextButton
import com.alal.yft.ui.components.isSavable
import com.alal.yft.ui.components.protectedHiddenLabel
import com.alal.yft.ui.theme.YftIcons
import com.alal.yft.ui.theme.YftShapes
import com.alal.yft.ui.theme.YftTheme
import java.net.URI
import java.util.Locale

@Composable
fun BrowserRoute(
    onNavigateBack: () -> Unit,
    initialLink: String? = null,
    onGoHome: () -> Unit = onNavigateBack,
    searchMode: Boolean = false,
    onDownloadLink: (String) -> Unit = {},
    onOpenQuickDownload: () -> Unit = {},
    viewModel: BrowserViewModel = hiltViewModel(),
    browserSettings: BrowserSettingsViewModel = hiltViewModel(),
    history: BrowserHistoryViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val preferences by browserSettings.preferences.collectAsStateWithLifecycle()
    val historyState by history.uiState.collectAsStateWithLifecycle()
    var historyOpen by rememberSaveable { mutableStateOf(false) }
    // P30: typed words (the view model asks BrowserSearch) search with the chosen engine.
    SideEffect { BrowserSearch.engine = preferences.searchEngine }
    val clipboard = LocalClipboardManager.current
    val copiedLinkHint = rememberCopiedLinkHint()
    val pageUrlState = remember { BrowserPageUrl() }
    var webView by remember { mutableStateOf<WebView?>(null) }
    // P32: what the WebView's clients block (Settings › Browser) and the last blocked page.
    val guard = remember { BrowserNavigationGuard() }
    SideEffect { guard.enabled = preferences.blockPopups }
    var blockedNotice by remember { mutableStateOf<BlockedNavigation?>(null) }
    // P31: the detection's view model stays the sink; page loads also reach the history.
    val sink = remember(viewModel, history) {
        val recorder = BrowserHistoryRecorder(
            onVisit = history::recordVisit,
            onTitle = history::renamePage,
        )
        val recording = HistoryRecordingSink(viewModel, recorder, currentTitle = { webView?.title })
        BlockedNavigationSink(recording) { blocked -> blockedNotice = blocked }
    }
    // Latch on the first navigation; redirects and later empty/failed pages never recreate it.
    var browserRequested by remember { mutableStateOf(uiState.currentUrl != null) }
    var pendingUrl by remember { mutableStateOf(uiState.currentUrl) }
    var canGoBack by remember { mutableStateOf(false) }
    var canGoForward by remember { mutableStateOf(false) }
    // The site player's full-screen view (P2) and how to tell the page it was closed.
    var fullscreenView by remember { mutableStateOf<View?>(null) }
    var exitFullscreen by remember { mutableStateOf<(() -> Unit)?>(null) }
    val fullscreenHandler = remember {
        object : SecureBrowserChromeClient.FullscreenHandler {
            override fun show(view: View, exit: () -> Unit) {
                fullscreenView = view
                exitFullscreen = exit
            }

            override fun hide() {
                fullscreenView = null
                exitFullscreen = null
            }
        }
    }

    fun loadPage(browser: WebView, url: String) {
        pageUrlState.update(url)
        // Typed, picked or opened from a notice: the user chose it, so it may redirect.
        guard.userNavigation()
        browser.loadUrl(url)
    }

    fun requestNavigation(url: String) {
        if (url == EMPTY_BROWSER_PAGE && !browserRequested) return
        pendingUrl = url
        browserRequested = true
    }

    fun submitAddress() {
        viewModel.addressForLoading()?.let(::requestNavigation)
    }

    fun openPage(url: String) {
        historyOpen = false
        viewModel.onAddressChanged(url)
        submitAddress()
    }

    fun refreshHistoryState() {
        canGoBack = webView?.canGoBack() == true
        canGoForward = webView?.canGoForward() == true
    }

    LaunchedEffect(uiState.currentUrl, uiState.isLoading) {
        refreshHistoryState()
    }
    LaunchedEffect(initialLink) {
        initialLink?.let(viewModel::openInitialLink)?.let(::requestNavigation)
    }
    // P5: the video on screen was found and selected; its download sheet opens.
    val openQuickDownload by rememberUpdatedState(onOpenQuickDownload)
    LaunchedEffect(viewModel) {
        viewModel.quickDownloadRequests.collect { openQuickDownload() }
    }
    // P37: the sheet's "Reload page and try again" reloads the tab once without its cache; the
    // cache comes back once that load finished.
    var shownReload by rememberSaveable { mutableIntStateOf(uiState.reloadRequest) }
    var noCacheApplied by remember { mutableStateOf(false) }
    LaunchedEffect(webView, uiState.reloadRequest) {
        val browser = webView ?: return@LaunchedEffect
        if (uiState.reloadRequest == shownReload) return@LaunchedEffect
        shownReload = uiState.reloadRequest
        browser.settings.cacheMode = WebSettings.LOAD_NO_CACHE
        noCacheApplied = true
        browser.reload()
    }
    LaunchedEffect(webView, uiState.noCacheLoad, noCacheApplied) {
        if (uiState.noCacheLoad || !noCacheApplied) return@LaunchedEffect
        webView?.settings?.cacheMode = WebSettings.LOAD_DEFAULT
        noCacheApplied = false
    }
    LaunchedEffect(webView, pendingUrl) {
        val browser = webView ?: return@LaunchedEffect
        val url = pendingUrl ?: return@LaunchedEffect
        loadPage(browser, url)
        pendingUrl = null
    }
    DisposableEffect(Unit) {
        onDispose {
            webView = null
        }
    }
    BackHandler(enabled = canGoBack) {
        webView?.goBack()
        refreshHistoryState()
    }
    val lifecycleState by LocalLifecycleOwner.current.lifecycle.currentStateAsState()
    val resumed = lifecycleState.isAtLeast(Lifecycle.State.RESUMED)
    Box(modifier = Modifier.fillMaxSize()) {
        BrowserScreen(
            uiState = uiState,
            canGoBack = canGoBack,
            canGoForward = canGoForward,
            onAddressChanged = viewModel::onAddressChanged,
            onGo = ::submitAddress,
            onBrowserBack = {
                webView?.goBack()
                refreshHistoryState()
            },
            onBrowserForward = {
                webView?.goForward()
                refreshHistoryState()
            },
            onReload = { webView?.reload() },
            onStop = {
                webView?.stopLoading()
                refreshHistoryState()
            },
            onDownloadGroup = { group ->
                if (viewModel.selectForDownload(group)) onOpenQuickDownload()
            },
            onDownloadPage = {
                // P12: this page's video, or its sheet waiting for the page's own lookup.
                if (viewModel.openPageVideo()) onOpenQuickDownload()
            },
            onDownloadMain = {
                // P12: the page says which video plays; the view model opens the main one.
                val script = viewModel.mainVideoScript()
                val browser = webView
                if (browser != null) {
                    browser.evaluateJavascript(script, viewModel::onPlayingVideoResult)
                } else {
                    viewModel.onPlayingVideoResult(null)
                }
            },
            onDownloadFocused = {
                // P5: the page script runs on the main thread; the view model never sees the page.
                val browser = webView
                val script = browser?.let { viewModel.focusedVideoScript() }
                if (browser != null && script != null) {
                    browser.evaluateJavascript(script, viewModel::onFocusedVideoResult)
                }
            },
            onNavigateBack = onNavigateBack,
            onGoHome = onGoHome,
            hasBrowserPage = browserRequested,
            searchMode = searchMode,
            onSearch = { url ->
                viewModel.onAddressChanged(url)
                submitAddress()
            },
            copiedLinkHint = copiedLinkHint,
            onDownloadCopiedLink = {
                // Read only after this tap; Home looks the link up and opens its sheet.
                HomeLinks.fromClipboard(clipboard.getText()?.text)?.let(onDownloadLink)
            },
            onUseCopiedLink = {
                // The description above is safe to inspect; payload is read only after this tap.
                HomeLinks.fromClipboard(clipboard.getText()?.text)?.let { link ->
                    viewModel.onAddressChanged(link)
                    submitAddress()
                }
            },
            onOpenSite = { site ->
                viewModel.onAddressChanged(site.url)
                submitAddress()
            },
            onRetrySiteLookup = viewModel::retrySiteLookup,
            onRetryFocusedLookup = viewModel::retryFocusedLookup,
            fullScreen = fullscreenView != null,
            // P13: a download sheet over the browser pauses this screen; the wide button goes.
            downloadSheetOpen = !resumed,
            searchEngine = preferences.searchEngine,
            recentPages = historyState.recent,
            onOpenRecent = { page -> openPage(page.url) },
            onShowHistory = { historyOpen = true },
            browserSurface = { modifier ->
                BrowserWebView(
                    modifier = modifier,
                    sink = sink,
                    pageUrlState = pageUrlState,
                    fullscreenHandler = fullscreenHandler,
                    guard = guard,
                    masterCapture = viewModel.masterCapture,
                    onWebViewReady = {
                        webView = it
                        refreshHistoryState()
                    },
                )
            },
        )
        if (historyOpen) {
            val now = remember { System.currentTimeMillis() }
            BrowserHistoryPanel(
                state = historyState,
                nowEpochMs = now,
                onQueryChanged = history::onQueryChanged,
                onOpen = { page -> openPage(page.url) },
                onDelete = { page -> history.delete(page.url) },
                onClear = history::clear,
                onClose = { historyOpen = false },
            )
        }
        blockedNotice?.let { blocked ->
            BrowserBlockedNotice(
                blocked = blocked,
                onOpen = {
                    blockedNotice = null
                    openPage(blocked.url)
                },
                onDismiss = { blockedNotice = null },
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .windowInsetsPadding(
                        WindowInsets.safeDrawing.only(
                            WindowInsetsSides.Top + WindowInsetsSides.Horizontal,
                        ),
                    )
                    .padding(start = 16.dp, top = BLOCKED_NOTICE_TOP, end = 16.dp),
            )
        }
        fullscreenView?.let { view ->
            BrowserFullscreen(
                view = view,
                onExit = {
                    val exit = exitFullscreen
                    fullscreenView = null
                    exitFullscreen = null
                    exit?.invoke()
                },
            )
        }
    }
}

/**
 * The page's video in full screen: the player's own view over the whole browser on black, with
 * the system bars hidden until a swipe shows them for a moment. Composed last, so Back leaves
 * full screen before it goes back in the page.
 */
@Composable
private fun BrowserFullscreen(view: View, onExit: () -> Unit) {
    BackHandler(onBack = onExit)
    val hostView = LocalView.current
    DisposableEffect(hostView) {
        val window = hostView.context.findActivity()?.window
        val controller = window?.let { WindowCompat.getInsetsController(it, hostView) }
        controller?.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        controller?.hide(WindowInsetsCompat.Type.systemBars())
        onDispose { controller?.show(WindowInsetsCompat.Type.systemBars()) }
    }
    key(view) {
        AndroidView(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
                .testTag("browser-fullscreen"),
            factory = { context ->
                FrameLayout(context).apply {
                    (view.parent as? ViewGroup)?.removeView(view)
                    addView(
                        view,
                        FrameLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT,
                        ),
                    )
                }
            },
            onRelease = { container -> container.removeAllViews() },
        )
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

/**
 * The in-app browser (`02`): a close button and address pill on top, the page, the docked
 * "Found on this page" sheet and a back / forward / reload / YFT Home toolbar. The sheet only
 * lists media YFT may save; DRM-protected candidates are counted in a note and never offered.
 * It starts as a peek so the page stays usable and expands on tap or an upward drag.
 */
@Composable
fun BrowserScreen(
    uiState: BrowserUiState,
    canGoBack: Boolean,
    canGoForward: Boolean,
    onAddressChanged: (String) -> Unit,
    onGo: () -> Unit,
    onBrowserBack: () -> Unit,
    onBrowserForward: () -> Unit,
    onReload: () -> Unit,
    onStop: () -> Unit,
    onDownloadGroup: (MediaGroup) -> Unit,
    onNavigateBack: () -> Unit,
    onGoHome: () -> Unit = onNavigateBack,
    initialSheetExpanded: Boolean = false,
    hasBrowserPage: Boolean = uiState.currentUrl != null || uiState.isLoading,
    searchMode: Boolean = false,
    onSearch: (url: String) -> Unit = {},
    copiedLinkHint: Boolean = false,
    onDownloadCopiedLink: () -> Unit = {},
    onUseCopiedLink: () -> Unit = {},
    onOpenSite: (HomeSite) -> Unit = {},
    onRetrySiteLookup: () -> Unit = {},
    onRetryFocusedLookup: () -> Unit = {},
    onDownloadFocused: () -> Unit = {},
    onDownloadPage: () -> Unit = {},
    onDownloadMain: () -> Unit = {},
    fullScreen: Boolean = false,
    downloadSheetOpen: Boolean = false,
    searchEngine: SearchEngine = BrowserSearch.engine,
    recentPages: List<BrowserHistoryEntry> = emptyList(),
    onOpenRecent: (BrowserHistoryEntry) -> Unit = {},
    onShowHistory: () -> Unit = {},
    browserSurface: @Composable (Modifier) -> Unit,
) {
    val colors = YftTheme.colors
    val savable = remember(uiState.candidates) { uiState.candidates.filter { it.isSavable } }
    val hiddenCount = uiState.candidates.size - savable.size
    // One row, one count and one sheet per video: its qualities and audio are inside (P3).
    // A video a site adapter named is the page's video; its player's files are not more (P3-FIX).
    // On a site's page they never are, even before it named one (P12).
    val videos = remember(savable, uiState.sitePage) {
        MediaGroups.pageVideos(savable, adapterSite = uiState.sitePage)
    }
    // P24: the count and the button's label count the page's videos; its previews and ads
    // follow them under "Other videos on this page". The tap still sees every entry.
    val pageList = remember(videos) { MediaGroups.ofPage(videos) }
    var sheetExpanded by rememberSaveable { mutableStateOf(initialSheetExpanded) }
    // P12: "Other videos on this page" in the main video's sheet opens this list, once per ask:
    // coming back to the browser later must not open it again.
    var shownFoundList by rememberSaveable { mutableIntStateOf(uiState.foundListRequest) }
    LaunchedEffect(uiState.foundListRequest) {
        if (uiState.foundListRequest == shownFoundList) return@LaunchedEffect
        shownFoundList = uiState.foundListRequest
        if (videos.isNotEmpty()) sheetExpanded = true
    }
    var editingAddress by remember { mutableStateOf(false) }
    LaunchedEffect(savable.isEmpty()) {
        if (savable.isEmpty()) sheetExpanded = false
    }
    BackHandler(enabled = sheetExpanded && savable.isNotEmpty()) { sheetExpanded = false }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.card)
            .imePadding()
            .windowInsetsPadding(
                WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal),
            ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .zIndex(1f)
                .background(colors.card)
                .padding(start = 4.dp, end = 12.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            YftIconButton(
                icon = YftIcons.Close,
                contentDescription = "Close browser",
                onClick = onNavigateBack,
                modifier = Modifier.testTag("browser-close"),
            )
            BrowserAddressField(
                address = uiState.address,
                isLoading = uiState.isLoading,
                canReload = uiState.currentUrl != null,
                onAddressChanged = onAddressChanged,
                onGo = onGo,
                onReload = onReload,
                onStop = onStop,
                onEditingChanged = { editingAddress = it },
                focusOnStart = searchMode && !hasBrowserPage,
                modifier = Modifier.weight(1f),
            )
        }
        Box(modifier = Modifier.fillMaxWidth().height(PROGRESS_HEIGHT)) {
            if (uiState.isLoading) {
                LinearProgressIndicator(
                    progress = { uiState.progress.coerceIn(0, 100) / 100f },
                    modifier = Modifier
                        .fillMaxSize()
                        .testTag("browser-progress"),
                    color = colors.accent,
                    trackColor = Color.Transparent,
                    gapSize = 0.dp,
                    drawStopIndicator = {},
                )
            }
        }
        uiState.errorMessage?.let { message ->
            BrowserBanner(
                text = message,
                icon = YftIcons.Error,
                container = colors.coralSoft,
                content = colors.coralText,
                modifier = Modifier.testTag("browser-error"),
            )
        }
        uiState.siteNotice?.let { notice ->
            BrowserBanner(
                text = notice,
                icon = YftIcons.Info,
                container = colors.chip,
                content = colors.textPrimary,
                modifier = Modifier.testTag("browser-site-notice"),
                action = if (uiState.canRetrySiteLookup) {
                    {
                        YftTextButton(
                            text = "Try again",
                            onClick = onRetrySiteLookup,
                            modifier = Modifier.testTag("browser-site-retry"),
                        )
                    }
                } else {
                    null
                },
            )
        }
        if (hasBrowserPage && savable.isEmpty()) {
            protectedHiddenLabel(hiddenCount)?.let { note ->
                BrowserBanner(
                    text = note,
                    icon = YftIcons.Shield,
                    container = colors.chip,
                    content = colors.textPrimary,
                    modifier = Modifier.testTag("browser-protected-notice"),
                )
            }
        }
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .clipToBounds(),
        ) {
            val sheetMaxHeight = maxHeight * SHEET_MAX_FRACTION
            val fabAction = BrowserDownloadFab.action(
                savableCount = videos.size,
                findsFocusedVideo = uiState.findsFocusedVideo,
                feedPage = uiState.feedPage,
                sitePage = uiState.sitePage,
            )
            val fabVisible = BrowserDownloadFab.isVisible(
                hasPage = hasBrowserPage,
                savableCount = videos.size,
                sheetExpanded = sheetExpanded,
                editingAddress = editingAddress,
                findsFocusedVideo = uiState.findsFocusedVideo,
                sitePage = uiState.sitePage,
            ) && fabAction != null
            // P13: one button at a time; the wide one under the page where a tap means one video.
            val wideVisible = BrowserDownloadFab.wideVisible(
                action = fabAction,
                roundVisible = fabVisible,
                fullScreen = fullScreen,
                sheetOpen = downloadSheetOpen,
                keyboardUp = WindowInsets.ime.getBottom(LocalDensity.current) > 0,
            )
            val roundVisible = fabVisible && !wideVisible
            var wideHeight by remember { mutableIntStateOf(0) }
            val pageBottomPadding = if (wideVisible) {
                with(LocalDensity.current) { wideHeight.toDp() }
            } else {
                0.dp
            }
            if (hasBrowserPage) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .clipToBounds()
                        .testTag("browser-surface"),
                ) {
                    // P13: the page ends above the wide button, so its own controls stay usable.
                    browserSurface(Modifier.fillMaxSize().padding(bottom = pageBottomPadding))
                }
            } else {
                BrowserStartPage(
                    query = uiState.address,
                    sites = uiState.sites,
                    copiedLinkHint = copiedLinkHint,
                    onSearch = onSearch,
                    onDownloadCopiedLink = onDownloadCopiedLink,
                    onUseCopiedLink = onUseCopiedLink,
                    onOpenSite = onOpenSite,
                    onEditSites = onGoHome,
                    modifier = Modifier.fillMaxSize(),
                    searchEngine = searchEngine,
                    recentPages = recentPages,
                    onOpenRecent = onOpenRecent,
                    onShowHistory = onShowHistory,
                )
            }
            val showSheet = hasBrowserPage && savable.isNotEmpty() && !editingAddress
            // Qualified so the outer Column's scoped overload is not picked up implicitly.
            androidx.compose.animation.AnimatedVisibility(
                visible = showSheet && sheetExpanded,
                enter = fadeIn(),
                exit = fadeOut(),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(SHEET_SCRIM)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClickLabel = "Hide found media",
                        ) { sheetExpanded = false }
                        .testTag("found-sheet-scrim"),
                )
            }
            val findsOnScreen = fabAction == BrowserDownloadFab.Action.FIND_VIDEO_ON_SCREEN
            val focusNotice = uiState.focusNotice?.takeIf { hasBrowserPage }
            val busy = uiState.sitePage && uiState.pageLookupRunning
            val onDownloadTap = {
                // One video opens the download sheet; several the main one's; a site's page
                // its own video (P12); a feed looks for the video on screen (P5).
                when (fabAction) {
                    BrowserDownloadFab.Action.OPEN_VIDEO ->
                        videos.singleOrNull()?.let(onDownloadGroup)
                    BrowserDownloadFab.Action.OPEN_MAIN_VIDEO -> onDownloadMain()
                    BrowserDownloadFab.Action.OPEN_PAGE_VIDEO -> onDownloadPage()
                    BrowserDownloadFab.Action.FIND_VIDEO_ON_SCREEN ->
                        if (!uiState.findingFocusedVideo) onDownloadFocused()
                    null -> Unit
                }
                Unit
            }
            if (showSheet || fabVisible || focusNotice != null) {
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth(),
                    horizontalAlignment = Alignment.End,
                ) {
                    focusNotice?.let { notice ->
                        BrowserBanner(
                            text = notice,
                            icon = YftIcons.Info,
                            container = colors.chip,
                            content = colors.textPrimary,
                            modifier = Modifier.testTag("browser-focus-notice"),
                            action = if (uiState.canRetryFocusedLookup) {
                                {
                                    YftTextButton(
                                        text = "Retry",
                                        onClick = onRetryFocusedLookup,
                                        modifier = Modifier.testTag("browser-focus-retry"),
                                    )
                                }
                            } else {
                                null
                            },
                        )
                    }
                    if (roundVisible) {
                        BrowserDownloadButton(
                            savableCount = pageList.videos.size,
                            findsOnScreen = findsOnScreen,
                            sitePage = fabAction == BrowserDownloadFab.Action.OPEN_PAGE_VIDEO,
                            busy = busy,
                            onClick = onDownloadTap,
                            modifier = Modifier.padding(end = 16.dp, bottom = 12.dp),
                        )
                    }
                    if (showSheet) {
                        FoundMediaSheet(
                            list = pageList,
                            hiddenCount = hiddenCount,
                            expanded = sheetExpanded,
                            onExpandedChange = { sheetExpanded = it },
                            onDownloadGroup = onDownloadGroup,
                            modifier = Modifier.heightIn(max = sheetMaxHeight),
                        )
                    }
                    if (wideVisible) {
                        BrowserWideDownloadButton(
                            onClick = onDownloadTap,
                            busy = busy,
                            modifier = Modifier.onSizeChanged { wideHeight = it.height },
                        )
                    }
                }
            }
        }
        BrowserToolbar(
            canGoBack = canGoBack,
            canGoForward = canGoForward,
            isLoading = uiState.isLoading,
            canReload = uiState.currentUrl != null,
            onBrowserBack = onBrowserBack,
            onBrowserForward = onBrowserForward,
            onReload = onReload,
            onStop = onStop,
            onGoHome = onGoHome,
            onShowHistory = onShowHistory,
        )
    }
}

/**
 * The address pill. While it is not being edited it reads like the design: a lock for HTTPS,
 * the host in Ink and the path in Slate, never the query (it can carry tokens). Tapping it
 * edits the full address with the whole text selected, so a new address replaces it at once.
 */
@Composable
private fun BrowserAddressField(
    address: String,
    isLoading: Boolean,
    canReload: Boolean,
    onAddressChanged: (String) -> Unit,
    onGo: () -> Unit,
    onReload: () -> Unit,
    onStop: () -> Unit,
    onEditingChanged: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    focusOnStart: Boolean = false,
) {
    val colors = YftTheme.colors
    val focusManager = LocalFocusManager.current
    val focusRequester = remember { FocusRequester() }
    var focused by remember { mutableStateOf(false) }
    // Search to download opens with the keyboard up, once; returning keeps the user's choice.
    var startFocusDone by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(focusOnStart) {
        if (focusOnStart && !startFocusDone) {
            startFocusDone = true
            focusRequester.requestFocus()
        }
    }
    var field by remember { mutableStateOf(TextFieldValue(address)) }
    LaunchedEffect(address, focused) {
        // Follow the page (redirects, history) unless the user is typing a new address.
        if (!focused && field.text != address) field = TextFieldValue(address)
    }
    val display = remember(address) { addressDisplay(address) }
    val submit = {
        onGo()
        focusManager.clearFocus()
    }
    Row(
        modifier = modifier
            .heightIn(min = ADDRESS_HEIGHT)
            .clip(YftShapes.pill)
            .background(colors.chip)
            .padding(start = 14.dp, end = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        YftIcon(
            icon = if (display?.secure == true && !focused) YftIcons.Lock else YftIcons.Globe,
            contentDescription = if (display?.secure == true && !focused) "Secure page" else null,
            tint = colors.textSecondary,
            size = 18.dp,
        )
        Box(
            modifier = Modifier
                .weight(1f)
                .padding(start = 8.dp, end = 4.dp),
            contentAlignment = Alignment.CenterStart,
        ) {
            BasicTextField(
                value = field,
                onValueChange = {
                    field = it
                    onAddressChanged(it.text)
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(focusRequester)
                    .onFocusChanged { state ->
                        if (state.isFocused != focused) {
                            focused = state.isFocused
                            onEditingChanged(state.isFocused)
                        }
                    }
                    .testTag("browser-address"),
                // Alpha-zero hides the entire node from Android's accessibility tree.
                // Hide only the ink; the editable field stays reachable under its styled URL.
                textStyle = MaterialTheme.typography.bodyLarge.copy(
                    color = if (focused || display == null) {
                        colors.textPrimary
                    } else {
                        Color.Transparent
                    },
                ),
                singleLine = true,
                cursorBrush = SolidColor(colors.textPrimary),
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Uri,
                    imeAction = ImeAction.Go,
                    autoCorrectEnabled = false,
                ),
                keyboardActions = KeyboardActions(onGo = { submit() }),
                decorationBox = { innerTextField ->
                    Box(contentAlignment = Alignment.CenterStart) {
                        if (field.text.isEmpty()) {
                            Text(
                                text = ADDRESS_PLACEHOLDER,
                                color = colors.textSecondary,
                                style = MaterialTheme.typography.bodyLarge,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        innerTextField()
                    }
                },
            )
            if (!focused && display != null) {
                // Visual only: accessibility services read and edit the field underneath.
                Text(
                    text = buildAnnotatedString {
                        append(display.host)
                        withStyle(SpanStyle(color = colors.textSecondary)) { append(display.path) }
                    },
                    // Cleared before the click handler, so TalkBack skips this layer entirely.
                    modifier = Modifier
                        .fillMaxWidth()
                        .clearAndSetSemantics {}
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                        ) {
                            field = field.copy(selection = TextRange(0, field.text.length))
                            focusRequester.requestFocus()
                        },
                    color = colors.textPrimary,
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        when {
            focused && field.text.isNotBlank() -> YftIconButton(
                icon = YftIcons.ArrowForward,
                contentDescription = "Go",
                onClick = submit,
                modifier = Modifier.testTag("browser-go"),
                tint = colors.icon,
            )
            focused -> Spacer(modifier = Modifier.size(12.dp))
            else -> ReloadStopButton(
                isLoading = isLoading,
                enabled = canReload,
                onReload = onReload,
                onStop = onStop,
                modifier = Modifier.testTag("browser-reload-stop"),
            )
        }
    }
}

@Composable
private fun ReloadStopButton(
    isLoading: Boolean,
    enabled: Boolean,
    onReload: () -> Unit,
    onStop: () -> Unit,
    modifier: Modifier = Modifier,
) {
    YftIconButton(
        icon = if (isLoading) YftIcons.Close else YftIcons.Refresh,
        contentDescription = if (isLoading) "Stop loading" else "Reload page",
        onClick = if (isLoading) onStop else onReload,
        modifier = modifier,
        enabled = enabled || isLoading,
    )
}

@Composable
private fun BrowserBanner(
    text: String,
    @DrawableRes icon: Int,
    container: Color,
    content: Color,
    modifier: Modifier = Modifier,
    action: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp)
            .clip(YftShapes.thumbnail)
            .background(container)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        YftIcon(icon = icon, contentDescription = null, tint = content, size = 20.dp)
        Text(
            text = text,
            color = content,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f),
        )
        action?.invoke()
    }
}

/** The docked sheet from `02`: a peek header that expands into the savable media list. */
@Composable
private fun FoundMediaSheet(
    list: PageVideoList,
    hiddenCount: Int,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    onDownloadGroup: (MediaGroup) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = YftTheme.colors
    val currentExpanded by rememberUpdatedState(expanded)
    val currentOnExpandedChange by rememberUpdatedState(onExpandedChange)
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .testTag("found-sheet"),
        shape = YftShapes.sheet,
        color = colors.card,
        contentColor = colors.textPrimary,
        shadowElevation = 12.dp,
        border = if (colors.isDark) BorderStroke(1.dp, colors.border) else null,
    ) {
        Column {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .pointerInput(Unit) {
                        // A short drag up opens the list and a drag down closes it; a tap is
                        // left to the click below.
                        var total = 0f
                        detectVerticalDragGestures(
                            onDragStart = { total = 0f },
                            onDragEnd = {
                                val threshold = DRAG_THRESHOLD.toPx()
                                if (total < -threshold && !currentExpanded) {
                                    currentOnExpandedChange(true)
                                }
                                if (total > threshold && currentExpanded) {
                                    currentOnExpandedChange(false)
                                }
                            },
                        ) { change, amount ->
                            change.consume()
                            total += amount
                        }
                    }
                    .clickable(
                        role = Role.Button,
                        onClickLabel = if (expanded) "Hide found media" else "Show found media",
                    ) { onExpandedChange(!expanded) }
                    .semantics {
                        stateDescription = if (expanded) "Expanded" else "Collapsed"
                    }
                    .testTag("media-found-button"),
            ) {
                YftSheetHandle(modifier = Modifier.padding(top = 2.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                        .padding(start = 20.dp, end = 16.dp, bottom = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = FOUND_TITLE,
                            modifier = Modifier
                                .weight(1f, fill = false)
                                .semantics {
                                    heading()
                                    contentDescription = foundCountLabel(list.videos.size)
                                },
                            color = colors.textPrimary,
                            style = MaterialTheme.typography.titleLarge,
                        )
                        Box(
                            modifier = Modifier
                                .testTag("found-count")
                                .padding(start = 10.dp),
                        ) {
                            YftCountBadge(
                                count = list.videos.size,
                                modifier = Modifier.clearAndSetSemantics {},
                                minSize = 24.dp,
                            )
                        }
                    }
                    if (!expanded) {
                        YftIcon(
                            icon = YftIcons.ExpandMore,
                            contentDescription = null,
                            tint = colors.textSecondary,
                            modifier = Modifier.rotate(180f),
                        )
                    }
                }
            }
            AnimatedVisibility(
                visible = expanded,
                enter = expandVertically(expandFrom = Alignment.Top) + fadeIn(),
                exit = shrinkVertically(shrinkTowards = Alignment.Top) + fadeOut(),
            ) {
                Column {
                    LazyColumn(
                        modifier = Modifier
                            .weight(1f, fill = false)
                            .testTag("found-list"),
                    ) {
                        itemsIndexed(
                            items = list.all,
                            key = { index, video -> "$index-${video.key}" },
                        ) { index, video ->
                            if (index == list.videos.size) {
                                YftOtherVideosHeader(
                                    count = list.previews.size,
                                    modifier = Modifier
                                        .padding(start = 20.dp, end = 16.dp, top = 12.dp)
                                        .testTag("found-other-videos"),
                                )
                            } else if (index > 0) {
                                YftDivider(
                                    modifier = Modifier.padding(start = FoundMediaDividerInset),
                                )
                            }
                            YftFoundMediaRow(
                                video = video,
                                onPreview = { onDownloadGroup(video) },
                                modifier = Modifier.testTag("found-item-$index"),
                                previewTag = "found-preview-$index",
                            )
                        }
                        protectedHiddenLabel(hiddenCount)?.let { note ->
                            item {
                                Text(
                                    text = note,
                                    modifier = Modifier
                                        .padding(horizontal = 20.dp, vertical = 8.dp)
                                        .testTag("found-protected-note"),
                                    color = colors.textSecondary,
                                    style = MaterialTheme.typography.bodyMedium,
                                )
                            }
                        }
                    }
                    YftAllowedMediaNote(
                        modifier = Modifier.padding(
                            start = 16.dp,
                            end = 16.dp,
                            top = 4.dp,
                            bottom = 12.dp,
                        ),
                    )
                }
            }
        }
    }
}

@Composable
private fun BrowserToolbar(
    canGoBack: Boolean,
    canGoForward: Boolean,
    isLoading: Boolean,
    canReload: Boolean,
    onBrowserBack: () -> Unit,
    onBrowserForward: () -> Unit,
    onReload: () -> Unit,
    onStop: () -> Unit,
    onGoHome: () -> Unit,
    onShowHistory: () -> Unit,
) {
    val colors = YftTheme.colors
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.card)
            .navigationBarsPadding(),
    ) {
        YftDivider()
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            YftIconButton(
                icon = YftIcons.ArrowBack,
                contentDescription = "Previous page",
                onClick = onBrowserBack,
                enabled = canGoBack,
                modifier = Modifier.testTag("browser-history-back"),
            )
            YftIconButton(
                icon = YftIcons.ArrowForward,
                contentDescription = "Next page",
                onClick = onBrowserForward,
                enabled = canGoForward,
                modifier = Modifier.testTag("browser-history-forward"),
            )
            ReloadStopButton(
                isLoading = isLoading,
                enabled = canReload,
                onReload = onReload,
                onStop = onStop,
                modifier = Modifier.testTag("browser-toolbar-reload"),
            )
            YftIconButton(
                icon = YftIcons.Home,
                contentDescription = "YFT Home",
                onClick = onGoHome,
                modifier = Modifier.testTag("browser-home"),
            )
            BrowserMenu(onShowHistory = onShowHistory)
        }
    }
}

/** The browser's menu (P31): History. */
@Composable
private fun BrowserMenu(onShowHistory: () -> Unit) {
    val colors = YftTheme.colors
    var open by remember { mutableStateOf(false) }
    Box {
        YftIconButton(
            icon = YftIcons.MoreHoriz,
            contentDescription = "Browser menu",
            onClick = { open = true },
            modifier = Modifier.testTag("browser-menu"),
        )
        DropdownMenu(
            expanded = open,
            onDismissRequest = { open = false },
            shape = YftShapes.card,
            containerColor = colors.card,
        ) {
            DropdownMenuItem(
                text = { Text(text = "History", color = colors.textPrimary) },
                onClick = {
                    open = false
                    onShowHistory()
                },
                modifier = Modifier.testTag("browser-menu-history"),
                leadingIcon = {
                    YftIcon(icon = YftIcons.History, contentDescription = null, tint = colors.icon)
                },
            )
        }
    }
}

/** How the address pill shows a page address: host, then the path without query or fragment. */
internal data class AddressDisplay(val host: String, val path: String, val secure: Boolean)

internal fun addressDisplay(address: String): AddressDisplay? {
    val uri = runCatching { URI(address.trim()) }.getOrNull() ?: return null
    val scheme = uri.scheme?.lowercase(Locale.US)
    if (scheme != "https" && scheme != "http") return null
    val host = uri.host?.takeIf(String::isNotBlank) ?: return null
    val port = if (uri.port == -1) "" else ":${uri.port}"
    val path = uri.path.orEmpty().takeUnless { it == "/" }.orEmpty()
    return AddressDisplay(
        host = host.removePrefix("www.") + port,
        path = path,
        secure = scheme == "https",
    )
}

internal fun foundCountLabel(count: Int): String = when (count) {
    1 -> "$FOUND_TITLE, 1 item"
    else -> "$FOUND_TITLE, $count items"
}

private const val FOUND_TITLE = "Found on this page"
private const val ADDRESS_PLACEHOLDER = "Search or enter a web address"
private const val EMPTY_BROWSER_PAGE = "about:blank"
private const val SHEET_MAX_FRACTION = 0.72f
private val SHEET_SCRIM = Color.Black.copy(alpha = SHEET_SCRIM_ALPHA)
private val ADDRESS_HEIGHT = 44.dp
private val PROGRESS_HEIGHT = 2.dp
private val DRAG_THRESHOLD = 24.dp

/** The blocked-page notice sits just under the address bar and its progress line. */
private val BLOCKED_NOTICE_TOP = 68.dp

@Composable
private fun BrowserWebView(
    modifier: Modifier,
    sink: BrowserObservationSink,
    pageUrlState: BrowserPageUrl,
    fullscreenHandler: SecureBrowserChromeClient.FullscreenHandler,
    guard: BrowserNavigationGuard,
    masterCapture: WebViewPlaybackCapture?,
    onWebViewReady: (WebView) -> Unit,
) {
    AndroidView(
        modifier = modifier,
        factory = { context ->
            val browser = WebView(context)
            // Without these, the WebView treats its height as "wrap content" and lays pages out
            // with a zero viewport height: Facebook's reel video got a 320x0 box (P2).
            browser.layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            )
            SecureWebViewPolicy.apply(browser)
            val cookieManager = CookieManager.getInstance()
            val cachedUserAgent = browser.settings.userAgentString
            masterCapture?.attach(browser)
            val observingSink = masterCapture?.decorate(sink, browser) ?: sink
            browser.webViewClient = SecureBrowserWebViewClient(
                sink = observingSink,
                cookieProvider = cookieManager::getCookie,
                userAgentProvider = { cachedUserAgent },
                pageUrlState = pageUrlState,
                guard = guard,
            )
            browser.webChromeClient =
                SecureBrowserChromeClient(observingSink, fullscreenHandler, guard)
            browser.setDownloadListener(
                BrowserDownloadListener(
                    pageUrlProvider = pageUrlState::get,
                    cookieProvider = cookieManager::getCookie,
                    sink = observingSink,
                ),
            )
            onWebViewReady(browser)
            browser
        },
        onRelease = { browser ->
            masterCapture?.detach(browser)
            pageUrlState.update(null)
            browser.stopLoading()
            browser.setDownloadListener(null)
            browser.webChromeClient = null
            browser.webViewClient = WebViewClient()
            browser.clearHistory()
            browser.removeAllViews()
            browser.destroy()
        },
    )
}
