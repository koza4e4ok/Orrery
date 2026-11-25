package me.kozakov.orrery.compose

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
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
import kotlin.math.roundToInt

/** 200 % system text on a Pixel 5. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = RobolectricDeviceQualifiers.Pixel5, fontScale = 2f)
class LargeFontPixel5LayoutTest : LargeFontLayoutTest(largeFont = true, narrowWindow = false)

/** 200 % system text in a 320 dp window: small phones, split screen, "Largest" display size. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = NARROW_WINDOW_QUALIFIERS, fontScale = 2f)
class LargeFontNarrowLayoutTest : LargeFontLayoutTest(largeFont = true, narrowWindow = true)

/** Control: the same checks hold at the default text size, so a failure above is the font scale's. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = RobolectricDeviceQualifiers.Pixel5)
class DefaultFontPixel5LayoutTest : LargeFontLayoutTest(largeFont = false, narrowWindow = false)

/** Control: default text size in the 320 dp window. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = NARROW_WINDOW_QUALIFIERS)
class DefaultFontNarrowLayoutTest : LargeFontLayoutTest(largeFont = false, narrowWindow = true)

internal const val NARROW_WINDOW_QUALIFIERS = "w320dp-h640dp-xhdpi"

/**
 * Key nodes stay on screen and inside their container, and their text is not
 * cut off (no ellipsis or clipped line; where a line count is given, no word
 * broken over lines either). Subclasses pick the window and font scale.
 */
abstract class LargeFontLayoutTest(
    private val largeFont: Boolean,
    private val narrowWindow: Boolean,
) {
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
        val paragraph = layout.multiParagraph
        val ellipsized = (0 until layout.lineCount).any { layout.isLineEllipsized(it) }
        // A line can end a fraction of a pixel below the box after rounding.
        val clippedBelow = layout.size.height + 1 < paragraph.height
        assertFalse(
            "$what is cut off: ellipsized=$ellipsized, exceededMaxLines=${paragraph.didExceedMaxLines}, " +
                "box ${layout.size} < text ${paragraph.width}x${paragraph.height}, ${layout.lineCount} lines, " +
                "constraints ${layout.layoutInput.constraints}",
            ellipsized || paragraph.didExceedMaxLines || clippedBelow,
        )
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
        for (index in 0..1) assertInside("step pill $index", pills[index], surface, vertically = true)
        assertTextFits("Sep 28, 2026", surface, vertically = true)
        assertTextFits("09:30", surface, maxLines = 1, vertically = true)
    }

    @Test
    fun dateTimePickerDateStepFitsTheWindow() {
        dateTimePicker()
        val surface = surface()
        assertStepPillsFit(surface)
        assertChevronsFit(surface)
        // The date step shows Next where the time step shows the confirm button.
        assertTextFits("Cancel", surface, maxLines = 1, vertically = true)
        assertTextFits("Next", surface, maxLines = 1, vertically = true)
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
        // Narrow names repeat (T, S): the n-th narrow header with a name is the n-th day using it.
        val narrowSeen = mutableMapOf<String, Int>()
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
            val weekday = PlatformDateFormat.weekdayName(dayOfWeek, narrow = false)
            val header =
                if (full.fetchSemanticsNodes().isNotEmpty()) {
                    full.onFirst()
                } else {
                    val name = PlatformDateFormat.weekdayName(dayOfWeek, narrow = true).lowercase()
                    val index = narrowSeen.getOrElse(name) { 0 }
                    narrowSeen[name] = index + 1
                    // A one-letter header still tells screen readers which day it is.
                    narrow[index].assert(hasContentDescription(weekday))
                }
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
        assertFullRowsInMonthOrder()
        val pageScroll =
            rule
                .onNode(
                    SemanticsMatcher.keyIsDefined(SemanticsProperties.VerticalScrollAxisRange) and
                        hasAnyAncestor(hasTestTag("year")),
                ).fetchSemanticsNode()
                .config[SemanticsProperties.VerticalScrollAxisRange]
        if (!largeFont) {
            // At the default size the page keeps its 3 columns x 4 rows.
            assertEquals(3, monthRows().first().size)
        }
        if (!largeFont && !narrowWindow) {
            // ...and on a phone it fits without scrolling, as before.
            assertEquals(0f, pageScroll.maxValue(), 0f)
        } else {
            // Taller than a 640 dp window or at 200 %: every month can be scrolled to and shown
            // (in a 320 x 640 dp window the last row was cut off before).
            for (month in Month.entries) {
                val title = PlatformDateFormat.yearMonthTitle(YearMonth(2026, month))
                rule.onNodeWithContentDescription(title, useUnmergedTree = true).performScrollTo().assertIsDisplayed()
            }
        }
    }

    /** Month cells grouped into rows by their top edge, top to bottom. */
    private fun monthRows(): List<List<Month>> =
        Month.entries
            .groupBy { month ->
                val title = PlatformDateFormat.yearMonthTitle(YearMonth(2026, month))
                rule
                    .onNodeWithContentDescription(
                        title,
                        useUnmergedTree = true,
                    ).getUnclippedBoundsInRoot()
                    .top.value
                    .roundToInt()
            }.toSortedMap()
            .values
            .toList()

    private fun assertFullRowsInMonthOrder() {
        val rows = monthRows()
        assertEquals("rows of $rows are not all full", 1, rows.map { it.size }.toSet().size)
        assertEquals(Month.entries, rows.flatten())
    }

    @Test
    fun yearCalendarComposesInsideAVerticallyScrollingColumn() {
        rule.setContent {
            MaterialTheme {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    YearCalendar(
                        state = rememberYearCalendarState(startYear = 2026, endYear = 2026, firstVisibleYear = 2026),
                        modifier = Modifier.testTag("year").fillMaxWidth(),
                        onMonthClick = {},
                    )
                }
            }
        }
        // Unbounded height: the page takes the height of all its rows instead of scrolling.
        assertFullRowsInMonthOrder()
        val year = rule.onNodeWithTag("year").getUnclippedBoundsInRoot()
        val december =
            rule.onNodeWithContentDescription(
                PlatformDateFormat.yearMonthTitle(YearMonth(2026, 12)),
                useUnmergedTree = true,
            )
        assertInside("December", december.getUnclippedBoundsInRoot(), year, vertically = true)
    }

    @Test
    fun yearCalendarComposesInALazyColumnItem() {
        rule.setContent {
            MaterialTheme {
                LazyColumn {
                    item {
                        YearCalendar(
                            state =
                                rememberYearCalendarState(
                                    startYear = 2026,
                                    endYear = 2026,
                                    firstVisibleYear = 2026,
                                ),
                            modifier = Modifier.testTag("year").fillMaxWidth(),
                            onMonthClick = {},
                        )
                    }
                }
            }
        }
        assertFullRowsInMonthOrder()
    }

    private companion object {
        const val SURFACE = "surface"
    }
}
