package me.kozakov.orrery.lunar

import kotlinx.datetime.LocalDate
import kotlinx.datetime.number

/**
 * A date in the Chinese lunisolar calendar.
 *
 * Convert with [toLunarDate] and [toLocalDate]. [toLunarDate] supports Gregorian dates in
 * 1900..2099 (1-30 January 1900 therefore map to lunar year 1899); [toLocalDate] supports lunar
 * years 1900..2099.
 *
 * Not validated: neither the constructor nor [toLocalDate] checks [month], [day] or
 * [isLeapMonth], so values that do not exist in that lunar year give a wrong date rather than
 * an exception.
 *
 * @property year Lunar year: the Gregorian year in which this lunar year starts (Chinese New
 *   Year, between 21 January and 20 February). A date in January or February before the New
 *   Year therefore belongs to the previous lunar year.
 * @property month Lunar month, 1..12. A leap month repeats the number of the month before it
 *   and sets [isLeapMonth].
 * @property day Day of the lunar month, 1..30 (a lunar month has 29 or 30 days).
 * @property isLeapMonth `true` if [month] is the intercalary (leap) month that follows the
 *   regular month with the same number.
 */
public data class LunarDate(
    val year: Int,
    val month: Int,
    val day: Int,
    val isLeapMonth: Boolean = false,
)

private fun getBits(
    data: Int,
    length: Int,
    shift: Int,
): Int = (data and (((1 shl length) - 1) shl shift)) shr shift

private fun solarToInt(
    year: Int,
    month: Int,
    day: Int,
): Long {
    val m = (month + 9) % 12
    val y = year - m / 10
    return 365L * y + y / 4 - y / 100 + y / 400 + (m * 306 + 5) / 10 + (day - 1)
}

private fun solarFromInt(g: Long): LocalDate {
    var y = (10000 * g + 14780) / 3652425
    var ddd = g - (365 * y + y / 4 - y / 100 + y / 400)
    if (ddd < 0) {
        y--
        ddd = g - (365 * y + y / 4 - y / 100 + y / 400)
    }
    val mi = (100 * ddd + 52) / 3060
    val mm = (mi + 2) % 12 + 1
    y += (mi + 2) / 12
    val dd = ddd - (mi * 306 + 5) / 10 + 1
    return LocalDate(y.toInt(), mm.toInt(), dd.toInt())
}

/** Converts a Gregorian date to its lunar equivalent. Valid for years 1900..2099. */
public fun LocalDate.toLunarDate(): LunarDate {
    var index = year - SOLAR[0]
    val data = (year shl 9) or (month.number shl 5) or day
    if (SOLAR[index] > data) index--
    val solar11 = SOLAR[index]
    val y = getBits(solar11, 12, 9)
    val m = getBits(solar11, 4, 5)
    val d = getBits(solar11, 5, 0)
    var offset = solarToInt(year, month.number, day) - solarToInt(y, m, d)
    val days = LUNAR_MONTH_DAYS[index]
    val leap = getBits(days, 4, 13)
    val lunarYear = index + SOLAR[0]
    var lunarMonth = 1
    offset += 1
    for (i in 0 until 13) {
        val dm = if (getBits(days, 1, 12 - i) == 1) 30 else 29
        if (offset > dm) {
            lunarMonth++
            offset -= dm
        } else {
            break
        }
    }
    var resultMonth = lunarMonth
    var isLeap = false
    if (leap != 0 && lunarMonth > leap) {
        resultMonth = lunarMonth - 1
        if (lunarMonth == leap + 1) isLeap = true
    }
    return LunarDate(lunarYear, resultMonth, offset.toInt(), isLeap)
}

/** Converts a lunar date back to Gregorian. Valid for lunar years 1900..2099. */
public fun LunarDate.toLocalDate(): LocalDate {
    val days = LUNAR_MONTH_DAYS[year - LUNAR_MONTH_DAYS[0]]
    val leap = getBits(days, 4, 13)
    var loop = leap
    if (!isLeapMonth) {
        loop = if (month <= leap || leap == 0) month - 1 else month
    }
    var offset = 0
    for (i in 0 until loop) {
        offset += if (getBits(days, 1, 12 - i) == 1) 30 else 29
    }
    offset += day
    val solar11 = SOLAR[year - SOLAR[0]]
    val y = getBits(solar11, 12, 9)
    val m = getBits(solar11, 4, 5)
    val d = getBits(solar11, 5, 0)
    return solarFromInt(solarToInt(y, m, d) + offset - 1)
}
