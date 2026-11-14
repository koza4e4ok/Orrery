package me.kozakov.orrery.widget

import androidx.compose.ui.graphics.Color
import me.kozakov.orrery.ExperimentalOrreryApi

/**
 * Colours of a calendar snapshot. [CalendarSnapshotDefaults.colors] derives them from a
 * Material 3 colour scheme with the same mapping as `CalendarDefaults.MiniMonth`.
 */
@ExperimentalOrreryApi
@Suppress("LongParameterList")
public class CalendarSnapshotColors(
    /** Background of the whole snapshot. */
    public val container: Color,
    /** Title row text. */
    public val title: Color,
    /** Weekday row text, agenda day headings and event times. */
    public val weekday: Color,
    /** Day numbers and agenda event titles. */
    public val day: Color,
    /** Day numbers of neighbouring months (`showOutsideDays`). */
    public val outsideDay: Color,
    /** Today mark: a filled circle, or a ring on a selected day. */
    public val today: Color,
    /** Today's day number on the filled circle. */
    public val todayContent: Color,
    /** Selected days and the ends of a selected range. */
    public val selection: Color,
    /** Day numbers and event dots on selected days. */
    public val selectionContent: Color,
    /** Band behind the days inside a selected range. */
    public val rangeBand: Color,
    /** Day numbers of holidays without their own colour. */
    public val holiday: Color,
    /** Event dots, and agenda colour bars of events without a colour. */
    public val eventDot: Color,
) {
    /** Returns a copy of these colours with the given fields replaced. */
    @Suppress("LongParameterList")
    public fun copy(
        container: Color = this.container,
        title: Color = this.title,
        weekday: Color = this.weekday,
        day: Color = this.day,
        outsideDay: Color = this.outsideDay,
        today: Color = this.today,
        todayContent: Color = this.todayContent,
        selection: Color = this.selection,
        selectionContent: Color = this.selectionContent,
        rangeBand: Color = this.rangeBand,
        holiday: Color = this.holiday,
        eventDot: Color = this.eventDot,
    ): CalendarSnapshotColors =
        CalendarSnapshotColors(
            container = container,
            title = title,
            weekday = weekday,
            day = day,
            outsideDay = outsideDay,
            today = today,
            todayContent = todayContent,
            selection = selection,
            selectionContent = selectionContent,
            rangeBand = rangeBand,
            holiday = holiday,
            eventDot = eventDot,
        )

    override fun equals(other: Any?): Boolean =
        other is CalendarSnapshotColors &&
            container == other.container &&
            title == other.title &&
            weekday == other.weekday &&
            day == other.day &&
            outsideDay == other.outsideDay &&
            today == other.today &&
            todayContent == other.todayContent &&
            selection == other.selection &&
            selectionContent == other.selectionContent &&
            rangeBand == other.rangeBand &&
            holiday == other.holiday &&
            eventDot == other.eventDot

    override fun hashCode(): Int {
        var result = container.hashCode()
        result = 31 * result + title.hashCode()
        result = 31 * result + weekday.hashCode()
        result = 31 * result + day.hashCode()
        result = 31 * result + outsideDay.hashCode()
        result = 31 * result + today.hashCode()
        result = 31 * result + todayContent.hashCode()
        result = 31 * result + selection.hashCode()
        result = 31 * result + selectionContent.hashCode()
        result = 31 * result + rangeBand.hashCode()
        result = 31 * result + holiday.hashCode()
        result = 31 * result + eventDot.hashCode()
        return result
    }

    override fun toString(): String =
        "CalendarSnapshotColors(container=$container, title=$title, weekday=$weekday, day=$day, " +
            "outsideDay=$outsideDay, today=$today, todayContent=$todayContent, selection=$selection, " +
            "selectionContent=$selectionContent, rangeBand=$rangeBand, holiday=$holiday, eventDot=$eventDot)"
}
