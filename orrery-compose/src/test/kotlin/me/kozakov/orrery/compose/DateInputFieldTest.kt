package me.kozakov.orrery.compose

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTextReplacement
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.datetime.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DateInputFieldTest {
    @get:Rule val rule = createComposeRule()

    private var value: LocalDate? = null
    private val strings = DatePickerDefaults.strings()

    private fun content(disabled: (LocalDate) -> Boolean = { false }) {
        rule.setContent {
            MaterialTheme {
                DateInputField(
                    value = null,
                    onValueChange = { value = it },
                    label = "Date",
                    bounds = LocalDate(2026, 1, 1)..LocalDate(2026, 12, 31),
                    isDisabled = disabled,
                    strings = strings,
                )
            }
        }
    }

    @Test
    fun validInputFiresParsedDate() {
        content()
        rule.onNodeWithText("Date").performTextReplacement("07/15/2026")
        rule.runOnIdle { assertEquals(LocalDate(2026, 7, 15), value) }
    }

    @Test
    fun garbageShowsFormatError() {
        content()
        rule.onNodeWithText("Date").performTextReplacement("potato")
        rule.onNodeWithText(strings.invalidFormatError.format("MM/DD/YYYY")).assertExists()
        rule.runOnIdle { assertNull(value) }
    }

    @Test
    fun impossibleDateShowsFormatError() {
        content()
        rule.onNodeWithText("Date").performTextReplacement("02/31/2026")
        rule.onNodeWithText(strings.invalidFormatError.format("MM/DD/YYYY")).assertExists()
        rule.runOnIdle { assertNull(value) }
    }

    @Test
    fun outOfBoundsShowsRangeError() {
        content()
        rule.onNodeWithText("Date").performTextReplacement("07/15/2030")
        rule.onNodeWithText(strings.outOfRangeError).assertExists()
        rule.runOnIdle { assertNull(value) }
    }

    @Test
    fun disabledDateShowsDisabledError() {
        content(disabled = { it == LocalDate(2026, 7, 20) })
        rule.onNodeWithText("Date").performTextReplacement("07/20/2026")
        rule.onNodeWithText(strings.disabledDateError).assertExists()
        rule.runOnIdle { assertNull(value) }
    }
}
