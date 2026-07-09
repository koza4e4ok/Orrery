package dev.koza4e4ok.material.calendar.compose

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import dev.koza4e4ok.material.calendar.core.CalendarDay
import dev.koza4e4ok.material.calendar.core.CalendarMonth

@Composable
internal fun MonthContent(
    month: CalendarMonth,
    monthHeader: (@Composable ColumnScope.(CalendarMonth) -> Unit)?,
    dayContent: @Composable BoxScope.(CalendarDay) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier) {
        monthHeader?.invoke(this, month)
        month.weeks.forEach { week ->
            Row(Modifier.fillMaxWidth()) {
                week.days.forEach { day ->
                    Box(Modifier.weight(1f)) { dayContent(day) }
                }
            }
        }
    }
}
