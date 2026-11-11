package me.kozakov.orrery.compose

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.Placeable
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
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

/**
 * Sizes used by [DaySchedule], [WeekSchedule] and the [ScheduleDefaults] slots.
 *
 * @property timeGutterWidth Minimum width of the hour gutter; it widens to fit
 *   the default hour labels.
 * @property maxVisibleAllDayRows Most all-day rows, including the "+N" row;
 *   fewer in short windows, where the all-day area takes at most about a
 *   quarter of the height.
 * @property resizeHandleWidth Width of the resize handle pill drawn on a tile's
 *   top and bottom edge when `onEventResize` is set.
 * @property resizeHandleThickness Thickness of the resize handle pill.
 * @property resizeHandleTouchSize Height of the touch zone around each resize
 *   handle; it never covers more than a third of the tile.
 * @property ghostBorderWidth Outline width of [ScheduleDefaults.DragGhost].
 */
@Immutable
@Suppress("LongParameterList")
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
    public val resizeHandleWidth: Dp,
    public val resizeHandleThickness: Dp,
    public val resizeHandleTouchSize: Dp,
    public val ghostBorderWidth: Dp,
) {
    @Deprecated(
        "Kept for binary compatibility; use the constructor with the resize handle and ghost sizes.",
        level = DeprecationLevel.HIDDEN,
    )
    @Suppress("LongParameterList")
    public constructor(
        timeGutterWidth: Dp,
        tileCornerRadius: Dp,
        tileAccentBarWidth: Dp,
        tileHorizontalGap: Dp,
        tileVerticalGap: Dp,
        nowLineStrokeWidth: Dp,
        nowDotRadius: Dp,
        allDayChipHeight: Dp,
        maxVisibleAllDayRows: Int,
    ) : this(
        timeGutterWidth = timeGutterWidth,
        tileCornerRadius = tileCornerRadius,
        tileAccentBarWidth = tileAccentBarWidth,
        tileHorizontalGap = tileHorizontalGap,
        tileVerticalGap = tileVerticalGap,
        nowLineStrokeWidth = nowLineStrokeWidth,
        nowDotRadius = nowDotRadius,
        allDayChipHeight = allDayChipHeight,
        maxVisibleAllDayRows = maxVisibleAllDayRows,
        resizeHandleWidth = DefaultResizeHandleWidth,
        resizeHandleThickness = DefaultResizeHandleThickness,
        resizeHandleTouchSize = DefaultResizeHandleTouchSize,
        ghostBorderWidth = DefaultGhostBorderWidth,
    )
}

private val DefaultResizeHandleWidth = 28.dp
private val DefaultResizeHandleThickness = 4.dp
private val DefaultResizeHandleTouchSize = 32.dp
private val DefaultGhostBorderWidth = 2.dp

/**
 * Localized strings for [DaySchedule] and [WeekSchedule].
 * [ScheduleDefaults.strings] resolves them from the current locale's resources.
 *
 * @property moveEarlier Accessibility action: move an event one snap step earlier.
 * @property moveLater Accessibility action: move an event one snap step later.
 * @property movePreviousDay Accessibility action: move an event to the previous day of the page.
 * @property moveNextDay Accessibility action: move an event to the next day of the page.
 * @property extendEnd Accessibility action: make an event end one snap step later.
 * @property shortenEnd Accessibility action: make an event end one snap step earlier.
 * @property createAt Accessibility action template for creating an event; `%1$s` is the time.
 * @property dragTimeRange Template describing the dragged time range; `%1$s` is the start, `%2$s` the end.
 */
