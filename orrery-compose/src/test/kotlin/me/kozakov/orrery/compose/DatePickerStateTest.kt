package me.kozakov.orrery.compose

import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.datetime.LocalDate
import kotlinx.datetime.YearMonth
import me.kozakov.orrery.core.DisabledDates
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DatePickerStateTest {
    @get:Rule val rule = createComposeRule()

    @Test
    fun selectedDateWritesThroughValidation() {
        lateinit var state: OrreryDatePickerState
        rule.setContent { state = rememberOrreryDatePickerState() }
        rule.runOnIdle { state.selectedDate = LocalDate(2026, 7, 15) }
        rule.runOnIdle { assertEquals(LocalDate(2026, 7, 15), state.selectedDate) }
        rule.runOnIdle { state.selectedDate = null }
        rule.runOnIdle { assertNull(state.selectedDate) }
    }

    @Test
    fun selectedDateRejectsDisabledDates() {
        lateinit var state: OrreryDatePickerState
        rule.setContent {
            state =
                rememberOrreryDatePickerState(
                    disabledDates = DisabledDates { dates(LocalDate(2026, 7, 20)) },
                )
        }
        rule.runOnIdle { state.selectedDate = LocalDate(2026, 7, 20) }
        rule.runOnIdle { assertNull(state.selectedDate) }
    }

    @Test
    fun selectedDateRejectsDatesOutsideYearRange() {
        lateinit var state: OrreryDatePickerState
        rule.setContent { state = rememberOrreryDatePickerState(yearRange = 2026..2026) }
        rule.runOnIdle { state.selectedDate = LocalDate(2027, 1, 1) }
        rule.runOnIdle { assertNull(state.selectedDate) }
    }

    @Test
    fun displayedMonthFollowsInitialDate() {
        lateinit var state: OrreryDatePickerState
        rule.setContent {
            state = rememberOrreryDatePickerState(initialDate = LocalDate(2026, 3, 10))
        }
        rule.runOnIdle { assertEquals(YearMonth(2026, 3), state.displayedMonth) }
    }

    @Test
    fun stateSurvivesRestoration() {
        val restorationTester = StateRestorationTester(rule)
        lateinit var state: OrreryDatePickerState
        restorationTester.setContent { state = rememberOrreryDatePickerState() }
        rule.runOnIdle {
            state.selectedDate = LocalDate(2026, 7, 15)
            state.displayMode = DatePickerDisplayMode.Input
        }
        restorationTester.emulateSavedInstanceStateRestore()
        rule.runOnIdle {
            assertEquals(LocalDate(2026, 7, 15), state.selectedDate)
            assertEquals(DatePickerDisplayMode.Input, state.displayMode)
        }
    }
}
