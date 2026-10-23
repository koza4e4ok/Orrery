package me.kozakov.orrery.core

import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.Month
import kotlinx.datetime.isoDayNumber
import kotlinx.datetime.minus
import kotlinx.datetime.number
import kotlinx.datetime.plus

/** Pure factories for common range selections. Apply via CalendarSelectionState.set. */
public object SelectionPresets {
    /** [from] .. [from] + [days] - 1; requires [days] >= 1. */
    public fun nextDays(
        from: LocalDate,
        days: Int,
    ): Selection {
        require(days >= 1) { "days must be >= 1, was $days" }
        return Selection(rangeStart = from, rangeEnd = from.plus(days - 1, DateTimeUnit.DAY))
    }

    /** The 7-day week containing [today], starting at [firstDayOfWeek]. */
    public fun thisWeek(
        today: LocalDate,
        firstDayOfWeek: DayOfWeek,
    ): Selection {
        val start = CalendarPages.weekStart(today, firstDayOfWeek)
        return Selection(rangeStart = start, rangeEnd = start.plus(6, DateTimeUnit.DAY))
    }

    /** First..last day of [today]'s month. */
    public fun thisMonth(today: LocalDate): Selection {
        val first = LocalDate(today.year, today.month, 1)
        val last = first.plus(1, DateTimeUnit.MONTH).minus(1, DateTimeUnit.DAY)
        return Selection(rangeStart = first, rangeEnd = last)
    }

    /**
     * The current weekend when [from] is a Saturday or Sunday (the range may
     * start before [from]); otherwise the upcoming Saturday..Sunday.
     */
    public fun nextWeekend(from: LocalDate): Selection {
        val saturday =
            if (from.dayOfWeek == DayOfWeek.SUNDAY) {
                from.minus(1, DateTimeUnit.DAY)
            } else {
                val untilSaturday =
                    (DayOfWeek.SATURDAY.isoDayNumber - from.dayOfWeek.isoDayNumber).mod(7)
                from.plus(untilSaturday, DateTimeUnit.DAY)
            }
        return Selection(rangeStart = saturday, rangeEnd = saturday.plus(1, DateTimeUnit.DAY))
    }

    /** The [days]-day range ending at [until], inclusive; requires [days] >= 1. */
    public fun lastNDays(
        until: LocalDate,
        days: Int,
    ): Selection {
        require(days >= 1) { "days must be >= 1, was $days" }
        return Selection(rangeStart = until.minus(days - 1, DateTimeUnit.DAY), rangeEnd = until)
    }

    /** First..last day of [today]'s calendar quarter (Jan-Mar, Apr-Jun, Jul-Sep, Oct-Dec). */
    public fun thisQuarter(today: LocalDate): Selection {
        val firstMonth = Month(((today.month.number - 1) / 3) * 3 + 1)
        val first = LocalDate(today.year, firstMonth, 1)
        val last = first.plus(3, DateTimeUnit.MONTH).minus(1, DateTimeUnit.DAY)
        return Selection(rangeStart = first, rangeEnd = last)
    }

    /** Monday..Friday of the week containing [today]. */
    public fun workweek(today: LocalDate): Selection {
        val monday = CalendarPages.weekStart(today, DayOfWeek.MONDAY)
        return Selection(rangeStart = monday, rangeEnd = monday.plus(4, DateTimeUnit.DAY))
    }
}
