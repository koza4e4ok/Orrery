package me.kozakov.orrery.compose

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.YearMonth
import me.kozakov.orrery.core.CalendarEvent
import me.kozakov.orrery.core.DisabledDates
import me.kozakov.orrery.core.Selection
import me.kozakov.orrery.core.SelectionEvent
import me.kozakov.orrery.core.SelectionMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
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
    fun selectionStateDragAndExtendAllowDisabledInsideByDefault() {
        val events = mutableListOf<SelectionEvent>()
        lateinit var state: CalendarSelectionState
        rule.setContent {
            state =
                rememberCalendarSelectionState(
                    mode = SelectionMode.Range(),
                    disabled = DisabledDates { dates(LocalDate(2026, 7, 12)) },
                    onEvent = { events += it },
                )
        }
        rule.runOnIdle {
            state.dragStart(LocalDate(2026, 7, 10))
            state.dragUpdate(LocalDate(2026, 7, 14))
            state.dragEnd()
        }
        assertEquals(LocalDate(2026, 7, 10)..LocalDate(2026, 7, 14), state.selection.range)

        rule.runOnIdle { state.extendRange(LocalDate(2026, 7, 10), LocalDate(2026, 7, 15)) }
        assertEquals(LocalDate(2026, 7, 10)..LocalDate(2026, 7, 15), state.selection.range)
        assertTrue(events.isEmpty())

        // A disabled endpoint is still rejected
        rule.runOnIdle { state.extendRange(LocalDate(2026, 7, 10), LocalDate(2026, 7, 12)) }
        assertEquals(Selection(rangeStart = LocalDate(2026, 7, 10)), state.selection)
        assertEquals(listOf<SelectionEvent>(SelectionEvent.Intercepted(LocalDate(2026, 7, 12))), events)
    }

    @Test
    fun selectionStateDragAndExtendRejectDisabledInsideWhenDisallowed() {
        val events = mutableListOf<SelectionEvent>()
        lateinit var state: CalendarSelectionState
        rule.setContent {
            state =
                rememberCalendarSelectionState(
                    mode = SelectionMode.Range(),
                    disabled = DisabledDates { dates(LocalDate(2026, 7, 12)) },
                    onEvent = { events += it },
                    disallowDisabledInRange = true,
                    maxRangeDays = 4,
                )
        }
        rule.runOnIdle {
            state.dragStart(LocalDate(2026, 7, 10))
            state.dragUpdate(LocalDate(2026, 7, 14))
            state.dragEnd()
        }
        assertEquals(Selection(rangeStart = LocalDate(2026, 7, 10)), state.selection)

        // 7 days (13..19) is over maxRangeDays = 4
        rule.runOnIdle { state.extendRange(LocalDate(2026, 7, 13), LocalDate(2026, 7, 19)) }
        assertEquals(Selection(rangeStart = LocalDate(2026, 7, 13)), state.selection)
        assertEquals(
            listOf(
                SelectionEvent.Intercepted(LocalDate(2026, 7, 12)),
                SelectionEvent.RangeTooLong(LocalDate(2026, 7, 19), 4),
            ),
            events,
        )
    }

    @Test
    fun weekCalendarStateRelativeWeekNavigationStepsOneWeek() {
        assertWeekNavigation(
            visibleWeeksCount = 1,
            endDate = LocalDate(2026, 7, 26),
            pageStarts = listOf(LocalDate(2026, 7, 6), LocalDate(2026, 7, 13), LocalDate(2026, 7, 20)),
        )
    }

    @Test
    fun weekCalendarStateRelativeWeekNavigationStepsTwoWeeks() {
        assertWeekNavigation(
            visibleWeeksCount = 2,
            endDate = LocalDate(2026, 8, 16),
            pageStarts = listOf(LocalDate(2026, 7, 6), LocalDate(2026, 7, 20), LocalDate(2026, 8, 3)),
        )
    }

    /** Steps through three pages starting on [pageStarts] (the last one at endDate) and back. */
    private fun assertWeekNavigation(
        visibleWeeksCount: Int,
        endDate: LocalDate,
        pageStarts: List<LocalDate>,
    ) {
        lateinit var state: WeekCalendarState
        lateinit var scope: CoroutineScope
        rule.setContent {
            scope = rememberCoroutineScope()
            state =
                rememberWeekCalendarState(
                    startDate = pageStarts.first(),
                    endDate = endDate,
                    firstVisibleDate = pageStarts.first(),
                    firstDayOfWeek = DayOfWeek.MONDAY,
                    visibleWeeksCount = visibleWeeksCount,
                )
            WeekCalendar(state = state, visibleWeeksCount = visibleWeeksCount, dayContent = {})
        }

        fun firstVisible(): LocalDate =
            rule.runOnIdle {
                state.firstVisibleWeek.days
                    .first()
                    .date
            }

        fun step(block: suspend WeekCalendarState.() -> Unit) {
            rule.runOnIdle { scope.launch { state.block() } }
            rule.waitForIdle()
        }
        assertEquals(pageStarts[0], firstVisible())

        step { scrollToNextWeek() }
        assertEquals(pageStarts[1], firstVisible())

        step { animateScrollToNextWeek() }
        assertEquals(pageStarts[2], firstVisible())

        // Clamped on the last page
        step { animateScrollToNextWeek() }
        assertEquals(pageStarts[2], firstVisible())
        step { scrollToNextWeek() }
        assertEquals(pageStarts[2], firstVisible())

        step { animateScrollToPreviousWeek() }
        assertEquals(pageStarts[1], firstVisible())

        step { scrollToPreviousWeek() }
        assertEquals(pageStarts[0], firstVisible())

        // Clamped on the first page
        step { animateScrollToPreviousWeek() }
        assertEquals(pageStarts[0], firstVisible())
    }

    @Test
    fun scheduleStateRelativeNavigationStepsDaysInDaySchedule() {
        lateinit var state: ScheduleState
        lateinit var scope: CoroutineScope
        rule.setContent {
            scope = rememberCoroutineScope()
            state =
                rememberScheduleState(
                    startDate = LocalDate(2026, 7, 10),
                    endDate = LocalDate(2026, 7, 13),
                    firstVisibleDate = LocalDate(2026, 7, 10),
                )
            DaySchedule(
                events = { emptyList<CalendarEvent<Nothing>>() },
                modifier = Modifier.fillMaxWidth().height(400.dp),
                state = state,
                today = LocalDate(2026, 7, 10),
            )
        }

        fun step(block: suspend ScheduleState.() -> Unit) {
            rule.runOnIdle { scope.launch { state.block() } }
            rule.waitForIdle()
        }

        step { scrollToNextPage() }
        assertEquals(LocalDate(2026, 7, 11), state.firstVisibleDate)

        step { animateScrollToNextPage() }
        assertEquals(LocalDate(2026, 7, 12), state.firstVisibleDate)

        step { animateScrollToNextDay() }
        assertEquals(LocalDate(2026, 7, 13), state.firstVisibleDate)

        // Clamped at endDate
        step { animateScrollToNextDay() }
        assertEquals(LocalDate(2026, 7, 13), state.firstVisibleDate)
        step { scrollToNextPage() }
        assertEquals(LocalDate(2026, 7, 13), state.firstVisibleDate)

        step { animateScrollToPreviousDay() }
        assertEquals(LocalDate(2026, 7, 12), state.firstVisibleDate)

        step { scrollToPreviousPage() }
        assertEquals(LocalDate(2026, 7, 11), state.firstVisibleDate)

        step { animateScrollToPreviousPage() }
        assertEquals(LocalDate(2026, 7, 10), state.firstVisibleDate)

        // Clamped at startDate
        step { animateScrollToPreviousDay() }
        assertEquals(LocalDate(2026, 7, 10), state.firstVisibleDate)
    }

    @Test
    fun scheduleStateRelativeNavigationStepsWeeksInWeekSchedule() {
        lateinit var state: ScheduleState
        lateinit var scope: CoroutineScope
        rule.setContent {
            scope = rememberCoroutineScope()
            state =
                rememberScheduleState(
                    startDate = LocalDate(2026, 7, 6),
                    endDate = LocalDate(2026, 7, 26),
                    firstVisibleDate = LocalDate(2026, 7, 6),
                    firstDayOfWeek = DayOfWeek.MONDAY,
                )
            WeekSchedule(
                events = { emptyList<CalendarEvent<Nothing>>() },
                modifier = Modifier.fillMaxWidth().height(400.dp),
                state = state,
                today = LocalDate(2026, 7, 6),
            )
        }

        fun step(block: suspend ScheduleState.() -> Unit) {
            rule.runOnIdle { scope.launch { state.block() } }
            rule.waitForIdle()
        }

        // Pages are 7 days: Jul 6, Jul 13 and Jul 20
        step { scrollToNextPage() }
        assertEquals(LocalDate(2026, 7, 13), state.firstVisibleDate)

        step { animateScrollToNextPage() }
        assertEquals(LocalDate(2026, 7, 20), state.firstVisibleDate)

        // Clamped on the last page
        step { animateScrollToNextPage() }
        assertEquals(LocalDate(2026, 7, 20), state.firstVisibleDate)
        step { scrollToNextPage() }
        assertEquals(LocalDate(2026, 7, 20), state.firstVisibleDate)

        // One day back crosses into the previous week's page
        step { animateScrollToPreviousDay() }
        assertEquals(LocalDate(2026, 7, 19), state.firstVisibleDate)

        // Page stepping goes from the page containing Jul 19 (Jul 13) to Jul 6
        step { animateScrollToPreviousPage() }
        assertEquals(LocalDate(2026, 7, 6), state.firstVisibleDate)

        // Clamped on the first page
        step { scrollToPreviousPage() }
        assertEquals(LocalDate(2026, 7, 6), state.firstVisibleDate)

        step { animateScrollToNextDay() }
        assertEquals(LocalDate(2026, 7, 7), state.firstVisibleDate)

        // Next page from Jul 7 starts the next week
        step { scrollToNextPage() }
        assertEquals(LocalDate(2026, 7, 13), state.firstVisibleDate)
    }

    @Test
    fun dateRangePickerSetSelectionAllowsDisabledDatesInsideByDefault() {
        lateinit var state: OrreryDateRangePickerState
        rule.setContent {
            state =
                rememberOrreryDateRangePickerState(
                    disabledDates = DisabledDates { dates(LocalDate(2026, 7, 10)) },
                )
        }
        rule.runOnIdle { state.setSelection(LocalDate(2026, 7, 8), LocalDate(2026, 7, 12)) }
        rule.runOnIdle {
            assertEquals(LocalDate(2026, 7, 8), state.selectedStartDate)
            assertEquals(LocalDate(2026, 7, 12), state.selectedEndDate)
        }

        // A disabled end is rejected and keeps the previous range
        rule.runOnIdle { state.setSelection(LocalDate(2026, 7, 6), LocalDate(2026, 7, 10)) }
        rule.runOnIdle {
            assertEquals(LocalDate(2026, 7, 8), state.selectedStartDate)
            assertEquals(LocalDate(2026, 7, 12), state.selectedEndDate)
        }
    }

    @Test
    fun dateRangePickerSetSelectionRejectsDisabledDatesInsideWhenDisallowed() {
        lateinit var state: OrreryDateRangePickerState
        rule.setContent {
            state =
                rememberOrreryDateRangePickerState(
                    disabledDates = DisabledDates { dates(LocalDate(2026, 7, 10)) },
                    disallowDisabledInRange = true,
                )
        }
        rule.runOnIdle { state.setSelection(LocalDate(2026, 7, 8), LocalDate(2026, 7, 12)) }
        rule.runOnIdle {
            assertNull(state.selectedStartDate)
            assertNull(state.selectedEndDate)
        }

        rule.runOnIdle { state.setSelection(LocalDate(2026, 7, 11), LocalDate(2026, 7, 14)) }
        rule.runOnIdle {
            assertEquals(LocalDate(2026, 7, 11), state.selectedStartDate)
            assertEquals(LocalDate(2026, 7, 14), state.selectedEndDate)
        }
    }

    @Test
    fun dateRangePickerSetSelectionEnforcesMinRangeDays() {
        lateinit var state: OrreryDateRangePickerState
        rule.setContent { state = rememberOrreryDateRangePickerState(minRangeDays = 3) }
        // 2 days (8..9) is too short
        rule.runOnIdle { state.setSelection(LocalDate(2026, 7, 8), LocalDate(2026, 7, 9)) }
        rule.runOnIdle {
            assertNull(state.selectedStartDate)
            assertNull(state.selectedEndDate)
        }

        rule.runOnIdle { state.setSelection(LocalDate(2026, 7, 8), LocalDate(2026, 7, 10)) }
        rule.runOnIdle {
            assertEquals(LocalDate(2026, 7, 8), state.selectedStartDate)
            assertEquals(LocalDate(2026, 7, 10), state.selectedEndDate)
        }
    }

    @Test
    fun dateRangePickerSetSelectionEnforcesMaxRangeDays() {
        lateinit var state: OrreryDateRangePickerState
        rule.setContent { state = rememberOrreryDateRangePickerState(maxRangeDays = 5) }
        // 6 days (8..13) is too long
        rule.runOnIdle { state.setSelection(LocalDate(2026, 7, 8), LocalDate(2026, 7, 13)) }
        rule.runOnIdle {
            assertNull(state.selectedStartDate)
            assertNull(state.selectedEndDate)
        }

        rule.runOnIdle { state.setSelection(LocalDate(2026, 7, 8), LocalDate(2026, 7, 12)) }
        rule.runOnIdle {
            assertEquals(LocalDate(2026, 7, 8), state.selectedStartDate)
            assertEquals(LocalDate(2026, 7, 12), state.selectedEndDate)
        }
    }
}
