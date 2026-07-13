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
    fun `matches original SolarTermUtil for 1900-2099`() {
        val golden =
            checkNotNull(javaClass.getResourceAsStream("/golden/solar-terms.csv"))
                .bufferedReader()
                .readLines()
                .map { it.split(",") }
                .groupBy(
                    keySelector = { it[0].toInt() },
                    valueTransform = { LocalDate.parse(it[1]) to it[2].toInt() },
                )
        for (year in 1900..2099) {
            val actual = solarTermsFor(year).map { (date, term) -> date to term.ordinal }.toSet()
            assertEquals(checkNotNull(golden[year]).toSet(), actual, "year $year")
        }
    }
}
