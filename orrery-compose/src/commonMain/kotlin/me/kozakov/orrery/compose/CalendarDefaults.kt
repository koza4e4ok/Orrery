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
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.hideFromAccessibility
import androidx.compose.ui.semantics.semantics
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
 *
 * [weekendContentColor] colors the day number of available, unselected,
 * in-month days whose day of week is in [weekendDays] (and that are not
 * today or in a range); it defaults to [contentColor], so weekends look like
 * weekdays until a distinct color is passed.
 *
 * Build one with [CalendarDefaults.dayColors].
 *
 * @property containerColor Background of every day cell, drawn behind the day shapes.
 * @property contentColor Day number of an available, unselected in-month day.
 * @property todayContentColor Day number of today while it is not selected or in a range.
 * @property todayIndicatorColor Color of the [TodayIndicator] (ring, fill or underline).
 * @property selectedContainerColor Fill ([CalendarDayShapes.selectedShape]) of a selected day,
 *   including [RangeEndpointStyle.FullCell] range endpoints.
 * @property selectedContentColor Day number and label of a selected day, and of today with
 *   [TodayIndicator.FilledCircle].
 * @property inRangeContainerColor Fill of the days inside a range (band or segments), and of the
 *   split endpoint halves of [RangeEndpointStyle.SplitHorizontal]/[RangeEndpointStyle.SplitDiagonal].
 * @property inRangeContentColor Day number and label of days inside a range and of split endpoints.
 * @property outDateContentColor Day number and label of out dates (days of the neighbouring months).
 * @property unavailableContainerColor Background of a disabled (unavailable) day.
 * @property unavailableContentColor Day number and label of a disabled (unavailable) day.
 * @property labelColor Secondary label ([me.kozakov.orrery.core.DayInfo.label]) of a regular day.
 * @property holidayLabelColor Secondary label of a day whose [me.kozakov.orrery.core.DayInfo.isHoliday]
 *   is true; defaults to [labelColor].
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
    public val weekendContentColor: Color = contentColor,
    public val weekendDays: Set<DayOfWeek> = DefaultWeekendDays,
)

/** Default [CalendarDayShapes.verticalInset]: a 4.dp gap between the shapes of adjacent weeks. */
internal val DefaultDayVerticalInset: Dp = 2.dp

/** Saturday and Sunday: the default weekend of [CalendarDayColors] and [CalendarDefaults.WeekHeader]. */
internal val DefaultWeekendDays: Set<DayOfWeek> = setOf(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY)

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
 *
 * [rangeEndpointStyle] picks how the endpoints of a completed range are
 * drawn: [RangeEndpointStyle.FullCell] (the default) fills them with
 * [selectedShape]; the split styles fill only the check-in (PM) or
 * check-out (AM) half of the cell for hospitality calendars.
 *
 * [verticalInset] is the space kept free above and below everything
 * [DefaultDay] draws (shapes, range band, decorators, focus ring), so the
 * shapes of days in adjacent weeks never touch. The cell keeps its height
 * and the whole cell stays clickable.
 *
 * Build one with [CalendarDefaults.dayShapes].
 *
 * @property todayIndicator How today is marked while it is not selected.
 */
@Immutable
public class CalendarDayShapes(
    public val dayShape: Shape,
    public val selectedShape: Shape,
    public val inRangeShape: Shape,
    public val todayIndicator: TodayIndicator,
    public val focusedBorderWidth: Dp = 2.dp,
    public val rangeEndpointStyle: RangeEndpointStyle = RangeEndpointStyle.FullCell,
    public val verticalInset: Dp = DefaultDayVerticalInset,
)

/**
 * Texts [DefaultDay] reports to accessibility services (e.g. TalkBack announces
 * "Thursday, July 9, 2026, Range start, Today"). Applicable states are joined
 * with ", " in the order of the properties below, and no state replaces another.
 *
 * @property rangeStart Reported for the first day of a selected range.
 * @property rangeEnd Reported for the last day of a selected range.
 * @property inRange Reported for the days between the range endpoints.
 * @property today Reported for today.
 * @property unavailable Reported for a disabled (unavailable) day.
 * @property selected Reported for a selected day that is not a range day (range
 *   days already say range start, range end or in range). Reported after the
 *   range states and before [today].
 * @property holiday Reported after [today] for a day whose [me.kozakov.orrery.core.DayInfo.isHoliday]
 *   is true, whether or not its label is shown.
 * @property events Template for the number of events of the built-in event
 *   decorators, added to the content description; `%1$d` is the count.
 */
@Immutable
public class CalendarDayStrings(
    public val rangeStart: String,
    public val rangeEnd: String,
    public val inRange: String,
    public val today: String,
    public val unavailable: String,
    public val selected: String,
    public val holiday: String,
    public val events: String,
) {
    /**
     * Strings without [selected], [holiday] and [events], which get their
     * English texts ("Selected", "Holiday", "Events: %1$d"). Use
     * [CalendarDefaults.dayStrings] for localized defaults.
     */
    public constructor(
        rangeStart: String,
        rangeEnd: String,
        inRange: String,
        today: String,
        unavailable: String,
    ) : this(
        rangeStart = rangeStart,
        rangeEnd = rangeEnd,
        inRange = inRange,
        today = today,
        unavailable = unavailable,
        selected = "Selected",
        holiday = "Holiday",
        events = "Events: %1\$d",
    )
}

/**
 * How today's date is marked when it is not selected.
 *
 * New entries/subtypes may be added in a minor release: do not rely on an exhaustive
 * `when` over this type across versions; add an `else` branch.
 */
