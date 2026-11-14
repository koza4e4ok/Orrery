@file:OptIn(ExperimentalOrreryApi::class, InternalOrreryApi::class)

package me.kozakov.orrery.widget

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.inset
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.datetime.YearMonth
import kotlinx.datetime.plus
import me.kozakov.orrery.ExperimentalOrreryApi
import me.kozakov.orrery.InternalOrreryApi
import me.kozakov.orrery.compose.internal.MonthTextCache
import me.kozakov.orrery.compose.internal.MonthTextKey
import me.kozakov.orrery.compose.internal.drawEventDots
import me.kozakov.orrery.compose.internal.drawSelectionPill
import me.kozakov.orrery.compose.internal.drawTodayMark

/** Selection pill and today circle radius as a share of the smaller cell side. */
private const val PILL_RADIUS_FACTOR = 0.42f

/** Event dot diameter as a share of the smaller cell side. */
private const val DOT_DIAMETER_FACTOR = 0.08f

/** Space between event dots as a share of their diameter. */
private const val DOT_SPACING_FACTOR = 0.6f

/** Dot row centre below the cell centre, as a share of the pill radius. */
private const val DOT_OFFSET_FACTOR = 0.75f

/** Height of an agenda event row as a multiple of its font size. */
internal const val AGENDA_ROW_FACTOR = 1.5f

/** Height of the agenda colour bar as a share of the row height. */
private const val AGENDA_BAR_HEIGHT_FACTOR = 0.7f

private val AgendaBarWidth = 3.dp
private val AgendaGap = 6.dp
private val TodayRingWidth = 1.dp

/**
 * Date text of a snapshot. The renderer (#89 task 4) backs it with the platform date
 * formatting of `orrery-compose`; tests use a fixed, locale-free one.
 */
internal interface SnapshotFormatter {
    /** Title of a month grid, for example "November 2026". */
    fun monthTitle(month: YearMonth): String

    /** Title of a week grid or an agenda strip covering [start]..[end]. */
    fun dateRange(
        start: LocalDate,
        end: LocalDate,
    ): String

    /** Weekday column label, for example "M" or "Mon". */
    fun weekdayLabel(dayOfWeek: DayOfWeek): String

    /** Agenda day heading, for example "Sat, Nov 14". */
    fun dayHeading(date: LocalDate): String

    /** Start time of an agenda event row. */
    fun time(time: LocalTime): String
}

/**
 * Draws one snapshot of [spec] into a [DrawScope], with no composition, window or
 * clock: the same inputs draw the same pixels. Keeps no state between [draw] calls; the
 * text measurer and its cache live inside one call. Only software drawing is used (no
 * shadows, blur or layers).
 *
 * Marks are painted in this order: range band, selection pill, today mark, day number
 * (in the holiday or priority colour when set), event dots.
 */
