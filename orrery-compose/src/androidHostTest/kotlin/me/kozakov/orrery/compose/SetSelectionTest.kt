package me.kozakov.orrery.compose

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import me.kozakov.orrery.core.DisabledDates
import me.kozakov.orrery.core.Selection
import me.kozakov.orrery.core.SelectionEvent
import me.kozakov.orrery.core.SelectionMode
import me.kozakov.orrery.core.SelectionPresets
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SetSelectionTest {
    @get:Rule
    val rule = createComposeRule()

    private val wed15 = LocalDate(2026, 7, 15)

    @Test
    fun validPresetIsApplied() {
        lateinit var state: CalendarSelectionState
        rule.setContent {
            state = rememberCalendarSelectionState(mode = SelectionMode.Range())
        }
        rule.runOnIdle { state.set(SelectionPresets.nextDays(wed15, 7)) }
        assertEquals(wed15..LocalDate(2026, 7, 21), state.selection.range)
    }

    @Test
    fun invalidProposalIsRejectedWithEvents() {
        lateinit var state: CalendarSelectionState
        val events = mutableListOf<SelectionEvent>()
        rule.setContent {
            state =
                rememberCalendarSelectionState(
                    mode = SelectionMode.Range(),
                    disabled = DisabledDates { daysOfWeek(DayOfWeek.SATURDAY) },
                    onEvent = { events.add(it) },
                )
        }
        // Jul 15..21 contains Saturday Jul 18
        rule.runOnIdle { state.set(SelectionPresets.nextDays(wed15, 7)) }
        assertNull(state.selection.range)
        assertEquals(
            listOf<SelectionEvent>(SelectionEvent.Intercepted(LocalDate(2026, 7, 18))),
            events,
        )
    }

    @Test
    fun setEmptyClears() {
        lateinit var state: CalendarSelectionState
        rule.setContent {
            state =
                rememberCalendarSelectionState(
                    mode = SelectionMode.Single(),
                    initialSelection = Selection(single = wed15),
                )
        }
        rule.runOnIdle { state.set(Selection.Empty) }
        assertTrue(state.selection == Selection.Empty)
    }
}
