package me.kozakov.orrery.core

import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** #102: booking range guardrails (disallowDisabledInRange, minRangeDays, maxRangeDays). */
class SelectionEngineTest {
    private fun jul(day: Int) = LocalDate(2026, 7, day)

    private val started = Selection(rangeStart = jul(10))

    private fun engine(
        disabled: DisabledDates = DisabledDates.None,
        disallowDisabledInRange: Boolean = false,
        minRangeDays: Int = 1,
        maxRangeDays: Int? = null,
        mode: SelectionMode.Range = SelectionMode.Range(),
    ) = SelectionEngine(
        mode = mode,
        disabled = disabled,
        disallowDisabledInRange = disallowDisabledInRange,
        minRangeDays = minRangeDays,
        maxRangeDays = maxRangeDays,
    )

    @Test
    fun `defaults keep the original range behavior`() {
        val result = engine().click(started, jul(10))
        assertEquals(jul(10)..jul(10), result.selection.range)
        assertTrue(result.events.isEmpty())
        assertEquals(jul(10)..jul(20), engine().click(started, jul(20)).selection.range)
    }

    @Test
    fun `disabled intermediate date restarts the range at the tapped date when disallowed`() {
        val e = engine(disabled = DisabledDates { dates(jul(12)) }, disallowDisabledInRange = true)
        val result = e.click(started, jul(14))
        assertEquals(Selection(rangeStart = jul(14)), result.selection)
        assertEquals(listOf<SelectionEvent>(SelectionEvent.Intercepted(jul(12))), result.events)
        // The restarted anchor completes normally after the booked night.
        assertEquals(jul(14)..jul(16), e.click(result.selection, jul(16)).selection.range)
    }

    @Test
    fun `disabled intermediate date keeps the start by default`() {
        val result = engine(disabled = DisabledDates { dates(jul(12)) }).click(started, jul(14))
        assertEquals(started, result.selection)
        assertEquals(listOf<SelectionEvent>(SelectionEvent.Intercepted(jul(12))), result.events)
    }

    @Test
    fun `a disabled tapped date is still rejected before any guardrail`() {
        val e = engine(disabled = DisabledDates { dates(jul(14)) }, disallowDisabledInRange = true)
        val result = e.click(started, jul(14))
        assertEquals(started, result.selection)
        assertEquals(listOf<SelectionEvent>(SelectionEvent.Intercepted(jul(14))), result.events)
    }

    @Test
    fun `disabled dates outside the span do not affect completion`() {
        val e = engine(disabled = DisabledDates { dates(jul(9), jul(15)) }, disallowDisabledInRange = true)
        assertEquals(jul(10)..jul(14), e.click(started, jul(14)).selection.range)
    }

    @Test
    fun `minRangeDays ignores short completing taps and counts inclusive days`() {
        val e = engine(minRangeDays = 3)
        val sameDay = e.click(started, jul(10)) // 1 day
        assertEquals(started, sameDay.selection)
        assertEquals(listOf<SelectionEvent>(SelectionEvent.RangeTooShort(jul(10), 3)), sameDay.events)
        val twoDays = e.click(started, jul(11)) // 2 days
        assertEquals(started, twoDays.selection)
        assertEquals(listOf<SelectionEvent>(SelectionEvent.RangeTooShort(jul(11), 3)), twoDays.events)
        val threeDays = e.click(started, jul(12)) // 3 days
        assertEquals(jul(10)..jul(12), threeDays.selection.range)
        assertTrue(threeDays.events.isEmpty())
    }

    @Test
    fun `maxRangeDays restarts the range at the tapped date`() {
        val e = engine(maxRangeDays = 7)
        assertEquals(jul(10)..jul(16), e.click(started, jul(16)).selection.range) // 7 days
        val tooLong = e.click(started, jul(17)) // 8 days
        assertEquals(Selection(rangeStart = jul(17)), tooLong.selection)
        assertNull(tooLong.selection.rangeEnd)
        assertEquals(listOf<SelectionEvent>(SelectionEvent.RangeTooLong(jul(17), 7)), tooLong.events)
    }

    @Test
    fun `min equal to max allows exactly that length`() {
        val e = engine(minRangeDays = 3, maxRangeDays = 3)
        assertEquals(started, e.click(started, jul(11)).selection)
        assertEquals(jul(10)..jul(12), e.click(started, jul(12)).selection.range)
        assertEquals(Selection(rangeStart = jul(13)), e.click(started, jul(13)).selection)
    }

    @Test
    fun `disabled check runs before the max check`() {
        val e = engine(disabled = DisabledDates { dates(jul(11)) }, disallowDisabledInRange = true, maxRangeDays = 2)
        val result = e.click(started, jul(20))
        assertEquals(Selection(rangeStart = jul(20)), result.selection)
        assertEquals(listOf<SelectionEvent>(SelectionEvent.Intercepted(jul(11))), result.events)
    }

    @Test
    fun `first tap and earlier tap and tap after a complete range still restart`() {
        val e = engine(minRangeDays = 3, maxRangeDays = 5)
        assertEquals(Selection(rangeStart = jul(10)), e.click(Selection.Empty, jul(10)).selection)
        assertEquals(Selection(rangeStart = jul(5)), e.click(started, jul(5)).selection)
        val complete = Selection(rangeStart = jul(10), rangeEnd = jul(12))
        assertEquals(Selection(rangeStart = jul(25)), e.click(complete, jul(25)).selection)
    }

    @Test
    fun `mode minDays and maxDays keep their original keep-the-start behavior`() {
        val e = engine(mode = SelectionMode.Range(maxDays = 3), maxRangeDays = 10)
        val result = e.click(started, jul(14)) // 5 days: mode max wins and keeps the start
        assertEquals(started, result.selection)
        assertEquals(listOf<SelectionEvent>(SelectionEvent.RangeTooLong(jul(14), 3)), result.events)
    }

    @Test
    fun `replace rejects proposals that break minRangeDays or maxRangeDays`() {
        val e = engine(minRangeDays = 3, maxRangeDays = 5)
        val short = e.replace(Selection.Empty, Selection(rangeStart = jul(10), rangeEnd = jul(11)))
        assertEquals(Selection.Empty, short.selection)
        assertEquals(listOf<SelectionEvent>(SelectionEvent.RangeTooShort(jul(11), 3)), short.events)
        val long = e.replace(Selection.Empty, Selection(rangeStart = jul(10), rangeEnd = jul(15)))
        assertEquals(Selection.Empty, long.selection)
        assertEquals(listOf<SelectionEvent>(SelectionEvent.RangeTooLong(jul(15), 5)), long.events)
        val ok = e.replace(Selection.Empty, Selection(rangeStart = jul(10), rangeEnd = jul(14)))
        assertEquals(jul(10)..jul(14), ok.selection.range)
        // A half-open proposal has no length yet.
        assertEquals(started, e.replace(Selection.Empty, started).selection)
    }

    @Test
    fun `invalid guardrail arguments throw`() {
        assertFailsWith<IllegalArgumentException> { engine(minRangeDays = 0) }
        assertFailsWith<IllegalArgumentException> { engine(minRangeDays = -1) }
        assertFailsWith<IllegalArgumentException> { engine(minRangeDays = 3, maxRangeDays = 2) }
        engine(minRangeDays = 3, maxRangeDays = 3)
    }

    @Test
    fun `guardrails do not affect non-range modes`() {
        val e = SelectionEngine(SelectionMode.Single(), minRangeDays = 5, maxRangeDays = 5)
        assertEquals(jul(10), e.click(Selection.Empty, jul(10)).selection.single)
    }
}
