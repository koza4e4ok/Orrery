package me.kozakov.orrery.widget

import androidx.compose.foundation.layout.Spacer
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFontFamilyResolver
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import me.kozakov.orrery.ExperimentalOrreryApi
import me.kozakov.orrery.core.CalendarEvent
import me.kozakov.orrery.core.DayInfoProvider
import me.kozakov.orrery.core.Selection
import kotlin.math.sqrt

/**
 * What a calendar snapshot shows and how: the [range] plus the day marks drawn on it.
 *
 * The snapshot never reads the clock or the locale's first day of the week: pass [today]
 * and [firstDayOfWeek] explicitly, so the same spec always draws the same picture.
 * This is a plain class with a hand-written [copy], so new fields can be added later
 * without breaking callers.
 */
@ExperimentalOrreryApi
@Suppress("LongParameterList")
public class CalendarSnapshotSpec(
    /** What to draw: a month, a week or an agenda strip. */
    public val range: CalendarSnapshotRange,
    /** The current date, highlighted when it is inside [range]; null for no highlight. */
    public val today: LocalDate? = null,
    /** Selected dates (single, range or multiple), drawn like `MiniMonth`'s selection. */
    public val selection: Selection = Selection.Empty,
    /**
     * Per-day information. A day is tinted with [me.kozakov.orrery.core.DayInfo.colorArgb] when it
     * is set, else with the holiday colour when [me.kozakov.orrery.core.DayInfo.isHoliday] is true.
     * Labels are not drawn.
     */
    public val dayInfo: DayInfoProvider? = null,
    /** Number of events per day, drawn as one to three dots; counts above three draw three. */
    public val eventCounts: Map<LocalDate, Int> = emptyMap(),
    /**
     * Events of the [CalendarSnapshotRange.Agenda] strip; ignored by the month and week ranges.
     * Recurring events are not expanded here: pass occurrences, for example from
     * [me.kozakov.orrery.core.occurrencesIn] or [me.kozakov.orrery.core.expandRecurring].
     */
    public val agendaEvents: List<CalendarEvent<*>> = emptyList(),
    /** Weekday of the first column of month and week grids, and the start of [CalendarSnapshotRange.Week]. */
    public val firstDayOfWeek: DayOfWeek = DayOfWeek.MONDAY,
    /** Whether to draw the title row; it is also dropped when the output is too small for it. */
    public val showTitle: Boolean = true,
    /** Whether to draw the weekday row of month and week grids; it is also dropped when it does not fit. */
    public val showWeekdayLabels: Boolean = true,
    /** Month range only: draw the leading and trailing days of the neighbouring months, without marks. */
    public val showOutsideDays: Boolean = false,
) {
    /** Returns a copy of this spec with the given fields replaced. */
    @Suppress("LongParameterList")
    public fun copy(
        range: CalendarSnapshotRange = this.range,
        today: LocalDate? = this.today,
        selection: Selection = this.selection,
        dayInfo: DayInfoProvider? = this.dayInfo,
        eventCounts: Map<LocalDate, Int> = this.eventCounts,
        agendaEvents: List<CalendarEvent<*>> = this.agendaEvents,
        firstDayOfWeek: DayOfWeek = this.firstDayOfWeek,
        showTitle: Boolean = this.showTitle,
        showWeekdayLabels: Boolean = this.showWeekdayLabels,
        showOutsideDays: Boolean = this.showOutsideDays,
    ): CalendarSnapshotSpec =
        CalendarSnapshotSpec(
            range = range,
            today = today,
            selection = selection,
            dayInfo = dayInfo,
            eventCounts = eventCounts,
            agendaEvents = agendaEvents,
            firstDayOfWeek = firstDayOfWeek,
            showTitle = showTitle,
            showWeekdayLabels = showWeekdayLabels,
            showOutsideDays = showOutsideDays,
        )

    override fun equals(other: Any?): Boolean =
        other is CalendarSnapshotSpec &&
            range == other.range &&
            today == other.today &&
            selection == other.selection &&
            dayInfo == other.dayInfo &&
            eventCounts == other.eventCounts &&
            agendaEvents == other.agendaEvents &&
            firstDayOfWeek == other.firstDayOfWeek &&
            showTitle == other.showTitle &&
            showWeekdayLabels == other.showWeekdayLabels &&
            showOutsideDays == other.showOutsideDays

    override fun hashCode(): Int {
        var result = range.hashCode()
        result = 31 * result + today.hashCode()
        result = 31 * result + selection.hashCode()
        result = 31 * result + dayInfo.hashCode()
        result = 31 * result + eventCounts.hashCode()
        result = 31 * result + agendaEvents.hashCode()
        result = 31 * result + firstDayOfWeek.hashCode()
        result = 31 * result + showTitle.hashCode()
        result = 31 * result + showWeekdayLabels.hashCode()
        result = 31 * result + showOutsideDays.hashCode()
        return result
    }

    override fun toString(): String =
        "CalendarSnapshotSpec(range=$range, today=$today, selection=$selection, dayInfo=$dayInfo, " +
            "eventCounts=$eventCounts, agendaEvents=${agendaEvents.size} events, firstDayOfWeek=$firstDayOfWeek, " +
            "showTitle=$showTitle, showWeekdayLabels=$showWeekdayLabels, showOutsideDays=$showOutsideDays)"
}

