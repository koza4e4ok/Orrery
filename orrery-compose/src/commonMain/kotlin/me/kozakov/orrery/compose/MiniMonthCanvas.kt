@file:OptIn(InternalOrreryApi::class)

package me.kozakov.orrery.compose

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.YearMonth
import me.kozakov.orrery.InternalOrreryApi
import me.kozakov.orrery.compose.internal.MonthGridModel
import me.kozakov.orrery.compose.internal.MonthTextCache
import me.kozakov.orrery.compose.internal.drawSelectionPill
import me.kozakov.orrery.compose.internal.drawTodayMark
import me.kozakov.orrery.core.DayInfo
import me.kozakov.orrery.core.Selection
import kotlin.math.roundToInt

private const val DAYS_PER_WEEK = 7

/** Default capacity of [MiniMonthGridModelCache]: four years of months. */
internal const val MINI_MONTH_MODEL_CACHE_CAPACITY: Int = 48

/**
 * Bounded LRU cache of [MonthGridModel]s keyed by (month, first day of
 * week). Not thread-safe: it is only touched on the UI thread during
 * composition, so it needs no locks on any platform.
 */
internal class MiniMonthGridModelCache(
    private val capacity: Int = MINI_MONTH_MODEL_CACHE_CAPACITY,
) {
    init {
        require(capacity > 0) { "capacity ($capacity) must be > 0" }
    }

    // Insertion order doubles as recency: a hit is re-inserted at the end.
    private val entries = LinkedHashMap<Long, MonthGridModel>()

    /** Models built so far (cache misses). */
    var misses: Int = 0
        private set

    val size: Int
        get() = entries.size

    operator fun get(
        yearMonth: YearMonth,
        firstDayOfWeek: DayOfWeek,
    ): MonthGridModel {
        val key = (yearMonth.year.toLong() * 12 + yearMonth.month.ordinal) * DAYS_PER_WEEK + firstDayOfWeek.ordinal
        val hit = entries.remove(key)
        if (hit != null) {
            entries[key] = hit
            return hit
        }
        misses++
        val model = MonthGridModel(yearMonth, firstDayOfWeek)
        entries[key] = model
        if (entries.size > capacity) entries.remove(entries.keys.first())
        return model
    }
}

/** The caches [YearCalendar] shares with all its [CalendarDefaults.MiniMonth]s. */
internal class MiniMonthCaches(
    measurer: TextMeasurer,
) {
    val models: MiniMonthGridModelCache = MiniMonthGridModelCache()
    val texts: MonthTextCache = MonthTextCache(measurer)
}

/** Provided by [YearCalendar]; a standalone mini-month remembers its own caches. */
internal val LocalMiniMonthCaches = staticCompositionLocalOf<MiniMonthCaches?> { null }

/** [LocalMiniMonthCaches] when provided, else caches remembered here. */
@Composable
internal fun rememberMiniMonthCaches(): MiniMonthCaches {
    val provided = LocalMiniMonthCaches.current
    val measurer = rememberTextMeasurer(cacheSize = 0)
    return remember(provided, measurer) { provided ?: MiniMonthCaches(measurer) }
}

/**
 * Per-month highlight state folded into bit masks (bit d-1 = day d) so an
 * unchanged month compares equal and skips work. [rangeStartDay] and
 * [rangeEndDay] are 0 when the range ends lie outside the month.
 */
@Immutable
internal data class MiniMonthDayState(
    val todayDay: Int,
    val selectedMask: Int,
    val inRangeMask: Int,
    val rangeStartDay: Int,
    val rangeEndDay: Int,
    val holidayMask: Int,
) {
    fun isSelected(day: Int): Boolean = selectedMask and (1 shl (day - 1)) != 0

    fun isInRange(day: Int): Boolean = inRangeMask and (1 shl (day - 1)) != 0

    fun isHoliday(day: Int): Boolean = holidayMask and (1 shl (day - 1)) != 0
}

