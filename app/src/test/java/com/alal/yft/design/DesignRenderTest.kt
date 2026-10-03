package com.alal.yft.design

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.junit4.AndroidComposeTestRule
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.navigation.compose.rememberNavController
import com.alal.yft.BuildConfig
import com.alal.yft.core.download.DownloadDestinationKind
import com.alal.yft.core.download.DownloadPlanType
import com.alal.yft.core.model.ThemeMode
import com.alal.yft.core.model.download.DownloadFailureReason
import com.alal.yft.core.model.download.DownloadTaskStatus
import com.alal.yft.core.model.media.BrowserRequestContext
import com.alal.yft.core.model.media.CandidateSource
import com.alal.yft.core.model.media.MediaAsset
import com.alal.yft.core.model.media.MediaCandidate
import com.alal.yft.core.model.media.MediaKind
import com.alal.yft.core.model.media.MediaSizeAccuracy
import com.alal.yft.core.model.media.MediaTrackType
import com.alal.yft.core.model.media.MediaVariant
import com.alal.yft.core.model.settings.DownloadLocation
import com.alal.yft.core.model.settings.DownloadPreferences
import com.alal.yft.core.model.settings.HomeSites
import com.alal.yft.download.policy.TransferNetworkState
import com.alal.yft.feature.about.AboutScreen
import com.alal.yft.feature.about.LicensesScreen
import com.alal.yft.feature.browser.BrowserScreen
import com.alal.yft.feature.browser.BrowserUiState
import com.alal.yft.feature.detectedmedia.DetectedMediaScreen
import com.alal.yft.feature.detectedmedia.DetectedPage
import com.alal.yft.feature.downloads.DownloadRowUiState
import com.alal.yft.feature.downloads.DownloadStorageSummary
import com.alal.yft.feature.downloads.DownloadsScreen
import com.alal.yft.feature.downloads.DownloadsUiState
import com.alal.yft.feature.home.HomeScreen
import com.alal.yft.feature.home.HomeUiState
import com.alal.yft.feature.library.LibraryItem
import com.alal.yft.feature.library.LibraryLocation
import com.alal.yft.feature.library.LibraryScreen
import com.alal.yft.feature.library.LibraryUiState
import com.alal.yft.feature.library.LocalMediaDetailsSource
import com.alal.yft.feature.library.MediaDetails
import com.alal.yft.feature.library.MediaDetailsSource
import com.alal.yft.feature.library.MiniPlayer
import com.alal.yft.feature.library.PlaybackState
import com.alal.yft.feature.library.PlayerScreen
import com.alal.yft.feature.preview.PreviewDownloadOptions
import com.alal.yft.feature.preview.PreviewPlayerControls
import com.alal.yft.feature.preview.PreviewScreen
import com.alal.yft.feature.preview.PreviewTab
import com.alal.yft.feature.preview.PreviewUiState
import com.alal.yft.feature.settings.SettingsScreen
import com.alal.yft.feature.settings.SettingsUiState
import com.alal.yft.ui.YftAppShell
import com.alal.yft.ui.components.PromptboxStatus
import com.alal.yft.ui.components.SHEET_SCRIM_ALPHA
import com.alal.yft.ui.components.YftPromptbox
import com.alal.yft.ui.components.YftSheetHandle
import com.alal.yft.ui.navigation.YftDestination
import com.alal.yft.ui.navigation.YftNavHost
import com.alal.yft.ui.navigation.navigateToTab
import com.alal.yft.ui.theme.YftShapes
import com.alal.yft.ui.theme.YftTheme
import java.io.File
import kotlinx.coroutines.flow.first
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Renders screens at phone size for side-by-side comparison with `docs/design/reference`.
 * Skipped unless `YFT_RENDER_DIR` names an output folder, so normal test runs stay fast:
 * `YFT_RENDER_DIR=/tmp/renders ./gradlew :app:testDebugUnitTest --tests '*DesignRenderTest'`.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w360dp-h780dp-xhdpi")
class DesignRenderTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val outputDir: File? = System.getenv("YFT_RENDER_DIR")?.let(::File)

    @Before
    fun onlyWhenAsked() {
        assumeTrue("Set YFT_RENDER_DIR to render design screens", outputDir != null)
    }

    @Test
    fun homeLight() = render("01-home-light", ThemeMode.LIGHT) { ShellPreview(activeDownloads = 2) }

    @Test
    fun homeDark() = render("07-home-dark", ThemeMode.DARK) { ShellPreview(activeDownloads = 2) }

    @Test
    fun promptboxStates() = render("09-promptbox-states", ThemeMode.LIGHT) {
        PromptboxStatesPreview()
    }

    @Test
    fun browserFoundMedia() = render("02-browser-found-media", ThemeMode.LIGHT) {
        BrowserPreview()
    }

    @Test
    fun browserFoundMediaDark() = render("02-browser-found-media-dark", ThemeMode.DARK) {
        BrowserPreview()
    }

    @Test
    fun browserStart() = render("02-browser-start", ThemeMode.LIGHT) {
        BrowserPreview(startPage = true)
    }

    @Test
    fun browserStartDark() = render("02-browser-start-dark", ThemeMode.DARK) {
        BrowserPreview(startPage = true)
    }

    @Test
    fun downloadAs() = render("03-download-as", ThemeMode.LIGHT) { DownloadAsPreview() }

    @Test
    fun downloadAsDark() = render("03-download-as-dark", ThemeMode.DARK) { DownloadAsPreview() }

    @Test
    fun downloads() = render("04-downloads", ThemeMode.LIGHT) { DownloadsShellPreview() }

    @Test
    fun downloadsDark() = render("04-downloads-dark", ThemeMode.DARK) { DownloadsShellPreview() }

    @Test
    fun library() = render("05-library", ThemeMode.LIGHT) { LibraryShellPreview() }

    @Test
    fun libraryDark() = render("05-library-dark", ThemeMode.DARK) { LibraryShellPreview() }

    @Test
    fun settings() = render("06-settings", ThemeMode.LIGHT) { SettingsShellPreview() }

    @Test
    fun settingsDark() = render("06-settings-dark", ThemeMode.DARK) { SettingsShellPreview() }

    @Test
    fun foundMedia() = render("02-found-media-screen", ThemeMode.LIGHT) { FoundMediaPreview() }

    @Test
    fun foundMediaDark() = render("02-found-media-screen-dark", ThemeMode.DARK) {
        FoundMediaPreview()
    }

    @Test
    fun about() = render("10-about", ThemeMode.LIGHT) { AboutPreview() }

    @Test
    fun aboutDark() = render("10-about-dark", ThemeMode.DARK) { AboutPreview() }

    @Test
    fun licenses() = render("11-licenses", ThemeMode.LIGHT) { LicensesScreen(onNavigateBack = {}) }

    @Test
    fun licensesDark() = render("11-licenses-dark", ThemeMode.DARK) {
        LicensesScreen(onNavigateBack = {})
    }

    private fun render(name: String, themeMode: ThemeMode, content: @Composable () -> Unit) {
        composeRule.setContent { DesignStage(themeMode = themeMode, content = content) }
        composeRule.saveWindow(File(requireNotNull(outputDir), "$name.png"))
    }
}

/** Draws the test activity's window into [file] as a PNG once the content has settled. */
internal fun AndroidComposeTestRule<*, ComponentActivity>.saveWindow(file: File) {
    waitForIdle()
    // Drawing the window ourselves avoids waiting for a frame callback Robolectric's paused
    // looper never delivers to captureToImage.
    val view = activity.window.decorView
    val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
    runOnUiThread { view.draw(Canvas(bitmap)) }
    file.parentFile?.mkdirs()
    file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, PNG_QUALITY, it) }
}

private const val PNG_QUALITY = 100

/** A design screen with the sample data it is rendered and audited with. */
internal class DesignScreen(val name: String, val content: @Composable () -> Unit)

/** Every screen of the redesign, in the shell or over its page where the design puts it. */
internal val DESIGN_SCREENS: List<DesignScreen> = listOf(
    DesignScreen("01-home") { ShellPreview(activeDownloads = 2) },
    DesignScreen("09-promptbox-states") { PromptboxStatesPreview() },
    DesignScreen("02-browser-found-media") { BrowserPreview() },
    DesignScreen("02-browser-start") { BrowserPreview(startPage = true) },
    DesignScreen("02-found-media-screen") { FoundMediaPreview() },
    DesignScreen("03-download-as") { DownloadAsPreview() },
    DesignScreen("04-downloads") { DownloadsShellPreview() },
    DesignScreen("05-library") { LibraryShellPreview() },
    DesignScreen("05-player") { PlayerPreview() },
    DesignScreen("06-settings") { SettingsShellPreview() },
    DesignScreen("10-about") { AboutPreview() },
    DesignScreen("11-licenses") { LicensesScreen(onNavigateBack = {}) },
)

