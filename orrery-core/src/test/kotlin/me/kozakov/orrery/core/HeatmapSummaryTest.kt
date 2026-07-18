package me.kozakov.orrery.core

import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals

class HeatmapSummaryTest {
    private val jul1 = LocalDate(2026, 7, 1)
    private val jul10 = LocalDate(2026, 7, 10)

    @Test
    fun `empty range is all zero`() {
        val summary = heatmapSummary(jul10..jul1) { true }
        assertEquals(HeatmapSummary(0, 0, 0), summary)
    }

    @Test
    fun `no active days`() {
        val summary = heatmapSummary(jul1..jul10) { false }
        assertEquals(HeatmapSummary(0, 0, 0), summary)
    }

    @Test
    fun `all active days`() {
        val summary = heatmapSummary(jul1..jul10) { true }
        assertEquals(HeatmapSummary(10, 10, 10), summary)
    }

    @Test
    fun `gap splits streaks`() {
        val active = setOf(1, 2, 3, 4, 6, 7).map { LocalDate(2026, 7, it) }.toSet()
        val summary = heatmapSummary(jul1..jul10) { it in active }
        assertEquals(HeatmapSummary(activeDays = 6, currentStreak = 0, longestStreak = 4), summary)
    }

    @Test
    fun `current streak ends at range end`() {
        val active = setOf(8, 9, 10).map { LocalDate(2026, 7, it) }.toSet()
        val summary = heatmapSummary(jul1..jul10) { it in active }
        assertEquals(HeatmapSummary(activeDays = 3, currentStreak = 3, longestStreak = 3), summary)
    }

    @Test
    fun `single day range`() {
        assertEquals(HeatmapSummary(1, 1, 1), heatmapSummary(jul1..jul1) { true })
        assertEquals(HeatmapSummary(0, 0, 0), heatmapSummary(jul1..jul1) { false })
    }
}
