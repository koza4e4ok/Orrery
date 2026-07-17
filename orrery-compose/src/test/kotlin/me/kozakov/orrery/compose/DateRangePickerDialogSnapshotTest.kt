package me.kozakov.orrery.compose

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.github.takahirom.roborazzi.captureRoboImage
import kotlinx.datetime.LocalDate
import kotlinx.datetime.YearMonth
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = RobolectricDeviceQualifiers.Pixel5)
class DateRangePickerDialogSnapshotTest {
    @get:Rule val rule = createComposeRule()

    private fun content(initialDisplayMode: DatePickerDisplayMode = DatePickerDisplayMode.Picker) {
        rule.setContent {
            MaterialTheme(
                colorScheme = if (isSystemInDarkTheme()) darkColorScheme() else lightColorScheme(),
            ) {
                val state =
                    rememberOrreryDateRangePickerState(
                        initialStartDate = LocalDate(2026, 7, 8),
                        initialEndDate = LocalDate(2026, 7, 12),
                        initialDisplayedMonth = YearMonth(2026, 7),
                        yearRange = 2026..2026,
                        initialDisplayMode = initialDisplayMode,
                    )
                OrreryDateRangePickerDialog(
                    onDismissRequest = {},
                    confirmButton = { TextButton(onClick = {}) { Text("Save") } },
                    state = state,
                    today = LocalDate(2026, 7, 15),
                )
            }
        }
    }

    @Test
    fun rangePickerLight() {
        content()
        rule.onNode(isDialog()).captureRoboImage()
    }

    @Test
    @Config(qualifiers = "+night")
    fun rangePickerDark() {
        content()
        rule.onNode(isDialog()).captureRoboImage()
    }

    @Test
    fun rangeInputLight() {
        content(initialDisplayMode = DatePickerDisplayMode.Input)
        rule.onNode(isDialog()).captureRoboImage()
    }

    @Test
    @Config(qualifiers = "+night")
    fun rangeInputDark() {
        content(initialDisplayMode = DatePickerDisplayMode.Input)
        rule.onNode(isDialog()).captureRoboImage()
    }
}