/** The theme, the painted stand-ins for saved files' frames and an optional text scale. */
@Composable
internal fun DesignStage(
    themeMode: ThemeMode,
    fontScale: Float = 1f,
    content: @Composable () -> Unit,
) {
    val density = LocalDensity.current
    YftTheme(themeMode = themeMode) {
        CompositionLocalProvider(
            LocalMediaDetailsSource provides SampleStills,
            LocalDensity provides Density(density.density, fontScale),
            content = content,
        )
    }
}

@Composable
internal fun ShellPreview(activeDownloads: Int) {
    val navController = rememberNavController()
    YftAppShell(navController = navController, activeDownloads = activeDownloads) {
        YftNavHost(
            navController = navController,
            themeMode = ThemeMode.SYSTEM,
            onThemeModeChanged = {},
            modifier = it,
            homeContent = { onOpenBrowser, onOpenDetectedMedia, onOpenLibrary ->
                HomeScreen(
                    state = SAMPLE_HOME,
                    onAction = {},
                    onOpenBrowser = onOpenBrowser,
                    onOpenDetectedMedia = onOpenDetectedMedia,
                    onOpenLibrary = onOpenLibrary,
                )
            },
        )
    }
}

/** The Downloads tab (`04-downloads`) in the shell, with the design's five downloads. */
@Composable
internal fun DownloadsShellPreview() {
    val navController = rememberNavController()
    YftAppShell(navController = navController, activeDownloads = 2) {
        YftNavHost(
            navController = navController,
            themeMode = ThemeMode.SYSTEM,
            onThemeModeChanged = {},
            modifier = it,
            homeContent = { onOpenBrowser, onOpenDetectedMedia, onOpenLibrary ->
                HomeScreen(
                    state = SAMPLE_HOME,
                    onAction = {},
                    onOpenBrowser = onOpenBrowser,
                    onOpenDetectedMedia = onOpenDetectedMedia,
                    onOpenLibrary = onOpenLibrary,
                )
            },
            downloadsContent = { _, _ ->
                DownloadsScreen(
                    uiState = SAMPLE_DOWNLOADS,
                    onAction = { _, _ -> },
                    onPauseAll = {},
                    todayStartEpochMs = 0,
                )
            },
        )
    }
    LaunchedEffect(navController) {
        // The shell composes the NavHost during layout, so wait for its graph first.
        navController.currentBackStackEntryFlow.first()
        navController.navigateToTab(YftDestination.DOWNLOADS)
    }
}

/** The design's Promptbox board (`09-promptbox-states`), one state under another. */
@Composable
internal fun PromptboxStatesPreview() {
    val colors = YftTheme.colors
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.background)
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        PromptboxSample("Empty", "", PromptboxStatus.Editing)
        PromptboxSample("Clipboard", "", PromptboxStatus.Editing, clipboard = true)
        PromptboxSample(
            "Typing",
            "https://archive.org/details/ocean-waves",
            PromptboxStatus.Editing,
        )
        PromptboxSample("Searching", "", PromptboxStatus.Searching)
        PromptboxSample("Found", "", PromptboxStatus.Found(count = 3))
        PromptboxSample("Error", "", PromptboxStatus.NotFound())
    }
}

@Composable
private fun PromptboxSample(
    label: String,
    text: String,
    status: PromptboxStatus,
    clipboard: Boolean = false,
) {
    Text(
        text = label,
        color = YftTheme.colors.textSecondary,
        style = MaterialTheme.typography.labelMedium,
    )
    YftPromptbox(
        text = text,
        onTextChange = {},
        status = status,
        onSubmit = {},
        onClear = {},
        showClipboardSuggestion = clipboard,
    )
}

