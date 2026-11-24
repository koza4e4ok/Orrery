package me.kozakov.orrery.compose

import kotlinx.datetime.LocalDate
import me.kozakov.orrery.core.DayInfo
import me.kozakov.orrery.core.DayPosition
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame

class DayAnnouncementTest {
    private val strings =
        CalendarDayStrings(
            rangeStart = "Range start",
            rangeEnd = "Range end",
            inRange = "In range",
            today = "Today",
            unavailable = "Unavailable",
            selected = "Selected",
            holiday = "Holiday",
            events = "Events: %1\$d",
        )

    private val date = LocalDate(2026, 7, 9)
    private val dateText = "Thursday, July 9, 2026"

    @Suppress("LongParameterList")
    private fun announce(
        isToday: Boolean = false,
        isSelected: Boolean = false,
        isRangeStart: Boolean = false,
        isRangeEnd: Boolean = false,
        isInRange: Boolean = false,
        isAvailable: Boolean = true,
        info: DayInfo? = null,
        labelVisible: Boolean = true,
        eventCount: Int = 0,
    ): DayAnnouncement =
        dayAnnouncement(
            date = date,
            position = DayPosition.MonthDate,
            dateText = dateText,
            isToday = isToday,
            isSelected = isSelected,
            isRangeStart = isRangeStart,
            isRangeEnd = isRangeEnd,
            isInRange = isInRange,
            isAvailable = isAvailable,
            info = info,
            labelVisible = labelVisible,
            eventCount = eventCount,
            strings = strings,
        )

    @Test
    fun plainDayHasOnlyTheDate() {
        val announcement = announce()
        assertEquals(dateText, announcement.contentDescription)
        assertEquals("", announcement.stateDescription)
    }

    @Test
    fun selectedTodayKeepsBothStates() {
        assertEquals("Selected, Today", announce(isToday = true, isSelected = true).stateDescription)
        assertEquals(
            "Selected, Today, Holiday",
            announce(isToday = true, isSelected = true, info = DayInfo(label = "Christmas", isHoliday = true))
                .stateDescription,
        )
        assertEquals("Selected, Unavailable", announce(isSelected = true, isAvailable = false).stateDescription)
    }

    @Test
    fun rangeStatesImplySelected() {
        assertEquals(
            "Range start, Today",
            announce(isToday = true, isSelected = true, isRangeStart = true).stateDescription,
        )
        assertEquals("Range end", announce(isSelected = true, isRangeEnd = true).stateDescription)
        assertEquals("In range", announce(isSelected = true, isInRange = true).stateDescription)
        assertEquals("Today, Unavailable", announce(isToday = true, isAvailable = false).stateDescription)
    }

    @Test
    fun everyFlagCombinationKeepsTheOrderWithoutDuplicates() {
        val order = listOf("Range start", "Range end", "In range", "Selected", "Today", "Holiday", "Unavailable")
        for (mask in 0 until (1 shl 7)) {
            val flags = List(7) { mask and (1 shl it) != 0 }
            val (selected, rangeStart, rangeEnd, inRange, today) = flags
            val holiday = flags[5]
            val unavailable = flags[6]
            val announcement =
                announce(
                    isToday = today,
                    isSelected = selected,
                    isRangeStart = rangeStart,
                    isRangeEnd = rangeEnd,
                    isInRange = inRange,
                    isAvailable = !unavailable,
                    info = DayInfo(label = null, isHoliday = holiday),
                )
            val expected =
                listOfNotNull(
                    "Range start".takeIf { rangeStart },
                    "Range end".takeIf { rangeEnd },
                    "In range".takeIf { inRange },
                    "Selected".takeIf { selected && !rangeStart && !rangeEnd && !inRange },
                    "Today".takeIf { today },
                    "Holiday".takeIf { holiday },
                    "Unavailable".takeIf { unavailable },
                )
            val parts = announcement.stateDescription.split(", ").filter { it.isNotEmpty() }
            assertEquals(expected, parts, "mask $mask")
            assertEquals(parts.sortedBy { order.indexOf(it) }, parts, "mask $mask")
            assertEquals(parts.toSet().size, parts.size, "mask $mask")
            assertEquals(dateText, announcement.contentDescription, "mask $mask")
        }
    }

    @Test
    fun visibleLabelIsAnnouncedTrimmed() {
        assertEquals("$dateText, Christmas", announce(info = DayInfo(label = "  Christmas ")).contentDescription)
    }

    @Test
    fun blankLabelIsIgnored() {
        assertEquals(dateText, announce(info = DayInfo(label = "   ")).contentDescription)
        assertEquals(dateText, announce(info = DayInfo(label = "")).contentDescription)
    }

    @Test
    fun hiddenLabelIsNotAnnouncedButStaysInInfo() {
        val info = DayInfo(label = "Christmas", isHoliday = true)
        val announcement = announce(info = info, labelVisible = false)
        assertEquals(dateText, announcement.contentDescription)
        assertEquals("Holiday", announcement.stateDescription)
        assertSame(info, announcement.info)
    }

    @Test
    fun eventCountIsAppendedAfterTheLabel() {
        assertEquals(
            "$dateText, Christmas, Events: 3",
            announce(info = DayInfo(label = "Christmas"), eventCount = 3).contentDescription,
        )
        assertEquals(dateText, announce(eventCount = 0).contentDescription)
        val negative = announce(eventCount = -2)
        assertEquals(dateText, negative.contentDescription)
        assertEquals(0, negative.eventCount)
    }

    @Test
    fun announcementCarriesTheCellState() {
        val announcement = announce(isToday = true, isSelected = true, eventCount = 2)
        assertEquals(date, announcement.date)
        assertEquals(DayPosition.MonthDate, announcement.position)
        assertEquals(true, announcement.isToday)
        assertEquals(true, announcement.isSelected)
        assertEquals(true, announcement.isAvailable)
        assertEquals(2, announcement.eventCount)
    }

    @Test
    fun fiveStringConstructorUsesEnglishForTheNewStrings() {
        val old = CalendarDayStrings("a", "b", "c", "d", "e")
        assertEquals("Selected", old.selected)
        assertEquals("Holiday", old.holiday)
        assertEquals("Events: %1\$d", old.events)
        assertEquals("e", old.unavailable)
    }
}
