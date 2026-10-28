package me.kozakov.orrery.holidays.rules

import kotlinx.datetime.DayOfWeek.WEDNESDAY
import kotlinx.datetime.LocalDate
import kotlinx.datetime.Month.AUGUST
import kotlinx.datetime.Month.DECEMBER
import kotlinx.datetime.Month.JANUARY
import kotlinx.datetime.Month.MARCH
import kotlinx.datetime.Month.MAY
import kotlinx.datetime.Month.NOVEMBER
import kotlinx.datetime.Month.OCTOBER
import kotlinx.datetime.Month.SEPTEMBER
import me.kozakov.orrery.holidays.HolidayCalendar
import me.kozakov.orrery.holidays.HolidayDate
import me.kozakov.orrery.holidays.HolidayDate.EasterOffset
import me.kozakov.orrery.holidays.HolidayDate.Fixed
import me.kozakov.orrery.holidays.HolidayDate.OneOff
import me.kozakov.orrery.holidays.HolidayDate.WeekdayRelative
import me.kozakov.orrery.holidays.HolidayRule
import me.kozakov.orrery.holidays.HolidayType

/*
 * German public holidays (gesetzliche Feiertage), from 1991. Federal law sets only
 * Tag der Deutschen Einheit; every other day comes from the 16 state holiday laws
 * (Feiertagsgesetze). There are no substitute days. Subdivisions: DE-BB ... DE-TH.
 *
 * Sources (checked 2026-10-27):
 * - Einigungsvertrag Art. 2 (3 October), https://www.gesetze-im-internet.de/einigvtr/art_2.html
 * - Bundesministerium des Innern, "Nationale Feiertage", https://www.bmi.bund.de
 * - State laws: BW FTG, BayFTG, BE FeiertG, BbgFTG, HB FeiertagsG, HH FeiertagsG, HFeiertagsG,
 *   MV FTG, NFeiertagsG, NW FeiertagsG, RP LFtG, SL FTG, SächsSFG, ST FeiertG, SH SFTG, ThürFtG
 *   (Reformation Day added in HB, HH, NI, SH from 2018; Women's Day in BE from 2019 and MV
 *   from 2023; World Children's Day in TH from 2019; Berlin one-off days 2020, 2025, 2028)
 * Assumption (Bavaria) and Corpus Christi (Saxony, Thuringia) apply only in some
 * municipalities and are typed Optional. Local days such as the Augsburg Peace Festival are
 * not included.
 */
private fun state(vararg codes: String): Set<String> = codes.mapTo(mutableSetOf()) { "DE-$it" }

private fun feiertag(
    id: String,
    en: String,
    de: String,
    date: HolidayDate,
    years: IntRange = 1991..FOREVER,
    subdivisions: Set<String>? = null,
    type: HolidayType = HolidayType.Public,
) = HolidayRule(id, names(en, "de" to de), date, type, years, subdivisions = subdivisions)

