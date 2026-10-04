package com.alal.yft.feature.home

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogProperties
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.alal.yft.R
import com.alal.yft.core.model.settings.HomeSite
import com.alal.yft.core.model.settings.SiteBrand
import com.alal.yft.feature.library.LibraryItem
import com.alal.yft.feature.library.libraryMeta
import com.alal.yft.feature.library.rememberMediaDetails
import com.alal.yft.ui.components.PromptboxStatus
import com.alal.yft.ui.components.YftCard
import com.alal.yft.ui.components.YftIcon
import com.alal.yft.ui.components.YftMediaKind
import com.alal.yft.ui.components.YftOutlinedChip
import com.alal.yft.ui.components.YftPromptbox
import com.alal.yft.ui.components.YftSectionHeader
import com.alal.yft.ui.components.YftTextButton
import com.alal.yft.ui.components.YftThumbnail
import com.alal.yft.ui.components.YftTonalButton
import com.alal.yft.ui.components.logoRes
import com.alal.yft.ui.components.siteLogoTint
import com.alal.yft.ui.format.YftFormat
import com.alal.yft.ui.theme.YftIcons
import com.alal.yft.ui.theme.YftShapes
import com.alal.yft.ui.theme.YftTheme

@Composable
fun HomeRoute(
    onOpenBrowser: (link: String?) -> Unit,
    onOpenDetectedMedia: () -> Unit,
    onOpenLibrary: () -> Unit,
    modifier: Modifier = Modifier,
    onOpenQuickDownload: () -> Unit = {},
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val openQuickDownload by rememberUpdatedState(onOpenQuickDownload)
    LaunchedEffect(viewModel) {
        viewModel.quickDownloadRequests.collect { openQuickDownload() }
    }
    // Recent follows the library: refreshed each time Home comes back into view.
    LifecycleResumeEffect(viewModel) {
        viewModel.onAction(HomeAction.RefreshRecent)
        onPauseOrDispose {}
    }
    HomeScreen(
        state = state,
        onAction = viewModel::onAction,
        onOpenBrowser = onOpenBrowser,
        // One video found: View reopens "Video you copied"; several keep the Found list.
        onOpenDetectedMedia = if (state.quickDownload) onOpenQuickDownload else onOpenDetectedMedia,
        onOpenLibrary = onOpenLibrary,
        copiedLinkHint = rememberCopiedLinkHint(),
        modifier = modifier,
    )
}

/**
 * Home from the design (`01-home-light`, `07-home-dark`): the YFT header, the Promptbox card
 * with Paste and Open browser, "Your sites" and the two newest downloads.
 *
 * The clipboard is read only inside the Paste and Use click handlers; [copiedLinkHint] comes
 * from the clip's description alone.
 */
@Composable
fun HomeScreen(
    state: HomeUiState,
    onAction: (HomeAction) -> Unit,
    onOpenBrowser: (link: String?) -> Unit,
    onOpenDetectedMedia: () -> Unit,
    onOpenLibrary: () -> Unit,
    modifier: Modifier = Modifier,
    copiedLinkHint: Boolean = false,
) {
    val colors = YftTheme.colors
    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(colors.background)
            .windowInsetsPadding(WindowInsets.statusBars)
            .testTag("home-list"),
        contentPadding = PaddingValues(start = 20.dp, top = 12.dp, end = 20.dp, bottom = 16.dp),
    ) {
        item(key = "header") { HomeHeader() }
        item(key = "intro") { Intro(modifier = Modifier.padding(top = 18.dp)) }
        item(key = "link") {
            LinkCard(
                state = state,
                onAction = onAction,
                onOpenBrowser = onOpenBrowser,
                onOpenDetectedMedia = onOpenDetectedMedia,
                copiedLinkHint = copiedLinkHint,
                modifier = Modifier.padding(top = 22.dp),
            )
        }
        item(key = "sites") {
            SitesSection(
                sites = state.sites,
                editing = state.editingSites,
                onAction = onAction,
                onOpenSite = { onOpenBrowser(it.url) },
                modifier = Modifier.padding(top = 10.dp),
            )
        }
        item(key = "recent") {
            RecentSection(
                items = state.recent,
                onOpenLibrary = onOpenLibrary,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
    }

    state.siteDialog?.let { dialog -> AddSiteDialog(dialog = dialog, onAction = onAction) }
}

@Composable
private fun HomeHeader() {
    val colors = YftTheme.colors
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Image(
            painter = painterResource(R.drawable.yft_logo),
            contentDescription = null,
            modifier = Modifier.size(36.dp),
        )
        Text(
            text = "YFT",
            modifier = Modifier
                .padding(start = 10.dp)
                .weight(1f)
                .semantics { heading() },
            color = colors.textPrimary,
            style = MaterialTheme.typography.headlineMedium.copy(fontSize = 28.sp),
        )
        YftOutlinedChip(
            text = "No ads",
            icon = YftIcons.Shield,
            modifier = Modifier.testTag("home-no-ads"),
        )
    }
}

