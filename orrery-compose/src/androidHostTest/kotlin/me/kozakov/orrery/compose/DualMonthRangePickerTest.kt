package me.kozakov.orrery.compose

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.input.InputModeManager
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.LocalInputModeManager
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.KeyInjectionScope
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.requestFocus
import androidx.compose.ui.test.withKeyDown
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.YearMonth
import me.kozakov.orrery.core.DisabledDates
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * Two months side by side in [OrreryDateRangePickerDialog] (#88): layout
 * resolution, synchronized paging, and selection, drag and keyboard moves
 * that cross from one month to the other.
 */
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = WIDE_WINDOW)
class DualMonthRangePickerTest {
    @get:Rule val rule = createComposeRule()

    private lateinit var state: OrreryDateRangePickerState
    private lateinit var strings: DatePickerStrings
    private lateinit var inputModeManager: InputModeManager
    private var layoutMode: DateRangeCalendarLayoutMode by mutableStateOf(DateRangeCalendarLayoutMode.Adaptive())
    private var dismissed = false

    private val july = YearMonth(2026, 7)
    private val august = YearMonth(2026, 8)
    private val bothTitles = "July 2026 – August 2026"

    @Suppress("LongParameterList")
    private fun content(
        displayedMonth: YearMonth = july,
        yearRange: IntRange = 2026..2027,
        layoutDirection: LayoutDirection = LayoutDirection.Ltr,
        displayMode: DatePickerDisplayMode = DatePickerDisplayMode.Picker,
        disabledDates: DisabledDates = DisabledDates.None,
        disallowDisabledInRange: Boolean = false,
        minRangeDays: Int = 1,
        maxRangeDays: Int? = null,
    ) {
        rule.setContent {
            CompositionLocalProvider(LocalLayoutDirection provides layoutDirection) {
                MaterialTheme {
                    strings = DatePickerDefaults.strings()
                    state =
                        rememberOrreryDateRangePickerState(
                            initialDisplayedMonth = displayedMonth,
                            yearRange = yearRange,
                            initialDisplayMode = displayMode,
                            disabledDates = disabledDates,
                            firstDayOfWeek = DayOfWeek.MONDAY,
                            disallowDisabledInRange = disallowDisabledInRange,
                            minRangeDays = minRangeDays,
                            maxRangeDays = maxRangeDays,
                        )
                    OrreryDateRangePickerDialog(
                        onDismissRequest = { dismissed = true },
                        confirmButton = {
                            // The dialog is its own window with its own input mode.
                            inputModeManager = LocalInputModeManager.current
                            TextButton(onClick = {}) { Text("Save") }
                        },
                        state = state,
                        today = LocalDate(2026, 7, 15),
                        layoutMode = layoutMode,
                    )
                }
            }
        }
    }

    private fun day(date: String): SemanticsNodeInteraction = rule.onNode(hasContentDescription(date, substring = true))

    private fun titles(text: String = bothTitles): SemanticsNodeInteraction = rule.onNodeWithContentDescription(text)

    private fun assertRange(
        start: LocalDate?,
        end: LocalDate?,
    ) {
        rule.runOnIdle {
            assertEquals(start, state.selectedStartDate)
            assertEquals(end, state.selectedEndDate)
        }
    }

    private fun assertDisplayed(month: YearMonth) {
        rule.runOnIdle { assertEquals(month, state.displayedMonth) }
    }

    /** Day cells are focusable only in keyboard mode, as on Desktop. */
    @OptIn(ExperimentalComposeUiApi::class)
    private fun press(
        date: String,
        keys: KeyInjectionScope.() -> Unit,
    ) {
        rule.runOnIdle { inputModeManager.requestInputMode(InputMode.Keyboard) }
        day(date).requestFocus()
        day(date).performKeyInput(keys)
        rule.waitForIdle()
    }

    // ---- Layout resolution ----

    @Test
    fun adaptiveShowsTwoMonthsOnAWideWindow() {
        content()
        titles().assertIsDisplayed()
        rule.onNodeWithContentDescription("July 2026").assertIsDisplayed()
        rule.onNodeWithContentDescription("August 2026").assertIsDisplayed()
        day("July 31, 2026").assertIsDisplayed()
        day("August 31, 2026").assertIsDisplayed()
    }

