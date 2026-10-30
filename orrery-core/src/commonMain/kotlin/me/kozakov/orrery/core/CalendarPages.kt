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

    /**
     * Pager index of the [visibleWeeksCount]-week block (`1..4`) containing
     * [date], counted from the week of [startDate].
     */
    public fun weekIndex(
        startDate: LocalDate,
        date: LocalDate,
        firstDayOfWeek: DayOfWeek,
        visibleWeeksCount: Int = 1,
    ): Int {
        require(visibleWeeksCount in 1..4) { "visibleWeeksCount ($visibleWeeksCount) must be in 1..4" }
        val spanDays = 7 * visibleWeeksCount
        return weekStart(startDate, firstDayOfWeek)
            .daysUntil(weekStart(date, firstDayOfWeek))
            .floorDiv(spanDays)
    }

    public fun weekCount(
        startDate: LocalDate,
        endDate: LocalDate,
        firstDayOfWeek: DayOfWeek,
    ): Int = weekIndex(startDate, endDate, firstDayOfWeek) + 1

    /**
     * Number of [visibleWeeksCount]-week pages (`1..4`) needed to cover
     * [startDate]..[endDate] inclusive.
     */
    public fun weekCount(
        startDate: LocalDate,
        endDate: LocalDate,
        firstDayOfWeek: DayOfWeek,
        visibleWeeksCount: Int = 1,
    ): Int {
        require(visibleWeeksCount in 1..4) { "visibleWeeksCount ($visibleWeeksCount) must be in 1..4" }
        val totalWeeks = weekIndex(startDate, endDate, firstDayOfWeek) + 1
        return ((totalWeeks + visibleWeeksCount - 1) / visibleWeeksCount).coerceAtLeast(1)
    }

    /**
     * Start date of the [index]-th [visibleWeeksCount]-week page (`1..4`)
     * from [startDate].
     */
    public fun weekAt(
        startDate: LocalDate,
        index: Int,
        firstDayOfWeek: DayOfWeek,
        visibleWeeksCount: Int = 1,
    ): LocalDate {
        require(visibleWeeksCount in 1..4) { "visibleWeeksCount ($visibleWeeksCount) must be in 1..4" }
        return weekStart(startDate, firstDayOfWeek).plus(index * 7 * visibleWeeksCount, DateTimeUnit.DAY)
    }
}
