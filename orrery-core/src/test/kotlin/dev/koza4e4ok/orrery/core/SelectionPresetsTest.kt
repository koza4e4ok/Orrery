package dev.koza4e4ok.orrery.core

import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class SelectionPresetsTest {
    private val wed15 = LocalDate(2026, 7, 15)

    @Test
    fun `nextDays is inclusive of the start`() {
        val sel = SelectionPresets.nextDays(wed15, 7)
        assertEquals(wed15..LocalDate(2026, 7, 21), sel.range)
    }

    @Test
    fun `nextDays of one selects a single-day range`() {
        assertEquals(wed15..wed15, SelectionPresets.nextDays(wed15, 1).range)
    }

    @Test
    fun `nextDays rejects non-positive counts`() {
        assertFailsWith<IllegalArgumentException> { SelectionPresets.nextDays(wed15, 0) }
    }

    @Test
    fun `thisWeek honors the first day of week`() {
        assertEquals(
            LocalDate(2026, 7, 13)..LocalDate(2026, 7, 19),
            SelectionPresets.thisWeek(wed15, DayOfWeek.MONDAY).range,
        )
        assertEquals(
            LocalDate(2026, 7, 12)..LocalDate(2026, 7, 18),
            SelectionPresets.thisWeek(wed15, DayOfWeek.SUNDAY).range,
        )
    }

    @Test
    fun `thisMonth spans the whole month`() {
        assertEquals(
            LocalDate(2026, 7, 1)..LocalDate(2026, 7, 31),
            SelectionPresets.thisMonth(wed15).range,
        )
        // February in a non-leap year
        assertEquals(
            LocalDate(2026, 2, 1)..LocalDate(2026, 2, 28),
            SelectionPresets.thisMonth(LocalDate(2026, 2, 10)).range,
        )
    }

    @Test
    fun `nextWeekend from a weekday is the upcoming weekend`() {
        assertEquals(
            LocalDate(2026, 7, 18)..LocalDate(2026, 7, 19),
            SelectionPresets.nextWeekend(wed15).range,
        )
    }

    @Test
    fun `nextWeekend from a Saturday is the current weekend`() {
        assertEquals(
            LocalDate(2026, 7, 11)..LocalDate(2026, 7, 12),
            SelectionPresets.nextWeekend(LocalDate(2026, 7, 11)).range,
        )
    }

    @Test
    fun `nextWeekend from a Sunday is the current weekend even though it started yesterday`() {
        assertEquals(
            LocalDate(2026, 7, 11)..LocalDate(2026, 7, 12),
            SelectionPresets.nextWeekend(LocalDate(2026, 7, 12)).range,
        )
    }
}
