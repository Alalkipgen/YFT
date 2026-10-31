package com.alal.yft.feature.library

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.activity.ComponentActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import com.alal.yft.core.model.ThemeMode
import com.alal.yft.feature.downloads.DeleteFileDialog
import com.alal.yft.feature.downloads.DeleteFileQuestion
import com.alal.yft.ui.theme.YftTheme
import kotlin.math.roundToInt
import kotlin.math.sqrt
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowDialog

/**
 * N5 (v1.0.2): the delete question's **Delete** is drawn in the same red (`coralText`) on the
 * Downloads screen as in the Library; it was the link's teal on Downloads.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class DeleteColourTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private var red = Color.Unspecified
    private var teal = Color.Unspecified
    private var card = Color.Unspecified

    @Test
    fun `Downloads' Delete is the Library's red`() {
        composeRule.setContent {
            Themed {
                DeleteFileDialog(
                    question = DeleteFileQuestion("c", "Movie.mp4"),
                    onConfirm = {},
                    onCancel = {},
                )
            }
        }

        assertRed(inkOf("download-delete-file-confirm"), "Downloads' Delete")
    }

    @Test
    fun `Library's Delete is red`() {
        val lake = libraryItem("lake", "Mountain Lake.mp4")
        composeRule.setContent {
            Themed {
                CompositionLocalProvider(
                    LocalMediaDetailsSource provides FixedMediaDetails(emptyMap()),
                ) {
                    LibraryScreen(
                        uiState = LibraryUiState.Ready(
                            items = listOf(lake),
                            pendingDelete = listOf(lake),
                        ),
                        playingId = null,
                        onRefresh = {},
                        onPlay = {},
                        onOpen = {},
                        onShare = {},
                        onRequestDelete = {},
                        onConfirmDelete = {},
                        onDismissDelete = {},
                        onDismissMessage = {},
                    )
                }
            }
        }

        assertRed(inkOf("library-delete-confirm"), "Library's Delete")
    }

    @Composable
    private fun Themed(content: @Composable () -> Unit) {
        YftTheme(themeMode = ThemeMode.LIGHT) {
            red = YftTheme.colors.coralText
            teal = YftTheme.colors.link
            card = YftTheme.colors.card
            content()
        }
    }

    /** The button's ink: of its pixels in the dialog, the one farthest from the dialog's card. */
    private fun inkOf(tag: String): Int {
        composeRule.waitForIdle()
        val bounds = composeRule.onNodeWithTag(tag).fetchSemanticsNode().boundsInWindow
        val view = requireNotNull(ShadowDialog.getLatestDialog().window).decorView
        val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        composeRule.runOnUiThread { view.draw(Canvas(bitmap)) }
        val ground = card.toArgb()
        var ink = ground
        for (x in bounds.left.roundToInt() until bounds.right.roundToInt()) {
            for (y in bounds.top.roundToInt() until bounds.bottom.roundToInt()) {
                val pixel = bitmap.getPixel(x, y)
                if (distance(pixel, ground) > distance(ink, ground)) ink = pixel
            }
        }
        return ink
    }

    private fun assertRed(ink: Int, what: String) {
        val toRed = distance(ink, red.toArgb())
        val toTeal = distance(ink, teal.toArgb())
        assertTrue(
            "$what is #${Integer.toHexString(ink)}: $toRed from the red, $toTeal from the teal",
            toRed < CLOSE && toRed < toTeal,
        )
    }

    private fun distance(a: Int, b: Int): Double {
        val red = (a shr 16 and 0xFF) - (b shr 16 and 0xFF)
        val green = (a shr 8 and 0xFF) - (b shr 8 and 0xFF)
        val blue = (a and 0xFF) - (b and 0xFF)
        return sqrt((red * red + green * green + blue * blue).toDouble())
    }

    private companion object {
        /** Anti-aliased glyph edges blend with the card; the ink's core is the colour itself. */
        const val CLOSE = 24.0
    }
}
