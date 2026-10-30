package me.kozakov.orrery.compose

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import kotlinx.coroutines.CoroutineScope
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.YearMonth
import me.kozakov.orrery.core.CalendarDay
import me.kozakov.orrery.core.DayPosition
import me.kozakov.orrery.core.Selection
import me.kozakov.orrery.core.SelectionMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.math.abs

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = RobolectricDeviceQualifiers.Pixel5)
class HospitalitySelectionTest {
    @get:Rule
    val rule = createComposeRule()

    private val july = YearMonth(2026, 7)
    private val inDate = LocalDate(2026, 6, 30)
    private val today = LocalDate(2026, 7, 1)

    // ---- OutDateClickPolicy -------------------------------------------------

    private fun setCalendar(
        policy: OutDateClickPolicy,
        mode: SelectionMode = SelectionMode.Single(),
        onClick: (CalendarDay) -> Unit = {},
    ): Pair<CalendarState, CalendarSelectionState> {
        lateinit var calendarState: CalendarState
        lateinit var selectionState: CalendarSelectionState
        rule.setContent {
            calendarState =
                rememberCalendarState(
                    startMonth = YearMonth(2026, 6),
                    endMonth = YearMonth(2026, 8),
                    firstVisibleMonth = july,
                    firstDayOfWeek = DayOfWeek.MONDAY,
                )
            selectionState = rememberCalendarSelectionState(mode = mode)
            val style = CalendarDefaults.hospitalityStyle()
            HorizontalCalendar(
                state = calendarState,
                dayContent = { day ->
                    DefaultDay(
                        day = day,
                        style = style,
                        turnoverDates = emptySet(),
                        modifier = Modifier.testTag(tag(day)),
                        selectionState = selectionState,
                        calendarState = calendarState,
                        outDateClickPolicy = policy,
                        today = today,
                        animateSelection = false,
                        onClick = onClick,
                    )
                },
            )
        }
        return calendarState to selectionState
    }

    private fun tag(day: CalendarDay): String = "${day.date}-${day.position}"

    @Test
    fun ignorePolicyLeavesSelectionAndMonthAlone() {
        var clicks = 0
        val (calendar, selection) = setCalendar(OutDateClickPolicy.Ignore) { clicks++ }
        rule.onNodeWithTag("$inDate-${DayPosition.InDate}").performClick()
        rule.waitForIdle()
        assertEquals(Selection.Empty, selection.selection)
        assertEquals(july, calendar.firstVisibleMonth)
        assertEquals(0, clicks)

        rule.onNodeWithTag("2026-07-10-${DayPosition.MonthDate}").performClick()
        rule.waitForIdle()
        assertEquals(LocalDate(2026, 7, 10), selection.selection.single)
        assertEquals(1, clicks)
    }

    @Test
    fun selectOnlyPolicySelectsWithoutPaging() {
        var clicks = 0
        val (calendar, selection) = setCalendar(OutDateClickPolicy.SelectOnly) { clicks++ }
        rule.onNodeWithTag("$inDate-${DayPosition.InDate}").performClick()
        rule.waitForIdle()
        assertEquals(inDate, selection.selection.single)
        assertEquals(july, calendar.firstVisibleMonth)
        assertEquals(1, clicks)
    }

    @Test
    fun selectAndScrollPolicySelectsAndPagesToTheDatesMonth() {
        val (calendar, selection) = setCalendar(OutDateClickPolicy.SelectAndScrollToMonth)
        rule.onNodeWithTag("$inDate-${DayPosition.InDate}").performClick()
        rule.waitForIdle()
        assertEquals(inDate, selection.selection.single)
        assertEquals(YearMonth(2026, 6), calendar.firstVisibleMonth)
    }

