package com.alal.yft.smoke

import android.graphics.Bitmap
import android.util.Log
import android.util.Xml
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import com.alal.yft.MainActivity
import java.io.File
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestName
import org.junit.runner.RunWith
import org.xmlpull.v1.XmlPullParser

/** Exercises the production activity and Chromium, not a Robolectric WebView shadow. */
@RunWith(AndroidJUnit4::class)
@LargeTest
class BrowserSmokeTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @get:Rule
    val testName = TestName()

    @Before
    fun openBrowserFromHome() {
        composeRule.waitUntil(20_000) { hasNode("home-open-browser") }
        composeRule.onNodeWithTag("home-open-browser").performClick()
        composeRule.onNodeWithTag("browser-close").assertIsDisplayed()
    }

    @Test
    fun emptyBrowserKeepsAddressAndCloseVisible() {
        composeRule.onNodeWithTag("browser-address").assertIsDisplayed()
        composeRule.onNodeWithTag("browser-close").assertIsDisplayed()
        composeRule.onNodeWithTag("browser-empty").assertIsDisplayed()
    }

    @Test
    fun publicPageLoadsWithoutClosingTheApp() {
        navigateTo("https://example.com/")
        composeRule.waitUntil(30_000) {
            !hasNode("browser-empty") && !hasNode("browser-progress")
        }
        composeRule.onNodeWithTag("browser-address")
            .assertTextContains("example.com", substring = true)
        composeRule.onNodeWithTag("browser-close").assertIsDisplayed()
        composeRule.onNodeWithTag("browser-surface").assertIsDisplayed()
        composeRule.onAllNodesWithTag("browser-error").fetchSemanticsNodes().let { nodes ->
            check(nodes.isEmpty()) { "The public HTTPS page did not load successfully" }
        }
    }

    @Test
    fun publicHtml5VideoIsDetectedOrReportsASlowSiteWarning() {
        navigateTo(PUBLIC_VIDEO_PAGE)
        val found = runCatching {
            composeRule.waitUntil(20_000) { hasNode("media-found-button") }
        }.isSuccess
        composeRule.onNodeWithTag("browser-close").assertIsDisplayed()
        if (found) {
            composeRule.onNodeWithTag("media-found-button").performClick()
            composeRule.onNodeWithTag("found-sheet").assertIsDisplayed()
        } else {
            Log.w(LOG_TAG, "Public HTML5 media was not found within 20 s; slow-site warning")
        }
    }

    @After
    fun captureScreenshotAndSafeBounds() {
        val name = when (testName.methodName) {
            "emptyBrowserKeepsAddressAndCloseVisible" -> "01-browser-empty"
            "publicPageLoadsWithoutClosingTheApp" -> "02-browser-page"
            else -> "03-found"
        }
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val directory = checkNotNull(context.getExternalFilesDir("smoke-screenshots"))
        check(directory.exists() || directory.mkdirs())
        val bitmap = checkNotNull(instrumentation.uiAutomation.takeScreenshot())
        try {
            File(directory, "$name.png").outputStream().use { output ->
                check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, output))
            }
        } finally {
            bitmap.recycle()
        }

        // Never keep the UI hierarchy: it can contain page text and complete link addresses.
        val hierarchy = File(context.cacheDir, "smoke-window.xml")
        try {
            UiDevice.getInstance(instrumentation).dumpWindowHierarchy(hierarchy)
            val bounds = readSafeBounds(hierarchy)
            File(directory, "$name.bounds.txt").writeText(bounds)
            Log.i(LOG_TAG, "$name: ${bounds.replace('\n', ';')}")
        } finally {
            hierarchy.delete()
        }
    }

    private fun navigateTo(url: String) {
        composeRule.onNodeWithTag("browser-address")
            .performClick()
            .performTextReplacement(url)
        composeRule.onNodeWithTag("browser-go").performClick()
    }

    private fun hasNode(tag: String): Boolean =
        composeRule.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty()

    private fun readSafeBounds(hierarchy: File): String {
        var address = "missing"
        var webView = "absent"
        hierarchy.inputStream().use { input ->
            val parser = Xml.newPullParser()
            parser.setInput(input, "UTF-8")
            while (parser.eventType != XmlPullParser.END_DOCUMENT) {
                if (parser.eventType == XmlPullParser.START_TAG && parser.name == "node") {
                    val id = parser.getAttributeValue(null, "resource-id").orEmpty()
                    val className = parser.getAttributeValue(null, "class").orEmpty()
                    val bounds = parser.getAttributeValue(null, "bounds").orEmpty()
                    if (BOUNDS.matches(bounds)) {
                        if (id == "browser-address" || id.endsWith("/browser-address")) {
                            address = bounds
                        }
                        if (className == "android.webkit.WebView") webView = bounds
                    }
                }
                parser.next()
            }
        }
        return "browser-address bounds=$address\nWebView bounds=$webView\n"
    }

    private companion object {
        const val LOG_TAG = "YFTSmoke"
        const val PUBLIC_VIDEO_PAGE =
            "https://commons.wikimedia.org/wiki/File:Big_Buck_Bunny_4K.webm"
        val BOUNDS = Regex("""\[-?\d+,-?\d+\]\[-?\d+,-?\d+\]""")
    }
}