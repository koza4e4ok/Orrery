package me.kozakov.orrery.compose

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.datetime.LocalDate
import kotlinx.datetime.YearMonth
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
class LocalizedStringsTest {
    @get:Rule val rule = createComposeRule()

    private fun datePicker(strings: @Composable () -> DatePickerStrings = { DatePickerDefaults.strings() }) {
        rule.setContent {
            MaterialTheme {
                OrreryDatePickerDialog(
                    onDismissRequest = {},
                    confirmButton = { TextButton(onClick = {}) { Text("OK") } },
                    state =
                        rememberOrreryDatePickerState(
                            initialDisplayedMonth = YearMonth(2026, 7),
                            yearRange = 2026..2026,
                        ),
                    today = LocalDate(2026, 7, 15),
                    strings = strings(),
                )
            }
        }
    }

    private fun navHeader(previousMonthContentDescription: (@Composable () -> String)? = null) {
        rule.setContent {
            MaterialTheme {
                val state = rememberCalendarState(firstVisibleMonth = YearMonth(2026, 7))
                if (previousMonthContentDescription == null) {
                    CalendarNavHeader(state = state)
                } else {
                    CalendarNavHeader(
                        state = state,
                        previousMonthContentDescription = previousMonthContentDescription(),
                    )
                }
            }
        }
    }

    private fun heatmapStrings(): HeatmapStrings {
        lateinit var strings: HeatmapStrings
        rule.setContent { MaterialTheme { strings = HeatmapDefaults.strings() } }
        rule.waitForIdle()
        return strings
    }

    @Test
    fun englishDefaultsUnchanged() {
        datePicker()
        rule.onNodeWithText("Select date").assertExists()
        rule.onNodeWithContentDescription("Switch to text input").assertExists()
    }

    @Test
    fun englishNavHeaderDefaultsUnchanged() {
        navHeader()
        rule.onNodeWithContentDescription("Previous month").assertExists()
        rule.onNodeWithContentDescription("Next month").assertExists()
    }

    @Test
    fun englishHeatmapDefaultsUnchanged() {
        val strings = heatmapStrings()
        assertEquals("Less", strings.legendLess)
        assertEquals("More", strings.legendMore)
        assertEquals("%1\$s, level %2\$d of %3\$d", strings.dayDescription)
        assertEquals("%1\$s, no data", strings.dayDescriptionEmpty)
        assertEquals("%d active days", strings.summaryActiveDays)
        assertEquals("%d-day streak", strings.summaryStreak)
    }

    @Test
    @Config(qualifiers = "de")
    fun germanDatePickerTitle() {
        datePicker()
        rule.onNodeWithText("Datum auswählen").assertExists()
        rule.onNodeWithContentDescription("Zur Texteingabe wechseln").assertExists()
    }

    @Test
    @Config(qualifiers = "de")
    fun germanNavHeaderChevrons() {
        navHeader()
        rule.onNodeWithContentDescription("Vorheriger Monat").assertExists()
        rule.onNodeWithContentDescription("Nächster Monat").assertExists()
    }

    @Test
    @Config(qualifiers = "uk")
    fun ukrainianNavHeaderChevrons() {
        navHeader()
        rule.onNodeWithContentDescription("Попередній місяць").assertExists()
        rule.onNodeWithContentDescription("Наступний місяць").assertExists()
    }

    @Test
    @Config(qualifiers = "ja")
    fun japaneseDatePickerTitle() {
        datePicker()
        rule.onNodeWithText("日付を選択").assertExists()
    }

    @Test
    @Config(qualifiers = "ja")
    fun japaneseHeatmapTemplatesKeepPlaceholders() {
        val strings = heatmapStrings()
        assertEquals("7月15日、レベル 4/5", strings.dayDescription.format("7月15日", 4, 5))
        assertEquals("連続 3 日", strings.summaryStreak.format(3))
    }

    @Test
    @Config(qualifiers = "de")
    fun explicitOverrideWinsInNonEnglishLocale() {
        datePicker(strings = { DatePickerDefaults.strings(title = "Pick a day") })
        rule.onNodeWithText("Pick a day").assertExists()
        rule.onNodeWithText("Datum auswählen").assertDoesNotExist()
        // Parameters that are not overridden still follow the locale.
        rule.onNodeWithContentDescription("Zur Texteingabe wechseln").assertExists()
    }

    @Test
    @Config(qualifiers = "de")
    fun explicitNavHeaderOverrideWinsInNonEnglishLocale() {
        navHeader(previousMonthContentDescription = { "Back" })
        rule.onNodeWithContentDescription("Back").assertExists()
        rule.onNodeWithContentDescription("Vorheriger Monat").assertDoesNotExist()
        rule.onNodeWithContentDescription("Nächster Monat").assertExists()
    }
}
