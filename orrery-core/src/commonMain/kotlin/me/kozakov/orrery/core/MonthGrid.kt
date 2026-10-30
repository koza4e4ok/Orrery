package me.kozakov.orrery.core

import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.YearMonth
import kotlinx.datetime.isoDayNumber
import kotlinx.datetime.minus

/**
 * Builds the day grid for [yearMonth]. Leading cells are previous-month
 * [DayPosition.InDate]s, trailing cells next-month [DayPosition.OutDate]s.
 * Row count follows the original CalendarView line-count semantics.
 */
public fun monthGrid(
    yearMonth: YearMonth,
    firstDayOfWeek: DayOfWeek = DayOfWeek.MONDAY,
    outDateStyle: OutDateStyle = OutDateStyle.EndOfRow,
): CalendarMonth =
    monthGrid(
        yearMonth = yearMonth,
        firstDayOfWeek = firstDayOfWeek,
        outDateStyle = outDateStyle,
        visibleDaysOfWeek = ALL_DAYS_OF_WEEK,
    )

/**
 * Builds the day grid for [yearMonth] filtered to [visibleDaysOfWeek].
 * When [visibleDaysOfWeek] filters out days (for example a Mon–Fri work week),
 * any week row that ends up with zero [DayPosition.MonthDate] days is dropped
 * unless [outDateStyle] is [OutDateStyle.EndOfGrid].
 */
public fun monthGrid(
    yearMonth: YearMonth,
    firstDayOfWeek: DayOfWeek = DayOfWeek.MONDAY,
    outDateStyle: OutDateStyle = OutDateStyle.EndOfRow,
    visibleDaysOfWeek: Set<DayOfWeek>,
): CalendarMonth {
    require(visibleDaysOfWeek.isNotEmpty()) { "visibleDaysOfWeek must not be empty" }
    val firstDay = yearMonth.firstDay
    val lastDay = yearMonth.lastDay
    val offset = (firstDay.dayOfWeek.isoDayNumber - firstDayOfWeek.isoDayNumber).mod(7)
    val rows =
        when (outDateStyle) {
            OutDateStyle.EndOfGrid -> 6
            OutDateStyle.EndOfRow, OutDateStyle.None -> (offset + yearMonth.numberOfDays + 6) / 7
        }
    val gridStart = firstDay.minus(offset, DateTimeUnit.DAY)
    val allVisible = visibleDaysOfWeek.size == 7
    // ⚡ Bolt optimization: Iterating over epoch days and converting back to LocalDate
    // is significantly faster than using `LocalDate.plus(1, DateTimeUnit.DAY)`, which
    // performs full calendar math on every step.
    val gridStartEpoch = gridStart.toEpochDays()
    val rawWeeks =
        List(rows) { row ->
            val rowStartEpoch = gridStartEpoch + row * 7
            val days =
                buildList(visibleDaysOfWeek.size) {
                    for (col in 0 until 7) {
                        val date = LocalDate.fromEpochDays(rowStartEpoch + col)
                        if (allVisible || date.dayOfWeek in visibleDaysOfWeek) {
                            val position =
                                when {
                                    date < firstDay -> DayPosition.InDate
                                    date > lastDay -> DayPosition.OutDate
                                    else -> DayPosition.MonthDate
                                }
                            add(CalendarDay(date, position))
                        }
                    }
                }
            CalendarWeek(days)
        }
    val weeks =
        if (allVisible || outDateStyle == OutDateStyle.EndOfGrid) {
            rawWeeks
        } else {
            rawWeeks.filter { week -> week.days.any { it.position == DayPosition.MonthDate } }
        }
    return CalendarMonth(yearMonth, weeks)
}

/** The week containing [anchor]; days are always [DayPosition.MonthDate]. */
public fun weekGrid(
    anchor: LocalDate,
    firstDayOfWeek: DayOfWeek = DayOfWeek.MONDAY,
): CalendarWeek = weekGrid(anchor, firstDayOfWeek, ALL_DAYS_OF_WEEK)

/**
 * The week containing [anchor] filtered to [visibleDaysOfWeek]; days are
 * always [DayPosition.MonthDate].
 */
public fun weekGrid(
    anchor: LocalDate,
    firstDayOfWeek: DayOfWeek = DayOfWeek.MONDAY,
    visibleDaysOfWeek: Set<DayOfWeek>,
): CalendarWeek {
    require(visibleDaysOfWeek.isNotEmpty()) { "visibleDaysOfWeek must not be empty" }
    val offset = (anchor.dayOfWeek.isoDayNumber - firstDayOfWeek.isoDayNumber).mod(7)
    val start = anchor.minus(offset, DateTimeUnit.DAY)
    val allVisible = visibleDaysOfWeek.size == 7
    // ⚡ Bolt optimization: Iterating over epoch days is significantly faster than `plus`.
    val startEpoch = start.toEpochDays()
    val days =
        buildList(visibleDaysOfWeek.size) {
            for (i in 0 until 7) {
                val date = LocalDate.fromEpochDays(startEpoch + i)
                if (allVisible || date.dayOfWeek in visibleDaysOfWeek) {
                    add(CalendarDay(date, DayPosition.MonthDate))
                }
            }
        }
    return CalendarWeek(days)
}

/**
 * [weeksCount] consecutive weeks (`1..4`) starting with the week containing
 * [anchor], filtered to [visibleDaysOfWeek].
 */
public fun multiWeekGrid(
    anchor: LocalDate,
    weeksCount: Int = 2,
    firstDayOfWeek: DayOfWeek = DayOfWeek.MONDAY,
    visibleDaysOfWeek: Set<DayOfWeek> = ALL_DAYS_OF_WEEK,
): List<CalendarWeek> {
    require(weeksCount in 1..4) { "weeksCount ($weeksCount) must be in 1..4" }
    require(visibleDaysOfWeek.isNotEmpty()) { "visibleDaysOfWeek must not be empty" }
    val offset = (anchor.dayOfWeek.isoDayNumber - firstDayOfWeek.isoDayNumber).mod(7)
    val startEpoch = anchor.minus(offset, DateTimeUnit.DAY).toEpochDays()
    return List(weeksCount) { weekIndex ->
        weekGrid(
            anchor = LocalDate.fromEpochDays(startEpoch + weekIndex * 7),
            firstDayOfWeek = firstDayOfWeek,
            visibleDaysOfWeek = visibleDaysOfWeek,
        )
    }
}