@Composable
private fun Intro(modifier: Modifier = Modifier) {
    val colors = YftTheme.colors
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            text = "Download from any link",
            modifier = Modifier.semantics { heading() },
            color = colors.textPrimary,
            style = MaterialTheme.typography.headlineMedium,
        )
        Text(
            text = "Paste a link or open a site. YFT finds the media you can save.",
            color = colors.textSecondary,
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

@Composable
@OptIn(ExperimentalLayoutApi::class)
private fun LinkCard(
    state: HomeUiState,
    onAction: (HomeAction) -> Unit,
    onOpenBrowser: (link: String?) -> Unit,
    onOpenDetectedMedia: () -> Unit,
    copiedLinkHint: Boolean,
    modifier: Modifier = Modifier,
) {
    val clipboard = LocalClipboardManager.current
    val focusManager = LocalFocusManager.current
    YftCard(
        modifier = modifier.fillMaxWidth(),
        shape = YftShapes.cardLarge,
        contentPadding = PaddingValues(start = 14.dp, top = 14.dp, end = 14.dp, bottom = 8.dp),
    ) {
        YftPromptbox(
            text = state.link,
            onTextChange = { onAction(HomeAction.LinkChanged(it)) },
            status = state.status,
            onSubmit = {
                focusManager.clearFocus()
                onAction(HomeAction.Submit)
            },
            onClear = { onAction(HomeAction.ClearLink) },
            showClipboardSuggestion = copiedLinkHint && state.link.isEmpty(),
            onUseClipboard = {
                focusManager.clearFocus()
                onAction(HomeAction.UseCopied(clipboard.getText()?.text))
            },
            onView = onOpenDetectedMedia,
            onOpenInBrowser = { onOpenBrowser(state.link.trim()) },
            onCancelSearch = { onAction(HomeAction.CancelSearch) },
            onEdit = { onAction(HomeAction.EditLink) },
        )
        if (state.status is PromptboxStatus.NotFound) {
            YftTonalButton(
                text = "Copy details",
                icon = YftIcons.Document,
                onClick = {
                    clipboard.setText(
                        androidx.compose.ui.text.AnnotatedString(state.lookupDetailsText()),
                    )
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 6.dp)
                    .testTag("home-copy-details"),
            )
        }
        FlowRow(
            modifier = Modifier.padding(top = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            YftTonalButton(
                text = "Paste",
                icon = YftIcons.Paste,
                onClick = { onAction(HomeAction.Pasted(clipboard.getText()?.text)) },
                modifier = Modifier.testTag("home-paste"),
                compact = true,
            )
            YftTonalButton(
                text = "Open browser",
                icon = YftIcons.Globe,
                compact = true,
                onClick = {
                    val typed = state.link.trim().takeIf { it.isNotEmpty() }
                    onOpenBrowser(typed.takeIf { state.status != PromptboxStatus.Searching })
                },
                modifier = Modifier.testTag("home-open-browser"),
            )
        }
    }
}

@Composable
private fun SitesSection(
    sites: List<HomeSite>,
    editing: Boolean,
    onAction: (HomeAction) -> Unit,
    onOpenSite: (HomeSite) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        YftSectionHeader(
            title = "Your sites",
            actionLabel = if (editing) "Done" else "Edit",
            onAction = { onAction(HomeAction.ToggleEditSites) }
                .takeIf { sites.isNotEmpty() || editing },
            actionTestTag = "home-sites-edit",
        )
        // Four tiles fill the row as in the design; more scroll sideways.
        BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
            val gap = ((maxWidth - SITE_TILE_WIDTH * VISIBLE_TILES) / (VISIBLE_TILES - 1))
                .coerceAtLeast(8.dp)
            LazyRow(
                modifier = Modifier.fillMaxWidth().testTag("home-sites"),
                horizontalArrangement = Arrangement.spacedBy(gap),
            ) {
                items(sites, key = { it.url }) { site ->
                    SiteTile(
                        site = site,
                        editing = editing,
                        onOpen = { onOpenSite(site) },
                        onRemove = { onAction(HomeAction.RemoveSite(site)) },
                    )
                }
                if (!editing) {
                    item(key = "add") { AddSiteTile(onClick = { onAction(HomeAction.AddSite) }) }
                }
            }
        }
    }
}

