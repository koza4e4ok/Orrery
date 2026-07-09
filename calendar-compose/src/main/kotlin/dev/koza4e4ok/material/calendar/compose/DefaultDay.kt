package dev.koza4e4ok.material.calendar.compose

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import dev.koza4e4ok.material.calendar.core.CalendarDay
import dev.koza4e4ok.material.calendar.core.DayInfo
import dev.koza4e4ok.material.calendar.core.DayPosition
import kotlinx.datetime.LocalDate
import kotlinx.datetime.toJavaLocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/**
 * Batteries-included day cell: selection circle, in-range band, today ring,
 * optional secondary label, and a [decorator] DrawScope escape hatch for
 * custom drawing (markers, heatmaps, pressed effects).
 */
@Composable
public fun DefaultDay(
    day: CalendarDay,
    modifier: Modifier = Modifier,
    selectionState: CalendarSelectionState? = null,
    today: LocalDate = currentDate(),
    colors: CalendarDayColors = CalendarDefaults.dayColors(),
    info: DayInfo? = null,
    enabled: Boolean = true,
    showOutDates: Boolean = true,
    decorator: (DrawScope.(CalendarDay) -> Unit)? = null,
    onClick: ((CalendarDay) -> Unit)? = null,
) {
    val hidden = !showOutDates && day.position != DayPosition.MonthDate
    if (hidden) {
        Box(modifier.fillMaxWidth().heightIn(min = 48.dp))
        return
    }
    val isToday = day.date == today
    val isSelected = selectionState?.isSelected(day.date) == true
    val isInRange = selectionState?.isInRange(day.date) == true
    val description =
        remember(day.date) {
            day.date.toJavaLocalDate().format(DateTimeFormatter.ofLocalizedDate(FormatStyle.LONG))
        }
    val contentColor =
        when {
            !enabled -> colors.disabledContentColor
            isSelected -> colors.selectedContentColor
            isInRange -> colors.inRangeContentColor
            day.position != DayPosition.MonthDate -> colors.outDateContentColor
            isToday -> colors.todayContentColor
            else -> colors.contentColor
        }
    Box(
        modifier =
            modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
                .background(if (isInRange) colors.inRangeContainerColor else colors.containerColor)
                .clip(CircleShape)
                .then(
                    if (isSelected) Modifier.background(colors.selectedContainerColor, CircleShape) else Modifier,
                ).then(
                    if (isToday && !isSelected) {
                        Modifier.border(1.dp, colors.todayIndicatorColor, CircleShape)
                    } else {
                        Modifier
                    },
                ).selectable(
                    selected = isSelected,
                    enabled = enabled,
                    onClick = {
                        selectionState?.click(day.date)
                        onClick?.invoke(day)
                    },
                ).semantics { contentDescription = description }
                .then(if (decorator != null) Modifier.drawBehind { decorator(day) } else Modifier),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = day.date.day.toString(),
                style = MaterialTheme.typography.bodyMedium,
                color = contentColor,
            )
            val label = info?.label
            if (label != null) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (isSelected) colors.selectedContentColor else colors.labelColor,
                )
            }
        }
    }
}
