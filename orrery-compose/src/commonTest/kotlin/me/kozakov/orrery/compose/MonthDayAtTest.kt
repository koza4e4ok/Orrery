package me.kozakov.orrery.compose

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.IntSize
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.YearMonth
import me.kozakov.orrery.core.CalendarMonth
import me.kozakov.orrery.core.OutDateStyle
import me.kozakov.orrery.core.monthGrid
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Monday-first grids with 100 px cells: February 2027 has 4 rows, July
 * 2026 has 5 (ending with the out-dates Aug 1-2), August 2026 has 6.
 */
class MonthDayAtTest {
    private val february = month(2027, 2)
    private val july = month(2026, 7)
    private val august = month(2026, 8)

    private fun month(
        year: Int,
        month: Int,
    ) = monthGrid(YearMonth(year, month), DayOfWeek.MONDAY, OutDateStyle.EndOfRow)

    private fun dateAt(
        grid: CalendarMonth,
        x: Float,
        y: Float,
        isRtl: Boolean = false,
        leading: Float = 0f,
        rowTops: IntArray? = null,
    ): LocalDate? {
        val size = IntSize(700 + leading.toInt(), grid.weeks.size * 100)
        return monthDayAt(grid, leading, size, Offset(x, y), isRtl, rowTops)?.date
    }

    @Test
    fun rowAndColumnInLtr() {
        assertEquals(4, february.weeks.size)
        assertEquals(LocalDate(2027, 2, 1), dateAt(february, 50f, 50f))
        assertEquals(LocalDate(2027, 2, 28), dateAt(february, 650f, 350f))
        assertEquals(5, july.weeks.size)
        assertEquals(LocalDate(2026, 8, 2), dateAt(july, 650f, 450f))
        assertEquals(6, august.weeks.size)
        assertEquals(LocalDate(2026, 8, 31), dateAt(august, 50f, 550f))
    }

    @Test
    fun columnsMirrorInRtl() {
        assertEquals(LocalDate(2027, 2, 1), dateAt(february, 650f, 50f, isRtl = true))
        assertEquals(LocalDate(2027, 2, 7), dateAt(february, 50f, 50f, isRtl = true))
        assertEquals(LocalDate(2026, 7, 27), dateAt(july, 650f, 450f, isRtl = true))
        assertEquals(LocalDate(2026, 9, 6), dateAt(august, 50f, 550f, isRtl = true))
    }

    @Test
    fun weekNumberColumnIsSkippedOnTheLeadingSide() {
        assertEquals(LocalDate(2027, 2, 1), dateAt(february, 75f, 50f, leading = 70f))
        assertEquals(LocalDate(2027, 2, 7), dateAt(february, 765f, 50f, leading = 70f))
        assertEquals(LocalDate(2027, 2, 1), dateAt(february, 695f, 50f, isRtl = true, leading = 70f))
        assertEquals(LocalDate(2027, 2, 7), dateAt(february, 5f, 50f, isRtl = true, leading = 70f))
    }

    @Test
    fun positionsOutsideTheGridClamp() {
        assertEquals(LocalDate(2026, 6, 29), dateAt(july, -10f, -10f))
        assertEquals(LocalDate(2026, 8, 2), dateAt(july, 10_000f, 10_000f))
    }

    @Test
    fun measuredRowTopsDecideTheRow() {
        val tops = intArrayOf(0, 100, 250, 300, 400)
        assertEquals(LocalDate(2026, 7, 13), dateAt(july, 50f, 260f, rowTops = tops))
        assertEquals(LocalDate(2026, 7, 6), dateAt(july, 50f, 240f, rowTops = tops))
    }
}
