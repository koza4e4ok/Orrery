package me.kozakov.orrery.demo

import kotlinx.coroutines.delay
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.YearMonth
import kotlinx.datetime.plus
import me.kozakov.orrery.compose.CalendarDensity
import me.kozakov.orrery.compose.CollapsibleCalendarAnchor
import me.kozakov.orrery.core.Selection

/** TEMPORARY: auto-playing tour used to record the README GIFs. */
internal suspend fun DemoUiState.playTour() {
    val month = YearMonth(today.year, today.month)
    while (true) {
        // Marker the recording script waits for, so each GIF is exactly one loop.
        println("ORRERY_DEMO_LOOP")
        tab = DemoTab.Month
        selectedDay = today
        weekMode = false
        stackedOverlap = false
        endpoint = EndpointOption.Diagonal
        preset = StylePreset.Rounded
        density = CalendarDensity.Comfortable
        workWeekOnly = false
        staysSelection.clear()
        monthState.scrollToMonth(month)
        delay(1800)
        selectedDay = today.plus(3, DateTimeUnit.DAY)
        delay(1400)
        monthState.animateScrollToMonth(month.plus(1, DateTimeUnit.MONTH))
        delay(2000)
        monthState.animateScrollToMonth(month)
        delay(800)

        tab = DemoTab.Agenda
        delay(1400)
        collapsible.animateTo(CollapsibleCalendarAnchor.TwoWeeks)
        delay(1300)
        collapsible.animateTo(CollapsibleCalendarAnchor.Week)
        delay(1300)
        agendaSelection.set(Selection(single = today.plus(2, DateTimeUnit.DAY)))
        delay(1300)
        collapsible.animateTo(CollapsibleCalendarAnchor.Month)
        agendaSelection.set(Selection(single = today))
        delay(1200)

        tab = DemoTab.Schedule
        delay(1500)
        stackedOverlap = true
        delay(1600)
        weekMode = true
        delay(1800)
        stackedOverlap = false
        delay(1400)

        tab = DemoTab.Stays
        delay(1000)
        staysSelection.set(
            Selection(rangeStart = today.plus(10, DateTimeUnit.DAY), rangeEnd = today.plus(14, DateTimeUnit.DAY)),
        )
        delay(1800)
        endpoint = EndpointOption.Split
        delay(1500)
        endpoint = EndpointOption.Full
        delay(1200)
        endpoint = EndpointOption.Diagonal
        delay(800)

        tab = DemoTab.Styles
        delay(1300)
        workWeekOnly = true
        delay(1500)
        preset = StylePreset.Segmented
        delay(1300)
        preset = StylePreset.Bordered
        density = CalendarDensity.Expanded
        delay(1500)
        preset = StylePreset.Default
        density = CalendarDensity.Compact
        delay(1300)
    }
}
