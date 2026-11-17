package me.kozakov.orrery.compose

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.datetime.LocalDate
import kotlinx.datetime.YearMonth
import me.kozakov.orrery.core.CalendarDay
import me.kozakov.orrery.core.CalendarWeek
import me.kozakov.orrery.core.DayPosition
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

/**
 * The tier B types of orrery-compose are plain classes since 1.0 (#89, ledger section D).
 * Their hand-written equals, hashCode, copy and toString must behave like the data classes
 * they replace.
 */
class PlainClassContractTest {
    private val fixed = CalendarRowHeight.Fixed(height = 52.5.dp)
    private val fillViewport = CalendarRowHeight.FillViewport(minRowHeight = 36.5.dp)
    private val ring = TodayIndicator.Ring(width = 2.5.dp)

    private val july = YearMonth(2026, 7)
    private val monthChange = VisibleMonthChange(month = july, source = ScrollSource.Programmatic)

    private val week =
        CalendarWeek(
            listOf(
                CalendarDay(LocalDate(2026, 6, 30), DayPosition.InDate),
                CalendarDay(LocalDate(2026, 7, 1), DayPosition.MonthDate),
            ),
        )
    private val otherWeek = CalendarWeek(listOf(CalendarDay(LocalDate(2026, 7, 6), DayPosition.MonthDate)))
    private val weekChange = VisibleWeekChange(week = week, source = ScrollSource.UserDrag)

    /**
     * [equal] is built separately from [base]; [copied] is `base.copy()` with no arguments; each of [differing]
     * changes one property of [base] to a value with a different hash.
     *
     * Every property of [base] differs from its constructor default, from null and from every other property of
     * the same type, so [copied] only equals [base] if each `copy` default reads its own property.
     */
    private fun <T : Any> assertContract(
        base: T,
        equal: T,
        copied: T,
        vararg differing: T,
    ) {
        assertEquals(base, equal)
        assertEquals(equal, base)
        assertEquals(base.hashCode(), equal.hashCode())
        assertEquals(base.toString(), equal.toString())
        assertEquals(base, copied, "copy() with no arguments")
        assertEquals(base.hashCode(), copied.hashCode(), "copy() with no arguments")
        assertNotEquals<Any?>(base, null)
        assertNotEquals<Any>(base, "not a $base")
        differing.forEachIndexed { index, variant ->
            assertNotEquals(base, variant, "variant $index: $variant")
            assertNotEquals(variant, base, "variant $index: $variant")
            assertNotEquals(base.hashCode(), variant.hashCode(), "hashCode of variant $index: $variant")
        }
    }

    /**
     * Each of [differing] changes only a [ScrollSource] of [base]. Enum hashCode is the identity hash, so only
     * equals is asserted here, not a hash inequality.
     */
    private fun <T : Any> assertDiffersBySource(
        base: T,
        vararg differing: T,
    ) {
        differing.forEachIndexed { index, variant ->
            assertNotEquals(base, variant, "source variant $index: $variant")
            assertNotEquals(variant, base, "source variant $index: $variant")
        }
    }

    /**
     * Dp is a value class over Float and the data classes compared it like Float.equals:
     * -0.dp and 0.dp differ, NaN (Dp.Unspecified) equals NaN and hashes the same.
     */
    private fun <T : Any> assertDpSemantics(make: (Dp) -> T) {
        assertNotEquals(make(0.dp), make((-0f).dp))
        assertNotEquals(make(0.dp).hashCode(), make((-0f).dp).hashCode())
        assertEquals(make(0.dp), make(0.dp))
        assertEquals(make(Dp.Unspecified), make(Dp(Float.NaN)))
        assertEquals(make(Dp.Unspecified).hashCode(), make(Dp(Float.NaN)).hashCode())
        assertNotEquals(make(Dp.Unspecified), make(0.dp))
    }

    @Test
    fun `CalendarRowHeight Fixed compares every property`() {
        assertContract(
            fixed,
            CalendarRowHeight.Fixed(52.5.dp),
            fixed.copy(),
            fixed.copy(height = 48.dp),
        )
        assertDpSemantics { CalendarRowHeight.Fixed(it) }
    }

    @Test
    fun `CalendarRowHeight Fixed copy and toString`() {
        assertEquals(48.dp, fixed.copy(height = 48.dp).height)
        assertEquals("Fixed(height=52.5.dp)", fixed.toString())
        assertEquals("Fixed(height=Dp.Unspecified)", CalendarRowHeight.Fixed(Dp.Unspecified).toString())
        // A different subtype with the same Dp is not equal.
        assertNotEquals<CalendarRowHeight>(CalendarRowHeight.Fixed(40.dp), CalendarRowHeight.FillViewport(40.dp))
    }

