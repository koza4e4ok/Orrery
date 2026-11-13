package me.kozakov.orrery.compose

import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Which layout [DateRangeCalendarLayoutMode] resolves to around the width breakpoint (#88b). */
class DateRangeCalendarLayoutModeTest {
    @Test
    fun adaptiveSwitchesToTwoMonthsAtTheDefaultBreakpoint() {
        val adaptive = DateRangeCalendarLayoutMode.Adaptive()
        assertFalse(adaptive.isDualMonth(639.dp), "just below 640dp")
        assertFalse(adaptive.isDualMonth(639.9.dp), "a fraction below 640dp")
        assertTrue(adaptive.isDualMonth(640.dp), "at 640dp")
        assertTrue(adaptive.isDualMonth(640.1.dp), "a fraction above 640dp")
        assertTrue(adaptive.isDualMonth(641.dp), "just above 640dp")
    }

    @Test
    fun adaptiveSwitchesAtACustomBreakpoint() {
        val adaptive = DateRangeCalendarLayoutMode.Adaptive(minDualMonthWidth = 700.dp)
        assertFalse(adaptive.isDualMonth(699.dp))
        assertTrue(adaptive.isDualMonth(700.dp))
        assertTrue(adaptive.isDualMonth(701.dp))
        // The default breakpoint no longer applies.
        assertFalse(adaptive.isDualMonth(640.dp))
    }

    @Test
    fun fixedModesIgnoreTheWidth() {
        for (width in listOf(0.dp, 639.dp, 640.dp, 641.dp, 2000.dp)) {
            assertFalse(DateRangeCalendarLayoutMode.SingleMonth.isDualMonth(width))
            assertTrue(DateRangeCalendarLayoutMode.DualMonth.isDualMonth(width))
        }
    }
}