/** `02`: a page standing in for the WebView, with the found-media sheet open. */
@Composable
internal fun BrowserPreview(startPage: Boolean = false) {
    BrowserScreen(
        uiState = if (startPage) BrowserUiState() else BrowserUiState(
            address = "https://archive.org/details/ocean-waves",
            currentUrl = "https://archive.org/details/ocean-waves",
            pageTitle = "Ocean Waves – Public Domain Footage",
            candidates = SAMPLE_CANDIDATES,
        ),
        canGoBack = !startPage,
        canGoForward = false,
        onAddressChanged = {},
        onGo = {},
        onBrowserBack = {},
        onBrowserForward = {},
        onReload = {},
        onStop = {},
        onPreviewCandidate = {},
        onNavigateBack = {},
        initialSheetExpanded = !startPage,
        copiedLinkHint = startPage,
        browserSurface = { SamplePage(it) },
    )
}

@Composable
private fun SamplePage(modifier: Modifier) {
    Column(modifier = modifier.background(Color.White)) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(220.dp)
                .background(
                    Brush.verticalGradient(listOf(Color(0xFF0B1418), Color(0xFF2E4B57))),
                ),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(CircleShape)
                    .background(Color(0x99000000)),
            )
        }
        Text(
            text = "Ocean Waves – Public Domain Footage",
            modifier = Modifier.padding(16.dp),
            color = Color(0xFF111111),
            style = MaterialTheme.typography.titleLarge,
        )
    }
}

/**
 * `03`: the page's player under the 32% scrim with the "Download as" sheet drawn over it (the
 * real sheet is its own window, which a window snapshot does not include).
 */
@Composable
internal fun DownloadAsPreview() {
    val colors = YftTheme.colors
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(300.dp),
            contentAlignment = Alignment.Center,
        ) {
            LandscapePoster(modifier = Modifier.fillMaxSize())
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(CircleShape)
                    .background(Color(0x99000000)),
            )
        }
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = SHEET_SCRIM_ALPHA)),
        )
        Surface(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth(),
            shape = YftShapes.sheet,
            color = colors.card,
            contentColor = colors.textPrimary,
        ) {
            Column {
                YftSheetHandle()
                PreviewScreen(
                    uiState = SAMPLE_DOWNLOAD_AS,
                    onNavigateBack = {},
                    onRetry = {},
                    onTabSelected = {},
                    onVariantSelected = {},
                    playerSurface = { _, modifier ->
                        Box(modifier = modifier) {
                            LandscapePoster(modifier = Modifier.fillMaxSize())
                            PreviewPlayerControls(
                                playing = false,
                                positionMs = 0,
                                durationMs = SAMPLE_DURATION_MS,
                                onPlayPause = {},
                                onSeek = {},
                                modifier = Modifier.fillMaxSize(),
                            )
                        }
                    },
                    options = PreviewDownloadOptions(wifiOnly = true),
                )
            }
        }
    }
}

/** The Library tab (`05-library`) in the shell, with Ocean Waves in the mini player. */
@Composable
internal fun LibraryShellPreview() {
    val navController = rememberNavController()
    YftAppShell(
        navController = navController,
        activeDownloads = 2,
        miniPlayer = {
            MiniPlayer(
                state = PlaybackState(
                    item = SAMPLE_LIBRARY[1],
                    positionMs = 84_000,
                    durationMs = 178_000,
                ),
                onPlayPause = {},
                onSeek = {},
                onClose = {},
            )
        },
    ) {
        YftNavHost(
            navController = navController,
            themeMode = ThemeMode.SYSTEM,
            onThemeModeChanged = {},
            modifier = it,
            homeContent = { onOpenBrowser, onOpenDetectedMedia, onOpenLibrary ->
                HomeScreen(
                    state = SAMPLE_HOME,
                    onAction = {},
                    onOpenBrowser = onOpenBrowser,
                    onOpenDetectedMedia = onOpenDetectedMedia,
                    onOpenLibrary = onOpenLibrary,
                )
            },
            libraryContent = { _ ->
                LibraryScreen(
                    uiState = LibraryUiState.Ready(items = SAMPLE_LIBRARY),
                    playingId = SAMPLE_LIBRARY[1].id,
                )
            },
        )
    }
    LaunchedEffect(navController) {
        navController.currentBackStackEntryFlow.first()
        navController.navigateToTab(YftDestination.LIBRARY)
    }
}