    @Test
    @Config(qualifiers = PHONE_WINDOW)
    fun adaptiveKeepsTheSingleColumnOnAPhone() {
        content()
        titles().assertDoesNotExist()
        day("July 31, 2026").assertIsDisplayed()
    }

    @Test
    @Config(qualifiers = PHONE_WINDOW)
    fun dualMonthIsForcedOnANarrowWindow() {
        layoutMode = DateRangeCalendarLayoutMode.DualMonth
        content()
        titles().assertExists()
        day("July 31, 2026").assertIsDisplayed()
        day("August 31, 2026").assertIsDisplayed()
    }

    @Test
    fun singleMonthIsForcedOnAWideWindow() {
        layoutMode = DateRangeCalendarLayoutMode.SingleMonth
        content()
        titles().assertDoesNotExist()
        day("July 31, 2026").assertIsDisplayed()
    }

    @Test
    fun adaptiveThresholdIsConfigurable() {
        layoutMode = DateRangeCalendarLayoutMode.Adaptive(minDualMonthWidth = 1000.dp)
        content()
        titles().assertDoesNotExist()
    }

    @Test
    fun switchingLayoutsKeepsTheMonthAndTheSelection() {
        content(displayedMonth = YearMonth(2026, 9))
        day("September 28, 2026").performClick()
        day("October 4, 2026").performClick()
        val modes =
            listOf(
                DateRangeCalendarLayoutMode.SingleMonth,
                DateRangeCalendarLayoutMode.DualMonth,
                DateRangeCalendarLayoutMode.SingleMonth,
                DateRangeCalendarLayoutMode.Adaptive(),
            )
        modes.forEach { mode ->
            rule.runOnIdle { layoutMode = mode }
            rule.waitForIdle()
            assertDisplayed(YearMonth(2026, 9))
            day("September 28, 2026").assertIsDisplayed()
            assertRange(LocalDate(2026, 9, 28), LocalDate(2026, 10, 4))
        }
    }

    @Test
    fun inputModeLaysTheTwoFieldsSideBySide() {
        content(displayMode = DatePickerDisplayMode.Input)
        val start = rule.onNodeWithText(strings.rangeStartInputLabel).fetchSemanticsNode().boundsInRoot
        val end = rule.onNodeWithText(strings.rangeEndInputLabel).fetchSemanticsNode().boundsInRoot
        assertEquals(start.top, end.top, 0.5f)
        assertTrue("start $start should be left of end $end", start.right <= end.left)
    }

    @Test
    @Config(qualifiers = "w1100dp-h800dp")
    fun tapBesideTheCardDismisses() {
        content()
        day("July 15, 2026").performClick()
        rule.runOnIdle { assertFalse(dismissed) }
        rule.onNode(isDialog()).performTouchInput { click(Offset(4f, 4f)) }
        rule.runOnIdle { assertTrue(dismissed) }
    }

    // ---- Navigation ----

    @Test
    fun chevronsShiftBothMonthsByOne() {
        content()
        rule.onNodeWithContentDescription(strings.nextMonthDescription).performClick()
        assertDisplayed(august)
        titles("August 2026 – September 2026").assertIsDisplayed()
        rule.onNodeWithContentDescription(strings.previousMonthDescription).performClick()
        rule.onNodeWithContentDescription(strings.previousMonthDescription).performClick()
        assertDisplayed(YearMonth(2026, 6))
    }

    @Test
    fun previousIsDisabledOnTheFirstMonth() {
        content(displayedMonth = YearMonth(2026, 1), yearRange = 2026..2026)
        rule.onNodeWithContentDescription(strings.previousMonthDescription).assertIsNotEnabled()
        rule.onNodeWithContentDescription(strings.nextMonthDescription).assertIsEnabled()
    }

    @Test
    fun lastMonthIsAlwaysTheRightPane() {
        content(displayedMonth = YearMonth(2026, 12), yearRange = 2026..2026)
        assertDisplayed(YearMonth(2026, 11))
        titles("November 2026 – December 2026").assertIsDisplayed()
        rule.onNodeWithContentDescription(strings.nextMonthDescription).assertIsNotEnabled()
        rule.onNodeWithContentDescription(strings.previousMonthDescription).assertIsEnabled()
    }

