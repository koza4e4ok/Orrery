package me.kozakov.orrery.holidays.rules

import kotlinx.datetime.DayOfWeek.MONDAY
import kotlinx.datetime.LocalDate
import kotlinx.datetime.Month.AUGUST
import kotlinx.datetime.Month.DECEMBER
import kotlinx.datetime.Month.JANUARY
import kotlinx.datetime.Month.JULY
import kotlinx.datetime.Month.MARCH
import kotlinx.datetime.Month.MAY
import kotlinx.datetime.Month.NOVEMBER
import me.kozakov.orrery.holidays.HolidayCalendar
import me.kozakov.orrery.holidays.HolidayDate
import me.kozakov.orrery.holidays.HolidayDate.EasterOffset
import me.kozakov.orrery.holidays.HolidayDate.Fixed
import me.kozakov.orrery.holidays.HolidayDate.NthWeekday
import me.kozakov.orrery.holidays.HolidayDate.OneOff
import me.kozakov.orrery.holidays.HolidayRule
import me.kozakov.orrery.holidays.HolidayType
import me.kozakov.orrery.holidays.Observance

/*
 * United Kingdom bank holidays, from 1978 (Early May bank holiday introduced). Subdivisions:
 * GB-ENG, GB-WLS, GB-SCT, GB-NIR. Fixed-date holidays on a weekend get the next free weekday.
 *
 * Sources (checked 2026-10-27):
 * - Banking and Financial Dealings Act 1971, Schedule 1,
 *   https://www.legislation.gov.uk/ukpga/1971/80/schedule/1
 * - St Andrew's Day Bank Holiday (Scotland) Act 2007, https://www.legislation.gov.uk/asp/2007/2
 * - GOV.UK bank holidays, https://www.gov.uk/bank-holidays
 * - Royal proclamations moving or adding days in 1995, 1999, 2002, 2011, 2012, 2020, 2022, 2023
 * - Scotland World Cup bank holiday, https://www.gov.scot/news/world-cup-bank-holiday-confirmed/
 */
private val ENGLAND_WALES_NI = setOf("GB-ENG", "GB-WLS", "GB-NIR")
private val SCOTLAND = setOf("GB-SCT")
private val NORTHERN_IRELAND = setOf("GB-NIR")
private val ALWAYS = Int.MIN_VALUE..FOREVER

private fun bank(
    id: String,
    name: String,
    date: HolidayDate,
    years: IntRange = ALWAYS,
    subdivisions: Set<String>? = null,
): HolidayRule {
    val observance = if (date is Fixed) Observance.NextWorkingDay else Observance.None
    return HolidayRule(id, names(name), date, HolidayType.Bank, years, observance, subdivisions)
}

private fun oneOff(
    id: String,
    name: String,
    date: LocalDate,
    subdivisions: Set<String>? = null,
) = bank(id, name, OneOff(date), subdivisions = subdivisions)

private val FIRST_MONDAY_OF_MAY = NthWeekday(MAY, MONDAY, 1)
private val LAST_MONDAY_OF_MAY = NthWeekday(MAY, MONDAY, -1)

internal fun unitedKingdom(): HolidayCalendar =
    HolidayCalendar(
        country = "GB",
        rules =
            listOf(
                bank("gb.new-years-day", "New Year's Day", Fixed(JANUARY, 1), 1975..FOREVER),
                bank("gb.new-years-day", "New Year's Day", Fixed(JANUARY, 1), Int.MIN_VALUE..1974, SCOTLAND),
                bank("gb.new-year-holiday", "2nd January", Fixed(JANUARY, 2), subdivisions = SCOTLAND),
                bank("gb.st-patricks-day", "St Patrick's Day", Fixed(MARCH, 17), subdivisions = NORTHERN_IRELAND),
                bank("gb.good-friday", "Good Friday", EasterOffset(-2)),
                bank("gb.easter-monday", "Easter Monday", EasterOffset(1), subdivisions = ENGLAND_WALES_NI),
                // Moved to 8 May for the VE Day anniversaries in 1995 and 2020.
                bank("gb.early-may", "Early May bank holiday", FIRST_MONDAY_OF_MAY, 1978..1994),
                bank("gb.early-may", "Early May bank holiday", OneOff(LocalDate(1995, 5, 8))),
                bank("gb.early-may", "Early May bank holiday", FIRST_MONDAY_OF_MAY, 1996..2019),
                bank("gb.early-may", "Early May bank holiday", OneOff(LocalDate(2020, 5, 8))),
                bank("gb.early-may", "Early May bank holiday", FIRST_MONDAY_OF_MAY, 2021..FOREVER),
                // Moved for the Golden, Diamond and Platinum Jubilees.
                bank("gb.spring", "Spring bank holiday", LAST_MONDAY_OF_MAY, 1971..2001),
                bank("gb.spring", "Spring bank holiday", OneOff(LocalDate(2002, 6, 4))),
                bank("gb.spring", "Spring bank holiday", LAST_MONDAY_OF_MAY, 2003..2011),
                bank("gb.spring", "Spring bank holiday", OneOff(LocalDate(2012, 6, 4))),
                bank("gb.spring", "Spring bank holiday", LAST_MONDAY_OF_MAY, 2013..2021),
                bank("gb.spring", "Spring bank holiday", OneOff(LocalDate(2022, 6, 2))),
                bank("gb.spring", "Spring bank holiday", LAST_MONDAY_OF_MAY, 2023..FOREVER),
                bank("gb.battle-of-the-boyne", "Battle of the Boyne", Fixed(JULY, 12), subdivisions = NORTHERN_IRELAND),
                bank("gb.summer", "Summer bank holiday", NthWeekday(AUGUST, MONDAY, 1), subdivisions = SCOTLAND),
                bank(
                    "gb.summer",
                    "Summer bank holiday",
                    NthWeekday(AUGUST, MONDAY, -1),
                    1971..FOREVER,
                    ENGLAND_WALES_NI,
                ),
                bank("gb.st-andrews-day", "St Andrew's Day", Fixed(NOVEMBER, 30), 2007..FOREVER, SCOTLAND),
                bank("gb.christmas-day", "Christmas Day", Fixed(DECEMBER, 25)),
                bank("gb.boxing-day", "Boxing Day", Fixed(DECEMBER, 26)),
                oneOff("gb.silver-jubilee", "Silver Jubilee of Elizabeth II", LocalDate(1977, 6, 7)),
                oneOff("gb.royal-wedding-1981", "Wedding of Charles and Diana", LocalDate(1981, 7, 29)),
                oneOff("gb.millennium", "Millennium celebrations", LocalDate(1999, 12, 31)),
                oneOff("gb.golden-jubilee", "Golden Jubilee of Elizabeth II", LocalDate(2002, 6, 3)),
                oneOff("gb.royal-wedding-2011", "Wedding of William and Catherine", LocalDate(2011, 4, 29)),
                oneOff("gb.diamond-jubilee", "Diamond Jubilee of Elizabeth II", LocalDate(2012, 6, 5)),
                oneOff("gb.platinum-jubilee", "Platinum Jubilee of Elizabeth II", LocalDate(2022, 6, 3)),
                oneOff("gb.state-funeral-2022", "State Funeral of Queen Elizabeth II", LocalDate(2022, 9, 19)),
                oneOff("gb.coronation-2023", "Coronation of Charles III", LocalDate(2023, 5, 8)),
                oneOff("gb.world-cup-2026", "World Cup bank holiday", LocalDate(2026, 6, 15), SCOTLAND),
            ),
    )
