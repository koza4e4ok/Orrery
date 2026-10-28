package me.kozakov.orrery.compose

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.YearMonth
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

// A 420dp-tall landscape window (a phone on its side): every picker body is
// taller than that, so the body has to give way for the buttons to stay on screen.
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = COMPACT_HEIGHT_QUALIFIERS)
class CompactHeightDialogTest {
    @get:Rule val rule = createComposeRule()

    private var confirmed = 0
    private var dismissed = 0
    private val today = LocalDate(2026, 7, 15)

    @Composable
    private fun Confirm() {
        TextButton(onClick = { confirmed++ }) { Text("OK") }
    }

    @Composable
    private fun Dismiss() {
        TextButton(onClick = { dismissed++ }) { Text("Cancel") }
    }

    private fun assertButtonsWork() {
        rule.onNodeWithText("Cancel").assertIsDisplayed().performClick()
        rule.onNodeWithText("OK").assertIsDisplayed().performClick()
        rule.runOnIdle {
            assertEquals(1, confirmed)
            assertEquals(1, dismissed)
        }
    }

    @Test
    fun datePickerKeepsItsButtonsOnScreen() {
        rule.setContent {
            MaterialTheme {
                OrreryDatePickerDialog(
                    onDismissRequest = {},
                    confirmButton = { Confirm() },
                    dismissButton = { Dismiss() },
                    state = rememberOrreryDatePickerState(initialDisplayedMonth = YearMonth(2026, 7)),
                    today = today,
                )
            }
        }
        assertButtonsWork()
    }

    @Test
    fun datePickerBodyScrollsToTheLastWeek() {
        lateinit var state: OrreryDatePickerState
        rule.setContent {
            MaterialTheme {
                state = rememberOrreryDatePickerState(initialDisplayedMonth = YearMonth(2026, 7))
                OrreryDatePickerDialog(
                    onDismissRequest = {},
                    confirmButton = { Confirm() },
                    state = state,
                    today = today,
                )
            }
        }
        rule.onNodeWithText("31").performScrollTo().performClick()
        rule.runOnIdle { assertEquals(LocalDate(2026, 7, 31), state.selectedDate) }
        rule.onNodeWithText("OK").assertIsDisplayed()
    }

    @Test
    fun dateRangePickerKeepsConfirmOnScreenInBothModes() {
        lateinit var strings: DatePickerStrings
        rule.setContent {
            MaterialTheme {
                strings = DatePickerDefaults.strings()
                OrreryDateRangePickerDialog(
                    onDismissRequest = {},
                    confirmButton = { Confirm() },
                    state = rememberOrreryDateRangePickerState(initialDisplayedMonth = YearMonth(2026, 7)),
                    today = today,
                    presets = DatePickerDefaults.rangePresets(today),
                )
            }
        }
        rule.onNodeWithText("OK").assertIsDisplayed()
        rule.onNodeWithContentDescription(strings.switchToInputDescription).performClick()
        rule.onNodeWithText(strings.rangeEndInputLabel).performScrollTo().assertIsDisplayed()
        rule.onNodeWithText("OK").assertIsDisplayed().performClick()
        rule.runOnIdle { assertEquals(1, confirmed) }
    }

    @Test
    fun dateTimePickerKeepsItsButtonsOnScreenOnBothSteps() {
        lateinit var state: OrreryDateTimePickerState
        lateinit var strings: DateTimePickerStrings
        rule.setContent {
            MaterialTheme {
                strings = DatePickerDefaults.dateTimeStrings()
                state =
                    rememberOrreryDateTimePickerState(
                        initialDateTime = LocalDateTime(2026, 7, 8, 9, 30),
                        is24Hour = true,
                    )
                OrreryDateTimePickerDialog(
                    onDismissRequest = {},
                    confirmButton = { Confirm() },
                    dismissButton = { Dismiss() },
                    state = state,
                    today = today,
                )
            }
        }
        rule.onNodeWithText(strings.next).assertIsDisplayed().performClick()
        rule.runOnIdle { assertEquals(DateTimePickerStep.Time, state.step) }
        rule.onNodeWithText(strings.back).assertIsDisplayed()
        assertButtonsWork()
    }

    @Test
    fun monthPickerKeepsItsButtonsOnScreen() {
        rule.setContent {
            MaterialTheme {
                OrreryMonthPickerDialog(
                    onDismissRequest = {},
                    confirmButton = { Confirm() },
                    dismissButton = { Dismiss() },
                    state = rememberOrreryMonthPickerState(initialSelectedMonth = YearMonth(2026, 3)),
                    today = today,
                )
            }
        }
        assertButtonsWork()
    }
}

/** Robolectric qualifiers for a 420dp-tall landscape window. */
internal const val COMPACT_HEIGHT_QUALIFIERS = "w800dp-h420dp-land"
