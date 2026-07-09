package dev.koza4e4ok.material.calendar.core

import kotlinx.datetime.LocalDate
import kotlinx.datetime.YearMonth
import kotlinx.datetime.number

/**
 * Picks the day to auto-select when auto-advance paging lands on [month]
 * (parity with the original month_view_auto_select_day).
 */
public fun autoSelectDate(
    month: YearMonth,
    previous: LocalDate?,
    policy: AutoSelectDay,
    today: LocalDate,
): LocalDate {
    val isCurrentMonth = month.year == today.year && month.month == today.month
    return when (policy) {
        AutoSelectDay.FirstDayOfMonth -> {
            if (isCurrentMonth) today else month.firstDay
        }

        AutoSelectDay.LastSelectedDay -> {
            when {
                isCurrentMonth -> today
                previous == null -> month.firstDay
                else -> clampedDay(month, previous)
            }
        }

        AutoSelectDay.LastSelectedDayIgnoringCurrent -> {
            if (previous == null) month.firstDay else clampedDay(month, previous)
        }
    }
}

private fun clampedDay(
    month: YearMonth,
    previous: LocalDate,
): LocalDate = LocalDate(month.year, month.month.number, minOf(previous.day, month.numberOfDays))