public sealed interface TodayIndicator {
    /**
     * Outlined [CalendarDayShapes.dayShape] ring — the default.
     *
     * @property width Stroke width of the ring.
     */
    public class Ring(
        public val width: Dp = 1.dp,
    ) : TodayIndicator {
        /** Returns a copy of this indicator with the given properties replaced. */
        public fun copy(width: Dp = this.width): Ring = Ring(width = width)

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is Ring) return false
            return width == other.width
        }

        override fun hashCode(): Int = width.hashCode()

        override fun toString(): String = "Ring(width=$width)"
    }

    /**
     * Fills the day shape with the indicator color; the day number then
     * uses [CalendarDayColors.selectedContentColor] for contrast.
     */
    public data object FilledCircle : TodayIndicator

    /** Short line under the day number. */
    public data object Underline : TodayIndicator

    /** No marker: today is drawn like any other day, apart from [CalendarDayColors.todayContentColor]. */
    public data object None : TodayIndicator
}

/**
 * Defaults for the month, week and year calendars and [DefaultDay]: factories for
 * [CalendarDayColors], [CalendarDayShapes], [CalendarDayStrings] and [CalendarStyle] presets,
 * plus default header and slot composables, mirroring Material 3's `*Defaults` objects.
 */
@Suppress("TooManyFunctions")
public object CalendarDefaults {
    /**
     * Builds [CalendarDayColors] from the current [MaterialTheme] color scheme; override any
     * parameter to replace a color. See [CalendarDayColors] for what each color paints.
     */
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
        weekendContentColor: Color = contentColor,
        weekendDays: Set<DayOfWeek> = DefaultWeekendDays,
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
            weekendContentColor,
            weekendDays,
        )

    /**
     * Builds [CalendarDayShapes]: circle day and selection shapes, a continuous range band
     * ([RectangleShape] in-range shape) and a 1.dp [TodayIndicator.Ring] by default. See
     * [CalendarDayShapes] for what each parameter controls.
     */
    public fun dayShapes(
        dayShape: Shape = CircleShape,
        selectedShape: Shape = CircleShape,
        inRangeShape: Shape = RectangleShape,
        todayIndicator: TodayIndicator = TodayIndicator.Ring(),
        focusedBorderWidth: Dp = 2.dp,
        rangeEndpointStyle: RangeEndpointStyle = RangeEndpointStyle.FullCell,
        verticalInset: Dp = DefaultDayVerticalInset,
    ): CalendarDayShapes =
        CalendarDayShapes(
            dayShape,
            selectedShape,
            inRangeShape,
            todayIndicator,
            focusedBorderWidth,
            rangeEndpointStyle,
            verticalInset,
        )

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
     * Hospitality / booking [CalendarStyle]: a continuous range band whose
     * endpoints use the split [rangeEndpointStyle] ([RangeEndpointStyle.SplitDiagonal]
     * by default), so check-in (PM) and check-out (AM) days fill only their
     * half of the cell. Pass the dates where one stay checks out and the
     * next checks in as `turnoverDates` to the matching [DefaultDay]
     * overload to draw both halves. Pair it with
     * `SelectionMode.Range` and, for a booking flow that must always
     * keep a date, `SelectionMode.Single(allowClear = false)`.
     */
    @Composable
    public fun hospitalityStyle(
        rangeEndpointStyle: RangeEndpointStyle = RangeEndpointStyle.SplitDiagonal,
        density: CalendarDensity = CalendarDensity.Comfortable,
        colors: CalendarDayColors = dayColors(),
    ): CalendarStyle =
        CalendarStyle(
            colors = colors,
            shapes = dayShapes(inRangeShape = RectangleShape, rangeEndpointStyle = rangeEndpointStyle),
            density = density,
        )

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
        selected: String = orreryString(OrreryString.DaySelected),
        holiday: String = orreryString(OrreryString.DayHoliday),
        events: String = orreryString(OrreryString.DayEvents),
    ): CalendarDayStrings =
        CalendarDayStrings(
            rangeStart = rangeStart,
            rangeEnd = rangeEnd,
            inRange = inRange,
            today = today,
            unavailable = unavailable,
            selected = selected,
            holiday = holiday,
            events = events,
        )

    /**
     * Weekday header row with localized short names in [contentColor];
     * labels of [weekendDays] use [weekendContentColor] (the same color by
     * default). The labels are hidden from accessibility services because
     * [DefaultDay] includes the weekday in each day's description; a custom
     * day cell should do the same.
     */
    @Composable
    public fun WeekHeader(
        daysOfWeek: List<DayOfWeek>,
        modifier: Modifier = Modifier,
        leadingSpacer: Boolean = false,
        contentColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
        weekendDays: Set<DayOfWeek> = DefaultWeekendDays,
        weekendContentColor: Color = contentColor,
    ) {
        Row(modifier.fillMaxWidth().padding(vertical = 4.dp)) {
            if (leadingSpacer) {
                Spacer(Modifier.width(WeekNumberColumnWidth))
            }
            daysOfWeek.forEach { day ->
                Text(
                    text = PlatformDateFormat.weekdayName(day, narrow = false),
                    // DefaultDay already says its weekday; seven extra stops would only repeat it.
                    modifier = Modifier.weight(1f).semantics { hideFromAccessibility() },
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.labelMedium,
                    color = if (day in weekendDays) weekendContentColor else contentColor,
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

    /** Month + year title used by [VerticalCalendar]'s default header; a heading for accessibility services. */
    @Composable
    public fun MonthTitle(
        month: CalendarMonth,
        modifier: Modifier = Modifier,
    ) {
        Text(
            text = PlatformDateFormat.yearMonthTitle(month.yearMonth),
            modifier = modifier.semantics { heading() }.padding(horizontal = 16.dp, vertical = 8.dp),
            style = MaterialTheme.typography.titleMedium,
        )
    }
}
