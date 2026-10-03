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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import androidx.navigation.compose.rememberNavController
import com.alal.yft.core.model.ThemeMode
import com.alal.yft.core.model.media.CandidateSource
import com.alal.yft.core.model.media.MediaCandidate
import com.alal.yft.core.model.media.MediaKind
import com.alal.yft.core.model.settings.HomeSites
import com.alal.yft.feature.browser.BrowserScreen
import com.alal.yft.feature.browser.BrowserUiState
import com.alal.yft.feature.home.HomeScreen
import com.alal.yft.feature.home.HomeUiState
import com.alal.yft.feature.library.LibraryItem
import com.alal.yft.feature.library.LibraryLocation
import com.alal.yft.ui.YftAppShell
import com.alal.yft.ui.components.PromptboxStatus
import com.alal.yft.ui.components.YftPromptbox
import com.alal.yft.ui.navigation.YftNavHost
import com.alal.yft.ui.theme.YftTheme
import java.io.File
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
