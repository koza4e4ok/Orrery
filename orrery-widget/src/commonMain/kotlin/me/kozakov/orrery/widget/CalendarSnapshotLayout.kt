@file:OptIn(ExperimentalOrreryApi::class, InternalOrreryApi::class)

package me.kozakov.orrery.widget

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Density
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.YearMonth
import kotlinx.datetime.isoDayNumber
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import me.kozakov.orrery.ExperimentalOrreryApi
import me.kozakov.orrery.InternalOrreryApi
import me.kozakov.orrery.compose.internal.MonthGridModel
import me.kozakov.orrery.core.Selection

internal const val DAYS_PER_WEEK = 7

/** At most this many event dots per day. */
internal const val MAX_EVENT_DOTS = 3

/** Height of one text line as a multiple of its font size, with some room above and below. */
internal const val LINE_HEIGHT_FACTOR = 1.4f

/** A day number may take up to this share of the smaller cell side. */
internal const val DAY_TEXT_CELL_FACTOR = 0.5f

/** A weekday label may take up to this share of the cell width. */
internal const val WEEKDAY_TEXT_CELL_FACTOR = 0.5f

/** One cell of a month or week grid, in logical (start-to-end) [column] order. */
internal class SnapshotCell(
    val date: LocalDate,
    val row: Int,
    val column: Int,
    val isOutside: Boolean,
)

/** The cells of a month or week grid with [rowCount] week rows; outside days only when requested. */
internal class SnapshotGrid(
    val rowCount: Int,
    val cells: List<SnapshotCell>,
) {
    /** The weekday of each logical column. */
    fun weekdays(firstDayOfWeek: DayOfWeek): List<DayOfWeek> =
        List(DAYS_PER_WEEK) { DayOfWeek((firstDayOfWeek.isoDayNumber - 1 + it) % DAYS_PER_WEEK + 1) }
}

/** The first day of the week (starting on [firstDayOfWeek]) that contains [date]. */
internal fun weekStart(
    date: LocalDate,
    firstDayOfWeek: DayOfWeek,
): LocalDate {
    val daysBack = (date.dayOfWeek.isoDayNumber - firstDayOfWeek.isoDayNumber).mod(DAYS_PER_WEEK)
    return date.minus(daysBack, DateTimeUnit.DAY)
}

/** One row of seven days: the week of [date]. */
internal fun weekGrid(
    date: LocalDate,
    firstDayOfWeek: DayOfWeek,
): SnapshotGrid {
    val start = weekStart(date, firstDayOfWeek)
    return SnapshotGrid(
        rowCount = 1,
        cells = List(DAYS_PER_WEEK) { SnapshotCell(start.plus(it, DateTimeUnit.DAY), 0, it, isOutside = false) },
    )
}

/**
 * The grid of [month]: 4 to 6 rows (as many as the month needs, never padded to 6).
 * With [showOutsideDays] the blank cells of the first and last row hold the
 * neighbouring months' days.
 */
internal fun monthGrid(
    month: YearMonth,
    firstDayOfWeek: DayOfWeek,
    showOutsideDays: Boolean,
): SnapshotGrid {
    val model = MonthGridModel(month, firstDayOfWeek)
    val first = month.firstDay
    val cells = ArrayList<SnapshotCell>(model.rowCount * DAYS_PER_WEEK)
    val total = model.rowCount * DAYS_PER_WEEK
    for (index in 0 until total) {
        val offset = index - model.leadingBlanks
        val inMonth = offset in 0 until model.dayCount
        if (inMonth || showOutsideDays) {
            val date = first.plus(offset, DateTimeUnit.DAY)
            cells += SnapshotCell(date, index / DAYS_PER_WEEK, index % DAYS_PER_WEEK, isOutside = !inMonth)
        }
    }
    return SnapshotGrid(model.rowCount, cells)
}

/** How a day takes part in the selection. */
internal enum class SelectionMark {
    None,
    Single,
    RangeStart,
    RangeMiddle,
    RangeEnd,
    ;

    /** Whether the day gets the selection pill (and `selectionContent` for its number). */
    val isEndpoint: Boolean
        get() = this == Single || this == RangeStart || this == RangeEnd
}

/**
 * The selection mark of [date]. A range wins over single and multi selection; a range
 * with only a start, or with equal ends, marks one day; reversed ends are normalised.
 */
internal fun selectionMark(
    date: LocalDate,
    selection: Selection,
): SelectionMark {
    val start = selection.rangeStart
    if (start != null) {
        val end = selection.rangeEnd ?: start
        val low = minOf(start, end)
        val high = maxOf(start, end)
        return when {
            date < low || date > high -> SelectionMark.None
            low == high -> SelectionMark.Single
            date == low -> SelectionMark.RangeStart
            date == high -> SelectionMark.RangeEnd
            else -> SelectionMark.RangeMiddle
        }
    }
    return if (date == selection.single || date in selection.multi) SelectionMark.Single else SelectionMark.None
}

/** Number of event dots for [count] events: 0 to [MAX_EVENT_DOTS]. */
internal fun eventDotCount(count: Int?): Int = (count ?: 0).coerceIn(0, MAX_EVENT_DOTS)

/** Everything the painter draws for one cell. Outside days carry no marks. */
internal class SnapshotDayState(
    val cell: SnapshotCell,
    val selection: SelectionMark,
    val isToday: Boolean,
    val tint: Color?,
    val dots: Int,
)