    @Test
    fun `CalendarRowHeight FillViewport compares every property`() {
        assertContract(
            fillViewport,
            CalendarRowHeight.FillViewport(36.5.dp),
            fillViewport.copy(),
            fillViewport.copy(minRowHeight = 40.dp),
            CalendarRowHeight.FillViewport(),
        )
        assertDpSemantics { CalendarRowHeight.FillViewport(it) }
    }

    @Test
    fun `CalendarRowHeight FillViewport keeps its default and copy and toString`() {
        assertEquals(40.dp, CalendarRowHeight.FillViewport().minRowHeight)
        assertEquals(CalendarRowHeight.FillViewport(40.dp), CalendarRowHeight.FillViewport())
        assertEquals(44.5.dp, fillViewport.copy(minRowHeight = 44.5.dp).minRowHeight)
        assertEquals("FillViewport(minRowHeight=36.5.dp)", fillViewport.toString())
        assertEquals("FillViewport(minRowHeight=40.0.dp)", CalendarRowHeight.FillViewport().toString())
    }

    @Test
    fun `TodayIndicator Ring compares every property`() {
        assertContract(
            ring,
            TodayIndicator.Ring(2.5.dp),
            ring.copy(),
            ring.copy(width = 1.5.dp),
            TodayIndicator.Ring(),
        )
        assertDpSemantics { TodayIndicator.Ring(it) }
    }

    @Test
    fun `TodayIndicator Ring keeps its default and copy and toString`() {
        assertEquals(1.dp, TodayIndicator.Ring().width)
        assertEquals(TodayIndicator.Ring(1.dp), TodayIndicator.Ring())
        assertEquals(3.5.dp, ring.copy(width = 3.5.dp).width)
        assertEquals("Ring(width=2.5.dp)", ring.toString())
        assertEquals("Ring(width=1.0.dp)", TodayIndicator.Ring().toString())
    }

    @Test
    fun `VisibleMonthChange compares every property`() {
        assertContract(
            monthChange,
            VisibleMonthChange(YearMonth(2026, 7), ScrollSource.Programmatic),
            monthChange.copy(),
            monthChange.copy(month = YearMonth(2026, 8)),
        )
        assertDiffersBySource(
            monthChange,
            monthChange.copy(source = ScrollSource.UserDrag),
            monthChange.copy(source = ScrollSource.Initial),
        )
    }

    @Test
    fun `VisibleMonthChange copy preserves unspecified fields`() {
        val moved = monthChange.copy(month = YearMonth(2026, 9))
        assertEquals(YearMonth(2026, 9), moved.month)
        assertEquals(ScrollSource.Programmatic, moved.source)

        val dragged = monthChange.copy(source = ScrollSource.UserDrag)
        assertEquals(july, dragged.month)
        assertEquals(ScrollSource.UserDrag, dragged.source)
    }

    @Test
    fun `VisibleMonthChange toString keeps the data class format`() {
        assertEquals("VisibleMonthChange(month=2026-07, source=Programmatic)", monthChange.toString())
    }

    @Test
    fun `VisibleWeekChange compares every property`() {
        assertContract(
            weekChange,
            VisibleWeekChange(
                CalendarWeek(
                    listOf(
                        CalendarDay(LocalDate(2026, 6, 30), DayPosition.InDate),
                        CalendarDay(LocalDate(2026, 7, 1), DayPosition.MonthDate),
                    ),
                ),
                ScrollSource.UserDrag,
            ),
            weekChange.copy(),
            weekChange.copy(week = otherWeek),
        )
        assertDiffersBySource(
            weekChange,
            weekChange.copy(source = ScrollSource.Programmatic),
            weekChange.copy(source = ScrollSource.Initial),
        )
    }

    @Test
    fun `VisibleWeekChange copy preserves unspecified fields`() {
        val moved = weekChange.copy(week = otherWeek)
        assertEquals(otherWeek, moved.week)
        assertEquals(ScrollSource.UserDrag, moved.source)

        val programmatic = weekChange.copy(source = ScrollSource.Programmatic)
        assertEquals(week, programmatic.week)
        assertEquals(ScrollSource.Programmatic, programmatic.source)
    }

    @Test
    fun `VisibleWeekChange toString keeps the data class format`() {
        assertEquals(
            "VisibleWeekChange(week=CalendarWeek(days=[CalendarDay(date=2026-06-30, position=InDate), " +
                "CalendarDay(date=2026-07-01, position=MonthDate)]), source=UserDrag)",
            weekChange.toString(),
        )
    }
}
