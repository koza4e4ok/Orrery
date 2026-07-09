package dev.koza4e4ok.material.calendar.compose

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import dev.koza4e4ok.material.calendar.core.CalendarMonth
import kotlinx.datetime.DayOfWeek

public object CalendarDefaults {
    /** Weekday header row with localized short names. */
    @Composable
    public fun WeekHeader(
        daysOfWeek: List<DayOfWeek>,
        modifier: Modifier = Modifier,
    ) {
        Row(modifier.fillMaxWidth()) {
            daysOfWeek.forEach { day ->
                Text(
                    text = day.displayName(),
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center,
                )
            }
        }
    }

    /** Month + year title used by [VerticalCalendar]'s default header. */
    @Composable
    public fun MonthTitle(
        month: CalendarMonth,
        modifier: Modifier = Modifier,
    ) {
        Text(text = month.yearMonth.displayName(), modifier = modifier)
    }
}
