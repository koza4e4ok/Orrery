package me.kozakov.orrery.compose.internal

import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.YearMonth
import me.kozakov.orrery.InternalOrreryApi
import me.kozakov.orrery.compose.DateFormattingStyle
import me.kozakov.orrery.compose.PlatformDateFormat
import me.kozakov.orrery.compose.formatTime

/**
 * The locale-sensitive date text of `orrery-compose` (java.time, NSDateFormatter or
 * Intl.DateTimeFormat, for the current default locale), for the snapshot renderer of
 * `orrery-widget`, so both modules format dates the same way.
 *
 * Internal Orrery API: public only for sibling `orrery-*` modules.
 */
@InternalOrreryApi
public object PlatformDateText {
    /** Month and year title, for example "November 2026". */
    public fun yearMonthTitle(yearMonth: YearMonth): String = PlatformDateFormat.yearMonthTitle(yearMonth)

    /** Weekday name: short ("Mon") or narrow ("M"). */
    public fun weekdayName(
        dayOfWeek: DayOfWeek,
        narrow: Boolean,
    ): String = PlatformDateFormat.weekdayName(dayOfWeek, narrow)

    /** Medium date, for example "Nov 14, 2026". */
    public fun mediumDate(date: LocalDate): String = PlatformDateFormat.formatDate(date, DateFormattingStyle.Medium)

    /** Long date for accessibility text, for example "November 14, 2026". */
    public fun longDate(date: LocalDate): String = PlatformDateFormat.formatDate(date, DateFormattingStyle.Long)

    /** Agenda day heading, for example "Sat, Nov 14". */
    public fun dayHeader(date: LocalDate): String = PlatformDateFormat.dayHeader(date)

    /** Whether the current locale uses a 24-hour clock. */
    public fun is24HourClock(): Boolean = PlatformDateFormat.is24HourClock()

    /** Time of day in the current locale, on a 24-hour ([is24Hour]) or 12-hour clock. */
    public fun time(
        hour: Int,
        minute: Int,
        is24Hour: Boolean,
    ): String = PlatformDateFormat.formatTime(hour, minute, is24Hour)

    /** Time of day for [languageTag] (BCP 47, for example "uk-UA") instead of the current locale. */
    public fun time(
        hour: Int,
        minute: Int,
        is24Hour: Boolean,
        languageTag: String,
    ): String = formatTime(hour, minute, is24Hour, languageTag)
}
