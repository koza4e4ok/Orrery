package me.kozakov.orrery.compose

import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.Month
import kotlinx.datetime.YearMonth
import kotlinx.datetime.isoDayNumber
import kotlinx.datetime.number
import platform.Foundation.NSCalendar
import platform.Foundation.NSCalendarIdentifierGregorian
import platform.Foundation.NSDate
import platform.Foundation.NSDateComponents
import platform.Foundation.NSDateFormatter
import platform.Foundation.NSDateFormatterFullStyle
import platform.Foundation.NSDateFormatterLongStyle
import platform.Foundation.NSDateFormatterMediumStyle
import platform.Foundation.NSDateFormatterNoStyle
import platform.Foundation.NSLocale
import platform.Foundation.NSString
import platform.Foundation.currentLocale
import platform.Foundation.uppercaseStringWithLocale

/**
 * Foundation formatters for the current locale. Dates are always rendered in the
 * Gregorian calendar so the numbers match the ISO [LocalDate], as java.time does.
 */
internal actual object PlatformDateFormat {
    actual fun firstDayOfWeek(): DayOfWeek {
        // NSCalendar numbers weekdays from 1 (Sunday) to 7 (Saturday).
        val weekday = NSCalendar.currentCalendar.firstWeekday.toInt()
        return DayOfWeek((weekday + 5) % 7 + 1)
    }

    actual fun weekdayName(
        dayOfWeek: DayOfWeek,
        narrow: Boolean,
    ): String {
        val formatter = formatter()
        val symbols =
            if (narrow) formatter.veryShortStandaloneWeekdaySymbols else formatter.shortStandaloneWeekdaySymbols
        // Foundation lists weekdays from Sunday.
        return symbols[dayOfWeek.isoDayNumber % 7] as String
    }

    actual fun monthName(
        month: Month,
        short: Boolean,
    ): String {
        val formatter = formatter()
        val symbols = if (short) formatter.shortStandaloneMonthSymbols else formatter.standaloneMonthSymbols
        return symbols[month.number - 1] as String
    }

    actual fun yearMonthTitle(yearMonth: YearMonth): String = format(yearMonth.firstDay, template = "yMMMM")

    actual fun formatDate(
        date: LocalDate,
        style: DateFormattingStyle,
    ): String {
        val formatter = formatter()
        formatter.dateStyle =
            when (style) {
                DateFormattingStyle.Medium -> NSDateFormatterMediumStyle
                DateFormattingStyle.Long -> NSDateFormatterLongStyle
                DateFormattingStyle.Full -> NSDateFormatterFullStyle
            }
        formatter.timeStyle = NSDateFormatterNoStyle
        return formatter.stringFromDate(date.toNSDate())
    }

    actual fun dayHeader(date: LocalDate): String = format(date, template = "EEEMMMd")

    actual fun inputDatePattern(): String {
        val pattern = NSDateFormatter.dateFormatFromTemplate("yyyyMMdd", 0u, NSLocale.currentLocale)
        return normalizeInputPattern(pattern ?: "yyyy-MM-dd")
    }

    @Suppress("CAST_NEVER_SUCCEEDS")
    actual fun uppercase(text: String): String = (text as NSString).uppercaseStringWithLocale(NSLocale.currentLocale)

    actual fun is24HourClock(): Boolean = uses24HourClock(NSLocale.currentLocale)

    actual fun formatTime(
        hour: Int,
        minute: Int,
        is24Hour: Boolean,
    ): String = localizedTime(hour, minute, is24Hour, NSLocale.currentLocale)
}

internal actual fun is24HourClock(languageTag: String): Boolean = uses24HourClock(nsLocale(languageTag))

internal actual fun formatTime(
    hour: Int,
    minute: Int,
    is24Hour: Boolean,
    languageTag: String,
): String = localizedTime(hour, minute, is24Hour, nsLocale(languageTag))

private fun nsLocale(languageTag: String) = NSLocale(localeIdentifier = languageTag.replace('-', '_'))

/** Template "HHmm" or "hmma": Foundation picks the locale's separators and AM/PM placement. */
private fun localizedTime(
    hour: Int,
    minute: Int,
    is24Hour: Boolean,
    locale: NSLocale,
): String {
    val formatter = NSDateFormatter()
    formatter.locale = locale
    formatter.calendar = gregorian()
    val pattern = NSDateFormatter.dateFormatFromTemplate(if (is24Hour) "HHmm" else "hmma", 0u, locale)
    formatter.dateFormat = pattern ?: if (is24Hour) "HH:mm" else "h:mm a"
    val components = NSDateComponents()
    components.year = 2000
    components.month = 1
    components.day = 1
    components.hour = hour.toLong()
    components.minute = minute.toLong()
    val date = checkNotNull(gregorian().dateFromComponents(components)) { "Invalid time $hour:$minute" }
    return formatter.stringFromDate(date)
}

/** "j" asks for the locale's preferred hour field: "h a" in en-US, "HH" in de-DE. */
private fun uses24HourClock(locale: NSLocale): Boolean =
    patternUses24HourClock(NSDateFormatter.dateFormatFromTemplate("j", 0u, locale) ?: "")

private fun gregorian(): NSCalendar =
    checkNotNull(NSCalendar.calendarWithIdentifier(NSCalendarIdentifierGregorian)) {
        "Gregorian calendar unavailable"
    }

private fun formatter(): NSDateFormatter {
    val formatter = NSDateFormatter()
    formatter.locale = NSLocale.currentLocale
    formatter.calendar = gregorian()
    return formatter
}

private fun format(
    date: LocalDate,
    template: String,
): String {
    val formatter = formatter()
    formatter.setLocalizedDateFormatFromTemplate(template)
    return formatter.stringFromDate(date.toNSDate())
}

/** Noon on [this] date in the calendar's (system) time zone, clear of DST transitions. */
private fun LocalDate.toNSDate(): NSDate {
    val components = NSDateComponents()
    components.year = year.toLong()
    components.month = month.number.toLong()
    components.day = day.toLong()
    components.hour = 12
    return checkNotNull(gregorian().dateFromComponents(components)) { "Invalid date $this" }
}
