package me.kozakov.orrery.core

import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.YearMonth
import kotlinx.datetime.daysUntil
import kotlinx.datetime.isoDayNumber
import kotlinx.datetime.minus
import kotlinx.datetime.monthsUntil
import kotlinx.datetime.plus

/** Maps pager indices to months/weeks within a calendar range. */
public object CalendarPages {
    public fun monthCount(
        start: YearMonth,
        end: YearMonth,
    ): Int = start.monthsUntil(end) + 1

    public fun monthIndex(
        start: YearMonth,
        month: YearMonth,
    ): Int = start.monthsUntil(month)

    public fun monthAt(
        start: YearMonth,
        index: Int,
    ): YearMonth = start.plus(index, DateTimeUnit.MONTH)

    public fun weekStart(
        date: LocalDate,
        firstDayOfWeek: DayOfWeek,
    ): LocalDate =
        date.minus(
            (date.dayOfWeek.isoDayNumber - firstDayOfWeek.isoDayNumber).mod(7),
            DateTimeUnit.DAY,
        )

    public fun weekIndex(
        startDate: LocalDate,
        date: LocalDate,
        firstDayOfWeek: DayOfWeek,
    ): Int = weekStart(startDate, firstDayOfWeek).daysUntil(weekStart(date, firstDayOfWeek)) / 7

    public fun weekCount(
        startDate: LocalDate,
        endDate: LocalDate,
        firstDayOfWeek: DayOfWeek,
    ): Int = weekIndex(startDate, endDate, firstDayOfWeek) + 1
}
