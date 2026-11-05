package me.kozakov.orrery.compose

import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.datetime.YearMonth
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.GraphicsMode

// Native graphics so month names are measured with real font metrics.
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class MonthYearPickerLayoutTest {
    @get:Rule val rule = createComposeRule()

    @Test
    fun narrowMonthGridFallsBackToShortMonthNames() {
        rule.setContent {
            MaterialTheme {
                MonthYearPicker(
                    current = YearMonth(2026, 7),
                    range = YearMonth(2020, 1)..YearMonth(2030, 12),
                    onSelect = {},
                    modifier = Modifier.width(240.dp),
                )
            }
        }
        rule.onNodeWithText("2026").performClick()
        rule.waitForIdle()
        rule.onNodeWithText("Sep").assertExists()
        rule.onNodeWithText("September").assertDoesNotExist()
        rule.onNodeWithContentDescription("September").assertExists()
    }
}
