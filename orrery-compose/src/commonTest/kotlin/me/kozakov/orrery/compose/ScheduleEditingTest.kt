package me.kozakov.orrery.compose

import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import me.kozakov.orrery.core.CalendarEvent
import me.kozakov.orrery.core.EventSpan
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ScheduleEditingTest {
    private val day = HourWindow(0, 24)
    private val workingHours = HourWindow(8, 18)

    private fun range(
        start: Int,
        end: Int,
    ) = MinuteRange(start, end)

    private fun at(
        hour: Int,
        minute: Int = 0,
    ) = hour * 60 + minute

    @Test
    fun snapMinutesMustDivideTheHour() {
        for (snap in listOf(1, 5, 10, 15, 20, 30, 60)) assertTrue(isValidSnapMinutes(snap), "$snap")
        for (snap in listOf(0, -15, 7, 25, 45, 90, 120)) assertFalse(isValidSnapMinutes(snap), "$snap")
    }

    @Test
    fun snappingOnMultiplesAndAroundThem() {
        for (snap in listOf(5, 10, 15, 20, 30, 60)) {
            val exact = snap * 7
            assertEquals(exact, snapNearest(exact, snap), "nearest $snap")
            assertEquals(exact, snapFloor(exact, snap), "floor $snap")
            assertEquals(exact, snapCeil(exact, snap), "ceil $snap")
            assertEquals(exact, snapNearest(exact + 1, snap), "nearest +1 $snap")
            assertEquals(exact, snapNearest(exact - 1, snap), "nearest -1 $snap")
            assertEquals(exact, snapFloor(exact + 1, snap), "floor +1 $snap")
            assertEquals(exact - snap, snapFloor(exact - 1, snap), "floor -1 $snap")
            assertEquals(exact + snap, snapCeil(exact + 1, snap), "ceil +1 $snap")
            assertEquals(exact, snapCeil(exact - 1, snap), "ceil -1 $snap")
        }
        assertEquals(at(10, 30), snapNearest(at(10, 23), 15))
        assertEquals(at(10, 15), snapNearest(at(10, 22), 15))
    }

    @Test
    fun minuteAtIsClampedToTheWindow() {
        assertEquals(at(10, 20), minuteAt(140f, 60f, workingHours))
        assertEquals(at(8), minuteAt(-30f, 60f, workingHours))
        assertEquals(at(18), minuteAt(5000f, 60f, workingHours))
        assertEquals(at(1, 30), minuteAt(180f, 120f, day))
    }

    @Test
    fun createDraggingDownFloorsTheAnchorAndCeilsThePointer() {
        assertEquals(range(at(10, 15), at(11, 15)), createRange(at(10, 20), at(11, 10), 15, 0, 1440))
    }

    @Test
    fun createDraggingUpGrowsUpward() {
        assertEquals(range(at(9, 30), at(10, 30)), createRange(at(10, 20), at(9, 40), 15, 0, 1440))
    }

    @Test
    fun createIsAtLeastOneSnapStep() {
        assertEquals(range(at(10, 15), at(10, 30)), createRange(at(10, 20), at(10, 20), 15, 0, 1440))
        assertEquals(range(at(10), at(10, 15)), createRange(at(10), at(10), 15, 0, 1440))
        assertEquals(range(at(10), at(11)), createRange(at(10, 20), at(10, 40), 60, 0, 1440))
    }

    @Test
    fun createIsClampedToTheWindow() {
        assertEquals(range(at(17, 30), at(18)), createRange(at(17, 40), at(18), 15, at(8), at(18)))
        assertEquals(range(at(17, 45), at(18)), createRange(at(18), at(18), 15, at(8), at(18)))
        assertEquals(range(at(23, 30), 1440), createRange(at(23, 40), 1440, 15, 0, 1440))
    }

    @Test
    fun longPressWithoutDragCreatesOneHour() {
        assertEquals(range(at(10, 15), at(11, 15)), defaultCreateRange(at(10, 20), 15, 0, 1440))
        assertEquals(range(at(10), at(11)), defaultCreateRange(at(10, 20), 60, 0, 1440))
        assertEquals(range(at(23, 30), 1440), defaultCreateRange(at(23, 40), 15, 0, 1440))
        assertEquals(range(at(17, 45), at(18)), defaultCreateRange(at(18), 15, at(8), at(18)))
    }

    @Test
    fun moveKeepsTheDuration() {
        assertEquals(range(at(10, 30), at(11)), moveRange(at(9), at(9, 30), 90, 15, 0, 1440))
        assertEquals(range(at(10, 30), at(11, 20)), moveRange(at(9), at(9, 50), 88, 15, 0, 1440))
        assertEquals(range(at(8, 30), at(9)), moveRange(at(9), at(9, 30), -25, 15, 0, 1440))
    }

    @Test
    fun moveByZeroReturnsTheSameRangeEvenOffTheGrid() {
        assertEquals(range(at(9, 10), at(9, 40)), moveRange(at(9, 10), at(9, 40), 0, 15, 0, 1440))
    }

    @Test
    fun moveShiftsInsteadOfShrinkingAtTheWindowEdges() {
        assertEquals(range(at(8), at(9)), moveRange(at(9), at(10), -300, 15, at(8), at(18)))
        assertEquals(range(at(17), at(18)), moveRange(at(9), at(10), 900, 15, at(8), at(18)))
        assertEquals(range(at(23), 1440), moveRange(at(22), at(23), 500, 15, 0, 1440))
        // A clipped event (06:30-08:30 in an 8-18 window) keeps its real two hours.
        assertEquals(range(at(9), at(11)), moveRange(at(6, 30), at(8, 30), 150, 15, at(8), at(18)))
    }

    @Test
    fun resizeKeepsTheMinimumDurationAndCannotCross() {
        assertEquals(range(at(9), at(10, 30)), resizeEnd(at(9), at(10), at(10, 30), 15, 15, 1440))
        assertEquals(range(at(9), at(9, 15)), resizeEnd(at(9), at(10), at(8), 15, 15, 1440))
        assertEquals(range(at(9), at(9, 30)), resizeEnd(at(9), at(10), at(9, 5), 15, 30, 1440))
        assertEquals(range(at(9, 30), at(10)), resizeStart(at(9), at(10), at(9, 30), 15, 15, 0))
        assertEquals(range(at(9, 45), at(10)), resizeStart(at(9), at(10), at(11), 15, 15, 0))
        assertEquals(range(at(9, 30), at(10)), resizeStart(at(9), at(10), at(9, 55), 15, 30, 0))
    }

    @Test
    fun resizeIsClampedToTheWindow() {
        assertEquals(range(at(8), at(10)), resizeStart(at(9), at(10), at(6), 15, 15, at(8)))
        assertEquals(range(at(17), at(18)), resizeEnd(at(17), at(17, 30), at(20), 15, 15, at(18)))
        assertEquals(range(at(23), 1440), resizeEnd(at(23), at(23, 30), 1500, 15, 15, 1440))
    }

    @Test
    fun midnightIsReportedAsZeroOClock() {
        assertEquals(LocalTime(0, 0), minuteToLocalTime(1440))
        assertEquals(LocalTime(23, 45), minuteToLocalTime(at(23, 45)))
        val preview =
            ScheduleDragPreview<Nothing>(
                kind = ScheduleDragKind.Create,
                event = null,
                date = LocalDate(2026, 7, 15),
                startTime = LocalTime(23, 0),
                endTime = LocalTime(0, 0),
            )
        assertEquals(range(at(23), 1440), previewMinutes(preview))
    }

    @Test
    fun onlySingleDayTimedEventsAreEditable() {
        fun timed(
            start: LocalDateTime,
            end: LocalDateTime,
        ) = CalendarEvent<Nothing>(id = "e", title = "E", span = EventSpan.Timed(start, end))
        val jul15 = LocalDate(2026, 7, 15)
        assertEquals(
            range(at(9), at(9, 30)),
            editableMinutes(timed(LocalDateTime(2026, 7, 15, 9, 0), LocalDateTime(2026, 7, 15, 9, 30))),
        )
        assertEquals(
            range(at(22), 1440),
            editableMinutes(timed(LocalDateTime(2026, 7, 15, 22, 0), LocalDateTime(2026, 7, 16, 0, 0))),
        )
        assertNull(editableMinutes(timed(LocalDateTime(2026, 7, 15, 22, 0), LocalDateTime(2026, 7, 16, 1, 0))))
        assertNull(editableMinutes(CalendarEvent<Nothing>(id = "a", title = "A", span = EventSpan.AllDay(jul15))))
    }

    @Test
    fun templatesAreFilledByPosition() {
        assertEquals("Create event at 09:15", fillTemplate("Create event at %1\$s", "09:15"))
        assertEquals("09:15 – 10:30", fillTemplate("%1\$s – %2\$s", "09:15", "10:30"))
    }
}
