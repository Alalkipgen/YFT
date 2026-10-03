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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import androidx.navigation.compose.rememberNavController
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
import com.alal.yft.core.model.settings.HomeSites
import com.alal.yft.download.policy.TransferNetworkState
import com.alal.yft.feature.browser.BrowserScreen
import com.alal.yft.feature.browser.BrowserUiState
import com.alal.yft.feature.downloads.DownloadRowUiState
import com.alal.yft.feature.downloads.DownloadStorageSummary
import com.alal.yft.feature.downloads.DownloadsScreen
import com.alal.yft.feature.downloads.DownloadsUiState
import com.alal.yft.feature.home.HomeScreen
import com.alal.yft.feature.home.HomeUiState
import com.alal.yft.feature.library.LibraryItem
import com.alal.yft.feature.library.LibraryLocation
import com.alal.yft.feature.preview.PreviewDownloadOptions
import com.alal.yft.feature.preview.PreviewPlayerControls
import com.alal.yft.feature.preview.PreviewScreen
import com.alal.yft.feature.preview.PreviewTab
import com.alal.yft.feature.preview.PreviewUiState
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
    fun downloadAs() = render("03-download-as", ThemeMode.LIGHT) { DownloadAsPreview() }

    @Test
    fun downloadAsDark() = render("03-download-as-dark", ThemeMode.DARK) { DownloadAsPreview() }

    @Test
    fun downloads() = render("04-downloads", ThemeMode.LIGHT) { DownloadsShellPreview() }

    @Test
    fun downloadsDark() = render("04-downloads-dark", ThemeMode.DARK) { DownloadsShellPreview() }

    private fun render(name: String, themeMode: ThemeMode, content: @Composable () -> Unit) {
        composeRule.setContent { YftTheme(themeMode = themeMode) { content() } }
        composeRule.waitForIdle()
        // Drawing the window ourselves avoids waiting for a frame callback Robolectric's paused
        // looper never delivers to captureToImage.
        val view = composeRule.activity.window.decorView
        val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        composeRule.runOnUiThread { view.draw(Canvas(bitmap)) }
        val dir = requireNotNull(outputDir).apply { mkdirs() }
        File(dir, "$name.png").outputStream().use {
            bitmap.compress(Bitmap.CompressFormat.PNG, PNG_QUALITY, it)
        }
    }
}

private const val PNG_QUALITY = 100

@Composable
private fun ShellPreview(activeDownloads: Int) {
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
private fun DownloadsShellPreview() {
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
            downloadsContent = { _ ->
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
private fun PromptboxStatesPreview() {
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
private fun BrowserPreview() {
    BrowserScreen(
        uiState = BrowserUiState(
            address = "https://archive.org/details/ocean-waves",
            currentUrl = "https://archive.org/details/ocean-waves",
            pageTitle = "Ocean Waves – Public Domain Footage",
            candidates = SAMPLE_CANDIDATES,
        ),
        canGoBack = true,
        canGoForward = false,
        onAddressChanged = {},
        onGo = {},
        onBrowserBack = {},
        onBrowserForward = {},
        onReload = {},
        onStop = {},
        onPreviewCandidate = {},
        onNavigateBack = {},
        initialSheetExpanded = true,
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
private fun DownloadAsPreview() {
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

/** A painted stand-in for the design's mountain-lake still: sky, lit peaks, trees and water. */
@Composable
private fun LandscapePoster(modifier: Modifier) {
    Box(modifier = modifier.drawBehind {
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
    })
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

private fun sampleItem(id: String, name: String, mimeType: String, size: Long) = LibraryItem(
    id = id,
    displayName = name,
    uri = "content://media/external/downloads/$id",
    mimeType = mimeType,
    sizeBytes = size,
    modifiedAtEpochMs = id.toLong(),
    location = LibraryLocation.SHARED_DOWNLOADS,
)
