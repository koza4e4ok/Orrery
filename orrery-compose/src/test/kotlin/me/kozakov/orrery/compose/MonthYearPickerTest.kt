package me.kozakov.orrery.compose

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.datetime.YearMonth
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MonthYearPickerTest {
    @get:Rule
    val rule = createComposeRule()

    private var selected: YearMonth? = null

    private fun content(range: ClosedRange<YearMonth> = YearMonth(2020, 1)..YearMonth(2030, 12)) {
        rule.setContent {
            MaterialTheme {
                MonthYearPicker(
                    current = YearMonth(2026, 7),
                    range = range,
                    onSelect = { selected = it },
                )
            }
        }
    }

    @Test
    fun drillingDownSelectsAYearMonth() {
        content()
        rule.onNodeWithText("2027").performClick()
        rule.waitForIdle()
        rule.onNodeWithText("March").performClick()
        rule.runOnIdle { assertEquals(YearMonth(2027, 3), selected) }
    }

    @Test
    fun monthsOutsideTheRangeAreDisabled() {
        content(range = YearMonth(2026, 3)..YearMonth(2026, 10))
        rule.onNodeWithText("2026").performClick()
        rule.waitForIdle()
        rule.onNodeWithText("January").assertIsNotEnabled()
        rule.onNodeWithText("March").assertIsEnabled()
        rule.onNodeWithText("December").assertIsNotEnabled()
    }

    @Test
    fun yearHeaderNavigatesBackToTheYearGrid() {
        content()
        rule.onNodeWithText("2027").performClick()
        rule.waitForIdle()
        rule.onNodeWithText("2027").performClick() // month-grid header acts as back
        rule.waitForIdle()
        // the year grid scrolls to the current year's row, so assert a year
        // near 2026 (2020 would not be composed in the lazy grid)
        rule.onNodeWithText("2029").assertIsEnabled()
    }
}
