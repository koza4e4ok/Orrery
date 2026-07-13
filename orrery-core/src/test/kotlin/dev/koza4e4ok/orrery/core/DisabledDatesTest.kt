package dev.koza4e4ok.orrery.core

import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertSame
import kotlin.test.assertTrue

class DisabledDatesTest {
    private val mon6 = LocalDate(2026, 7, 6)
    private val tue7 = LocalDate(2026, 7, 7)
    private val sat11 = LocalDate(2026, 7, 11)
    private val sun12 = LocalDate(2026, 7, 12)

    @Test
    fun `explicit dates are disabled`() {
        val disabled = DisabledDates { dates(mon6, tue7) }
        assertTrue(mon6 in disabled)
        assertTrue(tue7 in disabled)
        assertFalse(sat11 in disabled)
    }

    @Test
    fun `date collections are disabled`() {
        val disabled = DisabledDates { dates(listOf(mon6)) }
        assertTrue(mon6 in disabled)
        assertFalse(tue7 in disabled)
    }

    @Test
    fun `ranges are inclusive`() {
        val disabled = DisabledDates { range(mon6..sat11) }
        assertTrue(mon6 in disabled)
        assertTrue(sat11 in disabled)
        assertFalse(LocalDate(2026, 7, 5) in disabled)
        assertFalse(sun12 in disabled)
    }

    @Test
    fun `days of week are disabled`() {
        val disabled = DisabledDates { daysOfWeek(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY) }
        assertTrue(sat11 in disabled)
        assertTrue(sun12 in disabled)
        assertFalse(mon6 in disabled)
    }

    @Test
    fun `before and after are exclusive`() {
        val disabled =
            DisabledDates {
                before(mon6)
                after(sat11)
            }
        assertTrue(LocalDate(2026, 7, 5) in disabled)
        assertFalse(mon6 in disabled)
        assertFalse(sat11 in disabled)
        assertTrue(sun12 in disabled)
    }

    @Test
    fun `repeated before and after keep the widest bounds`() {
        val disabled =
            DisabledDates {
                before(mon6)
                before(tue7)
                after(sat11)
                after(LocalDate(2026, 7, 10))
            }
        assertTrue(mon6 in disabled) // before(tue7) is wider
        assertTrue(sat11 in disabled) // after(jul 10) is wider
        assertFalse(LocalDate(2026, 7, 9) in disabled)
    }

    @Test
    fun `predicates are consulted`() {
        val disabled = DisabledDates { predicate { it.day == 7 } }
        assertTrue(tue7 in disabled)
        assertFalse(mon6 in disabled)
    }

    @Test
    fun `entries combine as a union`() {
        val disabled =
            DisabledDates {
                dates(mon6)
                daysOfWeek(DayOfWeek.SUNDAY)
            }
        assertTrue(mon6 in disabled)
        assertTrue(sun12 in disabled)
        assertFalse(tue7 in disabled)
    }

    @Test
    fun `empty builder returns None`() {
        assertSame(DisabledDates.None, DisabledDates {})
    }

    @Test
    fun `None disables nothing`() {
        assertFalse(mon6 in DisabledDates.None)
    }
}
