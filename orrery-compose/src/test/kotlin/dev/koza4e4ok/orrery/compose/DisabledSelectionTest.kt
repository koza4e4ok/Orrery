package dev.koza4e4ok.orrery.compose

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.koza4e4ok.orrery.core.DisabledDates
import dev.koza4e4ok.orrery.core.SelectionEvent
import dev.koza4e4ok.orrery.core.SelectionMode
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DisabledSelectionTest {
    @get:Rule
    val rule = createComposeRule()

    private val mon = LocalDate(2026, 7, 6)
    private val sat = LocalDate(2026, 7, 11)

    @Test
    fun clickOnDisabledDateEmitsInterceptedAndKeepsSelection() {
        lateinit var state: CalendarSelectionState
        val events = mutableListOf<SelectionEvent>()
        rule.setContent {
            state =
                rememberCalendarSelectionState(
                    mode = SelectionMode.Single(),
                    disabled = DisabledDates { daysOfWeek(DayOfWeek.SATURDAY) },
                    onEvent = { events.add(it) },
                )
        }
        rule.runOnIdle { state.click(sat) }
        assertNull(state.selection.single)
        assertEquals(listOf<SelectionEvent>(SelectionEvent.Intercepted(sat)), events)
        assertTrue(state.isDisabled(sat))
        assertFalse(state.isDisabled(mon))
    }

    @Test
    fun rangeSpanningDisabledDateIsInvalidated() {
        lateinit var state: CalendarSelectionState
        val events = mutableListOf<SelectionEvent>()
        rule.setContent {
            state =
                rememberCalendarSelectionState(
                    mode = SelectionMode.Range(),
                    disabled = DisabledDates { dates(LocalDate(2026, 7, 8)) },
                    onEvent = { events.add(it) },
                )
        }
        rule.runOnIdle {
            state.click(mon)
            state.click(LocalDate(2026, 7, 10))
        }
        assertNull(state.selection.range)
        assertEquals(
            listOf<SelectionEvent>(SelectionEvent.Intercepted(LocalDate(2026, 7, 8))),
            events,
        )
    }

    @Test
    fun dragStartOnDisabledDateIsIgnored() {
        lateinit var state: CalendarSelectionState
        rule.setContent {
            state =
                rememberCalendarSelectionState(
                    mode = SelectionMode.Range(),
                    disabled = DisabledDates { daysOfWeek(DayOfWeek.SATURDAY) },
                )
        }
        rule.runOnIdle { state.dragStart(sat) }
        assertNull(state.selection.rangeStart)
    }

    @Test
    fun inlineDisabledDatesDoesNotResetSelectionOnRecomposition() {
        lateinit var state: CalendarSelectionState
        var tick by mutableStateOf(0)
        rule.setContent {
            @Suppress("UNUSED_EXPRESSION")
            tick
            state =
                rememberCalendarSelectionState(
                    mode = SelectionMode.Single(),
                    // built inline on purpose: a fresh, never-equal instance per recomposition
                    disabled = DisabledDates { predicate { false } },
                )
        }
        rule.runOnIdle { state.click(mon) }
        rule.runOnIdle { tick++ }
        rule.runOnIdle { assertEquals(mon, state.selection.single) }
    }

    @Test
    fun disabledInputIsRefreshedOnRecomposition() {
        lateinit var state: CalendarSelectionState
        var blockSaturdays by mutableStateOf(false)
        rule.setContent {
            state =
                rememberCalendarSelectionState(
                    mode = SelectionMode.Single(),
                    disabled =
                        if (blockSaturdays) {
                            DisabledDates { daysOfWeek(DayOfWeek.SATURDAY) }
                        } else {
                            DisabledDates.None
                        },
                )
        }
        rule.runOnIdle { assertFalse(state.isDisabled(sat)) }
        rule.runOnIdle { blockSaturdays = true }
        rule.runOnIdle { assertTrue(state.isDisabled(sat)) }
    }
}
