package me.kozakov.orrery.holidays.rules

import kotlinx.datetime.Month.AUGUST
import kotlinx.datetime.Month.DECEMBER
import kotlinx.datetime.Month.JANUARY
import kotlinx.datetime.Month.JULY
import kotlinx.datetime.Month.JUNE
import kotlinx.datetime.Month.MARCH
import kotlinx.datetime.Month.MAY
import kotlinx.datetime.Month.NOVEMBER
import kotlinx.datetime.Month.OCTOBER
import me.kozakov.orrery.holidays.Church
import me.kozakov.orrery.holidays.HolidayCalendar
import me.kozakov.orrery.holidays.HolidayDate
import me.kozakov.orrery.holidays.HolidayDate.EasterOffset
import me.kozakov.orrery.holidays.HolidayDate.Fixed
import me.kozakov.orrery.holidays.HolidayRule
import me.kozakov.orrery.holidays.Observance

/*
 * Ukrainian public holidays (святкові та неробочі дні), from 1991.
 *
 * A holiday on a Saturday or Sunday gives the next working day off (Labour Code Art. 67,
 * from 1999). Under martial law, in force since 24 February 2022, holidays are working days
 * and no days off are moved (Law No. 2136-IX, Art. 6); the dates are still listed here as
 * public holidays, but without substitute days from 2022 (the 1 January 2022 substitute came
 * before martial law and is kept). Cabinet transfers of working days are not modelled.
 *
 * Sources (checked 2026-10-27):
 * - Labour Code of Ukraine, Art. 73, https://zakon.rada.gov.ua/laws/show/322-08#n454
 * - Law No. 2136-IX (2022), https://zakon.rada.gov.ua/laws/show/2136-20
 * - Law No. 3006-IX (2023): Christmas only on 25 December, Day of Remembrance and Victory on
 *   8 May, Statehood Day on 15 July and Defenders' Day on 1 October from 2024,
 *   https://zakon.rada.gov.ua/laws/show/3006-20
 * - Law No. 1622-VIII (2016): Christmas on 25 December from 2017; Labour Day only 1 May from 2018
 * - Law No. 1610-IX (2021) and the Defenders' Day law of 2014 (14 October from 2015)
 */
private const val OBSERVED_FROM = 1999
private const val MARTIAL_LAW = 2022

/**
 * The rule for [years], with substitute days in [OBSERVED_FROM]..[lastObserved] and none
 * outside.
 */
private fun holiday(
    id: String,
    en: String,
    uk: String,
    date: HolidayDate,
    years: IntRange = 1991..FOREVER,
    lastObserved: Int = MARTIAL_LAW - 1,
): List<HolidayRule> {
    val names = names(en, "uk" to uk)
    val spans =
        listOf(
            (years.first..minOf(years.last, OBSERVED_FROM - 1)) to Observance.None,
            (maxOf(years.first, OBSERVED_FROM)..minOf(years.last, lastObserved)) to Observance.NextWorkingDay,
            (maxOf(years.first, lastObserved + 1)..years.last) to Observance.None,
        )
    return spans
        .filterNot { (span, _) -> span.isEmpty() }
        .map { (span, observance) -> HolidayRule(id, names, date, years = span, observance = observance) }
}

private val ORTHODOX_EASTER = EasterOffset(0, Church.Orthodox)
private val ORTHODOX_TRINITY = EasterOffset(49, Church.Orthodox)

// One flat table of rules reads better than splitting it to satisfy LongMethod.
@Suppress("LongMethod")
internal fun ukraine(): HolidayCalendar =
    HolidayCalendar(
        country = "UA",
        rules =
            listOf(
                holiday(
                    "ua.new-years-day",
                    "New Year's Day",
                    "Новий рік",
                    Fixed(JANUARY, 1),
                    lastObserved = MARTIAL_LAW,
                ),
                holiday(
                    "ua.orthodox-christmas",
                    "Christmas (Julian calendar)",
                    "Різдво Христове (за юліанським календарем)",
                    Fixed(JANUARY, 7),
                    1991..2023,
                    lastObserved = MARTIAL_LAW,
                ),
                holiday(
                    "ua.womens-day",
                    "International Women's Day",
                    "Міжнародний жіночий день",
                    Fixed(MARCH, 8),
                    lastObserved = MARTIAL_LAW,
                ),
                holiday("ua.easter", "Easter", "Великдень (Пасха)", ORTHODOX_EASTER),
                holiday("ua.trinity", "Trinity Sunday", "Трійця", ORTHODOX_TRINITY),
                holiday("ua.labour-day", "Labour Day", "День праці", Fixed(MAY, 1)),
                holiday(
                    "ua.labour-day-2",
                    "Labour Day (second day)",
                    "День праці (другий день)",
                    Fixed(MAY, 2),
                    1991..2017,
                ),
                holiday(
                    "ua.remembrance-and-victory-day",
                    "Day of Remembrance and Victory over Nazism",
                    "День памʼяті та перемоги над нацизмом у Другій світовій війні",
                    Fixed(MAY, 8),
                    2024..FOREVER,
                ),
                holiday(
                    "ua.victory-day",
                    "Victory Day",
                    "День перемоги над нацизмом у Другій світовій війні",
                    Fixed(MAY, 9),
                    1991..2023,
                ),
                holiday(
                    "ua.constitution-day",
                    "Constitution Day",
                    "День Конституції України",
                    Fixed(JUNE, 28),
                    1997..FOREVER,
                ),
                holiday(
                    "ua.statehood-day",
                    "Statehood Day",
                    "День Української Державності",
                    Fixed(JULY, 15),
                    2024..FOREVER,
                ),
                holiday(
                    "ua.statehood-day",
                    "Statehood Day",
                    "День Української Державності",
                    Fixed(JULY, 28),
                    2022..2023,
                ),
                holiday(
                    "ua.independence-day",
                    "Independence Day",
                    "День незалежності України",
                    Fixed(AUGUST, 24),
                    1992..FOREVER,
                ),
                holiday(
                    "ua.defenders-day",
                    "Defenders' Day",
                    "День захисників і захисниць України",
                    Fixed(OCTOBER, 1),
                    2023..FOREVER,
                ),
                holiday(
                    "ua.defenders-day",
                    "Defenders' Day",
                    "День захисників і захисниць України",
                    Fixed(OCTOBER, 14),
                    2015..2022,
                ),
                holiday(
                    "ua.october-revolution",
                    "October Revolution Day",
                    "Річниця Великої Жовтневої соціалістичної революції",
                    Fixed(NOVEMBER, 7),
                    1991..1999,
                ),
                holiday(
                    "ua.october-revolution-2",
                    "October Revolution Day (second day)",
                    "Річниця Великої Жовтневої соціалістичної революції (другий день)",
                    Fixed(NOVEMBER, 8),
                    1991..1999,
                ),
                holiday("ua.christmas-day", "Christmas Day", "Різдво Христове", Fixed(DECEMBER, 25), 2017..FOREVER),
            ).flatten(),
    )
