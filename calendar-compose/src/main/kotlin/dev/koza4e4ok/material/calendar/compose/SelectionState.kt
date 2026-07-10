package dev.koza4e4ok.material.calendar.compose

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import dev.koza4e4ok.material.calendar.core.Selection
import dev.koza4e4ok.material.calendar.core.SelectionEngine
import dev.koza4e4ok.material.calendar.core.SelectionEvent
import dev.koza4e4ok.material.calendar.core.SelectionMode
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.daysUntil
import kotlinx.datetime.plus

/** Snapshot-state wrapper around the core [SelectionEngine]. */
@Stable
public class CalendarSelectionState internal constructor(
    public val mode: SelectionMode,
    initialSelection: Selection,
    bounds: ClosedRange<LocalDate>?,
    private val interceptor: (LocalDate) -> Boolean,
    private val onEvent: (SelectionEvent) -> Unit,
) {
    private val engine = SelectionEngine(mode, bounds, interceptor)

    private var dragAnchor: LocalDate? = null

    public var selection: Selection by mutableStateOf(initialSelection)
        private set

    public fun click(date: LocalDate) {
        val result = engine.click(selection, date)
        selection = result.selection
        result.events.forEach(onEvent)
    }

    public fun clear() {
        selection = Selection.Empty
    }

    /** Begins a drag range selection at [date]. Only acts in [SelectionMode.Range]. */
    public fun dragStart(date: LocalDate) {
        if (mode !is SelectionMode.Range || interceptor(date)) return
        dragAnchor = date
        selection = Selection(rangeStart = date)
    }

    /** Live-updates the dragged range; start/end are kept ordered. */
    public fun dragUpdate(date: LocalDate) {
        val anchor = dragAnchor ?: return
        selection =
            if (date >= anchor) {
                Selection(rangeStart = anchor, rangeEnd = date)
            } else {
                Selection(rangeStart = date, rangeEnd = anchor)
            }
    }

    /** Ends the drag, validating min/max and intercepted dates; violations clear the end. */
    public fun dragEnd() {
        if (dragAnchor == null) return
        dragAnchor = null
        val range = selection.range ?: return
        val rangeMode = mode as? SelectionMode.Range ?: return
        val days = range.start.daysUntil(range.endInclusive) + 1
        val min = rangeMode.effectiveMin
        val max = rangeMode.effectiveMax
        val blocked =
            generateSequence(range.start) { it.plus(1, DateTimeUnit.DAY) }
                .takeWhile { it <= range.endInclusive }
                .firstOrNull(interceptor)
        when {
            min != null && days < min -> {
                onEvent(SelectionEvent.RangeTooShort(range.endInclusive, min))
                selection = Selection(rangeStart = range.start)
            }

            max != null && days > max -> {
                onEvent(SelectionEvent.RangeTooLong(range.endInclusive, max))
                selection = Selection(rangeStart = range.start)
            }

            blocked != null -> {
                onEvent(SelectionEvent.Intercepted(blocked))
                selection = Selection(rangeStart = range.start)
            }

            else -> {
                Unit
            }
        }
    }

    /** True for the single selection, a multi selection, or a range endpoint. */
    public fun isSelected(date: LocalDate): Boolean =
        date == selection.single ||
            date in selection.multi ||
            date == selection.rangeStart ||
            date == selection.rangeEnd

    /** True strictly inside a completed range (endpoints excluded). */
    public fun isInRange(date: LocalDate): Boolean {
        val range = selection.range ?: return false
        return date in range && date != range.start && date != range.endInclusive
    }

    internal companion object {
        internal fun saver(
            mode: SelectionMode,
            bounds: ClosedRange<LocalDate>?,
            interceptor: (LocalDate) -> Boolean,
            onEvent: (SelectionEvent) -> Unit,
        ): Saver<CalendarSelectionState, Any> =
            listSaver(
                save = { state ->
                    val s = state.selection
                    listOf(
                        s.single?.toString() ?: "",
                        s.rangeStart?.toString() ?: "",
                        s.rangeEnd?.toString() ?: "",
                        s.multi.joinToString(",") { it.toString() },
                    )
                },
                restore = { saved ->
                    fun date(v: String): LocalDate? = v.takeIf { it.isNotEmpty() }?.let(LocalDate::parse)
                    CalendarSelectionState(
                        mode = mode,
                        initialSelection =
                            Selection(
                                single = date(saved[0] as String),
                                rangeStart = date(saved[1] as String),
                                rangeEnd = date(saved[2] as String),
                                multi =
                                    (saved[3] as String)
                                        .split(",")
                                        .filter { it.isNotEmpty() }
                                        .map(LocalDate::parse)
                                        .toSet(),
                            ),
                        bounds = bounds,
                        interceptor = interceptor,
                        onEvent = onEvent,
                    )
                },
            )
    }
}

@Composable
public fun rememberCalendarSelectionState(
    mode: SelectionMode = SelectionMode.Single(),
    initialSelection: Selection = Selection.Empty,
    bounds: ClosedRange<LocalDate>? = null,
    interceptor: (LocalDate) -> Boolean = { false },
    onEvent: (SelectionEvent) -> Unit = {},
): CalendarSelectionState {
    val saver =
        remember(mode, bounds) {
            CalendarSelectionState.saver(mode, bounds, interceptor, onEvent)
        }
    return rememberSaveable(mode, bounds, saver = saver) {
        CalendarSelectionState(mode, initialSelection, bounds, interceptor, onEvent)
    }
}
