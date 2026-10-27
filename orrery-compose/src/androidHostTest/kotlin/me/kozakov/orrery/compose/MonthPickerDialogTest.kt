package me.kozakov.orrery.compose

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.datetime.LocalDate
import kotlinx.datetime.YearMonth
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MonthPickerDialogTest {
    @get:Rule val rule = createComposeRule()

    private lateinit var state: OrreryMonthPickerState

    private fun content(
        initialSelectedMonth: YearMonth? = null,
        range: ClosedRange<YearMonth> = YearMonth(2020, 1)..YearMonth(2030, 12),
        today: LocalDate = LocalDate(2026, 7, 15),
    ) {
        rule.setContent {
            MaterialTheme {
                state = rememberOrreryMonthPickerState(initialSelectedMonth = initialSelectedMonth, range = range)
                OrreryMonthPickerDialog(
                    onDismissRequest = {},
                    confirmButton = { TextButton(onClick = {}) { Text("OK") } },
                    dismissButton = { TextButton(onClick = {}) { Text("Cancel") } },
                    state = state,
                    today = today,
                )
            }
        }
    }

    @Test
    fun opensOnTheMonthGridOfTheSelectedYear() {
        content(initialSelectedMonth = YearMonth(2027, 3))
        rule.onNodeWithText("2027").assertExists()
        rule.onNodeWithText("March").assertIsSelected()
        rule.onNodeWithText("April").assertIsNotSelected()
        rule.onNodeWithText("March 2027").assertExists()
    }

    @Test
    fun opensOnTodaysYearWithoutASelection() {
        content()
        rule.onNodeWithText("2026").assertExists()
        rule.onNodeWithText("Month").assertExists()
        rule.onNodeWithText("OK").assertExists()
        rule.onNodeWithText("Cancel").assertExists()
    }

    @Test
    fun opensOnTheClampedYearWhenTodayIsOutsideTheRange() {
        content(today = LocalDate(2045, 1, 1))
        rule.onNodeWithText("2030").assertExists()
    }

    @Test
    fun selectingAMonthUpdatesTheHeadline() {
        content()
        rule.onNodeWithText("May").performClick()
        rule.runOnIdle { assertEquals(YearMonth(2026, 5), state.selectedMonth) }
        rule.onNodeWithText("May 2026").assertExists()
        rule.onNodeWithText("May").assertIsSelected()
    }

    @Test
    fun monthsOutsideTheRangeAreDisabled() {
        content(range = YearMonth(2026, 3)..YearMonth(2026, 10))
        rule.onNodeWithText("January").assertIsNotEnabled()
        rule.onNodeWithText("March").assertIsEnabled()
        rule.onNodeWithText("December").assertIsNotEnabled()
        rule.runOnIdle { assertNull(state.selectedMonth) }
    }

    @Test
    fun yearTitleZoomsOutToYearsAndDecades() {
        content()
        rule.onNodeWithText("2026").performClick()
        rule.waitForIdle()
        rule.onNodeWithText("2020 – 2030").performClick()
        rule.waitForIdle()
        rule.onNodeWithText("2020s").assertExists()
    }

    @Test
    fun selectedMonthSurvivesRestoration() {
        val restorationTester = StateRestorationTester(rule)
        restorationTester.setContent {
            state = rememberOrreryMonthPickerState(range = YearMonth(2020, 1)..YearMonth(2030, 12))
        }
        rule.runOnIdle { state.select(YearMonth(2029, 2)) }
        restorationTester.emulateSavedInstanceStateRestore()
        rule.runOnIdle { assertEquals(YearMonth(2029, 2), state.selectedMonth) }
    }
}
