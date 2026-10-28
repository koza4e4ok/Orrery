package me.kozakov.orrery.compose

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.Placeable
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import kotlinx.datetime.LocalDate
import me.kozakov.orrery.core.CalendarEvent
import me.kozakov.orrery.core.PositionedEvent

/** Colors used by [DaySchedule], [WeekSchedule] and the [ScheduleDefaults] slots. */
@Immutable
public class ScheduleColors(
    public val gridLineColor: Color,
    public val halfHourLineColor: Color,
    public val hourLabelColor: Color,
    public val headerContentColor: Color,
    public val todayHeaderColor: Color,
    public val nowIndicatorColor: Color,
    public val defaultEventContainerColor: Color,
    public val defaultEventContentColor: Color,
    public val defaultEventAccentColor: Color,
)

/** Sizes used by [DaySchedule], [WeekSchedule] and the [ScheduleDefaults] slots. */
@Immutable
public class ScheduleDimensions(
    public val timeGutterWidth: Dp,
    public val tileCornerRadius: Dp,
    public val tileAccentBarWidth: Dp,
    public val tileHorizontalGap: Dp,
    public val tileVerticalGap: Dp,
    public val nowLineStrokeWidth: Dp,
    public val nowDotRadius: Dp,
    public val allDayChipHeight: Dp,
    public val maxVisibleAllDayRows: Int,
)

/**
 * Localized strings for [DaySchedule] and [WeekSchedule].
 * [ScheduleDefaults.strings] resolves them from the current locale's resources.
 */
@Immutable
public class ScheduleStrings(
    public val allDayLabel: String,
    public val nowLabel: String,
)

