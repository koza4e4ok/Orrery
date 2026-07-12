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

    /**
     * Validates [proposed] the way clicks are validated and returns either
     * the proposed selection (no events) or [current] plus the first
     * violation. The proposal's shape must match the mode ([Selection.Empty]
     * matches every mode); a mismatch throws [IllegalArgumentException].
     */
    public fun replace(
        current: Selection,
        proposed: Selection,
    ): SelectionResult {
        if (proposed == Selection.Empty) return SelectionResult(proposed, emptyList())
        requireShapeMatches(proposed)
        val violation = firstViolation(proposed)
        return if (violation == null) {
            SelectionResult(proposed, emptyList())
        } else {
            SelectionResult(current, listOf(violation))
        }
    }

    private fun requireShapeMatches(proposed: Selection) {
        val matches =
            when (mode) {
                SelectionMode.None -> {
                    false
                }

                is SelectionMode.Single -> {
                    proposed.single != null && proposed.rangeStart == null && proposed.multi.isEmpty()
                }

                is SelectionMode.Range -> {
                    proposed.single == null && proposed.rangeStart != null && proposed.multi.isEmpty()
                }

                is SelectionMode.Multi -> {
                    proposed.single == null && proposed.rangeStart == null && proposed.multi.isNotEmpty()
                }
            }
        require(matches) { "Selection shape does not match mode $mode" }
    }

    private fun firstViolation(proposed: Selection): SelectionEvent? {
        val endpointDates =
            listOfNotNull(proposed.single, proposed.rangeStart, proposed.rangeEnd) + proposed.multi
        val outOfBounds = endpointDates.firstOrNull { bounds != null && it !in bounds }
        if (outOfBounds != null) return SelectionEvent.OutOfRange(outOfBounds)
        return rangeLengthViolation(proposed)
            ?: disabledViolation(proposed)
            ?: multiCountViolation(proposed)
    }

    private fun rangeLengthViolation(proposed: Selection): SelectionEvent? {
        val range = proposed.range ?: return null
        val rangeMode = mode as? SelectionMode.Range ?: return null
        val days = range.start.daysUntil(range.endInclusive) + 1
        val min = rangeMode.effectiveMin
        if (min != null && days < min) return SelectionEvent.RangeTooShort(range.endInclusive, min)
        val max = rangeMode.effectiveMax
        if (max != null && days > max) return SelectionEvent.RangeTooLong(range.endInclusive, max)
        return null
    }

    private fun disabledViolation(proposed: Selection): SelectionEvent? {
        val range = proposed.range
        val blocked =
            if (range != null) {
                generateSequence(range.start) { it.plus(1, DateTimeUnit.DAY) }
                    .takeWhile { it <= range.endInclusive }
                    .firstOrNull { it in disabled }
            } else {
                (listOfNotNull(proposed.single, proposed.rangeStart) + proposed.multi)
                    .firstOrNull { it in disabled }
            }
        return blocked?.let { SelectionEvent.Intercepted(it) }
    }

    private fun multiCountViolation(proposed: Selection): SelectionEvent? {
        val maxCount = (mode as? SelectionMode.Multi)?.maxCount ?: return null
        return if (proposed.multi.size > maxCount) {
            SelectionEvent.TooManyDays(proposed.multi.max(), maxCount)
        } else {
            null
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
