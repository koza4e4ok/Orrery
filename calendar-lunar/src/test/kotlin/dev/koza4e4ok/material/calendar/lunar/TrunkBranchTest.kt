package dev.koza4e4ok.material.calendar.lunar

import kotlin.test.Test
import kotlin.test.assertEquals

class TrunkBranchTest {
    @Test
    fun `known sexagenary years`() {
        assertEquals("甲子", trunkBranchYear(1984))
        assertEquals("庚子", trunkBranchYear(1900))
        assertEquals("甲辰", trunkBranchYear(2024))
        assertEquals("丙午", trunkBranchYear(2026))
    }

    @Test
    fun `cycle repeats every 60 years`() {
        for (year in 1900..2039) {
            assertEquals(trunkBranchYear(year), trunkBranchYear(year + 60), "year $year")
        }
    }
}
