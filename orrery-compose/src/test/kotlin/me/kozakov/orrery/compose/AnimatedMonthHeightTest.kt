package me.kozakov.orrery.compose

import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
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
class AnimatedMonthHeightTest {
    @get:Rule
    val rule = createComposeRule()

    /**
     * July 2026 spans 5 weeks and August 2026 spans 6 (Monday start), so the
     * pager must be taller once settled on August. Guards the height that
     * [HorizontalCalendar] derives per page for the resize animation.
     */
    @Test
    fun pagerHeightFollowsWeekCount() {
        lateinit var state: CalendarState
        lateinit var scope: CoroutineScope
        rule.setContent {
            state =
                rememberCalendarState(
                    startMonth = YearMonth(2026, 7),
                    endMonth = YearMonth(2026, 8),
                    firstVisibleMonth = YearMonth(2026, 7),
                    firstDayOfWeek = DayOfWeek.MONDAY,
                )
            scope = rememberCoroutineScope()
            HorizontalCalendar(
                state = state,
                dayContent = { day -> DefaultDay(day = day, animateSelection = false) },
            )
        }
        rule.waitForIdle()
        val julyHeight =
            rule
                .onRoot()
                .fetchSemanticsNode()
                .size.height

        rule.runOnIdle { scope.launch { state.scrollToMonth(YearMonth(2026, 8)) } }
        rule.waitForIdle()

        assertEquals(YearMonth(2026, 8), state.firstVisibleMonth)
        val augustHeight =
            rule
                .onRoot()
                .fetchSemanticsNode()
                .size.height
        assertTrue(
            "6-week August should be taller than 5-week July (july=$julyHeight, august=$augustHeight)",
            augustHeight > julyHeight,
        )
    }
}