/** Builds [MiniMonthDayState]; [dayInfo] is called once per date of the month and never for other dates. */
internal fun miniMonthDayState(
    model: MonthGridModel,
    today: LocalDate,
    selection: Selection?,
    dayInfo: ((LocalDate) -> DayInfo?)?,
): MiniMonthDayState {
    val yearMonth = model.yearMonth
    val range = selection?.range
    val firstEpochDay = yearMonth.firstDay.toEpochDays()
    var selected = 0
    var inRange = 0
    var holidays = 0
    for (day in 1..model.dayCount) {
        val date = LocalDate.fromEpochDays(firstEpochDay + day - 1)
        val bit = 1 shl (day - 1)
        val dateInRange = range != null && date in range
        if (dateInRange) inRange = inRange or bit
        if (dateInRange || selection?.marks(date) == true) selected = selected or bit
        if (dayInfo?.invoke(date)?.isHoliday == true) holidays = holidays or bit
    }
    return MiniMonthDayState(
        todayDay = today.dayIn(yearMonth),
        selectedMask = selected,
        inRangeMask = inRange,
        rangeStartDay = range?.start?.dayIn(yearMonth) ?: 0,
        rangeEndDay = range?.endInclusive?.dayIn(yearMonth) ?: 0,
        holidayMask = holidays,
    )
}

private fun Selection.marks(date: LocalDate): Boolean =
    date == single || date in multi || date == rangeStart || date == rangeEnd

/** Day of month when this date lies in [yearMonth], else 0. */
private fun LocalDate.dayIn(yearMonth: YearMonth): Int =
    if (year == yearMonth.year && month == yearMonth.month) day else 0

/** Theme colours of a mini-month's day cells. */
@Immutable
internal class MiniMonthColors(
    val today: Color,
    val onToday: Color,
    val holiday: Color,
    val pill: Color,
    val onPill: Color,
    val day: Color,
)

private val MiniDayMaxRadius = 8.dp

/**
 * Draws a whole mini-month (title, selection pills, today circle, day
 * numbers) in one pass. Columns mirror in RTL exactly as the former
 * per-day Row layout did.
 */
internal class MiniMonthPainter(
    private val model: MonthGridModel,
    private val state: MiniMonthDayState,
    private val texts: MonthTextCache.Texts,
    private val title: TextLayoutResult,
    private val titleColor: Color,
    private val colors: MiniMonthColors,
) {
    val contentHeight: Int
        get() = title.size.height + model.rowCount * texts.rowHeight

    val minWidth: Int
        get() = maxOf(title.size.width, texts.minGridWidth)

    fun draw(scope: DrawScope): Unit =
        with(scope) {
            val rtl = layoutDirection == LayoutDirection.Rtl
            drawText(title, color = titleColor, topLeft = Offset(if (rtl) size.width - title.size.width else 0f, 0f))
            val cellWidth = size.width / DAYS_PER_WEEK
            val rowHeight = texts.rowHeight.toFloat()
            val radius = minOf(cellWidth, rowHeight, MiniDayMaxRadius.toPx() * 2) / 2
            for (day in 1..model.dayCount) {
                val column = model.column(day)
                val visualColumn = if (rtl) DAYS_PER_WEEK - 1 - column else column
                // Whole-pixel cell edges, so adjacent pill segments meet without a seam.
                val left = (visualColumn * cellWidth).roundToInt().toFloat()
                val right = ((visualColumn + 1) * cellWidth).roundToInt().toFloat()
                val top = title.size.height + model.row(day) * rowHeight
                drawDay(day, column, Rect(left, top, right, top + rowHeight), radius)
            }
        }

    private fun DrawScope.drawDay(
        day: Int,
        column: Int,
        cell: Rect,
        radius: Float,
    ) {
        val isToday = day == state.todayDay
        val selected = state.isSelected(day)
        if (selected) {
            val inRange = state.isInRange(day)
            val roundStart = !inRange || day == state.rangeStartDay || column == 0 || day == 1
            val roundEnd =
                !inRange || day == state.rangeEndDay || column == DAYS_PER_WEEK - 1 || day == model.dayCount
            drawSelectionPill(colors.pill, cell, radius, roundStart, roundEnd)
        }
        if (isToday) drawTodayMark(colors.today, radius, cell.center)
        val color =
            when {
                isToday -> colors.onToday
                state.isHoliday(day) -> colors.holiday
                selected -> colors.onPill
                else -> colors.day
            }
        val layout = texts.day(day, isBold = isToday)
        drawText(layout, color = color, topLeft = Offset(cell.left + (cell.width - layout.size.width) / 2f, cell.top))
    }
}
