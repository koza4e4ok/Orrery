package me.kozakov.orrery.compose

import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.github.takahirom.roborazzi.captureRoboImage
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = RobolectricDeviceQualifiers.Pixel5)
class DateTimePickerDialogSnapshotTest {
    @get:Rule val rule = createComposeRule()

    private lateinit var state: OrreryDateTimePickerState

    private fun content() {
        rule.setContent {
            MaterialTheme(colorScheme = lightColorScheme()) {
                state =
                    rememberOrreryDateTimePickerState(
                        initialDateTime = LocalDateTime(2026, 7, 8, 9, 30),
                        bounds = LocalDate(2026, 1, 1)..LocalDate(2026, 12, 31),
                        is24Hour = false,
                    )
                OrreryDateTimePickerDialog(
                    onDismissRequest = {},
                    confirmButton = { TextButton(onClick = {}) { Text("OK") } },
                    dismissButton = { TextButton(onClick = {}) { Text("Cancel") } },
                    state = state,
                    today = LocalDate(2026, 7, 15),
                )
            }
        }
    }

    @Test
    fun dateStepLight() {
        content()
        rule.onNode(isDialog()).captureRoboImage()
    }

    @Test
    fun timeStepLight() {
        content()
        rule.runOnIdle { state.step = DateTimePickerStep.Time }
        rule.waitForIdle()
        rule.onNode(isDialog()).captureRoboImage()
    }

    // Captured on the step body alone: the dialog window does not re-lay out
    // while the test clock is paused.
    @Test
    fun stepTransitionMidway() {
        rule.setContent {
            MaterialTheme(colorScheme = lightColorScheme()) {
                Surface {
                    state =
                        rememberOrreryDateTimePickerState(
                            initialDateTime = LocalDateTime(2026, 7, 8, 9, 30),
                            bounds = LocalDate(2026, 1, 1)..LocalDate(2026, 12, 31),
                            is24Hour = false,
                        )
                    DateTimeStepBody(
                        state = state,
                        timeInput = false,
                        colors = CalendarDefaults.dayColors(),
                        shapes = CalendarDefaults.dayShapes(),
                        today = LocalDate(2026, 7, 15),
                        dateStrings = DatePickerDefaults.strings(),
                        modifier = Modifier.width(360.dp),
                    )
                }
            }
        }
        rule.mainClock.autoAdvance = false
        rule.runOnIdle { Snapshot.withMutableSnapshot { state.step = DateTimePickerStep.Time } }
        rule.mainClock.advanceTimeBy(MIDWAY_MS)
        rule.onRoot().captureRoboImage()
    }

    private companion object {
        const val MIDWAY_MS = 120L
    }
}