/**
 * Shows the snapshot of [spec] in the app, drawn by the same painter as
 * [renderCalendarSnapshot], so an in-app preview matches the widget bitmap pixel for pixel.
 * The snapshot fills the size given by [modifier] (it has no size of its own) and is
 * drawn into an offscreen bitmap first, with software drawing only.
 *
 * Not interactive. For accessibility the whole snapshot is one node whose content
 * description is the month title, or the date range of a week or agenda strip.
 *
 * A size above the renderer limits (4096 px per side, 4 194 304 px in total) is drawn at
 * a lower resolution and scaled up instead of failing.
 */
@ExperimentalOrreryApi
@Composable
public fun CalendarSnapshot(
    spec: CalendarSnapshotSpec,
    modifier: Modifier = Modifier,
    colors: CalendarSnapshotColors = CalendarSnapshotDefaults.colors(),
    style: CalendarSnapshotStyle = CalendarSnapshotDefaults.style(),
) {
    val localDensity = LocalDensity.current
    val fontFamilyResolver = LocalFontFamilyResolver.current
    val formatter = PlatformSnapshotFormatter()
    val description = snapshotDescription(spec, formatter)
    Spacer(
        modifier
            .semantics { contentDescription = description }
            .drawWithCache {
                val target = IntSize(size.width.toInt(), size.height.toInt())
                val bitmap =
                    snapshotBitmapFor(target, localDensity, fontFamilyResolver, layoutDirection) { output ->
                        drawCalendarSnapshot(spec, output, colors, style, formatter)
                    }
                onDrawBehind {
                    if (bitmap != null) drawImage(bitmap, dstSize = target)
                }
            },
    )
}

/**
 * Renders a [target]-sized snapshot with [render], or a smaller one with a scaled density
 * when [target] is above the renderer limits; null for an empty [target].
 */
internal fun snapshotBitmapFor(
    target: IntSize,
    density: Density,
    fontFamilyResolver: FontFamily.Resolver,
    layoutDirection: LayoutDirection,
    render: (CalendarSnapshotOutput) -> ImageBitmap,
): ImageBitmap? {
    if (target.width < 1 || target.height < 1) return null
    val scale = snapshotDownscale(target.width, target.height)
    val outputDensity = if (scale < 1f) Density(density.density * scale, density.fontScale) else density
    val width = (target.width * scale).toInt().coerceIn(1, MAX_SNAPSHOT_SIDE_PX)
    val height = (target.height * scale).toInt().coerceIn(1, MAX_SNAPSHOT_SIDE_PX)
    return render(CalendarSnapshotOutput(width, height, outputDensity, fontFamilyResolver, layoutDirection))
}

/** The factor (at most 1) that brings a [width] × [height] px snapshot inside the renderer limits. */
internal fun snapshotDownscale(
    width: Int,
    height: Int,
): Float {
    val side = MAX_SNAPSHOT_SIDE_PX.toFloat() / maxOf(width, height)
    val pixels = width.toDouble() * height
    val area = if (pixels <= MAX_SNAPSHOT_AREA_PX) 1f else sqrt(MAX_SNAPSHOT_AREA_PX / pixels).toFloat() * AREA_SLACK
    return minOf(1f, side, area)
}

/** Keeps the rounded-down area of a downscaled snapshot below the limit. */
private const val AREA_SLACK = 0.999f
