package me.kozakov.orrery.compose

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.DpRect
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.Month
import kotlinx.datetime.YearMonth
import me.kozakov.orrery.core.CalendarEvent
import me.kozakov.orrery.core.EventSpan
import me.kozakov.orrery.core.eventsByDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** 200 % system text on a Pixel 5. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = RobolectricDeviceQualifiers.Pixel5, fontScale = 2f)
class LargeFontPixel5LayoutTest : LargeFontLayoutTest()

/** 200 % system text in a 320 dp window: small phones, split screen, "Largest" display size. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = NARROW_WINDOW_QUALIFIERS, fontScale = 2f)
class LargeFontNarrowLayoutTest : LargeFontLayoutTest()

internal const val NARROW_WINDOW_QUALIFIERS = "w320dp-h640dp-xhdpi"

/**
 * Key nodes stay on screen and inside their container, and their text is not
 * cut off (no ellipsis or clipped line; where a line count is given, no word
 * broken over lines either). Subclasses pick the window and font scale.
 */
abstract class LargeFontLayoutTest {
    @get:Rule val rule = createComposeRule()

    // Time labels follow the device clock; these tests assert 24-hour text.
    @Before
    fun use24HourClock() = setSystem24HourClock(true)

    private fun SemanticsNodeInteraction.textLayout(): TextLayoutResult {
        val results = mutableListOf<TextLayoutResult>()
        performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(results) }
        return results.single()
    }

    private fun assertInside(
        what: String,
        inner: DpRect,
        outer: DpRect,
        vertically: Boolean,
    ) {
        val slack = 0.5.dp
        val horizontal = inner.left >= outer.left - slack && inner.right <= outer.right + slack
        val vertical = !vertically || (inner.top >= outer.top - slack && inner.bottom <= outer.bottom + slack)
        assertTrue("$what $inner is outside $outer", horizontal && vertical)
    }

    // Horizontal containment only by default: dialog bodies scroll vertically.
    private fun assertInside(
        what: String,
        node: SemanticsNodeInteraction,
        container: DpRect,
        vertically: Boolean = false,
    ) {
        node.assertIsDisplayed()
        assertInside(what, node.getUnclippedBoundsInRoot(), container, vertically)
    }

    private fun assertTextFits(
        what: String,
        node: SemanticsNodeInteraction,
        container: DpRect,
        maxLines: Int = Int.MAX_VALUE,
        vertically: Boolean = false,
    ) {
        val layout = node.textLayout()
        assertFalse("$what is cut off (${layout.size}, ${layout.lineCount} lines)", layout.hasVisualOverflow)
        assertTrue("$what wraps to ${layout.lineCount} lines", layout.lineCount <= maxLines)
        assertInside(what, node, container, vertically)
    }

    private fun assertTextFits(
        text: String,
        container: DpRect,
        maxLines: Int = Int.MAX_VALUE,
        vertically: Boolean = false,
    ) = assertTextFits("\"$text\"", rule.onNodeWithText(text, useUnmergedTree = true), container, maxLines, vertically)

    private fun surface(): DpRect {
        val surface = rule.onNodeWithTag(SURFACE).getUnclippedBoundsInRoot()
        // The dialog's own layout is at most as wide as the window.
        assertInside("dialog surface", surface, rule.onNode(isDialog()).getUnclippedBoundsInRoot(), vertically = true)
        return surface
    }

    private fun assertButtonsFit(surface: DpRect) {
        assertTextFits("Cancel", surface, maxLines = 1, vertically = true)
        assertTextFits("OK", surface, maxLines = 1, vertically = true)
    }

    private fun assertChevronsFit(container: DpRect) {
        assertInside("previous chevron", rule.onNodeWithContentDescription("Previous month"), container)
        assertInside("next chevron", rule.onNodeWithContentDescription("Next month"), container)
    }

    private fun datePicker(initialDisplayMode: DatePickerDisplayMode = DatePickerDisplayMode.Picker) {
        rule.setContent {
            MaterialTheme {
                OrreryDatePickerDialog(
                    onDismissRequest = {},
                    confirmButton = { TextButton(onClick = {}) { Text("OK") } },
                    dismissButton = { TextButton(onClick = {}) { Text("Cancel") } },
                    state =
                        rememberOrreryDatePickerState(
                            initialDate = LocalDate(2026, 9, 28),
                            yearRange = 2020..2030,
                            initialDisplayMode = initialDisplayMode,
                        ),
                    modifier = Modifier.testTag(SURFACE),
                    today = LocalDate(2026, 9, 15),
                )
            }
        }
    }

    @Test
    fun datePickerDialogFitsTheWindow() {
        datePicker()
        val surface = surface()
        assertTextFits("Select date", surface, vertically = true)
        assertTextFits("Sep 28, 2026", surface, vertically = true)
        assertChevronsFit(surface)
        assertTextFits("September 2026", surface)
        assertInside(
            "September 30",
            rule.onAllNodes(hasContentDescription("September 30, 2026", substring = true)).onFirst(),
            surface,
        )
        assertButtonsFit(surface)
    }

    @Test
    fun datePickerTextInputFitsTheWindow() {
        datePicker(DatePickerDisplayMode.Input)
        val surface = surface()
        assertInside("date field", rule.onNode(hasSetTextAction()), surface)
        assertButtonsFit(surface)
    }

    @Test
    fun datePickerYearGridFitsTheWindow() {
        datePicker()
        rule.onNodeWithText("September 2026").performClick()
        rule.waitForIdle()
        val surface = surface()
        assertTextFits("2020 – 2030", surface)
        // The grid is lazy: check the years on screen.
        var checked = 0
        for (year in 2020..2030) {
            val nodes = rule.onAllNodesWithText(year.toString(), useUnmergedTree = true)
            if (nodes.fetchSemanticsNodes().isNotEmpty()) {
                assertTextFits("year $year", nodes.onFirst(), surface, maxLines = 1)
                checked++
            }
        }
        assertTrue("no years on screen", checked > 0)
        assertButtonsFit(surface)
    }

    @Test
    fun monthPickerDialogFitsTheWindow() {
        rule.setContent {
            MaterialTheme {
                OrreryMonthPickerDialog(
                    onDismissRequest = {},
                    confirmButton = { TextButton(onClick = {}) { Text("OK") } },
                    dismissButton = { TextButton(onClick = {}) { Text("Cancel") } },
                    state = rememberOrreryMonthPickerState(initialSelectedMonth = YearMonth(2026, 9)),
                    modifier = Modifier.testTag(SURFACE),
                    today = LocalDate(2026, 9, 15),
                )
            }
        }
        val surface = surface()
        assertTextFits("September 2026", surface, vertically = true)
        assertTextFits("2026", surface, maxLines = 1)
        var checked = 0
        for (month in Month.entries) {
            for (short in listOf(false, true)) {
                val name = PlatformDateFormat.monthName(month, short = short)
                val nodes = rule.onAllNodesWithText(name, useUnmergedTree = true)
                if (nodes.fetchSemanticsNodes().isNotEmpty()) {
                    assertTextFits(name, nodes.onFirst(), surface, maxLines = 1)
                    checked++
                }
            }
        }
        assertTrue("no month names on screen", checked > 0)
        assertButtonsFit(surface)
    }

    @Test
    fun rangePickerDialogFitsTheWindow() {
        rule.setContent {
            MaterialTheme {
                OrreryDateRangePickerDialog(
                    onDismissRequest = {},
                    confirmButton = { TextButton(onClick = {}) { Text("Save") } },
                    state =
                        rememberOrreryDateRangePickerState(
                            initialStartDate = LocalDate(2026, 9, 28),
                            initialEndDate = LocalDate(2026, 10, 30),
                            initialDisplayedMonth = YearMonth(2026, 9),
                        ),
                    modifier = Modifier.testTag(SURFACE),
                    today = LocalDate(2026, 9, 15),
                )
            }
        }
        val surface = surface()
        assertInside("close", rule.onNodeWithContentDescription("Close"), surface, vertically = true)
        assertTextFits("Select dates", surface, vertically = true)
        assertTextFits("Sep 28, 2026 – Oct 30, 2026", surface, vertically = true)
        assertTextFits("Save", surface, maxLines = 1, vertically = true)
    }

    private lateinit var dateTimeState: OrreryDateTimePickerState

    private fun dateTimePicker() {
        rule.setContent {
            MaterialTheme {
                dateTimeState =
                    rememberOrreryDateTimePickerState(
                        initialDateTime = LocalDateTime(2026, 9, 28, 9, 30),
                        bounds = LocalDate(2026, 1, 1)..LocalDate(2026, 12, 31),
                        is24Hour = true,
                    )
                OrreryDateTimePickerDialog(
                    onDismissRequest = {},
                    confirmButton = { TextButton(onClick = {}) { Text("OK") } },
                    dismissButton = { TextButton(onClick = {}) { Text("Cancel") } },
                    state = dateTimeState,
                    modifier = Modifier.testTag(SURFACE),
                    today = LocalDate(2026, 9, 15),
                )
            }
        }
    }

    private fun assertStepPillsFit(surface: DpRect) {
        val pills =
            rule.onAllNodes(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Tab), useUnmergedTree = true)
        assertEquals("date and time pills", 2, pills.fetchSemanticsNodes().size)
        assertTextFits("date pill", pills[0], surface, vertically = true)
        assertTextFits("time pill", pills[1], surface, vertically = true)
    }

    @Test
    fun dateTimePickerDateStepFitsTheWindow() {
        dateTimePicker()
        val surface = surface()
        assertStepPillsFit(surface)
        assertChevronsFit(surface)
        assertButtonsFit(surface)
    }

    @Test
    fun dateTimePickerTimeStepFitsTheWindow() {
        dateTimePicker()
        rule.runOnIdle { dateTimeState.step = DateTimePickerStep.Time }
        rule.waitForIdle()
        val surface = surface()
        assertStepPillsFit(surface)
        assertButtonsFit(surface)
    }

    @Test
    fun navHeaderKeepsChevronsDropdownAndTodayOnScreen() {
        rule.setContent {
            MaterialTheme {
                Surface {
                    CalendarNavHeader(
                        state =
                            rememberCalendarState(
                                startMonth = YearMonth(2026, 1),
                                endMonth = YearMonth(2026, 12),
                                firstVisibleMonth = YearMonth(2026, 9),
                                firstDayOfWeek = DayOfWeek.MONDAY,
                            ),
                        modifier = Modifier.testTag("header"),
                        onTitleClick = {},
                        showTodayButton = true,
                        today = YearMonth(2026, 7),
                    )
                }
            }
        }
        val header = rule.onNodeWithTag("header").getUnclippedBoundsInRoot()
        assertChevronsFit(header)
        assertTextFits("September 2026", header)
        val dropdown = rule.onNode(SemanticsMatcher.keyIsDefined(DropdownRotationKey), useUnmergedTree = true)
        val triangle = dropdown.getUnclippedBoundsInRoot()
        assertTrue("dropdown triangle is ${triangle.right - triangle.left} wide", triangle.right - triangle.left > 1.dp)
        assertInside("dropdown triangle", dropdown, header)
        assertTextFits("Today", header, maxLines = 1)
    }

    private val jul15 = LocalDate(2026, 7, 15)

    private val scheduleEvents =
        listOf(
            CalendarEvent<Nothing>(
                id = "review",
                title = "Design review",
                span = EventSpan.Timed(LocalDateTime(2026, 7, 15, 9, 0), LocalDateTime(2026, 7, 15, 10, 0)),
            ),
        ).eventsByDate(LocalDate(2026, 7, 1)..LocalDate(2026, 7, 31))

    @Test
    fun dayScheduleTileAndLabelsFit() {
        rule.setContent {
            MaterialTheme {
                DaySchedule(
                    events = { date -> scheduleEvents[date].orEmpty() },
                    modifier = Modifier.testTag("schedule").fillMaxWidth().height(480.dp),
                    state =
                        rememberScheduleState(
                            firstVisibleDate = jul15,
                            firstDayOfWeek = DayOfWeek.MONDAY,
                            initialScrollTime = LocalTime(8, 0),
                        ),
                    today = jul15,
                )
            }
        }
        val schedule = rule.onNodeWithTag("schedule").getUnclippedBoundsInRoot()
        assertTextFits("Design review", schedule, maxLines = 1)
        assertTextFits("09:00", schedule, maxLines = 1)
        assertTextFits("15", schedule, maxLines = 1)
    }

    @Test
    fun weekScheduleHeadersFit() {
        rule.setContent {
            MaterialTheme {
                WeekSchedule(
                    events = { date -> scheduleEvents[date].orEmpty() },
                    modifier = Modifier.testTag("schedule").fillMaxWidth().height(480.dp),
                    state = rememberScheduleState(firstVisibleDate = jul15, firstDayOfWeek = DayOfWeek.MONDAY),
                    today = jul15,
                )
            }
        }
        val schedule = rule.onNodeWithTag("schedule").getUnclippedBoundsInRoot()
        for (day in 13..19) {
            val dayOfWeek = LocalDate(2026, 7, day).dayOfWeek
            assertTextFits("$day", schedule, maxLines = 1)
            // A header may fall back to the narrow weekday name; either must fit.
            val full =
                rule.onAllNodesWithText(
                    PlatformDateFormat.weekdayName(dayOfWeek, narrow = false),
                    ignoreCase = true,
                    useUnmergedTree = true,
                )
            val narrow =
                rule.onAllNodesWithText(
                    PlatformDateFormat.weekdayName(dayOfWeek, narrow = true),
                    ignoreCase = true,
                    useUnmergedTree = true,
                )
            val header = if (full.fetchSemanticsNodes().isNotEmpty()) full.onFirst() else narrow.onFirst()
            assertTextFits("$dayOfWeek header", header, schedule, maxLines = 1)
        }
    }

    @Test
    fun agendaRowsFit() {
        val events =
            listOf(
                CalendarEvent<Nothing>(
                    id = "standup",
                    title = "Team standup",
                    span = EventSpan.Timed(LocalDateTime(2026, 7, 15, 9, 0), LocalDateTime(2026, 7, 15, 9, 30)),
                    subtitle = "Room 302",
                ),
                CalendarEvent<Nothing>(id = "offsite", title = "Offsite", span = EventSpan.AllDay(jul15)),
            ).eventsByDate(LocalDate(2026, 7, 1)..LocalDate(2026, 7, 31))
        rule.setContent {
            MaterialTheme {
                Surface {
                    Agenda(
                        events = { date -> events[date].orEmpty() },
                        state =
                            rememberAgendaState(
                                startDate = LocalDate(2026, 7, 1),
                                endDate = LocalDate(2026, 7, 31),
                                initialVisibleDate = jul15,
                            ),
                        modifier = Modifier.testTag("agenda").fillMaxWidth().height(560.dp),
                        today = jul15,
                    )
                }
            }
        }
        val agenda = rule.onNodeWithTag("agenda").getUnclippedBoundsInRoot()
        assertTextFits("Team standup", agenda)
        assertTextFits("Room 302", agenda)
        assertTextFits("09:00 – 09:30", agenda)
        assertTextFits("Offsite", agenda)
        assertTextFits("All day", agenda)
    }

    @Test
    fun yearCalendarMonthsAreWideEnoughForTheirDays() {
        rule.setContent {
            MaterialTheme {
                Column {
                    YearCalendar(
                        state = rememberYearCalendarState(startYear = 2026, endYear = 2026, firstVisibleYear = 2026),
                        modifier = Modifier.testTag("year").fillMaxWidth(),
                        onMonthClick = {},
                    )
                    // The narrowest a mini-month gets before its day numbers overlap.
                    CalendarDefaults.MiniMonth(YearMonth(2027, 12), Modifier.width(IntrinsicSize.Min))
                }
            }
        }
        val year = rule.onNodeWithTag("year").getUnclippedBoundsInRoot()
        val probe =
            rule
                .onNodeWithContentDescription(
                    "December 2027",
                    useUnmergedTree = true,
                ).getUnclippedBoundsInRoot()
        val minWidth = probe.right - probe.left
        for (month in Month.entries) {
            val title = PlatformDateFormat.yearMonthTitle(YearMonth(2026, month))
            val cell = rule.onNodeWithContentDescription(title, useUnmergedTree = true).getUnclippedBoundsInRoot()
            assertTrue(
                "$title is ${cell.right - cell.left} wide, needs $minWidth",
                cell.right - cell.left >= minWidth - 0.5.dp,
            )
            assertInside(title, cell, year, vertically = false)
        }
    }

    private companion object {
        const val SURFACE = "surface"
    }
}