@Immutable
@Suppress("LongParameterList")
public class ScheduleStrings(
    public val allDayLabel: String,
    public val nowLabel: String,
    public val moveEarlier: String,
    public val moveLater: String,
    public val movePreviousDay: String,
    public val moveNextDay: String,
    public val extendEnd: String,
    public val shortenEnd: String,
    public val createAt: String,
    public val dragTimeRange: String,
) {
    /** Fills the editing strings with their English text. */
    @Deprecated(
        "Kept for binary compatibility; use the constructor with the editing strings.",
        level = DeprecationLevel.HIDDEN,
    )
    public constructor(
        allDayLabel: String,
        nowLabel: String,
    ) : this(
        allDayLabel = allDayLabel,
        nowLabel = nowLabel,
        moveEarlier = "Move earlier",
        moveLater = "Move later",
        movePreviousDay = "Move to previous day",
        moveNextDay = "Move to next day",
        extendEnd = "Extend end",
        shortenEnd = "Shorten end",
        createAt = "Create event at %1\$s",
        dragTimeRange = "%1\$s – %2\$s",
    )
}

/** Defaults for [DaySchedule] and [WeekSchedule]. */
@Suppress("TooManyFunctions")
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

    @Suppress("LongParameterList")
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
        resizeHandleWidth: Dp = DefaultResizeHandleWidth,
        resizeHandleThickness: Dp = DefaultResizeHandleThickness,
        resizeHandleTouchSize: Dp = DefaultResizeHandleTouchSize,
        ghostBorderWidth: Dp = DefaultGhostBorderWidth,
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
            resizeHandleWidth = resizeHandleWidth,
            resizeHandleThickness = resizeHandleThickness,
            resizeHandleTouchSize = resizeHandleTouchSize,
            ghostBorderWidth = ghostBorderWidth,
        )

    @Deprecated(
        "Kept for binary compatibility; use the overload with the resize handle and ghost sizes.",
        level = DeprecationLevel.HIDDEN,
    )
    @Suppress("LongParameterList")
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
        dimensions(
            timeGutterWidth = timeGutterWidth,
            tileCornerRadius = tileCornerRadius,
            tileAccentBarWidth = tileAccentBarWidth,
            tileHorizontalGap = tileHorizontalGap,
            tileVerticalGap = tileVerticalGap,
            nowLineStrokeWidth = nowLineStrokeWidth,
            nowDotRadius = nowDotRadius,
            allDayChipHeight = allDayChipHeight,
            maxVisibleAllDayRows = maxVisibleAllDayRows,
            resizeHandleWidth = DefaultResizeHandleWidth,
        )

    /**
     * Builds [ScheduleStrings]. Defaults are resolved from the current
     * locale's resources; override any parameter to replace a string.
     * [createAt] and [dragTimeRange] are raw templates with `%1$s` / `%2$s` placeholders.
     */
    @Composable
    @ReadOnlyComposable
    public fun strings(
        allDayLabel: String = orreryString(OrreryString.AgendaAllDay),
        nowLabel: String = orreryString(OrreryString.ScheduleNow),
        moveEarlier: String = orreryString(OrreryString.ScheduleMoveEarlier),
        moveLater: String = orreryString(OrreryString.ScheduleMoveLater),
        movePreviousDay: String = orreryString(OrreryString.ScheduleMovePreviousDay),
        moveNextDay: String = orreryString(OrreryString.ScheduleMoveNextDay),
        extendEnd: String = orreryString(OrreryString.ScheduleExtendEnd),
        shortenEnd: String = orreryString(OrreryString.ScheduleShortenEnd),
        createAt: String = orreryString(OrreryString.ScheduleCreateAt),
        dragTimeRange: String = orreryString(OrreryString.ScheduleDragTimeRange),
    ): ScheduleStrings =
        ScheduleStrings(
            allDayLabel = allDayLabel,
            nowLabel = nowLabel,
            moveEarlier = moveEarlier,
            moveLater = moveLater,
            movePreviousDay = movePreviousDay,
            moveNextDay = moveNextDay,
            extendEnd = extendEnd,
            shortenEnd = shortenEnd,
            createAt = createAt,
            dragTimeRange = dragTimeRange,
        )

    @Deprecated(
        "Kept for binary compatibility; use the overload with the editing strings.",
        level = DeprecationLevel.HIDDEN,
    )
    @Composable
    @ReadOnlyComposable
    public fun strings(
        allDayLabel: String = orreryString(OrreryString.AgendaAllDay),
        nowLabel: String = orreryString(OrreryString.ScheduleNow),
    ): ScheduleStrings =
        strings(
            allDayLabel = allDayLabel,
            nowLabel = nowLabel,
            moveEarlier = orreryString(OrreryString.ScheduleMoveEarlier),
        )

    /**
     * Default ghost tile of an edit in flight (see `ghostContent` on [DaySchedule]):
     * a translucent tile with a [ScheduleDimensions.ghostBorderWidth] outline at the
     * snapped position, the event title (none while creating) and a time chip
     * floating above it, or below it near the top of the grid.
     *
     * @param is24Hour Clock for the time chip; defaults to the device setting.
     */
    @Composable
    public fun DragGhost(
        preview: ScheduleDragPreview<*>,
        colors: ScheduleColors,
        dimensions: ScheduleDimensions = dimensions(),
        modifier: Modifier = Modifier,
        strings: ScheduleStrings = strings(),
        is24Hour: Boolean = systemUses24HourClock(),
    ) {
        val scheme = MaterialTheme.colorScheme
        val event = preview.event
        val palette = if (event != null) eventPalette(event, colors) else null
        val accent = palette?.accent ?: scheme.primary
        val container = (palette?.accent ?: scheme.primaryContainer).copy(alpha = 0.55f).compositeOver(scheme.surface)
        val content = readableContentColor(container, scheme.onSurface, scheme.inverseOnSurface)
        val shape = RoundedCornerShape(dimensions.tileCornerRadius)
        val range = previewMinutes(preview)
        val startLabel = clockLabel(range.start, is24Hour)
        val endLabel = clockLabel(range.end, is24Hour)
        val description = fillTemplate(strings.dragTimeRange, startLabel, endLabel)
        val chipBelow = LocalGhostChipBelow.current
        val elevation = if (preview.kind == ScheduleDragKind.Move) 4.dp else 0.dp
        Box(
            modifier
                .fillMaxSize()
                .shadow(elevation, shape, clip = false)
                .background(container, shape)
                .border(dimensions.ghostBorderWidth, accent, shape),
        ) {
            if (event != null) {
                Text(
                    text = event.title,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                    style = MaterialTheme.typography.labelMedium,
                    color = content,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Layout(
                content = {
                    Text(
                        text = "$startLabel – $endLabel",
                        modifier =
                            Modifier
                                .background(scheme.inverseSurface, CircleShape)
                                .padding(horizontal = 8.dp, vertical = 2.dp)
                                .semantics {
                                    liveRegion = LiveRegionMode.Polite
                                    contentDescription = description
                                },
                        style = MaterialTheme.typography.labelMedium,
                        color = scheme.inverseOnSurface,
                        maxLines = 1,
                        softWrap = false,
                    )
                },
                modifier = Modifier.matchParentSize(),
            ) { measurables, constraints ->
                val chip = measurables.first().measure(Constraints())
                val gap = 6.dp.roundToPx()
                layout(constraints.maxWidth, constraints.maxHeight) {
                    val y = if (chipBelow) constraints.maxHeight + gap else -chip.height - gap
                    chip.place((constraints.maxWidth - chip.width) / 2, y)
                }
            }
        }
    }

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

    /**
     * Default all-day chip shown above the time grid. Inside a schedule's
     * all-day row, an end whose event continues past the visible days is drawn
     * square and flush with the column edge.
     */
    @Composable
    public fun AllDayEventChip(
        event: CalendarEvent<*>,
        colors: ScheduleColors,
        dimensions: ScheduleDimensions = dimensions(),
        modifier: Modifier = Modifier,
        onClick: (() -> Unit)? = null,
    ) {
        val palette = eventPalette(event, colors)
        val edges = LocalAllDayBannerEdges.current
        val radius = dimensions.tileCornerRadius
        val startRadius = if (edges.clippedStart) 0.dp else radius
        val endRadius = if (edges.clippedEnd) 0.dp else radius
        val shape =
            RoundedCornerShape(
                topStart = startRadius,
                bottomStart = startRadius,
                topEnd = endRadius,
                bottomEnd = endRadius,
            )
        val interactionSource = remember { MutableInteractionSource() }
        val isFocused by interactionSource.collectIsFocusedAsState()
        Row(
            modifier =
                modifier
                    .fillMaxWidth()
                    .height(dimensions.allDayChipHeight)
                    .eventFocusRing(onClick != null && isFocused, shape, MaterialTheme.colorScheme.onSurface)
                    .clip(shape)
                    .background(palette.container.compositeOver(MaterialTheme.colorScheme.surface))
                    .then(
                        if (onClick != null) {
                            Modifier.clickable(
                                interactionSource = interactionSource,
                                indication = LocalIndication.current,
                                role = Role.Button,
                                onClick = onClick,
                            )
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

    /**
     * Default hour label in the time gutter, e.g. "08:00" or "8:00 AM".
     *
     * @param is24Hour Clock for the label; defaults to the device setting.
     */
    @Composable
    public fun HourLabel(
        hour: Int,
        colors: ScheduleColors,
        modifier: Modifier = Modifier,
        is24Hour: Boolean = systemUses24HourClock(),
    ) {
        Text(
            text = clockLabel(hour * 60, is24Hour),
            modifier = modifier.fillMaxWidth().padding(end = HourLabelEndPadding),
            style = MaterialTheme.typography.labelSmall,
            color = colors.hourLabelColor,
            textAlign = TextAlign.End,
            maxLines = 1,
            softWrap = false,
        )
    }

    @Deprecated("Kept for binary compatibility; use the overload with is24Hour.", level = DeprecationLevel.HIDDEN)
    @Composable
    public fun HourLabel(
        hour: Int,
        colors: ScheduleColors,
        modifier: Modifier = Modifier,
    ) {
        HourLabel(hour, colors, modifier, is24Hour = systemUses24HourClock())
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
     * Default timed event tile, filled opaque (the event tint composited over
     * the surface) so grid lines never show through: a leading accent
     * stripe, the title, the time range clipped to the column's day, and an
     * optional subtitle.
     *
     * @param is24Hour Clock for the time range; defaults to the device setting.
     */
    @Composable
    public fun EventTile(
        positioned: PositionedEvent<*>,
        colors: ScheduleColors,
        dimensions: ScheduleDimensions = dimensions(),
        modifier: Modifier = Modifier,
        onClick: (() -> Unit)? = null,
        is24Hour: Boolean = systemUses24HourClock(),
    ) {
        val event = positioned.event
        val palette = eventPalette(event, colors)
        val surface = MaterialTheme.colorScheme.surface
        val isStacked =
            positioned.columnCount > 1 &&
                positioned.leftFraction + positioned.widthFraction > 0.99f &&
                (positioned.columnIndex == 0 || positioned.widthFraction > 0.501f)
        val containerColor = palette.container.compositeOver(surface)
        val shape = RoundedCornerShape(dimensions.tileCornerRadius)
        val borderModifier =
            if (isStacked && positioned.columnIndex > 0) Modifier.border(1.dp, surface, shape) else Modifier
        val interactionSource = remember { MutableInteractionSource() }
        val isFocused by interactionSource.collectIsFocusedAsState()
        Row(
            modifier =
                modifier
                    .fillMaxSize()
                    .eventFocusRing(onClick != null && isFocused, shape, MaterialTheme.colorScheme.onSurface)
                    .then(borderModifier)
                    .clip(shape)
                    .background(containerColor)
                    .then(
                        if (onClick != null) {
                            Modifier.clickable(
                                interactionSource = interactionSource,
                                indication = LocalIndication.current,
                                role = Role.Button,
                                onClick = onClick,
                            )
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
            BoxWithConstraints(Modifier.padding(horizontal = 4.dp, vertical = 2.dp)) {
                val titleStyle = MaterialTheme.typography.labelMedium
                val titleMaxLines = rememberTitleMaxLines(event.title, titleStyle, maxWidth, maxHeight)
                val timeStyle = MaterialTheme.typography.labelSmall
                val timeText =
                    rememberTileTimeText(positioned.startMinute, positioned.endMinute, is24Hour, timeStyle, maxWidth)
                FittingColumn(Modifier) {
                    Text(
                        text = event.title,
                        style = titleStyle,
                        color = palette.content,
                        maxLines = titleMaxLines,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = timeText,
                        style = timeStyle,
                        color = palette.content.copy(alpha = 0.8f),
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Ellipsis,
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

    @Deprecated("Kept for binary compatibility; use the overload with is24Hour.", level = DeprecationLevel.HIDDEN)
    @Composable
    public fun EventTile(
        positioned: PositionedEvent<*>,
        colors: ScheduleColors,
        dimensions: ScheduleDimensions = dimensions(),
        modifier: Modifier = Modifier,
        onClick: (() -> Unit)? = null,
    ) {
        EventTile(positioned, colors, dimensions, modifier, onClick, is24Hour = systemUses24HourClock())
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

private const val MAX_TITLE_LINES = 2

/** End padding of the default hour label; the gutter reserves it when sizing to the labels. */
internal val HourLabelEndPadding = 8.dp

/**
 * Title lines for a tile: at most [MAX_TITLE_LINES], no more than whole lines
 * fit in [maxHeight], and one ellipsized line when the first word is wider
 * than [maxWidth], so narrow week columns never break a word mid-way.
 */
@Composable
private fun rememberTitleMaxLines(
    title: String,
    style: TextStyle,
    maxWidth: Dp,
    maxHeight: Dp,
): Int {
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    return remember(title, style, maxWidth, maxHeight, density, measurer) {
        with(density) {
            val lineHeightPx = measurer.measure("A", style).size.height
            val byHeight =
                if (maxHeight == Dp.Infinity || lineHeightPx == 0) {
                    MAX_TITLE_LINES
                } else {
                    (maxHeight.toPx() / lineHeightPx).toInt()
                }
            // With two lines at most, only a too-wide first word gets split across lines;
            // a later one wraps to the last line and is ellipsized there.
            val firstWord = title.trimStart().substringBefore(' ')
            val wordWidthPx = measurer.measure(firstWord, style, softWrap = false, maxLines = 1).size.width
            val byWidth = if (maxWidth != Dp.Infinity && wordWidthPx > maxWidth.toPx()) 1 else MAX_TITLE_LINES
            minOf(byHeight, byWidth).coerceIn(1, MAX_TITLE_LINES)
        }
    }
}

/**
 * The tile's time line: the whole range when it fits [maxWidth], otherwise just the
 * start time (long 12-hour ranges in narrow week columns), never a range cut mid-way.
 */
@Composable
private fun rememberTileTimeText(
    startMinute: Int,
    endMinute: Int,
    is24Hour: Boolean,
    style: TextStyle,
    maxWidth: Dp,
): String {
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    return remember(startMinute, endMinute, is24Hour, style, maxWidth, density, measurer) {
        val start = clockLabel(startMinute, is24Hour)
        val range = start + " – " + clockLabel(endMinute, is24Hour)
        val rangeWidth = measurer.measure(range, style, softWrap = false, maxLines = 1).size.width
        if (maxWidth == Dp.Infinity || rangeWidth <= with(density) { maxWidth.toPx() }) range else start
    }
}

internal class EventPalette(
    val container: Color,
    val content: Color,
    val accent: Color,
)

@Composable
@ReadOnlyComposable
internal fun eventPalette(
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
