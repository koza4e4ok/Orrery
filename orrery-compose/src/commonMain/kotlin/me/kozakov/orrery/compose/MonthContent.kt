@file:Suppress("TooManyFunctions")

package me.kozakov.orrery.compose

import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import me.kozakov.orrery.core.CalendarDay
import me.kozakov.orrery.core.CalendarMonth
import me.kozakov.orrery.core.CalendarWeek
import me.kozakov.orrery.core.DayPosition
import me.kozakov.orrery.core.SelectionMode

internal val WeekNumberColumnWidth = 32.dp

/**
 * One month grid. [weekOverlay] is drawn over each week row's day columns
 * (not the week-number column) in a [BoxScope] sized to the row, which grows
 * when the overlay is taller; [weekFooter] goes below each week row and
 * [monthFooter] below the whole grid.
 */
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
    weekOverlay: (@Composable BoxScope.(CalendarWeek) -> Unit)? = null,
    weekFooter: (@Composable ColumnScope.(CalendarWeek) -> Unit)? = null,
    monthFooter: (@Composable ColumnScope.(CalendarMonth) -> Unit)? = null,
) {
    val haptics = LocalHapticFeedback.current
    val isRtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    // Week rows stop being uniform once overlays or footers add height, so drag
    // hit-testing then looks rows up by their measured tops.
    val unevenRows = weekOverlay != null || weekFooter != null
    val rowTops = remember(month.weeks.size) { IntArray(month.weeks.size) }
    Column(modifier) {
        monthHeader?.invoke(this, month)
        Column(
            Modifier.then(
                if (dragSelection != null && dragSelection.supportsDrag) {
                    Modifier.pointerInput(month, weekNumber != null, hapticsEnabled, unevenRows, isRtl) {
                        val leading = if (weekNumber != null) WeekNumberColumnWidth.toPx() else 0f

                        fun dayAt(offset: Offset): CalendarDay? {
                            val rows = month.weeks.size
                            if (rows == 0) return null
                            val rowHeight = size.height.toFloat() / rows
                            val colWidth = (size.width - leading) / 7f
                            val row =
                                if (unevenRows) {
                                    rowTops.indexOfLast { it <= offset.y }.coerceAtLeast(0)
                                } else {
                                    (offset.y / rowHeight).toInt().coerceIn(0, rows - 1)
                                }
                            val rawX = if (isRtl) size.width - leading - offset.x else offset.x - leading
                            val col = (rawX / colWidth).toInt().coerceIn(0, 6)
                            return month.weeks[row].days[col]
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
                    if (unevenRows) {
                        Modifier.onPlaced { rowTops[row] = it.positionInParent().y.toInt() }
                    } else {
                        Modifier
                    }
                Row(rowModifier.fillMaxWidth()) {
                    if (weekNumber != null) {
                        Box(Modifier.width(WeekNumberColumnWidth), contentAlignment = Alignment.Center) {
                            weekNumber(week)
                        }
                    }
                    val rowFocusRequesters = rememberRowFocusRequesters(keyboardNavigation != null)
                    WeekDays(week, weekOverlay) {
                        week.days.forEachIndexed { col, day ->
                            DayCell(
                                day = day,
                                col = col,
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
                            )
                        }
                    }
                }
                weekFooter?.invoke(this, week)
            }
        }
        monthFooter?.invoke(this, month)
    }
}

/**
 * The day cells of one week row ([days] lays them out with `weight(1f)`).
 * Without a [weekOverlay] they go straight into the enclosing row, exactly as
 * before the slot existed; with one they share a [Box] with the overlay.
 */
@Composable
internal fun RowScope.WeekDays(
    week: CalendarWeek,
    weekOverlay: (@Composable BoxScope.(CalendarWeek) -> Unit)?,
    days: @Composable RowScope.() -> Unit,
) {
    if (weekOverlay == null) {
        days()
    } else {
        Box(Modifier.weight(1f)) {
            Row(Modifier.fillMaxWidth(), content = days)
            weekOverlay.invoke(this, week)
        }
    }
}

/** One [FocusRequester] per column of a week row, for Home/End; empty without keyboard navigation. */
@Composable
internal fun rememberRowFocusRequesters(enabled: Boolean): List<FocusRequester> =
    remember(enabled) { if (enabled) List(DAYS_PER_WEEK) { FocusRequester() } else emptyList() }

private const val DAYS_PER_WEEK = 7

/**
 * Day-cell wrapper adding keyboard navigation on top of native Compose
 * focus search (which handles plain arrow moves inside a page):
 * - Left/Right at a row edge (col 0 / col 6) and Up/Down at the top or
 *   bottom row page the calendar; focus lands on the date ±1 / ±7 days
 *   once its cell is composed. RTL swaps which horizontal key means
 *   "toward the next day".
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
    rowFocusRequesters: List<FocusRequester> = emptyList(),
    selectionState: CalendarSelectionState? = null,
    acceptsPendingFocus: Boolean = true,
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
                        position = GridPosition(row = row, col = col, rowCount = rowCount, isRtl = isRtl),
                        keyboard = keyboard,
                        focusManager = focusManager,
                        rowFocusRequesters = rowFocusRequesters,
                        selectionState = selectionState,
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
)

/** Handles one key-down on a day cell; returns true when the event is consumed. */
private fun DayKeyContext.handle(event: KeyEvent): Boolean =
    when (event.key) {
        Key.PageUp, Key.PageDown -> pageByPeriod(forward = event.key == Key.PageDown, byYear = event.isShiftPressed)
        Key.MoveHome, Key.MoveEnd -> focusRowEdge(start = event.key == Key.MoveHome)
        else -> moveByArrow(event)
    }

/** PageUp/PageDown: one month (or one year) back/forward, keeping the day of month where it exists. */
private fun DayKeyContext.pageByPeriod(
    forward: Boolean,
    byYear: Boolean,
): Boolean {
    val unit = if (byYear) DateTimeUnit.YEAR else DateTimeUnit.MONTH
    keyboard.page(if (forward) day.date.plus(1, unit) else day.date.minus(1, unit))
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
    val target = day.date.plus(days, DateTimeUnit.DAY)
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
        1 -> col == DAYS_PER_WEEK - 1
        -1 -> col == 0
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
