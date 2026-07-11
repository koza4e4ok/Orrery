package dev.koza4e4ok.material.calendar.lunar

import kotlinx.datetime.LocalDate
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
    }

    @Test
    fun `gregorian festival beats traditional and lunar text`() {
        val info = checkNotNull(provider.info(LocalDate(2026, 1, 1)))
        assertEquals("元旦", info.label)
        assertTrue(info.isHoliday)
        assertTrue("gregorian-festival" in info.tags)
    }

    @Test
    fun `chinese new year and eve`() {
        assertEquals("春节", provider.info(LocalDate(2026, 2, 17))?.label)
        assertEquals("除夕", provider.info(LocalDate(2026, 2, 16))?.label)
        assertTrue(checkNotNull(provider.info(LocalDate(2026, 2, 17))).isHoliday)
    }

    @Test
    fun `plain day shows lunar day text and is not a holiday`() {
        // 2026-02-21 = lunar 1/5; nearest term (雨水) is Feb 18/19
        val info = checkNotNull(provider.info(LocalDate(2026, 2, 21)))
        assertEquals("初五", info.label)
        assertFalse(info.isHoliday)
        assertTrue(info.tags.isEmpty())
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
