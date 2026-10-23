package me.kozakov.orrery.compose

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.material3.Text
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.YearMonth
import me.kozakov.orrery.core.CalendarPages
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class InfinitePagingTest {
    @get:Rule
    val rule = createComposeRule()

    private lateinit var state: CalendarState
    private lateinit var scope: CoroutineScope

    private fun calendarContent(
        startMonth: YearMonth? = null,
        endMonth: YearMonth? = null,
    ) {
        rule.setContent {
            scope = rememberCoroutineScope()
            state =
                rememberCalendarState(
                    startMonth = startMonth,
                    endMonth = endMonth,
                    firstVisibleMonth = YearMonth(2026, 7),
                    firstDayOfWeek = DayOfWeek.MONDAY,
                )
            LazyRow(state = state.listState) {
                items(state.monthCount) { Box(Modifier.size(80.dp)) { Text("m$it") } }
            }
        }
    }

    @Test
    fun defaultStateIsUnbounded() {
        calendarContent()
        rule.runOnIdle {
            assertNull(state.startMonth)
            assertNull(state.endMonth)
            assertEquals(YearMonth(2026, 7), state.firstVisibleMonth)
        }
    }

    @Test
    fun scrollsCenturiesOutAndBack() {
        calendarContent()
        rule.waitForIdle()
        scope.launch { state.scrollToMonth(YearMonth(2526, 7)) }
        rule.runOnIdle { assertEquals(YearMonth(2526, 7), state.firstVisibleMonth) }
        scope.launch { state.scrollToMonth(YearMonth(1526, 7)) }
        rule.runOnIdle { assertEquals(YearMonth(1526, 7), state.firstVisibleMonth) }
    }

    @Test
    fun oneSidedStartClampsScroll() {
        calendarContent(startMonth = YearMonth(2026, 1))
        rule.waitForIdle()
        scope.launch { state.scrollToMonth(YearMonth(2020, 1)) }
        rule.runOnIdle { assertEquals(YearMonth(2026, 1), state.firstVisibleMonth) }
    }

    @Test
    fun updateRangeToUnboundedKeepsVisibleMonth() {
        calendarContent(startMonth = YearMonth(2026, 1), endMonth = YearMonth(2026, 12))
        rule.runOnIdle { state.updateRange(null, null) }
        rule.runOnIdle { assertEquals(YearMonth(2026, 7), state.firstVisibleMonth) }
    }

    @Test
    fun farAnimatedJumpLandsExactly() {
        calendarContent()
        rule.waitForIdle()
        scope.launch { state.animateScrollToMonth(YearMonth(2126, 7)) }
        rule.waitForIdle()
        rule.runOnIdle { assertEquals(YearMonth(2126, 7), state.firstVisibleMonth) }
    }

    @Test
    fun mixedBoundsSurviveRestoration() {
        val restorationTester = StateRestorationTester(rule)
        restorationTester.setContent {
            state =
                rememberCalendarState(
                    startMonth = YearMonth(2026, 1),
                    endMonth = null,
                    firstVisibleMonth = YearMonth(2026, 7),
                    firstDayOfWeek = DayOfWeek.MONDAY,
                )
            LazyRow(state = state.listState) {
                items(state.monthCount) { Box(Modifier.size(80.dp)) { Text("m$it") } }
            }
        }
        restorationTester.emulateSavedInstanceStateRestore()
        rule.runOnIdle {
            assertEquals(YearMonth(2026, 1), state.startMonth)
            assertNull(state.endMonth)
            assertEquals(YearMonth(2026, 7), state.firstVisibleMonth)
        }
    }

    private lateinit var weekState: WeekCalendarState

    private fun weekContent(
        startDate: LocalDate? = null,
        endDate: LocalDate? = null,
    ) {
        rule.setContent {
            scope = rememberCoroutineScope()
            weekState =
                rememberWeekCalendarState(
                    startDate = startDate,
                    endDate = endDate,
                    firstVisibleDate = LocalDate(2026, 7, 15),
                    firstDayOfWeek = DayOfWeek.MONDAY,
                )
            LazyRow(state = weekState.listState) {
                items(weekState.weekCount) { Box(Modifier.size(80.dp)) { Text("w$it") } }
            }
        }
    }

    @Test
    fun weekDefaultStateIsUnbounded() {
        weekContent()
        rule.runOnIdle {
            assertNull(weekState.startDate)
            assertNull(weekState.endDate)
        }
    }

    @Test
    fun weekScrollsCenturiesOutAndBack() {
        weekContent()
        rule.waitForIdle()
        scope.launch { weekState.scrollToDate(LocalDate(2526, 7, 15)) }
        rule.runOnIdle {
            assertEquals(
                CalendarPages.weekStart(LocalDate(2526, 7, 15), DayOfWeek.MONDAY),
                weekState.firstVisibleWeek.days
                    .first()
                    .date,
            )
        }
        scope.launch { weekState.scrollToDate(LocalDate(1526, 7, 15)) }
        rule.runOnIdle {
            assertEquals(
                CalendarPages.weekStart(LocalDate(1526, 7, 15), DayOfWeek.MONDAY),
                weekState.firstVisibleWeek.days
                    .first()
                    .date,
            )
        }
    }

    @Test
    fun weekOneSidedStartClamps() {
        weekContent(startDate = LocalDate(2026, 7, 1))
        rule.waitForIdle()
        scope.launch { weekState.scrollToDate(LocalDate(2020, 1, 1)) }
        rule.runOnIdle {
            assertEquals(
                CalendarPages.weekStart(LocalDate(2026, 7, 1), DayOfWeek.MONDAY),
                weekState.firstVisibleWeek.days
                    .first()
                    .date,
            )
        }
    }

    @Test
    fun weekFarAnimatedJumpLandsExactly() {
        weekContent()
        rule.waitForIdle()
        scope.launch { weekState.animateScrollToDate(LocalDate(2126, 7, 15)) }
        rule.waitForIdle()
        rule.runOnIdle {
            assertEquals(
                CalendarPages.weekStart(LocalDate(2126, 7, 15), DayOfWeek.MONDAY),
                weekState.firstVisibleWeek.days
                    .first()
                    .date,
            )
        }
    }
}
