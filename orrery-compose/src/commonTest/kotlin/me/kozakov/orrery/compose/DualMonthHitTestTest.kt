package me.kozakov.orrery.compose

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** Pane and edge hit tests of the dual-month container drag (#88b). */
class DualMonthHitTestTest {
    // Two 400 px panes visible, the left one (index 6) scrolled 100 px out.
    private val panes =
        listOf(
            DualMonthPaneSpan(6, -100, 400),
            DualMonthPaneSpan(7, 300, 400),
            DualMonthPaneSpan(8, 700, 400),
        )

    @Test
    fun paneUnderThePointerInLtr() {
        assertEquals(6, dualMonthPaneAt(0f, 800, isRtl = false, panes))
        assertEquals(6, dualMonthPaneAt(299f, 800, isRtl = false, panes))
        assertEquals(7, dualMonthPaneAt(300f, 800, isRtl = false, panes))
        assertEquals(8, dualMonthPaneAt(799f, 800, isRtl = false, panes))
    }

    @Test
    fun rtlMeasuresOffsetsFromTheRightEdge() {
        // The start pane is on the right in RTL.
        assertEquals(6, dualMonthPaneAt(799f, 800, isRtl = true, panes))
        assertEquals(7, dualMonthPaneAt(400f, 800, isRtl = true, panes))
        assertEquals(8, dualMonthPaneAt(50f, 800, isRtl = true, panes))
    }

    @Test
    fun noPaneOutsideTheItems() {
        assertNull(dualMonthPaneAt(1200f, 800, isRtl = false, panes))
        assertNull(dualMonthPaneAt(10f, 800, isRtl = false, emptyList()))
    }

    @Test
    fun edgesPageTowardTheirMonth() {
        assertEquals(-1, dualMonthEdgePageDirection(10f, 800, 24f, isRtl = false))
        assertEquals(1, dualMonthEdgePageDirection(790f, 800, 24f, isRtl = false))
        assertEquals(0, dualMonthEdgePageDirection(24f, 800, 24f, isRtl = false))
        assertEquals(0, dualMonthEdgePageDirection(400f, 800, 24f, isRtl = false))
        // RTL: the later month is on the left.
        assertEquals(1, dualMonthEdgePageDirection(10f, 800, 24f, isRtl = true))
        assertEquals(-1, dualMonthEdgePageDirection(790f, 800, 24f, isRtl = true))
    }
}
