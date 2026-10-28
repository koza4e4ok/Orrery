package me.kozakov.orrery.compose

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.input.InputModeManager
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.LocalInputModeManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.KeyInjectionScope
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.requestFocus
import androidx.compose.ui.test.withKeyDown
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.YearMonth
import me.kozakov.orrery.core.Selection
import me.kozakov.orrery.core.SelectionMode
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Month-grid keyboard shortcuts (#62). July 2026 with Monday first-day
 * has five rows: Jun 29 – Jul 5, Jul 6 – 12, 13 – 19, 20 – 26 and
 * Jul 27 – Aug 2. Day cells are keyboard-focusable, as on Desktop or
 * with a hardware keyboard.
 */
@RunWith(AndroidJUnit4::class)
class KeyboardShortcutsTest {
    @get:Rule
    val rule = createComposeRule()

    private lateinit var inputModeManager: InputModeManager

    private fun content(
        firstVisible: YearMonth = YearMonth(2026, 7),
        mode: SelectionMode? = null,
        initialSelection: Selection = Selection.Empty,
    ): Pair<() -> CalendarState, () -> CalendarSelectionState?> {
        lateinit var state: CalendarState
        var selection: CalendarSelectionState? = null
        rule.setContent {
            inputModeManager = LocalInputModeManager.current
            state =
                rememberCalendarState(
                    startMonth = YearMonth(2026, 1),
                    endMonth = YearMonth(2027, 12),
                    firstVisibleMonth = firstVisible,
                    firstDayOfWeek = DayOfWeek.MONDAY,
                )
            selection = mode?.let { rememberCalendarSelectionState(mode = it, initialSelection = initialSelection) }
            HorizontalCalendar(
                state = state,
                dragSelection = selection,
                dayContent = { day ->
                    DefaultDay(
                        day = day,
                        modifier = Modifier.testTag("day-${day.date}"),
                        selectionState = selection,
                        today = LocalDate(2026, 7, 15),
                        animateSelection = false,
                    )
                },
            )
        }
        return { state } to { selection }
    }

    /** Focuses [date]'s cell in keyboard input mode and sends [keys] to it. */
    @OptIn(ExperimentalComposeUiApi::class)
    private fun press(
        date: String,
        keys: KeyInjectionScope.() -> Unit,
    ) {
        rule.runOnIdle { inputModeManager.requestInputMode(InputMode.Keyboard) }
        rule.onNodeWithTag("day-$date").requestFocus()
        rule.onNodeWithTag("day-$date").performKeyInput(keys)
        rule.waitForIdle()
    }

    private fun range(
        start: LocalDate,
        end: LocalDate,
    ) = Selection(rangeStart = start, rangeEnd = end)

    private fun assertFocused(date: String) {
        rule.onNodeWithTag("day-$date").assertIsFocused()
    }

    @Test
    fun upOnTheTopRowPagesBackAndFocusesSevenDaysEarlier() {
        val (state, _) = content()
        press("2026-07-03") { pressKey(Key.DirectionUp) }
        assertEquals(YearMonth(2026, 6), state().firstVisibleMonth)
        assertFocused("2026-06-26")
    }

    @Test
    fun downOnTheBottomRowPagesForwardAndFocusesSevenDaysLater() {
        val (state, _) = content()
        press("2026-07-28") { pressKey(Key.DirectionDown) }
        assertEquals(YearMonth(2026, 8), state().firstVisibleMonth)
        assertFocused("2026-08-04")
    }

    @Test
    fun upAndDownInsideTheMonthDoNotPage() {
        val (state, _) = content()
        press("2026-07-15") { pressKey(Key.DirectionUp) }
        assertEquals(YearMonth(2026, 7), state().firstVisibleMonth)
        press("2026-07-15") { pressKey(Key.DirectionDown) }
        assertEquals(YearMonth(2026, 7), state().firstVisibleMonth)
    }

    @Test
    fun pageDownJumpsOneMonthForwardKeepingTheDay() {
        val (state, _) = content()
        press("2026-07-15") { pressKey(Key.PageDown) }
        assertEquals(YearMonth(2026, 8), state().firstVisibleMonth)
        assertFocused("2026-08-15")
    }

    @Test
    fun pageUpJumpsOneMonthBackKeepingTheDay() {
        val (state, _) = content()
        press("2026-07-15") { pressKey(Key.PageUp) }
        assertEquals(YearMonth(2026, 6), state().firstVisibleMonth)
        assertFocused("2026-06-15")
    }

    @Test
    fun pageDownClampsToTheEndOfAShorterMonth() {
        // Monday-first, January and February 2027 grids share no dates.
        val (state, _) = content(firstVisible = YearMonth(2027, 1))
        press("2027-01-31") { pressKey(Key.PageDown) }
        assertEquals(YearMonth(2027, 2), state().firstVisibleMonth)
        assertFocused("2027-02-28")
    }

    @Test
    fun shiftPageDownJumpsOneYearForward() {
        val (state, _) = content()
        press("2026-07-15") { withKeyDown(Key.ShiftLeft) { pressKey(Key.PageDown) } }
        assertEquals(YearMonth(2027, 7), state().firstVisibleMonth)
        assertFocused("2027-07-15")
    }

    @Test
    fun shiftPageUpJumpsOneYearBack() {
        val (state, _) = content(firstVisible = YearMonth(2027, 3))
        press("2027-03-10") { withKeyDown(Key.ShiftLeft) { pressKey(Key.PageUp) } }
        assertEquals(YearMonth(2026, 3), state().firstVisibleMonth)
        assertFocused("2026-03-10")
    }

    @Test
    fun homeAndEndFocusTheEndsOfTheWeekRow() {
        val (state, _) = content()
        press("2026-07-15") { pressKey(Key.MoveHome) }
        assertFocused("2026-07-13")
        press("2026-07-15") { pressKey(Key.MoveEnd) }
        assertFocused("2026-07-19")
        assertEquals(YearMonth(2026, 7), state().firstVisibleMonth)
    }

    @Test
    fun shiftRightStartsARangeAtTheFocusedDay() {
        val (_, selection) = content(mode = SelectionMode.Range())
        press("2026-07-15") { withKeyDown(Key.ShiftLeft) { pressKey(Key.DirectionRight) } }
        rule.runOnIdle {
            assertEquals(range(LocalDate(2026, 7, 15), LocalDate(2026, 7, 16)), selection()?.selection)
        }
        assertFocused("2026-07-16")
    }

    @Test
    fun shiftArrowsExtendTheRangeFromItsFixedStart() {
        val (_, selection) =
            content(
                mode = SelectionMode.Range(),
                initialSelection = range(LocalDate(2026, 7, 15), LocalDate(2026, 7, 16)),
            )
        press("2026-07-16") { withKeyDown(Key.ShiftLeft) { pressKey(Key.DirectionDown) } }
        rule.runOnIdle {
            assertEquals(range(LocalDate(2026, 7, 15), LocalDate(2026, 7, 23)), selection()?.selection)
        }
        assertFocused("2026-07-23")
        press("2026-07-23") { withKeyDown(Key.ShiftLeft) { pressKey(Key.DirectionLeft) } }
        rule.runOnIdle {
            assertEquals(range(LocalDate(2026, 7, 15), LocalDate(2026, 7, 22)), selection()?.selection)
        }
    }

    @Test
    fun shiftLeftFromTheRangeStartGrowsTheRangeBackward() {
        val (_, selection) =
            content(
                mode = SelectionMode.Range(),
                initialSelection = range(LocalDate(2026, 7, 15), LocalDate(2026, 7, 16)),
            )
        press("2026-07-15") { withKeyDown(Key.ShiftLeft) { pressKey(Key.DirectionLeft) } }
        rule.runOnIdle {
            assertEquals(range(LocalDate(2026, 7, 14), LocalDate(2026, 7, 16)), selection()?.selection)
        }
        assertFocused("2026-07-14")
    }

    @Test
    fun shiftDownOnTheBottomRowExtendsTheRangeAndPages() {
        val (state, selection) = content(mode = SelectionMode.Range())
        press("2026-07-29") { withKeyDown(Key.ShiftLeft) { pressKey(Key.DirectionDown) } }
        rule.runOnIdle {
            assertEquals(range(LocalDate(2026, 7, 29), LocalDate(2026, 8, 5)), selection()?.selection)
        }
        assertEquals(YearMonth(2026, 8), state().firstVisibleMonth)
        assertFocused("2026-08-05")
    }

    @Test
    fun shiftArrowRespectsTheRangeLengthLimitsAndKeepsTheAnchor() {
        val (_, selection) = content(mode = SelectionMode.Range(maxDays = 2))
        press("2026-07-15") { withKeyDown(Key.ShiftLeft) { pressKey(Key.DirectionDown) } }
        rule.runOnIdle { assertEquals(Selection(rangeStart = LocalDate(2026, 7, 15)), selection()?.selection) }
    }

    @Test
    fun shiftArrowOutsideRangeModeDoesNotSelect() {
        val (_, selection) = content(mode = SelectionMode.Single())
        press("2026-07-15") { withKeyDown(Key.ShiftLeft) { pressKey(Key.DirectionRight) } }
        rule.runOnIdle { assertEquals(Selection.Empty, selection()?.selection) }
    }
}
