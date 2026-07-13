package dev.koza4e4ok.orrery.lunar

import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.Month
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class FestivalsTest {
    @Test
    fun `traditional festivals from lunar date`() {
        val duanwu = LunarDate(2026, 5, 5).toLocalDate()
        assertEquals("端午", traditionalFestival(duanwu.toLunarDate(), duanwu, ChineseVariant.SIMPLIFIED))
        val eve = LocalDate(2026, 2, 16) // day before CNY 2026-02-17
        assertEquals("除夕", traditionalFestival(eve.toLunarDate(), eve, ChineseVariant.SIMPLIFIED))
        val plain = LocalDate(2026, 3, 10)
        assertNull(traditionalFestival(plain.toLunarDate(), plain, ChineseVariant.SIMPLIFIED))
    }

    @Test
    fun `gregorian festivals are variant specific`() {
        assertEquals("国庆节", gregorianFestival(LocalDate(2026, 10, 1), ChineseVariant.SIMPLIFIED))
        assertEquals("國慶節", gregorianFestival(LocalDate(2026, 10, 1), ChineseVariant.TRADITIONAL_TW))
        assertEquals("國慶日", gregorianFestival(LocalDate(2026, 10, 1), ChineseVariant.TRADITIONAL_HK))
        assertEquals("香港回歸", gregorianFestival(LocalDate(2026, 7, 1), ChineseVariant.TRADITIONAL_HK))
        assertNull(gregorianFestival(LocalDate(2026, 7, 2), ChineseVariant.SIMPLIFIED))
    }

    @Test
    fun `special festivals on nth weekdays`() {
        assertEquals("母亲节", specialFestival(LocalDate(2026, 5, 10), ChineseVariant.SIMPLIFIED))
        assertEquals("父亲节", specialFestival(LocalDate(2026, 6, 21), ChineseVariant.SIMPLIFIED))
        assertEquals("感恩节", specialFestival(LocalDate(2026, 11, 26), ChineseVariant.SIMPLIFIED))
        assertNull(specialFestival(LocalDate(2026, 5, 3), ChineseVariant.SIMPLIFIED)) // first Sunday, not second
    }

    @Test
    fun `nthWeekday matches java time across 1900-2099`() {
        for (year in 1900..2099) {
            val expected =
                java.time.LocalDate
                    .of(year, 11, 1)
                    .with(
                        java.time.temporal.TemporalAdjusters
                            .dayOfWeekInMonth(4, java.time.DayOfWeek.THURSDAY),
                    )
            val actual = nthWeekday(year, Month.NOVEMBER, DayOfWeek.THURSDAY, 4)
            assertEquals(expected.dayOfMonth, actual.day, "year $year")
        }
    }
}
