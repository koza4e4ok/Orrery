package me.kozakov.orrery.compose.internal

import androidx.compose.runtime.Immutable
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.YearMonth
import kotlinx.datetime.isoDayNumber
import me.kozakov.orrery.InternalOrreryApi

private const val DAYS_PER_WEEK = 7

/**
 * Immutable grid geometry of one month without out-dates: it depends only on
 * [yearMonth] and [firstDayOfWeek]. Shared by MiniMonth and the snapshot renderer.
 *
 * Internal Orrery API: public only for sibling `orrery-*` modules.
 */
@InternalOrreryApi
@Immutable
public class MonthGridModel(
    /** The month this grid lays out. */
    public val yearMonth: YearMonth,
    /** The weekday of the first column. */
    public val firstDayOfWeek: DayOfWeek,
) {
    /** Empty cells before day 1 in the first row (0..6). */
    public val leadingBlanks: Int =
        (yearMonth.firstDay.dayOfWeek.isoDayNumber - firstDayOfWeek.isoDayNumber).mod(DAYS_PER_WEEK)

    /** Days in the month (28..31). */
    public val dayCount: Int = yearMonth.numberOfDays

    /** Week rows of the grid without out-dates (4..6). */
    public val rowCount: Int = (leadingBlanks + dayCount + DAYS_PER_WEEK - 1) / DAYS_PER_WEEK

    /** Logical (start-to-end) column of [day]. */
    public fun column(day: Int): Int = (leadingBlanks + day - 1) % DAYS_PER_WEEK

    /** Week row of [day], 0 for the row that holds day 1. */
    public fun row(day: Int): Int = (leadingBlanks + day - 1) / DAYS_PER_WEEK
}
