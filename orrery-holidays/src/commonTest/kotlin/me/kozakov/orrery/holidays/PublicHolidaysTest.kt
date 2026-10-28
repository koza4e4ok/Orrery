package me.kozakov.orrery.holidays

import kotlinx.datetime.LocalDate
import kotlinx.datetime.Month
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PublicHolidaysTest {
    private fun calendar(country: String) = assertNotNull(PublicHolidays.forCountry(country))

    private fun HolidayCalendar.substitutes(
        year: Int,
        subdivision: String? = null,
    ) = holidaysIn(year, subdivision).filter { it.isSubstitute }.map { it.date to it.ruleId }

    @Test
    fun supportedCountries() {
        assertEquals(setOf("DE", "GB", "UA", "US"), PublicHolidays.supportedCountries)
        assertNotNull(PublicHolidays.forCountry("us"))
        assertNull(PublicHolidays.forCountry("FR"))
    }

    @Test
    fun ruleIdsAreConsistent() {
        for (country in PublicHolidays.supportedCountries) {
            val rules = calendar(country).rules
            rules.forEach { assertTrue(it.id.startsWith(country.lowercase() + "."), it.id) }
            // A rule split by years keeps its id and its names.
            rules.groupBy { it.id }.forEach { (id, group) ->
                assertEquals(1, group.map { it.names }.distinct().size, id)
            }
        }
    }

    @Test
    fun usSaturdayHolidayIsObservedOnFridayAcrossTheYearEnd() {
        val us = calendar("US")
        assertEquals(
            listOf(
                LocalDate(2021, 6, 18) to "us.juneteenth",
                LocalDate(2021, 7, 5) to "us.independence-day",
                LocalDate(2021, 12, 24) to "us.christmas-day",
                LocalDate(2021, 12, 31) to "us.new-years-day",
            ),
            us.substitutes(2021),
        )
        assertTrue(us.holidaysIn(2020).none { it.ruleId == "us.juneteenth" })
        assertEquals(11, us.holidaysIn(2026).count { !it.isSubstitute })
    }

    @Test
    fun gbSubstitutesSkipDaysAlreadyOff() {
        val gb = calendar("GB")
        assertEquals(
            listOf(LocalDate(2021, 12, 27) to "gb.christmas-day", LocalDate(2021, 12, 28) to "gb.boxing-day"),
            gb.substitutes(2021),
        )
        assertEquals(listOf(LocalDate(2022, 1, 3) to "gb.new-years-day"), gb.substitutes(2022, "GB-ENG").take(1))
        assertEquals(
            listOf(LocalDate(2022, 1, 3) to "gb.new-years-day", LocalDate(2022, 1, 4) to "gb.new-year-holiday"),
            gb.substitutes(2022, "GB-SCT").take(2),
        )
    }

    @Test
    fun gbSubdivisionsAndOneOffs() {
        val gb = calendar("GB")
        val nationwide = gb.holidaysIn(2023).map { it.ruleId }
        assertTrue("gb.coronation-2023" in nationwide)
        assertFalse("gb.easter-monday" in nationwide)
        assertTrue("gb.easter-monday" in gb.holidaysIn(2023, "GB-WLS").map { it.ruleId })
        assertFalse("gb.easter-monday" in gb.holidaysIn(2023, "GB-SCT").map { it.ruleId })
        assertEquals(LocalDate(2020, 5, 8), gb.holidaysIn(2020).single { it.ruleId == "gb.early-may" }.date)
        assertEquals(LocalDate(2022, 6, 2), gb.holidaysIn(2022).single { it.ruleId == "gb.spring" }.date)
        assertTrue(gb.holidaysIn(2026).all { it.type == HolidayType.Bank })
    }

    @Test
    fun deStatesAndOptionalDays() {
        val de = calendar("DE")
        assertEquals(9, de.holidaysIn(2026).size)
        val bavaria = de.holidaysIn(2026, "DE-BY")
        assertTrue(bavaria.any { it.ruleId == "de.epiphany" })
        assertEquals(HolidayType.Optional, bavaria.single { it.ruleId == "de.assumption-day" }.type)
        assertEquals(HolidayType.Public, de.holidaysIn(2026, "DE-SL").single { it.ruleId == "de.assumption-day" }.type)
        assertEquals(
            LocalDate(2026, 11, 18),
            de.holidaysIn(2026, "DE-SN").single { it.ruleId == "de.repentance-day" }.date,
        )
        assertTrue(de.holidaysIn(2017).any { it.ruleId == "de.reformation-day" })
        assertFalse(de.holidaysIn(2018).any { it.ruleId == "de.reformation-day" })
        assertEquals(1, de.holidaysIn(2017, "DE-BB").count { it.ruleId == "de.reformation-day" })
        assertEquals("Tag der Deutschen Einheit", de.holidaysOn(LocalDate(2026, 10, 3), language = "de").single().name)
        assertTrue(de.holidaysIn(2026, "DE-BY").none { it.isSubstitute })
    }

    @Test
    fun uaLawChangesAndMartialLaw() {
        val ua = calendar("UA")
        assertEquals(
            listOf(
                LocalDate(2021, 5, 3) to "ua.labour-day",
                LocalDate(2021, 5, 4) to "ua.easter",
                LocalDate(2021, 5, 10) to "ua.victory-day",
            ),
            ua.substitutes(2021).filter { it.first.month == Month.MAY },
        )
        // 1 May 2005 was both Labour Day and Easter Sunday: one substitute day, on 3 May
        // because 2 May was a holiday too.
        assertEquals(
            listOf(LocalDate(2005, 5, 3) to "ua.easter"),
            ua.substitutes(2005).filter { it.first.month == Month.MAY },
        )
        assertEquals(listOf(LocalDate(2022, 1, 3) to "ua.new-years-day"), ua.substitutes(2022))
        assertEquals(emptyList(), ua.substitutes(2023))
        val ids2024 = ua.holidaysIn(2024).associate { it.ruleId to it.date }
        assertEquals(LocalDate(2024, 5, 8), ids2024["ua.remembrance-and-victory-day"])
        assertEquals(LocalDate(2024, 7, 15), ids2024["ua.statehood-day"])
        assertEquals(LocalDate(2024, 10, 1), ids2024["ua.defenders-day"])
        assertEquals(LocalDate(2024, 5, 5), ids2024["ua.easter"])
        assertNull(ids2024["ua.orthodox-christmas"])
        assertNull(ids2024["ua.victory-day"])
        assertTrue(ua.holidaysIn(2016).none { it.ruleId == "ua.christmas-day" })
        assertEquals("Новий рік", ua.holidaysOn(LocalDate(2026, 1, 1), language = "uk").single().name)
    }
}
