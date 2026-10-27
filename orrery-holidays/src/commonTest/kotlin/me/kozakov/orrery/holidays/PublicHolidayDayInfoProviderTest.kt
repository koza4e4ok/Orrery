package me.kozakov.orrery.holidays

import kotlinx.datetime.LocalDate
import kotlinx.datetime.Month
import me.kozakov.orrery.core.DayInfo
import me.kozakov.orrery.core.DayInfoPriority
import me.kozakov.orrery.core.DayInfoProvider
import me.kozakov.orrery.core.payloadAs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PublicHolidayDayInfoProviderTest {
    private val us = PublicHolidayDayInfoProvider(PublicHolidays.forCountry("US")!!)

    @Test
    fun holidayInfo() {
        val info = assertNotNull(us.info(LocalDate(2026, 7, 4)))
        assertEquals("Independence Day", info.label)
        assertTrue(info.isHoliday)
        assertEquals(DayInfoPriority.PUBLIC_HOLIDAY, info.priority)
        assertEquals(listOf("holiday", "public"), info.tags)
        assertEquals("us.independence-day", info.payloadAs<List<PublicHoliday>>()!!.single().ruleId)
        assertNull(us.info(LocalDate(2026, 7, 6)))
    }

    @Test
    fun substituteDayIsTagged() {
        // 4 July 2026 is a Saturday.
        val info = assertNotNull(us.info(LocalDate(2026, 7, 3)))
        assertEquals(listOf("holiday", "public", "substitute"), info.tags)
    }

    @Test
    fun cacheServesManyYears() {
        val years = (2000..2030) + (2030 downTo 2000)
        years.forEach { assertEquals("Christmas Day", us.info(LocalDate(it, 12, 25))?.label, "$it") }
    }

    @Test
    fun subdivisionLanguageAndTypes() {
        val de = PublicHolidays.forCountry("DE")!!
        val bavaria = PublicHolidayDayInfoProvider(de, subdivision = "DE-BY", language = "de")
        assertEquals("Heilige Drei Könige", bavaria.info(LocalDate(2026, 1, 6))?.label)
        assertNull(bavaria.info(LocalDate(2026, 8, 15)))
        val withOptional =
            PublicHolidayDayInfoProvider(de, "DE-BY", types = setOf(HolidayType.Public, HolidayType.Optional))
        assertEquals("Assumption Day", withOptional.info(LocalDate(2026, 8, 15))?.label)
        assertEquals(listOf("holiday", "optional"), withOptional.info(LocalDate(2026, 8, 15))?.tags)
    }

    @Test
    fun estimatedHijriHolidayIsTagged() {
        val calendar =
            HolidayCalendar("XX", listOf(HolidayRule("xx.eid", mapOf("en" to "Eid"), HolidayDate.HijriFixed(10, 1))))
        val info = assertNotNull(PublicHolidayDayInfoProvider(calendar).info(LocalDate(2026, 3, 20)))
        assertEquals(listOf("holiday", "public", "estimated"), info.tags)
    }

    @Test
    fun winsTheLabelWhenComposed() {
        val secondary =
            object : DayInfoProvider {
                override fun info(date: LocalDate) =
                    DayInfo(label = "${date.day}", priority = DayInfoPriority.SECONDARY)
            }
        val composed = secondary + us
        assertEquals("Christmas Day", composed.info(LocalDate(2026, Month.DECEMBER, 25))?.label)
        assertEquals("24", composed.info(LocalDate(2026, Month.DECEMBER, 24))?.label)
    }
}