internal class CalendarSnapshotPainter(
    private val spec: CalendarSnapshotSpec,
    private val colors: CalendarSnapshotColors,
    private val style: CalendarSnapshotStyle,
    private val fontFamilyResolver: FontFamily.Resolver,
    private val formatter: SnapshotFormatter,
) {
    /** Fills [scope] with the snapshot: the container, then the month, week or agenda content. */
    fun draw(scope: DrawScope) {
        val measurer =
            TextMeasurer(fontFamilyResolver, Density(scope.density, scope.fontScale), scope.layoutDirection, 0)
        scope.drawSnapshot(measurer)
    }

    private fun DrawScope.drawSnapshot(measurer: TextMeasurer) {
        drawRoundRect(colors.container, cornerRadius = CornerRadius(style.cornerRadius.toPx()))
        val padding = style.contentPadding.toPx()
        val content = Rect(padding, padding, size.width - padding, size.height - padding)
        if (content.width <= 0f || content.height <= 0f) return
        when (val range = spec.range) {
            is CalendarSnapshotRange.Month -> {
                val grid = monthGrid(range.month, spec.firstDayOfWeek, spec.showOutsideDays)
                drawGrid(measurer, content, grid, formatter.monthTitle(range.month))
            }

            is CalendarSnapshotRange.Week -> {
                val grid = weekGrid(range.date, spec.firstDayOfWeek)
                drawGrid(measurer, content, grid, formatter.dateRange(grid.cells.first().date, grid.cells.last().date))
            }

            is CalendarSnapshotRange.Agenda -> drawAgenda(measurer, content, range)
        }
    }

    private fun DrawScope.drawGrid(
        measurer: TextMeasurer,
        content: Rect,
        grid: SnapshotGrid,
        title: String,
    ) {
        val request =
            GridTextRequest(
                titleTextPx = style.titleTextSize.toPx(),
                weekdayTextPx = style.weekdayTextSize.toPx(),
                dayTextPx = style.dayTextSize.toPx(),
                minTextScale = style.minTextScale,
                showTitle = spec.showTitle,
                showWeekdays = spec.showWeekdayLabels,
            )
        val plan = planGridText(content.width, content.height, grid.rowCount, request)
        var top = content.top
        if (plan.showTitle) {
            val layout = measureLabel(measurer, title, plan.titleTextPx, FontWeight.Medium, content.width)
            val box = Rect(content.left, top, content.right, top + plan.titleBand)
            drawLabel(layout, colors.title, box, centered = false)
            top += plan.titleBand
        }
        val cellWidth = content.width / DAYS_PER_WEEK
        val rtl = layoutDirection == LayoutDirection.Rtl
        if (plan.showWeekdays) {
            val row = GridGeometry(content.left, top, cellWidth, plan.weekdayBand, rtl)
            grid.weekdays(spec.firstDayOfWeek).forEachIndexed { column, dayOfWeek ->
                val text = formatter.weekdayLabel(dayOfWeek)
                val layout = measureLabel(measurer, text, plan.weekdayTextPx, FontWeight.Normal, cellWidth)
                drawLabel(layout, colors.weekday, row.cell(0, column), centered = true)
            }
            top += plan.weekdayBand
        }
        val geometry = GridGeometry(content.left, top, cellWidth, (content.bottom - top) / grid.rowCount, rtl)
        drawDays(measurer, plan, geometry, dayStates(grid, spec, colors))
    }

    private fun DrawScope.drawDays(
        measurer: TextMeasurer,
        plan: GridTextPlan,
        geometry: GridGeometry,
        states: List<SnapshotDayState>,
    ) {
        val radius = minOf(geometry.cellWidth, geometry.cellHeight) * PILL_RADIUS_FACTOR
        val texts =
            if (plan.showDayNumbers) {
                val key =
                    MonthTextKey(
                        dayStyle = TextStyle(fontSize = plan.dayTextPx.toSp()),
                        titleStyle = TextStyle(fontSize = plan.titleTextPx.toSp()),
                        density = Density(density, fontScale),
                        layoutDirection = layoutDirection,
                        fontFamilyResolver = fontFamilyResolver,
                    )
                MonthTextCache(measurer).texts(key)
            } else {
                null
            }
        val cells = states.map { geometry.cell(it.cell.row, it.cell.column) }
        states.forEachIndexed { index, state -> drawBand(state.selection, cells[index], radius) }
        states.forEachIndexed { index, state -> drawDay(state, cells[index], radius, texts) }
    }

    private fun DrawScope.drawBand(
        mark: SelectionMark,
        cell: Rect,
        radius: Float,
    ) {
        val band = colors.rangeBand
        when (mark) {
            SelectionMark.RangeStart -> drawSelectionPill(band, cell, radius, roundStart = true, roundEnd = false)
            SelectionMark.RangeMiddle -> drawSelectionPill(band, cell, radius, roundStart = false, roundEnd = false)
            SelectionMark.RangeEnd -> drawSelectionPill(band, cell, radius, roundStart = false, roundEnd = true)
            SelectionMark.None, SelectionMark.Single -> Unit
        }
    }

    private fun DrawScope.drawDay(
        state: SnapshotDayState,
        cell: Rect,
        radius: Float,
        texts: MonthTextCache.Texts?,
    ) {
        val selected = state.selection.isEndpoint
        if (selected) drawSelectionPill(colors.selection, cell, radius, roundStart = true, roundEnd = true)
        if (state.isToday && selected) {
            val ring = maxOf(1f, TodayRingWidth.toPx())
            drawCircle(colors.selectionContent, radius - ring / 2f, cell.center, style = Stroke(ring))
        } else if (state.isToday) {
            drawTodayMark(colors.today, radius, cell.center)
        }
        if (texts != null) {
            val color =
                when {
                    selected -> colors.selectionContent
                    state.isToday -> colors.todayContent
                    state.cell.isOutside -> colors.outsideDay
                    else -> state.tint ?: colors.day
                }
            val layout = texts.day(state.cell.date.day, isBold = selected || state.isToday)
            val topLeft = Offset(cell.center.x - layout.size.width / 2f, cell.center.y - layout.size.height / 2f)
            drawText(layout, color, topLeft)
        }
        if (state.dots > 0) {
            val diameter = maxOf(1f, minOf(cell.width, cell.height) * DOT_DIAMETER_FACTOR)
            val centerY = minOf(cell.height - diameter, cell.height / 2f + radius * DOT_OFFSET_FACTOR)
            val override = if (selected) colors.selectionContent else null
            val dots = List(state.dots) { colors.eventDot }
            inset(cell.left, cell.top, size.width - cell.right, size.height - cell.bottom) {
                drawEventDots(dots, centerY, diameter, diameter * DOT_SPACING_FACTOR, override)
            }
        }
    }

    private fun DrawScope.drawAgenda(
        measurer: TextMeasurer,
        content: Rect,
        range: CalendarSnapshotRange.Agenda,
    ) {
        val titlePx = style.titleTextSize.toPx()
        val headingPx = style.weekdayTextSize.toPx()
        val rowPx = style.dayTextSize.toPx()
        val titleBand = titlePx * LINE_HEIGHT_FACTOR
        var top = content.top
        if (spec.showTitle && titleBand <= content.height) {
            val end = range.start.plus(range.days - 1, DateTimeUnit.DAY)
            val title = formatter.dateRange(range.start, end)
            val layout = measureLabel(measurer, title, titlePx, FontWeight.Medium, content.width)
            drawLabel(layout, colors.title, Rect(content.left, top, content.right, top + titleBand), centered = false)
            top += titleBand
        }
        val headingHeight = headingPx * LINE_HEIGHT_FACTOR
        val rowHeight = rowPx * AGENDA_ROW_FACTOR
        val packed = packAgenda(spec.agendaEvents, range.start, range.days, range.maxRows)
        for (day in fitAgenda(packed, content.bottom - top, headingHeight, rowHeight)) {
            val text = formatter.dayHeading(day.date)
            val heading = measureLabel(measurer, text, headingPx, FontWeight.Medium, content.width)
            val headingColor = if (day.date == spec.today) colors.today else colors.weekday
            val box = Rect(content.left, top, content.right, top + headingHeight)
            drawLabel(heading, headingColor, box, centered = false)
            top += headingHeight
            for (row in day.rows) {
                drawAgendaRow(measurer, row, Rect(content.left, top, content.right, top + rowHeight), rowPx)
                top += rowHeight
            }
        }
    }

    private fun DrawScope.drawAgendaRow(
        measurer: TextMeasurer,
        row: AgendaRow,
        box: Rect,
        textPx: Float,
    ) {
        val rtl = layoutDirection == LayoutDirection.Rtl
        val barWidth = AgendaBarWidth.toPx()
        val gap = AgendaGap.toPx()
        val barHeight = box.height * AGENDA_BAR_HEIGHT_FACTOR
        val barColor = row.event.colorArgb?.let { Color(it) } ?: colors.eventDot
        val barLeft = if (rtl) box.right - barWidth else box.left
        drawRoundRect(
            color = barColor,
            topLeft = Offset(barLeft, box.center.y - barHeight / 2f),
            size = Size(barWidth, barHeight),
            cornerRadius = CornerRadius(barWidth / 2f),
        )
        var start = barWidth + gap
        val time = row.startTime?.let(formatter::time)
        if (time != null && box.width - start > 0f) {
            val layout = measureLabel(measurer, time, textPx, FontWeight.Normal, box.width - start)
            drawLabel(layout, colors.weekday, box.fromStart(start, rtl), centered = false)
            start += layout.size.width + gap
        }
        if (box.width - start > 0f) {
            val layout = measureLabel(measurer, row.event.title, textPx, FontWeight.Normal, box.width - start)
            drawLabel(layout, colors.day, box.fromStart(start, rtl), centered = false)
        }
    }

    /** One line of [text], ellipsised to [maxWidth]. */
    private fun DrawScope.measureLabel(
        measurer: TextMeasurer,
        text: String,
        textPx: Float,
        fontWeight: FontWeight,
        maxWidth: Float,
    ): TextLayoutResult =
        measurer.measure(
            text = text,
            style = TextStyle(fontSize = textPx.toSp(), fontWeight = fontWeight),
            overflow = TextOverflow.Ellipsis,
            softWrap = false,
            maxLines = 1,
            constraints = Constraints(maxWidth = maxWidth.toInt().coerceAtLeast(0)),
            layoutDirection = layoutDirection,
            density = Density(density, fontScale),
            fontFamilyResolver = fontFamilyResolver,
        )

    /** Draws [layout] centred vertically in [box]: centred horizontally, or at the start edge. */
    private fun DrawScope.drawLabel(
        layout: TextLayoutResult,
        color: Color,
        box: Rect,
        centered: Boolean,
    ) {
        val width = layout.size.width.toFloat()
        val x =
            when {
                centered -> box.center.x - width / 2f
                layoutDirection == LayoutDirection.Rtl -> box.right - width
                else -> box.left
            }
        drawText(layout, color, Offset(x, box.center.y - layout.size.height / 2f))
    }
}

/** [this] without its first [start] px from the start edge. */
private fun Rect.fromStart(
    start: Float,
    rtl: Boolean,
): Rect = if (rtl) Rect(left, top, right - start, bottom) else Rect(left + start, top, right, bottom)

/** Cell rectangles of a grid whose logical columns are mirrored in RTL. */
private class GridGeometry(
    val left: Float,
    val top: Float,
    val cellWidth: Float,
    val cellHeight: Float,
    val rtl: Boolean,
) {
    fun cell(
        row: Int,
        column: Int,
    ): Rect {
        val visual = if (rtl) DAYS_PER_WEEK - 1 - column else column
        val x = left + visual * cellWidth
        val y = top + row * cellHeight
        return Rect(x, y, x + cellWidth, y + cellHeight)
    }
}
