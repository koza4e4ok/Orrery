package me.kozakov.orrery.compose

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.YearMonth
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DateRangePresetsTest {
    @get:Rule val rule = createComposeRule()

    private val today = LocalDate(2026, 7, 15)
    private lateinit var state: OrreryDateRangePickerState
    private lateinit var strings: DatePickerStrings
    private lateinit var presets: List<DateRangePreset>

    private fun content(
        withPresets: Boolean = true,
        maxDays: Int? = null,
    ) {
        rule.setContent {
            MaterialTheme {
                strings = DatePickerDefaults.strings()
                presets = DatePickerDefaults.rangePresets(today = today, firstDayOfWeek = DayOfWeek.MONDAY)
                state =
                    rememberOrreryDateRangePickerState(
                        initialDisplayedMonth = YearMonth(2026, 7),
                        yearRange = 2026..2026,
                        maxDays = maxDays,
                    )
                if (withPresets) {
                    OrreryDateRangePickerDialog(
                        onDismissRequest = {},
                        confirmButton = { TextButton(onClick = {}) { Text("Save") } },
                        state = state,
                        today = today,
                        presets = presets,
                    )
                } else {
                    OrreryDateRangePickerDialog(
                        onDismissRequest = {},
                        confirmButton = { TextButton(onClick = {}) { Text("Save") } },
                        state = state,
                        today = today,
                    )
                }
            }
        }
    }

    @Test
    fun defaultPresetsComeFromSelectionPresets() {
        content()
        rule.runOnIdle {
            assertEquals(listOf("Last 7 days", "Last 30 days", "This week", "This month"), presets.map { it.label })
            assertEquals(
                listOf(
                    LocalDate(2026, 7, 9)..LocalDate(2026, 7, 15),
                    LocalDate(2026, 6, 16)..LocalDate(2026, 7, 15),
                    LocalDate(2026, 7, 13)..LocalDate(2026, 7, 19),
                    LocalDate(2026, 7, 1)..LocalDate(2026, 7, 31),
                ),
                presets.map { it.range },
            )
        }
    }

    @Test
    fun noPresetRowByDefault() {
        content(withPresets = false)
        rule.onNodeWithText("This week").assertDoesNotExist()
    }

    @Test
    fun tappingAPresetSelectsItsRange() {
        content()
        rule.onNodeWithText("This week").assertIsNotSelected().performClick()
        rule.onNodeWithText("This week").assertIsSelected()
        rule.onNodeWithText("This month").assertIsNotSelected()
        rule.runOnIdle {
            assertEquals(LocalDate(2026, 7, 13), state.selectedStartDate)
            assertEquals(LocalDate(2026, 7, 19), state.selectedEndDate)
        }
    }

    @Test
    fun presetRejectedByTheEngineKeepsTheSelection() {
        content(maxDays = 10)
        rule.onNodeWithText("Last 30 days").performClick()
        rule.runOnIdle {
            assertNull(state.selectedStartDate)
            assertNull(state.selectedEndDate)
        }
        rule.onNodeWithText("Last 30 days").assertIsNotSelected()
    }

    @Test
    fun presetsAreHiddenInInputMode() {
        content()
        rule.onNodeWithText("This month").assertExists()
        rule.onNodeWithContentDescription(strings.switchToInputDescription).performClick()
        rule.onNodeWithText("This month").assertDoesNotExist()
    }
}
