package dev.koza4e4ok.material.calendar.compose

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.koza4e4ok.material.calendar.core.CalendarMonth
import dev.koza4e4ok.material.calendar.core.CalendarWeek
import dev.koza4e4ok.material.calendar.core.isoWeekNumber
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
    public val unavailableContainerColor: Color,
    public val unavailableContentColor: Color,
    public val labelColor: Color,
)

/** Shapes and today-marker style used by [DefaultDay]. */
@Immutable
public class CalendarDayShapes(
    public val dayShape: Shape,
    public val selectedShape: Shape,
    public val inRangeShape: Shape,
    public val todayIndicator: TodayIndicator,
)

/** How today's date is marked when it is not selected. */
public sealed interface TodayIndicator {
    /** Outlined [CalendarDayShapes.dayShape] ring — the default. */
    public data class Ring(
        val width: Dp = 1.dp,
    ) : TodayIndicator

    /**
     * Fills the day shape with the indicator color; the day number then
     * uses [CalendarDayColors.selectedContentColor] for contrast.
     */
    public data object FilledCircle : TodayIndicator

    /** Short line under the day number. */
    public data object Underline : TodayIndicator

    public data object None : TodayIndicator
}

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
        unavailableContainerColor: Color = Color.Transparent,
        unavailableContentColor: Color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
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
            unavailableContainerColor,
            unavailableContentColor,
            labelColor,
        )

    public fun dayShapes(
        dayShape: Shape = CircleShape,
        selectedShape: Shape = CircleShape,
        inRangeShape: Shape = RectangleShape,
        todayIndicator: TodayIndicator = TodayIndicator.Ring(),
    ): CalendarDayShapes = CalendarDayShapes(dayShape, selectedShape, inRangeShape, todayIndicator)

    /** Weekday header row with localized short names. */
    @Composable
    public fun WeekHeader(
        daysOfWeek: List<DayOfWeek>,
        modifier: Modifier = Modifier,
        leadingSpacer: Boolean = false,
    ) {
        Row(modifier.fillMaxWidth().padding(vertical = 4.dp)) {
            if (leadingSpacer) {
                Spacer(Modifier.width(WeekNumberColumnWidth))
            }
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

    /** ISO week number cell for the leading column. */
    @Composable
    public fun WeekNumber(
        week: CalendarWeek,
        modifier: Modifier = Modifier,
    ) {
        Text(
            text =
                week.days
                    .first()
                    .date
                    .isoWeekNumber()
                    .toString(),
            modifier = modifier,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
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
