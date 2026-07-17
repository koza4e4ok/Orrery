package me.kozakov.orrery.compose

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.datetime.LocalDate
import kotlinx.datetime.YearMonth
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DateRangePickerDialogTest {
    @get:Rule val rule = createComposeRule()

    private lateinit var state: OrreryDateRangePickerState
    private val strings = DatePickerDefaults.strings()
    private var dismissed = false

    private fun content() {
        rule.setContent {
            MaterialTheme {
                state =
                    rememberOrreryDateRangePickerState(
                        initialDisplayedMonth = YearMonth(2026, 7),
                        yearRange = 2026..2026,
                    )
                OrreryDateRangePickerDialog(
                    onDismissRequest = { dismissed = true },
                    confirmButton = { TextButton(onClick = {}) { Text("Save") } },
                    state = state,
                    today = LocalDate(2026, 7, 15),
                )
            }
        }
    }

    @Test
    fun clickingTwoDaysSelectsARange() {
        content()
        rule.onAllNodesWithText("8").onFirst().performClick()
        rule.onAllNodesWithText("12").onFirst().performClick()
        rule.runOnIdle {
            assertEquals(LocalDate(2026, 7, 8), state.selectedStartDate)
            assertEquals(LocalDate(2026, 7, 12), state.selectedEndDate)
        }
    }

    @Test
    fun closeGlyphFiresOnDismissRequest() {
        content()
        rule.onNodeWithContentDescription(strings.closeDescription).performClick()
        rule.runOnIdle { assertTrue(dismissed) }
    }

    @Test
    fun inputModeCrossValidatesTheRange() {
        content()
        rule.onNodeWithContentDescription(strings.switchToInputDescription).performClick()
        rule.onNodeWithText(strings.rangeStartInputLabel).performTextReplacement("07/12/2026")
        rule.onNodeWithText(strings.rangeEndInputLabel).performTextReplacement("07/08/2026")
        rule.onNodeWithText(strings.invalidRangeError).assertExists()
        rule.runOnIdle { assertNull(state.selectedEndDate) }
    }

    @Test
    fun validInputPairAppliesToState() {
        content()
        rule.onNodeWithContentDescription(strings.switchToInputDescription).performClick()
        rule.onNodeWithText(strings.rangeStartInputLabel).performTextReplacement("07/08/2026")
        rule.onNodeWithText(strings.rangeEndInputLabel).performTextReplacement("07/12/2026")
        rule.runOnIdle {
            assertEquals(LocalDate(2026, 7, 8), state.selectedStartDate)
            assertEquals(LocalDate(2026, 7, 12), state.selectedEndDate)
        }
    }
}
