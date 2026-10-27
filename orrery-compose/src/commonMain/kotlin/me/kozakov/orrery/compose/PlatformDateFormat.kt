package me.kozakov.orrery.compose

import androidx.compose.runtime.Composable
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

    /**
     * Localized time of day on a 24-hour ([is24Hour]) or 12-hour clock, with the
     * locale's own day-period text and order: "14:05", "2:05 PM", "下午2:05".
     */
    fun formatTime(
        hour: Int,
        minute: Int,
        is24Hour: Boolean,
    ): String
}

internal enum class DateFormattingStyle { Medium, Long, Full }

/** Whether [languageTag] (BCP 47, e.g. "de-DE") shows time of day on a 24-hour clock. */
internal expect fun is24HourClock(languageTag: String): Boolean

/** [PlatformDateFormat.formatTime] for [languageTag] (BCP 47, e.g. "zh-CN") instead of the current locale. */
internal expect fun formatTime(
    hour: Int,
    minute: Int,
    is24Hour: Boolean,
    languageTag: String,
): String

/**
 * The device's clock setting, used as the default is24Hour: Android's "Use
 * 24-hour format" setting, the current locale's clock elsewhere.
 */
@Composable
internal expect fun systemUses24HourClock(): Boolean

/**
 * Rewrites a time [pattern] to the requested clock, for platforms without a
 * skeleton API: hour fields become "HH" (24-hour) or "h" plus a trailing " a"
 * (12-hour), and day-period fields are dropped on a 24-hour clock. Quoted
 * literals are kept; a pattern already on the requested clock is unchanged.
 */
internal fun timePatternForClock(
    pattern: String,
    is24Hour: Boolean,
): String {
    if (patternUses24HourClock(pattern) == is24Hour) return pattern
    val rewritten =
        pattern
            .split('\'')
            // Even segments are pattern letters, odd ones quoted literals.
            .mapIndexed { index, part -> if (index % 2 == 0) rewriteClockFields(part, is24Hour) else part }
            .joinToString("'")
            .trim()
    return if (is24Hour) rewritten else "$rewritten a"
}

private fun rewriteClockFields(
    fields: String,
    is24Hour: Boolean,
): String =
    if (is24Hour) {
        fields.replace(Regex("[hK]+"), "HH").replace(Regex("[abB]+"), "")
    } else {
        fields.replace(Regex("[Hk]+"), "h")
    }

/** True when a date-format [pattern] uses a 0-23 or 1-24 hour field, ignoring quoted literals. */
internal fun patternUses24HourClock(pattern: String): Boolean {
    val fields = pattern.replace(Regex("'[^']*'"), "")
    return 'H' in fields || 'k' in fields
}

/** printf-style formatting of a built-in string template ("%s", "%1\$d", ...). */
internal expect fun String.formatTemplate(vararg args: Any): String
