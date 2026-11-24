package me.kozakov.orrery.compose

import kotlinx.datetime.LocalDate
import me.kozakov.orrery.core.DayInfo
import me.kozakov.orrery.core.DayPosition

/**
 * What a [DefaultDay] cell reports to accessibility services, passed to the
 * `describeDay` parameter of [DefaultDay].
 *
 * [contentDescription] is the default text for "what this cell is": the full date
 * with the weekday, the [DayInfo.label] when the cell shows it, and the event count
 * of the built-in event decorators. [DayDecorators.stackedBars] is not counted: its
 * bars are colour markers that need not be events, so an app that wants them
 * announced adds the text in `describeDay`. [stateDescription] joins the states that apply
 * (range start, range end, in range, selected, today, holiday, unavailable) with
 * ", ", or is empty when none applies.
 *
 * [info] is the cell's [DayInfo] even when its label is hidden (for example by
 * [CalendarDensity.Compact]), so a `describeDay` hook can announce it.
 *
 * @property date The day's date.
 * @property position Whether the date belongs to the rendered month.
 * @property isToday The date is today.
 * @property isSelected The selection state reports the date as selected.
 * @property isRangeStart The date starts the selected range.
 * @property isRangeEnd The date ends the selected range.
 * @property isInRange The date lies between the range endpoints.
 * @property isAvailable The cell is enabled and the date is not disabled.
 * @property info Metadata passed to [DefaultDay], or null.
 * @property eventCount Events reported by the built-in event decorators (event dots,
 *   spanning event bars and badges; the largest count when several are set), 0 when none.
 *   [DayDecorators.stackedBars] does not contribute.
 * @property contentDescription The default content description.
 * @property stateDescription The state description, "" when no state applies.
 */
public class DayAnnouncement internal constructor(
    public val date: LocalDate,
    public val position: DayPosition,
    public val isToday: Boolean,
    public val isSelected: Boolean,
    public val isRangeStart: Boolean,
    public val isRangeEnd: Boolean,
    public val isInRange: Boolean,
    public val isAvailable: Boolean,
    public val info: DayInfo?,
    public val eventCount: Int,
    public val contentDescription: String,
    public val stateDescription: String,
)

/**
 * Builds the [DayAnnouncement] of a day cell. Every applicable state contributes
 * its string independently, so no state hides another; "selected" is left out
 * when a range state already implies it. [dateText] is the full date with the
 * weekday; [labelVisible] is false when the cell does not draw [DayInfo.label].
 */
@Suppress("LongParameterList")
internal fun dayAnnouncement(
    date: LocalDate,
    position: DayPosition,
    dateText: String,
    isToday: Boolean,
    isSelected: Boolean,
    isRangeStart: Boolean,
    isRangeEnd: Boolean,
    isInRange: Boolean,
    isAvailable: Boolean,
    info: DayInfo?,
    labelVisible: Boolean,
    eventCount: Int,
    strings: CalendarDayStrings,
): DayAnnouncement {
    val label = info?.label?.trim()?.takeIf { labelVisible && it.isNotEmpty() }
    val events = eventCount.coerceAtLeast(0)
    val content =
        listOfNotNull(
            dateText,
            label,
            if (events > 0) strings.events.formatTemplate(events) else null,
        ).joinToString(", ")
    val impliedByRange = isRangeStart || isRangeEnd || isInRange
    val state =
        listOfNotNull(
            strings.rangeStart.takeIf { isRangeStart },
            strings.rangeEnd.takeIf { isRangeEnd },
            strings.inRange.takeIf { isInRange },
            strings.selected.takeIf { isSelected && !impliedByRange },
            strings.today.takeIf { isToday },
            strings.holiday.takeIf { info?.isHoliday == true },
            strings.unavailable.takeIf { !isAvailable },
        ).joinToString(", ")
    return DayAnnouncement(
        date = date,
        position = position,
        isToday = isToday,
        isSelected = isSelected,
        isRangeStart = isRangeStart,
        isRangeEnd = isRangeEnd,
        isInRange = isInRange,
        isAvailable = isAvailable,
        info = info,
        eventCount = events,
        contentDescription = content,
        stateDescription = state,
    )
}