    @Test
    fun onDayClickFollowsThePolicyWithoutUi() {
        lateinit var calendarState: CalendarState
        lateinit var selectionState: CalendarSelectionState
        lateinit var scope: CoroutineScope
        rule.setContent {
            calendarState =
                rememberCalendarState(
                    startMonth = YearMonth(2026, 6),
                    endMonth = YearMonth(2026, 8),
                    firstVisibleMonth = july,
                )
            selectionState = rememberCalendarSelectionState()
            scope = rememberCoroutineScope()
            HorizontalCalendar(state = calendarState, dayContent = { DefaultDay(day = it, animateSelection = false) })
        }
        val outDay = CalendarDay(LocalDate(2026, 8, 1), DayPosition.OutDate)
        val monthDay = CalendarDay(LocalDate(2026, 7, 3), DayPosition.MonthDate)
        rule.runOnIdle {
            assertFalse(selectionState.onDayClick(outDay, calendarState, OutDateClickPolicy.Ignore, scope))
            assertEquals(Selection.Empty, selectionState.selection)
            // Days of the rendered month are always selected, whatever the policy.
            assertTrue(selectionState.onDayClick(monthDay, calendarState, OutDateClickPolicy.Ignore, scope))
            assertEquals(monthDay.date, selectionState.selection.single)
            // Without a scope, SelectAndScrollToMonth only selects.
            assertTrue(selectionState.onDayClick(outDay, calendarState, OutDateClickPolicy.SelectAndScrollToMonth))
            assertEquals(outDay.date, selectionState.selection.single)
        }
        rule.waitForIdle()
        assertEquals(july, calendarState.firstVisibleMonth)
        rule.runOnIdle {
            val handled =
                selectionState.onDayClick(outDay, calendarState, OutDateClickPolicy.SelectAndScrollToMonth, scope)
            assertTrue(handled)
        }
        rule.waitForIdle()
        assertEquals(YearMonth(2026, 8), calendarState.firstVisibleMonth)
    }

    // ---- SelectionMode.Single(allowClear) -----------------------------------

    @Test
    fun singleWithoutAllowClearKeepsTheSelectedDay() {
        val (_, selection) = setCalendar(OutDateClickPolicy.SelectOnly, SelectionMode.Single(allowClear = false))
        val node = rule.onNodeWithTag("2026-07-10-${DayPosition.MonthDate}")
        node.performClick()
        node.performClick()
        rule.waitForIdle()
        assertEquals(LocalDate(2026, 7, 10), selection.selection.single)
    }

    @Test
    fun singleWithAllowClearClearsOnSecondTap() {
        val (_, selection) = setCalendar(OutDateClickPolicy.SelectOnly, SelectionMode.Single(allowClear = true))
        val node = rule.onNodeWithTag("2026-07-10-${DayPosition.MonthDate}")
        node.performClick()
        rule.waitForIdle()
        assertEquals(LocalDate(2026, 7, 10), selection.selection.single)
        node.performClick()
        rule.waitForIdle()
        assertNull(selection.selection.single)
    }

    // ---- Split range endpoints ----------------------------------------------

    private fun setCell(
        date: LocalDate,
        endpointStyle: RangeEndpointStyle,
        turnoverDates: Set<LocalDate> = emptySet(),
    ) {
        rule.setContent {
            MaterialTheme(colorScheme = lightColorScheme()) {
                val selection =
                    rememberCalendarSelectionState(
                        mode = SelectionMode.Range(),
                        initialSelection = Selection(rangeStart = RANGE_START, rangeEnd = RANGE_END),
                    )
                val base = CalendarDefaults.hospitalityStyle(rangeEndpointStyle = endpointStyle)
                val style =
                    CalendarStyle(
                        colors =
                            CalendarDefaults.dayColors(
                                inRangeContainerColor = Color.Red,
                                inRangeContentColor = Color.Black,
                                todayIndicatorColor = Color.Transparent,
                            ),
                        shapes = base.shapes,
                    )
                Box(Modifier.background(Color.White)) {
                    DefaultDay(
                        day = CalendarDay(date, DayPosition.MonthDate),
                        style = style,
                        turnoverDates = turnoverDates,
                        modifier = Modifier.size(48.dp).testTag(CELL),
                        selectionState = selection,
                        today = today,
                        animateSelection = false,
                    )
                }
            }
        }
    }

    @Test
    fun hospitalityStyleDefaultsToSplitDiagonal() {
        lateinit var style: CalendarStyle
        rule.setContent { style = CalendarDefaults.hospitalityStyle() }
        rule.waitForIdle()
        assertEquals(RangeEndpointStyle.SplitDiagonal, style.shapes.rangeEndpointStyle)
        assertEquals(RangeEndpointStyle.FullCell, CalendarDefaults.dayShapes().rangeEndpointStyle)
    }

