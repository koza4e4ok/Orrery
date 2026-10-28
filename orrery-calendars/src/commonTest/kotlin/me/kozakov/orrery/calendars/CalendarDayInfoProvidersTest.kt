package me.kozakov.orrery.calendars

import kotlinx.datetime.LocalDate
import me.kozakov.orrery.calendars.hijri.HijriDate
import me.kozakov.orrery.calendars.hijri.HijriDayInfoProvider
import me.kozakov.orrery.calendars.hijri.HijriStrings
import me.kozakov.orrery.calendars.hijri.HijriVariant
import me.kozakov.orrery.calendars.persian.PersianDate
import me.kozakov.orrery.calendars.persian.PersianDayInfoProvider
import me.kozakov.orrery.calendars.persian.PersianStrings
import me.kozakov.orrery.core.DayInfoPriority
import me.kozakov.orrery.core.payloadAs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class CalendarDayInfoProvidersTest {
    @Test
    fun hijriLabelsTagsAndPayload() {
        val provider = HijriDayInfoProvider()
        val first = assertNotNull(provider.info(LocalDate(2026, 2, 18)))
        assertEquals("Ramadan", first.label)
        assertEquals(listOf("hijri"), first.tags)
        assertEquals(DayInfoPriority.SECONDARY, first.priority)
        assertEquals(HijriDate(1447, 9, 1), first.payloadAs<HijriDate>())
        assertEquals("2", provider.info(LocalDate(2026, 2, 19))?.label)
        assertEquals("رمضان", HijriDayInfoProvider(strings = HijriStrings.Arabic).info(LocalDate(2026, 2, 18))?.label)
        assertEquals("١٢", HijriDayInfoProvider(strings = HijriStrings.Arabic).info(LocalDate(2026, 3, 1))?.label)
    }

    @Test
    fun hijriOutsideUmmAlQuraRangeIsNullButTabularWorks() {
        assertNull(HijriDayInfoProvider().info(LocalDate(1800, 1, 1)))
        assertNotNull(HijriDayInfoProvider(HijriVariant.Tabular).info(LocalDate(1800, 1, 1)))
    }

    @Test
    fun persianLabels() {
        val provider = PersianDayInfoProvider()
        val nowruz = assertNotNull(provider.info(LocalDate(2026, 3, 21)))
        assertEquals("Farvardin", nowruz.label)
        assertEquals(listOf("persian"), nowruz.tags)
        assertEquals(PersianDate(1405, 1, 1), nowruz.payloadAs<PersianDate>())
        assertEquals("۲", PersianDayInfoProvider(PersianStrings.Persian).info(LocalDate(2026, 3, 22))?.label)
        assertNull(provider.info(LocalDate(4000, 1, 1)))
    }

    @Test
    fun stringsValidateSizes() {
        assertFailsWith<IllegalArgumentException> { HijriStrings(listOf("a")) }
        assertFailsWith<IllegalArgumentException> { PersianStrings(PersianStrings.English.monthNames, digits = "0123") }
        assertEquals("٢٩", HijriStrings.Arabic.dayLabel(29))
    }
}
