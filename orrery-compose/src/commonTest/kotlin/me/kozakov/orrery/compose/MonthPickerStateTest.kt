package me.kozakov.orrery.compose

import androidx.compose.runtime.saveable.SaverScope
import kotlinx.datetime.YearMonth
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class MonthPickerStateTest {
    private val range = YearMonth(2020, 1)..YearMonth(2030, 12)

    @Test
    fun selectsMonthsInsideTheRange() {
        val state = OrreryMonthPickerState(initialSelectedMonth = null, range = range)
        assertNull(state.selectedMonth)
        state.select(YearMonth(2027, 3))
        assertEquals(YearMonth(2027, 3), state.selectedMonth)
        state.select(null)
        assertNull(state.selectedMonth)
    }

    @Test
    fun ignoresMonthsOutsideTheRange() {
        val state = OrreryMonthPickerState(initialSelectedMonth = YearMonth(2026, 7), range = range)
        state.select(YearMonth(2031, 1))
        assertEquals(YearMonth(2026, 7), state.selectedMonth)
        state.select(YearMonth(2019, 12))
        assertEquals(YearMonth(2026, 7), state.selectedMonth)
    }

    @Test
    fun dropsAnInitialMonthOutsideTheRange() {
        assertNull(OrreryMonthPickerState(initialSelectedMonth = YearMonth(2040, 1), range = range).selectedMonth)
    }

    @Test
    fun initialYearIsClampedToTheRange() {
        assertEquals(2027, initialPickerYear(YearMonth(2027, 3), YearMonth(2026, 7), range))
        assertEquals(2026, initialPickerYear(null, YearMonth(2026, 7), range))
        assertEquals(2030, initialPickerYear(null, YearMonth(2045, 1), range))
        assertEquals(2020, initialPickerYear(null, YearMonth(1999, 5), range))
    }

    @Test
    fun monthEncodingRoundTrips() {
        for (month in listOf(YearMonth(2026, 1), YearMonth(2026, 12), YearMonth(0, 1), YearMonth(-1, 12))) {
            assertEquals(month, OrreryMonthPickerState.decode(OrreryMonthPickerState.encode(month)))
        }
        assertEquals(2026 * 12 + 6, OrreryMonthPickerState.encode(YearMonth(2026, 7)))
    }

    @Test
    fun saverRoundTrips() {
        val saver = OrreryMonthPickerState.saver(range)
        val scope = SaverScope { true }
        val selected = OrreryMonthPickerState(initialSelectedMonth = YearMonth(2028, 11), range = range)
        val restored = saver.restore(assertNotNull(with(saver) { scope.save(selected) }))
        assertEquals(YearMonth(2028, 11), restored?.selectedMonth)
        assertEquals(range, restored?.range)

        val empty = OrreryMonthPickerState(initialSelectedMonth = null, range = range)
        assertNull(saver.restore(assertNotNull(with(saver) { scope.save(empty) }))?.selectedMonth)
    }
}
