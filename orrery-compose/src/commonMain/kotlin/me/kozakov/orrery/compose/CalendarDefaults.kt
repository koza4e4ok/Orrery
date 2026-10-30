package me.kozakov.orrery.compose

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.datetime.DayOfWeek
import me.kozakov.orrery.core.CalendarMonth
import me.kozakov.orrery.core.CalendarWeek
import me.kozakov.orrery.core.isoWeekNumber

/**
 * Colors used by [DefaultDay].
 *
 * [focusedBorderColor] draws the keyboard / D-pad focus ring around the
 * day shape; [hoverContainerColor] fills the day shape while a pointer
 * hovers an available, unselected day (Desktop and Web).
 */
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
    public val holidayLabelColor: Color = labelColor,
    public val focusedBorderColor: Color = todayIndicatorColor,
    public val hoverContainerColor: Color = contentColor.copy(alpha = 0.08f),
) {
    @Deprecated("Kept for binary compatibility; use the primary constructor.", level = DeprecationLevel.HIDDEN)
    public constructor(
        containerColor: Color,
        contentColor: Color,
        todayContentColor: Color,
        todayIndicatorColor: Color,
        selectedContainerColor: Color,
        selectedContentColor: Color,
        inRangeContainerColor: Color,
        inRangeContentColor: Color,
        outDateContentColor: Color,
        unavailableContainerColor: Color,
        unavailableContentColor: Color,
        labelColor: Color,
        holidayLabelColor: Color = labelColor,
    ) : this(
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
        holidayLabelColor,
        todayIndicatorColor,
        contentColor.copy(alpha = 0.08f),
    )
}

/**
 * Shapes and today-marker style used by [DefaultDay].
 *
 * [inRangeShape] picks the in-range rendering mode: [RectangleShape]
 * (the default) draws a continuous connector band across the range;
 * any other shape draws a separate centered fill per in-range day
 * (segmented style), with the range endpoints keeping [selectedShape].
 *
 * [focusedBorderWidth] is the stroke width of the focus ring drawn
 * around [dayShape] while the day has keyboard / D-pad focus.
 */
@Immutable
public class CalendarDayShapes(
    public val dayShape: Shape,
    public val selectedShape: Shape,
    public val inRangeShape: Shape,
    public val todayIndicator: TodayIndicator,
    public val focusedBorderWidth: Dp = 2.dp,
) {
    @Deprecated("Kept for binary compatibility; use the primary constructor.", level = DeprecationLevel.HIDDEN)
    public constructor(
        dayShape: Shape,
        selectedShape: Shape,
        inRangeShape: Shape,
        todayIndicator: TodayIndicator,
    ) : this(dayShape, selectedShape, inRangeShape, todayIndicator, 2.dp)
}

/**
 * State descriptions [DefaultDay] reports to accessibility services
 * (e.g. TalkBack announces "July 9, 2026, Range start, Today").
 * Applicable states are joined with ", ".
 */