/** The marks of every cell of [grid]; [dayInfo][CalendarSnapshotSpec.dayInfo] is called once per in-range day. */
internal fun dayStates(
    grid: SnapshotGrid,
    spec: CalendarSnapshotSpec,
    colors: CalendarSnapshotColors,
): List<SnapshotDayState> =
    grid.cells.map { cell ->
        if (cell.isOutside) {
            SnapshotDayState(cell, SelectionMark.None, isToday = false, tint = null, dots = 0)
        } else {
            val info = spec.dayInfo?.info(cell.date)
            val argb = info?.colorArgb
            val tint =
                when {
                    argb != null -> Color(argb)
                    info?.isHoliday == true -> colors.holiday
                    else -> null
                }
            SnapshotDayState(
                cell = cell,
                selection = selectionMark(cell.date, spec.selection),
                isToday = cell.date == spec.today,
                tint = tint,
                dots = eventDotCount(spec.eventCounts[cell.date]),
            )
        }
    }

/**
 * Which rows of a month or week grid are drawn, and at which text sizes (px).
 * [dayTextPx] is 0 when even the bare grid is too small for readable numbers.
 */
internal class GridTextPlan(
    val showTitle: Boolean,
    val showWeekdays: Boolean,
    val titleTextPx: Float,
    val weekdayTextPx: Float,
    val dayTextPx: Float,
) {
    val titleBand: Float
        get() = if (showTitle) titleTextPx * LINE_HEIGHT_FACTOR else 0f
    val weekdayBand: Float
        get() = if (showWeekdays) weekdayTextPx * LINE_HEIGHT_FACTOR else 0f
    val showDayNumbers: Boolean
        get() = dayTextPx > 0f
}

/**
 * The requested text sizes of a snapshot in px (with [fontScale] applied), and its wishes.
 *
 * The shrink floor is `minTextScale` times the size at font scale 1, never more than the
 * requested size: a larger font makes text bigger while the cells allow it, but never
 * raises the floor, so it cannot make the day numbers disappear.
 */
internal class GridTextRequest(
    val titleTextPx: Float,
    val weekdayTextPx: Float,
    val dayTextPx: Float,
    val minTextScale: Float,
    val showTitle: Boolean,
    val showWeekdays: Boolean,
    val fontScale: Float = 1f,
) {
    init {
        require(fontScale > 0f) { "fontScale must be positive, was $fontScale" }
    }

    /** Smallest day number size in px: [minTextScale] of the unscaled request. */
    val dayFloorPx: Float
        get() = floor(dayTextPx)

    /** Smallest weekday label size in px: [minTextScale] of the unscaled request. */
    val weekdayFloorPx: Float
        get() = floor(weekdayTextPx)

    private fun floor(requestedPx: Float): Float = minOf(requestedPx, minTextScale * requestedPx / fontScale)
}

/** The text request of a grid drawn with [style] and [spec] at [density] (font scale included). */
internal fun gridTextRequest(
    style: CalendarSnapshotStyle,
    spec: CalendarSnapshotSpec,
    density: Density,
): GridTextRequest =
    with(density) {
        GridTextRequest(
            titleTextPx = style.titleTextSize.toPx(),
            weekdayTextPx = style.weekdayTextSize.toPx(),
            dayTextPx = style.dayTextSize.toPx(),
            minTextScale = style.minTextScale,
            showTitle = spec.showTitle,
            showWeekdays = spec.showWeekdayLabels,
            fontScale = fontScale,
        )
    }

/**
 * Sizes the text of a [rows]-row grid in a [width] × [height] px content box.
 *
 * Day numbers are `min(requested, cell × DAY_TEXT_CELL_FACTOR)` and weekday labels
 * `min(requested, cell width × WEEKDAY_TEXT_CELL_FACTOR)`; neither goes below its floor,
 * `minTextScale` times the size at font scale 1 (see [GridTextRequest]). Cells shrink
 * first; when the day numbers would go below that floor, the title row is dropped, then
 * the weekday row (a weekday row too narrow for its floor is dropped at once). Only if the
 * bare grid is too small for the floor are the day numbers dropped too and only the marks
 * drawn; since the floor ignores font scale, that never depends on the font scale.
 */
internal fun planGridText(
    width: Float,
    height: Float,
    rows: Int,
    request: GridTextRequest,
): GridTextPlan {
    val cellWidth = width / DAYS_PER_WEEK
    val weekdayPx = minOf(request.weekdayTextPx, cellWidth * WEEKDAY_TEXT_CELL_FACTOR)
    val weekdaysFit = request.showWeekdays && weekdayPx >= request.weekdayFloorPx
    val candidates = listOf(request.showTitle to weekdaysFit, false to weekdaysFit, false to false).distinct()
    for ((title, weekdays) in candidates) {
        val plan = GridTextPlan(title, weekdays, request.titleTextPx, weekdayPx, dayTextPx = 0f)
        val gridHeight = height - plan.titleBand - plan.weekdayBand
        if (gridHeight > 0f && width > 0f) {
            val cell = minOf(cellWidth, gridHeight / rows)
            val dayPx = minOf(request.dayTextPx, cell * DAY_TEXT_CELL_FACTOR)
            if (dayPx >= request.dayFloorPx) {
                return GridTextPlan(title, weekdays, request.titleTextPx, weekdayPx, dayPx)
            }
        }
    }
    return GridTextPlan(
        showTitle = false,
        showWeekdays = false,
        titleTextPx = request.titleTextPx,
        weekdayTextPx = weekdayPx,
        dayTextPx = 0f,
    )
}
