package me.kozakov.orrery.compose

import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.YearMonth
import me.kozakov.orrery.core.DisabledDates
import me.kozakov.orrery.core.Selection
import me.kozakov.orrery.core.SelectionEvent
import me.kozakov.orrery.core.SelectionMode
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** #102: Compose state guardrails and relative navigation helpers. */
@RunWith(AndroidJUnit4::class)
class RangeGuardrailsAndNavHelpersTest {
    @get:Rule
    val rule = createComposeRule()

    @Test
    fun selectionStateAppliesBookingGuardrails() {
        val events = mutableListOf<SelectionEvent>()
        lateinit var state: CalendarSelectionState
        rule.setContent {
            state =
                rememberCalendarSelectionState(
                    mode = SelectionMode.Range(),
                    disabled = DisabledDates { dates(LocalDate(2026, 7, 12)) },
                    onEvent = { events += it },
                    disallowDisabledInRange = true,
                    minRangeDays = 3,
                    maxRangeDays = 5,
                )
        }
        rule.waitForIdle()

        rule.runOnIdle { state.click(LocalDate(2026, 7, 10)) }
        assertEquals(Selection(rangeStart = LocalDate(2026, 7, 10)), state.selection)

        // Spanning disabled 2026-07-12 restarts at 2026-07-14
        rule.runOnIdle { state.click(LocalDate(2026, 7, 14)) }
        assertEquals(Selection(rangeStart = LocalDate(2026, 7, 14)), state.selection)

        // Shorter than minRangeDays=3 (2 days: 14..15) is ignored
        rule.runOnIdle { state.click(LocalDate(2026, 7, 15)) }
        assertEquals(Selection(rangeStart = LocalDate(2026, 7, 14)), state.selection)

        // Longer than maxRangeDays=5 (7 days: 14..20) restarts at 2026-07-20
        rule.runOnIdle { state.click(LocalDate(2026, 7, 20)) }
        assertEquals(Selection(rangeStart = LocalDate(2026, 7, 20)), state.selection)

        // Valid 3-day range (20..22) completes
        rule.runOnIdle { state.click(LocalDate(2026, 7, 22)) }
        assertEquals(LocalDate(2026, 7, 20)..LocalDate(2026, 7, 22), state.selection.range)
    }

    @Test
    fun calendarStateRelativeMonthNavigationStepsAndClamps() {
        lateinit var state: CalendarState
        lateinit var scope: CoroutineScope
        rule.setContent {
            scope = rememberCoroutineScope()
            state =
                rememberCalendarState(
                    startMonth = YearMonth(2026, 6),
                    endMonth = YearMonth(2026, 8),
                    firstVisibleMonth = YearMonth(2026, 7),
                    firstDayOfWeek = DayOfWeek.MONDAY,
                )
            HorizontalCalendar(state = state, dayContent = {})
        }
        rule.waitForIdle()
        assertEquals(YearMonth(2026, 7), state.firstVisibleMonth)

        rule.runOnIdle { scope.launch { state.scrollToNextMonth() } }
        rule.waitForIdle()
        assertEquals(YearMonth(2026, 8), state.firstVisibleMonth)

        // Clamped at endMonth
        rule.runOnIdle { scope.launch { state.scrollToNextMonth() } }
        rule.waitForIdle()
        assertEquals(YearMonth(2026, 8), state.firstVisibleMonth)

        rule.runOnIdle { scope.launch { state.scrollToPreviousMonth() } }
        rule.waitForIdle()
        assertEquals(YearMonth(2026, 7), state.firstVisibleMonth)
    }

    @Test
    fun weekCalendarStateRelativeWeekNavigationStepsByVisibleWeeksCount() {
        lateinit var state: WeekCalendarState
        lateinit var scope: CoroutineScope
        rule.setContent {
            scope = rememberCoroutineScope()
            state =
                rememberWeekCalendarState(
                    startDate = LocalDate(2026, 7, 6),
                    endDate = LocalDate(2026, 7, 26),
                    firstVisibleDate = LocalDate(2026, 7, 6),
                    firstDayOfWeek = DayOfWeek.MONDAY,
                )
            WeekCalendar(state = state, visibleWeeksCount = 1, dayContent = {})
        }
        rule.waitForIdle()
        val firstDay = state.firstVisibleWeek.days.first()
        assertEquals(LocalDate(2026, 7, 6), firstDay.date)

        rule.runOnIdle { scope.launch { state.scrollToNextWeek() } }
        rule.waitForIdle()
        val nextDay = state.firstVisibleWeek.days.first()
        assertEquals(LocalDate(2026, 7, 13), nextDay.date)

        rule.runOnIdle { scope.launch { state.scrollToPreviousWeek() } }
        rule.waitForIdle()
        val prevDay = state.firstVisibleWeek.days.first()
        assertEquals(LocalDate(2026, 7, 6), prevDay.date)
    }

    @Test
    fun scheduleStateRelativeNavigationStepsPagesAndDays() {
        lateinit var state: ScheduleState
        lateinit var scope: CoroutineScope
        rule.setContent {
            scope = rememberCoroutineScope()
            state =
                rememberScheduleState(
                    startDate = LocalDate(2026, 7, 10),
                    endDate = LocalDate(2026, 7, 15),
                    firstVisibleDate = LocalDate(2026, 7, 10),
                )
        }
        rule.waitForIdle()

        rule.runOnIdle { scope.launch { state.scrollToNextPage() } }
        rule.waitForIdle()
        assertEquals(LocalDate(2026, 7, 11), state.firstVisibleDate)

        rule.runOnIdle { scope.launch { state.scrollToPreviousPage() } }
        rule.waitForIdle()
        assertEquals(LocalDate(2026, 7, 10), state.firstVisibleDate)
    }
}
