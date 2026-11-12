package me.kozakov.orrery.compose

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.input.InputModeManager
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.LocalInputModeManager
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.requestFocus
import androidx.compose.ui.test.withKeyDown
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.datetime.LocalDate
import kotlinx.datetime.Month
import kotlinx.datetime.YearMonth
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Range selection across the two panes of the dual-month picker (#88b,
 * step 2): July 2026 on the left, August on the right, on a 900dp window.
 */
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "w900dp-h800dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class DualMonthRangeSelectionTest {
    @get:Rule val rule = createComposeRule()

    private lateinit var state: OrreryDateRangePickerState
    private lateinit var inputModeManager: InputModeManager

    private val july = "July 2026"
    private val august = "August 2026"
    private val september = "September 2026"

    private fun content() {
        rule.setContent {
            MaterialTheme {
                state =
                    rememberOrreryDateRangePickerState(
                        initialDisplayedMonth = YearMonth(2026, 7),
                        yearRange = 2026..2027,
                    )
                OrreryDateRangePickerDialog(
                    onDismissRequest = {},
                    confirmButton = {
                        // The dialog is its own window with its own input mode.
                        inputModeManager = LocalInputModeManager.current
                        TextButton(onClick = {}) { Text("Save") }
                    },
                    state = state,
                    today = LocalDate(2026, 7, 15),
                    layoutMode = DateRangeCalendarLayoutMode.DualMonth,
                )
            }
        }
    }

    /** The in-month cell of [dayOfMonth] in the pane described as [month]. */
    private fun day(
        month: String,
        dayOfMonth: Int,
    ): SemanticsNodeInteraction = rule.onNode(hasText("$dayOfMonth") and hasAnyAncestor(hasContentDescription(month)))

    private val dialog get() = rule.onNode(isDialog())

    /** Center of [node] relative to the dialog's root node, for gestures on [dialog]. */
    private fun centerOf(node: SemanticsNodeInteraction): Offset {
        val origin = dialog.fetchSemanticsNode().boundsInRoot.topLeft
        return node.fetchSemanticsNode().boundsInRoot.center - origin
    }

    private fun assertRange(
        start: LocalDate?,
        end: LocalDate?,
    ) = rule.runOnIdle {
        assertEquals(start, state.selectedStartDate)
        assertEquals(end, state.selectedEndDate)
    }

    private fun longPressDrag(
        from: Offset,
        to: Offset,
    ) {
        dialog.performTouchInput {
            down(from)
            advanceEventTime(1000)
            moveTo(to)
            up()
        }
        rule.waitForIdle()
    }

    @OptIn(ExperimentalComposeUiApi::class)
    private fun SemanticsNodeInteraction.keyboardFocus(): SemanticsNodeInteraction {
        rule.runOnIdle { inputModeManager.requestInputMode(InputMode.Keyboard) }
        requestFocus()
        rule.waitForIdle()
        return this
    }

    @Test
    fun clickStartInTheLeftPaneAndEndInTheRightPane() {
        content()
        day(july, 28).performClick()
        day(august, 4).performClick()
        assertRange(LocalDate(2026, 7, 28), LocalDate(2026, 8, 4))
    }

    @Test
    fun clickingAnEarlierDayInTheLeftPaneRestartsTheRange() {
        content()
        day(august, 4).performClick()
        day(july, 28).performClick()
        assertRange(LocalDate(2026, 7, 28), null)
        day(august, 4).performClick()
        assertRange(LocalDate(2026, 7, 28), LocalDate(2026, 8, 4))
    }

    @Test
    fun clickingTheSameDayTwiceMakesAOneDayRange() {
        content()
        day(august, 1).performClick()
        day(august, 1).performClick()
        assertRange(LocalDate(2026, 8, 1), LocalDate(2026, 8, 1))
    }

    @Test
    fun clickRangeCrossesAPageTurn() {
        content()
        day(july, 28).performClick()
        rule.onNodeWithContentDescription("Next month").performClick()
        rule.waitForIdle()
        rule.onNodeWithContentDescription("Next month").performClick()
        rule.waitForIdle()
        rule.runOnIdle { assertEquals(YearMonth(2026, 9), state.displayedMonth) }
        day(september, 3).performClick()
        assertRange(LocalDate(2026, 7, 28), LocalDate(2026, 9, 3))
    }

    @Test
    fun longPressDragFromTheLeftPaneIntoTheRightPane() {
        content()
        longPressDrag(centerOf(day(july, 30)), centerOf(day(august, 5)))
        assertRange(LocalDate(2026, 7, 30), LocalDate(2026, 8, 5))
        rule.runOnIdle { assertEquals(YearMonth(2026, 7), state.displayedMonth) }
    }

    @Test
    fun longPressDragBackwardsFromTheRightPaneKeepsTheRangeOrdered() {
        content()
        longPressDrag(centerOf(day(august, 5)), centerOf(day(july, 30)))
        assertRange(LocalDate(2026, 7, 30), LocalDate(2026, 8, 5))
    }

    @Test
    fun longPressWithoutMovingOnlySetsTheStart() {
        content()
        val at = centerOf(day(august, 1))
        dialog.performTouchInput {
            down(at)
            advanceEventTime(1000)
            moveTo(at + Offset(1f, 0f))
            up()
        }
        rule.waitForIdle()
        assertRange(LocalDate(2026, 8, 1), null)
    }

    @Test
    fun dragAtTheRightEdgePagesOneMonthAndFollowsIntoTheNewPane() {
        content()
        val start = centerOf(day(july, 14))
        val origin = dialog.fetchSemanticsNode().boundsInRoot.topLeft
        val rightPane = rule.onNode(hasContentDescription(august)).fetchSemanticsNode().boundsInRoot
        // Inside the 24dp edge zone (the pane itself is inset 12dp from the pager edge).
        val edge = Offset(rightPane.right - origin.x - 2f, start.y)
        dialog.performTouchInput {
            down(start)
            advanceEventTime(1000)
            moveTo(edge)
        }
        rule.waitForIdle()
        dialog.performTouchInput { up() }
        rule.waitForIdle()
        rule.runOnIdle {
            assertEquals(YearMonth(2026, 8), state.displayedMonth)
            assertEquals(LocalDate(2026, 7, 14), state.selectedStartDate)
            assertEquals(Month.SEPTEMBER, state.selectedEndDate?.month)
        }
    }

    @Test
    fun rightArrowOnTheLastDayOfTheLeftPaneFocusesTheRightPaneWithoutScrolling() {
        content()
        day(july, 31).keyboardFocus().performKeyInput { pressKey(Key.DirectionRight) }
        rule.waitForIdle()
        day(august, 1).assertIsFocused()
        rule.runOnIdle { assertEquals(YearMonth(2026, 7), state.displayedMonth) }
        day(august, 1).performKeyInput { pressKey(Key.DirectionLeft) }
        rule.waitForIdle()
        day(july, 31).assertIsFocused()
    }

    @Test
    fun shiftArrowExtendsTheRangeAcrossThePanes() {
        content()
        day(july, 28).performClick()
        day(july, 31).keyboardFocus().performKeyInput {
            withKeyDown(Key.ShiftLeft) { pressKey(Key.DirectionRight) }
        }
        rule.waitForIdle()
        assertRange(LocalDate(2026, 7, 28), LocalDate(2026, 8, 1))
        day(august, 1).assertIsFocused()
    }

    @Test
    fun rightArrowOffTheRightPaneSlidesByOneMonth() {
        content()
        day(august, 31).keyboardFocus().performKeyInput { pressKey(Key.DirectionRight) }
        rule.waitForIdle()
        rule.runOnIdle { assertEquals(YearMonth(2026, 8), state.displayedMonth) }
        day(september, 1).assertIsFocused()
    }

    @Test
    fun mouseHoverPreviewsTheRangeInTheOtherPaneAndExitClearsIt() {
        content()
        day(july, 28).performClick()
        rule.waitForIdle()
        val rightPane = rule.onNode(hasContentDescription(august))
        val before = rightPane.captureToImage()
        dialog.performMouseInput { moveTo(centerOf(day(august, 4))) }
        rule.waitForIdle()
        assertNull(state.selectedEndDate)
        assertTrue("hover should draw the preview band", differingPixels(before, rightPane.captureToImage()) > 0)
        dialog.performMouseInput { exit() }
        rule.waitForIdle()
        assertEquals(0, differingPixels(before, rightPane.captureToImage()))
    }

    private fun differingPixels(
        a: ImageBitmap,
        b: ImageBitmap,
    ): Int {
        val pa = a.toPixelMap()
        val pb = b.toPixelMap()
        assertEquals(pa.width, pb.width)
        assertEquals(pa.height, pb.height)
        var count = 0
        for (x in 0 until pa.width) {
            for (y in 0 until pa.height) {
                if (pa[x, y] != pb[x, y]) count++
            }
        }
        return count
    }
}