/** The full-screen list of media found on the page, opened from Home or the browser. */
@Composable
internal fun FoundMediaPreview() {
    DetectedMediaScreen(
        page = DetectedPage(
            pageUrl = "https://archive.org/details/ocean-waves",
            pageTitle = "Ocean Waves – Public Domain Footage",
            candidates = SAMPLE_CANDIDATES,
        ),
        onNavigateBack = {},
    )
}

/** About, opened from Settings → Version, with the release version the design shows. */
@Composable
internal fun AboutPreview() {
    AboutScreen(
        onNavigateBack = {},
        versionName = BuildConfig.VERSION_NAME.removeSuffix(DEBUG_SUFFIX),
    )
}

/** A Library video playing full screen, paused at 1:24 over its painted frame. */
@Composable
internal fun PlayerPreview() {
    PlayerScreen(
        state = PlaybackState(
            item = SAMPLE_LIBRARY[0],
            isPlaying = false,
            positionMs = 84_000,
            durationMs = SAMPLE_DURATION_MS,
        ),
        onPlayPause = {},
        onSeek = {},
        onClose = {},
        surface = { LandscapePoster(it) },
    )
}

/** The Settings tab (`06-settings`) in the shell: Wi-Fi only on, three at a time. */
@Composable
internal fun SettingsShellPreview() {
    val navController = rememberNavController()
    YftAppShell(navController = navController, activeDownloads = 2) {
        YftNavHost(
            navController = navController,
            themeMode = ThemeMode.SYSTEM,
            onThemeModeChanged = {},
            modifier = it,
            homeContent = { onOpenBrowser, onOpenDetectedMedia, onOpenLibrary ->
                HomeScreen(
                    state = SAMPLE_HOME,
                    onAction = {},
                    onOpenBrowser = onOpenBrowser,
                    onOpenDetectedMedia = onOpenDetectedMedia,
                    onOpenLibrary = onOpenLibrary,
                )
            },
            settingsContent = { _, _ ->
                SettingsScreen(
                    state = SettingsUiState(
                        download = DownloadPreferences(
                            unmeteredOnly = true,
                            maxConcurrentDownloads = 3,
                        ),
                        finishedDownloads = 6,
                    ),
                    themeMode = ThemeMode.SYSTEM,
                    sharedDownloadsSupported = true,
                    onAction = {},
                    onThemeModeChanged = {},
                    versionName = BuildConfig.VERSION_NAME.removeSuffix(DEBUG_SUFFIX),
                )
            },
        )
    }
    LaunchedEffect(navController) {
        navController.currentBackStackEntryFlow.first()
        navController.navigateToTab(YftDestination.SETTINGS)
    }
}

private const val DEBUG_SUFFIX = "-debug"

/** A painted stand-in for the design's mountain-lake still: sky, lit peaks, trees and water. */
@Composable
private fun LandscapePoster(modifier: Modifier) {
    Box(modifier = modifier.drawBehind { drawLandscape() })
}

private fun DrawScope.drawLandscape() {
    run {
        val w = size.width
        val h = size.height
        drawRect(Brush.verticalGradient(listOf(Color(0xFF7FA3C8), Color(0xFFE2CFAF))))
        val peaks = Path().apply {
            moveTo(0f, h * 0.60f)
            lineTo(0f, h * 0.20f)
            lineTo(w * 0.14f, h * 0.30f)
            lineTo(w * 0.30f, h * 0.16f)
            lineTo(w * 0.46f, h * 0.34f)
            lineTo(w * 0.62f, h * 0.20f)
            lineTo(w * 0.80f, h * 0.36f)
            lineTo(w, h * 0.30f)
            lineTo(w, h * 0.60f)
            close()
        }
        drawPath(
            path = peaks,
            brush = Brush.verticalGradient(
                colors = listOf(Color(0xFFD08B55), Color(0xFF6B6F78), Color(0xFF3C474F)),
                startY = h * 0.16f,
                endY = h * 0.60f,
            ),
        )
        drawRect(
            color = Color(0xFF1F4636),
            topLeft = Offset(0f, h * 0.54f),
            size = Size(w, h * 0.10f),
        )
        drawRect(
            brush = Brush.verticalGradient(
                colors = listOf(Color(0xFF39B3CC), Color(0xFF17677E)),
                startY = h * 0.64f,
                endY = h,
            ),
            topLeft = Offset(0f, h * 0.64f),
            size = Size(w, h * 0.36f),
        )
    }
}

