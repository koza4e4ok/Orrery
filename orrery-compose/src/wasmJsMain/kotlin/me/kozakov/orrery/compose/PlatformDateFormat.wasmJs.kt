// The js() bodies read their parameters by name, which detekt cannot see.
@file:Suppress("UnusedParameter")

package me.kozakov.orrery.compose

import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.Month
import kotlinx.datetime.YearMonth
import kotlinx.datetime.isoDayNumber
import kotlinx.datetime.number

/** Intl.DateTimeFormat in the browser's default locale; dates are formatted as UTC calendar days. */
internal actual object PlatformDateFormat {
    actual fun firstDayOfWeek(): DayOfWeek {
        val firstDay = jsFirstDayOfWeek()
        return when {
            firstDay in 1..7 -> DayOfWeek(firstDay)
            // Intl.Locale week info is missing in older engines.
            jsDefaultLocale().endsWith("-US") -> DayOfWeek.SUNDAY
            else -> DayOfWeek.MONDAY
        }
    }

    actual fun weekdayName(
        dayOfWeek: DayOfWeek,
        narrow: Boolean,
    ): String {
        // 2024-01-01 is a Monday, so January 1-7 run Monday to Sunday.
        val options = if (narrow) """{"weekday":"narrow"}""" else """{"weekday":"short"}"""
        return intlFormat(2024, 1, dayOfWeek.isoDayNumber, options)
    }

    actual fun monthName(
        month: Month,
        short: Boolean,
    ): String = intlFormat(2024, month.number, 1, if (short) """{"month":"short"}""" else """{"month":"long"}""")

    actual fun yearMonthTitle(yearMonth: YearMonth): String =
        intlFormat(yearMonth.year, yearMonth.month.number, 1, """{"year":"numeric","month":"long"}""")

    actual fun formatDate(
        date: LocalDate,
        style: DateFormattingStyle,
    ): String {
        val dateStyle =
            when (style) {
                DateFormattingStyle.Medium -> "medium"
                DateFormattingStyle.Long -> "long"
                DateFormattingStyle.Full -> "full"
            }
        return intlFormat(date.year, date.month.number, date.day, """{"dateStyle":"$dateStyle"}""")
    }

    actual fun dayHeader(date: LocalDate): String =
        intlFormat(date.year, date.month.number, date.day, """{"weekday":"short","month":"short","day":"numeric"}""")

    actual fun inputDatePattern(): String = normalizeInputPattern(jsInputDatePattern())

    actual fun uppercase(text: String): String = jsUppercase(text)

    actual fun is24HourClock(): Boolean = jsIs24HourClock("")

    actual fun formatTime(
        hour: Int,
        minute: Int,
        is24Hour: Boolean,
    ): String = jsFormatTime(hour, minute, is24Hour, "")
}

internal actual fun is24HourClock(languageTag: String): Boolean = jsIs24HourClock(languageTag)

internal actual fun formatTime(
    hour: Int,
    minute: Int,
    is24Hour: Boolean,
    languageTag: String,
): String = jsFormatTime(hour, minute, is24Hour, languageTag)

/** Hour and minute on hourCycle h23 or h12 for [tag] (empty = default locale), as a UTC wall time. */
private fun jsFormatTime(
    hour: Int,
    minute: Int,
    is24Hour: Boolean,
    tag: String,
): String =
    js(
        """{
    const options = {
        hour: is24Hour ? '2-digit' : 'numeric',
        minute: '2-digit',
        hourCycle: is24Hour ? 'h23' : 'h12',
        timeZone: 'UTC',
    };
    const time = new Date(Date.UTC(2000, 0, 1, hour, minute));
    return new Intl.DateTimeFormat(tag ? tag : undefined, options).format(time);
}""",
    )

/** hourCycle h23/h24 of Intl.DateTimeFormat for [tag] (empty = default locale); hour12 in older engines. */
private fun jsIs24HourClock(tag: String): Boolean =
    js(
        """{
    const options = new Intl.DateTimeFormat(tag ? tag : undefined, { hour: 'numeric' }).resolvedOptions();
    if (options.hourCycle) {
        return options.hourCycle === 'h23' || options.hourCycle === 'h24';
    }
    return options.hour12 === false;
}""",
    )

private fun intlFormat(
    year: Int,
    month: Int,
    day: Int,
    options: String,
): String =
    js(
        """{
    const date = new Date(0);
    date.setUTCFullYear(year, month - 1, day);
    const opts = JSON.parse(options);
    opts.timeZone = 'UTC';
    return new Intl.DateTimeFormat(undefined, opts).format(date);
}""",
    )

/** ISO first day of week (1 = Monday ... 7 = Sunday) from Intl.Locale week info, or 0 if unsupported. */
private fun jsFirstDayOfWeek(): Int =
    js(
        """{
    try {
        const locale = new Intl.Locale(new Intl.DateTimeFormat().resolvedOptions().locale);
        const info = typeof locale.getWeekInfo === 'function' ? locale.getWeekInfo() : locale.weekInfo;
        return info && info.firstDay ? info.firstDay : 0;
    } catch (e) {
        return 0;
    }
}""",
    )

private fun jsDefaultLocale(): String = js("new Intl.DateTimeFormat().resolvedOptions().locale")

/** The locale's numeric date order as a y/M/d pattern with quoted literals, e.g. MM'/'dd'/'yyyy. */
private fun jsInputDatePattern(): String =
    js(
        """{
    const options = { year: 'numeric', month: '2-digit', day: '2-digit', timeZone: 'UTC' };
    const parts = new Intl.DateTimeFormat(undefined, options).formatToParts(new Date(Date.UTC(2026, 10, 22)));
    return parts.map(function (part) {
        switch (part.type) {
            case 'year': return 'yyyy';
            case 'month': return 'MM';
            case 'day': return 'dd';
            default: {
                const text = part.value.replace(/[\u200e\u200f]/g, '');
                return text ? "'" + text.replace(/'/g, "''") + "'" : '';
            }
        }
    }).join('');
}""",
    )

private fun jsUppercase(text: String): String = js("text.toLocaleUpperCase()")
