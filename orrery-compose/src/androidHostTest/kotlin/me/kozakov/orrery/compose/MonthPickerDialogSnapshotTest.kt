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
class MonthPickerDialogSnapshotTest {
    @get:Rule val rule = createComposeRule()

    private fun content() {
        rule.setContent {
            MaterialTheme(
                colorScheme = if (isSystemInDarkTheme()) darkColorScheme() else lightColorScheme(),
            ) {
                OrreryMonthPickerDialog(
                    onDismissRequest = {},
                    confirmButton = { TextButton(onClick = {}) { Text("OK") } },
                    dismissButton = { TextButton(onClick = {}) { Text("Cancel") } },
                    state =
                        rememberOrreryMonthPickerState(
                            initialSelectedMonth = YearMonth(2026, 3),
                            range = YearMonth(2026, 2)..YearMonth(2046, 2),
                        ),
                    today = LocalDate(2026, 7, 15),
                )
            }
        }
    }

    @Test
    fun monthPickerLight() {
        content()
        rule.onNode(isDialog()).captureRoboImage()
    }

    @Test
    @Config(qualifiers = "+night")
    fun monthPickerDark() {
        content()
        rule.onNode(isDialog()).captureRoboImage()
    }
}
