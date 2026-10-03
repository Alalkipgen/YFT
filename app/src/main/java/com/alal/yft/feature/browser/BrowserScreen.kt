package com.alal.yft.feature.browser

import android.webkit.CookieManager
import android.webkit.WebView
import android.webkit.WebViewClient
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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalFocusManager
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
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
import com.alal.yft.ui.components.FoundMediaDividerInset
import com.alal.yft.ui.components.SHEET_SCRIM_ALPHA
import com.alal.yft.ui.components.YftAllowedMediaNote
import com.alal.yft.ui.components.YftCountBadge
import com.alal.yft.ui.components.YftDivider
import com.alal.yft.ui.components.YftFoundMediaRow
import com.alal.yft.ui.components.YftIcon
import com.alal.yft.ui.components.YftIconButton
import com.alal.yft.ui.components.YftSheetHandle
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
    onOpenPreview: () -> Unit,
    initialLink: String? = null,
    onGoHome: () -> Unit = onNavigateBack,
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
        onGoHome = onGoHome,
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
    onPreviewCandidate: (MediaCandidate) -> Unit,
    onNavigateBack: () -> Unit,
    onGoHome: () -> Unit = onNavigateBack,
    initialSheetExpanded: Boolean = false,
    browserSurface: @Composable (Modifier) -> Unit,
) {
    val colors = YftTheme.colors
    val savable = remember(uiState.candidates) { uiState.candidates.filter { it.isSavable } }
    val hiddenCount = uiState.candidates.size - savable.size
    var sheetExpanded by rememberSaveable { mutableStateOf(initialSheetExpanded) }
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
            )
        }
        if (savable.isEmpty()) {
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
                .weight(1f),
        ) {
            val sheetMaxHeight = maxHeight * SHEET_MAX_FRACTION
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .testTag("browser-surface"),
            ) {
                browserSurface(Modifier.fillMaxSize())
            }
            if (uiState.currentUrl == null && !uiState.isLoading) {
                BrowserEmptyHint(modifier = Modifier.align(Alignment.Center))
            }
            val showSheet = savable.isNotEmpty() && !editingAddress
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
            if (showSheet) {
                FoundMediaSheet(
                    candidates = savable,
                    hiddenCount = hiddenCount,
                    expanded = sheetExpanded,
                    onExpandedChange = { sheetExpanded = it },
                    onPreviewCandidate = onPreviewCandidate,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .heightIn(max = sheetMaxHeight),
                )
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
) {
    val colors = YftTheme.colors
    val focusManager = LocalFocusManager.current
    val focusRequester = remember { FocusRequester() }
    var focused by remember { mutableStateOf(false) }
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
                    .alpha(if (focused || display == null) 1f else 0f)
                    .focusRequester(focusRequester)
                    .onFocusChanged { state ->
                        if (state.isFocused != focused) {
                            focused = state.isFocused
                            onEditingChanged(state.isFocused)
                        }
                    }
                    .testTag("browser-address"),
                textStyle = MaterialTheme.typography.bodyLarge.copy(color = colors.textPrimary),
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
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                        ) {
                            field = field.copy(selection = TextRange(0, field.text.length))
                            focusRequester.requestFocus()
                        }
                        .clearAndSetSemantics {},
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
        Text(text = text, color = content, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun BrowserEmptyHint(modifier: Modifier = Modifier) {
    val colors = YftTheme.colors
    Column(
        modifier = modifier
            .widthIn(max = 320.dp)
            .padding(horizontal = 32.dp)
            .testTag("browser-empty"),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        YftIcon(icon = YftIcons.Globe, contentDescription = null, tint = colors.icon, size = 40.dp)
        Text(
            text = "Open a page",
            color = colors.textPrimary,
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center,
        )
        Text(
            text = "Type a web address above. Media YFT can save shows up here as the page loads.",
            color = colors.textSecondary,
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
        )
    }
}

/** The docked sheet from `02`: a peek header that expands into the savable media list. */
@Composable
private fun FoundMediaSheet(
    candidates: List<MediaCandidate>,
    hiddenCount: Int,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    onPreviewCandidate: (MediaCandidate) -> Unit,
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
                    Text(
                        text = FOUND_TITLE,
                        modifier = Modifier.semantics {
                            heading()
                            contentDescription = foundCountLabel(candidates.size)
                        },
                        color = colors.textPrimary,
                        style = MaterialTheme.typography.titleLarge,
                    )
                    YftCountBadge(
                        count = candidates.size,
                        modifier = Modifier
                            .padding(start = 10.dp)
                            .clearAndSetSemantics {},
                        minSize = 24.dp,
                    )
                    Spacer(modifier = Modifier.weight(1f))
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
                            items = candidates,
                            key = { index, candidate ->
                                "${candidate.kind}-$index-${candidate.observedAtEpochMs}"
                            },
                        ) { index, candidate ->
                            if (index > 0) {
                                YftDivider(
                                    modifier = Modifier.padding(start = FoundMediaDividerInset),
                                )
                            }
                            YftFoundMediaRow(
                                candidate = candidate,
                                onPreview = { onPreviewCandidate(candidate) },
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
private const val ADDRESS_PLACEHOLDER = "Enter a web address"
private const val SHEET_MAX_FRACTION = 0.72f
private val SHEET_SCRIM = Color.Black.copy(alpha = SHEET_SCRIM_ALPHA)
private val ADDRESS_HEIGHT = 44.dp
private val PROGRESS_HEIGHT = 2.dp
private val DRAG_THRESHOLD = 24.dp

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
