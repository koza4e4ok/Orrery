package dev.koza4e4ok.material.calendar.compose

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.koza4e4ok.material.calendar.core.CalendarMonth
import kotlinx.datetime.DayOfWeek

@Immutable
public class CalendarDayColors(
    public val containerColor: Color,
    public val contentColor: Color,
    public val todayContentColor: Color,
    public val todayIndicatorColor: Color,
    public val selectedContainerColor: Color,
    public val selectedContentColor: Color,
    public val inRangeContainerColor: Color,
    public val inRangeContentColor: Color,
    public val outDateContentColor: Color,
    public val disabledContentColor: Color,
    public val labelColor: Color,
)

public object CalendarDefaults {
    @Composable
    public fun dayColors(
        containerColor: Color = Color.Transparent,
        contentColor: Color = MaterialTheme.colorScheme.onSurface,
        todayContentColor: Color = MaterialTheme.colorScheme.primary,
        todayIndicatorColor: Color = MaterialTheme.colorScheme.primary,
        selectedContainerColor: Color = MaterialTheme.colorScheme.primary,
        selectedContentColor: Color = MaterialTheme.colorScheme.onPrimary,
        inRangeContainerColor: Color = MaterialTheme.colorScheme.primaryContainer,
        inRangeContentColor: Color = MaterialTheme.colorScheme.onPrimaryContainer,
        outDateContentColor: Color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
        disabledContentColor: Color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
        labelColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    ): CalendarDayColors =
        CalendarDayColors(
            containerColor,
            contentColor,
            todayContentColor,
            todayIndicatorColor,
            selectedContainerColor,
            selectedContentColor,
            inRangeContainerColor,
            inRangeContentColor,
            outDateContentColor,
            disabledContentColor,
            labelColor,
        )

    /** Weekday header row with localized short names. */
    @Composable
    public fun WeekHeader(
        daysOfWeek: List<DayOfWeek>,
        modifier: Modifier = Modifier,
    ) {
        Row(modifier.fillMaxWidth().padding(vertical = 4.dp)) {
            daysOfWeek.forEach { day ->
                Text(
                    text = day.displayName(),
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
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
        Text(
            text = month.yearMonth.displayName(),
            modifier = modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            style = MaterialTheme.typography.titleMedium,
        )
    }
}
