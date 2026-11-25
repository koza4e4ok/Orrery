package me.kozakov.orrery.compose

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.filter
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onChildren
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextReplacement
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.YearMonth
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Headings, live regions, picker states and field errors (#89 task 13, S1-S6). */
@RunWith(AndroidJUnit4::class)
class TitlesAndPickersSemanticsTest {
    @get:Rule val rule = createComposeRule()

    private val isHeading = SemanticsMatcher.keyIsDefined(SemanticsProperties.Heading)
    private val politeLiveRegion = SemanticsMatcher.expectValue(SemanticsProperties.LiveRegion, LiveRegionMode.Polite)
    private val noStateDescription = SemanticsMatcher.keyNotDefined(SemanticsProperties.StateDescription)

    private fun stateDescription(value: String) =
        SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, value)

    private fun month(name: String) = hasText(name) or hasContentDescription(name)

    private fun clickLabel(node: SemanticsNodeInteraction): String? =
        node.fetchSemanticsNode().config[SemanticsActions.OnClick].label

    // S1 + S2: CalendarNavHeader

    @Test
    fun navHeaderTitleIsAPoliteHeadingWithExpandedState() {
        var expanded by mutableStateOf(false)
        lateinit var expandedLabel: String
        lateinit var collapsedLabel: String
        rule.setContent {
            MaterialTheme {
                expandedLabel = orreryString(OrreryString.NavHeaderExpanded)
                collapsedLabel = orreryString(OrreryString.NavHeaderCollapsed)
                val state =
                    rememberCalendarState(firstVisibleMonth = YearMonth(2026, 8), firstDayOfWeek = DayOfWeek.MONDAY)
                CalendarNavHeader(state = state, onTitleClick = { expanded = !expanded }, expanded = expanded)
            }
        }
        val title = rule.onNodeWithText(PlatformDateFormat.yearMonthTitle(YearMonth(2026, 8)))
        title.assert(isHeading).assert(politeLiveRegion).assert(stateDescription(collapsedLabel))
        title.performClick()
        title.assert(stateDescription(expandedLabel))
    }

    /**
     * A July 2026 nav header over a pager; [composed] tracks the months whose title is composed.
     * Returns the "Next month" and "Collapsed" strings.
     */
    private fun pagingNavHeader(composed: MutableSet<YearMonth>): Pair<String, String> {
        lateinit var nextMonth: String
        lateinit var collapsed: String
        rule.setContent {
            MaterialTheme {
                nextMonth = orreryString(OrreryString.NextMonth)
                collapsed = orreryString(OrreryString.NavHeaderCollapsed)
                val state =
                    rememberCalendarState(
                        startMonth = YearMonth(2026, 6),
                        endMonth = YearMonth(2026, 9),
                        firstVisibleMonth = YearMonth(2026, 7),
                        firstDayOfWeek = DayOfWeek.MONDAY,
                    )
                Column {
                    CalendarNavHeader(
                        state = state,
                        onTitleClick = {},
                        title = { month ->
                            DisposableEffect(month) {
                                composed += month
                                onDispose { composed -= month }
                            }
                            Text(PlatformDateFormat.yearMonthTitle(month))
                        },
                    )
                    HorizontalCalendar(state = state, dayContent = { day -> Text(day.date.day.toString()) })
                }
            }
        }
        rule.waitForIdle()
        return nextMonth to collapsed
    }

    @Test
    fun pagingNavHeaderExposesOnlyTheIncomingMonthMidTransition() {
        val july = PlatformDateFormat.yearMonthTitle(YearMonth(2026, 7))
        val august = PlatformDateFormat.yearMonthTitle(YearMonth(2026, 8))
        // Months whose title is composed right now; both are while AnimatedContent animates.
        val composed = mutableSetOf<YearMonth>()
        val (nextMonth, collapsedLabel) = pagingNavHeader(composed)
        rule.mainClock.autoAdvance = false
        rule.onNodeWithContentDescription(nextMonth).performClick()
        var sawBothComposed = false
        repeat(MAX_TRANSITION_FRAMES) { frame ->
            rule.mainClock.advanceTimeByFrame()
            val titles =
                rule
                    .onNode(politeLiveRegion)
                    .fetchSemanticsNode()
                    .config
                    .getOrElse(SemanticsProperties.Text) { emptyList() }
                    .map { it.text }
            assertEquals("frame $frame: the live region exposes $titles", 1, titles.size)
            if (composed.size == 2) {
                sawBothComposed = true
                assertEquals("frame $frame: mid-transition the title is the target month", august, titles.single())
            } else {
                assertTrue("frame $frame: unexpected title $titles", titles.single() in setOf(july, august))
            }
        }
        rule.mainClock.autoAdvance = true
        rule.waitForIdle()
        assertTrue("the outgoing and incoming titles were never composed together", sawBothComposed)
        rule
            .onNode(politeLiveRegion)
            .assert(hasText(august))
            .assert(isHeading)
            .assert(hasClickAction())
            .assert(stateDescription(collapsedLabel))
    }

    @Test
    fun staticNavHeaderTitleHasNoExpandedState() {
        rule.setContent {
            MaterialTheme {
                val state =
                    rememberCalendarState(firstVisibleMonth = YearMonth(2026, 8), firstDayOfWeek = DayOfWeek.MONDAY)
                CalendarNavHeader(state = state)
            }
        }
        rule.onNodeWithText(PlatformDateFormat.yearMonthTitle(YearMonth(2026, 8))).assert(isHeading)
        rule.onNode(politeLiveRegion).assert(noStateDescription)
    }

    @Test
    fun datePickerDialogTitleIsHeadingAndMonthTitleReportsThePicker() {
        lateinit var strings: DatePickerStrings
        lateinit var expandedLabel: String
        rule.setContent {
            MaterialTheme {
                strings = DatePickerDefaults.strings()
                expandedLabel = orreryString(OrreryString.NavHeaderExpanded)
                OrreryDatePickerDialog(
                    onDismissRequest = {},
                    confirmButton = { TextButton(onClick = {}) { Text("OK") } },
                    state =
                        rememberOrreryDatePickerState(
                            initialDisplayedMonth = YearMonth(2026, 7),
                            yearRange =
                                2026..2026,
                        ),
                    today = LocalDate(2026, 7, 15),
                )
            }
        }
        rule.onNodeWithText(strings.title).assert(isHeading)
        val monthTitle = rule.onNodeWithText("July 2026")
        monthTitle.performClick()
        rule.waitForIdle()
        monthTitle.assert(stateDescription(expandedLabel))
    }

    // S1: other dialog titles and month titles

    @Test
    fun rangeDialogTitleIsHeading() {
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
                            yearRange =
                                2026..2026,
                        ),
                    today = LocalDate(2026, 7, 15),
                )
            }
        }
        rule.onNodeWithText(strings.rangeTitle).assert(isHeading)
    }

    @Test
    fun monthDialogTitleIsHeading() {
        lateinit var strings: MonthPickerStrings
        rule.setContent {
            MaterialTheme {
                strings = DatePickerDefaults.monthPickerStrings()
                OrreryMonthPickerDialog(
                    onDismissRequest = {},
                    confirmButton = { TextButton(onClick = {}) { Text("OK") } },
                    today = LocalDate(2026, 7, 15),
                )
            }
        }
        rule.onNodeWithText(strings.title).assert(isHeading)
    }

    @Test
    fun dateTimeDialogTitleIsHeadingAndStepPillsFormATabGroup() {
        lateinit var title: String
        rule.setContent {
            MaterialTheme {
                title = orreryString(OrreryString.DateTimePickerTitle)
                OrreryDateTimePickerDialog(
                    onDismissRequest = {},
                    confirmButton = { TextButton(onClick = {}) { Text("OK") } },
                    state = rememberOrreryDateTimePickerState(initialDateTime = LocalDateTime(2026, 7, 15, 9, 30)),
                    today = LocalDate(2026, 7, 15),
                )
            }
        }
        rule.onNodeWithText(title).assert(isHeading)
        // S6: both pills are tabs inside one selectable group, so they are announced as "1 of 2".
        rule
            .onNode(SemanticsMatcher.keyIsDefined(SemanticsProperties.SelectableGroup))
            .onChildren()
            .filter(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Tab))
            .assertCountEquals(2)
    }

    @Test
    fun verticalCalendarMonthTitleIsHeading() {
        rule.setContent {
            MaterialTheme {
                val state =
                    rememberCalendarState(
                        startMonth = YearMonth(2026, 7),
                        endMonth = YearMonth(2026, 8),
                        firstVisibleMonth = YearMonth(2026, 7),
                        firstDayOfWeek = DayOfWeek.MONDAY,
                    )
                VerticalCalendar(state = state) { day -> Text(day.date.toString()) }
            }
        }
        rule.onAllNodesWithText(PlatformDateFormat.yearMonthTitle(YearMonth(2026, 7))).onFirst().assert(isHeading)
    }

    // S3: WeekHeader

    @Test
    fun weekHeaderLabelsAreHiddenFromAccessibility() {
        val days = listOf(DayOfWeek.MONDAY, DayOfWeek.TUESDAY)
        rule.setContent { MaterialTheme { Column { CalendarDefaults.WeekHeader(days) } } }
        for (day in days) {
            rule
                .onNodeWithText(PlatformDateFormat.weekdayName(day, narrow = false), useUnmergedTree = true)
                .assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.HideFromAccessibility))
        }
    }

    // S4: MonthYearPicker

    @Test
    fun yearAndDecadeGridsMarkTheCurrentOneAndLabelTheDrillUp() {
        lateinit var current: String
        lateinit var selectDecade: String
        rule.setContent {
            MaterialTheme {
                current = orreryString(OrreryString.MonthYearPickerCurrent)
                selectDecade = orreryString(OrreryString.MonthYearPickerSelectDecade)
                MonthYearPicker(
                    current = YearMonth(2026, 7),
                    range = YearMonth(2020, 1)..YearMonth(2030, 12),
                    onSelect = {},
                )
            }
        }
        rule.onNodeWithText("2026").assert(stateDescription(current))
        rule.onNodeWithText("2025").assert(noStateDescription)
        val title = rule.onNodeWithText("2020 – 2030")
        assertEquals(selectDecade, clickLabel(title))
        title.performSemanticsAction(SemanticsActions.OnClick)
        rule.waitForIdle()
        rule.onNodeWithText("2020s").assert(stateDescription(current))
        rule.onNodeWithText("2030s").assert(noStateDescription)
    }

    @Test
    fun monthGridMarksTheCurrentMonthAndLabelsTheDrillUp() {
        lateinit var current: String
        lateinit var selectYear: String
        rule.setContent {
            MaterialTheme {
                current = orreryString(OrreryString.MonthYearPickerCurrent)
                selectYear = orreryString(OrreryString.MonthYearPickerSelectYear)
                MonthYearPicker(
                    current = YearMonth(2026, 7),
                    range = YearMonth(2020, 1)..YearMonth(2030, 12),
                    onSelect = {},
                )
            }
        }
        rule.onNodeWithText("2026").performClick()
        rule.waitForIdle()
        rule.onNode(month("July")).assert(stateDescription(current))
        rule.onNode(month("June")).assert(noStateDescription)
        val back = rule.onNodeWithText("2026")
        assertEquals(selectYear, clickLabel(back))
        back.performSemanticsAction(SemanticsActions.OnClick)
        rule.waitForIdle()
        rule.onNodeWithText("2020 – 2030").assertExists()
    }

    @Test
    fun monthDialogSaysSelectedAndCurrentTogether() {
        lateinit var current: String
        lateinit var selected: String
        rule.setContent {
            MaterialTheme {
                current = orreryString(OrreryString.MonthYearPickerCurrent)
                selected = orreryString(OrreryString.DaySelected)
                OrreryMonthPickerDialog(
                    onDismissRequest = {},
                    confirmButton = { TextButton(onClick = {}) { Text("OK") } },
                    state = rememberOrreryMonthPickerState(initialSelectedMonth = YearMonth(2026, 7)),
                    today = LocalDate(2026, 7, 15),
                )
            }
        }
        rule
            .onNode(month("July"))
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Selected, true))
            .assert(stateDescription("$selected, $current"))
        rule
            .onNode(month("August"))
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Selected, false))
            .assert(noStateDescription)
    }

    // S5: field errors

    @Test
    fun dateFieldReportsItsOwnErrorMessage() {
        lateinit var strings: DatePickerStrings
        rule.setContent {
            MaterialTheme {
                strings = DatePickerDefaults.strings()
                DateInputField(
                    value = null,
                    onValueChange = {},
                    label = "Date",
                    bounds = LocalDate(2026, 1, 1)..LocalDate(2026, 12, 31),
                    isDisabled = { false },
                    strings = strings,
                )
            }
        }
        val field = rule.onNodeWithText("Date")
        field.assert(SemanticsMatcher.keyNotDefined(SemanticsProperties.Error))
        field.performTextReplacement("07/15/2030")
        field.assert(SemanticsMatcher.expectValue(SemanticsProperties.Error, strings.outOfRangeError))
        field.performTextReplacement("07/15/2026")
        field.assert(SemanticsMatcher.keyNotDefined(SemanticsProperties.Error))
    }

    @Test
    fun rangeDialogEndFieldReportsTheCrossFieldError() {
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
        rule.onNodeWithText(strings.rangeStartInputLabel).performTextReplacement("07/12/2026")
        rule.onNodeWithText(strings.rangeEndInputLabel).performTextReplacement("07/08/2026")
        rule
            .onNodeWithText(strings.rangeEndInputLabel)
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Error, strings.invalidRangeError))
        rule.onNodeWithText(strings.rangeEndInputLabel).performTextReplacement("07/14/2026")
        rule
            .onNodeWithText(strings.rangeEndInputLabel)
            .assert(SemanticsMatcher.keyNotDefined(SemanticsProperties.Error))
        rule
            .onNodeWithText(strings.rangeStartInputLabel)
            .assert(SemanticsMatcher.keyNotDefined(SemanticsProperties.Error))
    }

    private companion object {
        /** About two seconds at 16 ms per frame: longer than the pager scroll plus the title transition. */
        const val MAX_TRANSITION_FRAMES = 120
    }
}
