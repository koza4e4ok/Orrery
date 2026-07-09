package dev.koza4e4ok.material.calendar.core

import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.YearMonth
import kotlinx.datetime.isoDayNumber
import kotlinx.datetime.minus
import kotlinx.datetime.plus

/**
 * Builds the day grid for [yearMonth]. Leading cells are previous-month
 * [DayPosition.InDate]s, trailing cells next-month [DayPosition.OutDate]s.
 * Row count follows the original CalendarView line-count semantics.
 */
public fun monthGrid(
    yearMonth: YearMonth,
    firstDayOfWeek: DayOfWeek = DayOfWeek.MONDAY,
    outDateStyle: OutDateStyle = OutDateStyle.EndOfRow,
): CalendarMonth {
    val firstDay = yearMonth.firstDay
    val lastDay = yearMonth.lastDay
    val offset = (firstDay.dayOfWeek.isoDayNumber - firstDayOfWeek.isoDayNumber).mod(7)
    val rows =
        when (outDateStyle) {
            OutDateStyle.EndOfGrid -> 6
            OutDateStyle.EndOfRow, OutDateStyle.None -> (offset + yearMonth.numberOfDays + 6) / 7
        }
    val gridStart = firstDay.minus(offset, DateTimeUnit.DAY)
    val weeks =
        List(rows) { row ->
            CalendarWeek(
                List(7) { col ->
                    val date = gridStart.plus(row * 7 + col, DateTimeUnit.DAY)
                    val position =
                        when {
                            date < firstDay -> DayPosition.InDate
                            date > lastDay -> DayPosition.OutDate
                            else -> DayPosition.MonthDate
                        }
                    CalendarDay(date, position)
                },
            )
        }
    return CalendarMonth(yearMonth, weeks)
}

/** The week containing [anchor]; days are always [DayPosition.MonthDate]. */
public fun weekGrid(
    anchor: LocalDate,
    firstDayOfWeek: DayOfWeek = DayOfWeek.MONDAY,
): CalendarWeek {
    val offset = (anchor.dayOfWeek.isoDayNumber - firstDayOfWeek.isoDayNumber).mod(7)
    val start = anchor.minus(offset, DateTimeUnit.DAY)
    return CalendarWeek(
        List(7) { i -> CalendarDay(start.plus(i, DateTimeUnit.DAY), DayPosition.MonthDate) },
    )
}
