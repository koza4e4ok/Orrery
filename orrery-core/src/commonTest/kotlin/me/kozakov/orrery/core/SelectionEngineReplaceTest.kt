package me.kozakov.orrery.core

import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class SelectionEngineReplaceTest {
    private val d5 = LocalDate(2026, 7, 5)
    private val d8 = LocalDate(2026, 7, 8)
    private val d10 = LocalDate(2026, 7, 10)
    private val range = Selection(rangeStart = d5, rangeEnd = d10)

    @Test
    fun `valid proposal replaces the selection without events`() {
        val engine = SelectionEngine(SelectionMode.Range())
        val result = engine.replace(Selection.Empty, range)
        assertEquals(range, result.selection)
        assertTrue(result.events.isEmpty())
    }

    @Test
    fun `empty proposal is always accepted`() {
        val engine = SelectionEngine(SelectionMode.Single())
        val current = Selection(single = d5)
        assertEquals(Selection.Empty, engine.replace(current, Selection.Empty).selection)
    }

    @Test
    fun `shape mismatch throws`() {
        val engine = SelectionEngine(SelectionMode.Single())
        assertFailsWith<IllegalArgumentException> { engine.replace(Selection.Empty, range) }
    }

    @Test
    fun `range spanning a disabled date is rejected with Intercepted`() {
        val engine =
            SelectionEngine(SelectionMode.Range(), disabled = DisabledDates { dates(d8) })
        val result = engine.replace(Selection.Empty, range)
        assertEquals(Selection.Empty, result.selection)
        assertEquals(listOf<SelectionEvent>(SelectionEvent.Intercepted(d8)), result.events)
    }

    @Test
    fun `too-long range is rejected with RangeTooLong`() {
        val engine = SelectionEngine(SelectionMode.Range(maxDays = 3))
        val result = engine.replace(Selection.Empty, range) // 6 days
        assertEquals(Selection.Empty, result.selection)
        assertEquals(listOf<SelectionEvent>(SelectionEvent.RangeTooLong(d10, 3)), result.events)
    }

    @Test
    fun `out-of-bounds date is rejected with OutOfRange`() {
        val engine = SelectionEngine(SelectionMode.Single(), bounds = d5..d8)
        val result = engine.replace(Selection.Empty, Selection(single = d10))
        assertEquals(Selection.Empty, result.selection)
        assertEquals(listOf<SelectionEvent>(SelectionEvent.OutOfRange(d10)), result.events)
    }

    @Test
    fun `multi proposal over maxCount is rejected with TooManyDays`() {
        val engine = SelectionEngine(SelectionMode.Multi(maxCount = 2))
        val proposed = Selection(multi = setOf(d5, d8, d10))
        val result = engine.replace(Selection.Empty, proposed)
        assertEquals(Selection.Empty, result.selection)
        assertEquals(1, result.events.size)
        assertTrue(result.events.first() is SelectionEvent.TooManyDays)
    }

    @Test
    fun `half-open range is valid`() {
        val engine = SelectionEngine(SelectionMode.Range())
        val proposed = Selection(rangeStart = d5)
        assertEquals(proposed, engine.replace(Selection.Empty, proposed).selection)
    }
}
