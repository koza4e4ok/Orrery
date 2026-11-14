package me.kozakov.orrery.widget

import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import me.kozakov.orrery.ExperimentalOrreryApi
import me.kozakov.orrery.core.CalendarEvent
import me.kozakov.orrery.core.DayInfoProvider
import me.kozakov.orrery.core.Selection

/**
 * What a calendar snapshot shows and how: the [range] plus the day marks drawn on it.
 *
 * The snapshot never reads the clock or the locale's first day of the week: pass [today]
 * and [firstDayOfWeek] explicitly, so the same spec always draws the same picture.
 * This is a plain class with a hand-written [copy], so new fields can be added later
 * without breaking callers.
 */
@ExperimentalOrreryApi
@Suppress("LongParameterList")
public class CalendarSnapshotSpec(
    /** What to draw: a month, a week or an agenda strip. */
    public val range: CalendarSnapshotRange,
    /** The current date, highlighted when it is inside [range]; null for no highlight. */
    public val today: LocalDate? = null,
    /** Selected dates (single, range or multiple), drawn like `MiniMonth`'s selection. */
    public val selection: Selection = Selection.Empty,
    /**
     * Per-day information. A day is tinted with [me.kozakov.orrery.core.DayInfo.colorArgb] when it
     * is set, else with the holiday colour when [me.kozakov.orrery.core.DayInfo.isHoliday] is true.
     * Labels are not drawn.
     */
    public val dayInfo: DayInfoProvider? = null,
    /** Number of events per day, drawn as one to three dots; counts above three draw three. */
    public val eventCounts: Map<LocalDate, Int> = emptyMap(),
    /**
     * Events of the [CalendarSnapshotRange.Agenda] strip; ignored by the month and week ranges.
     * Recurring events are not expanded here: pass occurrences, for example from
     * [me.kozakov.orrery.core.occurrencesIn] or [me.kozakov.orrery.core.expandRecurring].
     */
    public val agendaEvents: List<CalendarEvent<*>> = emptyList(),
    /** Weekday of the first column of month and week grids, and the start of [CalendarSnapshotRange.Week]. */
    public val firstDayOfWeek: DayOfWeek = DayOfWeek.MONDAY,
    /** Whether to draw the title row; it is also dropped when the output is too small for it. */
    public val showTitle: Boolean = true,
    /** Whether to draw the weekday row of month and week grids; it is also dropped when it does not fit. */
    public val showWeekdayLabels: Boolean = true,
    /** Month range only: draw the leading and trailing days of the neighbouring months, without marks. */
    public val showOutsideDays: Boolean = false,
) {
    /** Returns a copy of this spec with the given fields replaced. */
    @Suppress("LongParameterList")
    public fun copy(
        range: CalendarSnapshotRange = this.range,
        today: LocalDate? = this.today,
        selection: Selection = this.selection,
        dayInfo: DayInfoProvider? = this.dayInfo,
        eventCounts: Map<LocalDate, Int> = this.eventCounts,
        agendaEvents: List<CalendarEvent<*>> = this.agendaEvents,
        firstDayOfWeek: DayOfWeek = this.firstDayOfWeek,
        showTitle: Boolean = this.showTitle,
        showWeekdayLabels: Boolean = this.showWeekdayLabels,
        showOutsideDays: Boolean = this.showOutsideDays,
    ): CalendarSnapshotSpec =
        CalendarSnapshotSpec(
            range = range,
            today = today,
            selection = selection,
            dayInfo = dayInfo,
            eventCounts = eventCounts,
            agendaEvents = agendaEvents,
            firstDayOfWeek = firstDayOfWeek,
            showTitle = showTitle,
            showWeekdayLabels = showWeekdayLabels,
            showOutsideDays = showOutsideDays,
        )

    override fun equals(other: Any?): Boolean =
        other is CalendarSnapshotSpec &&
            range == other.range &&
            today == other.today &&
            selection == other.selection &&
            dayInfo == other.dayInfo &&
            eventCounts == other.eventCounts &&
            agendaEvents == other.agendaEvents &&
            firstDayOfWeek == other.firstDayOfWeek &&
            showTitle == other.showTitle &&
            showWeekdayLabels == other.showWeekdayLabels &&
            showOutsideDays == other.showOutsideDays

    override fun hashCode(): Int {
        var result = range.hashCode()
        result = 31 * result + today.hashCode()
        result = 31 * result + selection.hashCode()
        result = 31 * result + dayInfo.hashCode()
        result = 31 * result + eventCounts.hashCode()
        result = 31 * result + agendaEvents.hashCode()
        result = 31 * result + firstDayOfWeek.hashCode()
        result = 31 * result + showTitle.hashCode()
        result = 31 * result + showWeekdayLabels.hashCode()
        result = 31 * result + showOutsideDays.hashCode()
        return result
    }

    override fun toString(): String =
        "CalendarSnapshotSpec(range=$range, today=$today, selection=$selection, dayInfo=$dayInfo, " +
            "eventCounts=$eventCounts, agendaEvents=${agendaEvents.size} events, firstDayOfWeek=$firstDayOfWeek, " +
            "showTitle=$showTitle, showWeekdayLabels=$showWeekdayLabels, showOutsideDays=$showOutsideDays)"
}
