package com.alal.yft.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTouchHeightIsEqualTo
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.unit.dp
import androidx.core.content.res.ResourcesCompat
import androidx.test.core.app.ApplicationProvider
import com.alal.yft.R
import com.alal.yft.core.model.ThemeMode
import com.alal.yft.ui.theme.PlusJakartaSans
import com.alal.yft.ui.theme.YftTheme
import com.alal.yft.ui.theme.YftTypography
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class YftComponentsTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun segmentedControlSelectsOneOptionWithFullSizeTargets() {
        val picked = mutableListOf<String>()
        composeRule.setContent {
            YftTheme(ThemeMode.LIGHT) {
                var selected by remember { mutableStateOf("Video") }
                YftSegmentedControl(
                    options = listOf("Video", "Audio"),
                    selected = selected,
                    onSelect = { selected = it; picked += it },
                    label = { it },
                    testTag = { "segment-$it" },
                )
            }
        }

        composeRule.onNodeWithTag("segment-Video").assertIsSelected()
        composeRule.onNodeWithTag("segment-Audio").assertIsNotSelected()
            .assertHeightIsAtLeast(48.dp)
            .performClick()
        composeRule.onNodeWithTag("segment-Audio").assertIsSelected()
        composeRule.onNodeWithTag("segment-Video").assertIsNotSelected()
        assertEquals(listOf("Audio"), picked)
    }

    @Test
    fun filterChipShowsCountAndSelection() {
        composeRule.setContent {
            YftTheme(ThemeMode.DARK) {
                Column {
                    YftFilterChip(
                        label = "Active",
                        selected = true,
                        onClick = {},
                        count = 2,
                        modifier = Modifier.testTag("chip-active"),
                    )
                    YftFilterChip(
                        label = "Done",
                        selected = false,
                        onClick = {},
                        modifier = Modifier.testTag("chip-done"),
                    )
                }
            }
        }

        // The 40dp pill keeps a 48dp touch target.
        composeRule.onNodeWithTag("chip-active").assertIsSelected()
            .assertTouchHeightIsEqualTo(48.dp)
        composeRule.onNodeWithText("2").assertIsDisplayed()
        composeRule.onNodeWithTag("chip-done").assertIsNotSelected()
    }

    @Test
    fun countBadgeHidesZeroAndCapsLargeCounts() {
        composeRule.setContent {
            YftTheme(ThemeMode.LIGHT) {
                Column {
                    YftCountBadge(count = 0, modifier = Modifier.testTag("badge-zero"))
                    YftCountBadge(count = 150)
                }
            }
        }

        composeRule.onNodeWithTag("badge-zero").assertDoesNotExist()
        composeRule.onNodeWithText("99+").assertIsDisplayed()
    }

    @Test
    fun promptboxEditingStatesSubmitOnlyARealLink() {
        var submitted = 0
        var used = 0
        composeRule.setContent {
            YftTheme(ThemeMode.LIGHT) {
                var text by remember { mutableStateOf("") }
                YftPromptbox(
                    text = text,
                    onTextChange = { text = it },
                    status = PromptboxStatus.Editing,
                    onSubmit = { submitted++ },
                    onClear = { text = "" },
                    showClipboardSuggestion = true,
                    onUseClipboard = { used++ },
                )
            }
        }

        composeRule.onNodeWithText("Paste a page or media link").assertIsDisplayed()
        composeRule.onNodeWithTag("home-link-clear").assertDoesNotExist()
        composeRule.onNodeWithTag("home-open-link")
            .assertHeightIsAtLeast(48.dp)
            .assertWidthIsAtLeast(48.dp)
            .performClick()
        assertEquals("a blank link is never submitted", 0, submitted)

        composeRule.onNodeWithText("Use copied link").assertIsDisplayed()
        composeRule.onNodeWithTag("home-use-clipboard").performClick()
        assertEquals(1, used)

        composeRule.onNodeWithTag("home-link").performTextInput("https://archive.org/details/x")
        composeRule.onNodeWithTag("home-link-clear").assertIsDisplayed()
        composeRule.onNodeWithTag("home-open-link").performClick()
        assertEquals(1, submitted)
        composeRule.onNodeWithTag("home-link-clear").performClick()
        composeRule.onNodeWithText("Paste a page or media link").assertIsDisplayed()
    }

    @Test
    fun promptboxResultStatesOfferTheirActions() {
        var status by mutableStateOf<PromptboxStatus>(PromptboxStatus.Searching)
        val events = mutableListOf<String>()
        composeRule.setContent {
            YftTheme(ThemeMode.LIGHT) {
                YftPromptbox(
                    text = "https://archive.org/details/x",
                    onTextChange = {},
                    status = status,
                    onSubmit = {},
                    onClear = {},
                    onView = { events += "view" },
                    onOpenInBrowser = { events += "browser" },
                    onCancelSearch = { events += "cancel" },
                    onEdit = { events += "edit" },
                )
            }
        }

        composeRule.onNodeWithText("Looking for media…").assertIsDisplayed()
        composeRule.onNodeWithTag("home-search-progress").assertIsDisplayed()
        composeRule.onNodeWithTag("home-search-cancel").performClick()

        status = PromptboxStatus.Found(count = 3)
        composeRule.onNodeWithText("3 media found").assertIsDisplayed()
        composeRule.onNodeWithTag("home-search-progress").assertDoesNotExist()
        composeRule.onNodeWithTag("home-view-media").performClick()

        status = PromptboxStatus.Found(count = 1)
        composeRule.onNodeWithText("1 media found").assertIsDisplayed()

        status = PromptboxStatus.NotFound()
        composeRule.onNodeWithText(PromptboxStatus.NO_MEDIA_MESSAGE).assertIsDisplayed()
        composeRule.onNodeWithTag("home-open-in-browser").performClick()
        composeRule.onNodeWithTag("home-not-found").performClick()

        assertEquals(listOf("cancel", "view", "browser", "edit"), events)
    }

    @Test
    fun thumbnailPlaceholderAndStatusChipsRender() {
        composeRule.setContent {
            YftTheme(ThemeMode.DARK) {
                Column {
                    YftThumbnail(
                        image = null,
                        kind = YftMediaKind.Audio,
                        durationLabel = "2:58",
                        modifier = Modifier.size(120.dp),
                    )
                    YftStatusChip(text = "Waiting for Wi-Fi", tone = YftStatusTone.Waiting)
                    YftStatusChip(text = "Failed · Link expired", tone = YftStatusTone.Failed)
                    YftOutlinedChip(text = "No ads")
                }
            }
        }

        composeRule.onNodeWithText("2:58").assertIsDisplayed()
        composeRule.onNodeWithText("Waiting for Wi-Fi").assertIsDisplayed()
        composeRule.onNodeWithText("Failed · Link expired").assertIsDisplayed()
        composeRule.onNodeWithText("No ads").assertIsDisplayed()
    }

    @Test
    fun plusJakartaSansIsBundledAndUsedByEveryRole() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        listOf(
            R.font.plus_jakarta_sans_regular,
            R.font.plus_jakarta_sans_medium,
            R.font.plus_jakarta_sans_semibold,
            R.font.plus_jakarta_sans_bold,
        ).forEach { font -> assertNotNull(ResourcesCompat.getFont(context, font)) }

        with(YftTypography) {
            listOf(headlineLarge, titleLarge, bodyLarge, labelLarge, bodySmall).forEach {
                assertEquals(PlusJakartaSans, it.fontFamily)
            }
            assertEquals(32f, headlineLarge.fontSize.value)
            assertEquals(22f, titleLarge.fontSize.value)
            assertEquals(16f, bodyLarge.fontSize.value)
            assertEquals(14f, labelLarge.fontSize.value)
        }
    }
}
