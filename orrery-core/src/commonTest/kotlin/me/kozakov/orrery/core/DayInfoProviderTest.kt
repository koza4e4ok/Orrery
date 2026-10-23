package me.kozakov.orrery.core

import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DayInfoProviderTest {
    private val sampleDate = LocalDate(2026, 7, 9)

    private data class RatePayload(
        val nightlyUsd: Int,
    )

    @Test
    fun `defaults match zero priority no color and null payload`() {
        val info = DayInfo(label = "初一")
        assertEquals("初一", info.label)
        assertFalse(info.isHoliday)
        assertTrue(info.tags.isEmpty())
        assertEquals(DayInfoPriority.DEFAULT, info.priority)
        assertNull(info.colorArgb)
        assertNull(info.payload)
    }

    @Test
    fun `payloadAs returns typed value on match and null on mismatch or null`() {
        val rate = RatePayload(nightlyUsd = 140)
        val withPayload = DayInfo(label = "$140", payload = rate)
        assertEquals(rate, withPayload.payloadAs<RatePayload>())
        assertNull(withPayload.payloadAs<String>())

        val withoutPayload = DayInfo(label = "plain")
        assertNull(withoutPayload.payloadAs<RatePayload>())
    }

    @Test
    fun `priority tiers are strictly ordered`() {
        assertTrue(DayInfoPriority.DEFAULT < DayInfoPriority.SECONDARY)
        assertTrue(DayInfoPriority.SECONDARY < DayInfoPriority.SOLAR_TERM)
        assertTrue(DayInfoPriority.SOLAR_TERM < DayInfoPriority.FESTIVAL)
        assertTrue(DayInfoPriority.FESTIVAL < DayInfoPriority.PUBLIC_HOLIDAY)
    }

    @Test
    fun `plus returns non-null side when the other returns null`() {
        val onlyLeft =
            object : DayInfoProvider {
                override fun info(date: LocalDate): DayInfo = DayInfo(label = "left")
            }
        val empty =
            object : DayInfoProvider {
                override fun info(date: LocalDate): DayInfo? = null
            }

        assertEquals(DayInfo(label = "left"), (onlyLeft + empty).info(sampleDate))
        assertEquals(DayInfo(label = "left"), (empty + onlyLeft).info(sampleDate))
        assertNull((empty + empty).info(sampleDate))
    }

    @Test
    fun `plus selects higher priority for label colorArgb and payload while merging holiday and tags`() {
        val lunar =
            object : DayInfoProvider {
                override fun info(date: LocalDate): DayInfo =
                    DayInfo(
                        label = "雨水",
                        isHoliday = false,
                        tags = listOf("solar-term", "shared"),
                        priority = DayInfoPriority.SOLAR_TERM,
                        colorArgb = 0xFF1565C0,
                        payload = "lunar-payload",
                    )
            }
        val holiday =
            object : DayInfoProvider {
                override fun info(date: LocalDate): DayInfo =
                    DayInfo(
                        label = "Bank Holiday",
                        isHoliday = true,
                        tags = listOf("shared", "public-holiday"),
                        priority = DayInfoPriority.PUBLIC_HOLIDAY,
                        colorArgb = 0xFFC62828,
                        payload = "holiday-payload",
                    )
            }

        val merged = checkNotNull((lunar + holiday).info(sampleDate))
        assertEquals("Bank Holiday", merged.label)
        assertTrue(merged.isHoliday)
        assertEquals(listOf("solar-term", "shared", "public-holiday"), merged.tags)
        assertEquals(DayInfoPriority.PUBLIC_HOLIDAY, merged.priority)
        assertEquals(0xFFC62828, merged.colorArgb)
        assertEquals("holiday-payload", merged.payloadAs<String>())
    }

    @Test
    fun `plus favors left provider on priority ties`() {
        val first =
            object : DayInfoProvider {
                override fun info(date: LocalDate): DayInfo =
                    DayInfo(
                        label = "first",
                        isHoliday = true,
                        tags = listOf("a"),
                        priority = DayInfoPriority.FESTIVAL,
                        colorArgb = 0xFF111111,
                        payload = 1,
                    )
            }
        val second =
            object : DayInfoProvider {
                override fun info(date: LocalDate): DayInfo =
                    DayInfo(
                        label = "second",
                        isHoliday = false,
                        tags = listOf("b"),
                        priority = DayInfoPriority.FESTIVAL,
                        colorArgb = 0xFF222222,
                        payload = 2,
                    )
            }

        val merged = checkNotNull((first + second).info(sampleDate))
        assertEquals("first", merged.label)
        assertTrue(merged.isHoliday)
        assertEquals(listOf("a", "b"), merged.tags)
        assertEquals(DayInfoPriority.FESTIVAL, merged.priority)
        assertEquals(0xFF111111, merged.colorArgb)
        assertEquals(1, merged.payloadAs<Int>())
    }
}