/** Night skyline: dark towers with lit windows under a navy sky. */
private fun DrawScope.drawCityNight() {
    val w = size.width
    val h = size.height
    drawRect(Brush.verticalGradient(listOf(Color(0xFF0A1428), Color(0xFF1E2F52))))
    var x = 0f
    var index = 0
    while (x < w) {
        val towerWidth = w * (0.05f + (index * 37 % 5) * 0.012f)
        val top = h * (0.30f + (index * 53 % 7) * 0.06f)
        drawRect(Color(0xFF0E1726), Offset(x, top), Size(towerWidth, h - top))
        var y = top + 4f
        while (y < h - 4f) {
            var wx = x + 3f
            while (wx < x + towerWidth - 4f) {
                if (((wx + y + index).toInt() / 3) % 3 != 0) {
                    drawRect(Color(0xFFFFD36B).copy(alpha = 0.85f), Offset(wx, y), Size(2f, 2f))
                }
                wx += 5f
            }
            y += 6f
        }
        x += towerWidth + 2f
        index += 1
    }
}

/** Green aurora curtains over a dark ridge and snow. */
private fun DrawScope.drawAurora() {
    val w = size.width
    val h = size.height
    drawRect(Brush.verticalGradient(listOf(Color(0xFF041019), Color(0xFF0D2B36))))
    listOf(0.18f to 0.55f, 0.32f to 0.40f).forEach { (top, alpha) ->
        val band = Path().apply {
            moveTo(w * 0.10f, h * 0.80f)
            cubicTo(w * 0.30f, h * top, w * 0.55f, h * (top + 0.35f), w * 0.95f, h * top)
            lineTo(w * 0.95f, h * (top + 0.18f))
            cubicTo(w * 0.60f, h * (top + 0.50f), w * 0.35f, h * (top + 0.20f), w * 0.18f, h)
            close()
        }
        drawPath(
            path = band,
            brush = Brush.verticalGradient(
                listOf(Color(0xFF7CFFC4).copy(alpha = alpha), Color(0xFF16C784).copy(alpha = 0f)),
                startY = h * top,
                endY = h * 0.9f,
            ),
        )
    }
    val ridge = Path().apply {
        moveTo(0f, h)
        lineTo(0f, h * 0.78f)
        lineTo(w * 0.25f, h * 0.70f)
        lineTo(w * 0.45f, h * 0.80f)
        lineTo(w * 0.70f, h * 0.72f)
        lineTo(w, h * 0.82f)
        lineTo(w, h)
        close()
    }
    drawPath(ridge, Color(0xFF0A1C24))
    drawRect(Color(0xFF2F4B57), Offset(0f, h * 0.90f), Size(w, h * 0.10f))
}

/** A straight desert road running to the horizon under a pale sky. */
private fun DrawScope.drawDesertRoad() {
    val w = size.width
    val h = size.height
    drawRect(Brush.verticalGradient(listOf(Color(0xFF5E9BD6), Color(0xFFDDEBF6))))
    drawRect(Color(0xFFB9825E), Offset(0f, h * 0.36f), Size(w, h * 0.06f))
    drawRect(
        brush = Brush.verticalGradient(
            listOf(Color(0xFFE2B07A), Color(0xFFC98A52)),
            startY = h * 0.42f,
            endY = h,
        ),
        topLeft = Offset(0f, h * 0.42f),
        size = Size(w, h * 0.58f),
    )
    val road = Path().apply {
        moveTo(w * 0.47f, h * 0.42f)
        lineTo(w * 0.53f, h * 0.42f)
        lineTo(w * 0.95f, h)
        lineTo(w * 0.15f, h)
        close()
    }
    drawPath(road, Color(0xFF3B3B3F))
    drawLine(
        color = Color(0xFFF2C14E),
        start = Offset(w * 0.50f, h * 0.44f),
        end = Offset(w * 0.55f, h),
        strokeWidth = 3f,
    )
}

/** A painted still as a file's frame would be, 320 x 200 like a scaled-down video picture. */
private fun paintedStill(scene: DrawScope.() -> Unit): ImageBitmap {
    val image = ImageBitmap(STILL_WIDTH, STILL_HEIGHT)
    CanvasDrawScope().draw(
        density = Density(1f),
        layoutDirection = LayoutDirection.Ltr,
        canvas = androidx.compose.ui.graphics.Canvas(image),
        size = Size(STILL_WIDTH.toFloat(), STILL_HEIGHT.toFloat()),
    ) { scene() }
    return image
}

