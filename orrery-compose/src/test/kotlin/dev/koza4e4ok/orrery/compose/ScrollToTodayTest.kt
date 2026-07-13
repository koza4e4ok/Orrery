package dev.koza4e4ok.orrery.compose

import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.datetime.YearMonth
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ScrollToTodayTest {
    @get:Rule
    val rule = createComposeRule()

    @Test
    fun animateScrollToTodayLandsOnTheCurrentMonth() {
        lateinit var state: CalendarState
        lateinit var scope: CoroutineScope
        rule.setContent {
            state = rememberCalendarState()
            scope = rememberCoroutineScope()
            HorizontalCalendar(
                state = state,
                dayContent = { day -> DefaultDay(day = day, animateSelection = false) },
            )
        }
        rule.runOnIdle { scope.launch { state.scrollToMonth(YearMonth(2020, 1)) } }
        rule.waitForIdle()
        assertEquals(YearMonth(2020, 1), state.firstVisibleMonth)
        rule.runOnIdle { scope.launch { state.animateScrollToToday() } }
        rule.waitForIdle()
        assertEquals(currentYearMonth(), state.firstVisibleMonth)
    }
}
