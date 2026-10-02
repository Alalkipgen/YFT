package com.alal.yft.feature.home

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ApplicationProvider
import com.alal.yft.core.model.ThemeMode
import com.alal.yft.ui.navigation.YftDestination
import com.alal.yft.ui.theme.YftTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class HomeScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val opened = mutableListOf<String>()
    private val destinations = mutableListOf<YftDestination>()

    private fun setContent() {
        composeRule.setContent {
            YftTheme(themeMode = ThemeMode.LIGHT) {
                HomeScreen(
                    onOpenDestination = { destinations += it },
                    onOpenLink = { opened += it },
                )
            }
        }
    }

    @Test
    fun typedLinkOpensTheBrowserTrimmed() {
        setContent()
        composeRule.onNodeWithTag("home-open-link").assertIsNotEnabled()

        composeRule.onNodeWithTag("home-link").performTextInput("  example.com/watch  ")
        composeRule.onNodeWithTag("home-open-link").assertIsEnabled().performClick()

        assertEquals(listOf("example.com/watch"), opened)
        composeRule.onNodeWithTag("home-link-clear").performClick()
        composeRule.onNodeWithTag("home-open-link").assertIsNotEnabled()
    }

    @Test
    fun pasteFillsTheFieldFromTheClipboardOnlyWhenTapped() {
        val clipboard = ApplicationProvider.getApplicationContext<Context>()
            .getSystemService(ClipboardManager::class.java)
        clipboard.setPrimaryClip(
            ClipData.newPlainText("copied", "Look: https://media.example.test/v/1."),
        )
        setContent()
        composeRule.onNodeWithTag("home-link").assertTextEquals("Page or media link", "")

        composeRule.onNodeWithTag("home-paste").performClick()

        composeRule.onNodeWithTag("home-link")
            .assertTextEquals("Page or media link", "https://media.example.test/v/1")
        composeRule.onNodeWithTag("home-open-link").performClick()
        assertEquals(listOf("https://media.example.test/v/1"), opened)
    }

    @Test
    fun browserCanStillBeOpenedWithoutALink() {
        setContent()

        composeRule.onNodeWithTag("home-open-browser").performClick()

        assertEquals(listOf(YftDestination.BROWSER), destinations)
        assertEquals(emptyList<String>(), opened)
    }
}
