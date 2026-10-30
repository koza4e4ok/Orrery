package me.kozakov.orrery.compose

import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusManager
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import me.kozakov.orrery.core.CalendarDay
import me.kozakov.orrery.core.CalendarMonth
import me.kozakov.orrery.core.CalendarWeek
import me.kozakov.orrery.core.DayPosition
import me.kozakov.orrery.core.SelectionMode

internal val WeekNumberColumnWidth = 32.dp

@Composable
internal fun MonthContent(
    month: CalendarMonth,
    monthHeader: (@Composable ColumnScope.(CalendarMonth) -> Unit)?,
    dayContent: @Composable BoxScope.(CalendarDay) -> Unit,
    modifier: Modifier = Modifier,
    weekNumber: (@Composable (CalendarWeek) -> Unit)? = null,
    dragSelection: CalendarSelectionState? = null,
    hapticsEnabled: Boolean = true,
    keyboardNavigation: CalendarKeyboardNavigation? = null,
    rowHeight: CalendarRowHeight = CalendarRowHeight.WrapContent,
    fillBoundedHeight: Boolean = false,
    visibleDaysOfWeek: Set<DayOfWeek> = ALL_DAYS_OF_WEEK_SET,
) {
    val haptics = LocalHapticFeedback.current
    Column(modifier) {
        monthHeader?.invoke(this, month)
        val gridWeightModifier =
            if (fillBoundedHeight && rowHeight is CalendarRowHeight.FillViewport) {
                Modifier.weight(1f)
            } else {
                Modifier
            }
        Column(
            gridWeightModifier.then(
                if (dragSelection != null && dragSelection.supportsDrag) {
                    Modifier.pointerInput(month, weekNumber != null, hapticsEnabled) {
                        val leading = if (weekNumber != null) WeekNumberColumnWidth.toPx() else 0f

                        fun dayAt(offset: Offset): CalendarDay? {
                            val rows = month.weeks.size
                            if (rows == 0) return null
                            val rowHeightPx = size.height.toFloat() / rows
                            val row = (offset.y / rowHeightPx).toInt().coerceIn(0, rows - 1)
                            val weekDays = month.weeks[row].days
                            val cols = weekDays.size
                            if (cols == 0) return null
                            val colWidth = (size.width - leading) / cols.toFloat()
                            val col = ((offset.x - leading) / colWidth).toInt().coerceIn(0, cols - 1)
                            return weekDays[col]
                        }

                        var lastDragDate: LocalDate? = null
                        detectDragGesturesAfterLongPress(
                            onDragStart = { offset ->
                                dayAt(offset)?.let {
                                    if (hapticsEnabled) haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                    lastDragDate = it.date
                                    dragSelection.dragStart(it.date)
                                }
                            },
                            onDrag = { change, _ ->
                                change.consume()
                                dayAt(change.position)?.let {
                                    if (hapticsEnabled && it.date != lastDragDate) {
                                        haptics.performHapticFeedback(HapticFeedbackType.SegmentFrequentTick)
                                    }
                                    lastDragDate = it.date
                                    dragSelection.dragUpdate(it.date)
                                }
                            },
                            onDragEnd = { dragSelection.dragEnd() },
                            onDragCancel = { dragSelection.dragEnd() },
                        )
                    }
                } else {
                    Modifier
                },
            ),
        ) {
            val rowCount = month.weeks.size
            month.weeks.forEachIndexed { row, week ->
                val rowModifier =
                    when (rowHeight) {
                        CalendarRowHeight.WrapContent -> {
                            Modifier.fillMaxWidth()
                        }

                        is CalendarRowHeight.Fixed -> {
                            Modifier.fillMaxWidth().height(rowHeight.height)
                        }

                        is CalendarRowHeight.FillViewport -> {
                            if (fillBoundedHeight) {
                                Modifier
                                    .fillMaxWidth()
                                    .weight(1f)
                                    .heightIn(min = rowHeight.minRowHeight)
                            } else {
                                Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = rowHeight.minRowHeight)
                            }
                        }
                    }
                Row(rowModifier, verticalAlignment = Alignment.CenterVertically) {
                    if (weekNumber != null) {
                        Box(Modifier.width(WeekNumberColumnWidth), contentAlignment = Alignment.Center) {
                            weekNumber(week)
                        }
                    }
                    val colCount = week.days.size
                    val rowFocusRequesters = rememberRowFocusRequesters(keyboardNavigation != null, colCount)
                    week.days.forEachIndexed { col, day ->
                        DayCell(
                            day = day,
                            col = col,
                            colCount = colCount,
                            keyboard = keyboardNavigation,
                            modifier = Modifier.weight(1f),
                            dayContent = dayContent,
                            row = row,
                            rowCount = rowCount,
                            rowFocusRequesters = rowFocusRequesters,
                            selectionState = dragSelection,
                            // Paging always scrolls to the page of the target's month, where the
                            // target is an in-month date; out-dates of other pages never take it.
                            acceptsPendingFocus = day.position == DayPosition.MonthDate,
                            visibleDaysOfWeek = visibleDaysOfWeek,
                        )
                    }
                }
            }
        }
    }
}

