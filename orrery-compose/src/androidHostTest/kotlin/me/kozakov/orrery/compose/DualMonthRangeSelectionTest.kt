package me.kozakov.orrery.compose

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.input.InputModeManager
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.LocalInputModeManager
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.requestFocus
import androidx.compose.ui.test.withKeyDown
import androidx.compose.ui.unit.LayoutDirection
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.YearMonth
import me.kozakov.orrery.core.DisabledDates
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
 * Weeks start on Sunday, so the outermost columns are Sunday and Saturday.
 */
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "w900dp-h800dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class DualMonthRangeSelectionTest {
    @get:Rule val rule = createComposeRule()

    private lateinit var state: OrreryDateRangePickerState
    private lateinit var inputModeManager: InputModeManager
    private var inDialog = true

    private companion object {
        /** Two seconds of frames: far longer than one page animation. */
        const val MAX_SETTLE_FRAMES = 120
    }

    private val july = "July 2026"
    private val august = "August 2026"
    private val september = "September 2026"

    private fun content(
        disabledDates: DisabledDates = DisabledDates.None,
        disallowDisabledInRange: Boolean = false,
        layoutDirection: LayoutDirection = LayoutDirection.Ltr,
    ) {
        // Robolectric's test manifest lacks supportsRtl, so a dialog window is
        // always LTR; RTL tests lay the dialog's dual-month calendar out bare.
        inDialog = layoutDirection == LayoutDirection.Ltr
        rule.setContent {
            CompositionLocalProvider(LocalLayoutDirection provides layoutDirection) {
                MaterialTheme {
                    state =
                        rememberOrreryDateRangePickerState(
                            initialDisplayedMonth = YearMonth(2026, 7),
                            yearRange = 2026..2027,
                            firstDayOfWeek = DayOfWeek.SUNDAY,
                            disabledDates = disabledDates,
                            disallowDisabledInRange = disallowDisabledInRange,
                        )
                    if (inDialog) {
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
                    } else {
                        inputModeManager = LocalInputModeManager.current
                        DualMonthRangeCalendar(
                            state = state,
                            today = LocalDate(2026, 7, 15),
                            colors = CalendarDefaults.dayColors(),
                            shapes = CalendarDefaults.dayShapes(),
                            strings = DatePickerDefaults.strings(),
                        )
                    }
                }
            }
        }
    }

    /** The in-month cell of [dayOfMonth] in the pane described as [month]. */
    private fun day(
        month: String,
        dayOfMonth: Int,
    ): SemanticsNodeInteraction = rule.onNode(hasText("$dayOfMonth") and hasAnyAncestor(hasContentDescription(month)))

    /** The node gestures go to: the dialog, or the root when the calendar is laid out bare. */
    private val dialog get() = if (inDialog) rule.onNode(isDialog()) else rule.onRoot()

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

    /**
     * A point inside the auto-page zone at the right (or left) side of the
     * pager, level with [level]: the pane described as [paneMonth] is inset
     * 12dp from the pager edge, so 2px past its bounds is inside the 24dp zone.
     */
    private fun edgeOf(
        paneMonth: String,
        right: Boolean,
        level: Offset,
    ): Offset {
        val origin = dialog.fetchSemanticsNode().boundsInRoot.topLeft
        val pane = rule.onNode(hasContentDescription(paneMonth)).fetchSemanticsNode().boundsInRoot
        val x = if (right) pane.right - origin.x - 2f else pane.left - origin.x + 2f
        return Offset(x, level.y)
    }

    /**
     * Long-presses at [from] and drags to [to], keeping the pointer down.
     * The edge pager keeps paging while the pointer is held in the zone and
     * an auto-advancing clock would run its delays to the end of the
     * calendar, so the clock is driven by hand ([holdFor]) until [release].
     */
    private fun longPressAndHoldAt(
        from: Offset,
        to: Offset,
    ) {
        dialog.performTouchInput {
            down(from)
            advanceEventTime(1000)
            moveBy(Offset(1f, 0f))
        }
        rule.waitForIdle()
        rule.mainClock.autoAdvance = false
        dialog.performTouchInput { moveTo(to) }
    }

    private fun release() {
        dialog.performTouchInput { up() }
        rule.mainClock.autoAdvance = true
        rule.waitForIdle()
    }

    /** Advances the clock by [millis], then frame by frame until a page animation it started has settled. */
    private fun holdFor(millis: Long) {
        rule.mainClock.advanceTimeBy(millis)
        repeat(MAX_SETTLE_FRAMES) {
            if (!rule.runOnUiThread { state.calendar.listState.isScrollInProgress }) return
            rule.mainClock.advanceTimeByFrame()
        }
    }

    private fun assertDisplayed(month: YearMonth) = rule.runOnIdle { assertEquals(month, state.displayedMonth) }

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
    fun longPressDragWithinOneDayMakesAOneDayRange() {
        content()
        val at = centerOf(day(august, 1))
        dialog.performTouchInput {
            down(at)
            advanceEventTime(1000)
            moveTo(at + Offset(1f, 0f))
            up()
        }
        rule.waitForIdle()
        // Same as the single-month drag: any move updates the range, here to one day.
        assertRange(LocalDate(2026, 8, 1), LocalDate(2026, 8, 1))
    }

    @Test
    fun dragAtTheRightEdgePagesOneMonthAndFollowsIntoTheNewPane() {
        content()
        val start = centerOf(day(july, 14))
        longPressAndHoldAt(start, edgeOf(august, right = true, level = start))
        // Before the dwell, the range follows to August's Saturday on July 14's row.
        assertDisplayed(YearMonth(2026, 7))
        assertRange(LocalDate(2026, 7, 14), LocalDate(2026, 8, 15))
        holdFor(DUAL_MONTH_AUTO_PAGE_DWELL_MS + 50)
        release()
        assertDisplayed(YearMonth(2026, 8))
        // Same row and column, now in September.
        assertRange(LocalDate(2026, 7, 14), LocalDate(2026, 9, 19))
    }

    @Test
    fun dragHeldStillAtTheEdgeKeepsPagingEveryInterval() {
        content()
        val start = centerOf(day(july, 14))
        longPressAndHoldAt(start, edgeOf(august, right = true, level = start))
        holdFor(DUAL_MONTH_AUTO_PAGE_DWELL_MS + 50)
        assertDisplayed(YearMonth(2026, 8))
        // No pointer events at all from here on: the pointer is held still.
        holdFor(DUAL_MONTH_AUTO_PAGE_INTERVAL_MS + 50)
        assertDisplayed(YearMonth(2026, 9))
        release()
        // Releasing stops the paging.
        holdFor(DUAL_MONTH_AUTO_PAGE_INTERVAL_MS * 3)
        assertDisplayed(YearMonth(2026, 9))
        assertRange(LocalDate(2026, 7, 14), LocalDate(2026, 10, 17))
    }

    @Test
    fun dragAtTheLeftEdgePagesBackAndFollowsIntoTheNewPane() {
        content()
        val start = centerOf(day(august, 20))
        longPressAndHoldAt(start, edgeOf(july, right = false, level = start))
        // Before the dwell, July's Sunday on August 20's row.
        assertRange(LocalDate(2026, 7, 19), LocalDate(2026, 8, 20))
        holdFor(DUAL_MONTH_AUTO_PAGE_DWELL_MS + 50)
        release()
        assertDisplayed(YearMonth(2026, 6))
        assertRange(LocalDate(2026, 6, 21), LocalDate(2026, 8, 20))
    }

    @Test
    fun dragOverABlankCellKeepsTheEnd() {
        content()
        // August 1 is a Saturday: the cells before it in its row are blank.
        val blank = Offset(centerOf(day(august, 2)).x, centerOf(day(august, 1)).y)
        val start = centerOf(day(july, 14))
        val end = centerOf(day(august, 4))
        dialog.performTouchInput {
            down(start)
            advanceEventTime(1000)
            moveTo(end)
            moveTo(blank)
            up()
        }
        rule.waitForIdle()
        assertRange(LocalDate(2026, 7, 14), LocalDate(2026, 8, 4))
    }

    @Test
    fun aBriefPassThroughTheEdgeZoneDoesNotPage() {
        content()
        val start = centerOf(day(july, 14))
        val edge = edgeOf(august, right = true, level = start)
        val back = centerOf(day(august, 5))
        dialog.performTouchInput {
            down(start)
            advanceEventTime(1000)
            moveTo(edge)
            advanceEventTime(DUAL_MONTH_AUTO_PAGE_DWELL_MS / 2)
            moveTo(back)
        }
        rule.waitForIdle()
        holdFor(DUAL_MONTH_AUTO_PAGE_DWELL_MS + DUAL_MONTH_AUTO_PAGE_INTERVAL_MS)
        release()
        assertDisplayed(YearMonth(2026, 7))
        assertRange(LocalDate(2026, 7, 14), LocalDate(2026, 8, 5))
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
    fun rapidArrowPressesLandOnTheExactDate() {
        content()
        // No idle between presses: each one must start from the cell the previous one focused.
        day(july, 1).keyboardFocus().performKeyInput {
            repeat(10) { pressKey(Key.DirectionRight) }
        }
        rule.waitForIdle()
        day(july, 11).assertIsFocused()
        day(july, 11).performKeyInput {
            // July 11 + 3 weeks = August 1, in the right pane.
            repeat(3) { pressKey(Key.DirectionDown) }
            pressKey(Key.DirectionLeft)
            pressKey(Key.DirectionLeft)
        }
        rule.waitForIdle()
        day(july, 30).assertIsFocused()
        assertDisplayed(YearMonth(2026, 7))
    }

    private fun booked(vararg dates: LocalDate) {
        content(disabledDates = DisabledDates { dates(*dates) }, disallowDisabledInRange = true)
    }

    @Test
    fun rightArrowSkipsABookedDate() {
        booked(LocalDate(2026, 7, 16))
        day(july, 15).keyboardFocus().performKeyInput { pressKey(Key.DirectionRight) }
        rule.waitForIdle()
        day(july, 17).assertIsFocused()
    }

    @Test
    fun rightArrowSkipsSeveralBookedDatesIntoTheOtherPane() {
        booked(LocalDate(2026, 7, 31), LocalDate(2026, 8, 1), LocalDate(2026, 8, 2))
        day(july, 30).keyboardFocus().performKeyInput { pressKey(Key.DirectionRight) }
        rule.waitForIdle()
        day(august, 3).assertIsFocused()
        assertDisplayed(YearMonth(2026, 7))
    }

    @Test
    fun downArrowSkipsABookedDateInTheSameColumn() {
        // July 30 is a Thursday; August 6 is booked, so Down lands on August 13.
        booked(LocalDate(2026, 8, 6))
        day(july, 30).keyboardFocus().performKeyInput { pressKey(Key.DirectionDown) }
        rule.waitForIdle()
        day(august, 13).assertIsFocused()
        assertDisplayed(YearMonth(2026, 7))
    }

    @Test
    fun shiftRightAcrossABookedDateMovesFocusPastItAndKeepsTheStart() {
        booked(LocalDate(2026, 7, 16))
        day(july, 15).performClick()
        day(july, 15).keyboardFocus().performKeyInput {
            withKeyDown(Key.ShiftLeft) { pressKey(Key.DirectionRight) }
        }
        rule.waitForIdle()
        day(july, 17).assertIsFocused()
        // July 15..17 would span the booked 16th, so only the start stays.
        assertRange(LocalDate(2026, 7, 15), null)
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

    private fun rtl() {
        content(layoutDirection = LayoutDirection.Rtl)
        // A real RTL layout: the later month is the left pane.
        val julyPane = rule.onNode(hasContentDescription(july)).fetchSemanticsNode().boundsInRoot
        val augustPane = rule.onNode(hasContentDescription(august)).fetchSemanticsNode().boundsInRoot
        assertTrue("RTL puts August left of July", augustPane.right <= julyPane.left)
    }

    @Test
    fun rtlLongPressDragFromAugustIntoJuly() {
        rtl()
        longPressDrag(centerOf(day(august, 5)), centerOf(day(july, 30)))
        assertRange(LocalDate(2026, 7, 30), LocalDate(2026, 8, 5))
        assertDisplayed(YearMonth(2026, 7))
    }

    @Test
    fun rtlDragAtTheLeftEdgePagesForward() {
        rtl()
        val start = centerOf(day(july, 14))
        longPressAndHoldAt(start, edgeOf(august, right = false, level = start))
        // RTL: the left edge is August's Saturday column.
        assertRange(LocalDate(2026, 7, 14), LocalDate(2026, 8, 15))
        holdFor(DUAL_MONTH_AUTO_PAGE_DWELL_MS + 50)
        release()
        assertDisplayed(YearMonth(2026, 8))
        assertRange(LocalDate(2026, 7, 14), LocalDate(2026, 9, 19))
    }

    @Test
    fun rtlDragAtTheRightEdgePagesBack() {
        rtl()
        val start = centerOf(day(august, 20))
        longPressAndHoldAt(start, edgeOf(july, right = true, level = start))
        // RTL: the right edge is July's Sunday column.
        assertRange(LocalDate(2026, 7, 19), LocalDate(2026, 8, 20))
        holdFor(DUAL_MONTH_AUTO_PAGE_DWELL_MS + 50)
        release()
        assertDisplayed(YearMonth(2026, 6))
        assertRange(LocalDate(2026, 6, 21), LocalDate(2026, 8, 20))
    }

    @Test
    fun rtlShiftArrowExtendsTheRangeAcrossThePanes() {
        rtl()
        day(july, 28).performClick()
        // RTL: Left is the next day, from July's leftmost cell into August.
        day(july, 31).keyboardFocus().performKeyInput {
            withKeyDown(Key.ShiftLeft) { pressKey(Key.DirectionLeft) }
        }
        rule.waitForIdle()
        assertRange(LocalDate(2026, 7, 28), LocalDate(2026, 8, 1))
        day(august, 1).assertIsFocused()
        assertDisplayed(YearMonth(2026, 7))
    }

    @Test
    fun touchNeverPreviewsTheRange() {
        content()
        day(july, 28).performClick()
        rule.waitForIdle()
        val rightPane = rule.onNode(hasContentDescription(august))
        val before = rightPane.captureToImage()
        // A touch over August, short of a long press or a click: no hover, so no preview band.
        val from = centerOf(day(august, 4))
        val to = centerOf(day(august, 11))
        dialog.performTouchInput {
            down(from)
            moveTo(to)
            cancel()
        }
        rule.waitForIdle()
        assertNull(state.selectedEndDate)
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
