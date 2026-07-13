package me.kozakov.orrery.lunar

import kotlin.test.Test
import kotlin.test.assertEquals

class LunarStringsTest {
    @Test
    fun `first of month shows month name, other days show day name`() {
        assertEquals("春节", lunarLabel(LunarDate(2026, 1, 1), ChineseVariant.SIMPLIFIED))
        assertEquals("初二", lunarLabel(LunarDate(2026, 1, 2), ChineseVariant.SIMPLIFIED))
        assertEquals("二十", lunarLabel(LunarDate(2026, 3, 20), ChineseVariant.SIMPLIFIED))
        assertEquals("三十", lunarLabel(LunarDate(2026, 1, 30), ChineseVariant.SIMPLIFIED))
        assertEquals("臘月", lunarLabel(LunarDate(2026, 12, 1), ChineseVariant.TRADITIONAL_HK))
    }

    @Test
    fun `leap month first day gets variant-aware prefix`() {
        assertEquals("闰六月", lunarLabel(LunarDate(2025, 6, 1, isLeapMonth = true), ChineseVariant.SIMPLIFIED))
        assertEquals("閏六月", lunarLabel(LunarDate(2025, 6, 1, isLeapMonth = true), ChineseVariant.TRADITIONAL_TW))
    }

    @Test
    fun `every variant table is fully populated`() {
        for (variant in ChineseVariant.entries) {
            val s = strings(variant)
            assertEquals(12, s.monthNames.size, "$variant months")
            assertEquals(30, s.dayNames.size, "$variant days")
            assertEquals(3, s.specialFestivals.size, "$variant specials")
            assertEquals(24, s.solarTermNames.size, "$variant terms")
            assertEquals(6, s.traditionalFestivals.size, "$variant traditional")
        }
    }
}
