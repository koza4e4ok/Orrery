@file:OptIn(ExperimentalOrreryApi::class)

package me.kozakov.orrery.widget

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.YearMonth
import me.kozakov.orrery.ExperimentalOrreryApi
import me.kozakov.orrery.core.CalendarEvent
import me.kozakov.orrery.core.EventSpan
import me.kozakov.orrery.core.Selection
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CalendarSnapshotModelTest {
    private val november = CalendarSnapshotRange.Month(YearMonth(2026, 11))
    private val day14 = LocalDate(2026, 11, 14)

    @Test
    fun specDefaults() {
        val spec = CalendarSnapshotSpec(november)
        assertEquals(november, spec.range)
        assertNull(spec.today)
        assertEquals(Selection.Empty, spec.selection)
        assertNull(spec.dayInfo)
        assertTrue(spec.eventCounts.isEmpty())
        assertTrue(spec.agendaEvents.isEmpty())
        assertEquals(DayOfWeek.MONDAY, spec.firstDayOfWeek)
        assertTrue(spec.showTitle)
        assertTrue(spec.showWeekdayLabels)
        assertFalse(spec.showOutsideDays)
    }

    @Test
    fun specCopyReplacesOnlyTheGivenFields() {
        val spec = CalendarSnapshotSpec(november, today = day14, eventCounts = mapOf(day14 to 2))
        val copy = spec.copy(firstDayOfWeek = DayOfWeek.SUNDAY, showOutsideDays = true)
        assertEquals(DayOfWeek.SUNDAY, copy.firstDayOfWeek)
        assertTrue(copy.showOutsideDays)
        assertEquals(spec.today, copy.today)
        assertEquals(spec.eventCounts, copy.eventCounts)
        assertEquals(spec, spec.copy())
        assertNotEquals(spec, copy)
    }

    @Test
    fun specComparesByValue() {
        val events = listOf(event("a"))
        val a = CalendarSnapshotSpec(november, selection = Selection(single = day14), agendaEvents = events)
        val b = CalendarSnapshotSpec(november, selection = Selection(single = day14), agendaEvents = events.toList())
        assertEquals(a, b)
        assertEquals(a.hashCode(), b.hashCode())
        assertNotEquals(a, b.copy(showTitle = false))
    }

    @Test
    fun specToStringDoesNotIncludeEventTitles() {
        val spec = CalendarSnapshotSpec(CalendarSnapshotRange.Agenda(day14), agendaEvents = listOf(event("Private")))
        assertFalse("Private" in spec.toString())
        assertTrue("1 events" in spec.toString())
    }

    @Test
    fun colorsFollowTheMiniMonthMapping() {
        listOf(lightColorScheme(), darkColorScheme()).forEach { scheme ->
            val colors = CalendarSnapshotDefaults.colors(scheme)
            assertEquals(scheme.surface, colors.container)
            assertEquals(scheme.onSurface, colors.title)
            assertEquals(scheme.onSurfaceVariant, colors.weekday)
            assertEquals(scheme.onSurface, colors.day)
            assertEquals(scheme.outline, colors.outsideDay)
            assertEquals(scheme.primary, colors.today)
            assertEquals(scheme.onPrimary, colors.todayContent)
            assertEquals(scheme.primary, colors.selection)
            assertEquals(scheme.onPrimary, colors.selectionContent)
            assertEquals(scheme.primaryContainer, colors.rangeBand)
            assertEquals(scheme.tertiary, colors.holiday)
            assertEquals(scheme.secondary, colors.eventDot)
        }
    }

    @Test
    fun colorsCopyAndCompareByValue() {
        val colors = CalendarSnapshotDefaults.colors(lightColorScheme())
        val red = colors.copy(eventDot = Color.Red)
        assertEquals(Color.Red, red.eventDot)
        assertEquals(colors.container, red.container)
        assertEquals(colors, colors.copy())
        assertEquals(colors.hashCode(), colors.copy().hashCode())
        assertNotEquals(colors, red)
        assertTrue("eventDot=" in red.toString())
    }

    @Test
    fun styleDefaults() {
        val style = CalendarSnapshotDefaults.style()
        assertEquals(12.dp, style.cornerRadius)
        assertEquals(8.dp, style.contentPadding)
        assertEquals(14.sp, style.titleTextSize)
        assertEquals(12.sp, style.dayTextSize)
        assertEquals(10.sp, style.weekdayTextSize)
        assertEquals(0.6f, style.minTextScale)
        assertEquals(CalendarSnapshotStyle(), style)
        assertEquals(16.sp, style.copy(dayTextSize = 16.sp).dayTextSize)
        assertNotEquals(style, style.copy(minTextScale = 1f))
    }

    @Test
    fun styleRejectsInvalidValues() {
        assertFailsWith<IllegalArgumentException> { CalendarSnapshotStyle(minTextScale = 0f) }
        assertFailsWith<IllegalArgumentException> { CalendarSnapshotStyle(minTextScale = 1.5f) }
        assertFailsWith<IllegalArgumentException> { CalendarSnapshotStyle(dayTextSize = 1.em) }
        assertFailsWith<IllegalArgumentException> { CalendarSnapshotStyle().copy(minTextScale = -1f) }
    }

    @Test
    fun sizeLimitsAcceptTheEdges() {
        checkSnapshotSize(1, 1)
        checkSnapshotSize(4096, 1024)
        checkSnapshotSize(1024, 4096)
        checkSnapshotSize(2048, 2048)
    }

    @Test
    fun sizeLimitsRejectValuesOutsideThemAndNameThem() {
        val sides = listOf(0 to 10, 10 to 0, -1 to 10, 4097 to 1, 1 to 4097)
        sides.forEach { (width, height) ->
            val error = assertFailsWith<IllegalArgumentException> { checkSnapshotSize(width, height) }
            assertTrue("$width x $height" in error.message.orEmpty(), error.message)
        }
        val area = assertFailsWith<IllegalArgumentException> { checkSnapshotSize(2049, 2048) }
        assertTrue("2049 x 2048 = 4196352" in area.message.orEmpty(), area.message)
    }

    private fun event(title: String): CalendarEvent<Nothing> = CalendarEvent(title, title, EventSpan.AllDay(day14))
}
