package me.kozakov.orrery.compose

import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.ZeroCornerSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.datetime.LocalDate
import me.kozakov.orrery.core.CalendarEvent
import me.kozakov.orrery.core.CalendarWeek
import me.kozakov.orrery.core.WeekBannerPackResult
import me.kozakov.orrery.core.WeekEventBanner
import me.kozakov.orrery.core.packWeekEventBanners
import kotlin.math.roundToInt

/**
 * Google Calendar-style multi-day event banners for one week row, laid out
 * from a [packWeekEventBanners] result.
 *
 * Meant for the `weekOverlay` slot of [HorizontalCalendar], [VerticalCalendar]
 * and [WeekCalendar]: the banners fill the full width, split it into one
 * column per [WeekBannerPackResult.weekDates] entry, and start [topInset]
 * below the top so the day numbers stay visible. Each banner shows the event
 * title on one ellipsized line over its `colorArgb` (or the theme's
 * `primaryContainer`) with a readable text color. Its ends are rounded,
 * except a side where the event continues into the previous or next week,
 * which is cut flat at the row edge.
 *
 * Columns with events that did not fit get an [overflowLabel] indicator
 * (`+N` by default) in the lane below the banners.
 *
 * The height is `topInset` plus the used lanes; inside a `weekOverlay` that
 * is taller than the day row, the row grows to fit, or pass
 * `Modifier.matchParentSize()` to keep the row's height.
 *
 * @param packResult Lanes for one week row, from [packWeekEventBanners].
 * @param laneHeight Height of one banner lane.
 * @param laneSpacing Vertical gap between lanes.
 * @param topInset Space above the first lane (the day number area).
 * @param onEventClick Called with the event when a banner is clicked; banners
 *   are not clickable when null.
 * @param onOverflowClick Called with the column's date and hidden-event count
 *   when an overflow indicator is clicked; not clickable when null.
 * @param overflowLabel Text of a column's overflow indicator for its
 *   hidden-event count, for example a localized "+N more".
 */
@Composable
public fun <T> WeekEventBanners(
    packResult: WeekBannerPackResult<T>,
    modifier: Modifier = Modifier,
    laneHeight: Dp = 18.dp,
    laneSpacing: Dp = 2.dp,
    topInset: Dp = 24.dp,
    onEventClick: ((CalendarEvent<T>) -> Unit)? = null,
    onOverflowClick: ((LocalDate, Int) -> Unit)? = null,
    overflowLabel: (count: Int) -> String = DefaultOverflowLabel,
) {
    WeekEventBannersContent(
        packResult = packResult,
        modifier = modifier,
        metrics = BannerMetrics(laneHeight, laneSpacing, topInset),
        onEventClick = onEventClick,
        onOverflowClick = onOverflowClick,
        overflowLabel = overflowLabel,
    )
}

/**
 * Packs [events] for [week] with [packWeekEventBanners] and draws them with
 * [WeekEventBanners]; drop it straight into a `weekOverlay` slot.
 *
 * @param maxVisibleLanes Banner lanes before events overflow into `+N`.
 */
@Composable
public fun <T> WeekEventBanners(
    week: CalendarWeek,
    events: List<CalendarEvent<T>>,
    modifier: Modifier = Modifier,
    maxVisibleLanes: Int = 3,
    laneHeight: Dp = 18.dp,
    laneSpacing: Dp = 2.dp,
    topInset: Dp = 24.dp,
    onEventClick: ((CalendarEvent<T>) -> Unit)? = null,
    onOverflowClick: ((LocalDate, Int) -> Unit)? = null,
    overflowLabel: (count: Int) -> String = DefaultOverflowLabel,
) {
    val packResult =
        remember(week, events, maxVisibleLanes) {
            packWeekEventBanners(week.days.map { it.date }, events, maxVisibleLanes)
        }
    WeekEventBannersContent(
        packResult = packResult,
        modifier = modifier,
        metrics = BannerMetrics(laneHeight, laneSpacing, topInset),
        onEventClick = onEventClick,
        onOverflowClick = onOverflowClick,
        overflowLabel = overflowLabel,
    )
}

private val DefaultOverflowLabel: (Int) -> String = { "+$it" }

private class BannerMetrics(
    val laneHeight: Dp,
    val laneSpacing: Dp,
    val topInset: Dp,
)

/** Where one child of the banner layout goes: columns [start]..[end] of lane [lane]. */
private class BannerSlot(
    val start: Int,
    val end: Int,
    val lane: Int,
    val insetStart: Boolean,
    val insetEnd: Boolean,
)

private val BannerEdgeInset = 2.dp
private val BannerTextPadding = 4.dp

