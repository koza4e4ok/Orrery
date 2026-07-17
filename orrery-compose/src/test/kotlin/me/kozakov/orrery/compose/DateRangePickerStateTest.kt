package me.kozakov.orrery.compose

import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.datetime.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DateRangePickerStateTest {
    @get:Rule val rule = createComposeRule()

    @Test
    fun setSelectionStoresAValidRange() {
        lateinit var state: OrreryDateRangePickerState
        rule.setContent { state = rememberOrreryDateRangePickerState() }
        rule.runOnIdle { state.setSelection(LocalDate(2026, 7, 8), LocalDate(2026, 7, 12)) }
        rule.runOnIdle {
            assertEquals(LocalDate(2026, 7, 8), state.selectedStartDate)
            assertEquals(LocalDate(2026, 7, 12), state.selectedEndDate)
        }
    }

    @Test
    fun setSelectionRejectsTooShortRanges() {
        lateinit var state: OrreryDateRangePickerState
        rule.setContent { state = rememberOrreryDateRangePickerState(minDays = 3) }
        // SelectionEngine.replace rejects wholesale, keeping the current
        // (empty) selection - so both endpoints stay null.
        rule.runOnIdle { state.setSelection(LocalDate(2026, 7, 8), LocalDate(2026, 7, 9)) }
        rule.runOnIdle {
            assertNull(state.selectedStartDate)
            assertNull(state.selectedEndDate)
        }
    }

    @Test
    fun setSelectionWithNullStartClears() {
        lateinit var state: OrreryDateRangePickerState
        rule.setContent { state = rememberOrreryDateRangePickerState() }
        rule.runOnIdle { state.setSelection(LocalDate(2026, 7, 8), LocalDate(2026, 7, 12)) }
        rule.runOnIdle { state.setSelection(null, null) }
        rule.runOnIdle { assertNull(state.selectedStartDate) }
    }

    @Test
    fun stateSurvivesRestoration() {
        val restorationTester = StateRestorationTester(rule)
        lateinit var state: OrreryDateRangePickerState
        restorationTester.setContent { state = rememberOrreryDateRangePickerState() }
        rule.runOnIdle {
            state.setSelection(LocalDate(2026, 7, 8), LocalDate(2026, 7, 12))
            state.displayMode = DatePickerDisplayMode.Input
        }
        restorationTester.emulateSavedInstanceStateRestore()
        rule.runOnIdle {
            assertEquals(LocalDate(2026, 7, 8), state.selectedStartDate)
            assertEquals(LocalDate(2026, 7, 12), state.selectedEndDate)
            assertEquals(DatePickerDisplayMode.Input, state.displayMode)
        }
    }
}
