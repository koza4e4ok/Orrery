package me.kozakov.orrery.compose

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.YearMonth
import me.kozakov.orrery.core.DisabledDates
import me.kozakov.orrery.core.Selection
import me.kozakov.orrery.core.SelectionEvent
import me.kozakov.orrery.core.SelectionMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class WeekMonthSelectionTest {
    @get:Rule
    val rule = createComposeRule()

    private fun setCalendar(
        mode: SelectionMode,
        firstDayOfWeek: DayOfWeek = DayOfWeek.MONDAY,
        disabled: DisabledDates = DisabledDates.None,
        events: MutableList<SelectionEvent> = mutableListOf(),
        onState: (CalendarSelectionState) -> Unit,
    ) {
        rule.setContent {
            val selectionState =
                rememberCalendarSelectionState(mode = mode, disabled = disabled, onEvent = { events.add(it) })
            onState(selectionState)
            HorizontalCalendar(
                state =
                    rememberCalendarState(
                        startMonth = YearMonth(2026, 7),
                        endMonth = YearMonth(2026, 7),
                        firstVisibleMonth = YearMonth(2026, 7),
                        firstDayOfWeek = firstDayOfWeek,
                    ),
                dragSelection = selectionState,
                modifier = Modifier.testTag("cal"),
                dayContent = { day ->
                    Box(
                        Modifier
                            .testTag("day-${day.date}")
                            .clickable { selectionState.click(day.date) },
                    ) { Text(day.date.day.toString()) }
                },
            )
        }
    }

    @Test
    fun tappingADaySelectsTheVisibleWeekRow() {
        lateinit var state: CalendarSelectionState
        setCalendar(SelectionMode.Week(firstDayOfWeek = DayOfWeek.SUNDAY)) { state = it }
        rule.onNodeWithTag("day-2026-07-08").performClick()
        assertEquals(LocalDate(2026, 7, 5)..LocalDate(2026, 7, 11), state.selection.range)
        assertTrue(state.isRangeStart(LocalDate(2026, 7, 5)))
        assertTrue(state.isInRange(LocalDate(2026, 7, 8)))
        assertTrue(state.isRangeEnd(LocalDate(2026, 7, 11)))

        rule.onNodeWithTag("day-2026-07-10").performClick()
        assertEquals(Selection.Empty, state.selection)
    }

    @Test
    fun tappingADaySelectsTheWholeMonth() {
        lateinit var state: CalendarSelectionState
        setCalendar(SelectionMode.Month()) { state = it }
        rule.onNodeWithTag("day-2026-07-17").performClick()
        assertEquals(LocalDate(2026, 7, 1)..LocalDate(2026, 7, 31), state.selection.range)
    }

    @Test
    fun weekWithADisabledDayIsRejected() {
        lateinit var state: CalendarSelectionState
        val events = mutableListOf<SelectionEvent>()
        val blocked = LocalDate(2026, 7, 10)
        setCalendar(SelectionMode.Week(), disabled = DisabledDates { dates(blocked) }, events = events) { state = it }
        rule.onNodeWithTag("day-2026-07-08").performClick()
        assertEquals(Selection.Empty, state.selection)
        assertEquals(listOf<SelectionEvent>(SelectionEvent.Intercepted(blocked)), events)
    }

    @Test
    fun dragIsDisabledInWeekAndMonthModes() {
        lateinit var state: CalendarSelectionState
        setCalendar(SelectionMode.Month()) { state = it }
        assertFalse(state.supportsDrag)
        rule.runOnIdle {
            state.dragStart(LocalDate(2026, 7, 8))
            state.dragUpdate(LocalDate(2026, 7, 10))
            state.dragEnd()
        }
        assertEquals(Selection.Empty, state.selection)

        rule.onNodeWithTag("cal").performTouchInput {
            down(Offset(width * 0.1f, height * 0.5f))
            advanceEventTime(1000)
            moveTo(Offset(width * 0.9f, height * 0.5f))
            up()
        }
        rule.waitForIdle()
        assertEquals(Selection.Empty, state.selection)
    }
}
