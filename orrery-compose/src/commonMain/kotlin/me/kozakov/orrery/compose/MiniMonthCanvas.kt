package me.kozakov.orrery.compose

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.YearMonth
import kotlinx.datetime.isoDayNumber
import me.kozakov.orrery.core.DayInfo
import me.kozakov.orrery.core.Selection

private const val DAYS_PER_WEEK = 7

/** Default capacity of [MiniMonthGridModelCache]: four years of months. */
internal const val MINI_MONTH_MODEL_CACHE_CAPACITY: Int = 48

/** "0".."31", shared by every mini-month so drawing allocates no day strings. */
internal val MiniMonthDayLabels: Array<String> = Array(32) { it.toString() }

/**
 * Immutable grid geometry of one [CalendarDefaults.MiniMonth]; depends only on
 * [yearMonth] and [firstDayOfWeek] and is shared through [MiniMonthGridModelCache].
 */
@Immutable
internal class MiniMonthGridModel(
    val yearMonth: YearMonth,
    val firstDayOfWeek: DayOfWeek,
) {
    /** Empty cells before day 1 in the first row (0..6). */
    val leadingBlanks: Int =
        (yearMonth.firstDay.dayOfWeek.isoDayNumber - firstDayOfWeek.isoDayNumber).mod(DAYS_PER_WEEK)

    /** Days in the month (28..31). */
    val dayCount: Int = yearMonth.numberOfDays

    /** Week rows of the grid without out-dates (4..6). */
    val rowCount: Int = (leadingBlanks + dayCount + DAYS_PER_WEEK - 1) / DAYS_PER_WEEK

    /** Logical (start-to-end) column of [day]. */
    fun column(day: Int): Int = (leadingBlanks + day - 1) % DAYS_PER_WEEK

    fun row(day: Int): Int = (leadingBlanks + day - 1) / DAYS_PER_WEEK
}

/**
 * Bounded LRU cache of [MiniMonthGridModel]s keyed by (month, first day of
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
    private val entries = LinkedHashMap<Long, MiniMonthGridModel>()

    /** Models built so far (cache misses). */
    var misses: Int = 0
        private set

    val size: Int
        get() = entries.size

    operator fun get(
        yearMonth: YearMonth,
        firstDayOfWeek: DayOfWeek,
    ): MiniMonthGridModel {
        val key = (yearMonth.year.toLong() * 12 + yearMonth.month.ordinal) * DAYS_PER_WEEK + firstDayOfWeek.ordinal
        val hit = entries.remove(key)
        if (hit != null) {
            entries[key] = hit
            return hit
        }
        misses++
        val model = MiniMonthGridModel(yearMonth, firstDayOfWeek)
        entries[key] = model
        if (entries.size > capacity) entries.remove(entries.keys.first())
        return model
    }
}

/** Everything a measured mini-month text layout depends on. */
internal data class MiniMonthTextKey(
    val dayStyle: TextStyle,
    val titleStyle: TextStyle,
    val density: Density,
    val layoutDirection: LayoutDirection,
    val fontFamilyResolver: FontFamily.Resolver,
)

/**
 * Shared text layouts for mini-months: one regular and one bold layout per
 * day number and one per month title, measured once (without colour, which
 * is applied when drawing) and reused by every month of every year.
 */
internal class MiniMonthTextCache(
    private val measurer: TextMeasurer,
) {
    /** Text layouts measured so far; stays bounded however many years scroll by. */
    var measureCount: Int = 0
        private set

    private var latest: Texts? = null

    /** The layouts for [key]; a different style or environment starts a fresh set. */
    fun texts(key: MiniMonthTextKey): Texts = latest?.takeIf { it.key == key } ?: Texts(key).also { latest = it }

    inner class Texts(
        val key: MiniMonthTextKey,
    ) {
        private val regular = arrayOfNulls<TextLayoutResult>(MiniMonthDayLabels.size)
        private val bold = arrayOfNulls<TextLayoutResult>(MiniMonthDayLabels.size)
        private val titles = HashMap<String, TextLayoutResult>()
        private val boldStyle = key.dayStyle.merge(TextStyle(fontWeight = FontWeight.Bold))

        fun day(
            day: Int,
            isBold: Boolean,
        ): TextLayoutResult {
            val layouts = if (isBold) bold else regular
            return layouts[day]
                ?: measure(MiniMonthDayLabels[day], if (isBold) boldStyle else key.dayStyle).also { layouts[day] = it }
        }

        fun title(text: String): TextLayoutResult = titles.getOrPut(text) { measure(text, key.titleStyle) }

        /** Height of one week row: the measured day-number line (font-scale aware). */
        val rowHeight: Int
            get() = maxOf(day(1, isBold = false).size.height, day(1, isBold = true).size.height)

        /** Width that fits seven day numbers side by side. */
        val minGridWidth: Int
            get() = DAYS_PER_WEEK * maxOf(day(31, isBold = false).size.width, day(31, isBold = true).size.width)

        private fun measure(
            text: String,
            style: TextStyle,
        ): TextLayoutResult {
            measureCount++
            return measurer.measure(
                text = text,
                style = style,
                softWrap = false,
                maxLines = 1,
                layoutDirection = key.layoutDirection,
                density = key.density,
                fontFamilyResolver = key.fontFamilyResolver,
            )
        }
    }
}

/** The caches [YearCalendar] shares with all its [CalendarDefaults.MiniMonth]s. */
internal class MiniMonthCaches(
    measurer: TextMeasurer,
) {
    val models: MiniMonthGridModelCache = MiniMonthGridModelCache()
    val texts: MiniMonthTextCache = MiniMonthTextCache(measurer)
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
    model: MiniMonthGridModel,
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
    private val model: MiniMonthGridModel,
    private val state: MiniMonthDayState,
    private val texts: MiniMonthTextCache.Texts,
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
                val left = (if (rtl) DAYS_PER_WEEK - 1 - column else column) * cellWidth
                val top = title.size.height + model.row(day) * rowHeight
                drawDay(day, column, Rect(left, top, left + cellWidth, top + rowHeight), radius)
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
            drawPill(cell, radius, roundStart, roundEnd)
        }
        if (isToday) drawCircle(colors.today, radius, cell.center)
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

    /** A selection pill segment; rounded ends inset to the today circle so a lone date reads as a circle. */
    private fun DrawScope.drawPill(
        cell: Rect,
        radius: Float,
        roundStart: Boolean,
        roundEnd: Boolean,
    ) {
        val rtl = layoutDirection == LayoutDirection.Rtl
        val roundLeft = if (rtl) roundEnd else roundStart
        val roundRight = if (rtl) roundStart else roundEnd
        val centerX = cell.center.x
        val top = cell.center.y - radius
        val left = if (roundLeft) centerX - radius else cell.left
        val right = if (roundRight) centerX + radius else cell.right
        drawRoundRect(colors.pill, Offset(left, top), Size(right - left, radius * 2), CornerRadius(radius))
        if (!roundLeft) drawRect(colors.pill, Offset(cell.left, top), Size(centerX - cell.left, radius * 2))
        if (!roundRight) drawRect(colors.pill, Offset(centerX, top), Size(cell.right - centerX, radius * 2))
    }
}
