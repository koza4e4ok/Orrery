package me.kozakov.orrery.compose

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.material3.Text
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
import me.kozakov.orrery.core.OutDateStyle
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CalendarStateTest {
    @get:Rule
    val rule = createComposeRule()

    private lateinit var state: CalendarState
    private lateinit var scope: CoroutineScope

    private fun setUp(firstVisible: YearMonth = YearMonth(2026, 7)) {
        rule.setContent {
            scope = rememberCoroutineScope()
            state =
                rememberCalendarState(
                    startMonth = YearMonth(2020, 1),
                    endMonth = YearMonth(2030, 12),
                    firstVisibleMonth = firstVisible,
                    firstDayOfWeek = DayOfWeek.MONDAY,
                )
            // attach the list state so scroll calls take effect
            LazyRow(state = state.listState) {
                items(state.monthCount) { index ->
                    Box(Modifier.size(80.dp)) { Text("m$index") }
                }
            }
        }
    }

    @Test
    fun initialStateExposesFirstVisibleMonthAndCount() {
        setUp()
        assertEquals(YearMonth(2026, 7), state.firstVisibleMonth)
        assertEquals(132, state.monthCount) // 11 years * 12
        assertEquals(OutDateStyle.EndOfRow, state.outDateStyle)
    }

    @Test
    fun firstVisibleMonthClampsToRange() {
        setUp(firstVisible = YearMonth(1999, 1))
        assertEquals(YearMonth(2020, 1), state.firstVisibleMonth)
    }

    @Test
    fun scrollToMonthAndDateUpdateFirstVisibleMonth() {
        setUp()
        scope.launch { state.scrollToMonth(YearMonth(2027, 3)) }
        rule.waitForIdle()
        assertEquals(YearMonth(2027, 3), state.firstVisibleMonth)
        scope.launch { state.scrollToDate(LocalDate(2021, 2, 14)) }
        rule.waitForIdle()
        assertEquals(YearMonth(2021, 2), state.firstVisibleMonth)
    }

    @Test
    fun updateRangeKeepsVisibleMonthWhenStillInRange() {
        setUp()
        rule.runOnIdle {
            state.updateRange(YearMonth(2026, 1), YearMonth(2027, 12))
        }
        rule.waitForIdle()
        assertEquals(YearMonth(2026, 7), state.firstVisibleMonth)
        assertEquals(24, state.monthCount)
    }
}