@Composable
internal fun SiteTile(
    site: HomeSite,
    editing: Boolean,
    onOpen: () -> Unit,
    onRemove: () -> Unit,
    tag: String = "home-site-${site.url}",
) {
    val colors = YftTheme.colors
    Column(
        modifier = Modifier
            .width(SITE_TILE_WIDTH)
            .clip(YftShapes.thumbnail)
            .clickable(
                role = Role.Button,
                onClickLabel = if (editing) "Remove" else "Open",
                onClick = if (editing) onRemove else onOpen,
            )
            .semantics {
                contentDescription = if (editing) "Remove ${site.name}" else site.name
            }
            .testTag(tag),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(modifier = Modifier.size(SITE_CIRCLE)) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(CircleShape)
                    .background(colors.accentSoft),
                contentAlignment = Alignment.Center,
            ) {
                val brand = remember(site.url) { SiteBrand.of(site.url) }
                if (brand != null) {
                    // Bundled logo; the tile's description already names the site.
                    YftIcon(
                        icon = brand.logoRes(),
                        contentDescription = null,
                        tint = siteLogoTint(brand, colors),
                        size = SITE_LOGO,
                        modifier = Modifier.testTag("site-logo-${brand.slug}"),
                    )
                } else {
                    Text(
                        text = site.initial,
                        // Night uses a paler Mint for the letter, as in `07-home-dark`.
                        color = if (colors.isDark) {
                            lerp(colors.accent, colors.textPrimary, NIGHT_LETTER_LIGHTEN)
                        } else {
                            colors.link
                        },
                        style = MaterialTheme.typography.headlineMedium.copy(
                            fontWeight = FontWeight.Medium,
                        ),
                    )
                }
            }
            if (editing) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .size(24.dp)
                        .clip(CircleShape)
                        .background(colors.textPrimary),
                    contentAlignment = Alignment.Center,
                ) {
                    YftIcon(
                        icon = YftIcons.Close,
                        contentDescription = null,
                        tint = colors.background,
                        size = 16.dp,
                    )
                }
            }
        }
        SiteLabel(text = site.name)
    }
}

@Composable
private fun AddSiteTile(onClick: () -> Unit) {
    val colors = YftTheme.colors
    Column(
        modifier = Modifier
            .width(SITE_TILE_WIDTH)
            .clip(YftShapes.thumbnail)
            .clickable(role = Role.Button, onClick = onClick)
            .testTag("home-site-add"),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        val outline = MaterialTheme.colorScheme.outline
        Box(
            modifier = Modifier
                .size(SITE_CIRCLE)
                .drawBehind {
                    val stroke = 1.5.dp.toPx()
                    drawRoundRect(
                        color = outline,
                        topLeft = Offset(stroke / 2, stroke / 2),
                        size = Size(size.width - stroke, size.height - stroke),
                        cornerRadius = CornerRadius(size.width / 2),
                        style = Stroke(
                            width = stroke,
                            pathEffect = PathEffect.dashPathEffect(
                                floatArrayOf(5.dp.toPx(), 4.dp.toPx()),
                            ),
                        ),
                    )
                },
            contentAlignment = Alignment.Center,
        ) {
            YftIcon(icon = YftIcons.Add, contentDescription = null, tint = colors.textPrimary)
        }
        SiteLabel(text = "Add")
    }
}

