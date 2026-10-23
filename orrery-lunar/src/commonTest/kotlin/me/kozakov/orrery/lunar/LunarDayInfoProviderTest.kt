package me.kozakov.orrery.lunar

import kotlinx.datetime.LocalDate
import me.kozakov.orrery.core.DayInfoPriority
import me.kozakov.orrery.core.payloadAs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class LunarDayInfoProviderTest {
    private val provider = LunarDayInfoProvider()

    @Test
    fun `solar term beats festivals`() {
        // 2024-04-04 is both 清明 and TW children's day (0404兒童節)
        val tw = LunarDayInfoProvider(ChineseVariant.TRADITIONAL_TW)
        val info = checkNotNull(tw.info(LocalDate(2024, 4, 4)))
        assertEquals("清明", info.label)
        assertTrue("solar-term" in info.tags)
        assertEquals(DayInfoPriority.FESTIVAL, info.priority)
        val metadata = checkNotNull(info.payloadAs<LunarDayMetadata>())
        assertEquals(SolarTerm.QINGMING, metadata.solarTerm)
        assertEquals("兒童節", metadata.gregorianFestival)
    }

    @Test
    fun `solar term without festival uses solar term priority`() {
        // 2026-02-04 is 立春 and not a festival
        val info = checkNotNull(provider.info(LocalDate(2026, 2, 4)))
        assertEquals("立春", info.label)
        assertFalse(info.isHoliday)
        assertEquals(DayInfoPriority.SOLAR_TERM, info.priority)
        val metadata = checkNotNull(info.payloadAs<LunarDayMetadata>())
        assertEquals(SolarTerm.LICHUN, metadata.solarTerm)
        assertNull(metadata.gregorianFestival)
        assertNull(metadata.traditionalFestival)
    }

    @Test
    fun `gregorian festival beats traditional and lunar text`() {
        val info = checkNotNull(provider.info(LocalDate(2026, 1, 1)))
        assertEquals("元旦", info.label)
        assertTrue(info.isHoliday)
        assertTrue("gregorian-festival" in info.tags)
        assertEquals(DayInfoPriority.FESTIVAL, info.priority)
        val metadata = checkNotNull(info.payloadAs<LunarDayMetadata>())
        assertEquals("元旦", metadata.gregorianFestival)
    }

    @Test
    fun `chinese new year and eve`() {
        val newYear = checkNotNull(provider.info(LocalDate(2026, 2, 17)))
        assertEquals("春节", newYear.label)
        assertEquals("除夕", provider.info(LocalDate(2026, 2, 16))?.label)
        assertTrue(newYear.isHoliday)
        assertEquals(DayInfoPriority.FESTIVAL, newYear.priority)
        val metadata = checkNotNull(newYear.payloadAs<LunarDayMetadata>())
        assertEquals(LunarDate(2026, 1, 1), metadata.lunarDate)
        assertEquals("春节", metadata.traditionalFestival)
    }

    @Test
    fun `plain day shows lunar day text and is not a holiday`() {
        // 2026-02-21 = lunar 1/5; nearest term (雨水) is Feb 18/19
        val info = checkNotNull(provider.info(LocalDate(2026, 2, 21)))
        assertEquals("初五", info.label)
        assertFalse(info.isHoliday)
        assertTrue(info.tags.isEmpty())
        assertEquals(DayInfoPriority.DEFAULT, info.priority)
        val metadata = checkNotNull(info.payloadAs<LunarDayMetadata>())
        assertEquals(LunarDate(2026, 1, 5), metadata.lunarDate)
        assertNull(metadata.solarTerm)
        assertNull(metadata.gregorianFestival)
        assertNull(metadata.traditionalFestival)
    }

    @Test
    fun `leap month first day`() {
        val date = LunarDate(2025, 6, 1, isLeapMonth = true).toLocalDate()
        assertEquals("闰六月", provider.info(date)?.label)
    }

    @Test
    fun `special weekday festival`() {
        val info = checkNotNull(provider.info(LocalDate(2026, 11, 26)))
        assertEquals("感恩节", info.label)
        assertTrue("special-festival" in info.tags)
        assertEquals(DayInfoPriority.FESTIVAL, info.priority)
        assertEquals("感恩节", info.payloadAs<LunarDayMetadata>()?.gregorianFestival)
    }

    @Test
    fun `variant strings flow through`() {
        val tw = LunarDayInfoProvider(ChineseVariant.TRADITIONAL_TW)
        assertEquals("春節", tw.info(LocalDate(2026, 2, 17))?.label)
    }

    @Test
    fun `out of range returns null`() {
        assertNull(provider.info(LocalDate(1899, 12, 31)))
        assertNull(provider.info(LocalDate(2100, 1, 1)))
    }
}
