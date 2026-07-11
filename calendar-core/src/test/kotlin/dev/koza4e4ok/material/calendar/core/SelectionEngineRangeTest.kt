package dev.koza4e4ok.material.calendar.core

import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SelectionEngineRangeTest {
    private val d5 = LocalDate(2026, 7, 5)
    private val d8 = LocalDate(2026, 7, 8)
    private val d10 = LocalDate(2026, 7, 10)
    private val d20 = LocalDate(2026, 7, 20)

    @Test
    fun `first click sets start, second completes the range`() {
        val engine = SelectionEngine(SelectionMode.Range())
        val started = engine.click(Selection.Empty, d5)
        assertEquals(d5, started.selection.rangeStart)
        assertNull(started.selection.rangeEnd)
        val completed = engine.click(started.selection, d10)
        assertEquals(d5..d10, completed.selection.range)
        assertTrue(completed.events.isEmpty())
    }

    @Test
    fun `clicking before the start restarts the range`() {
        val engine = SelectionEngine(SelectionMode.Range())
        val started = engine.click(Selection.Empty, d10).selection
        val restarted = engine.click(started, d5)
        assertEquals(d5, restarted.selection.rangeStart)
        assertNull(restarted.selection.rangeEnd)
    }

    @Test
    fun `clicking after a complete range starts a new one`() {
        val engine = SelectionEngine(SelectionMode.Range())
        var sel = engine.click(Selection.Empty, d5).selection
        sel = engine.click(sel, d10).selection
        val restarted = engine.click(sel, d20)
        assertEquals(d20, restarted.selection.rangeStart)
        assertNull(restarted.selection.rangeEnd)
    }

    @Test
    fun `same-day click completes a one-day range when min allows`() {
        val engine = SelectionEngine(SelectionMode.Range())
        val started = engine.click(Selection.Empty, d5).selection
        assertEquals(d5..d5, engine.click(started, d5).selection.range)
    }

    @Test
    fun `min range is inclusive and violations emit RangeTooShort`() {
        val engine = SelectionEngine(SelectionMode.Range(minDays = 4))
        val started = engine.click(Selection.Empty, d5).selection
        // Jul 5..8 = 4 days -> allowed
        assertEquals(d5..d8, engine.click(started, d8).selection.range)
        // Jul 5..7 = 3 days -> too short
        val tooShort = engine.click(started, LocalDate(2026, 7, 7))
        assertNull(tooShort.selection.range)
        assertEquals(
            listOf<SelectionEvent>(SelectionEvent.RangeTooShort(LocalDate(2026, 7, 7), 4)),
            tooShort.events,
        )
    }

    @Test
    fun `max range violations emit RangeTooLong`() {
        val engine = SelectionEngine(SelectionMode.Range(maxDays = 3))
        val started = engine.click(Selection.Empty, d5).selection
        val tooLong = engine.click(started, d10) // 6 days
        assertNull(tooLong.selection.range)
        assertEquals(listOf<SelectionEvent>(SelectionEvent.RangeTooLong(d10, 3)), tooLong.events)
    }

    @Test
    fun `min greater than max clamps both to min`() {
        val mode = SelectionMode.Range(minDays = 5, maxDays = 3)
        assertEquals(5, mode.effectiveMin)
        assertEquals(5, mode.effectiveMax)
        // zero or negative means unbounded
        assertNull(SelectionMode.Range(minDays = 0, maxDays = -1).effectiveMin)
        assertNull(SelectionMode.Range(minDays = 0, maxDays = -1).effectiveMax)
    }

    @Test
    fun `intercepted date inside candidate range blocks completion`() {
        val blocked = d8
        val engine = SelectionEngine(SelectionMode.Range(), disabled = DisabledDates { dates(blocked) })
        val started = engine.click(Selection.Empty, d5).selection
        val result = engine.click(started, d10)
        assertNull(result.selection.range)
        assertEquals(d5, result.selection.rangeStart)
        assertEquals(listOf<SelectionEvent>(SelectionEvent.Intercepted(blocked)), result.events)
    }
}
