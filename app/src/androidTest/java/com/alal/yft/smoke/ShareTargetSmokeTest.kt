package com.alal.yft.smoke

import android.content.Intent
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.test.platform.app.InstrumentationRegistry
import com.alal.yft.MainActivity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * v1.0.2 (item 4): another app's Share reaches the production activity, and Home looks the
 * shared link up. The link's host is `.invalid` (RFC 6761), so no site is ever asked.
 */
@RunWith(AndroidJUnit4::class)
@LargeTest
class ShareTargetSmokeTest {
    @get:Rule
    val composeRule = createEmptyComposeRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun androidOffersYftForSharedText() {
        val share = Intent(Intent.ACTION_SEND)
            .setType("text/plain")
            .putExtra(Intent.EXTRA_TEXT, "A clip $LINK")
            .setPackage(context.packageName)

        val targets = context.packageManager.queryIntentActivities(share, 0)

        assertEquals(
            listOf(MainActivity::class.java.name),
            targets.map { it.activityInfo.name },
        )
    }

    @Test
    fun aSharedTitleAndLinkIsLookedUpOnHomeAsThePastedLink() {
        val share = Intent(Intent.ACTION_SEND)
            .setClass(context, MainActivity::class.java)
            .setType("text/plain")
            .putExtra(Intent.EXTRA_TEXT, "A clip worth keeping $LINK.")
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

        ActivityScenario.launch<MainActivity>(share).use {
            // The lookup runs and ends (the host never resolves); Edit link shows what it was.
            val settled = runCatching {
                composeRule.waitUntil(LOOKUP_MILLIS) {
                    hasNode("home-not-found") || hasNode("home-found")
                }
            }.isSuccess
            when {
                hasNode("home-not-found") -> composeRule.onNodeWithTag("home-not-found")
                    .performClick()
                hasNode("home-found") -> composeRule.onNodeWithTag("home-found").performClick()
                else -> composeRule.onAllNodesWithTag("home-search-cancel").onFirst()
                    .performClick()
            }
            composeRule.waitUntil(5_000) { hasNode("home-link") }

            // The link alone, without the title or the full stop after it.
            val box = composeRule.onAllNodes(hasTestTag("home-link") and hasText(LINK))
            assertTrue(
                "Home's box must hold the shared link (lookup settled: $settled)",
                box.fetchSemanticsNodes().size == 1,
            )
        }
    }

    private fun hasNode(tag: String): Boolean =
        composeRule.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty()

    private companion object {
        const val LINK = "https://share-smoke.invalid/watch?v=1"
        const val LOOKUP_MILLIS = 60_000L
    }
}
