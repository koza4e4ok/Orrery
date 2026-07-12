package dev.koza4e4ok.material.calendar.compose

import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.requestFocus
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.YearMonth
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * July 2026 with Monday first-day: the grid's last row ends with the
 * out-dates Aug 1–2; August's grid starts with the out-dates Jul 27–31.
 * Right at column 6 moves to the next date (wrapping the row); the
 * calendar pages only when that date's month page differs.
 */
@RunWith(AndroidJUnit4::class)
class KeyboardNavigationTest {
    @get:Rule
    val rule = createComposeRule()

    private fun content(firstVisible: YearMonth): () -> CalendarState {
        lateinit var state: CalendarState
        rule.setContent {
            state =
                rememberCalendarState(
                    startMonth = YearMonth(2026, 7),
                    endMonth = YearMonth(2026, 8),
                    firstVisibleMonth = firstVisible,
                    firstDayOfWeek = DayOfWeek.MONDAY,
                )
            HorizontalCalendar(
                state = state,
                dayContent = { day ->
                    DefaultDay(
                        day = day,
                        modifier = Modifier.testTag("day-${day.date}"),
                        today = LocalDate(2026, 7, 15),
                        animateSelection = false,
                    )
                },
            )
        }
        return { state }
    }

    @Test
    fun rightArrowOnTheLastColumnWrapsToTheNextRow() {
        val state = content(firstVisible = YearMonth(2026, 7))
        rule.onNodeWithTag("day-2026-07-12").requestFocus()
        rule.onNodeWithTag("day-2026-07-12").performKeyInput { pressKey(Key.DirectionRight) }
        rule.waitForIdle()
        assertEquals(YearMonth(2026, 7), state().firstVisibleMonth)
        rule.onNodeWithTag("day-2026-07-13").assertIsFocused()
    }

    @Test
    fun rightArrowOnTheGridsLastCellPagesForward() {
        val state = content(firstVisible = YearMonth(2026, 7))
        rule.onNodeWithTag("day-2026-08-02").requestFocus()
        rule.onNodeWithTag("day-2026-08-02").performKeyInput { pressKey(Key.DirectionRight) }
        rule.waitForIdle()
        assertEquals(YearMonth(2026, 8), state().firstVisibleMonth)
    }

    @Test
    fun leftArrowOnTheGridsFirstCellPagesBackward() {
        val state = content(firstVisible = YearMonth(2026, 8))
        rule.onNodeWithTag("day-2026-07-27").requestFocus()
        rule.onNodeWithTag("day-2026-07-27").performKeyInput { pressKey(Key.DirectionLeft) }
        rule.waitForIdle()
        assertEquals(YearMonth(2026, 7), state().firstVisibleMonth)
    }

    @Test
    fun arrowsInsideTheMonthDoNotPage() {
        val state = content(firstVisible = YearMonth(2026, 7))
        rule.onNodeWithTag("day-2026-07-08").requestFocus()
        rule.onNodeWithTag("day-2026-07-08").performKeyInput { pressKey(Key.DirectionRight) }
        rule.waitForIdle()
        assertEquals(YearMonth(2026, 7), state().firstVisibleMonth)
    }
}
