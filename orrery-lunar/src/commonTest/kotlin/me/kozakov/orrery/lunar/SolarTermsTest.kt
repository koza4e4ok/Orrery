package me.kozakov.orrery.lunar

import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals

class SolarTermsTest {
    @Test
    fun `spring equinox 2026 is March 20`() {
        assertEquals(SolarTerm.CHUNFEN, solarTermsFor(2026)[LocalDate(2026, 3, 20)])
    }

    @Test
    fun `display names`() {
        assertEquals("谷雨", SolarTerm.GUYU.displayName(ChineseVariant.SIMPLIFIED))
        assertEquals("穀雨", SolarTerm.GUYU.displayName(ChineseVariant.TRADITIONAL_TW))
        assertEquals("惊蛰", SolarTerm.JINGZHE.displayName(ChineseVariant.SIMPLIFIED))
    }

    @Test
    fun `boundary solar terms and 24 terms per year across 1900-2099`() {
        val terms1900 = solarTermsFor(1900)
        assertEquals(SolarTerm.LICHUN, terms1900[LocalDate(1900, 2, 4)])
        assertEquals(SolarTerm.CHUNFEN, terms1900[LocalDate(1900, 3, 21)])

        val terms2099 = solarTermsFor(2099)
        assertEquals(SolarTerm.XIAOHAN, terms2099[LocalDate(2099, 1, 5)])
        assertEquals(SolarTerm.DONGZHI, terms2099[LocalDate(2099, 12, 21)])

        for (year in 1900..2099) {
            val terms = solarTermsFor(year)
            assertEquals(24, terms.size, "year $year")
            assertEquals(SolarTerm.entries.toSet(), terms.values.toSet(), "year $year")
        }
    }
}
