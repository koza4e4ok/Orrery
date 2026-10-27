package me.kozakov.orrery.compose

import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.Month
import kotlinx.datetime.YearMonth

/**
 * Locale-sensitive date text, resolved by the platform for the current default
 * locale: java.time on Android and Desktop, NSDateFormatter on iOS and
 * Intl.DateTimeFormat on Web (wasmJs). The only place library code turns
 * dates into localized text.
 */
internal expect object PlatformDateFormat {
    /** Locale-preferred first day of week (e.g. Sunday in US, Monday in DE). */
    fun firstDayOfWeek(): DayOfWeek

    /** Localized weekday display name: short ("Mon") or narrow ("M"). */
    fun weekdayName(
        dayOfWeek: DayOfWeek,
        narrow: Boolean,
    ): String

    /** Localized standalone month name: full ("July") or short ("Jul"). */
    fun monthName(
        month: Month,
        short: Boolean,
    ): String

    /** Localized month + year header, e.g. "July 2026". */
    fun yearMonthTitle(yearMonth: YearMonth): String

    /** Localized date for headlines (Medium) or accessibility descriptions (Long / Full). */
    fun formatDate(
        date: LocalDate,
        style: DateFormattingStyle,
    ): String

    /** Agenda section header, e.g. "Wed, Jul 15". */
    fun dayHeader(date: LocalDate): String

    /** Locale-ordered input pattern with a 4-digit year (e.g. "MM/dd/yyyy" or "dd.MM.yyyy"). */
    fun inputDatePattern(): String

    /** Upper-cases [text] with the rules of the current locale. */
    fun uppercase(text: String): String

    /** Whether the current locale shows time of day on a 24-hour clock (e.g. false in en-US, true in de-DE). */
    fun is24HourClock(): Boolean
}

internal enum class DateFormattingStyle { Medium, Long, Full }

/** Whether [languageTag] (BCP 47, e.g. "de-DE") shows time of day on a 24-hour clock. */
internal expect fun is24HourClock(languageTag: String): Boolean

/** True when a date-format [pattern] uses a 0-23 or 1-24 hour field, ignoring quoted literals. */
internal fun patternUses24HourClock(pattern: String): Boolean {
    val fields = pattern.replace(Regex("'[^']*'"), "")
    return 'H' in fields || 'k' in fields
}

/** printf-style formatting of a built-in string template ("%s", "%1\$d", ...). */
internal expect fun String.formatTemplate(vararg args: Any): String
