package com.alal.yft.design

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.alal.yft.core.model.ThemeMode
import java.io.File
import kotlin.math.roundToInt
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Renders every design screen with text at 130 % and at 200 %, the largest Android offers, as
 * `font130-<screen>.png` and `font200-<screen>.png`. Skipped unless `YFT_RENDER_DIR` is set,
 * like [DesignRenderTest].
 */
@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w360dp-h780dp-xhdpi")
class LargeTextRenderTest(private val screenName: String, private val fontScale: Float) {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val outputDir: File? = System.getenv("YFT_RENDER_DIR")?.let(::File)

    @Before
    fun onlyWhenAsked() {
        assumeTrue("Set YFT_RENDER_DIR to render design screens", outputDir != null)
    }

    @Test
    fun render() {
        val screen = DESIGN_SCREENS.single { it.name == screenName }
        composeRule.setContent {
            DesignStage(
                themeMode = ThemeMode.LIGHT,
                fontScale = fontScale,
                content = screen.content,
            )
        }
        val percent = (fontScale * PERCENT).roundToInt()
        composeRule.saveWindow(File(requireNotNull(outputDir), "font$percent-$screenName.png"))
    }

    companion object {
        private const val PERCENT = 100

        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0} at {1}x")
        fun cases(): List<Array<Any>> = DESIGN_SCREENS.flatMap { screen ->
            listOf(1.3f, 2f).map { scale -> arrayOf<Any>(screen.name, scale) }
        }
    }
}
