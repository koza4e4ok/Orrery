package me.kozakov.orrery.core

import kotlinx.datetime.LocalDate

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

    // ⚡ Bolt Optimization:
    // Iterating over a range of integer epoch days and converting each using
    // `LocalDate.fromEpochDays()` is significantly faster than using
    // `LocalDate.plus(1, DateTimeUnit.DAY)`, which performs heavy calendar math on every iteration.
    val startEpoch = range.start.toEpochDays()
    val endEpoch = range.endInclusive.toEpochDays()

    for (epoch in startEpoch..endEpoch) {
        val date = LocalDate.fromEpochDays(epoch)
        if (isActive(date)) {
            activeDays++
            run++
            if (run > longest) longest = run
        } else {
            run = 0
        }
    }
    return HeatmapSummary(activeDays = activeDays, currentStreak = run, longestStreak = longest)
}
