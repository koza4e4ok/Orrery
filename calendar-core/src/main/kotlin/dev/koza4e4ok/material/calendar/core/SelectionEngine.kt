package dev.koza4e4ok.material.calendar.core

import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.daysUntil
import kotlinx.datetime.plus

/**
 * Pure selection state machine. [click] never mutates; it returns the new
 * [Selection] plus any violation events. Bounds and disabled dates are
 * checked before mode-specific handling, matching the original library's
 * out-of-range and OnCalendarInterceptListener behavior.
 */
public class SelectionEngine(
    private val mode: SelectionMode,
    private val bounds: ClosedRange<LocalDate>? = null,
    private val disabled: DisabledDates = DisabledDates.None,
) {
    public fun click(
        current: Selection,
        date: LocalDate,
    ): SelectionResult {
        if (bounds != null && date !in bounds) {
            return SelectionResult(current, listOf(SelectionEvent.OutOfRange(date)))
        }
        if (date in disabled) {
            return SelectionResult(current, listOf(SelectionEvent.Intercepted(date)))
        }
        return when (mode) {
            SelectionMode.None -> SelectionResult(current, emptyList())
            is SelectionMode.Single -> SelectionResult(current.copy(single = date), emptyList())
            is SelectionMode.Multi -> clickMulti(mode, current, date)
            is SelectionMode.Range -> clickRange(mode, current, date)
        }
    }

    private fun clickMulti(
        mode: SelectionMode.Multi,
        current: Selection,
        date: LocalDate,
    ): SelectionResult =
        when {
            date in current.multi -> {
                SelectionResult(current.copy(multi = current.multi - date), emptyList())
            }

            mode.maxCount != null && current.multi.size >= mode.maxCount -> {
                SelectionResult(current, listOf(SelectionEvent.TooManyDays(date, mode.maxCount)))
            }

            else -> {
                SelectionResult(current.copy(multi = current.multi + date), emptyList())
            }
        }

    private fun clickRange(
        mode: SelectionMode.Range,
        current: Selection,
        date: LocalDate,
    ): SelectionResult {
        val start = current.rangeStart
        if (start == null || current.rangeEnd != null || date < start) {
            return SelectionResult(current.copy(rangeStart = date, rangeEnd = null), emptyList())
        }
        val days = start.daysUntil(date) + 1
        val min = mode.effectiveMin
        if (min != null && days < min) {
            return SelectionResult(current, listOf(SelectionEvent.RangeTooShort(date, min)))
        }
        val max = mode.effectiveMax
        if (max != null && days > max) {
            return SelectionResult(current, listOf(SelectionEvent.RangeTooLong(date, max)))
        }
        var cursor: LocalDate = start
        while (cursor <= date) {
            if (cursor in disabled) {
                return SelectionResult(current, listOf(SelectionEvent.Intercepted(cursor)))
            }
            cursor = cursor.plus(1, DateTimeUnit.DAY)
        }
        return SelectionResult(current.copy(rangeEnd = date), emptyList())
    }
}
