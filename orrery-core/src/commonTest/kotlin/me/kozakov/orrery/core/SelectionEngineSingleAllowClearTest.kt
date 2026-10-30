package me.kozakov.orrery.core

import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SelectionEngineSingleAllowClearTest {
    private val d1 = LocalDate(2026, 7, 1)
    private val d2 = LocalDate(2026, 7, 2)

    @Test
    fun `allowClear defaults to false and keeps the 0_8_0 behavior`() {
        assertFalse(SelectionMode.Single().allowClear)
        assertEquals(SelectionMode.Single(), SelectionMode.Single(autoAdvance = false))
    }

    @Test
    fun `allowClear false keeps the selected day on a repeated tap`() {
        val engine = SelectionEngine(SelectionMode.Single(allowClear = false))
        val first = engine.click(Selection.Empty, d1).selection
        val again = engine.click(first, d1)
        assertEquals(d1, again.selection.single)
        assertTrue(again.events.isEmpty())
        assertEquals(d2, engine.click(again.selection, d2).selection.single)
    }

    @Test
    fun `allowClear true clears the selection on a repeated tap`() {
        val engine = SelectionEngine(SelectionMode.Single(allowClear = true))
        val first = engine.click(Selection.Empty, d1).selection
        val cleared = engine.click(first, d1)
        assertNull(cleared.selection.single)
        assertEquals(Selection.Empty, cleared.selection)
        assertTrue(cleared.events.isEmpty())
    }

    @Test
    fun `allowClear true still replaces the selection when another day is tapped`() {
        val engine = SelectionEngine(SelectionMode.Single(allowClear = true))
        val first = engine.click(Selection.Empty, d1).selection
        assertEquals(d2, engine.click(first, d2).selection.single)
    }

    @Test
    fun `disabled and out of range taps never clear an allowClear selection`() {
        val engine =
            SelectionEngine(
                SelectionMode.Single(allowClear = true),
                bounds = d1..LocalDate(2026, 7, 31),
                disabled = DisabledDates { dates(d2) },
            )
        val selected = Selection(single = d1)
        val intercepted = engine.click(selected, d2)
        assertEquals(selected, intercepted.selection)
        assertEquals(listOf<SelectionEvent>(SelectionEvent.Intercepted(d2)), intercepted.events)
        val outside = engine.click(selected, LocalDate(2026, 8, 1))
        assertEquals(selected, outside.selection)
    }

    @Test
    fun `copy keeps allowClear`() {
        val mode = SelectionMode.Single(autoAdvance = true, allowClear = true)
        val copied = mode.copy(autoSelectDay = AutoSelectDay.LastSelectedDay)
        assertTrue(copied.allowClear)
        assertTrue(copied.autoAdvance)
        assertEquals(AutoSelectDay.LastSelectedDay, copied.autoSelectDay)
    }
}