/** Defaults for [DaySchedule] and [WeekSchedule]. */
public object ScheduleDefaults {
    @Composable
    public fun colors(
        gridLineColor: Color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
        halfHourLineColor: Color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f),
        hourLabelColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
        headerContentColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
        todayHeaderColor: Color = MaterialTheme.colorScheme.primary,
        nowIndicatorColor: Color = MaterialTheme.colorScheme.primary,
        defaultEventContainerColor: Color = MaterialTheme.colorScheme.primaryContainer,
        defaultEventContentColor: Color = MaterialTheme.colorScheme.onPrimaryContainer,
        defaultEventAccentColor: Color = MaterialTheme.colorScheme.primary,
    ): ScheduleColors =
        ScheduleColors(
            gridLineColor = gridLineColor,
            halfHourLineColor = halfHourLineColor,
            hourLabelColor = hourLabelColor,
            headerContentColor = headerContentColor,
            todayHeaderColor = todayHeaderColor,
            nowIndicatorColor = nowIndicatorColor,
            defaultEventContainerColor = defaultEventContainerColor,
            defaultEventContentColor = defaultEventContentColor,
            defaultEventAccentColor = defaultEventAccentColor,
        )

    public fun dimensions(
        timeGutterWidth: Dp = 56.dp,
        tileCornerRadius: Dp = 6.dp,
        tileAccentBarWidth: Dp = 3.dp,
        tileHorizontalGap: Dp = 2.dp,
        tileVerticalGap: Dp = 1.dp,
        nowLineStrokeWidth: Dp = 2.dp,
        nowDotRadius: Dp = 4.dp,
        allDayChipHeight: Dp = 20.dp,
        maxVisibleAllDayRows: Int = 3,
    ): ScheduleDimensions =
        ScheduleDimensions(
            timeGutterWidth = timeGutterWidth,
            tileCornerRadius = tileCornerRadius,
            tileAccentBarWidth = tileAccentBarWidth,
            tileHorizontalGap = tileHorizontalGap,
            tileVerticalGap = tileVerticalGap,
            nowLineStrokeWidth = nowLineStrokeWidth,
            nowDotRadius = nowDotRadius,
            allDayChipHeight = allDayChipHeight,
            maxVisibleAllDayRows = maxVisibleAllDayRows,
        )

    /**
     * Builds [ScheduleStrings]. Defaults are resolved from the current
     * locale's resources; override any parameter to replace a string.
     */
    @Composable
    @ReadOnlyComposable
    public fun strings(
        allDayLabel: String = orreryString(OrreryString.AgendaAllDay),
        nowLabel: String = orreryString(OrreryString.ScheduleNow),
    ): ScheduleStrings = ScheduleStrings(allDayLabel = allDayLabel, nowLabel = nowLabel)

    /** Default day column header: short weekday name above the day number. */
    @Composable
    public fun DayHeader(
        date: LocalDate,
        isToday: Boolean,
        colors: ScheduleColors,
        modifier: Modifier = Modifier,
    ) {
        val color = if (isToday) colors.todayHeaderColor else colors.headerContentColor
        Column(
            modifier = modifier.fillMaxWidth().padding(vertical = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = PlatformDateFormat.uppercase(PlatformDateFormat.weekdayName(date.dayOfWeek, narrow = false)),
                style = MaterialTheme.typography.labelSmall,
                color = color,
                maxLines = 1,
            )
            Text(
                text = date.day.toString(),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = if (isToday) FontWeight.Bold else null,
                color = if (isToday) colors.todayHeaderColor else MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
            )
        }
    }

    /** Default all-day chip shown above the time grid. */
    @Composable
    public fun AllDayEventChip(
        event: CalendarEvent<*>,
        colors: ScheduleColors,
        dimensions: ScheduleDimensions = dimensions(),
        modifier: Modifier = Modifier,
        onClick: (() -> Unit)? = null,
    ) {
        val palette = eventPalette(event, colors)
        Row(
            modifier =
                modifier
                    .fillMaxWidth()
                    .height(dimensions.allDayChipHeight)
                    .clip(RoundedCornerShape(dimensions.tileCornerRadius))
                    .background(palette.container)
                    .then(
                        if (onClick != null) {
                            Modifier.clickable(role = Role.Button, onClick = onClick)
                        } else {
                            Modifier
                        },
                    ),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier
                    .width(dimensions.tileAccentBarWidth)
                    .fillMaxHeight()
                    .background(palette.accent),
            )
            Text(
                text = event.title,
                modifier = Modifier.padding(horizontal = 4.dp),
                style = MaterialTheme.typography.labelSmall,
                color = palette.content,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }

    /** Default hour label in the time gutter, e.g. "08:00". */
    @Composable
    public fun HourLabel(
        hour: Int,
        colors: ScheduleColors,
        modifier: Modifier = Modifier,
    ) {
        Text(
            text = hour.toString().padStart(2, '0') + ":00",
            modifier = modifier.fillMaxWidth().padding(end = 8.dp),
            style = MaterialTheme.typography.labelSmall,
            color = colors.hourLabelColor,
            textAlign = TextAlign.End,
            maxLines = 1,
        )
    }

    /** Default current-time line: a dot at the start edge and a horizontal line. */
    @Composable
    public fun NowIndicator(
        colors: ScheduleColors,
        dimensions: ScheduleDimensions = dimensions(),
        modifier: Modifier = Modifier,
        contentDescription: String? = null,
    ) {
        val description = contentDescription
        Canvas(
            modifier
                .fillMaxWidth()
                .height(dimensions.nowDotRadius * 2)
                .semantics {
                    if (description != null) {
                        this.contentDescription = description
                    }
                },
        ) {
            val radius = dimensions.nowDotRadius.toPx()
            val centerY = size.height / 2f
            drawLine(
                color = colors.nowIndicatorColor,
                start = Offset(0f, centerY),
                end = Offset(size.width, centerY),
                strokeWidth = dimensions.nowLineStrokeWidth.toPx(),
            )
            val dotX = if (layoutDirection == LayoutDirection.Rtl) size.width - radius else radius
            drawCircle(color = colors.nowIndicatorColor, radius = radius, center = Offset(dotX, centerY))
        }
    }

    /**
     * Default timed event tile: a leading accent stripe, the title, the time
     * range clipped to the column's day, and an optional subtitle.
     */
    @Composable
    public fun EventTile(
        positioned: PositionedEvent<*>,
        colors: ScheduleColors,
        dimensions: ScheduleDimensions = dimensions(),
        modifier: Modifier = Modifier,
        onClick: (() -> Unit)? = null,
    ) {
        val event = positioned.event
        val palette = eventPalette(event, colors)
        Row(
            modifier =
                modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(dimensions.tileCornerRadius))
                    .background(palette.container)
                    .then(
                        if (onClick != null) {
                            Modifier.clickable(role = Role.Button, onClick = onClick)
                        } else {
                            Modifier
                        },
                    ),
        ) {
            Box(
                Modifier
                    .width(dimensions.tileAccentBarWidth)
                    .fillMaxHeight()
                    .background(palette.accent),
            )
            FittingColumn(Modifier.padding(horizontal = 4.dp, vertical = 2.dp)) {
                Text(
                    text = event.title,
                    style = MaterialTheme.typography.labelMedium,
                    color = palette.content,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = minuteLabel(positioned.startMinute) + " – " + minuteLabel(positioned.endMinute),
                    style = MaterialTheme.typography.labelSmall,
                    color = palette.content.copy(alpha = 0.8f),
                    maxLines = 1,
                )
                val subtitle = event.subtitle
                if (subtitle != null) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.labelSmall,
                        color = palette.content.copy(alpha = 0.8f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

/**
 * Stacks children top to bottom and drops every child after the first that
 * would not fit completely, so short tiles never show half-clipped lines.
 */
@Composable
private fun FittingColumn(
    modifier: Modifier,
    content: @Composable () -> Unit,
) {
    Layout(content = content, modifier = modifier) { measurables, constraints ->
        val childConstraints = constraints.copy(minWidth = 0, minHeight = 0, maxHeight = Constraints.Infinity)
        val placeables = ArrayList<Placeable>(measurables.size)
        var height = 0
        for (measurable in measurables) {
            val placeable = measurable.measure(childConstraints)
            if (placeables.isNotEmpty() && height + placeable.height > constraints.maxHeight) break
            placeables.add(placeable)
            height += placeable.height
        }
        val width =
            if (constraints.hasBoundedWidth) {
                constraints.maxWidth
            } else {
                placeables.maxOfOrNull { it.width } ?: 0
            }
        layout(width, height.coerceIn(constraints.minHeight, constraints.maxHeight)) {
            var y = 0
            for (placeable in placeables) {
                placeable.placeRelative(0, y)
                y += placeable.height
            }
        }
    }
}

private class EventPalette(
    val container: Color,
    val content: Color,
    val accent: Color,
)

@Composable
@ReadOnlyComposable
private fun eventPalette(
    event: CalendarEvent<*>,
    colors: ScheduleColors,
): EventPalette {
    val argb = event.colorArgb
    if (argb == null) {
        return EventPalette(
            container = colors.defaultEventContainerColor,
            content = colors.defaultEventContentColor,
            accent = colors.defaultEventAccentColor,
        )
    }
    val accent = Color(argb)
    val surface = MaterialTheme.colorScheme.surface
    val dark = surface.luminance() < 0.5f
    val container = accent.copy(alpha = if (dark) 0.28f else 0.18f)
    return EventPalette(
        container = container,
        content =
            readableContentColor(
                background = container.compositeOver(surface),
                onSurface = MaterialTheme.colorScheme.onSurface,
                inverseOnSurface = MaterialTheme.colorScheme.inverseOnSurface,
            ),
        accent = accent,
    )
}

/**
 * Picks whichever of the theme's text colors, [onSurface] or
 * [inverseOnSurface], has the higher WCAG contrast ratio against the
 * (opaque) [background]: a bright custom event color tinting a dark surface
 * gets dark text instead of washed-out light text, and vice versa.
 */
internal fun readableContentColor(
    background: Color,
    onSurface: Color,
    inverseOnSurface: Color,
): Color {
    val backgroundLuminance = background.luminance()
    return if (contrastRatio(onSurface.luminance(), backgroundLuminance) >=
        contrastRatio(inverseOnSurface.luminance(), backgroundLuminance)
    ) {
        onSurface
    } else {
        inverseOnSurface
    }
}

/** WCAG 2 contrast ratio between two relative luminances, 1..21. */
internal fun contrastRatio(
    a: Float,
    b: Float,
): Float = (maxOf(a, b) + WCAG_FLARE) / (minOf(a, b) + WCAG_FLARE)

private const val WCAG_FLARE = 0.05f

private fun minuteLabel(minuteOfDay: Int): String =
    (minuteOfDay / 60).toString().padStart(2, '0') + ":" + (minuteOfDay % 60).toString().padStart(2, '0')
