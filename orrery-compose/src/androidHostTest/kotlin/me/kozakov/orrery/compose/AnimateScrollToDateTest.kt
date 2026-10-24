package me.kozakov.orrery.compose

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate
import kotlinx.datetime.YearMonth
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AnimateScrollToDateTest {
    @get:Rule val rule = createComposeRule()

    private lateinit var state: CalendarState
    private lateinit var scope: CoroutineScope

    private fun content(firstVisibleMonth: YearMonth) {
        rule.setContent {
            MaterialTheme {
                state =
                    rememberCalendarState(
                        startMonth = YearMonth(2026, 1),
                        endMonth = YearMonth(2026, 12),
                        firstVisibleMonth = firstVisibleMonth,
                    )
                scope = rememberCoroutineScope()
                HorizontalCalendar(state = state) { day -> DefaultDay(day = day) }
            }
        }
    }

    @Test
    fun animateScrollToDateLandsOnTheDateMonth() {
        content(firstVisibleMonth = YearMonth(2026, 1))
        rule.runOnIdle { scope.launch { state.animateScrollToDate(LocalDate(2026, 11, 5)) } }
        rule.waitForIdle()
        rule.runOnIdle { assertEquals(YearMonth(2026, 11), state.firstVisibleMonth) }
    }

    @Test
    fun animateScrollToDateCoercesOutOfRangeDates() {
        content(firstVisibleMonth = YearMonth(2026, 6))
        rule.runOnIdle { scope.launch { state.animateScrollToDate(LocalDate(2030, 3, 1)) } }
        rule.waitForIdle()
        rule.runOnIdle { assertEquals(YearMonth(2026, 12), state.firstVisibleMonth) }
    }
}
