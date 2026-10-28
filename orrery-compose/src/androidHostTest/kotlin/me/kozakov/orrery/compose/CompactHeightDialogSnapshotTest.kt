package me.kozakov.orrery.compose

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.captureRoboImage
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.YearMonth
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = COMPACT_HEIGHT_QUALIFIERS)
class CompactHeightDialogSnapshotTest {
    @get:Rule val rule = createComposeRule()

    private val today = LocalDate(2026, 7, 15)

    @Test
    fun datePickerCompactHeight() {
        rule.setContent {
            MaterialTheme(colorScheme = lightColorScheme()) {
                OrreryDatePickerDialog(
                    onDismissRequest = {},
                    confirmButton = { TextButton(onClick = {}) { Text("OK") } },
                    dismissButton = { TextButton(onClick = {}) { Text("Cancel") } },
                    state = rememberOrreryDatePickerState(initialDate = LocalDate(2026, 7, 8)),
                    today = today,
                )
            }
        }
        rule.onNode(isDialog()).captureRoboImage()
    }

    @Test
    fun dateTimePickerTimeStepCompactHeight() {
        rule.setContent {
            MaterialTheme(colorScheme = lightColorScheme()) {
                val state =
                    rememberOrreryDateTimePickerState(
                        initialDateTime = LocalDateTime(2026, 7, 8, 9, 30),
                        is24Hour = false,
                    )
                LaunchedEffect(state) { state.step = DateTimePickerStep.Time }
                OrreryDateTimePickerDialog(
                    onDismissRequest = {},
                    confirmButton = { TextButton(onClick = {}) { Text("OK") } },
                    dismissButton = { TextButton(onClick = {}) { Text("Cancel") } },
                    state = state,
                    today = today,
                )
            }
        }
        rule.onNode(isDialog()).captureRoboImage()
    }

    @Test
    fun monthPickerCompactHeight() {
        rule.setContent {
            MaterialTheme(colorScheme = lightColorScheme()) {
                OrreryMonthPickerDialog(
                    onDismissRequest = {},
                    confirmButton = { TextButton(onClick = {}) { Text("OK") } },
                    dismissButton = { TextButton(onClick = {}) { Text("Cancel") } },
                    state = rememberOrreryMonthPickerState(initialSelectedMonth = YearMonth(2026, 3)),
                    today = today,
                )
            }
        }
        rule.onNode(isDialog()).captureRoboImage()
    }

    @Test
    fun dateRangePickerCompactHeight() {
        rule.setContent {
            MaterialTheme(colorScheme = lightColorScheme()) {
                OrreryDateRangePickerDialog(
                    onDismissRequest = {},
                    confirmButton = { TextButton(onClick = {}) { Text("Save") } },
                    state =
                        rememberOrreryDateRangePickerState(
                            initialStartDate = LocalDate(2026, 7, 8),
                            initialEndDate = LocalDate(2026, 7, 12),
                        ),
                    today = today,
                    presets = DatePickerDefaults.rangePresets(today),
                )
            }
        }
        rule.onNode(isDialog()).captureRoboImage()
    }
}
