package me.kozakov.orrery.compose

import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.Month
import kotlinx.datetime.TimeZone
import kotlinx.datetime.YearMonth
import kotlinx.datetime.isoDayNumber
import kotlinx.datetime.number
import kotlinx.datetime.toJavaLocalDate
import kotlinx.datetime.todayIn
import java.time.format.TextStyle
import java.time.temporal.WeekFields
import java.util.Locale
import kotlin.time.Clock

/** Locale-preferred first day of week (e.g. Sunday for en-US, Monday for de-DE). */
public fun firstDayOfWeekFromLocale(locale: Locale = Locale.getDefault()): DayOfWeek =
    DayOfWeek.entries[WeekFields.of(locale).firstDayOfWeek.value - 1]

/** Localized weekday name, short ("Mon") or narrow ("M"). */
public fun DayOfWeek.displayName(
    narrow: Boolean = false,
    locale: Locale = Locale.getDefault(),
): String =
    java.time.DayOfWeek.of(isoDayNumber).getDisplayName(
        if (narrow) TextStyle.NARROW_STANDALONE else TextStyle.SHORT_STANDALONE,
        locale,
    )

/** Localized month + year title, e.g. "July 2026". */
public fun YearMonth.displayName(locale: Locale = Locale.getDefault()): String {
    val month = firstDay.toJavaLocalDate().month.getDisplayName(TextStyle.FULL_STANDALONE, locale)
    return "$month $year"
}

/** Localized standalone month name, e.g. "July" or "Jul". */
public fun Month.displayName(
    short: Boolean = false,
    locale: Locale = Locale.getDefault(),
): String =
    java.time.Month.of(number).getDisplayName(
        if (short) TextStyle.SHORT_STANDALONE else TextStyle.FULL_STANDALONE,
        locale,
    )

public fun currentDate(): LocalDate = Clock.System.todayIn(TimeZone.currentSystemDefault())

public fun currentYearMonth(): YearMonth = currentDate().let { YearMonth(it.year, it.month) }
