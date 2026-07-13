package me.kozakov.orrery.compose

import androidx.compose.material3.Text
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.YearMonth
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CalendarsTest {
    @get:Rule
    val rule = createComposeRule()

    private lateinit var state: CalendarState
    private lateinit var scope: CoroutineScope

    private fun setHorizontal() {
        rule.setContent {
            scope = rememberCoroutineScope()
            state =
                rememberCalendarState(
                    startMonth = YearMonth(2026, 1),
                    endMonth = YearMonth(2026, 12),
                    firstVisibleMonth = YearMonth(2026, 7),
                    firstDayOfWeek = DayOfWeek.MONDAY,
                )
            HorizontalCalendar(
                state = state,
                monthHeader = { month -> Text(month.yearMonth.toString(), Modifier.testTag("header")) },
                dayContent = { day ->
                    Text(
                        text = day.date.day.toString(),
                        modifier = Modifier.testTag("day-${day.date}-${day.position.name}"),
                    )
                },
            )
        }
    }

    @Test
    fun horizontalShowsTheFirstVisibleMonth() {
        setHorizontal()
        rule.onNodeWithText("2026-07").assertIsDisplayed()
        // July 2026, Monday start: grid runs Jun 29 .. Aug 2
        rule.onNodeWithTag("day-2026-07-01-MonthDate").assertIsDisplayed()
        rule.onNodeWithTag("day-2026-06-29-InDate").assertIsDisplayed()
        rule.onNodeWithTag("day-2026-08-02-OutDate").assertIsDisplayed()
    }

    @Test
    fun horizontalScrollToMonthShowsThatMonth() {
        setHorizontal()
        scope.launch { state.scrollToMonth(YearMonth(2026, 2)) }
        rule.waitForIdle()
        rule.onNodeWithText("2026-02").assertIsDisplayed()
        assertEquals(YearMonth(2026, 2), state.firstVisibleMonth)
    }

    @Test
    fun verticalRendersMonthsInAColumn() {
        rule.setContent {
            state =
                rememberCalendarState(
                    startMonth = YearMonth(2026, 6),
                    endMonth = YearMonth(2026, 8),
                    firstVisibleMonth = YearMonth(2026, 6),
                    firstDayOfWeek = DayOfWeek.MONDAY,
                )
            VerticalCalendar(
                state = state,
                monthHeader = { month -> Text(month.yearMonth.toString(), Modifier.testTag("vheader")) },
                dayContent = { day -> Text(day.date.day.toString()) },
            )
        }
        rule.onNodeWithText("2026-06").assertIsDisplayed()
        // vertical list composes at least the first month's header
        assertTrue(rule.onAllNodesWithTag("vheader").fetchSemanticsNodes().isNotEmpty())
    }
}