    @Test
    fun splitHorizontalFillsThePmHalfOfTheRangeStart() {
        setCell(RANGE_START, RangeEndpointStyle.SplitHorizontal)
        assertColorNear(Color.White, pixel(4.dp, 4.dp))
        assertColorNear(Color.Red, pixel(44.dp, 4.dp))
        assertColorNear(Color.Red, pixel(44.dp, 44.dp))
    }

    @Test
    fun splitHorizontalFillsTheAmHalfOfTheRangeEnd() {
        setCell(RANGE_END, RangeEndpointStyle.SplitHorizontal)
        assertColorNear(Color.Red, pixel(4.dp, 4.dp))
        assertColorNear(Color.White, pixel(44.dp, 4.dp))
    }

    @Test
    fun splitHorizontalTurnoverFillsBothHalvesWithAGap() {
        setCell(TURNOVER, RangeEndpointStyle.SplitHorizontal, setOf(TURNOVER))
        assertColorNear(Color.Red, pixel(4.dp, 4.dp))
        assertColorNear(Color.Red, pixel(44.dp, 4.dp))
        assertColorNear(Color.White, pixel(24.dp, 4.dp))
    }

    @Test
    fun splitDiagonalFillsTheBottomRightTriangleOfTheRangeStart() {
        setCell(RANGE_START, RangeEndpointStyle.SplitDiagonal)
        assertColorNear(Color.White, pixel(4.dp, 4.dp))
        assertColorNear(Color.Red, pixel(44.dp, 44.dp))
    }

    @Test
    fun splitDiagonalFillsTheTopLeftTriangleOfTheRangeEnd() {
        setCell(RANGE_END, RangeEndpointStyle.SplitDiagonal)
        assertColorNear(Color.Red, pixel(4.dp, 4.dp))
        assertColorNear(Color.White, pixel(44.dp, 44.dp))
    }

    @Test
    fun splitDiagonalTurnoverFillsBothTrianglesWithAGap() {
        setCell(TURNOVER, RangeEndpointStyle.SplitDiagonal, setOf(TURNOVER))
        assertColorNear(Color.Red, pixel(4.dp, 4.dp))
        assertColorNear(Color.Red, pixel(44.dp, 44.dp))
        // A point on the diagonal, away from the centered day number.
        assertColorNear(Color.White, pixel(8.dp, 40.dp))
    }

    @Test
    fun fullCellIgnoresTurnoverDates() {
        setCell(TURNOVER, RangeEndpointStyle.FullCell, setOf(TURNOVER))
        assertColorNear(Color.White, pixel(4.dp, 4.dp))
        assertColorNear(Color.White, pixel(44.dp, 44.dp))
    }

    @Test
    fun fullCellRangeStartKeepsTheBand() {
        setCell(RANGE_START, RangeEndpointStyle.FullCell)
        // The connector band fills the half toward the next day (its corner is outside the
        // selection circle); the opposite corner stays empty.
        assertColorNear(Color.White, pixel(1.dp, 2.dp))
        assertColorNear(Color.Red, pixel(47.dp, 2.dp))
    }

    private fun pixel(
        x: Dp,
        y: Dp,
    ): Color {
        val map = rule.onNodeWithTag(CELL).captureToImage().toPixelMap()
        val (px, py) = with(rule.density) { x.roundToPx() to y.roundToPx() }
        return map[px.coerceAtMost(map.width - 1), py.coerceAtMost(map.height - 1)]
    }

    private fun assertColorNear(
        expected: Color,
        actual: Color,
    ) {
        val close =
            abs(expected.red - actual.red) < 0.05f &&
                abs(expected.green - actual.green) < 0.05f &&
                abs(expected.blue - actual.blue) < 0.05f
        assertTrue("expected ~$expected but was $actual", close)
    }

    private companion object {
        const val CELL = "cell"
        val RANGE_START = LocalDate(2026, 7, 8)
        val RANGE_END = LocalDate(2026, 7, 12)
        val TURNOVER = LocalDate(2026, 7, 20)
    }
}
