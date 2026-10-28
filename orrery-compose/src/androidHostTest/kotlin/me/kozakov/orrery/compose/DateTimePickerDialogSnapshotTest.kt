package me.kozakov.orrery.compose

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
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

    @Test
    fun stepTransitionMidway() {
        content()
        rule.mainClock.autoAdvance = false
        // Clicking (rather than writing state) goes through the frame clock the test drives.
        rule.onNodeWithText(PlatformDateFormat.formatTime(9, 30, is24Hour = false)).performClick()
        rule.mainClock.advanceTimeBy(MIDWAY_MS)
        rule.onNode(isDialog()).captureRoboImage()
    }

    private companion object {
        const val MIDWAY_MS = 120L
    }
}
