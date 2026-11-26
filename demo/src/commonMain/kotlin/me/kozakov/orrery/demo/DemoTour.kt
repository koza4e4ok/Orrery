package me.kozakov.orrery.demo

import kotlinx.coroutines.delay
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.datetime.YearMonth
import kotlinx.datetime.plus
import me.kozakov.orrery.compose.CalendarDensity
import me.kozakov.orrery.compose.CollapsibleCalendarAnchor
import me.kozakov.orrery.core.Selection

/** TEMPORARY: auto-playing tour used to record the README GIFs. */
@Suppress("LongMethod")
internal suspend fun DemoUiState.playTour() {
    val month = YearMonth(today.year, today.month)
    val initialEvents = events
    // The second Tuesday of this month: an occurrence of the RRULE board meeting.
    val boardDay = (8..14).map { LocalDate(today.year, today.month, it) }.first { it.dayOfWeek == DayOfWeek.TUESDAY }
    while (true) {
        // Marker the recording script waits for, so each GIF is exactly one loop.
        println("ORRERY_DEMO_LOOP")
        tab = DemoTab.Month
        events = initialEvents
        selectedDay = today
        weekMode = false
        stackedOverlap = false
        editing = false
        endpoint = EndpointOption.Diagonal
        preset = StylePreset.Rounded
        density = CalendarDensity.Comfortable
        workWeekOnly = false
        showPicker = false
        staysSelection.clear()
        monthState.scrollToMonth(month)
        delay(1500)
        selectedDay = boardDay
        delay(1600)
        monthState.animateScrollToMonth(month.plus(1, DateTimeUnit.MONTH))
        delay(1500)
        monthState.animateScrollToMonth(month)
        delay(600)

        tab = DemoTab.Agenda
        delay(1200)
        collapsible.animateTo(CollapsibleCalendarAnchor.TwoWeeks)
        delay(1100)
        collapsible.animateTo(CollapsibleCalendarAnchor.Week)
        delay(1100)
        agendaSelection.set(Selection(single = today.plus(2, DateTimeUnit.DAY)))
        delay(1100)
        collapsible.animateTo(CollapsibleCalendarAnchor.Month)
        agendaSelection.set(Selection(single = today))
        delay(1000)

        tab = DemoTab.Schedule
        delay(1300)
        stackedOverlap = true
        delay(1300)
        stackedOverlap = false
        editing = true
        delay(900)
        // The results the drag gestures report: create, move, then resize.
        events = events.withNewEvent(today, LocalTime(13, 0), LocalTime(14, 0))
        delay(1200)
        events = events.withRescheduled(events.last(), today, LocalTime(14, 30), LocalTime(15, 30))
        delay(1100)
        events = events.withRescheduled(events.last(), today, LocalTime(14, 30), LocalTime(16, 0))
        delay(1200)
        editing = false
        weekMode = true
        delay(1600)

        tab = DemoTab.Stays
        delay(900)
        staysSelection.set(
            Selection(rangeStart = today.plus(10, DateTimeUnit.DAY), rangeEnd = today.plus(14, DateTimeUnit.DAY)),
        )
        delay(1500)
        endpoint = EndpointOption.Split
        delay(1200)
        endpoint = EndpointOption.Full
        delay(1000)
        endpoint = EndpointOption.Diagonal
        showPicker = true
        delay(1700)
        showPicker = false
        delay(400)

        tab = DemoTab.Styles
        delay(1100)
        workWeekOnly = true
        delay(1300)
        preset = StylePreset.Segmented
        delay(1100)
        preset = StylePreset.Bordered
        density = CalendarDensity.Expanded
        delay(1300)
        preset = StylePreset.Default
        density = CalendarDensity.Compact
        delay(1100)
    }
}
