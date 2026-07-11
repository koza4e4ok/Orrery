package dev.koza4e4ok.material.calendar.lunar

import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.Month
import kotlinx.datetime.isoDayNumber
import kotlinx.datetime.number
import kotlinx.datetime.plus

internal fun traditionalFestival(
    lunar: LunarDate,
    date: LocalDate,
    variant: ChineseVariant,
): String? {
    val s = strings(variant)
    if (lunar.month == 12) {
        val next = date.plus(1, DateTimeUnit.DAY).toLunarDate()
        if (next.month == 1 && next.day == 1) return s.eve
    }
    // Like the original, the lookup ignores the leap flag (a leap 5/5 also reads 端午).
    return s.traditionalFestivals[lunar.month to lunar.day]
}

internal fun gregorianFestival(
    date: LocalDate,
    variant: ChineseVariant,
): String? = strings(variant).gregorianFestivals[date.month.number to date.day]

internal fun specialFestival(
    date: LocalDate,
    variant: ChineseVariant,
): String? {
    val s = strings(variant).specialFestivals
    return when {
        date.month == Month.MAY && date == nthWeekday(date.year, Month.MAY, DayOfWeek.SUNDAY, 2) -> s[0]
        date.month == Month.JUNE && date == nthWeekday(date.year, Month.JUNE, DayOfWeek.SUNDAY, 3) -> s[1]
        date.month == Month.NOVEMBER && date == nthWeekday(date.year, Month.NOVEMBER, DayOfWeek.THURSDAY, 4) -> s[2]
        else -> null
    }
}

internal fun nthWeekday(
    year: Int,
    month: Month,
    dayOfWeek: DayOfWeek,
    n: Int,
): LocalDate {
    val first = LocalDate(year, month, 1)
    val offset = (dayOfWeek.isoDayNumber - first.dayOfWeek.isoDayNumber + 7) % 7
    return first.plus(offset + (n - 1) * 7, DateTimeUnit.DAY)
}
