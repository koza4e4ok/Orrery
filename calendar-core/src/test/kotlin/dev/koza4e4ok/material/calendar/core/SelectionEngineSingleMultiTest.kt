package dev.koza4e4ok.material.calendar.core

import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SelectionEngineSingleMultiTest {
    private val d1 = LocalDate(2026, 7, 1)
    private val d2 = LocalDate(2026, 7, 2)
    private val d3 = LocalDate(2026, 7, 3)

    @Test
    fun `single mode replaces selection on each click and never deselects`() {
        val engine = SelectionEngine(SelectionMode.Single())
        val first = engine.click(Selection.Empty, d1)
        assertEquals(d1, first.selection.single)
        assertTrue(first.events.isEmpty())
        // clicking the same date keeps it selected (parity: no deselect in original)
        assertEquals(d1, engine.click(first.selection, d1).selection.single)
        assertEquals(d2, engine.click(first.selection, d2).selection.single)
    }

    @Test
    fun `none mode ignores clicks`() {
        val engine = SelectionEngine(SelectionMode.None)
        val result = engine.click(Selection.Empty, d1)
        assertEquals(Selection.Empty, result.selection)
        assertTrue(result.events.isEmpty())
    }

    @Test
    fun `clicks outside bounds emit OutOfRange and change nothing`() {
        val engine =
            SelectionEngine(
                SelectionMode.Single(),
                bounds = LocalDate(2026, 7, 2)..LocalDate(2026, 7, 31),
            )
        val result = engine.click(Selection.Empty, d1)
        assertEquals(Selection.Empty, result.selection)
        assertEquals(listOf<SelectionEvent>(SelectionEvent.OutOfRange(d1)), result.events)
    }

    @Test
    fun `intercepted dates emit Intercepted and change nothing`() {
        val engine = SelectionEngine(SelectionMode.Single(), disabled = DisabledDates { dates(d2) })
        val result = engine.click(Selection.Empty, d2)
        assertEquals(Selection.Empty, result.selection)
        assertEquals(listOf<SelectionEvent>(SelectionEvent.Intercepted(d2)), result.events)
    }

    @Test
    fun `multi mode toggles dates`() {
        val engine = SelectionEngine(SelectionMode.Multi())
        var sel = engine.click(Selection.Empty, d1).selection
        sel = engine.click(sel, d2).selection
        assertEquals(setOf(d1, d2), sel.multi)
        sel = engine.click(sel, d1).selection // toggle off
        assertEquals(setOf(d2), sel.multi)
    }

    @Test
    fun `multi mode enforces maxCount`() {
        val engine = SelectionEngine(SelectionMode.Multi(maxCount = 2))
        var sel = engine.click(Selection.Empty, d1).selection
        sel = engine.click(sel, d2).selection
        val result = engine.click(sel, d3)
        assertEquals(setOf(d1, d2), result.selection.multi)
        assertEquals(listOf<SelectionEvent>(SelectionEvent.TooManyDays(d3, 2)), result.events)
        // removing one still works at the cap
        assertEquals(setOf(d2), engine.click(result.selection, d1).selection.multi)
    }
}
