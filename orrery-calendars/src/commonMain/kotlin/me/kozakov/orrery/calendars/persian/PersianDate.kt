@file:Suppress("ktlint:standard:argument-list-wrapping")

package me.kozakov.orrery.calendars.persian

import kotlinx.datetime.LocalDate
import kotlinx.datetime.Month

/**
 * A date in the Persian (Solar Hijri, Jalali) calendar, the civil calendar of Iran and
 * Afghanistan. [month] is 1 (Farvardin) .. 12 (Esfand); months 1-6 have 31 days, 7-11 have
 * 30 and Esfand has 29, or 30 in a leap year. Supported years: -61..3177 AP.
 */
public data class PersianDate(
    val year: Int,
    val month: Int,
    val day: Int,
) {
    init {
        require(year in PersianCalendar.FIRST_YEAR..PersianCalendar.LAST_YEAR) {
            "year must be in ${PersianCalendar.FIRST_YEAR}..${PersianCalendar.LAST_YEAR}, was $year"
        }
        require(month in 1..12) { "month must be in 1..12, was $month" }
        require(day in 1..31) { "day must be in 1..31, was $day" }
    }
}

/**
 * Converts this date to the Persian calendar.
 *
 * @throws IllegalArgumentException outside the supported years -61..3177 AP
 *   (560-03-20 .. 3799-03-20).
 */
public fun LocalDate.toPersianDate(): PersianDate = PersianCalendar.fromLocalDate(this)

/**
 * Converts this Persian date to a Gregorian [LocalDate].
 *
 * @throws IllegalArgumentException if [PersianDate.day] exceeds the month length.
 */
public fun PersianDate.toLocalDate(): LocalDate {
    val length = monthLength()
    require(day <= length) { "day $day exceeds the $length days of $year-$month" }
    return LocalDate.fromEpochDays(PersianCalendar.nowruz(year) + PersianCalendar.dayOfYearOffset(month) + day - 1)
}

/** Whether Esfand of this date's year has 30 days. */
public fun PersianDate.isLeapYear(): Boolean = PersianCalendar.isLeapYear(year)

/** Number of days in this date's month. */
public fun PersianDate.monthLength(): Int =
    when {
        month <= 6 -> 31
        month <= 11 -> 30
        isLeapYear() -> 30
        else -> 29
    }

/**
 * Borkowski's arithmetic for the astronomical Persian calendar (K. M. Borkowski, "The
 * Persian calendar for 3000 years", Earth, Moon and Planets 74, 1996), using the published
 * table of years where the 33-year leap cycle breaks. Valid for -61..3177 AP.
 */
internal object PersianCalendar {
    const val FIRST_YEAR: Int = -61
    const val LAST_YEAR: Int = 3177

    private val breaks =
        intArrayOf(
            -61, 9, 38, 199, 426, 686, 756, 818, 1111, 1181,
            1210, 1635, 2060, 2097, 2192, 2262, 2324, 2394, 2456, 3178,
        )

    private class YearInfo(
        val leap: Int,
        val gregorianYear: Int,
        val marchDay: Int,
    )

    private fun yearInfo(year: Int): YearInfo {
        require(year in FIRST_YEAR..LAST_YEAR) { "year must be in $FIRST_YEAR..$LAST_YEAR, was $year" }
        var leapJ = -14
        var jp = breaks[0]
        var jump = 0
        for (i in 1 until breaks.size) {
            val jm = breaks[i]
            jump = jm - jp
            if (year < jm) break
            leapJ += jump / 33 * 8 + jump % 33 / 4
            jp = jm
        }
        var n = year - jp
        leapJ += n / 33 * 8 + (n % 33 + 3) / 4
        if (jump % 33 == 4 && jump - n == 4) leapJ += 1
        val gregorianYear = year + 621
        val leapG = gregorianYear / 4 - (gregorianYear / 100 + 1) * 3 / 4 - 150
        val marchDay = 20 + leapJ - leapG
        if (jump - n < 6) n = n - jump + (jump + 4) / 33 * 33
        var leap = ((n + 1) % 33 - 1) % 4
        if (leap == -1) leap = 4
        return YearInfo(leap, gregorianYear, marchDay)
    }

    fun isLeapYear(year: Int): Boolean = yearInfo(year).leap == 0

    /** Epoch day of 1 Farvardin of [year]. */
    fun nowruz(year: Int): Long {
        val info = yearInfo(year)
        return LocalDate(info.gregorianYear, Month.MARCH, info.marchDay).toEpochDays()
    }

    /** Days from 1 Farvardin to the first day of [month]. */
    fun dayOfYearOffset(month: Int): Int = if (month <= 7) (month - 1) * 31 else 186 + (month - 7) * 30

    /** Epoch day of 1 Farvardin [FIRST_YEAR]. */
    private val firstEpochDay: Long by lazy { nowruz(FIRST_YEAR) }

    /** Epoch day after the last day of [LAST_YEAR]. */
    private val endEpochDayExclusive: Long by lazy { nowruz(LAST_YEAR) + if (isLeapYear(LAST_YEAR)) 366 else 365 }

    fun supports(date: LocalDate): Boolean = date.toEpochDays() in firstEpochDay until endEpochDayExclusive

    fun fromLocalDate(date: LocalDate): PersianDate {
        require(supports(date)) { "Persian calendar supports $FIRST_YEAR..$LAST_YEAR AP, was $date" }
        var year = minOf(date.year - 621, LAST_YEAR)
        if (date.toEpochDays() < nowruz(year)) year--
        val dayOfYear = (date.toEpochDays() - nowruz(year)).toInt()
        return if (dayOfYear < 186) {
            PersianDate(year, dayOfYear / 31 + 1, dayOfYear % 31 + 1)
        } else {
            val rest = dayOfYear - 186
            PersianDate(year, rest / 30 + 7, rest % 30 + 1)
        }
    }
}
