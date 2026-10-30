package me.kozakov.orrery.compose

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.SaverScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertHeightIsEqualTo
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.requestFocus
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.YearMonth
import me.kozakov.orrery.core.OutDateStyle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CalendarFormatsTest {
    @get:Rule
    val rule = createComposeRule()

    private val workWeek =
        setOf(
            DayOfWeek.MONDAY,
            DayOfWeek.TUESDAY,
            DayOfWeek.WEDNESDAY,
            DayOfWeek.THURSDAY,
            DayOfWeek.FRIDAY,
        )

    @Test
    fun twoWeekCalendarShowsFourteenDaysAndPagesByTwoWeeks() {
        lateinit var state: WeekCalendarState
        lateinit var scope: CoroutineScope
        rule.setContent {
            scope = rememberCoroutineScope()
            state =
                rememberWeekCalendarState(
                    startDate = LocalDate(2026, 7, 6),
                    endDate = LocalDate(2026, 8, 30),
                    firstVisibleDate = LocalDate(2026, 7, 9),
                    firstDayOfWeek = DayOfWeek.MONDAY,
                    visibleWeeksCount = 2,
                )
            WeekCalendar(
                state = state,
                visibleWeeksCount = 2,
                dayContent = { day -> Text(day.date.toString(), Modifier.testTag("d-${day.date}")) },
            )
        }
        rule.waitForIdle()
        assertEquals(2, state.firstVisibleWeeks.size)
        // First 2-week block: Mon Jul 6 .. Sun Jul 19
        rule.onNodeWithTag("d-2026-07-06").assertIsDisplayed()
        rule.onNodeWithTag("d-2026-07-12").assertIsDisplayed()
        rule.onNodeWithTag("d-2026-07-13").assertIsDisplayed()
        rule.onNodeWithTag("d-2026-07-19").assertIsDisplayed()

        // Scroll to a date in the second 2-week block (Mon Jul 20 .. Sun Aug 2)
        rule.runOnIdle { scope.launch { state.scrollToDate(LocalDate(2026, 7, 29)) } }
        rule.waitForIdle()
        assertEquals(LocalDate(2026, 7, 20), state.firstVisibleWeek.days.first().date)
        rule.onNodeWithTag("d-2026-07-20").assertIsDisplayed()
        rule.onNodeWithTag("d-2026-08-02").assertIsDisplayed()
    }

    @Test
    fun workWeekMonthCalendarFiltersOutWeekendColumnsAndHeaders() {
        var headerDays: List<DayOfWeek> = emptyList()
        rule.setContent {
            val state =
                rememberCalendarState(
                    startMonth = YearMonth(2026, 7),
                    endMonth = YearMonth(2026, 8),
                    firstVisibleMonth = YearMonth(2026, 7),
                    firstDayOfWeek = DayOfWeek.MONDAY,
                    visibleDaysOfWeek = workWeek,
                )
            HorizontalCalendar(
                state = state,
                visibleDaysOfWeek = workWeek,
                weekHeader = { days ->
                    headerDays = days
                    CalendarDefaults.DaysOfWeekHeader(days)
                },
                dayContent = { day -> Text(day.date.toString(), Modifier.testTag("d-${day.date}")) },
            )
        }
        rule.waitForIdle()
        assertEquals(5, headerDays.size)
        assertEquals(DayOfWeek.MONDAY, headerDays.first())
        assertEquals(DayOfWeek.FRIDAY, headerDays.last())
        // Weekdays are displayed; Saturday Jul 4 and Sunday Jul 5 are not
        rule.onNodeWithTag("d-2026-07-03").assertIsDisplayed()
        rule.onNodeWithTag("d-2026-07-06").assertIsDisplayed()
        rule.onNodeWithTag("d-2026-07-04").assertDoesNotExist()
        rule.onNodeWithTag("d-2026-07-05").assertDoesNotExist()
    }

    @Test
    fun workWeekKeyboardNavigationSkipsWeekendAtRowEdge() {
        lateinit var state: CalendarState
        rule.setContent {
            state =
                rememberCalendarState(
                    startMonth = YearMonth(2026, 7),
                    endMonth = YearMonth(2026, 8),
                    firstVisibleMonth = YearMonth(2026, 7),
                    firstDayOfWeek = DayOfWeek.MONDAY,
                    visibleDaysOfWeek = workWeek,
                )
            HorizontalCalendar(
                state = state,
                visibleDaysOfWeek = workWeek,
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
        rule.waitForIdle()
        // Friday Jul 10 + Right arrow jumps over weekend to Monday Jul 13
        rule.onNodeWithTag("day-2026-07-10").requestFocus()
        rule.onNodeWithTag("day-2026-07-10").performKeyInput { pressKey(Key.DirectionRight) }
        rule.waitForIdle()
        rule.onNodeWithTag("day-2026-07-13").assertIsFocused()
    }

    @Test
    fun fixedRowHeightAppliesExactHeightToEachRow() {
        rule.setContent {
            val state =
                rememberCalendarState(
                    startMonth = YearMonth(2026, 7),
                    endMonth = YearMonth(2026, 7),
                    firstVisibleMonth = YearMonth(2026, 7),
                    firstDayOfWeek = DayOfWeek.MONDAY,
                )
            HorizontalCalendar(
                state = state,
                rowHeight = CalendarRowHeight.Fixed(64.dp),
                weekHeader = null,
                dayContent = { day ->
                    DefaultDay(
                        day = day,
                        modifier = Modifier.testTag("cell-${day.date}"),
                        today = LocalDate(2026, 7, 15),
                        animateSelection = false,
                    )
                },
            )
        }
        rule.waitForIdle()
        rule.onNodeWithTag("cell-2026-07-15").assertHeightIsEqualTo(64.dp)
    }

    @Test
    fun fillViewportDividesBoundedHeightAcrossMonthRows() {
        lateinit var state: CalendarState
        lateinit var scope: CoroutineScope
        rule.setContent {
            scope = rememberCoroutineScope()
            state =
                rememberCalendarState(
                    startMonth = YearMonth(2026, 7),
                    endMonth = YearMonth(2026, 8),
                    firstVisibleMonth = YearMonth(2026, 7),
                    firstDayOfWeek = DayOfWeek.MONDAY,
                )
            Box(Modifier.width(350.dp).height(300.dp)) {
                HorizontalCalendar(
                    state = state,
                    modifier = Modifier.fillMaxWidth().height(300.dp).testTag("cal"),
                    rowHeight = CalendarRowHeight.FillViewport(minRowHeight = 40.dp),
                    weekHeader = null,
                    dayContent = { day ->
                        DefaultDay(
                            day = day,
                            modifier = Modifier.testTag("fill-${day.date}-${day.position.name}"),
                            today = LocalDate(2026, 7, 15),
                            animateSelection = false,
                        )
                    },
                )
            }
        }
        rule.waitForIdle()
        // July 2026 has 5 rows -> 300.dp / 5 = 60.dp per row
        rule.onNodeWithTag("fill-2026-07-15-MonthDate").assertHeightIsEqualTo(60.dp)

        // August 2026 has 6 rows -> 300.dp / 6 = 50.dp per row, while total calendar height stays 300.dp
        rule.runOnIdle { scope.launch { state.scrollToMonth(YearMonth(2026, 8)) } }
        rule.waitForIdle()
        rule.onNodeWithTag("fill-2026-08-15-MonthDate").assertHeightIsEqualTo(50.dp)
        rule.onNodeWithTag("cal").assertHeightIsEqualTo(300.dp)
    }

    @Test
    fun verticalCalendarSupportsVisibleDaysAndFixedRowHeight() {
        rule.setContent {
            val state =
                rememberCalendarState(
                    startMonth = YearMonth(2026, 7),
                    endMonth = YearMonth(2026, 7),
                    firstVisibleMonth = YearMonth(2026, 7),
                    firstDayOfWeek = DayOfWeek.MONDAY,
                )
            VerticalCalendar(
                state = state,
                visibleDaysOfWeek = workWeek,
                rowHeight = CalendarRowHeight.Fixed(58.dp),
                dayContent = { day ->
                    DefaultDay(
                        day = day,
                        modifier = Modifier.testTag("v-${day.date}"),
                        today = LocalDate(2026, 7, 15),
                        animateSelection = false,
                    )
                },
            )
        }
        rule.waitForIdle()
        rule.onNodeWithTag("v-2026-07-15").assertHeightIsEqualTo(58.dp)
        rule.onNodeWithTag("v-2026-07-04").assertDoesNotExist()
    }

    @Test
    fun saversRoundTripNewFieldsAndTolerateLegacySavedLists() {
        val saverScope = SaverScope { true }
        val weekState =
            WeekCalendarState(
                startDate = LocalDate(2026, 7, 1),
                endDate = LocalDate(2026, 8, 31),
                firstVisibleDate = LocalDate(2026, 7, 6),
                firstDayOfWeek = DayOfWeek.MONDAY,
                visibleWeeksCount = 2,
                visibleDaysOfWeek = workWeek,
            )
        val savedWeek = with(WeekCalendarState.Saver) { saverScope.save(weekState) }!!
        val restoredWeek = WeekCalendarState.Saver.restore(savedWeek)!!
        assertEquals(2, restoredWeek.visibleWeeksCount)
        assertEquals(workWeek, restoredWeek.visibleDaysOfWeek)

        // Legacy 4-element list from 0.8.0 restores with defaults
        val legacyWeek =
            WeekCalendarState.Saver.restore(
                listOf("2026-07-01", "2026-08-31", "2026-07-06", DayOfWeek.MONDAY.ordinal),
            )!!
        assertEquals(1, legacyWeek.visibleWeeksCount)
        assertEquals(7, legacyWeek.visibleDaysOfWeek.size)

        val calState =
            CalendarState(
                startMonth = YearMonth(2026, 1),
                endMonth = YearMonth(2026, 12),
                firstVisibleMonth = YearMonth(2026, 7),
                firstDayOfWeek = DayOfWeek.MONDAY,
                outDateStyle = OutDateStyle.EndOfRow,
                visibleDaysOfWeek = workWeek,
            )
        val savedCal = with(CalendarState.Saver) { saverScope.save(calState) }!!
        val restoredCal = CalendarState.Saver.restore(savedCal)!!
        assertEquals(workWeek, restoredCal.visibleDaysOfWeek)
        assertTrue(restoredCal.firstVisibleMonth == YearMonth(2026, 7))
    }
}