    @Test
    fun todayIsHiddenWhileTodaysMonthIsInEitherPane() {
        content(displayedMonth = july)
        rule.onNodeWithText("Today").assertDoesNotExist()
        rule.onNodeWithContentDescription(strings.previousMonthDescription).performClick()
        // June | July: today's month is the right pane.
        assertDisplayed(YearMonth(2026, 6))
        rule.onNodeWithText("Today").assertDoesNotExist()
    }

    @Test
    fun todayScrollsTodaysMonthIntoTheLeftPane() {
        content(displayedMonth = YearMonth(2026, 10))
        rule.onNodeWithText("Today").assertIsDisplayed().performClick()
        assertDisplayed(july)
        rule.onNodeWithText("Today").assertDoesNotExist()
    }

    // ---- Selection across the two months ----

    @Test
    fun tappingADayInEachMonthSelectsTheRange() {
        content()
        day("July 28, 2026").performClick()
        day("August 4, 2026").performClick()
        assertRange(LocalDate(2026, 7, 28), LocalDate(2026, 8, 4))
        assertDisplayed(july)
    }

    @Test
    fun minimumLengthIgnoresATooShortRangeAcrossTheMonths() {
        content(minRangeDays = 3)
        day("July 31, 2026").performClick()
        day("August 1, 2026").performClick()
        assertRange(LocalDate(2026, 7, 31), null)
        day("August 2, 2026").performClick()
        assertRange(LocalDate(2026, 7, 31), LocalDate(2026, 8, 2))
    }

    @Test
    fun maximumLengthRestartsTheRangeAtTheTappedDate() {
        content(maxRangeDays = 5)
        day("July 28, 2026").performClick()
        day("August 10, 2026").performClick()
        assertRange(LocalDate(2026, 8, 10), null)
    }

    @Test
    fun disabledDateBetweenTheMonthsRestartsTheRange() {
        content(
            disabledDates = DisabledDates { dates(LocalDate(2026, 8, 2)) },
            disallowDisabledInRange = true,
        )
        day("July 30, 2026").performClick()
        day("August 4, 2026").performClick()
        assertRange(LocalDate(2026, 8, 4), null)
    }

    @Test
    fun longPressDragSelectsFromOneMonthIntoTheOther() {
        content()
        val from = day("July 30, 2026").fetchSemanticsNode().boundsInRoot.center
        val to = day("August 5, 2026").fetchSemanticsNode().boundsInRoot.center
        day("July 30, 2026").performTouchInput {
            down(center)
            advanceEventTime(1000) // hold for the long-press timeout
            moveTo(center + (to - from) / 2f)
            moveTo(center + (to - from))
            up()
        }
        assertRange(LocalDate(2026, 7, 30), LocalDate(2026, 8, 5))
        // The drag crossed the pager without swiping it.
        assertDisplayed(july)
    }

    @Test
    fun dragRestingAtTheEdgePagesByOneMonth() {
        content()
        val from = day("July 30, 2026").fetchSemanticsNode().boundsInRoot
        // Sunday is the last column of the right pane, next to the pager's edge.
        val edge = day("August 9, 2026").fetchSemanticsNode().boundsInRoot
        day("July 30, 2026").performTouchInput {
            down(center)
            advanceEventTime(1000)
            moveTo(center + (Offset(edge.right - 2f, edge.center.y) - from.center))
            advanceEventTime(700)
            up()
        }
        assertDisplayed(august)
        rule.runOnIdle {
            assertEquals(LocalDate(2026, 7, 30), state.selectedStartDate)
            val end = state.selectedEndDate
            assertNotNull(end)
            assertTrue("end $end should be at or past the edge day", end!! >= LocalDate(2026, 8, 9))
        }
    }

    @Test
    fun swipeStillPagesThePager() {
        content()
        day("August 12, 2026").performTouchInput {
            down(center)
            moveBy(Offset(-200f, 0f), 50L)
            moveBy(Offset(-200f, 0f), 50L)
            up()
        }
        rule.runOnIdle {
            assertTrue("swipe should page forward, at ${state.displayedMonth}", state.displayedMonth > july)
            assertNull(state.selectedStartDate)
        }
    }

    // ---- Keyboard ----

