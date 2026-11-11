package me.kozakov.orrery.compose

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.YearMonth
import me.kozakov.orrery.core.CalendarMonth
import me.kozakov.orrery.core.monthGrid
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DualMonthHelpersTest {
    private val start = YearMonth(2026, 1)
    private val july = YearMonth(2026, 7)

    // Monday-first grids: July 2026 has 5 week rows, August 2026 has 6.
    private val julyGrid = monthGrid(july, DayOfWeek.MONDAY)
    private val augustGrid = monthGrid(YearMonth(2026, 8), DayOfWeek.MONDAY)

    private fun CalendarMonth.dateAt(
        x: Float,
        y: Float,
        size: IntSize,
        isRtl: Boolean = false,
        leading: Float = 0f,
        rowTops: IntArray? = null,
    ): LocalDate? = monthDayAt(this, leading, size, Offset(x, y), isRtl, rowTops)?.date

    @Test
    fun pageTargetIsNullWhileTheTargetMonthIsVisible() {
        assertNull(dualMonthPageTarget(left = july, target = july, start = start))
        assertNull(dualMonthPageTarget(left = july, target = YearMonth(2026, 8), start = start))
    }

    @Test
    fun pageTargetBeforeTheLeftPaneBecomesTheLeftPane() {
        assertEquals(YearMonth(2026, 6), dualMonthPageTarget(july, YearMonth(2026, 6), start))
        assertEquals(YearMonth(2025, 7), dualMonthPageTarget(july, YearMonth(2025, 7), YearMonth(2025, 1)))
        assertEquals(start, dualMonthPageTarget(july, start, start))
    }

    @Test
    fun pageTargetAfterTheRightPaneBecomesTheRightPane() {
        // One month past the right pane slides the pair by a single month.
        assertEquals(YearMonth(2026, 8), dualMonthPageTarget(july, YearMonth(2026, 9), start))
        assertEquals(YearMonth(2027, 6), dualMonthPageTarget(july, YearMonth(2027, 7), start))
        // The last month of the range ends up as the right pane, never alone on the left.
        assertEquals(YearMonth(2026, 11), dualMonthPageTarget(july, YearMonth(2026, 12), start))
    }

    @Test
    fun pageTargetNeverStartsBeforeTheFirstMonth() {
        // Defensive: with the left pane before the range the target stays inside it.
        assertEquals(start, dualMonthPageTarget(YearMonth(2025, 10), start, start))
    }

    @Test
    fun dayAtResolvesRowsAndColumnsInLtr() {
        val six = IntSize(700, 600)
        assertEquals(LocalDate(2026, 7, 27), augustGrid.dateAt(50f, 50f, six))
        assertEquals(LocalDate(2026, 8, 4), augustGrid.dateAt(150f, 150f, six))
        assertEquals(LocalDate(2026, 9, 6), augustGrid.dateAt(650f, 550f, six))
        assertEquals(LocalDate(2026, 7, 29), julyGrid.dateAt(250f, 450f, IntSize(700, 500)))
    }

    @Test
    fun dayAtCoversAFourRowMonth() {
        // 1 Feb 2026 is a Sunday: four full Sunday-first weeks.
        val february = monthGrid(YearMonth(2026, 2), DayOfWeek.SUNDAY)
        assertEquals(4, february.weeks.size)
        val four = IntSize(700, 400)
        assertEquals(LocalDate(2026, 2, 1), february.dateAt(0f, 0f, four))
        assertEquals(LocalDate(2026, 2, 28), february.dateAt(699f, 399f, four))
    }

    @Test
    fun dayAtMirrorsColumnsInRtl() {
        val six = IntSize(700, 600)
        assertEquals(LocalDate(2026, 8, 9), augustGrid.dateAt(50f, 150f, six, isRtl = true))
        assertEquals(LocalDate(2026, 8, 3), augustGrid.dateAt(650f, 150f, six, isRtl = true))
        assertEquals(LocalDate(2026, 7, 5), julyGrid.dateAt(50f, 50f, IntSize(700, 500), isRtl = true))
    }

    @Test
    fun dayAtSkipsTheWeekNumberColumnAtTheStartEdge() {
        val size = IntSize(770, 600)
        assertEquals(LocalDate(2026, 8, 4), augustGrid.dateAt(220f, 150f, size, leading = 70f))
        // In RTL the week numbers sit on the right, so the days start at the left edge.
        assertEquals(LocalDate(2026, 8, 9), augustGrid.dateAt(50f, 150f, size, isRtl = true, leading = 70f))
    }

    @Test
    fun dayAtClampsOffsetsOutsideTheGrid() {
        val six = IntSize(700, 600)
        assertEquals(LocalDate(2026, 7, 27), augustGrid.dateAt(-10f, -10f, six))
        assertEquals(LocalDate(2026, 9, 6), augustGrid.dateAt(900f, 900f, six))
    }

    @Test
    fun dayAtUsesMeasuredRowTopsForUnevenRows() {
        val tops = intArrayOf(0, 100, 250, 400, 500, 600)
        assertEquals(LocalDate(2026, 8, 3), augustGrid.dateAt(50f, 240f, IntSize(700, 700), rowTops = tops))
        assertEquals(LocalDate(2026, 8, 10), augustGrid.dateAt(50f, 250f, IntSize(700, 700), rowTops = tops))
    }

    @Test
    fun dayAtIsNullForAnEmptyGrid() {
        assertNull(CalendarMonth(july, emptyList()).dateAt(10f, 10f, IntSize(700, 600)))
    }

    @Test
    fun adaptiveDefaultsTo640DpAndComparesByWidth() {
        assertEquals(640.dp, DatePickerDefaults.MinDualMonthWidth)
        assertEquals(640.dp, DateRangeCalendarLayoutMode.Adaptive().minDualMonthWidth)
        assertEquals(DateRangeCalendarLayoutMode.Adaptive(), DateRangeCalendarLayoutMode.Adaptive(640.dp))
        assertEquals(
            DateRangeCalendarLayoutMode.Adaptive().hashCode(),
            DateRangeCalendarLayoutMode.Adaptive(640.dp).hashCode(),
        )
        assertNotEquals(DateRangeCalendarLayoutMode.Adaptive(), DateRangeCalendarLayoutMode.Adaptive(800.dp))
        assertTrue(DateRangeCalendarLayoutMode.Adaptive(800.dp).toString().startsWith("Adaptive(minDualMonthWidth=800"))
    }
}
