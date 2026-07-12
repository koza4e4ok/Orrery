package dev.koza4e4ok.material.calendar.compose

import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import dev.koza4e4ok.material.calendar.core.CalendarDay
import dev.koza4e4ok.material.calendar.core.CalendarMonth
import dev.koza4e4ok.material.calendar.core.CalendarWeek
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.minus
import kotlinx.datetime.plus

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
) {
    val haptics = LocalHapticFeedback.current
    Column(modifier) {
        monthHeader?.invoke(this, month)
        Column(
            Modifier.then(
                if (dragSelection != null) {
                    Modifier.pointerInput(month, weekNumber != null, hapticsEnabled) {
                        val leading = if (weekNumber != null) WeekNumberColumnWidth.toPx() else 0f

                        fun dayAt(offset: Offset): CalendarDay? {
                            val rows = month.weeks.size
                            if (rows == 0) return null
                            val rowHeight = size.height.toFloat() / rows
                            val colWidth = (size.width - leading) / 7f
                            val row = (offset.y / rowHeight).toInt().coerceIn(0, rows - 1)
                            val col = ((offset.x - leading) / colWidth).toInt().coerceIn(0, 6)
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
            month.weeks.forEach { week ->
                Row(Modifier.fillMaxWidth()) {
                    if (weekNumber != null) {
                        Box(Modifier.width(WeekNumberColumnWidth), contentAlignment = Alignment.Center) {
                            weekNumber(week)
                        }
                    }
                    week.days.forEachIndexed { col, day ->
                        DayCell(
                            day = day,
                            col = col,
                            keyboard = keyboardNavigation,
                            modifier = Modifier.weight(1f),
                            dayContent = dayContent,
                        )
                    }
                }
            }
        }
    }
}

/**
 * Day-cell wrapper adding keyboard edge paging: within-month arrow
 * moves are native Compose focus search; at row edges (col 0 / col 6)
 * the matching arrow key pages the calendar and focus lands on the
 * target date's cell once composed. RTL swaps which physical key means
 * "toward the next day".
 */
@Composable
internal fun DayCell(
    day: CalendarDay,
    col: Int,
    keyboard: CalendarKeyboardNavigation?,
    modifier: Modifier,
    dayContent: @Composable BoxScope.(CalendarDay) -> Unit,
) {
    if (keyboard == null) {
        Box(modifier) { dayContent(day) }
        return
    }
    val isRtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val focusRequester = remember { FocusRequester() }
    Box(
        modifier
            .focusRequester(focusRequester)
            .onPreviewKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                val towardNext = if (isRtl) Key.DirectionLeft else Key.DirectionRight
                val towardPrevious = if (isRtl) Key.DirectionRight else Key.DirectionLeft
                when {
                    event.key == towardNext && col == 6 -> {
                        keyboard.page(day.date.plus(1, DateTimeUnit.DAY))
                        true
                    }

                    event.key == towardPrevious && col == 0 -> {
                        keyboard.page(day.date.minus(1, DateTimeUnit.DAY))
                        true
                    }

                    else -> {
                        false
                    }
                }
            },
    ) { dayContent(day) }
    LaunchedEffect(keyboard.pendingFocus.value) {
        if (keyboard.pendingFocus.value == day.date) {
            focusRequester.requestFocus()
            keyboard.pendingFocus.value = null
        }
    }
}
