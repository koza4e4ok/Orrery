package me.kozakov.orrery.core

import kotlinx.datetime.DayOfWeek

internal val ALL_DAYS_OF_WEEK: Set<DayOfWeek> = DayOfWeek.entries.toSet()

/** The 7 days of the week starting at [firstDayOfWeek]. */
public fun daysOfWeek(firstDayOfWeek: DayOfWeek = DayOfWeek.MONDAY): List<DayOfWeek> =
    List(7) { DayOfWeek.entries[(firstDayOfWeek.ordinal + it) % 7] }

/**
 * The days of the week in [visibleDaysOfWeek] ordered starting at
 * [firstDayOfWeek] (or the first visible day after it).
 */
public fun daysOfWeek(
    firstDayOfWeek: DayOfWeek = DayOfWeek.MONDAY,
    visibleDaysOfWeek: Set<DayOfWeek> = ALL_DAYS_OF_WEEK,
): List<DayOfWeek> {
    require(visibleDaysOfWeek.isNotEmpty()) { "visibleDaysOfWeek must not be empty" }
    return List(7) { DayOfWeek.entries[(firstDayOfWeek.ordinal + it) % 7] }
        .filter { it in visibleDaysOfWeek }
}
