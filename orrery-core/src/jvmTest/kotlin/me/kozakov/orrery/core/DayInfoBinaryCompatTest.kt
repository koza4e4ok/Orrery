package me.kozakov.orrery.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DayInfoBinaryCompatTest {
    @Test
    fun `three-arg constructor and default constructor bridge remain callable from bytecode`() {
        val ctor3 =
            DayInfo::class.java.getDeclaredConstructor(
                String::class.java,
                Boolean::class.javaPrimitiveType,
                List::class.java,
            )
        val created = ctor3.newInstance("元旦", true, listOf("gregorian-festival"))
        assertEquals("元旦", created.label)
        assertTrue(created.isHoliday)
        assertEquals(listOf("gregorian-festival"), created.tags)
        assertEquals(DayInfoPriority.DEFAULT, created.priority)
        assertNull(created.colorArgb)
        assertNull(created.payload)

        val defaultMarker = Class.forName("kotlin.jvm.internal.DefaultConstructorMarker")
        val ctorDefault =
            DayInfo::class.java.getDeclaredConstructor(
                String::class.java,
                Boolean::class.javaPrimitiveType,
                List::class.java,
                Int::class.javaPrimitiveType,
                defaultMarker,
            )
        val defaulted = ctorDefault.newInstance("初一", false, emptyList<String>(), 0b11, null)
        assertEquals(DayInfo(label = "初一"), defaulted)
    }

    @Test
    fun `three-arg copy and copy default bridges preserve priority colorArgb and payload`() {
        val original =
            DayInfo(
                label = "春节",
                isHoliday = true,
                tags = listOf("traditional-festival"),
                priority = DayInfoPriority.FESTIVAL,
                colorArgb = 0xFFD32F2F,
                payload = "metadata",
            )
        val copy3 =
            DayInfo::class.java.getDeclaredMethod(
                "copy",
                String::class.java,
                Boolean::class.javaPrimitiveType,
                List::class.java,
            )
        val copied = copy3.invoke(original, "Spring Festival", false, listOf("renamed")) as DayInfo
        assertEquals("Spring Festival", copied.label)
        assertEquals(false, copied.isHoliday)
        assertEquals(listOf("renamed"), copied.tags)
        assertEquals(DayInfoPriority.FESTIVAL, copied.priority)
        assertEquals(0xFFD32F2F, copied.colorArgb)
        assertEquals("metadata", copied.payload)

        val copyDefault =
            DayInfo::class.java.getDeclaredMethod(
                "copy$default",
                DayInfo::class.java,
                String::class.java,
                Boolean::class.javaPrimitiveType,
                List::class.java,
                Int::class.javaPrimitiveType,
                Any::class.java,
            )
        val copyDefaulted =
            copyDefault.invoke(null, original, "Updated", false, emptyList<String>(), 0b110, null) as DayInfo
        assertEquals("Updated", copyDefaulted.label)
        assertTrue(copyDefaulted.isHoliday)
        assertEquals(listOf("traditional-festival"), copyDefaulted.tags)
        assertEquals(DayInfoPriority.FESTIVAL, copyDefaulted.priority)
        assertEquals(0xFFD32F2F, copyDefaulted.colorArgb)
        assertEquals("metadata", copyDefaulted.payload)
    }
}
