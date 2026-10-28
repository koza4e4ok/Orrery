package me.kozakov.orrery.compose

import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.Month
import kotlinx.datetime.YearMonth
import kotlinx.datetime.toJavaLocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

internal actual object PlatformDateFormat {
    actual fun firstDayOfWeek(): DayOfWeek = firstDayOfWeekFromLocale(Locale.getDefault())

    actual fun weekdayName(
        dayOfWeek: DayOfWeek,
        narrow: Boolean,
    ): String = dayOfWeek.displayName(narrow, Locale.getDefault())

    actual fun monthName(
        month: Month,
        short: Boolean,
    ): String = month.displayName(short, Locale.getDefault())

    actual fun yearMonthTitle(yearMonth: YearMonth): String = yearMonth.displayName(Locale.getDefault())

    actual fun formatDate(
        date: LocalDate,
        style: DateFormattingStyle,
    ): String =
        date.toJavaLocalDate().format(
            DateTimeFormatter.ofLocalizedDate(
                when (style) {
                    DateFormattingStyle.Medium -> FormatStyle.MEDIUM
                    DateFormattingStyle.Long -> FormatStyle.LONG
                    DateFormattingStyle.Full -> FormatStyle.FULL
                },
            ),
        )

    actual fun dayHeader(date: LocalDate): String =
        date.toJavaLocalDate().format(DateTimeFormatter.ofPattern("EEE, MMM d", Locale.getDefault()))

    actual fun inputDatePattern(): String = localizedDatePattern(Locale.getDefault())

    actual fun uppercase(text: String): String = text.uppercase(Locale.getDefault())

    actual fun is24HourClock(): Boolean = localeUses24HourClock(Locale.getDefault())

    actual fun formatTime(
        hour: Int,
        minute: Int,
        is24Hour: Boolean,
    ): String = localizedTime(hour, minute, is24Hour, Locale.getDefault())
}

/** Locale-ordered date pattern with 4-digit years (e.g. MM/dd/yyyy for en-US). */
internal expect fun localizedDatePattern(locale: Locale): String

/** Whether [locale] shows time of day on a 24-hour clock. */
internal expect fun localeUses24HourClock(locale: Locale): Boolean

internal actual fun is24HourClock(languageTag: String): Boolean =
    localeUses24HourClock(Locale.forLanguageTag(languageTag))

/** [locale]'s hour-and-minute pattern on the requested clock, e.g. "h:mm a" or "HH:mm" for en-US. */
internal expect fun localeTimePattern(
    locale: Locale,
    is24Hour: Boolean,
): String

internal actual fun formatTime(
    hour: Int,
    minute: Int,
    is24Hour: Boolean,
    languageTag: String,
): String = localizedTime(hour, minute, is24Hour, Locale.forLanguageTag(languageTag))

private fun localizedTime(
    hour: Int,
    minute: Int,
    is24Hour: Boolean,
    locale: Locale,
): String {
    val formatter = DateTimeFormatter.ofPattern(localeTimePattern(locale, is24Hour), locale)
    return LocalTime.of(hour, minute).format(formatter)
}

internal actual fun String.formatTemplate(vararg args: Any): String = format(*args)
