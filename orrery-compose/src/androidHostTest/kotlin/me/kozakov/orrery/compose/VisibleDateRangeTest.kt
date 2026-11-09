package me.kozakov.orrery.compose

import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.YearMonth
import me.kozakov.orrery.core.OutDateStyle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** [visibleDateRange] and the three [visibleDateRangeFlow]s (#87). */
@RunWith(AndroidJUnit4::class)
class VisibleDateRangeTest {
    @get:Rule
    val rule = createComposeRule()

    private lateinit var scope: CoroutineScope

    private fun date(
        month: Int,
        day: Int,
    ) = LocalDate(2026, month, day)

    // ---- CalendarState ----

    private fun setMonthPager(
        startMonth: YearMonth? = YearMonth(2025, 1),
        endMonth: YearMonth? = YearMonth(2027, 12),
        outDateStyle: OutDateStyle = OutDateStyle.EndOfRow,
        prefetchMonths: Int = 1,
        emissions: MutableList<ClosedRange<LocalDate>> = mutableListOf(),
    ): CalendarState {
        lateinit var state: CalendarState
        rule.setContent {
            state =
                rememberCalendarState(
                    startMonth = startMonth,
                    endMonth = endMonth,
                    firstVisibleMonth = YearMonth(2026, 2),
                    firstDayOfWeek = DayOfWeek.MONDAY,
                    outDateStyle = outDateStyle,
                )
            scope = rememberCoroutineScope()
            LaunchedEffect(state) { state.visibleDateRangeFlow(prefetchMonths).collect { emissions += it } }
            HorizontalCalendar(
                state = state,
                modifier = Modifier.testTag("pager"),
                dayContent = { day -> DefaultDay(day = day, animateSelection = false) },
            )
        }
        rule.waitForIdle()
        return state
    }

    @Test
    fun monthPagerEmitsTheVisibleGridWidenedByOneMonth() {
        val emissions = mutableListOf<ClosedRange<LocalDate>>()
        val state = setMonthPager(emissions = emissions)
        // February 2026 starts on a Sunday and ends on a Saturday.
        assertEquals(date(1, 26)..date(3, 1), state.visibleDateRange)
        assertEquals(listOf(date(1, 1)..date(3, 31)), emissions)
    }

    @Test
    fun scrollingEmitsOnceAndRepeatedScrollsAreDeduplicated() {
        val emissions = mutableListOf<ClosedRange<LocalDate>>()
        val state = setMonthPager(emissions = emissions)
        rule.runOnIdle { scope.launch { state.scrollToMonth(YearMonth(2026, 3)) } }
        rule.waitForIdle()
        rule.runOnIdle { scope.launch { state.scrollToMonth(YearMonth(2026, 3)) } }
        rule.waitForIdle()
        assertEquals(date(2, 23)..date(4, 5), state.visibleDateRange)
        assertEquals(listOf(date(1, 1)..date(3, 31), date(2, 1)..date(4, 30)), emissions)
    }

    @Test
    fun prefetchZeroEmitsExactlyTheVisibleRange() {
        val emissions = mutableListOf<ClosedRange<LocalDate>>()
        val state = setMonthPager(prefetchMonths = 0, emissions = emissions)
        assertEquals(listOf(state.visibleDateRange), emissions)
    }

    @Test
    fun prefetchIsClampedToTheMonthBounds() {
        val emissions = mutableListOf<ClosedRange<LocalDate>>()
        setMonthPager(
            startMonth = YearMonth(2026, 2),
            endMonth = YearMonth(2026, 2),
            outDateStyle = OutDateStyle.None,
            prefetchMonths = 3,
            emissions = emissions,
        )
        assertEquals(listOf(date(2, 1)..date(2, 28)), emissions)
    }

    @Test
    fun prefetchNeverDropsPaintedInAndOutDates() {
        val emissions = mutableListOf<ClosedRange<LocalDate>>()
        setMonthPager(startMonth = YearMonth(2026, 2), endMonth = YearMonth(2026, 2), emissions = emissions)
        assertEquals(listOf(date(1, 26)..date(3, 1)), emissions)
    }

    @Test
    fun endOfGridCoversSixRows() {
        val state = setMonthPager(outDateStyle = OutDateStyle.EndOfGrid)
        assertEquals(date(1, 26)..date(3, 8), state.visibleDateRange)
    }

    @Test
    fun unboundedPagerWidensWithoutClamping() {
        val emissions = mutableListOf<ClosedRange<LocalDate>>()
        setMonthPager(startMonth = null, endMonth = null, prefetchMonths = 2, emissions = emissions)
        assertEquals(listOf(LocalDate(2025, 12, 1)..date(4, 30)), emissions)
    }

    @Test
    fun midSwipeCoversBothMonths() {
        val state = setMonthPager()
        rule.onNodeWithTag("pager").performTouchInput {
            down(center)
            moveBy(Offset(-viewConfiguration.touchSlop - 1f, 0f))
            moveBy(Offset(-width / 3f, 0f))
        }
        rule.waitForIdle()
        assertEquals(date(1, 26)..date(4, 5), state.visibleDateRange)
        rule.onNodeWithTag("pager").performTouchInput { up() }
        rule.waitForIdle()
    }

    @Test
    fun verticalCalendarCoversEveryMonthOnScreen() {
        lateinit var state: CalendarState
        rule.setContent {
            state =
                rememberCalendarState(
                    startMonth = YearMonth(2026, 1),
                    endMonth = YearMonth(2026, 12),
                    firstVisibleMonth = YearMonth(2026, 2),
                    firstDayOfWeek = DayOfWeek.MONDAY,
                )
            VerticalCalendar(
                state = state,
                modifier = Modifier.height(420.dp),
                dayContent = { day -> Text(day.date.day.toString(), Modifier.height(24.dp)) },
            )
        }
        rule.waitForIdle()
        val range = state.visibleDateRange
        assertEquals(date(1, 26), range.start)
        // February (5 rows) leaves room for at least the top of March in 420.dp.
        assertTrue("range $range", range.endInclusive >= date(4, 5))
    }

    @Test
    fun stickyHeadersCountTwoItemsPerMonth() {
        lateinit var state: CalendarState
        rule.setContent {
            state =
                rememberCalendarState(
                    startMonth = YearMonth(2026, 1),
                    endMonth = YearMonth(2026, 12),
                    firstVisibleMonth = YearMonth(2026, 6),
                    firstDayOfWeek = DayOfWeek.MONDAY,
                )
            VerticalCalendar(
                state = state,
                modifier = Modifier.height(100.dp),
                stickyMonthHeaders = true,
                monthHeader = { Text(it.yearMonth.toString()) },
                dayContent = { day -> Text(day.date.day.toString(), Modifier.height(24.dp)) },
            )
        }
        rule.waitForIdle()
        assertEquals(2, state.itemsPerMonth)
        // June 2026 starts on a Monday; 100.dp shows only June.
        assertEquals(date(6, 1), state.visibleDateRange.start)
        assertEquals(date(7, 5), state.visibleDateRange.endInclusive)
    }

    @Test
    fun negativePrefetchIsRejected() {
        val state = setMonthPager()
        assertThrows(IllegalArgumentException::class.java) { state.visibleDateRangeFlow(prefetchMonths = -1) }
    }

    // ---- WeekCalendarState ----

    private fun setWeekStrip(
        startDate: LocalDate? = null,
        endDate: LocalDate? = null,
        visibleWeeksCount: Int = 1,
        visibleDaysOfWeek: Set<DayOfWeek> = DayOfWeek.entries.toSet(),
        prefetchWeeks: Int = 1,
        emissions: MutableList<ClosedRange<LocalDate>> = mutableListOf(),
    ): WeekCalendarState {
        lateinit var state: WeekCalendarState
        rule.setContent {
            state =
                rememberWeekCalendarState(
                    startDate = startDate,
                    endDate = endDate,
                    firstVisibleDate = date(7, 15),
                    firstDayOfWeek = DayOfWeek.MONDAY,
                    visibleWeeksCount = visibleWeeksCount,
                    visibleDaysOfWeek = visibleDaysOfWeek,
                )
            scope = rememberCoroutineScope()
            LaunchedEffect(state) { state.visibleDateRangeFlow(prefetchWeeks).collect { emissions += it } }
            WeekCalendar(
                state = state,
                visibleWeeksCount = visibleWeeksCount,
                visibleDaysOfWeek = visibleDaysOfWeek,
                dayContent = { day -> DefaultDay(day = day, animateSelection = false) },
            )
        }
        rule.waitForIdle()
        return state
    }

    @Test
    fun weekStripEmitsTheVisibleWeekWidenedByOneWeek() {
        val emissions = mutableListOf<ClosedRange<LocalDate>>()
        val state = setWeekStrip(emissions = emissions)
        assertEquals(date(7, 13)..date(7, 19), state.visibleDateRange)
        assertEquals(listOf(date(7, 6)..date(7, 26)), emissions)
        rule.runOnIdle { scope.launch { state.scrollToDate(date(7, 22)) } }
        rule.waitForIdle()
        rule.runOnIdle { scope.launch { state.scrollToDate(date(7, 23)) } } // same week
        rule.waitForIdle()
        assertEquals(listOf(date(7, 6)..date(7, 26), date(7, 13)..date(8, 2)), emissions)
    }

    @Test
    fun weekStripPrefetchZeroIsExact() {
        val emissions = mutableListOf<ClosedRange<LocalDate>>()
        setWeekStrip(prefetchWeeks = 0, emissions = emissions)
        assertEquals(listOf(date(7, 13)..date(7, 19)), emissions)
    }

    @Test
    fun twoWeekPagesCoverBothRowsAndClampToTheStartWeek() {
        val emissions = mutableListOf<ClosedRange<LocalDate>>()
        val state = setWeekStrip(startDate = date(7, 8), visibleWeeksCount = 2, emissions = emissions)
        // Pages start at the week of startDate (6 Jul): 6-19 Jul holds the 15th.
        assertEquals(date(7, 6)..date(7, 19), state.visibleDateRange)
        assertEquals(listOf(date(7, 6)..date(7, 26)), emissions)
    }

    @Test
    fun hiddenWeekdaysDoNotNarrowTheRange() {
        val workWeek = DayOfWeek.entries.take(5).toSet()
        val state = setWeekStrip(visibleDaysOfWeek = workWeek)
        assertEquals(date(7, 13)..date(7, 19), state.visibleDateRange)
    }

    @Test
    fun weekPrefetchIsClampedToTheEndWeek() {
        val emissions = mutableListOf<ClosedRange<LocalDate>>()
        setWeekStrip(endDate = date(7, 17), prefetchWeeks = 2, emissions = emissions)
        assertEquals(listOf(date(6, 29)..date(7, 19)), emissions)
    }

    @Test
    fun negativeWeekPrefetchIsRejected() {
        val state = setWeekStrip()
        assertThrows(IllegalArgumentException::class.java) { state.visibleDateRangeFlow(prefetchWeeks = -1) }
    }

    // ---- CollapsibleCalendarState ----

    private class Collapsible(
        val state: CollapsibleCalendarState,
        val calendarState: CalendarState,
        val weekState: WeekCalendarState,
    )

    private fun setCollapsible(
        firstVisibleDate: LocalDate = date(7, 15),
        prefetchMonths: Int = 0,
        prefetchWeeks: Int = 0,
        emissions: MutableList<ClosedRange<LocalDate>> = mutableListOf(),
    ): Collapsible {
        lateinit var states: Collapsible
        rule.setContent {
            val calendarState =
                rememberCalendarState(
                    startMonth = YearMonth(2026, 1),
                    endMonth = YearMonth(2026, 12),
                    firstVisibleMonth = YearMonth(2026, 7),
                    firstDayOfWeek = DayOfWeek.MONDAY,
                )
            val weekState =
                rememberWeekCalendarState(
                    startDate = date(1, 1),
                    endDate = date(12, 31),
                    firstVisibleDate = firstVisibleDate,
                    firstDayOfWeek = DayOfWeek.MONDAY,
                )
            val state = rememberCollapsibleCalendarState(initialExpanded = true)
            states = Collapsible(state, calendarState, weekState)
            scope = rememberCoroutineScope()
            LaunchedEffect(state) {
                state
                    .visibleDateRangeFlow(calendarState, weekState, prefetchMonths, prefetchWeeks)
                    .collect { emissions += it }
            }
            CollapsibleCalendarScaffold(
                calendarState = calendarState,
                weekState = weekState,
                state = state,
                dayContent = { day -> DefaultDay(day = day, animateSelection = false) },
            ) {}
        }
        rule.waitForIdle()
        return states
    }

    // July 2026 (Monday first): rows start 29 Jun, 6, 13, 20 and 27 Jul.
    private val julyGrid = date(6, 29)..date(8, 2)

    @Test
    fun collapsibleMorphsFromMonthToTwoWeeksToWeek() {
        val emissions = mutableListOf<ClosedRange<LocalDate>>()
        val states = setCollapsible(emissions = emissions)
        assertEquals(listOf(julyGrid), emissions)
        rule.runOnIdle { scope.launch { states.state.animateTo(CollapsibleCalendarAnchor.TwoWeeks) } }
        rule.waitForIdle()
        assertEquals(date(7, 13)..date(7, 26), emissions.last())
        rule.runOnIdle { scope.launch { states.state.collapse() } }
        rule.waitForIdle()
        assertEquals(date(7, 13)..date(7, 19), emissions.last())
        rule.runOnIdle { scope.launch { states.state.expand() } }
        rule.waitForIdle()
        assertEquals(julyGrid, emissions.last())
        // Each settle emits at most once per stop: Month, TwoWeeks, Week, (TwoWeeks,) Month.
        assertTrue("emissions $emissions", emissions.size <= 5)
        assertEquals(emissions.size, emissions.zipWithNext().count { (a, b) -> a != b } + 1)
    }

    @Test
    fun collapsedDirectlyFromMonthEmitsTheWeek() {
        val emissions = mutableListOf<ClosedRange<LocalDate>>()
        val states = setCollapsible(prefetchWeeks = 1, prefetchMonths = 1, emissions = emissions)
        assertEquals(date(6, 1)..date(8, 31), emissions.first())
        rule.runOnIdle { scope.launch { states.state.collapse() } }
        rule.waitForIdle()
        assertEquals(date(7, 6)..date(7, 26), emissions.last())
        assertTrue("emissions $emissions", emissions.size <= 3)
    }

    @Test
    fun intermediateFractionsPickTheSupersetStop() {
        val states = setCollapsible()
        val ranges = mutableListOf<ClosedRange<LocalDate>>()
        for (fraction in listOf(0.2f, 0.35f, 0.5f, 0f)) {
            rule.runOnIdle { states.state.snapTo(fraction) }
            rule.waitForIdle()
            ranges += states.state.prefetchedDateRange(states.calendarState, states.weekState, 0, 0)
        }
        assertEquals(
            listOf(date(7, 13)..date(7, 26), date(7, 13)..date(7, 26), julyGrid, date(7, 13)..date(7, 19)),
            ranges,
        )
    }

    @Test
    fun twoWeeksOnTheLastRowIncludesThePreviousRow() {
        val states = setCollapsible(firstVisibleDate = date(7, 29))
        rule.runOnIdle { scope.launch { states.state.animateTo(CollapsibleCalendarAnchor.TwoWeeks) } }
        rule.waitForIdle()
        assertEquals(
            date(7, 20)..date(8, 2),
            states.state.prefetchedDateRange(states.calendarState, states.weekState, 0, 0),
        )
    }

    @Test
    fun pagingReEmitsWhileExpandedAndWhileCollapsed() {
        val emissions = mutableListOf<ClosedRange<LocalDate>>()
        val states = setCollapsible(emissions = emissions)
        rule.runOnIdle { scope.launch { states.calendarState.scrollToMonth(YearMonth(2026, 8)) } }
        rule.waitForIdle()
        // August 2026 starts on a Saturday and ends on a Monday.
        assertEquals(date(7, 27)..date(9, 6), emissions.last())
        rule.runOnIdle { scope.launch { states.state.collapse() } }
        rule.waitForIdle()
        val collapsed = emissions.last()
        rule.runOnIdle { scope.launch { states.weekState.scrollToDate(collapsed.endInclusive.shiftDays(7)) } }
        rule.waitForIdle()
        assertEquals(collapsed.start.shiftDays(7)..collapsed.endInclusive.shiftDays(7), emissions.last())
    }

    @Test
    fun negativeCollapsiblePrefetchIsRejected() {
        val states = setCollapsible()
        assertThrows(IllegalArgumentException::class.java) {
            states.state.visibleDateRangeFlow(states.calendarState, states.weekState, prefetchMonths = -1)
        }
        assertThrows(IllegalArgumentException::class.java) {
            states.state.visibleDateRangeFlow(states.calendarState, states.weekState, prefetchWeeks = -1)
        }
    }
}
