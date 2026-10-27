// The Easter tables keep ten dates per line.
@file:Suppress("ktlint:standard:argument-list-wrapping")

package me.kozakov.orrery.holidays

import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.Month
import me.kozakov.orrery.calendars.hijri.toHijriDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class HolidayRuleTest {
    @Test
    fun westernAndOrthodoxEasterMatchTable2000To2050() {
        for (year in 2000..2050) {
            assertEquals(LocalDate.parse("$year-${WESTERN[year - 2000]}"), easterSunday(year), "Western $year")
            assertEquals(
                LocalDate.parse("$year-${ORTHODOX[year - 2000]}"),
                easterSunday(year, Church.Orthodox),
                "Orthodox $year",
            )
        }
    }

    @Test
    fun nthAndLastWeekday() {
        assertEquals(LocalDate(2026, 11, 26), nthWeekday(2026, Month.NOVEMBER, DayOfWeek.THURSDAY, 4))
        assertEquals(LocalDate(2026, 5, 25), nthWeekday(2026, Month.MAY, DayOfWeek.MONDAY, -1))
        assertEquals(LocalDate(2026, 6, 1), nthWeekday(2026, Month.JUNE, DayOfWeek.MONDAY, 1))
        assertFailsWith<IllegalArgumentException> { nthWeekday(2026, Month.JUNE, DayOfWeek.MONDAY, 0) }
    }

    @Test
    fun fifthWeekdayStaysInItsMonth() {
        // June 2026 has five Mondays, February 2026 only four (2, 9, 16, 23).
        val fifthMondayOfJune = HolidayDate.NthWeekday(Month.JUNE, DayOfWeek.MONDAY, 5)
        assertEquals(listOf(LocalDate(2026, 6, 29)), fifthMondayOfJune.datesIn(2026))
        val fifthMondayOfFebruary = HolidayDate.NthWeekday(Month.FEBRUARY, DayOfWeek.MONDAY, 5)
        assertEquals(emptyList(), fifthMondayOfFebruary.datesIn(2026))
        assertEquals(null, nthWeekday(2026, Month.FEBRUARY, DayOfWeek.MONDAY, 5))
    }

    @Test
    fun weekdayRelativeIsStrictlyBeforeOrAfter() {
        val repentance = HolidayDate.WeekdayRelative(Month.NOVEMBER, 23, DayOfWeek.WEDNESDAY, after = false)
        assertEquals(listOf(LocalDate(2026, 11, 18)), repentance.datesIn(2026))
        assertEquals(listOf(LocalDate(2025, 11, 19)), repentance.datesIn(2025))
        // 5 January 2026 is a Monday: "after" skips the anchor itself.
        assertEquals(
            listOf(LocalDate(2026, 1, 12)),
            HolidayDate.WeekdayRelative(Month.JANUARY, 5, DayOfWeek.MONDAY, true).datesIn(2026),
        )
        assertEquals(
            listOf(LocalDate(2026, 1, 5)),
            HolidayDate.WeekdayRelative(Month.JANUARY, 1, DayOfWeek.MONDAY, true).datesIn(2026),
        )
    }

    @Test
    fun fixedLeapDayOnlyInLeapYears() {
        val leapDay = HolidayDate.Fixed(Month.FEBRUARY, 29)
        assertEquals(listOf(LocalDate(2028, 2, 29)), leapDay.datesIn(2028))
        assertEquals(emptyList(), leapDay.datesIn(2027))
    }

    @Test
    fun oneOffOnlyInItsYear() {
        val day = HolidayDate.OneOff(LocalDate(2023, 5, 8))
        assertEquals(listOf(LocalDate(2023, 5, 8)), day.datesIn(2023))
        assertEquals(emptyList(), day.datesIn(2024))
    }

    @Test
    fun hijriAndPersianRules() {
        assertEquals(listOf(LocalDate(2026, 3, 20)), HolidayDate.HijriFixed(10, 1).datesIn(2026))
        assertEquals(listOf(LocalDate(2026, 3, 21)), HolidayDate.PersianFixed(1, 1).datesIn(2026))
        val newYear = HolidayDate.HijriFixed(1, 1)
        val perYear = (2000..2050).associateWith { newYear.datesIn(it) }
        perYear.values.flatten().forEach { date ->
            val hijri = date.toHijriDate()
            assertEquals(1 to 1, hijri.month to hijri.day, "$date")
        }
        assertTrue(perYear.values.any { it.size == 2 }, "some Gregorian year holds two Hijri new years")
        // Outside the Umm al-Qura range the rule produces nothing.
        assertEquals(emptyList(), newYear.datesIn(1800))
    }

    @Test
    fun persianRulesCoverJanuaryToMarch() {
        // 22 Bahman 1404 and 29 Esfand 1404 fall before Nowruz 1405 (21 March 2026).
        assertEquals(listOf(LocalDate(2026, 2, 11)), HolidayDate.PersianFixed(11, 22).datesIn(2026))
        assertEquals(listOf(LocalDate(1979, 2, 11)), HolidayDate.PersianFixed(11, 22).datesIn(1979))
        assertEquals(listOf(LocalDate(2026, 3, 20)), HolidayDate.PersianFixed(12, 29).datesIn(2026))
        // 30 Esfand only exists in leap years: 1403 is leap, 1404 is not.
        assertEquals(listOf(LocalDate(2025, 3, 20)), HolidayDate.PersianFixed(12, 30).datesIn(2025))
        assertEquals(emptyList(), HolidayDate.PersianFixed(12, 30).datesIn(2026))
        // Late in the year: 10 Dey 1405 is 31 December 2026.
        assertEquals(listOf(LocalDate(2026, 12, 31)), HolidayDate.PersianFixed(10, 10).datesIn(2026))
        val calendar =
            HolidayCalendar(
                "XX",
                listOf(HolidayRule("xx.revolution", mapOf("en" to "Revolution Day"), HolidayDate.PersianFixed(11, 22))),
            )
        assertEquals(LocalDate(2026, 2, 11), calendar.holidaysIn(2026).single().date)
    }

    @Test
    fun hijriHolidaysAreEstimated() {
        val calendar =
            HolidayCalendar(
                "XX",
                listOf(HolidayRule("xx.eid", mapOf("en" to "Eid al-Fitr"), HolidayDate.HijriFixed(10, 1))),
            )
        val eid = calendar.holidaysIn(2026).single()
        assertEquals(LocalDate(2026, 3, 20), eid.date)
        assertTrue(eid.isEstimated)
    }

    @Test
    fun ruleNeedsEnglishName() {
        assertFailsWith<IllegalArgumentException> {
            HolidayRule("xx.day", mapOf("de" to "Tag"), HolidayDate.Fixed(Month.MAY, 1))
        }
        val rule = HolidayRule("xx.day", mapOf("en" to "Day", "de" to "Tag"), HolidayDate.Fixed(Month.MAY, 1))
        assertEquals("Tag", rule.name("de"))
        assertEquals("Day", rule.name("fr"))
        assertEquals("Day", rule.name(null))
    }

    @Test
    fun nameLookupUsesPrimaryLanguageSubtag() {
        val rule =
            HolidayRule(
                "xx.day",
                mapOf("en" to "Day", "de" to "Tag", "uk" to "День", "pt-BR" to "Dia"),
                HolidayDate.Fixed(Month.MAY, 1),
            )
        assertEquals("Tag", rule.name("de-DE"))
        assertEquals("Tag", rule.name("de_AT"))
        assertEquals("Tag", rule.name("DE"))
        assertEquals("День", rule.name("uk-UA"))
        assertEquals("День", rule.name("uk_UA"))
        assertEquals("Dia", rule.name("pt-BR"))
        assertEquals("Day", rule.name("fr-FR"))
        val calendar = HolidayCalendar("XX", listOf(rule))
        assertEquals("День", calendar.holidaysIn(2026, language = "uk-UA").single().name)
    }

    private companion object {
        // Easter Sunday 2000-2050 (Gregorian dates), from dateutil.easter.
        val WESTERN =
            listOf(
                "04-23", "04-15", "03-31", "04-20", "04-11", "03-27", "04-16", "04-08", "03-23", "04-12",
                "04-04", "04-24", "04-08", "03-31", "04-20", "04-05", "03-27", "04-16", "04-01", "04-21",
                "04-12", "04-04", "04-17", "04-09", "03-31", "04-20", "04-05", "03-28", "04-16", "04-01",
                "04-21", "04-13", "03-28", "04-17", "04-09", "03-25", "04-13", "04-05", "04-25", "04-10",
                "04-01", "04-21", "04-06", "03-29", "04-17", "04-09", "03-25", "04-14", "04-05", "04-18",
                "04-10",
            )
        val ORTHODOX =
            listOf(
                "04-30", "04-15", "05-05", "04-27", "04-11", "05-01", "04-23", "04-08", "04-27", "04-19",
                "04-04", "04-24", "04-15", "05-05", "04-20", "04-12", "05-01", "04-16", "04-08", "04-28",
                "04-19", "05-02", "04-24", "04-16", "05-05", "04-20", "04-12", "05-02", "04-16", "04-08",
                "04-28", "04-13", "05-02", "04-24", "04-09", "04-29", "04-20", "04-05", "04-25", "04-17",
                "05-06", "04-21", "04-13", "05-03", "04-24", "04-09", "04-29", "04-21", "04-05", "04-25",
                "04-17",
            )
    }
}