// One flat table of rules reads better than splitting it to satisfy LongMethod.
@Suppress("LongMethod")
internal fun germany(): HolidayCalendar =
    HolidayCalendar(
        country = "DE",
        rules =
            listOf(
                feiertag("de.new-years-day", "New Year's Day", "Neujahr", Fixed(JANUARY, 1)),
                feiertag(
                    "de.epiphany",
                    "Epiphany",
                    "Heilige Drei Könige",
                    Fixed(JANUARY, 6),
                    subdivisions = state("BW", "BY", "ST"),
                ),
                feiertag(
                    "de.womens-day",
                    "International Women's Day",
                    "Frauentag",
                    Fixed(MARCH, 8),
                    2019..FOREVER,
                    state("BE"),
                ),
                feiertag(
                    "de.womens-day",
                    "International Women's Day",
                    "Frauentag",
                    Fixed(MARCH, 8),
                    2023..FOREVER,
                    state("MV"),
                ),
                feiertag("de.good-friday", "Good Friday", "Karfreitag", EasterOffset(-2)),
                feiertag(
                    "de.easter-sunday",
                    "Easter Sunday",
                    "Ostersonntag",
                    EasterOffset(0),
                    subdivisions = state("BB"),
                ),
                feiertag("de.easter-monday", "Easter Monday", "Ostermontag", EasterOffset(1)),
                feiertag("de.labour-day", "Labour Day", "Tag der Arbeit", Fixed(MAY, 1)),
                feiertag(
                    "de.liberation-day-2020",
                    "75th anniversary of the end of World War II",
                    "75. Jahrestag der Befreiung vom Nationalsozialismus",
                    OneOff(LocalDate(2020, 5, 8)),
                    subdivisions = state("BE"),
                ),
                feiertag(
                    "de.liberation-day-2025",
                    "80th anniversary of the end of World War II",
                    "80. Jahrestag der Befreiung vom Nationalsozialismus",
                    OneOff(LocalDate(2025, 5, 8)),
                    subdivisions = state("BE"),
                ),
                feiertag("de.ascension-day", "Ascension Day", "Christi Himmelfahrt", EasterOffset(39)),
                feiertag(
                    "de.whit-sunday",
                    "Whit Sunday",
                    "Pfingstsonntag",
                    EasterOffset(49),
                    subdivisions = state("BB"),
                ),
                feiertag("de.whit-monday", "Whit Monday", "Pfingstmontag", EasterOffset(50)),
                feiertag(
                    "de.corpus-christi",
                    "Corpus Christi",
                    "Fronleichnam",
                    EasterOffset(60),
                    subdivisions = state("BW", "BY", "HE", "NW", "RP", "SL"),
                ),
                feiertag(
                    "de.corpus-christi",
                    "Corpus Christi",
                    "Fronleichnam",
                    EasterOffset(60),
                    subdivisions = state("SN", "TH"),
                    type = HolidayType.Optional,
                ),
                feiertag(
                    "de.uprising-1953",
                    "75th anniversary of the uprising of 17 June 1953",
                    "75. Jahrestag des Aufstandes vom 17. Juni 1953",
                    OneOff(LocalDate(2028, 6, 17)),
                    subdivisions = state("BE"),
                ),
                feiertag(
                    "de.assumption-day",
                    "Assumption Day",
                    "Mariä Himmelfahrt",
                    Fixed(AUGUST, 15),
                    subdivisions = state("SL"),
                ),
                feiertag(
                    "de.assumption-day",
                    "Assumption Day",
                    "Mariä Himmelfahrt",
                    Fixed(AUGUST, 15),
                    subdivisions = state("BY"),
                    type = HolidayType.Optional,
                ),
                feiertag(
                    "de.world-childrens-day",
                    "World Children's Day",
                    "Weltkindertag",
                    Fixed(SEPTEMBER, 20),
                    2019..FOREVER,
                    state("TH"),
                ),
                feiertag("de.german-unity-day", "German Unity Day", "Tag der Deutschen Einheit", Fixed(OCTOBER, 3)),
                feiertag("de.reformation-day", "Reformation Day", "Reformationstag", OneOff(LocalDate(2017, 10, 31))),
                feiertag(
                    "de.reformation-day",
                    "Reformation Day",
                    "Reformationstag",
                    Fixed(OCTOBER, 31),
                    subdivisions = state("BB", "MV", "SN", "ST", "TH"),
                ),
                feiertag(
                    "de.reformation-day",
                    "Reformation Day",
                    "Reformationstag",
                    Fixed(OCTOBER, 31),
                    2018..FOREVER,
                    state("HB", "HH", "NI", "SH"),
                ),
                feiertag(
                    "de.all-saints-day",
                    "All Saints' Day",
                    "Allerheiligen",
                    Fixed(NOVEMBER, 1),
                    subdivisions = state("BW", "BY", "NW", "RP", "SL"),
                ),
                // The Wednesday before 23 November; nationwide until 1994, then Saxony only.
                feiertag(
                    "de.repentance-day",
                    "Day of Repentance and Prayer",
                    "Buß- und Bettag",
                    WeekdayRelative(NOVEMBER, 23, WEDNESDAY, after = false),
                    1991..1994,
                ),
                feiertag(
                    "de.repentance-day",
                    "Day of Repentance and Prayer",
                    "Buß- und Bettag",
                    WeekdayRelative(NOVEMBER, 23, WEDNESDAY, after = false),
                    1995..FOREVER,
                    state("SN"),
                ),
                feiertag("de.christmas-day", "Christmas Day", "1. Weihnachtsfeiertag", Fixed(DECEMBER, 25)),
                feiertag("de.st-stephens-day", "St Stephen's Day", "2. Weihnachtsfeiertag", Fixed(DECEMBER, 26)),
            ),
    )