@Immutable
public class CalendarDayStrings(
    public val rangeStart: String,
    public val rangeEnd: String,
    public val inRange: String,
    public val today: String,
    public val unavailable: String,
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

// Factory object for colors, shapes, strings, styles and slot defaults, mirroring Material3's *Defaults objects.
@Suppress("TooManyFunctions")
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
        holidayLabelColor: Color = MaterialTheme.colorScheme.primary,
        focusedBorderColor: Color = todayIndicatorColor,
        hoverContainerColor: Color = contentColor.copy(alpha = 0.08f),
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
            holidayLabelColor,
            focusedBorderColor,
            hoverContainerColor,
        )

    @Deprecated(
        "Kept for binary compatibility; use the overload with focus and hover colors.",
        level = DeprecationLevel.HIDDEN,
    )
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
        holidayLabelColor: Color = MaterialTheme.colorScheme.primary,
    ): CalendarDayColors =
        dayColors(
            containerColor = containerColor,
            contentColor = contentColor,
            todayContentColor = todayContentColor,
            todayIndicatorColor = todayIndicatorColor,
            selectedContainerColor = selectedContainerColor,
            selectedContentColor = selectedContentColor,
            inRangeContainerColor = inRangeContainerColor,
            inRangeContentColor = inRangeContentColor,
            outDateContentColor = outDateContentColor,
            unavailableContainerColor = unavailableContainerColor,
            unavailableContentColor = unavailableContentColor,
            labelColor = labelColor,
            holidayLabelColor = holidayLabelColor,
            focusedBorderColor = todayIndicatorColor,
            hoverContainerColor = contentColor.copy(alpha = 0.08f),
        )

    public fun dayShapes(
        dayShape: Shape = CircleShape,
        selectedShape: Shape = CircleShape,
        inRangeShape: Shape = RectangleShape,
        todayIndicator: TodayIndicator = TodayIndicator.Ring(),
        focusedBorderWidth: Dp = 2.dp,
    ): CalendarDayShapes = CalendarDayShapes(dayShape, selectedShape, inRangeShape, todayIndicator, focusedBorderWidth)

    @Deprecated(
        "Kept for binary compatibility; use the overload with focusedBorderWidth.",
        level = DeprecationLevel.HIDDEN,
    )
    public fun dayShapes(
        dayShape: Shape = CircleShape,
        selectedShape: Shape = CircleShape,
        inRangeShape: Shape = RectangleShape,
        todayIndicator: TodayIndicator = TodayIndicator.Ring(),
    ): CalendarDayShapes = dayShapes(dayShape, selectedShape, inRangeShape, todayIndicator, 2.dp)

    /**
     * Default Material 3 [CalendarStyle]: circle day and selection shapes
     * with a continuous pill range band, at the given [density].
     */
    @Composable
    public fun style(
        density: CalendarDensity = CalendarDensity.Comfortable,
        colors: CalendarDayColors = dayColors(),
        shapes: CalendarDayShapes = dayShapes(),
        cellBorderWidth: Dp = 0.dp,
        cellBorderColor: Color = Color.Transparent,
        selectedElevation: Dp = 0.dp,
    ): CalendarStyle = CalendarStyle(colors, shapes, density, cellBorderWidth, cellBorderColor, selectedElevation)

    /**
     * Rounded-square [CalendarStyle]: day and selection shapes are
     * [RoundedCornerShape] with [cornerRadius], and in-range days keep the
     * continuous connector band.
     */
    @Composable
    public fun roundedSquareStyle(
        cornerRadius: Dp = 10.dp,
        density: CalendarDensity = CalendarDensity.Comfortable,
        colors: CalendarDayColors = dayColors(),
    ): CalendarStyle {
        val shape = RoundedCornerShape(cornerRadius)
        return CalendarStyle(
            colors = colors,
            shapes = dayShapes(dayShape = shape, selectedShape = shape, inRangeShape = RectangleShape),
            density = density,
        )
    }

    /**
     * Segmented-range [CalendarStyle]: day, selection and in-range shapes
     * are [RoundedCornerShape] with [cornerRadius], so every in-range day
     * gets its own rounded fill instead of a continuous band.
     */
    @Composable
    public fun segmentedRangeStyle(
        cornerRadius: Dp = 8.dp,
        density: CalendarDensity = CalendarDensity.Comfortable,
        colors: CalendarDayColors = dayColors(),
    ): CalendarStyle {
        val shape = RoundedCornerShape(cornerRadius)
        return CalendarStyle(
            colors = colors,
            shapes = dayShapes(dayShape = shape, selectedShape = shape, inRangeShape = shape),
            density = density,
        )
    }

    /**
     * Bordered-card [CalendarStyle] for dashboard and desktop grids:
     * rounded-square cells with [cornerRadius] outlined by a [borderWidth]
     * border in [borderColor], and a [selectedElevation] shadow under the
     * selected day. Defaults to [CalendarDensity.Expanded].
     */
    @Composable
    public fun borderedCardStyle(
        cornerRadius: Dp = 8.dp,
        density: CalendarDensity = CalendarDensity.Expanded,
        borderWidth: Dp = 1.dp,
        borderColor: Color = MaterialTheme.colorScheme.outlineVariant,
        selectedElevation: Dp = 2.dp,
        colors: CalendarDayColors = dayColors(),
    ): CalendarStyle {
        val shape = RoundedCornerShape(cornerRadius)
        return CalendarStyle(
            colors = colors,
            shapes = dayShapes(dayShape = shape, selectedShape = shape, inRangeShape = RectangleShape),
            density = density,
            cellBorderWidth = borderWidth,
            cellBorderColor = borderColor,
            selectedElevation = selectedElevation,
        )
    }

    /**
     * Builds [CalendarDayStrings]. Defaults are resolved from the current
     * locale's resources; override any parameter to replace a string.
     */
    @Composable
    @ReadOnlyComposable
    public fun dayStrings(
        rangeStart: String = orreryString(OrreryString.DayRangeStart),
        rangeEnd: String = orreryString(OrreryString.DayRangeEnd),
        inRange: String = orreryString(OrreryString.DayInRange),
        today: String = orreryString(OrreryString.DayToday),
        unavailable: String = orreryString(OrreryString.DayUnavailable),
    ): CalendarDayStrings = CalendarDayStrings(rangeStart, rangeEnd, inRange, today, unavailable)

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
                    text = PlatformDateFormat.weekdayName(day, narrow = false),
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }

    /** Alias of [WeekHeader] for weekday header rows with localized short names. */
    @Composable
    public fun DaysOfWeekHeader(
        daysOfWeek: List<DayOfWeek>,
        modifier: Modifier = Modifier,
        leadingSpacer: Boolean = false,
    ) {
        WeekHeader(daysOfWeek = daysOfWeek, modifier = modifier, leadingSpacer = leadingSpacer)
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
            text = PlatformDateFormat.yearMonthTitle(month.yearMonth),
            modifier = modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            style = MaterialTheme.typography.titleMedium,
        )
    }
}
