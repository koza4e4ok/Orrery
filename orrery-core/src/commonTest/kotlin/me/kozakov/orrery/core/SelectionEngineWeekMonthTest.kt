package me.kozakov.orrery.core

import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class SelectionEngineWeekMonthTest {
    // July 2026: the 1st is a Wednesday, the 6th a Monday, the 5th a Sunday.
    private val wed8 = LocalDate(2026, 7, 8)

    @Test
    fun `week mode selects the Monday-based week containing the tapped day`() {
        val result = SelectionEngine(SelectionMode.Week()).click(Selection.Empty, wed8)
        assertEquals(LocalDate(2026, 7, 6)..LocalDate(2026, 7, 12), result.selection.range)
        assertTrue(result.events.isEmpty())
    }

    @Test
    fun `week mode honors firstDayOfWeek`() {
        val engine = SelectionEngine(SelectionMode.Week(firstDayOfWeek = DayOfWeek.SUNDAY))
        assertEquals(LocalDate(2026, 7, 5)..LocalDate(2026, 7, 11), engine.click(Selection.Empty, wed8).selection.range)
        // Tapping the first day of the week selects the same week.
        val sunday = engine.click(Selection.Empty, LocalDate(2026, 7, 5)).selection.range
        assertEquals(LocalDate(2026, 7, 5)..LocalDate(2026, 7, 11), sunday)
    }

    @Test
    fun `week mode spans a month boundary`() {
        val result = SelectionEngine(SelectionMode.Week()).click(Selection.Empty, LocalDate(2026, 7, 1))
        assertEquals(LocalDate(2026, 6, 29)..LocalDate(2026, 7, 5), result.selection.range)
    }

    @Test
    fun `week mode clamps the week to the bounds`() {
        val bounds = LocalDate(2026, 7, 8)..LocalDate(2026, 7, 31)
        val engine = SelectionEngine(SelectionMode.Week(), bounds = bounds)
        assertEquals(LocalDate(2026, 7, 8)..LocalDate(2026, 7, 12), engine.click(Selection.Empty, wed8).selection.range)
        val lastWeek = engine.click(Selection.Empty, LocalDate(2026, 7, 30)).selection.range
        assertEquals(LocalDate(2026, 7, 27)..LocalDate(2026, 7, 31), lastWeek)
    }

    @Test
    fun `week mode rejects a week containing a disabled day`() {
        val blocked = LocalDate(2026, 7, 11)
        val engine = SelectionEngine(SelectionMode.Week(), disabled = DisabledDates { dates(blocked) })
        val result = engine.click(Selection.Empty, wed8)
        assertEquals(Selection.Empty, result.selection)
        assertEquals(listOf<SelectionEvent>(SelectionEvent.Intercepted(blocked)), result.events)
    }

    @Test
    fun `disabled days outside the clamped week do not block it`() {
        val engine =
            SelectionEngine(
                SelectionMode.Week(),
                bounds = LocalDate(2026, 7, 8)..LocalDate(2026, 7, 31),
                disabled = DisabledDates { dates(LocalDate(2026, 7, 6)) },
            )
        assertEquals(LocalDate(2026, 7, 8)..LocalDate(2026, 7, 12), engine.click(Selection.Empty, wed8).selection.range)
    }

    @Test
    fun `tapping the tapped day itself when disabled is intercepted`() {
        val engine = SelectionEngine(SelectionMode.Week(), disabled = DisabledDates { dates(wed8) })
        val events = engine.click(Selection.Empty, wed8).events
        assertEquals(listOf<SelectionEvent>(SelectionEvent.Intercepted(wed8)), events)
    }

    @Test
    fun `tapping inside the selected week clears it and tapping another week moves it`() {
        val engine = SelectionEngine(SelectionMode.Week())
        val selected = engine.click(Selection.Empty, wed8).selection
        assertEquals(Selection.Empty, engine.click(selected, LocalDate(2026, 7, 12)).selection)
        val moved = engine.click(selected, LocalDate(2026, 7, 15)).selection
        assertEquals(LocalDate(2026, 7, 13)..LocalDate(2026, 7, 19), moved.range)
    }

    @Test
    fun `allowClear false keeps the selected week`() {
        val engine = SelectionEngine(SelectionMode.Week(allowClear = false))
        val selected = engine.click(Selection.Empty, wed8).selection
        val again = engine.click(selected, LocalDate(2026, 7, 9))
        assertEquals(selected, again.selection)
        assertTrue(again.events.isEmpty())
    }

    @Test
    fun `month mode selects the whole month`() {
        val engine = SelectionEngine(SelectionMode.Month())
        assertEquals(LocalDate(2026, 7, 1)..LocalDate(2026, 7, 31), engine.click(Selection.Empty, wed8).selection.range)
        val leapFeb = engine.click(Selection.Empty, LocalDate(2028, 2, 14)).selection.range
        assertEquals(LocalDate(2028, 2, 1)..LocalDate(2028, 2, 29), leapFeb)
        val feb = engine.click(Selection.Empty, LocalDate(2026, 2, 14)).selection.range
        assertEquals(LocalDate(2026, 2, 1)..LocalDate(2026, 2, 28), feb)
    }

    @Test
    fun `month mode clamps to the bounds`() {
        val engine = SelectionEngine(SelectionMode.Month(), bounds = LocalDate(2026, 7, 10)..LocalDate(2026, 8, 20))
        assertEquals(
            LocalDate(2026, 7, 10)..LocalDate(2026, 7, 31),
            engine.click(Selection.Empty, LocalDate(2026, 7, 15)).selection.range,
        )
        assertEquals(
            LocalDate(2026, 8, 1)..LocalDate(2026, 8, 20),
            engine.click(Selection.Empty, LocalDate(2026, 8, 1)).selection.range,
        )
    }

    @Test
    fun `month mode rejects a month containing a disabled day`() {
        val blocked = LocalDate(2026, 7, 25)
        val engine = SelectionEngine(SelectionMode.Month(), disabled = DisabledDates { dates(blocked) })
        val result = engine.click(Selection.Empty, wed8)
        assertEquals(Selection.Empty, result.selection)
        assertEquals(listOf<SelectionEvent>(SelectionEvent.Intercepted(blocked)), result.events)
    }

    @Test
    fun `month mode toggles off and moves between months`() {
        val engine = SelectionEngine(SelectionMode.Month())
        val july = engine.click(Selection.Empty, wed8).selection
        assertEquals(Selection.Empty, engine.click(july, LocalDate(2026, 7, 31)).selection)
        val august = engine.click(july, LocalDate(2026, 8, 3)).selection
        assertEquals(LocalDate(2026, 8, 1)..LocalDate(2026, 8, 31), august.range)
    }

    @Test
    fun `out of bounds taps report OutOfRange`() {
        val engine = SelectionEngine(SelectionMode.Month(), bounds = LocalDate(2026, 7, 10)..LocalDate(2026, 7, 20))
        val result = engine.click(Selection.Empty, wed8)
        assertEquals(Selection.Empty, result.selection)
        assertEquals(listOf<SelectionEvent>(SelectionEvent.OutOfRange(wed8)), result.events)
    }

    @Test
    fun `replace accepts ranges and rejects other shapes in week and month modes`() {
        val week = SelectionPresets.thisWeek(wed8, DayOfWeek.MONDAY)
        assertEquals(week, SelectionEngine(SelectionMode.Week()).replace(Selection.Empty, week).selection)
        val month = SelectionPresets.thisMonth(wed8)
        assertEquals(month, SelectionEngine(SelectionMode.Month()).replace(Selection.Empty, month).selection)
        assertFailsWith<IllegalArgumentException> {
            SelectionEngine(SelectionMode.Week()).replace(Selection.Empty, Selection(single = wed8))
        }
    }
}
