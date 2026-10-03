package com.alal.yft.design

import androidx.activity.ComponentActivity
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.toSize
import com.alal.yft.core.model.ThemeMode
import kotlin.math.roundToInt
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Audits every redesigned screen with the sample data of [DesignRenderTest]: each control
 * TalkBack can reach says what it is and is at least 48dp each way, and the dark theme with the
 * largest text Android offers still composes every control with its label. The window is a
 * narrow phone made tall, so every sample row is laid out and none sits under a pinned bar
 * part-way through a scroll.
 */
@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w360dp-h1600dp-xhdpi")
class AccessibilityAuditTest(private val screenName: String) {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val screen = DESIGN_SCREENS.single { it.name == screenName }

    @Test
    fun everyControlIsLabelledAndAtLeast48dp() {
        show(ThemeMode.LIGHT, fontScale = 1f)

        val controls = controls()
        assertTrue("$screenName has no controls", controls.isNotEmpty())
        assertEquals("$screenName unlabelled controls", emptyList<String>(), unlabelled(controls))
        assertEquals("$screenName targets under 48dp", emptyList<String>(), smallTargets(controls))
    }

    @Test
    fun darkThemeWithTheLargestTextKeepsEveryLabel() {
        show(ThemeMode.DARK, fontScale = LARGEST_FONT_SCALE)

        val controls = controls()
        assertTrue("$screenName has no controls", controls.isNotEmpty())
        assertEquals("$screenName unlabelled controls", emptyList<String>(), unlabelled(controls))
    }

    private fun show(themeMode: ThemeMode, fontScale: Float) {
        composeRule.setContent {
            DesignStage(themeMode = themeMode, fontScale = fontScale, content = screen.content)
        }
        composeRule.waitForIdle()
    }

    private fun controls(): List<SemanticsNode> =
        composeRule.onAllNodes(hasClickAction()).fetchSemanticsNodes()

    private fun unlabelled(controls: List<SemanticsNode>): List<String> =
        controls.filter { it.label().isBlank() }.map { "control ${it.id} at ${it.boundsInRoot}" }

    /**
     * Controls whose touch area stays under 48dp. Compose widens a smaller control's touch area
     * to 48dp around its centre, but only up to the edge of a neighbouring control, so a 40dp
     * icon button with room around it passes and two crowded ones do not.
     */
    private fun smallTargets(controls: List<SemanticsNode>): List<String> {
        val density = composeRule.density
        val minimum = with(density) { MIN_TARGET.toPx() }
        val bounds = controls.associateWith { Rect(it.positionInRoot, it.size.toSize()) }
        return controls.mapNotNull { node ->
            val own = bounds.getValue(node)
            var area = own.widenedTo(minimum)
            controls.filter { other -> other !== node && !related(node, other) }.forEach { other ->
                area = area.stopAt(bounds.getValue(other), own)
            }
            if (area.width >= minimum - ROUNDING_PX && area.height >= minimum - ROUNDING_PX) {
                null
            } else {
                val width = with(density) { area.width.toDp().value.roundToInt() }
                val height = with(density) { area.height.toDp().value.roundToInt() }
                "'${node.label()}' can be touched in ${width}x${height}dp"
            }
        }
    }

    private fun Rect.widenedTo(minimum: Float): Rect {
        val extraX = ((minimum - width) / 2).coerceAtLeast(0f)
        val extraY = ((minimum - height) / 2).coerceAtLeast(0f)
        return Rect(left - extraX, top - extraY, right + extraX, bottom + extraY)
    }

    /** Shrinks this touch area so it ends where [neighbour] begins, on the side it lies. */
    private fun Rect.stopAt(neighbour: Rect, own: Rect): Rect {
        if (!overlaps(neighbour)) return this
        val besideX = neighbour.top < own.bottom && neighbour.bottom > own.top
        val besideY = neighbour.left < own.right && neighbour.right > own.left
        return when {
            besideX && neighbour.left >= own.right -> copy(right = minOf(right, neighbour.left))
            besideX && neighbour.right <= own.left -> copy(left = maxOf(left, neighbour.right))
            besideY && neighbour.top >= own.bottom -> copy(bottom = minOf(bottom, neighbour.top))
            besideY && neighbour.bottom <= own.top -> copy(top = maxOf(top, neighbour.bottom))
            else -> this
        }
    }

    /** A control inside another (a ⋯ button on a card) shares its touches with it. */
    private fun related(a: SemanticsNode, b: SemanticsNode): Boolean =
        a.isInside(b) || b.isInside(a)

    private fun SemanticsNode.isInside(other: SemanticsNode): Boolean =
        generateSequence(parent) { it.parent }.any { it.id == other.id }

    /** What TalkBack reads for a control: its description, its text or its click label. */
    private fun SemanticsNode.label(): String = listOfNotNull(
        config.getOrNull(SemanticsProperties.ContentDescription)?.joinToString(" "),
        config.getOrNull(SemanticsProperties.Text)?.joinToString(" ") { it.text },
        config.getOrNull(SemanticsProperties.EditableText)?.text,
        config.getOrNull(SemanticsActions.OnClick)?.label,
    ).joinToString(" ").trim()

    companion object {
        private val MIN_TARGET = 48.dp
        private const val ROUNDING_PX = 1f
        private const val LARGEST_FONT_SCALE = 2f

        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0}")
        fun screens(): List<Array<Any>> = DESIGN_SCREENS.map { arrayOf<Any>(it.name) }
    }
}