@Composable
private fun <T> WeekEventBannersContent(
    packResult: WeekBannerPackResult<T>,
    modifier: Modifier,
    metrics: BannerMetrics,
    onEventClick: ((CalendarEvent<T>) -> Unit)?,
    onOverflowClick: ((LocalDate, Int) -> Unit)?,
    overflowLabel: (Int) -> String,
) {
    val columns = packResult.weekDates.size
    val banners = packResult.banners.filter { it.startColumn in 0 until columns && it.endColumnInclusive < columns }
    // Every lane up to the highest used one is full somewhere when an event overflows,
    // so the overflow row sits right below the banners.
    val overflowLane = (banners.maxOfOrNull { it.laneIndex } ?: -1) + 1
    val overflowColumns =
        packResult.overflowCountByColumn.withIndex().filter { (col, count) -> count > 0 && col < columns }
    val slots =
        banners.map {
            BannerSlot(
                start = it.startColumn,
                end = it.endColumnInclusive,
                lane = it.laneIndex,
                insetStart = !it.continuesFromPreviousWeek,
                insetEnd = !it.continuesIntoNextWeek,
            )
        } + overflowColumns.map { (col, _) -> BannerSlot(col, col, overflowLane, insetStart = true, insetEnd = true) }
    val usedLanes = if (overflowColumns.isEmpty()) overflowLane else overflowLane + 1
    Layout(
        modifier = modifier,
        content = {
            banners.forEach { banner -> BannerChip(banner, onEventClick) }
            overflowColumns.forEach { (col, count) ->
                val date = packResult.weekDates[col]
                OverflowChip(
                    text = overflowLabel(count),
                    onClick = onOverflowClick?.let { { it(date, count) } },
                )
            }
        },
    ) { measurables, constraints ->
        val width = if (constraints.hasBoundedWidth) constraints.maxWidth else constraints.minWidth
        val laneHeight = metrics.laneHeight.roundToPx()
        val laneStep = laneHeight + metrics.laneSpacing.roundToPx()
        val topInset = metrics.topInset.roundToPx()
        val edgeInset = BannerEdgeInset.roundToPx()
        val contentHeight =
            if (usedLanes == 0) 0 else topInset + usedLanes * laneStep - metrics.laneSpacing.roundToPx()
        val height = contentHeight.coerceIn(constraints.minHeight, constraints.maxHeight)
        val columnWidth = if (columns == 0) 0f else width.toFloat() / columns
        val placeables =
            measurables.zip(slots) { measurable, slot ->
                val left = (slot.start * columnWidth).roundToInt() + if (slot.insetStart) edgeInset else 0
                val right = ((slot.end + 1) * columnWidth).roundToInt() - if (slot.insetEnd) edgeInset else 0
                val chipWidth = (right - left).coerceAtLeast(0)
                val placeable = measurable.measure(Constraints.fixed(chipWidth, laneHeight))
                Triple(placeable, left, topInset + slot.lane * laneStep)
            }
        layout(width, height) {
            // placeRelative mirrors x in RTL, so column 0 is the start side in both directions.
            placeables.forEach { (placeable, x, y) -> placeable.placeRelative(x, y) }
        }
    }
}

@Composable
private fun <T> BannerChip(
    banner: WeekEventBanner<T>,
    onEventClick: ((CalendarEvent<T>) -> Unit)?,
) {
    val event = banner.event
    val colorScheme = MaterialTheme.colorScheme
    val container = event.colorArgb?.let(::Color) ?: colorScheme.primaryContainer
    val content =
        if (event.colorArgb == null) {
            colorScheme.onPrimaryContainer
        } else {
            readableContentColor(
                background = container.compositeOver(colorScheme.surface),
                onSurface = colorScheme.onSurface,
                inverseOnSurface = colorScheme.inverseOnSurface,
            )
        }
    val shape = bannerShape(banner.continuesFromPreviousWeek, banner.continuesIntoNextWeek)
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    Box(
        modifier =
            Modifier
                .eventFocusRing(onEventClick != null && isFocused, shape, colorScheme.onSurface)
                .clip(shape)
                .background(container)
                .then(
                    if (onEventClick != null) {
                        Modifier.clickable(
                            interactionSource = interactionSource,
                            indication = LocalIndication.current,
                            role = Role.Button,
                            onClick = { onEventClick(event) },
                        )
                    } else {
                        Modifier
                    },
                ).padding(horizontal = BannerTextPadding),
        contentAlignment = Alignment.CenterStart,
    ) {
        Text(
            text = event.title,
            color = content,
            style = MaterialTheme.typography.labelSmall,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun OverflowChip(
    text: String,
    onClick: (() -> Unit)?,
) {
    val shape = RoundedCornerShape(CornerSize(50))
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    Box(
        modifier =
            Modifier
                .eventFocusRing(onClick != null && isFocused, shape, MaterialTheme.colorScheme.onSurface)
                .clip(shape)
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
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.labelSmall,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** Pill ends, cut flat on a side where the event continues past the week. */
internal fun bannerShape(
    continuesFromPreviousWeek: Boolean,
    continuesIntoNextWeek: Boolean,
): Shape {
    val startCorner = if (continuesFromPreviousWeek) ZeroCornerSize else CornerSize(50)
    val endCorner = if (continuesIntoNextWeek) ZeroCornerSize else CornerSize(50)
    return RoundedCornerShape(
        topStart = startCorner,
        topEnd = endCorner,
        bottomEnd = endCorner,
        bottomStart = startCorner,
    )
}
