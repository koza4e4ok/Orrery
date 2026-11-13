package me.kozakov.orrery.compose

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.captureRoboImage
import kotlinx.datetime.LocalDate
import kotlinx.datetime.YearMonth
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The dual-month range picker at its narrowest: a window exactly
 * [DatePickerDefaults.MinDualMonthWidth] wide, with the Today button in the
 * header next to both month titles (#88b, step 3).
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w640dp-h800dp")
class DualMonthSnapshotTest {
    @get:Rule val rule = createComposeRule()

    @Test
    fun dualMonthAtTheMinimumWidthWithToday() {
        rule.setContent {
            MaterialTheme(colorScheme = lightColorScheme()) {
                OrreryDateRangePickerDialog(
                    onDismissRequest = {},
                    confirmButton = { TextButton(onClick = {}) { Text("Save") } },
                    state =
                        rememberOrreryDateRangePickerState(
                            initialStartDate = LocalDate(2026, 9, 24),
                            initialEndDate = LocalDate(2026, 10, 6),
                            initialDisplayedMonth = YearMonth(2026, 9),
                            yearRange = 2026..2027,
                        ),
                    today = LocalDate(2026, 7, 15),
                )
            }
        }
        // The default Adaptive mode shows two months at exactly 640dp, and
        // today's month (July) is in neither pane, so Today is shown.
        rule.onNodeWithContentDescription("September 2026 – October 2026").assertIsDisplayed()
        rule.onNodeWithText("Today").assertIsDisplayed()
        rule.onNode(isDialog()).captureRoboImage()
    }
}
