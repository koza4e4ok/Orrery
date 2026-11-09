package me.kozakov.orrery.core

import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class CalendarEventBinaryCompatTest {
    private val span = EventSpan.AllDay(LocalDate(2026, 1, 31))
    private val rule = RecurrenceRule.monthlyOnLastDay()
    private val instance = RecurrenceInstance("series", LocalDate(2026, 1, 31))
    private val sixArgTypes =
        arrayOf(
            String::class.java,
            String::class.java,
            EventSpan::class.java,
            String::class.java,
            Long::class.javaObjectType,
            Any::class.java,
        )

    @Test
    fun `six-arg constructor and default constructor bridge remain callable from bytecode`() {
        val ctor6 = CalendarEvent::class.java.getDeclaredConstructor(*sixArgTypes)
        val created = ctor6.newInstance("id", "Title", span, "sub", 0xFF00FF00, "payload")
        assertEquals("id", created.id)
        assertEquals("Title", created.title)
        assertEquals(span, created.span)
        assertEquals("sub", created.subtitle)
        assertEquals(0xFF00FF00, created.colorArgb)
        assertEquals("payload", created.payload)
        assertNull(created.recurrence)
        assertNull(created.instance)

        val defaultMarker = Class.forName("kotlin.jvm.internal.DefaultConstructorMarker")
        val ctorDefault =
            CalendarEvent::class.java.getDeclaredConstructor(*sixArgTypes, Int::class.javaPrimitiveType, defaultMarker)
        val defaulted = ctorDefault.newInstance("id", "Title", span, null, null, null, 0b111000, null)
        assertEquals(CalendarEvent<Nothing>(id = "id", title = "Title", span = span), defaulted)
    }

    @Test
    fun `six-arg copy and copy default bridges preserve recurrence and instance`() {
        val original =
            CalendarEvent(
                id = "id",
                title = "Title",
                span = span,
                subtitle = "sub",
                colorArgb = 0xFF00FF00,
                payload = "payload",
                recurrence = rule,
                instance = instance,
            )
        val copy6 = CalendarEvent::class.java.getDeclaredMethod("copy", *sixArgTypes)
        val copied = copy6.invoke(original, "id2", "Renamed", span, null, null, "other") as CalendarEvent<*>
        assertEquals("id2", copied.id)
        assertEquals("Renamed", copied.title)
        assertNull(copied.subtitle)
        assertEquals("other", copied.payload)
        assertEquals(rule, copied.recurrence)
        assertEquals(instance, copied.instance)

        val copyDefault =
            CalendarEvent::class.java.getDeclaredMethod(
                "copy\$default",
                CalendarEvent::class.java,
                *sixArgTypes,
                Int::class.javaPrimitiveType,
                Any::class.java,
            )
        val copyDefaulted = copyDefault.invoke(null, original, null, "Updated", null, null, null, null, 0b111101, null)
        assertEquals(original.copy(title = "Updated"), copyDefaulted)
    }
}