/** One [FocusRequester] per column of a week row, for Home/End; empty without keyboard navigation. */
@Composable
internal fun rememberRowFocusRequesters(
    enabled: Boolean,
    colCount: Int = DAYS_PER_WEEK,
): List<FocusRequester> =
    remember(enabled, colCount) { if (enabled) List(colCount) { FocusRequester() } else emptyList() }

private const val DAYS_PER_WEEK = 7

/**
 * Day-cell wrapper adding keyboard navigation on top of native Compose
 * focus search (which handles plain arrow moves inside a page):
 * - Left/Right at a row edge (col 0 / colCount - 1) and Up/Down at the top or
 *   bottom row page the calendar; focus lands on the next/previous visible
 *   day or ±7 days once its cell is composed. RTL swaps which horizontal key
 *   means "toward the next day".
 * - PageUp/PageDown page one month back/forward, or one year with Shift.
 * - Home/End focus the first/last cell of the current week row.
 * - Shift+Arrow with a [SelectionMode.Range] [selectionState] extends the
 *   range from its other endpoint (or this day) to the target date and
 *   moves focus there.
 */
@Composable
internal fun DayCell(
    day: CalendarDay,
    col: Int,
    keyboard: CalendarKeyboardNavigation?,
    modifier: Modifier,
    dayContent: @Composable BoxScope.(CalendarDay) -> Unit,
    row: Int = 0,
    rowCount: Int = 1,
    colCount: Int = DAYS_PER_WEEK,
    rowFocusRequesters: List<FocusRequester> = emptyList(),
    selectionState: CalendarSelectionState? = null,
    acceptsPendingFocus: Boolean = true,
    visibleDaysOfWeek: Set<DayOfWeek> = ALL_DAYS_OF_WEEK_SET,
) {
    if (keyboard == null) {
        Box(modifier) { dayContent(day) }
        return
    }
    val isRtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val focusManager = LocalFocusManager.current
    val ownRequester = remember { FocusRequester() }
    val focusRequester = rowFocusRequesters.getOrNull(col) ?: ownRequester
    Box(
        modifier
            .focusRequester(focusRequester)
            .onPreviewKeyEvent { event ->
                event.type == KeyEventType.KeyDown &&
                    DayKeyContext(
                        day = day,
                        position =
                            GridPosition(
                                row = row,
                                col = col,
                                rowCount = rowCount,
                                colCount = colCount,
                                isRtl = isRtl,
                            ),
                        keyboard = keyboard,
                        focusManager = focusManager,
                        rowFocusRequesters = rowFocusRequesters,
                        selectionState = selectionState,
                        visibleDaysOfWeek = visibleDaysOfWeek,
                    ).handle(event)
            },
    ) { dayContent(day) }
    LaunchedEffect(keyboard.pendingFocus.value) {
        if (acceptsPendingFocus && keyboard.pendingFocus.value == day.date) {
            focusRequester.requestFocus()
            keyboard.pendingFocus.value = null
        }
    }
}

/** A day cell's place in its page's grid. */
private class GridPosition(
    val row: Int,
    val col: Int,
    val rowCount: Int,
    val colCount: Int,
    val isRtl: Boolean,
)

/** Everything a day cell needs to react to a key press. */
private class DayKeyContext(
    val day: CalendarDay,
    val position: GridPosition,
    val keyboard: CalendarKeyboardNavigation,
    val focusManager: FocusManager,
    val rowFocusRequesters: List<FocusRequester>,
    val selectionState: CalendarSelectionState?,
    val visibleDaysOfWeek: Set<DayOfWeek>,
)

