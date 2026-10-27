package me.kozakov.orrery.holidays

import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.Month
import kotlinx.datetime.isoDayNumber
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import me.kozakov.orrery.calendars.hijri.HijriDate
import me.kozakov.orrery.calendars.hijri.toHijriDate
import me.kozakov.orrery.calendars.hijri.toLocalDate
import me.kozakov.orrery.calendars.persian.PersianDate
import me.kozakov.orrery.calendars.persian.toLocalDate

/**
 * Holidays of one country, computed from [rules]. Build your own (company days off,
 * observances) with the same [HolidayRule] types, or get a built-in one from [PublicHolidays].
 *
 * @property country ISO 3166-1 alpha-2 code, or any label for custom calendars.
 */
public class HolidayCalendar(
    public val country: String,
    public val rules: List<HolidayRule>,
) {
    /** First index of each rule id; a rule split by [HolidayRule.years] keeps one id. */
    private val ruleOrder: Map<String, Int> =
        buildMap { rules.forEachIndexed { index, rule -> if (rule.id !in this) put(rule.id, index) } }

    /**
     * Every holiday in [year] for the whole country plus [subdivision] (an ISO 3166-2 code such
     * as `"DE-BY"`), including substitute days, sorted by date and then rule order. Names are
     * English unless [language] names a language present in the rules' [HolidayRule.names].
     */
    public fun holidaysIn(
        year: Int,
        subdivision: String? = null,
        language: String? = null,
    ): List<PublicHoliday> {
        // Neighbouring years are included because a substitute can cross the year boundary
        // (US: New Year's Day on a Saturday is observed on 31 December).
        val actual = (year - 1..year + 1).flatMap { occurrencesIn(it, subdivision, language) }.distinctBy { it.second }
        val taken = actual.mapTo(mutableSetOf()) { it.second.date }
        // One substitute per weekend day: two holidays on the same Sunday give one day off.
        val substituted = mutableSetOf<LocalDate>()
        val substitutes = mutableListOf<PublicHoliday>()
        actual.sortedBy { it.second.date }.forEach { (rule, holiday) ->
            if (holiday.date in substituted) return@forEach
            val substitute = substituteDate(holiday.date, rule.observance, taken) ?: return@forEach
            taken += substitute
            substituted += holiday.date
            substitutes += holiday.copy(date = substitute, isSubstitute = true)
        }
        return (actual.map { it.second } + substitutes)
            .filter { it.date.year == year }
            .sortedWith(compareBy({ it.date }, { it.isSubstitute }, { ruleOrder[it.ruleId] }))
    }

    private fun occurrencesIn(
        year: Int,
        subdivision: String?,
        language: String?,
    ): List<Pair<HolidayRule, PublicHoliday>> =
        rules
            .filter { rule -> year in rule.years && rule.appliesTo(subdivision) }
            .flatMap { rule ->
                rule.date.datesIn(year).map { date ->
                    rule to
                        PublicHoliday(
                            date = date,
                            ruleId = rule.id,
                            name = rule.name(language),
                            type = rule.type,
                            isEstimated = rule.date is HolidayDate.HijriFixed,
                        )
                }
            }

    /** Holidays on [date] (usually zero or one). */
    public fun holidaysOn(
        date: LocalDate,
        subdivision: String? = null,
        language: String? = null,
    ): List<PublicHoliday> = holidaysIn(date.year, subdivision, language).filter { it.date == date }
}

private fun HolidayRule.appliesTo(subdivision: String?): Boolean = subdivisions == null || subdivision in subdivisions

private fun substituteDate(
    date: LocalDate,
    observance: Observance,
    taken: Set<LocalDate>,
): LocalDate? {
    val weekend = date.dayOfWeek == DayOfWeek.SATURDAY || date.dayOfWeek == DayOfWeek.SUNDAY
    if (!weekend) return null
    return when (observance) {
        Observance.None -> {
            null
        }

        Observance.NearestWeekday -> {
            if (date.dayOfWeek == DayOfWeek.SATURDAY) {
                date.minus(1, DateTimeUnit.DAY)
            } else {
                date.plus(1, DateTimeUnit.DAY)
            }
        }

        Observance.SundayToMonday -> {
            if (date.dayOfWeek == DayOfWeek.SUNDAY) date.plus(1, DateTimeUnit.DAY) else null
        }

        Observance.NextWorkingDay -> {
            var day = date.plus(1, DateTimeUnit.DAY)
            while (day.dayOfWeek == DayOfWeek.SATURDAY || day.dayOfWeek == DayOfWeek.SUNDAY || day in taken) {
                day = day.plus(1, DateTimeUnit.DAY)
            }
            day
        }
    }
}

