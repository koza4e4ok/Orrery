package me.kozakov.orrery.compose

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.datetime.LocalDate
import kotlinx.datetime.YearMonth
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DatePickerDialogTest {
    @get:Rule val rule = createComposeRule()

    private lateinit var state: OrreryDatePickerState
    private val strings = DatePickerDefaults.strings()

    private fun content() {
        rule.setContent {
            MaterialTheme {
                state =
                    rememberOrreryDatePickerState(
                        initialDisplayedMonth = YearMonth(2026, 7),
                        yearRange = 2026..2026,
                    )
                OrreryDatePickerDialog(
                    onDismissRequest = {},
                    confirmButton = { TextButton(onClick = {}) { Text("OK") } },
                    dismissButton = { TextButton(onClick = {}) { Text("Cancel") } },
                    state = state,
                    today = LocalDate(2026, 7, 15),
                )
            }
        }
    }

    @Test
    fun clickingADayUpdatesSelectedDate() {
        content()
        rule.onNodeWithText("10").performClick()
        rule.runOnIdle { assertEquals(LocalDate(2026, 7, 10), state.selectedDate) }
    }

    @Test
    fun buttonSlotsRender() {
        content()
        rule.onNodeWithText("OK").assertExists()
        rule.onNodeWithText("Cancel").assertExists()
    }

    @Test
    fun typedDateSurvivesToggleBackToPicker() {
        content()
        rule.onNodeWithContentDescription(strings.switchToInputDescription).performClick()
        rule.onNodeWithText(strings.inputLabel).performTextReplacement("03/10/2026")
        rule.runOnIdle { assertEquals(LocalDate(2026, 3, 10), state.selectedDate) }
        rule.onNodeWithContentDescription(strings.switchToPickerDescription).performClick()
        rule.runOnIdle { assertEquals(YearMonth(2026, 3), state.displayedMonth) }
    }

    @Test
    fun titleClickOpensMonthYearPicker() {
        content()
        // NavHeaderTitle renders the month as plain text ("July 2026").
        rule.onNodeWithText("July 2026").performClick()
        rule.onNodeWithText("2026 – 2026").assertExists()
    }
}
