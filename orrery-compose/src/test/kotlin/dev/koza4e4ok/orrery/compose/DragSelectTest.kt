package dev.koza4e4ok.orrery.compose

import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.koza4e4ok.orrery.core.SelectionEvent
import dev.koza4e4ok.orrery.core.SelectionMode
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.YearMonth
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DragSelectTest {
    @get:Rule
    val rule = createComposeRule()

    private val d5 = LocalDate(2026, 7, 5)
    private val d9 = LocalDate(2026, 7, 9)

    @Test
    fun dragApiBuildsOrderedRangeAndValidatesOnEnd() {
        lateinit var selection: CalendarSelectionState
        val events = mutableListOf<SelectionEvent>()
        rule.setContent {
            selection =
                rememberCalendarSelectionState(
                    mode = SelectionMode.Range(minDays = 3),
                    onEvent = { events.add(it) },
                )
        }
        rule.runOnIdle {
            // forward drag
            selection.dragStart(d5)
            selection.dragUpdate(d9)
            selection.dragEnd()
        }
        assertEquals(d5..d9, selection.selection.range)

        rule.runOnIdle {
            // reverse drag keeps range ordered
            selection.dragStart(d9)
            selection.dragUpdate(d5)
        }
        assertEquals(d5..d9, selection.selection.range)

        rule.runOnIdle {
            // too-short drag emits event and clears the end
            selection.dragStart(d5)
            selection.dragUpdate(LocalDate(2026, 7, 6)) // 2 days < min 3
            selection.dragEnd()
        }
        assertTrue(events.single() is SelectionEvent.RangeTooShort)
        assertNull(selection.selection.rangeEnd)
        assertEquals(d5, selection.selection.rangeStart)
    }

    @Test
    fun longPressDragOnCalendarSelectsARange() {
        lateinit var selection: CalendarSelectionState
        rule.setContent {
            selection = rememberCalendarSelectionState(mode = SelectionMode.Range())
            HorizontalCalendar(
                state =
                    rememberCalendarState(
                        startMonth = YearMonth(2026, 7),
                        endMonth = YearMonth(2026, 7),
                        firstVisibleMonth = YearMonth(2026, 7),
                        firstDayOfWeek = DayOfWeek.MONDAY,
                    ),
                dragSelection = selection,
                modifier = Modifier.testTag("cal"),
                dayContent = { day ->
                    DefaultDay(day = day, selectionState = selection, today = LocalDate(2026, 7, 1))
                },
            )
        }
        rule.onNodeWithTag("cal").performTouchInput {
            longClick(Offset(width * 0.1f, height * 0.5f))
        }
        rule.onNodeWithTag("cal").performTouchInput {
            down(Offset(width * 0.1f, height * 0.5f))
            advanceEventTime(1000) // hold for long-press timeout
            moveTo(Offset(width * 0.9f, height * 0.5f))
            up()
        }
        rule.waitForIdle()
        val range = selection.selection.range
        assertNotNull("expected a completed range, got ${selection.selection}", range)
        assertTrue(range!!.start < range.endInclusive)
    }
}