private const val STILL_WIDTH = 320
private const val STILL_HEIGHT = 200

/** What the sample files would say about themselves: the design's lengths, sizes and stills. */
internal object SampleStills : MediaDetailsSource {
    private val details: Map<String, MediaDetails> by lazy {
        mapOf(
            sampleUri("1") to MediaDetails(252_000, 1_280, 720, paintedStill { drawLandscape() }),
            sampleUri("2") to MediaDetails(durationMs = 178_000),
            sampleUri("3") to MediaDetails(365_000, 1_920, 1_080, paintedStill { drawCityNight() }),
            sampleUri("4") to MediaDetails(durationMs = 600_000),
            sampleUri("5") to MediaDetails(210_000, 3_840, 2_160, paintedStill { drawAurora() }),
            sampleUri("6") to MediaDetails(312_000, 1_280, 720, paintedStill { drawDesertRoad() }),
        )
    }

    override fun cached(uri: String): MediaDetails? = details[uri]

    override suspend fun load(uri: String, isAudio: Boolean): MediaDetails =
        details[uri] ?: MediaDetails.Unknown
}

private const val SAMPLE_DURATION_MS = 252_000L
private const val SAMPLE_PAGE = "https://archive.org/details/mountain-lake-4k"

private val SAMPLE_DOWNLOAD_AS = PreviewUiState.Ready(
    asset = MediaAsset(
        sourcePageUrl = SAMPLE_PAGE,
        title = "Mountain Lake 4K",
        thumbnailUrl = null,
        durationMillis = SAMPLE_DURATION_MS,
        variants = listOf(
            sampleVariant("1080", width = 1_920, height = 1_080, mebibytes = 186),
            sampleVariant("720", width = 1_280, height = 720, mebibytes = 96),
            sampleVariant("480", width = 854, height = 480, mebibytes = 54),
            sampleVariant("360", width = 640, height = 360, mebibytes = 31),
            MediaVariant(
                id = "audio",
                playbackUrl = "https://ia800.us.archive.org/mountain-lake/audio.m4a",
                kind = MediaKind.DIRECT,
                trackType = MediaTrackType.AUDIO,
                requestContext = BrowserRequestContext(SAMPLE_PAGE, "fixture-agent", null),
                label = "English",
                bitrateBitsPerSecond = 128_000,
                language = "en",
            ),
        ),
        resolvedAtEpochMs = 1,
    ),
    selectedTab = PreviewTab.VIDEO,
    selectedVariantId = "720",
)

private fun sampleVariant(id: String, width: Int, height: Int, mebibytes: Long) = MediaVariant(
    id = id,
    playbackUrl = "https://ia800.us.archive.org/mountain-lake/$id.mp4",
    kind = MediaKind.DIRECT,
    trackType = MediaTrackType.AUDIO_VIDEO,
    requestContext = BrowserRequestContext(SAMPLE_PAGE, "fixture-agent", null),
    mimeType = "video/mp4",
    container = "MP4",
    codecs = listOf("avc1.640028", "mp4a.40.2"),
    width = width,
    height = height,
    framesPerSecond = 30.0,
    durationMillis = SAMPLE_DURATION_MS,
    sizeBytes = mebibytes * 1_024 * 1_024,
    sizeAccuracy = MediaSizeAccuracy.EXACT,
)

private val SAMPLE_CANDIDATES = listOf(
    sampleCandidate(1, "Ocean Waves", "ocean-waves.mp4", MediaKind.DIRECT, "video/mp4")
        .copy(contentLengthBytes = 186L * 1_024 * 1_024),
    sampleCandidate(
        2,
        "Ocean Waves (stream)",
        "master.m3u8",
        MediaKind.HLS,
        "application/vnd.apple.mpegurl",
    ),
    sampleCandidate(3, "Ocean Waves – audio", "ocean-waves.m4a", MediaKind.DIRECT, "audio/mp4"),
)

private fun sampleCandidate(
    index: Int,
    title: String,
    file: String,
    kind: MediaKind,
    mimeType: String,
) = MediaCandidate(
    pageUrl = "https://archive.org/details/ocean-waves",
    mediaUrl = "https://ia800.us.archive.org/ocean-waves/$file",
    sources = setOf(CandidateSource.DOM),
    kind = kind,
    mimeType = mimeType,
    title = title,
    observedAtEpochMs = index.toLong(),
)

