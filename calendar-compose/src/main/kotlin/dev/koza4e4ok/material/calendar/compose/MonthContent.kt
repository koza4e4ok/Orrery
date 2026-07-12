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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import dev.koza4e4ok.material.calendar.core.CalendarDay
import dev.koza4e4ok.material.calendar.core.CalendarMonth
import dev.koza4e4ok.material.calendar.core.CalendarWeek
import kotlinx.datetime.LocalDate

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
                    week.days.forEach { day ->
                        Box(Modifier.weight(1f)) { dayContent(day) }
                    }
                }
            }
        }
    }
}