/** Handles one key-down on a day cell; returns true when the event is consumed. */
private fun DayKeyContext.handle(event: KeyEvent): Boolean =
    when (event.key) {
        Key.PageUp, Key.PageDown -> pageByPeriod(forward = event.key == Key.PageDown, byYear = event.isShiftPressed)
        Key.MoveHome, Key.MoveEnd -> focusRowEdge(start = event.key == Key.MoveHome)
        else -> moveByArrow(event)
    }

/** PageUp/PageDown: one month (or one year) back/forward, keeping a visible day near the day of month. */
private fun DayKeyContext.pageByPeriod(
    forward: Boolean,
    byYear: Boolean,
): Boolean {
    val unit = if (byYear) DateTimeUnit.YEAR else DateTimeUnit.MONTH
    val rawTarget = if (forward) day.date.plus(1, unit) else day.date.minus(1, unit)
    val target =
        if (rawTarget.dayOfWeek in visibleDaysOfWeek) {
            rawTarget
        } else {
            stepToVisibleDate(rawTarget, if (forward) 1 else -1, visibleDaysOfWeek)
        }
    keyboard.page(target)
    return true
}

/** Home/End: focus the first or last cell of this week row. */
private fun DayKeyContext.focusRowEdge(start: Boolean): Boolean {
    val requester = if (start) rowFocusRequesters.firstOrNull() else rowFocusRequesters.lastOrNull()
    if (requester == null) return false
    requester.requestFocus()
    return true
}

/**
 * Arrows: page at the grid edges; with Shift in range mode, also extend
 * the range. Plain arrows inside the grid fall through to focus search.
 */
private fun DayKeyContext.moveByArrow(event: KeyEvent): Boolean {
    val days = position.arrowDays(event.key) ?: return false
    val atEdge = position.isAtEdge(days)
    val target =
        if (days == 1 || days == -1) {
            stepToVisibleDate(day.date, days, visibleDaysOfWeek)
        } else {
            day.date.plus(days, DateTimeUnit.DAY)
        }
    val rangeState = selectionState?.takeIf { event.isShiftPressed && it.mode is SelectionMode.Range }
    rangeState?.extendRange(anchor = rangeAnchor(rangeState, day.date), target = target)
    return when {
        atEdge -> {
            keyboard.page(target)
            true
        }

        rangeState != null -> {
            // Shift+Arrow is consumed here, so move focus the way the plain arrow would.
            focusManager.moveFocus(event.focusDirection())
            true
        }

        else -> {
            false
        }
    }
}

private fun stepToVisibleDate(
    from: LocalDate,
    direction: Int,
    visibleDaysOfWeek: Set<DayOfWeek>,
): LocalDate {
    for (step in 1..DAYS_PER_WEEK) {
        val candidate = from.plus(step * direction, DateTimeUnit.DAY)
        if (candidate.dayOfWeek in visibleDaysOfWeek) {
            return candidate
        }
    }
    return from.plus(direction, DateTimeUnit.DAY)
}

/** Day offset of an arrow [key] (RTL swaps the horizontal keys), or null for other keys. */
private fun GridPosition.arrowDays(key: Key): Int? {
    val towardNext = if (isRtl) Key.DirectionLeft else Key.DirectionRight
    val towardPrevious = if (isRtl) Key.DirectionRight else Key.DirectionLeft
    return when (key) {
        towardNext -> 1
        towardPrevious -> -1
        Key.DirectionUp -> -DAYS_PER_WEEK
        Key.DirectionDown -> DAYS_PER_WEEK
        else -> null
    }
}

/** True when moving by [days] (±1 or ±7) leaves this page's grid. */
private fun GridPosition.isAtEdge(days: Int): Boolean =
    when (days) {
        1 -> col >= colCount - 1
        -1 -> col <= 0
        -DAYS_PER_WEEK -> row == 0
        else -> row == rowCount - 1
    }

/** The physical focus-search direction of an arrow key. */
private fun KeyEvent.focusDirection(): FocusDirection =
    when (key) {
        Key.DirectionLeft -> FocusDirection.Left
        Key.DirectionRight -> FocusDirection.Right
        Key.DirectionUp -> FocusDirection.Up
        else -> FocusDirection.Down
    }

/**
 * The fixed end of a keyboard range extension: the endpoint opposite the
 * focused [date] when it is one, else the range start, else [date].
 */
private fun rangeAnchor(
    selectionState: CalendarSelectionState,
    date: LocalDate,
): LocalDate {
    val start = selectionState.selection.rangeStart ?: return date
    val end = selectionState.selection.rangeEnd
    return if (end != null && date == start) end else start
}
