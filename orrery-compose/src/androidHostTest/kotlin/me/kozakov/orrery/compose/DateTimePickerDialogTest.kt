package me.kozakov.orrery.compose

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import me.kozakov.orrery.core.DisabledDates
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

// The two-step dialog is taller than Robolectric's default 470dp window, which
// leaves Next outside it; a Pixel 5 screen keeps every button clickable.
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = RobolectricDeviceQualifiers.Pixel5)
class DateTimePickerDialogTest {
    @get:Rule val rule = createComposeRule()

    private lateinit var state: OrreryDateTimePickerState
    private lateinit var strings: DateTimePickerStrings
    private lateinit var dateStrings: DatePickerStrings

    private fun content(
        initialDateTime: LocalDateTime? = null,
        is24Hour: Boolean = true,
        bounds: ClosedRange<LocalDate>? = LocalDate(2026, 7, 1)..LocalDate(2026, 7, 31),
        disabled: DisabledDates = DisabledDates.None,
    ) {
        rule.setContent {
            MaterialTheme {
                strings = DatePickerDefaults.dateTimeStrings()
                dateStrings = DatePickerDefaults.strings()
                state =
                    rememberOrreryDateTimePickerState(
                        initialDateTime = initialDateTime,
                        bounds = bounds,
                        disabled = disabled,
                        is24Hour = is24Hour,
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
    fun nextIsDisabledUntilADateIsPicked() {
        content()
        rule.onNodeWithText(strings.next).assertIsNotEnabled()
        rule.onNodeWithText("OK").assertDoesNotExist()
        rule.onNodeWithText("10").performClick()
        rule.onNodeWithText(strings.next).assertIsEnabled()
        rule.runOnIdle { assertEquals(LocalDateTime(2026, 7, 10, 0, 0), state.selectedDateTime) }
    }

    @Test
    fun nextOpensTheTimeStepAndBackKeepsTheDate() {
        content()
        rule.onNodeWithText("10").performClick()
        rule.onNodeWithText(strings.next).performClick()
        rule.onNodeWithText(strings.timeTitle).assertExists()
        rule.onNodeWithText("OK").assertExists()
        rule.onNodeWithText(strings.back).performClick()
        rule.onNodeWithText(strings.title).assertExists()
        rule.runOnIdle {
            assertEquals(DateTimePickerStep.Date, state.step)
            assertEquals(LocalDate(2026, 7, 10), state.dateState.selectedDate)
        }
    }

    @Test
    fun headlineTimeJumpsToTheTimeStep() {
        content(initialDateTime = LocalDateTime(2026, 7, 8, 9, 30))
        rule.onNodeWithText("09:30").performClick()
        rule.runOnIdle { assertEquals(DateTimePickerStep.Time, state.step) }
    }

    @Test
    fun timeEditsUpdateSelectedDateTime() {
        content(initialDateTime = LocalDateTime(2026, 7, 8, 9, 30))
        rule.runOnIdle { state.step = DateTimePickerStep.Time }
        rule.runOnIdle {
            state.hour = 14
            state.minute = 5
        }
        rule.onNodeWithText("14:05").assertExists()
        rule.runOnIdle { assertEquals(LocalDateTime(2026, 7, 8, 14, 5), state.selectedDateTime) }
    }

    @Test
    fun outOfRangeTimeWritesAreIgnored() {
        content(initialDateTime = LocalDateTime(2026, 7, 8, 9, 30))
        rule.runOnIdle {
            state.hour = 24
            state.minute = -1
            assertEquals(9, state.hour)
            assertEquals(30, state.minute)
        }
    }

    @Test
    fun twelveHourClockIsHonored() {
        content(initialDateTime = LocalDateTime(2026, 7, 8, 14, 5), is24Hour = false)
        // The headline uses the locale's 12-hour format ("2:05 PM", with a narrow no-break space on newer ICU).
        val headline = PlatformDateFormat.formatTime(14, 5, is24Hour = false)
        assertEquals("2:05 PM", headline.replace('\u202F', ' '))
        rule.onNodeWithText(headline).assertExists()
        rule.runOnIdle { assertEquals(false, state.is24Hour) }
    }

    @Test
    fun timeStepTogglesBetweenDialAndKeyboard() {
        content(initialDateTime = LocalDateTime(2026, 7, 8, 9, 30))
        rule.onNodeWithText(strings.next).performClick()
        rule.onNodeWithContentDescription(strings.switchToKeyboard).performClick()
        rule.onNodeWithContentDescription(strings.switchToClock).assertExists()
    }

    @Test
    fun boundsAndDisabledDatesApplyToTheDate() {
        content(disabled = DisabledDates { dates(LocalDate(2026, 7, 10)) })
        rule.runOnIdle {
            state.dateState.selectedDate = LocalDate(2026, 8, 1)
            assertNull(state.selectedDateTime)
            state.dateState.selectedDate = LocalDate(2026, 7, 10)
            assertNull(state.selectedDateTime)
            state.dateState.selectedDate = LocalDate(2026, 7, 12)
            assertEquals(LocalDate(2026, 7, 12), state.selectedDateTime?.date)
        }
    }

    @Test
    fun stateSurvivesRestoration() {
        val restorationTester = StateRestorationTester(rule)
        restorationTester.setContent { state = rememberOrreryDateTimePickerState(is24Hour = true) }
        rule.runOnIdle {
            state.dateState.selectedDate = LocalDate(2026, 7, 15)
            state.hour = 18
            state.minute = 45
            state.step = DateTimePickerStep.Time
            state.is24Hour = false
        }
        restorationTester.emulateSavedInstanceStateRestore()
        rule.runOnIdle {
            assertEquals(LocalDateTime(2026, 7, 15, 18, 45), state.selectedDateTime)
            assertEquals(DateTimePickerStep.Time, state.step)
            assertEquals(false, state.is24Hour)
        }
    }

    @Test
    fun headlinePillMarksTheActiveStep() {
        content(initialDateTime = LocalDateTime(2026, 7, 8, 9, 30))
        val dateText = PlatformDateFormat.formatDate(LocalDate(2026, 7, 8), DateFormattingStyle.Medium)
        rule.onNodeWithText(dateText).assertIsSelected()
        rule.onNodeWithText("09:30").assertIsNotSelected().performClick()
        rule.onNodeWithText("09:30").assertIsSelected()
        rule.onNodeWithText(dateText).assertIsNotSelected().performClick()
        rule.onNodeWithText(dateText).assertIsSelected()
        rule.runOnIdle { assertEquals(DateTimePickerStep.Date, state.step) }
    }

    @Test
    fun timePillIsDisabledUntilADateIsPicked() {
        content()
        rule.onNodeWithText("00:00").assertIsNotEnabled().assertIsNotSelected()
        rule.onNodeWithText("10").performClick()
        rule.onNodeWithText("00:00").assertIsEnabled()
    }

    // The step body is tested outside the Dialog: the dialog window does not
    // re-lay out while the test clock is paused, so mid-transition frames are
    // only observable on the main root.
    @Test
    fun switchingStepsSlidesTheDateBodyOut() {
        rule.setContent {
            MaterialTheme {
                dateStrings = DatePickerDefaults.strings()
                state =
                    rememberOrreryDateTimePickerState(
                        initialDateTime = LocalDateTime(2026, 7, 8, 9, 30),
                        is24Hour = true,
                    )
                DateTimeStepBody(
                    state = state,
                    timeInput = false,
                    colors = CalendarDefaults.dayColors(),
                    shapes = CalendarDefaults.dayShapes(),
                    today = LocalDate(2026, 7, 15),
                    dateStrings = dateStrings,
                )
            }
        }
        val nextMonth = rule.onNodeWithContentDescription(dateStrings.nextMonthDescription)
        val restingLeft = nextMonth.fetchSemanticsNode().boundsInRoot.left
        rule.mainClock.autoAdvance = false
        rule.runOnIdle { Snapshot.withMutableSnapshot { state.step = DateTimePickerStep.Time } }
        rule.mainClock.advanceTimeBy(MIDWAY_MS)
        // Mid-transition the outgoing Date body is still there, sliding towards the start.
        val midwayLeft = nextMonth.fetchSemanticsNode().boundsInRoot.left
        assertTrue("expected the date body to slide left: $restingLeft -> $midwayLeft", midwayLeft < restingLeft)
        rule.mainClock.advanceTimeBy(SETTLED_MS)
        nextMonth.assertDoesNotExist()
    }

    private companion object {
        const val MIDWAY_MS = 100L
        const val SETTLED_MS = 1_000L
    }
}
