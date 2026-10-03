package com.alal.yft.design

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.activity.ComponentActivity
import androidx.compose.runtime.Composable
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.navigation.compose.rememberNavController
import com.alal.yft.core.model.ThemeMode
import com.alal.yft.ui.YftAppShell
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
    fun shellLight() = render("shell-light", ThemeMode.LIGHT) { ShellPreview(activeDownloads = 2) }

    @Test
    fun shellDark() = render("shell-dark", ThemeMode.DARK) { ShellPreview(activeDownloads = 2) }

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
        )
    }
}
