package dev.koza4e4ok.material.calendar.compose

import androidx.compose.material3.Text
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.YearMonth
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class StickyHeadersTest {
    @get:Rule
    val rule = createComposeRule()

    @Test
    fun stickyHeadersRenderAndScroll() {
        lateinit var state: CalendarState
        lateinit var scope: CoroutineScope
        rule.setContent {
            scope = rememberCoroutineScope()
            state =
                rememberCalendarState(
                    startMonth = YearMonth(2026, 6),
                    endMonth = YearMonth(2026, 12),
                    firstVisibleMonth = YearMonth(2026, 6),
                    firstDayOfWeek = DayOfWeek.MONDAY,
                )
            VerticalCalendar(
                state = state,
                stickyMonthHeaders = true,
                monthHeader = { month -> Text(month.yearMonth.toString(), Modifier.testTag("sticky")) },
                dayContent = { day -> Text(day.date.toString()) },
            )
        }
        rule.onNodeWithText("2026-06").assertIsDisplayed()
        scope.launch { state.scrollToMonth(YearMonth(2026, 8)) }
        rule.waitForIdle()
        rule.onNodeWithText("2026-08").assertIsDisplayed()
        assertEquals(YearMonth(2026, 8), state.firstVisibleMonth)
    }
}
