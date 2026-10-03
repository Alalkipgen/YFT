package com.alal.yft.feature.browser

import android.content.ClipData
import android.content.ClipboardManager
import android.view.View
import android.view.ViewGroup
import android.webkit.WebView
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import androidx.lifecycle.ViewModelStore
import com.alal.yft.core.model.ThemeMode
import com.alal.yft.detection.SiteAdapterCoordinator
import com.alal.yft.extractor.api.SiteExtractorRegistry
import com.alal.yft.ui.theme.YftTheme
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Uses a shadow WebView to verify creation/identity, not Chromium network behaviour. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w360dp-h780dp")
class BrowserRouteTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val viewModelStore = ViewModelStore()
    private lateinit var viewModel: BrowserViewModel

    @Before
    fun createViewModel() {
        viewModel = BrowserViewModel(
            OkHttpClient(),
            SiteAdapterCoordinator(SiteExtractorRegistry(emptyList())),
        )
        viewModelStore.put("browser", viewModel)
    }

    @After
    fun clearViewModel() {
        composeRule.runOnIdle { viewModelStore.clear() }
    }

    @Test
    fun emptyRouteDoesNotCreateEvenAShadowWebView() {
        showRoute()

        assertStartPage()
        composeRule.runOnIdle { assertTrue(webViews().isEmpty()) }
    }

    @Test
    fun firstGoCreatesOneWebViewAndLaterNavigationAndRecompositionKeepIt() {
        showRoute()
        navigate("example.test/one")
        lateinit var first: WebView
        composeRule.runOnIdle {
            first = webViews().single()
            assertEquals(FIRST_PAGE, Shadows.shadowOf(first).lastLoadedUrl)
            viewModel.onPageStarted(FIRST_PAGE)
            viewModel.onProgressChanged(40)
            viewModel.onPageFinished(FIRST_PAGE, "Fixture page")
            viewModel.onMainFrameError(FIRST_PAGE, "ignored upstream detail")
        }
        assertBrowserPage()
        composeRule.runOnIdle { assertSame(first, webViews().single()) }

        navigate("example.test/two")
        assertBrowserPage()
        composeRule.runOnIdle {
            assertSame(first, webViews().single())
            assertEquals(SECOND_PAGE, Shadows.shadowOf(first).lastLoadedUrl)
        }
    }

    @Test
    fun homeLinkCanCreateAndLoadTheFirstWebViewWithoutAReadinessDeadlock() {
        showRoute(initialLink = "example.test/one")

        assertBrowserPage()
        composeRule.runOnIdle {
            assertEquals(FIRST_PAGE, Shadows.shadowOf(webViews().single()).lastLoadedUrl)
        }
    }

    @Test
    fun recreatedRouteLoadsTheRetainedPageEvenWhenTheInitialLinkWasHandled() {
        composeRule.runOnIdle {
            viewModel.openInitialLink(FIRST_PAGE)
            viewModel.onPageStarted(FIRST_PAGE)
            viewModel.onPageFinished(FIRST_PAGE, "Retained page")
        }
        showRoute(initialLink = FIRST_PAGE)

        assertBrowserPage()
        composeRule.runOnIdle {
            assertEquals(FIRST_PAGE, Shadows.shadowOf(webViews().single()).lastLoadedUrl)
        }
    }

    @Test
    fun invalidAddressShowsAnErrorWithoutCreatingWebView() {
        showRoute()
        navigate("http://example.test")

        assertStartPage()
        composeRule.onNodeWithTag("browser-error").assertIsDisplayed()
        composeRule.runOnIdle { assertTrue(webViews().isEmpty()) }
    }

    @Test
    fun initialBlankPageDoesNotCreateWebView() {
        showRoute(initialLink = "about:blank")

        assertStartPage()
        composeRule.runOnIdle { assertTrue(webViews().isEmpty()) }
    }

    @Test
    fun clipboardDescriptionOnlyChangesHintAndTapOpensTheFirstCopiedLink() {
        showRoute()
        composeRule.runOnIdle {
            val clipboard = composeRule.activity.getSystemService(ClipboardManager::class.java)
            clipboard.setPrimaryClip(
                ClipData.newPlainText("copied", "Watch this: https://example.test/copied."),
            )
        }
        composeRule.onNodeWithText("Use copied link").assertIsDisplayed()
        composeRule.runOnIdle {
            assertTrue(webViews().isEmpty())
            assertEquals("", viewModel.uiState.value.address)
        }

        composeRule.onNodeWithTag("browser-use-copied-link").performClick()
        assertBrowserPage()
        composeRule.runOnIdle {
            assertEquals(
                "https://example.test/copied",
                Shadows.shadowOf(webViews().single()).lastLoadedUrl,
            )
        }
    }

    @Test
    fun siteShortcutCreatesTheFirstWebView() {
        showRoute()
        composeRule.onNodeWithTag("browser-site-https://archive.org").performClick()

        assertBrowserPage()
        composeRule.runOnIdle {
            assertEquals("https://archive.org", Shadows.shadowOf(webViews().single()).lastLoadedUrl)
        }
    }

    private fun showRoute(initialLink: String? = null) {
        composeRule.setContent {
            YftTheme(themeMode = ThemeMode.LIGHT) {
                BrowserRoute(
                    onNavigateBack = {},
                    onOpenPreview = {},
                    initialLink = initialLink,
                    viewModel = viewModel,
                )
            }
        }
    }

    private fun navigate(address: String) {
        composeRule.onNodeWithTag("browser-address")
            .performClick()
            .performTextReplacement(address)
        composeRule.onNodeWithTag("browser-go").performClick()
    }

    private fun assertStartPage() {
        composeRule.onNodeWithTag("browser-start").assertIsDisplayed()
        composeRule.onAllNodesWithTag("browser-surface").assertCountEquals(0)
        assertTopControls()
    }

    private fun assertBrowserPage() {
        composeRule.onAllNodesWithTag("browser-start").assertCountEquals(0)
        composeRule.onNodeWithTag("browser-surface").assertIsDisplayed()
        assertTopControls()
    }

    private fun assertTopControls() {
        composeRule.onNodeWithTag("browser-close").assertIsDisplayed()
        composeRule.onNodeWithTag("browser-address").assertIsDisplayed()
    }

    private fun webViews(): List<WebView> {
        val result = mutableListOf<WebView>()
        fun visit(view: View) {
            if (view is WebView) result += view
            if (view is ViewGroup) {
                for (index in 0 until view.childCount) visit(view.getChildAt(index))
            }
        }
        visit(composeRule.activity.window.decorView)
        return result
    }

    private companion object {
        const val FIRST_PAGE = "https://example.test/one"
        const val SECOND_PAGE = "https://example.test/two"
    }
}