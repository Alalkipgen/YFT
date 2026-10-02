package com.alal.yft.feature.browser

import android.webkit.CookieManager
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.alal.yft.core.browser.policy.SecureWebViewPolicy
import com.alal.yft.core.browser.webview.BrowserDownloadListener
import com.alal.yft.core.browser.webview.BrowserObservationSink
import com.alal.yft.core.browser.webview.SecureBrowserChromeClient
import com.alal.yft.core.browser.webview.SecureBrowserWebViewClient
import com.alal.yft.core.model.media.MediaCandidate
import com.alal.yft.ui.components.MediaCandidateCard
import com.alal.yft.ui.components.YftTopBar

@Composable
fun BrowserRoute(
    onNavigateBack: () -> Unit,
    onOpenPreview: () -> Unit,
    initialLink: String? = null,
    viewModel: BrowserViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var webView by remember { mutableStateOf<WebView?>(null) }
    var canGoBack by remember { mutableStateOf(false) }
    var canGoForward by remember { mutableStateOf(false) }

    fun refreshHistoryState() {
        canGoBack = webView?.canGoBack() == true
        canGoForward = webView?.canGoForward() == true
    }

    LaunchedEffect(uiState.currentUrl, uiState.isLoading) {
        refreshHistoryState()
    }
    LaunchedEffect(webView, initialLink) {
        val browser = webView ?: return@LaunchedEffect
        initialLink?.let(viewModel::openInitialLink)?.let(browser::loadUrl)
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

    BrowserScreen(
        uiState = uiState,
        canGoBack = canGoBack,
        canGoForward = canGoForward,
        onAddressChanged = viewModel::onAddressChanged,
        onGo = {
            viewModel.addressForLoading()?.let { url ->
                webView?.loadUrl(url)
            }
        },
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
        onPreviewCandidate = { candidate ->
            if (viewModel.selectForPreview(candidate)) onOpenPreview()
        },
        onNavigateBack = onNavigateBack,
        browserSurface = { modifier ->
            BrowserWebView(
                modifier = modifier,
                sink = viewModel,
                onWebViewReady = {
                    webView = it
                    refreshHistoryState()
                },
            )
        },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
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
    onPreviewCandidate: (MediaCandidate) -> Unit,
    onNavigateBack: () -> Unit,
    browserSurface: @Composable (Modifier) -> Unit,
) {
    var showCandidates by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(uiState.candidates.isEmpty()) {
        if (uiState.candidates.isEmpty()) showCandidates = false
    }

    if (showCandidates && uiState.candidates.isNotEmpty()) {
        CandidateBottomSheet(
            candidates = uiState.candidates,
            onPreviewCandidate = {
                showCandidates = false
                onPreviewCandidate(it)
            },
            onDismiss = { showCandidates = false },
        )
    }

    Scaffold(
        topBar = {
            YftTopBar(
                title = "Browser",
                canNavigateBack = true,
                onNavigateBack = onNavigateBack,
            )
        },
        floatingActionButton = {
            if (uiState.candidates.isNotEmpty()) {
                ExtendedFloatingActionButton(
                    onClick = { showCandidates = true },
                    icon = {
                        Icon(imageVector = Icons.Filled.PlayArrow, contentDescription = null)
                    },
                    text = {
                        Text(
                            text = if (uiState.candidates.size == 1) {
                                "1 media found"
                            } else {
                                "${uiState.candidates.size} media found"
                            },
                        )
                    },
                    modifier = Modifier.testTag("media-found-button"),
                )
            }
        },
    ) { contentPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(contentPadding),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedTextField(
                    value = uiState.address,
                    onValueChange = onAddressChanged,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("browser-address"),
                    label = { Text("HTTPS address") },
                    placeholder = { Text("example.com") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Uri,
                        imeAction = ImeAction.Go,
                    ),
                    keyboardActions = KeyboardActions(onGo = { onGo() }),
                )
                Button(
                    onClick = onGo,
                    modifier = Modifier.testTag("browser-go"),
                ) {
                    Text("Go")
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                IconButton(
                    onClick = onBrowserBack,
                    enabled = canGoBack,
                    modifier = Modifier.testTag("browser-history-back"),
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Previous page",
                    )
                }
                IconButton(
                    onClick = onBrowserForward,
                    enabled = canGoForward,
                    modifier = Modifier.testTag("browser-history-forward"),
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                        contentDescription = "Next page",
                    )
                }
                IconButton(
                    onClick = if (uiState.isLoading) onStop else onReload,
                    enabled = uiState.currentUrl != null,
                    modifier = Modifier.testTag("browser-reload-stop"),
                ) {
                    if (uiState.isLoading) {
                        Icon(imageVector = Icons.Filled.Close, contentDescription = "Stop loading")
                    } else {
                        Icon(imageVector = Icons.Filled.Refresh, contentDescription = "Reload page")
                    }
                }
                uiState.pageTitle?.let { title ->
                    Text(
                        text = title,
                        modifier = Modifier
                            .weight(1f)
                            .padding(top = 12.dp),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }

            if (uiState.isLoading) {
                LinearProgressIndicator(
                    progress = { uiState.progress.coerceIn(0, 100) / 100f },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp)
                        .testTag("browser-progress"),
                )
            } else {
                Spacer(modifier = Modifier.height(8.dp))
            }

            uiState.errorMessage?.let { message ->
                Surface(
                    color = MaterialTheme.colorScheme.errorContainer,
                    contentColor = MaterialTheme.colorScheme.onErrorContainer,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 4.dp)
                        .testTag("browser-error"),
                ) {
                    Text(
                        text = message,
                        modifier = Modifier.padding(12.dp),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }

            uiState.siteNotice?.let { notice ->
                Surface(
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 4.dp)
                        .testTag("browser-site-notice"),
                ) {
                    Text(
                        text = notice,
                        modifier = Modifier.padding(12.dp),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .testTag("browser-surface"),
            ) {
                browserSurface(Modifier.fillMaxSize())
            }
        }
    }
}

@Composable
private fun BrowserWebView(
    modifier: Modifier,
    sink: BrowserObservationSink,
    onWebViewReady: (WebView) -> Unit,
) {
    AndroidView(
        modifier = modifier,
        factory = { context ->
            val browser = WebView(context)
            SecureWebViewPolicy.apply(browser)
            val cookieManager = CookieManager.getInstance()
            browser.webViewClient = SecureBrowserWebViewClient(
                sink = sink,
                cookieProvider = cookieManager::getCookie,
                userAgentProvider = { browser.settings.userAgentString },
            )
            browser.webChromeClient = SecureBrowserChromeClient(sink)
            browser.setDownloadListener(
                BrowserDownloadListener(
                    pageUrlProvider = { browser.url },
                    cookieProvider = cookieManager::getCookie,
                    sink = sink,
                ),
            )
            onWebViewReady(browser)
            browser
        },
        onRelease = { browser ->
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CandidateBottomSheet(
    candidates: List<MediaCandidate>,
    onPreviewCandidate: (MediaCandidate) -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        modifier = Modifier.testTag("candidate-sheet"),
    ) {
        LazyColumn(
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Text(
                    text = "Detected media",
                    style = MaterialTheme.typography.headlineSmall,
                )
                Text(
                    text = "Resolve real variants and preview supported non-DRM media.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            itemsIndexed(
                items = candidates,
                key = { index, candidate ->
                    "${candidate.kind}-$index-${candidate.observedAtEpochMs}"
                },
            ) { index, candidate ->
                MediaCandidateCard(
                    candidate = candidate,
                    onPreview = { onPreviewCandidate(candidate) },
                    modifier = Modifier.testTag("candidate-$index"),
                )
            }
        }
    }
}
