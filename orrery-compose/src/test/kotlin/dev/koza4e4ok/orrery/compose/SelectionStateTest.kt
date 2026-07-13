package dev.koza4e4ok.orrery.compose

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.koza4e4ok.orrery.core.SelectionEvent
import dev.koza4e4ok.orrery.core.SelectionMode
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.YearMonth
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SelectionStateTest {
    @get:Rule
    val rule = createComposeRule()

    @Test
    fun clickingDaysDrivesRangeSelectionThroughTheUi() {
        lateinit var selectionState: CalendarSelectionState
        val events = mutableListOf<SelectionEvent>()
        rule.setContent {
            selectionState =
                rememberCalendarSelectionState(
                    mode = SelectionMode.Range(maxDays = 5),
                    onEvent = { events.add(it) },
                )
            val calendarState =
                rememberCalendarState(
                    startMonth = YearMonth(2026, 7),
                    endMonth = YearMonth(2026, 7),
                    firstVisibleMonth = YearMonth(2026, 7),
                    firstDayOfWeek = DayOfWeek.MONDAY,
                )
            HorizontalCalendar(
                state = calendarState,
                dayContent = { day ->
                    Box(
                        Modifier
                            .testTag("day-${day.date}")
                            .clickable { selectionState.click(day.date) },
                    ) { Text(day.date.day.toString()) }
                },
            )
        }
        rule.onNodeWithTag("day-2026-07-06").performClick()
        rule.onNodeWithTag("day-2026-07-09").performClick()
        assertEquals(LocalDate(2026, 7, 6)..LocalDate(2026, 7, 9), selectionState.selection.range)
        assertTrue(selectionState.isSelected(LocalDate(2026, 7, 6)))
        assertTrue(selectionState.isInRange(LocalDate(2026, 7, 7)))
        // range is complete, so the next click starts a new range (no violation event)
        rule.onNodeWithTag("day-2026-07-20").performClick()
        assertEquals(LocalDate(2026, 7, 20), selectionState.selection.rangeStart)
        assertEquals(0, events.size)
    }

    @Test
    fun violationEventsReachTheCallback() {
        lateinit var selectionState: CalendarSelectionState
        val events = mutableListOf<SelectionEvent>()
        rule.setContent {
            selectionState =
                rememberCalendarSelectionState(
                    mode = SelectionMode.Multi(maxCount = 1),
                    onEvent = { events.add(it) },
                )
        }
        rule.runOnIdle {
            selectionState.click(LocalDate(2026, 7, 1))
            selectionState.click(LocalDate(2026, 7, 2))
        }
        assertEquals(1, events.size)
        assertTrue(events.first() is SelectionEvent.TooManyDays)
        assertEquals(setOf(LocalDate(2026, 7, 1)), selectionState.selection.multi)
    }
}
