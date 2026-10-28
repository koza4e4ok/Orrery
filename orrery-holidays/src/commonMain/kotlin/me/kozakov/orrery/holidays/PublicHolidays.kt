package me.kozakov.orrery.holidays

import me.kozakov.orrery.holidays.rules.germany
import me.kozakov.orrery.holidays.rules.ukraine
import me.kozakov.orrery.holidays.rules.unitedKingdom
import me.kozakov.orrery.holidays.rules.unitedStates

/**
 * Built-in holiday calendars, written from each country's statutes and official government
 * pages (sources are listed in the rule files). Rules are pure Kotlin: no network and no
 * resource files.
 *
 * - `US`: federal holidays (5 U.S.C. 6103), Saturday observed on Friday and Sunday on Monday.
 * - `GB`: bank holidays with subdivisions `GB-ENG`, `GB-WLS`, `GB-SCT` and `GB-NIR`;
 *   holidays on a weekend get a substitute day.
 * - `DE`: nationwide holidays plus the 16 states (`DE-BW` ... `DE-TH`). Days that are only
 *   holidays in mainly Catholic municipalities (Assumption in Bavaria, Corpus Christi in
 *   Saxony and Thuringia) have type [HolidayType.Optional].
 * - `UA`: public holidays under Art. 73 of the Labour Code. Under martial law (since
 *   24 February 2022) they are still listed as [HolidayType.Public] but are working days and
 *   get no substitute days.
 */
public object PublicHolidays {
    private val calendars: Map<String, Lazy<HolidayCalendar>> =
        mapOf(
            "DE" to lazy { germany() },
            "GB" to lazy { unitedKingdom() },
            "UA" to lazy { ukraine() },
            "US" to lazy { unitedStates() },
        )

    /** ISO 3166-1 alpha-2 codes with a built-in calendar. */
    public val supportedCountries: Set<String> = calendars.keys

    /** The built-in calendar for [isoCode] (ISO 3166-1 alpha-2, any case), or null. */
    public fun forCountry(isoCode: String): HolidayCalendar? = calendars[isoCode.uppercase()]?.value
}