@Composable
private fun SiteLabel(text: String) {
    Text(
        text = text,
        modifier = Modifier.padding(top = 6.dp, bottom = 2.dp),
        color = YftTheme.colors.textPrimary,
        style = MaterialTheme.typography.bodyMedium,
        textAlign = TextAlign.Center,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}

@Composable
private fun RecentSection(
    items: List<LibraryItem>,
    onOpenLibrary: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        YftSectionHeader(
            title = "Recent",
            actionLabel = "See all",
            onAction = onOpenLibrary,
            actionTestTag = "home-recent-all",
        )
        if (items.isEmpty()) {
            Text(
                text = "Finished downloads show up here.",
                modifier = Modifier
                    .padding(top = 4.dp)
                    .testTag("home-recent-empty"),
                color = YftTheme.colors.textSecondary,
                style = MaterialTheme.typography.bodyMedium,
            )
        } else {
            Row(
                modifier = Modifier.padding(top = 2.dp),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                items.take(HomeViewModel.RECENT_COUNT).forEach { item ->
                    RecentCard(
                        item = item,
                        onClick = onOpenLibrary,
                        modifier = Modifier.weight(1f),
                    )
                }
                val shown = items.size.coerceAtMost(HomeViewModel.RECENT_COUNT)
                repeat(HomeViewModel.RECENT_COUNT - shown) {
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

/** One of the two newest saved files with its own frame or cover, length and "720p · 96 MB". */
@Composable
private fun RecentCard(item: LibraryItem, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val colors = YftTheme.colors
    val details = rememberMediaDetails(item.uri, item.isAudio)
    Column(
        modifier = modifier
            .clip(YftShapes.thumbnail)
            .clickable(onClick = onClick)
            .testTag("home-recent-${item.id}"),
    ) {
        YftThumbnail(
            image = details?.image,
            kind = if (item.isAudio) YftMediaKind.Audio else YftMediaKind.Video,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(THUMBNAIL_RATIO),
            durationLabel = details?.durationMs?.let(YftFormat::duration),
        )
        Text(
            text = YftFormat.title(item.displayName),
            modifier = Modifier.padding(top = 8.dp),
            color = colors.textPrimary,
            style = MaterialTheme.typography.titleMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = libraryMeta(item, details),
            modifier = Modifier
                .padding(top = 2.dp)
                .testTag("home-recent-meta-${item.id}"),
            color = colors.textSecondary,
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 1,
        )
    }
}

@Composable
private fun AddSiteDialog(dialog: SiteDialogState, onAction: (HomeAction) -> Unit) {
    AlertDialog(
        onDismissRequest = { onAction(HomeAction.DismissSiteDialog) },
        // A width-wrapping dialog window is first measured at the platform's preferred dialog
        // width; with text fields inside, that kept the window re-measuring every frame on a
        // 360dp screen in tests. A full-width window measures once; the padding keeps the
        // usual dialog margins.
        modifier = Modifier.padding(horizontal = 24.dp),
        properties = DialogProperties(usePlatformDefaultWidth = false),
        title = { Text(text = "Add a site") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = dialog.name,
                    onValueChange = { onAction(HomeAction.SiteNameChanged(it)) },
                    label = { Text("Name") },
                    singleLine = true,
                    isError = dialog.nameError != null,
                    supportingText = dialog.nameError?.let { error -> { Text(error) } },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                    modifier = Modifier.fillMaxWidth().testTag("home-site-name"),
                )
                OutlinedTextField(
                    value = dialog.address,
                    onValueChange = { onAction(HomeAction.SiteAddressChanged(it)) },
                    label = { Text("Address") },
                    placeholder = { Text("https://example.com") },
                    singleLine = true,
                    isError = dialog.addressError != null,
                    supportingText = dialog.addressError?.let { error -> { Text(error) } },
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Uri,
                        imeAction = ImeAction.Done,
                    ),
                    modifier = Modifier.fillMaxWidth().testTag("home-site-address"),
                )
            }
        },
        confirmButton = {
            YftTextButton(
                text = "Add",
                onClick = { onAction(HomeAction.SaveSite) },
                modifier = Modifier.testTag("home-site-save"),
            )
        },
        dismissButton = {
            YftTextButton(
                text = "Cancel",
                onClick = { onAction(HomeAction.DismissSiteDialog) },
                modifier = Modifier.testTag("home-site-cancel"),
            )
        },
        containerColor = YftTheme.colors.card,
    )
}

private val SITE_TILE_WIDTH = 72.dp
private val SITE_CIRCLE = 64.dp
private val SITE_LOGO = 30.dp
private const val NIGHT_LETTER_LIGHTEN = 0.6f
private const val VISIBLE_TILES = 4
private const val THUMBNAIL_RATIO = 16f / 9f
