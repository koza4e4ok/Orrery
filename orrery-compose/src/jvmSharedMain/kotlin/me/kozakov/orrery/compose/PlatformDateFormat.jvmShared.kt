package me.kozakov.orrery.compose

import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.Month
import kotlinx.datetime.YearMonth
import kotlinx.datetime.toJavaLocalDate
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
}

/** Locale-ordered date pattern with 4-digit years (e.g. MM/dd/yyyy for en-US). */
internal expect fun localizedDatePattern(locale: Locale): String

internal actual fun String.formatTemplate(vararg args: Any): String = format(*args)