private const val MIB = 1_048_576L

private val SAMPLE_DOWNLOADS = DownloadsUiState(
    rows = listOf(
        sampleDownload(
            id = "lake",
            name = "Mountain Lake 4K.mp4",
            status = DownloadTaskStatus.RUNNING,
            downloaded = 64_382_566,
            total = 96 * MIB,
            percent = 64,
            bytesPerSecond = 2_516_582,
        ),
        sampleDownload(
            id = "city",
            name = "City Lights Timelapse.mp4",
            status = DownloadTaskStatus.RUNNING,
            downloaded = 46 * MIB,
            total = 210 * MIB,
            percent = 22,
            planType = DownloadPlanType.HLS,
            bytesPerSecond = 1_887_437,
        ),
        sampleDownload(
            id = "rain",
            name = "Forest Rain Sounds.m4a",
            status = DownloadTaskStatus.WAITING_FOR_NETWORK,
            downloaded = 0,
            total = 12 * MIB,
        ),
        sampleDownload(
            id = "desert",
            name = "Desert Drive.mp4",
            status = DownloadTaskStatus.FAILED,
            downloaded = 30 * MIB,
            total = 140 * MIB,
            failure = DownloadFailureReason.EXPIRED_URL,
        ),
        sampleDownload(
            id = "waves",
            name = "Ocean Waves.m4a",
            status = DownloadTaskStatus.COMPLETED,
            downloaded = 7 * MIB,
            total = 7 * MIB,
            percent = 100,
        ),
    ),
    network = TransferNetworkState.WAITING_FOR_UNMETERED,
    storage = DownloadStorageSummary(DownloadLocation.SHARED_DOWNLOADS, freeBytes = 19_541_180_006),
)

private fun sampleDownload(
    id: String,
    name: String,
    status: DownloadTaskStatus,
    downloaded: Long,
    total: Long,
    percent: Int = (downloaded * 100 / total).toInt(),
    planType: DownloadPlanType = DownloadPlanType.DIRECT,
    bytesPerSecond: Long? = null,
    failure: DownloadFailureReason? = null,
) = DownloadRowUiState(
    id = id,
    displayName = name,
    status = status,
    planType = planType,
    destinationKind = DownloadDestinationKind.MEDIA_STORE,
    downloadedBytes = downloaded,
    totalBytes = total,
    progressPercent = percent,
    requiresLinkRefresh = false,
    failureReason = failure,
    updatedAtEpochMs = 1,
    bytesPerSecond = bytesPerSecond,
)

private val SAMPLE_HOME = HomeUiState(
    sites = HomeSites.DEFAULTS,
    recent = listOf(
        sampleItem("1", "Mountain Lake 4K.mp4", "video/mp4", 96L * 1_024 * 1_024),
        sampleItem("2", "Ocean Waves.m4a", "audio/mp4", 7L * 1_024 * 1_024),
    ),
)

private val SAMPLE_LIBRARY = listOf(
    sampleItem("1", "Mountain Lake 4K.mp4", "video/mp4", 96L * 1_024 * 1_024),
    sampleItem("2", "Ocean Waves.m4a", "audio/mp4", 7L * 1_024 * 1_024),
    sampleItem("3", "City Lights.mp4", "video/mp4", 186L * 1_024 * 1_024),
    sampleItem("4", "Forest Rain.m4a", "audio/mp4", 12L * 1_024 * 1_024),
    sampleItem("5", "Northern Lights.mp4", "video/mp4", 412L * 1_024 * 1_024),
    sampleItem("6", "Desert Drive.mp4", "video/mp4", 88L * 1_024 * 1_024),
).mapIndexed { index, item -> item.copy(modifiedAtEpochMs = 100L - index) }

private fun sampleUri(id: String) = "content://media/external/downloads/$id"

private fun sampleItem(id: String, name: String, mimeType: String, size: Long) = LibraryItem(
    id = id,
    displayName = name,
    uri = sampleUri(id),
    mimeType = mimeType,
    sizeBytes = size,
    modifiedAtEpochMs = id.toLong(),
    location = LibraryLocation.SHARED_DOWNLOADS,
)
