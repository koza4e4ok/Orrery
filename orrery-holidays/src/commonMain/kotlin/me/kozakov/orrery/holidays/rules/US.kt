package me.kozakov.orrery.holidays.rules

import kotlinx.datetime.DayOfWeek.MONDAY
import kotlinx.datetime.DayOfWeek.THURSDAY
import kotlinx.datetime.Month
import kotlinx.datetime.Month.DECEMBER
import kotlinx.datetime.Month.FEBRUARY
import kotlinx.datetime.Month.JANUARY
import kotlinx.datetime.Month.JULY
import kotlinx.datetime.Month.JUNE
import kotlinx.datetime.Month.MAY
import kotlinx.datetime.Month.NOVEMBER
import kotlinx.datetime.Month.OCTOBER
import kotlinx.datetime.Month.SEPTEMBER
import me.kozakov.orrery.holidays.HolidayCalendar
import me.kozakov.orrery.holidays.HolidayDate
import me.kozakov.orrery.holidays.HolidayDate.Fixed
import me.kozakov.orrery.holidays.HolidayDate.NthWeekday
import me.kozakov.orrery.holidays.HolidayRule
import me.kozakov.orrery.holidays.Observance

/*
 * United States federal holidays, from 1971 (Uniform Monday Holiday Act in force).
 * Holidays on a Saturday are observed on Friday, on a Sunday on Monday.
 *
 * Sources (checked 2026-10-27):
 * - 5 U.S.C. 6103, https://www.law.cornell.edu/uscode/text/5/6103
 * - Executive Order 11582 (1971), observance of holidays falling on a weekend
 * - OPM federal holidays, https://www.opm.gov/policy-data-oversight/pay-leave/federal-holidays/
 * - Juneteenth National Independence Day Act, Pub. L. 117-17 (2021)
 * - Veterans Day back on 11 November from 1978, Pub. L. 94-97
 * Inauguration Day (Washington, D.C. area only) is not included.
 */
private const val SINCE = 1971

private fun federal(
    id: String,
    name: String,
    date: HolidayDate,
    years: IntRange = SINCE..FOREVER,
): HolidayRule {
    // Only fixed-date holidays can fall on a weekend; Monday/Thursday holidays never move.
    val observance = if (date is Fixed) Observance.NearestWeekday else Observance.None
    return HolidayRule(id, names(name), date, years = years, observance = observance)
}

private fun monday(
    month: Month,
    n: Int,
) = NthWeekday(month, MONDAY, n)

internal fun unitedStates(): HolidayCalendar =
    HolidayCalendar(
        country = "US",
        rules =
            listOf(
                federal("us.new-years-day", "New Year's Day", Fixed(JANUARY, 1)),
                federal(
                    "us.martin-luther-king-day",
                    "Birthday of Martin Luther King, Jr.",
                    monday(JANUARY, 3),
                    1986..FOREVER,
                ),
                federal("us.washingtons-birthday", "Washington's Birthday", monday(FEBRUARY, 3)),
                federal("us.memorial-day", "Memorial Day", monday(MAY, -1)),
                federal("us.juneteenth", "Juneteenth National Independence Day", Fixed(JUNE, 19), 2021..FOREVER),
                federal("us.independence-day", "Independence Day", Fixed(JULY, 4)),
                federal("us.labor-day", "Labor Day", monday(SEPTEMBER, 1)),
                federal("us.columbus-day", "Columbus Day", monday(OCTOBER, 2)),
                federal("us.veterans-day", "Veterans Day", monday(OCTOBER, 4), SINCE..1977),
                federal("us.veterans-day", "Veterans Day", Fixed(NOVEMBER, 11), 1978..FOREVER),
                federal("us.thanksgiving-day", "Thanksgiving Day", NthWeekday(NOVEMBER, THURSDAY, 4)),
                federal("us.christmas-day", "Christmas Day", Fixed(DECEMBER, 25)),
            ),
    )
