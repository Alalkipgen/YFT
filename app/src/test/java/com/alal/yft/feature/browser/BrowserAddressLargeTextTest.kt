package com.alal.yft.feature.browser

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.dp
import com.alal.yft.core.model.ThemeMode
import com.alal.yft.ui.theme.YftTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * v1.0.2 fix R4: on the owner's phone, at a large text size, the address pill's host and path
 * were drawn on top of other text. With native graphics on a phone-sized screen at 100, 130, 150
 * and 200 % text: the styled address is the only text drawn in the pill (two addresses that look
 * the same there, one with "www." and one with a query, draw the same pixels although the field
 * underneath holds different text), and it is one line that ends in "…".
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w392dp-h872dp-xxhdpi")
class BrowserAddressLargeTextTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    @Config(fontScale = 1.0f)
    fun `at the default text size the address is one line and the only text`() =
        assertOneAddress(1.0f)

    @Test
    @Config(fontScale = 1.3f)
    fun `at 130 percent text size the address is one line and the only text`() =
        assertOneAddress(1.3f)

    @Test
    @Config(fontScale = 1.5f)
    fun `at 150 percent text size the address is one line and the only text`() =
        assertOneAddress(1.5f)

    @Test
    @Config(fontScale = 2.0f)
    fun `at 200 percent text size the address is one line and the only text`() =
        assertOneAddress(2.0f)

    private fun assertOneAddress(fontScale: Float) {
        var address by mutableStateOf(PAGE)
        var referenceWidth by mutableStateOf(0.dp)
        var layout: TextLayoutResult? = null
        composeRule.setContent {
            YftTheme(themeMode = ThemeMode.LIGHT) {
                Column(modifier = Modifier.fillMaxSize()) {
                    Box(modifier = Modifier.weight(1f)) {
                        BrowserScreen(
                            uiState = BrowserUiState(address = address, currentUrl = address),
                            canGoBack = false,
                            canGoForward = false,
                            onAddressChanged = {},
                            onGo = {},
                            onBrowserBack = {},
                            onBrowserForward = {},
                            onReload = {},
                            onStop = {},
                            onDownloadGroup = {},
                            onNavigateBack = {},
                            browserSurface = { Box(modifier = it.testTag("test-page")) },
                        )
                    }
                    // The pill's styled address alone, as wide as the field, for its layout.
                    if (referenceWidth > 0.dp) {
                        Box(modifier = Modifier.width(referenceWidth)) {
                            AddressDisplayText(
                                display = checkNotNull(addressDisplay(PAGE)),
                                onClick = {},
                                onTextLayout = { layout = it },
                            )
                        }
                    }
                }
            }
        }
        assertEquals(fontScale, composeRule.density.fontScale, 0.001f)
        val field = composeRule.onNodeWithTag("browser-address").fetchSemanticsNode().boundsInWindow
        val first = pixels(field)

        address = SAME_LOOK
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("browser-address").assertTextEquals(SAME_LOOK)
        val second = pixels(field)
        val changed = first.indices.count { first[it] != second[it] }
        assertEquals("Pixels that follow the field's own text", 0, changed)

        referenceWidth = with(composeRule.density) { field.width.toDp() }
        composeRule.waitForIdle()
        val line = checkNotNull(layout)
        assertEquals(1, line.lineCount)
        assertTrue("The long address ends in …", line.isLineEllipsized(0))
        assertTrue("One line fits in the field", line.size.height <= field.height)
    }

    /** The window's pixels inside [area]. */
    private fun pixels(area: Rect): IntArray {
        val view = composeRule.activity.window.decorView
        val window = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        view.draw(Canvas(window))
        val left = area.left.toInt()
        val top = area.top.toInt()
        val width = area.width.toInt()
        val height = area.height.toInt()
        return IntArray(width * height).also {
            window.getPixels(it, 0, width, left, top, width, height)
        }
    }

    private companion object {
        const val PAGE = "https://www.w3schools.com/html/html5_video.asp"

        /** The same host and path in the pill; a different address in the field. */
        const val SAME_LOOK = "https://w3schools.com/html/html5_video.asp?from=test"
    }
}
