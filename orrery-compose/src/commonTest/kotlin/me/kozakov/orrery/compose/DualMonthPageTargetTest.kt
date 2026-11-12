package me.kozakov.orrery.compose

import kotlinx.datetime.YearMonth
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class DualMonthPageTargetTest {
    private val start = YearMonth(2026, 1)
    private val july = YearMonth(2026, 7)

    @Test
    fun visibleMonthsNeedNoScroll() {
        assertNull(dualMonthPageTarget(july, july, start))
        assertNull(dualMonthPageTarget(july, YearMonth(2026, 8), start))
    }

    @Test
    fun earlierTargetBecomesTheLeftPane() {
        assertEquals(YearMonth(2026, 5), dualMonthPageTarget(july, YearMonth(2026, 5), start))
    }

    @Test
    fun laterTargetBecomesTheRightPane() {
        assertEquals(YearMonth(2026, 8), dualMonthPageTarget(july, YearMonth(2026, 9), start))
        assertEquals(YearMonth(2026, 11), dualMonthPageTarget(july, YearMonth(2026, 12), start))
    }

    @Test
    fun rightPaneTargetNeverScrollsBeforeTheStart() {
        assertEquals(start, dualMonthPageTarget(YearMonth(2025, 11), start, start))
    }
}
