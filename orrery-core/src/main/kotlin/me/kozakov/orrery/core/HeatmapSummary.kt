package me.kozakov.orrery.core

import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.plus

/**
 * Aggregates for a heatmap range: how many days were active, the streak
 * ending at the range end, and the longest streak anywhere in the range.
 */
public data class HeatmapSummary(
    public val activeDays: Int,
    public val currentStreak: Int,
    public val longestStreak: Int,
)

/**
 * Single-pass scan of [range]. [HeatmapSummary.currentStreak] counts
 * consecutive active days ending exactly at `range.endInclusive` (zero
 * when that day is inactive). An empty range (start after end) yields
 * all zeros.
 */
public fun heatmapSummary(
    range: ClosedRange<LocalDate>,
    isActive: (LocalDate) -> Boolean,
): HeatmapSummary {
    var activeDays = 0
    var longest = 0
    var run = 0
    var date = range.start
    while (date <= range.endInclusive) {
        if (isActive(date)) {
            activeDays++
            run++
            if (run > longest) longest = run
        } else {
            run = 0
        }
        date = date.plus(1, DateTimeUnit.DAY)
    }
    return HeatmapSummary(activeDays = activeDays, currentStreak = run, longestStreak = longest)
}