    @Test
    fun arrowMovesBetweenThePanesWithoutScrolling() {
        content()
        press("July 31, 2026") { pressKey(Key.DirectionRight) }
        day("August 1, 2026").assertIsFocused()
        assertDisplayed(july)
        press("August 1, 2026") { pressKey(Key.DirectionLeft) }
        day("July 31, 2026").assertIsFocused()
        assertDisplayed(july)
    }

    @Test
    fun upFromTheRightPanesFirstRowLandsInTheLeftPane() {
        content()
        press("August 1, 2026") { pressKey(Key.DirectionUp) }
        day("July 25, 2026").assertIsFocused()
        press("July 25, 2026") { pressKey(Key.DirectionDown) }
        day("August 1, 2026").assertIsFocused()
        assertDisplayed(july)
    }

    @Test
    fun arrowInsideAPaneMovesByOneDay() {
        content()
        press("July 15, 2026") { pressKey(Key.DirectionRight) }
        day("July 16, 2026").assertIsFocused()
        press("July 16, 2026") { pressKey(Key.DirectionDown) }
        day("July 23, 2026").assertIsFocused()
    }

    @Test
    fun shiftArrowExtendsTheRangeIntoTheOtherMonth() {
        content()
        day("July 30, 2026").performClick()
        press("July 31, 2026") { withKeyDown(Key.ShiftLeft) { pressKey(Key.DirectionRight) } }
        assertRange(LocalDate(2026, 7, 30), LocalDate(2026, 8, 1))
        day("August 1, 2026").assertIsFocused()
        assertDisplayed(july)
    }

    @Test
    fun movingPastTheRightPaneSlidesByOneMonth() {
        content()
        press("August 31, 2026") { pressKey(Key.DirectionRight) }
        assertDisplayed(august)
        day("September 1, 2026").assertIsFocused()
    }

    @Test
    fun movingBeforeTheLeftPaneMakesTheTargetTheLeftPane() {
        content()
        press("July 1, 2026") { pressKey(Key.DirectionLeft) }
        assertDisplayed(YearMonth(2026, 6))
        day("June 30, 2026").assertIsFocused()
    }

    @Test
    fun pageKeysMoveByMonthAndYear() {
        content()
        press("July 15, 2026") { pressKey(Key.PageDown) }
        day("August 15, 2026").assertIsFocused()
        assertDisplayed(july)
        press("August 15, 2026") { pressKey(Key.PageDown) }
        day("September 15, 2026").assertIsFocused()
        assertDisplayed(august)
        press("September 15, 2026") { withKeyDown(Key.ShiftLeft) { pressKey(Key.PageDown) } }
        day("September 15, 2027").assertIsFocused()
        assertDisplayed(YearMonth(2027, 8))
    }

    @Test
    fun rtlMirrorsThePanesAndTheHorizontalArrows() {
        content(layoutDirection = LayoutDirection.Rtl)
        val julyPane = rule.onNodeWithContentDescription("July 2026").fetchSemanticsNode().boundsInRoot
        val augustPane = rule.onNodeWithContentDescription("August 2026").fetchSemanticsNode().boundsInRoot
        assertTrue("July $julyPane should be right of August $augustPane", julyPane.left > augustPane.left)
        press("August 1, 2026") { pressKey(Key.DirectionRight) }
        day("July 31, 2026").assertIsFocused()
        press("July 31, 2026") { pressKey(Key.DirectionLeft) }
        day("August 1, 2026").assertIsFocused()
        assertDisplayed(july)
    }

    // ---- Accessibility ----

    @Test
    fun monthsAreTraversalGroupsAndTheTitlesAnnounceOnce() {
        content()
        val traversalGroup = SemanticsMatcher.expectValue(SemanticsProperties.IsTraversalGroup, true)
        rule.onNodeWithContentDescription("July 2026").assert(traversalGroup)
        rule.onNodeWithContentDescription("August 2026").assert(traversalGroup)
        titles().assert(SemanticsMatcher.expectValue(SemanticsProperties.LiveRegion, LiveRegionMode.Polite))
        // A date shows in one pane only, also in the boundary weeks.
        rule.onAllNodes(hasContentDescription("August 1, 2026", substring = true)).assertCountEquals(1)
    }
}

private const val WIDE_WINDOW = "w900dp-h800dp"
private const val PHONE_WINDOW = "w411dp-h900dp"
