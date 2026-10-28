package me.kozakov.orrery.compose

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.input.InputModeManager
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.LocalInputModeManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsNotFocused
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.requestFocus
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.YearMonth
import me.kozakov.orrery.core.CalendarEvent
import me.kozakov.orrery.core.EventSpan
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.GraphicsMode

/**
 * Keyboard focus beyond the month grid (#62): WeekCalendar shortcuts,
 * picker dialog initial focus, MonthYearPicker Escape drill-up and
 * focusable schedule/agenda event items.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class KeyboardFocusTest {
    @get:Rule
    val rule = createComposeRule()

    private lateinit var inputModeManager: InputModeManager

    @OptIn(ExperimentalComposeUiApi::class)
    private fun keyboardMode() {
        rule.runOnIdle { inputModeManager.requestInputMode(InputMode.Keyboard) }
    }

    private fun SemanticsNodeInteraction.keyboardFocus(): SemanticsNodeInteraction {
        keyboardMode()
        requestFocus()
        rule.waitForIdle()
        return this
    }

    private fun SemanticsNodeInteraction.press(key: Key) {
        keyboardFocus()
        performKeyInput { pressKey(key) }
        rule.waitForIdle()
    }

    // --- WeekCalendar: Mon Jul 6 .. Sun Jul 12, 2026 is the first visible week.

    private fun weekContent(): () -> WeekCalendarState {
        lateinit var state: WeekCalendarState
        rule.setContent {
            inputModeManager = LocalInputModeManager.current
            state =
                rememberWeekCalendarState(
                    startDate = LocalDate(2026, 6, 1),
                    endDate = LocalDate(2026, 9, 30),
                    firstVisibleDate = LocalDate(2026, 7, 9),
                    firstDayOfWeek = DayOfWeek.MONDAY,
                )
            WeekCalendar(
                state = state,
                dayContent = { day ->
                    DefaultDay(
                        day = day,
                        modifier = Modifier.testTag("day-${day.date}"),
                        today = LocalDate(2026, 7, 15),
                        animateSelection = false,
                    )
                },
            )
        }
        return { state }
    }

    private fun WeekCalendarState.firstDay(): LocalDate = firstVisibleWeek.days.first().date

    @Test
    fun weekCalendarRightOnTheLastColumnPagesToTheNextWeek() {
        val state = weekContent()
        rule.onNodeWithTag("day-2026-07-12").press(Key.DirectionRight)
        assertEquals(LocalDate(2026, 7, 13), state().firstDay())
        rule.onNodeWithTag("day-2026-07-13").assertIsFocused()
    }

    @Test
    fun weekCalendarLeftOnTheFirstColumnPagesToThePreviousWeek() {
        val state = weekContent()
        rule.onNodeWithTag("day-2026-07-06").press(Key.DirectionLeft)
        assertEquals(LocalDate(2026, 6, 29), state().firstDay())
        rule.onNodeWithTag("day-2026-07-05").assertIsFocused()
    }

    @Test
    fun weekCalendarUpAndDownPageByAWeek() {
        val state = weekContent()
        rule.onNodeWithTag("day-2026-07-08").press(Key.DirectionDown)
        assertEquals(LocalDate(2026, 7, 13), state().firstDay())
        rule.onNodeWithTag("day-2026-07-15").assertIsFocused()
        rule.onNodeWithTag("day-2026-07-15").press(Key.DirectionUp)
        assertEquals(LocalDate(2026, 7, 6), state().firstDay())
        rule.onNodeWithTag("day-2026-07-08").assertIsFocused()
    }

    @Test
    fun weekCalendarPageDownJumpsOneMonth() {
        val state = weekContent()
        rule.onNodeWithTag("day-2026-07-08").press(Key.PageDown)
        assertEquals(LocalDate(2026, 8, 3), state().firstDay())
        rule.onNodeWithTag("day-2026-08-08").assertIsFocused()
    }

    @Test
    fun weekCalendarHomeAndEndFocusTheEndsOfTheWeek() {
        weekContent()
        rule.onNodeWithTag("day-2026-07-08").press(Key.MoveHome)
        rule.onNodeWithTag("day-2026-07-06").assertIsFocused()
        rule.onNodeWithTag("day-2026-07-08").press(Key.MoveEnd)
        rule.onNodeWithTag("day-2026-07-12").assertIsFocused()
    }

    // --- Picker dialogs: initial focus.

    @Test
    fun datePickerOpenedInInputModeFocusesTheTextField() {
        lateinit var strings: DatePickerStrings
        rule.setContent {
            MaterialTheme {
                strings = DatePickerDefaults.strings()
                OrreryDatePickerDialog(
                    onDismissRequest = {},
                    confirmButton = { TextButton(onClick = {}) { Text("OK") } },
                    state =
                        rememberOrreryDatePickerState(
                            initialDisplayedMonth = YearMonth(2026, 7),
                            yearRange = 2026..2026,
                            initialDisplayMode = DatePickerDisplayMode.Input,
                        ),
                    today = LocalDate(2026, 7, 15),
                )
            }
        }
        rule.onNodeWithText(strings.inputLabel).assertIsFocused()
    }

    @Test
    fun datePickerTogglingToInputModeFocusesTheTextField() {
        lateinit var strings: DatePickerStrings
        rule.setContent {
            MaterialTheme {
                strings = DatePickerDefaults.strings()
                OrreryDatePickerDialog(
                    onDismissRequest = {},
                    confirmButton = { TextButton(onClick = {}) { Text("OK") } },
                    state =
                        rememberOrreryDatePickerState(
                            initialDisplayedMonth = YearMonth(2026, 7),
                            yearRange = 2026..2026,
                        ),
                    today = LocalDate(2026, 7, 15),
                )
            }
        }
        rule.onNodeWithContentDescription(strings.switchToInputDescription).performClick()
        rule.waitForIdle()
        rule.onNodeWithText(strings.inputLabel).assertIsFocused()
    }

    @Test
    fun dateRangePickerInputModeFocusesTheStartField() {
        lateinit var strings: DatePickerStrings
        rule.setContent {
            MaterialTheme {
                strings = DatePickerDefaults.strings()
                OrreryDateRangePickerDialog(
                    onDismissRequest = {},
                    confirmButton = { TextButton(onClick = {}) { Text("Save") } },
                    state =
                        rememberOrreryDateRangePickerState(
                            initialDisplayedMonth = YearMonth(2026, 7),
                            yearRange = 2026..2026,
                            initialDisplayMode = DatePickerDisplayMode.Input,
                        ),
                    today = LocalDate(2026, 7, 15),
                )
            }
        }
        rule.onNodeWithText(strings.rangeStartInputLabel).assertIsFocused()
        rule.onNodeWithText(strings.rangeEndInputLabel).assertIsNotFocused()
    }

    @Test
    fun initialDayFocusLandsOnTheActiveDateInKeyboardMode() {
        var show by mutableStateOf(false)
        rule.setContent {
            inputModeManager = LocalInputModeManager.current
            if (show) {
                val initialFocus = rememberInitialDayFocus(LocalDate(2026, 7, 10))
                HorizontalCalendar(
                    state =
                        rememberCalendarState(
                            startMonth = YearMonth(2026, 7),
                            endMonth = YearMonth(2026, 8),
                            firstVisibleMonth = YearMonth(2026, 7),
                            firstDayOfWeek = DayOfWeek.MONDAY,
                        ),
                ) { day ->
                    DefaultDay(
                        day = day,
                        modifier = initialFocus(day).testTag("day-${day.date}"),
                        today = LocalDate(2026, 7, 15),
                        animateSelection = false,
                    )
                }
            }
        }
        keyboardMode()
        rule.runOnIdle { show = true }
        rule.waitForIdle()
        rule.onNodeWithTag("day-2026-07-10").assertIsFocused()
    }

    // --- MonthYearPicker: Escape drills up one level.

    private fun pickerContent(startInMonths: Boolean = false) {
        rule.setContent {
            inputModeManager = LocalInputModeManager.current
            MaterialTheme {
                MonthYearPickerContent(
                    current = YearMonth(2026, 7),
                    range = YearMonth(2020, 1)..YearMonth(2030, 12),
                    onSelect = {},
                    startInMonths = startInMonths,
                )
            }
        }
    }

    @Test
    fun escapeInTheMonthGridReturnsToTheYearGrid() {
        pickerContent()
        rule.onNodeWithText("2027").performClick()
        rule.waitForIdle()
        rule.onNodeWithText("March").press(Key.Escape)
        rule.onNodeWithText("March").assertDoesNotExist()
        rule.onNodeWithText("2020 – 2030").assertIsFocused()
    }

    @Test
    fun escapeInTheDecadeGridReturnsToTheYearGrid() {
        pickerContent()
        rule.onNodeWithText("2020 – 2030").performClick()
        rule.waitForIdle()
        rule.onNodeWithText("2020s").press(Key.Escape)
        rule.onNodeWithText("2020s").assertDoesNotExist()
        rule.onNodeWithText("2020 – 2030").assertIsFocused()
    }

    @Test
    fun escapeInTheStartingMonthGridIsLeftToTheHost() {
        pickerContent(startInMonths = true)
        rule.onNodeWithText("March").press(Key.Escape)
        rule.onNodeWithText("March").assertExists()
    }

    // --- Schedule and agenda event items: focusable with a visible ring.

    private val marker = Color(0xFFFF0000)

    private val event =
        CalendarEvent(
            id = "standup",
            title = "Standup",
            span = EventSpan.Timed(LocalDateTime(2026, 7, 15, 9, 0), LocalDateTime(2026, 7, 15, 9, 30)),
            payload = "standup",
        )

    private fun SemanticsNodeInteraction.markerPixels(): Int {
        val pixels = captureToImage().toPixelMap()
        var count = 0
        for (x in 0 until pixels.width) {
            for (y in 0 until pixels.height) {
                val c = pixels[x, y]
                if (c.red > 0.8f && c.green < 0.25f && c.blue < 0.25f) count++
            }
        }
        return count
    }

    /** Renders [item] in a theme whose onSurface (the ring color) is the marker. */
    private fun eventItemContent(item: @Composable () -> Unit) {
        rule.setContent {
            inputModeManager = LocalInputModeManager.current
            MaterialTheme(colorScheme = lightColorScheme(onSurface = marker)) {
                Box(Modifier.testTag("item").padding(4.dp)) { item() }
            }
        }
    }

    private fun assertFocusRingDrawn(node: SemanticsNodeInteraction) {
        val before = rule.onNodeWithTag("item").markerPixels()
        node.keyboardFocus()
        node.assertIsFocused()
        val after = rule.onNodeWithTag("item").markerPixels()
        assertTrue("focus ring adds marker pixels ($before -> $after)", after > before)
    }

    @Test
    fun clickableAllDayChipShowsAFocusRing() {
        eventItemContent {
            ScheduleDefaults.AllDayEventChip(
                event = event,
                colors = ScheduleDefaults.colors(),
                modifier = Modifier.width(160.dp),
                onClick = {},
            )
        }
        assertFocusRingDrawn(rule.onNodeWithText("Standup"))
    }

    @Test
    fun clickableAgendaEventRowShowsAFocusRing() {
        eventItemContent {
            AgendaDefaults.EventRow(
                event = event,
                modifier = Modifier.width(240.dp),
                onClick = {},
                date = LocalDate(2026, 7, 15),
            )
        }
        assertFocusRingDrawn(rule.onNodeWithText("Standup"))
    }

    @Test
    fun dayScheduleEventTilesAreKeyboardFocusable() {
        rule.setContent {
            inputModeManager = LocalInputModeManager.current
            MaterialTheme {
                DaySchedule(
                    events = { date -> if (date == LocalDate(2026, 7, 15)) listOf(event) else emptyList() },
                    modifier = Modifier.fillMaxWidth().height(400.dp),
                    state = rememberScheduleState(firstVisibleDate = LocalDate(2026, 7, 15)),
                    today = LocalDate(2026, 7, 15),
                    onEventClick = {},
                )
            }
        }
        rule.onNodeWithText("Standup").keyboardFocus().assertIsFocused()
    }

    @Test
    fun agendaEventRowsAreKeyboardFocusable() {
        rule.setContent {
            inputModeManager = LocalInputModeManager.current
            MaterialTheme {
                Agenda(
                    events = { date -> if (date == LocalDate(2026, 7, 15)) listOf(event) else emptyList() },
                    modifier = Modifier.size(360.dp, 480.dp),
                    state =
                        rememberAgendaState(
                            startDate = LocalDate(2026, 7, 15),
                            endDate = LocalDate(2026, 7, 16),
                            initialVisibleDate = LocalDate(2026, 7, 15),
                        ),
                    today = LocalDate(2026, 7, 15),
                    onEventClick = {},
                )
            }
        }
        rule.onNodeWithText("Standup").keyboardFocus().assertIsFocused()
    }
}
