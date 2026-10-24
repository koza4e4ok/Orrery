package me.kozakov.orrery.compose

import androidx.compose.material3.Text
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class WeekCalendarTest {
    @get:Rule
    val rule = createComposeRule()

    private lateinit var state: WeekCalendarState
    private lateinit var scope: CoroutineScope

    private fun set() {
        rule.setContent {
            scope = rememberCoroutineScope()
            state =
                rememberWeekCalendarState(
                    startDate = LocalDate(2026, 7, 1),
                    endDate = LocalDate(2026, 8, 31),
                    firstVisibleDate = LocalDate(2026, 7, 9),
                    firstDayOfWeek = DayOfWeek.MONDAY,
                )
            WeekCalendar(
                state = state,
                dayContent = { day -> Text(day.date.toString(), Modifier.testTag("d-${day.date}")) },
            )
        }
    }

    @Test
    fun showsTheWeekContainingFirstVisibleDate() {
        set()
        // Week of Thu Jul 9 (Mon start) = Mon Jul 6 .. Sun Jul 12
        rule.onNodeWithTag("d-2026-07-06").assertIsDisplayed()
        rule.onNodeWithTag("d-2026-07-12").assertIsDisplayed()
        assertEquals(
            LocalDate(2026, 7, 6),
            state.firstVisibleWeek.days
                .first()
                .date,
        )
    }

    @Test
    fun scrollToDateMovesToItsWeek() {
        set()
        scope.launch { state.scrollToDate(LocalDate(2026, 8, 20)) } // Thursday
        rule.waitForIdle()
        rule.onNodeWithTag("d-2026-08-17").assertIsDisplayed() // that week's Monday
        assertEquals(
            LocalDate(2026, 8, 17),
            state.firstVisibleWeek.days
                .first()
                .date,
        )
    }
}