/** Dates this rule produces in Gregorian [year] (one, or zero/two for Hijri rules). */
internal fun HolidayDate.datesIn(year: Int): List<LocalDate> =
    when (this) {
        is HolidayDate.Fixed -> {
            // 29 February only exists in leap years.
            listOfNotNull(runCatching { LocalDate(year, month, day) }.getOrNull())
        }

        is HolidayDate.NthWeekday -> {
            listOfNotNull(nthWeekday(year, month, dayOfWeek, n))
        }

        is HolidayDate.WeekdayRelative -> {
            listOf(weekdayRelative(LocalDate(year, month, day), dayOfWeek, after))
        }

        is HolidayDate.EasterOffset -> {
            listOf(easterSunday(year, church).plus(days, DateTimeUnit.DAY))
        }

        is HolidayDate.OneOff -> {
            if (date.year == year) listOf(date) else emptyList()
        }

        is HolidayDate.PersianFixed -> {
            // Farvardin 1 falls in March, so Gregorian [year] holds the end of Persian year
            // year - 622 (January to about 20 March) and the start of year - 621.
            listOf(year - 622, year - 621)
                .mapNotNull { persianYear ->
                    runCatching { PersianDate(persianYear, month, day).toLocalDate() }.getOrNull()
                }.filter { it.year == year }
        }

        is HolidayDate.HijriFixed -> {
            hijriDatesIn(year, month, day)
        }
    }

private fun hijriDatesIn(
    year: Int,
    month: Int,
    day: Int,
): List<LocalDate> {
    // Outside the Umm al-Qura range the rule produces nothing rather than a tabular guess.
    // A Gregorian year can touch three Hijri years (2008: 1 Muharram on 10 January and 29 December).
    val hijriYears =
        runCatching {
            LocalDate(year, Month.JANUARY, 1).toHijriDate().year..LocalDate(year, Month.DECEMBER, 31).toHijriDate().year
        }.getOrNull() ?: return emptyList()
    val dates =
        hijriYears.mapNotNull { hijriYear ->
            runCatching { HijriDate(hijriYear, month, day).toLocalDate() }.getOrNull()
        }
    return dates.filter { it.year == year }
}

internal fun nthWeekday(
    year: Int,
    month: Month,
    dayOfWeek: DayOfWeek,
    n: Int,
): LocalDate? {
    require(n in 1..5 || n == -1) { "n must be 1..5 or -1, was $n" }
    return if (n > 0) {
        val first = LocalDate(year, month, 1)
        val offset = (dayOfWeek.isoDayNumber - first.dayOfWeek.isoDayNumber).mod(7)
        // A 5th occurrence does not exist in every month; never spill into the next one.
        first.plus(offset + (n - 1) * 7, DateTimeUnit.DAY).takeIf { it.month == month }
    } else {
        val last = LocalDate(year, month, 1).plus(1, DateTimeUnit.MONTH).minus(1, DateTimeUnit.DAY)
        last.minus((last.dayOfWeek.isoDayNumber - dayOfWeek.isoDayNumber).mod(7), DateTimeUnit.DAY)
    }
}

private fun weekdayRelative(
    anchor: LocalDate,
    dayOfWeek: DayOfWeek,
    after: Boolean,
): LocalDate =
    if (after) {
        anchor.plus((dayOfWeek.isoDayNumber - anchor.dayOfWeek.isoDayNumber - 1).mod(7) + 1, DateTimeUnit.DAY)
    } else {
        anchor.minus((anchor.dayOfWeek.isoDayNumber - dayOfWeek.isoDayNumber - 1).mod(7) + 1, DateTimeUnit.DAY)
    }

/** Easter Sunday of [year] as a Gregorian date. */
public fun easterSunday(
    year: Int,
    church: Church = Church.Western,
): LocalDate =
    when (church) {
        Church.Western -> {
            // Anonymous Gregorian algorithm (Meeus/Jones/Butcher).
            val a = year % 19
            val b = year / 100
            val c = year % 100
            val d = b / 4
            val e = b % 4
            val f = (b + 8) / 25
            val g = (b - f + 1) / 3
            val h = (19 * a + b - d - g + 15) % 30
            val i = c / 4
            val k = c % 4
            val l = (32 + 2 * e + 2 * i - h - k) % 7
            val m = (a + 11 * h + 22 * l) / 451
            val month = (h + l - 7 * m + 114) / 31
            val day = (h + l - 7 * m + 114) % 31 + 1
            LocalDate(year, month, day)
        }

        Church.Orthodox -> {
            // Meeus' Julian algorithm, then the Julian-to-Gregorian offset for the year.
            val a = year % 4
            val b = year % 7
            val c = year % 19
            val d = (19 * c + 15) % 30
            val e = (2 * a + 4 * b - d + 34) % 7
            val month = (d + e + 114) / 31
            val day = (d + e + 114) % 31 + 1
            val offset = year / 100 - year / 400 - 2
            LocalDate(year, month, 1).plus(day - 1 + offset, DateTimeUnit.DAY)
        }
    }
