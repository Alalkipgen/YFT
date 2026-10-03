package com.alal.yft.design

import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.activity.ComponentActivity
import androidx.compose.runtime.Composable
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performScrollTo
import com.alal.yft.core.model.ThemeMode
import com.alal.yft.feature.about.AboutScreen
import com.alal.yft.feature.about.CrashReportDialog
import com.alal.yft.feature.home.HomeScreen
import com.alal.yft.feature.home.HomeUiState
import com.alal.yft.ui.components.PromptboxStatus
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowDialog

/** Native-canvas previews; a dialog is captured from its own window, not the activity. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w360dp-h780dp-xhdpi")
class DiagnosticsRenderTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()
    private val outputDir = System.getenv("YFT_RENDER_DIR")?.let(::File)

    @Before
    fun onlyWhenAsked() {
        assumeTrue("Set YFT_RENDER_DIR to render diagnostics", outputDir != null)
    }

    @Test
    fun aboutLight() = render("12-about-report", ThemeMode.LIGHT) { About() }

    @Test
    fun aboutDark() = render("12-about-report-dark", ThemeMode.DARK) { About() }

    @Test
    fun aboutLarge() = render("font200-12-about-report", ThemeMode.LIGHT, 2f) { About() }

    @Test
    fun aboutLargeActions() = render(
        "font200-12-about-report-actions", ThemeMode.LIGHT, 2f, scrollToReport = true,
    ) { About() }

    @Test
    fun dialogLight() = render("12-report-dialog", ThemeMode.LIGHT, dialog = true) {
        CrashReportDialog(REPORT, onDismiss = {})
    }

    @Test
    fun dialogDarkLarge() = render(
        "font200-12-report-dialog-dark", ThemeMode.DARK, 2f, dialog = true,
    ) {
        CrashReportDialog(REPORT, onDismiss = {})
    }

    @Test
    fun homeLight() = render("12-home-details", ThemeMode.LIGHT) { Home() }

    @Test
    fun homeDark() = render("12-home-details-dark", ThemeMode.DARK) { Home() }

    @Test
    fun homeLarge() = render("font200-12-home-details", ThemeMode.LIGHT, 2f) { Home() }

    private fun render(
        name: String,
        mode: ThemeMode,
        scale: Float = 1f,
        dialog: Boolean = false,
        scrollToReport: Boolean = false,
        content: @Composable () -> Unit,
    ) {
        // Dialogs own another AndroidComposeView and do not inherit DesignStage's density.
        // Set the real resource configuration too, so their "200%" capture is not actually 100%.
        composeRule.runOnUiThread {
            val resources = composeRule.activity.resources
            val configuration = Configuration(resources.configuration).apply { fontScale = scale }
            @Suppress("DEPRECATION")
            resources.updateConfiguration(configuration, resources.displayMetrics)
        }
        composeRule.setContent { DesignStage(mode, scale, content) }
        if (scrollToReport) {
            composeRule.onNodeWithTag("about-crash-report").performScrollTo()
        }
        val file = File(requireNotNull(outputDir), "$name.png")
        if (!dialog) {
            composeRule.saveWindow(file)
        } else {
            composeRule.waitForIdle()
            val view = requireNotNull(ShadowDialog.getLatestDialog().window).decorView
            assertEquals(scale, view.resources.configuration.fontScale, 0.001f)
            val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
            composeRule.runOnUiThread { view.draw(Canvas(bitmap)) }
            file.parentFile?.mkdirs()
            file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
    }

    @Composable
    private fun About() {
        AboutScreen(onNavigateBack = {}, crashReport = REPORT)
    }

    @Composable
    private fun Home() {
        HomeScreen(
            state = HomeUiState(
                link = "https://a.test/watch",
                status = PromptboxStatus.NotFound("The site did not return the page."),
                failureDetails = listOf("adapter fixture: HTTP_STATUS", "page GET 403"),
            ),
            onAction = {},
            onOpenBrowser = {},
            onOpenDetectedMedia = {},
            onOpenLibrary = {},
            copiedLinkHint = false,
        )
    }

    private companion object {
        val REPORT = """
            YFT local crash report
            UTC: 2026-10-03T00:00:00.000Z
            Version: 1.0.0-beta.2-debug (2)
            Android SDK: 35
            Device: Test maker Test model
            Thread: main

            java.lang.IllegalStateException: sample local crash
                at com.alal.yft.Fixture.run(Fixture.kt:12)
            Caused by: java.io.IOException: sample failure
                at com.alal.yft.Fixture.read(Fixture.kt:24)
        """.trimIndent()
    }
}
