@file:OptIn(ExperimentalOrreryApi::class)

package me.kozakov.orrery.widget

import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.sp
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.YearMonth
import kotlinx.datetime.plus
import me.kozakov.orrery.ExperimentalOrreryApi
import me.kozakov.orrery.core.DayInfo
import me.kozakov.orrery.core.DayInfoProvider
import me.kozakov.orrery.core.Selection
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CalendarSnapshotLayoutTest {
    private val colors = CalendarSnapshotDefaults.colors(lightColorScheme())
    private val november = YearMonth(2026, 11)

    @Test
    fun weekGridStartsOnEveryFirstDayOfWeek() {
        val date = LocalDate(2026, 11, 14)
        DayOfWeek.entries.forEach { first ->
            val grid = weekGrid(date, first)
            assertEquals(1, grid.rowCount)
            assertEquals(7, grid.cells.size)
            val start = grid.cells.first().date
            assertEquals(first, start.dayOfWeek)
            assertTrue(grid.cells.any { it.date == date }, "$first")
            grid.cells.forEachIndexed { index, cell ->
                assertEquals(start.plus(index, DateTimeUnit.DAY), cell.date)
                assertEquals(index, cell.column)
                assertFalse(cell.isOutside)
            }
            assertEquals(grid.weekdays(first), grid.cells.map { it.date.dayOfWeek })
        }
    }

    @Test
    fun monthGridPutsEveryDayUnderItsWeekdayForEveryFirstDayOfWeek() {
        DayOfWeek.entries.forEach { first ->
            val grid = monthGrid(november, first, showOutsideDays = false)
            val weekdays = grid.weekdays(first)
            assertEquals(first, weekdays.first())
            assertEquals((1..30).toList(), grid.cells.map { it.date.day })
            grid.cells.forEach { assertEquals(weekdays[it.column], it.date.dayOfWeek, "$first ${it.date}") }
            assertEquals(0, grid.cells.first().row)
            assertEquals(grid.rowCount - 1, grid.cells.last().row)
            grid.cells.zipWithNext().forEach { (a, b) ->
                val sameRow = b.column == a.column + 1 && b.row == a.row
                val nextRow = a.column == 6 && b.column == 0 && b.row == a.row + 1
                assertTrue(sameRow || nextRow, "$first ${b.date}")
            }
        }
    }

    @Test
    fun monthsNeedFourFiveOrSixRows() {
        // 1 Feb 2026 is a Sunday: February fills exactly four rows when weeks start on Sunday.
        val february = monthGrid(YearMonth(2026, 2), DayOfWeek.SUNDAY, showOutsideDays = true)
        assertEquals(4, february.rowCount)
        assertEquals(28, february.cells.size)
        assertTrue(february.cells.none { it.isOutside })
        assertEquals(5, monthGrid(YearMonth(2026, 2), DayOfWeek.MONDAY, showOutsideDays = false).rowCount)
        assertEquals(5, monthGrid(november, DayOfWeek.SUNDAY, showOutsideDays = false).rowCount)
        // 1 Aug 2026 is a Saturday: 5 leading blanks + 31 days need six rows.
        assertEquals(6, monthGrid(YearMonth(2026, 8), DayOfWeek.MONDAY, showOutsideDays = false).rowCount)
    }

    @Test
    fun outsideDaysFillTheFirstAndLastRow() {
        val grid = monthGrid(november, DayOfWeek.MONDAY, showOutsideDays = true)
        assertEquals(6, grid.rowCount)
        assertEquals(42, grid.cells.size)
        val outside = grid.cells.filter { it.isOutside }
        val expected = (26..31).map { LocalDate(2026, 10, it) } + (1..6).map { LocalDate(2026, 12, it) }
        assertEquals(expected, outside.map { it.date })
        assertTrue(outside.take(6).all { it.row == 0 })
        assertTrue(outside.drop(6).all { it.row == 5 })
        assertEquals(30, monthGrid(november, DayOfWeek.MONDAY, showOutsideDays = false).cells.size)
    }

    @Test
    fun todayIsMarkedOnlyInsideTheRange() {
        val grid = monthGrid(november, DayOfWeek.MONDAY, showOutsideDays = true)
        val inside = dayStates(grid, spec(today = LocalDate(2026, 11, 14)), colors)
        assertEquals(listOf(LocalDate(2026, 11, 14)), inside.filter { it.isToday }.map { it.cell.date })
        // 3 Dec is drawn as an outside day, which carries no marks.
        assertTrue(dayStates(grid, spec(today = LocalDate(2026, 12, 3)), colors).none { it.isToday })
        assertTrue(dayStates(grid, spec(today = LocalDate(2027, 1, 1)), colors).none { it.isToday })
    }

    @Test
    fun rangeSelectionMarksStartMiddleAndEnd() {
        // Reversed ends are normalised.
        val selection = Selection(rangeStart = LocalDate(2026, 11, 6), rangeEnd = LocalDate(2026, 11, 3))
        val marks = (2..7).map { selectionMark(LocalDate(2026, 11, it), selection) }
        val expected =
            listOf(
                SelectionMark.None,
                SelectionMark.RangeStart,
                SelectionMark.RangeMiddle,
                SelectionMark.RangeMiddle,
                SelectionMark.RangeEnd,
                SelectionMark.None,
            )
        assertEquals(expected, marks)
        assertTrue(marks.count { it.isEndpoint } == 2)
    }

    @Test
    fun openOrOneDayRangesAndSingleOrMultiSelectionMarkOneDay() {
        val day = LocalDate(2026, 11, 3)
        assertEquals(SelectionMark.Single, selectionMark(day, Selection(rangeStart = day)))
        assertEquals(SelectionMark.None, selectionMark(day.plus(1, DateTimeUnit.DAY), Selection(rangeStart = day)))
        assertEquals(SelectionMark.Single, selectionMark(day, Selection(rangeStart = day, rangeEnd = day)))
        val multi = Selection(single = day, multi = setOf(LocalDate(2026, 11, 9)))
        assertEquals(SelectionMark.Single, selectionMark(day, multi))
        assertEquals(SelectionMark.Single, selectionMark(LocalDate(2026, 11, 9), multi))
        assertEquals(SelectionMark.None, selectionMark(LocalDate(2026, 11, 10), multi))
        assertEquals(SelectionMark.None, selectionMark(day, Selection.Empty))
    }

    @Test
    fun outsideDaysCarryNoMarks() {
        val grid = monthGrid(november, DayOfWeek.MONDAY, showOutsideDays = true)
        val october30 = LocalDate(2026, 10, 30)
        val states =
            dayStates(
                grid,
                spec(
                    selection = Selection(single = october30),
                    eventCounts = mapOf(october30 to 2),
                    dayInfo = holidays,
                ),
                colors,
            )
        val state = states.single { it.cell.date == october30 }
        assertEquals(SelectionMark.None, state.selection)
        assertEquals(0, state.dots)
        assertNull(state.tint)
    }

    @Test
    fun eventDotsAreCappedAtThree() {
        assertEquals(listOf(0, 0, 0, 1, 2, 3, 3), listOf(null, -2, 0, 1, 2, 3, 7).map(::eventDotCount))
        val grid = monthGrid(november, DayOfWeek.MONDAY, showOutsideDays = false)
        val counts = mapOf(LocalDate(2026, 11, 2) to 5, LocalDate(2026, 11, 3) to 1)
        val dots = dayStates(grid, spec(eventCounts = counts), colors).associate { it.cell.date.day to it.dots }
        assertEquals(3, dots[2])
        assertEquals(1, dots[3])
        assertEquals(0, dots[4])
    }

    @Test
    fun holidaysAndPriorityColoursTintTheDay() {
        val grid = monthGrid(november, DayOfWeek.MONDAY, showOutsideDays = false)
        val tints = dayStates(grid, spec(dayInfo = holidays), colors).associate { it.cell.date.day to it.tint }
        assertEquals(colors.holiday, tints[11])
        assertEquals(Color(0xFF123456), tints[12])
        assertNull(tints[13])
    }

    @Test
    fun roomyGridKeepsEveryRowAtTheRequestedSizes() {
        val plan = planGridText(700f, 700f, 6, request())
        assertTrue(plan.showTitle)
        assertTrue(plan.showWeekdays)
        assertEquals(12f, plan.dayTextPx)
        assertEquals(10f, plan.weekdayTextPx)
    }

    @Test
    fun cellsShrinkFirstThenTitleThenWeekdaysThenNumbersAreDropped() {
        // 40 px cells wide; a 12 px day number needs 14.4 px cells at the 0.6 floor.
        val shrunk = planGridText(280f, 130f, 6, request())
        assertTrue(shrunk.showTitle && shrunk.showWeekdays)
        assertTrue(shrunk.dayTextPx < 12f && shrunk.dayTextPx >= 7.2f, "${shrunk.dayTextPx}")
        val noTitle = planGridText(280f, 110f, 6, request())
        assertFalse(noTitle.showTitle)
        assertTrue(noTitle.showWeekdays)
        val bare = planGridText(280f, 90f, 6, request())
        assertFalse(bare.showTitle || bare.showWeekdays)
        assertTrue(bare.showDayNumbers)
        val marksOnly = planGridText(280f, 80f, 6, request())
        assertFalse(marksOnly.showTitle || marksOnly.showWeekdays || marksOnly.showDayNumbers)
    }

    @Test
    fun narrowCellsDropTheWeekdayRowButKeepTheTitle() {
        // 16 px cells: a 16 px weekday label would need 8 px, below its 9.6 px floor.
        val plan = planGridText(112f, 700f, 6, request(weekdayPx = 16f))
        assertTrue(plan.showTitle)
        assertFalse(plan.showWeekdays)
        assertEquals(8f, plan.dayTextPx)
    }

    @Test
    fun hiddenRowsStayHidden() {
        val plan = planGridText(700f, 700f, 6, request(showTitle = false, showWeekdays = false))
        assertFalse(plan.showTitle || plan.showWeekdays)
        assertEquals(0f, plan.titleBand)
        assertEquals(0f, plan.weekdayBand)
    }

    @Test
    fun fontScaleIsHonouredAndTextNeverGoesBelowTheFloor() {
        val scaled = with(Density(1f, fontScale = 2f)) { 12.sp.toPx() }
        assertEquals(24f, scaled)
        // The floor is 0.6 x the unscaled 12 px, so a bigger font does not raise it.
        assertEquals(7.2f, request(dayPx = scaled, fontScale = 2f).dayFloorPx, 0.001f)
        assertTrue(planGridText(280f, 130f, 6, request()).showDayNumbers)
        assertTrue(planGridText(280f, 130f, 6, request(dayPx = scaled, fontScale = 2f)).showDayNumbers)
        for (height in 1..400) {
            val plan = planGridText(280f, height.toFloat(), 6, request(dayPx = scaled, fontScale = 2f))
            val day = plan.dayTextPx
            val gridHeight = height - plan.titleBand - plan.weekdayBand
            val cell = minOf(40f, gridHeight / 6)
            assertTrue(day == 0f || (day >= 7.2f - 0.001f && day <= scaled), "height $height: $day")
            assertTrue(day <= cell * DAY_TEXT_CELL_FACTOR + 0.001f, "height $height: $day in $cell px cells")
            assertTrue(plan.titleBand + plan.weekdayBand <= height || !plan.showDayNumbers, "height $height")
            // Whether numbers show depends only on the box, never on the font scale.
            val unscaled = planGridText(280f, height.toFloat(), 6, request())
            assertEquals(unscaled.showDayNumbers, plan.showDayNumbers, "height $height")
        }
    }

    @Test
    fun largeFontKeepsTheDayNumbersOnAFourByTwoWidget() {
        // 250 x 110 dp at density 3, minus 8 dp padding on each side: a 702 x 282 px content box.
        val august = monthGrid(YearMonth(2026, 8), DayOfWeek.MONDAY, showOutsideDays = false)
        assertEquals(6, august.rowCount)
        val style = CalendarSnapshotDefaults.style()
        val spec = spec()
        val cell = 282f / 6
        listOf(1f, 1.3f, 2f).forEach { fontScale ->
            val density = Density(3f, fontScale)
            val request = gridTextRequest(style, spec, density)
            val plan = planGridText(702f, 282f, august.rowCount, request)
            assertTrue(plan.showDayNumbers, "font scale $fontScale")
            assertTrue(plan.dayTextPx >= 0.6f * 36f - 0.001f, "font scale $fontScale: ${plan.dayTextPx}")
            assertTrue(plan.dayTextPx <= request.dayTextPx, "font scale $fontScale: ${plan.dayTextPx}")
            assertTrue(plan.dayTextPx <= cell * DAY_TEXT_CELL_FACTOR + 0.001f, "font scale $fontScale")
        }
        // The weekday floor ignores font scale too: 0.6 x 30 px whatever the font scale.
        assertEquals(18f, gridTextRequest(style, spec, Density(3f, 2f)).weekdayFloorPx, 0.001f)
    }

    @Test
    fun smallFontScaleKeepsTheFloorAtTheRequestedSize() {
        // At font scale 0.5 the unscaled floor (7.2 px) is above the 6 px request: the request wins.
        assertEquals(6f, request(dayPx = 6f, fontScale = 0.5f).dayFloorPx)
        assertTrue(planGridText(700f, 700f, 6, request(dayPx = 6f, fontScale = 0.5f)).showDayNumbers)
    }

    private fun request(
        dayPx: Float = 12f,
        weekdayPx: Float = 10f,
        showTitle: Boolean = true,
        showWeekdays: Boolean = true,
        fontScale: Float = 1f,
    ): GridTextRequest = GridTextRequest(14f, weekdayPx, dayPx, 0.6f, showTitle, showWeekdays, fontScale)

    private fun spec(
        today: LocalDate? = null,
        selection: Selection = Selection.Empty,
        eventCounts: Map<LocalDate, Int> = emptyMap(),
        dayInfo: DayInfoProvider? = null,
    ): CalendarSnapshotSpec =
        CalendarSnapshotSpec(
            range = CalendarSnapshotRange.Month(november),
            today = today,
            selection = selection,
            dayInfo = dayInfo,
            eventCounts = eventCounts,
            showOutsideDays = true,
        )

    private val holidays =
        object : DayInfoProvider {
            override fun info(date: LocalDate): DayInfo? =
                when (date.day) {
                    11 -> DayInfo(label = null, isHoliday = true)
                    12 -> DayInfo(label = null, colorArgb = 0xFF123456)
                    30 -> DayInfo(label = null, isHoliday = true)
                    else -> null
                }
        }
}
