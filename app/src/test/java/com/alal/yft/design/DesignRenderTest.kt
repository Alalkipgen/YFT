package com.alal.yft.design

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import androidx.navigation.compose.rememberNavController
import com.alal.yft.core.model.ThemeMode
import com.alal.yft.core.model.settings.HomeSites
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
