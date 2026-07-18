package me.kozakov.orrery.compose

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import kotlinx.datetime.toJavaLocalDate
import me.kozakov.orrery.core.CalendarPages
import me.kozakov.orrery.core.HeatmapSummary
import me.kozakov.orrery.core.daysOfWeek
import me.kozakov.orrery.core.heatmapSummary
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/** State holder for [HeatmapCalendar]. */
@Stable
public class HeatmapCalendarState internal constructor(
    startDate: LocalDate,
    endDate: LocalDate,
    firstVisibleDate: LocalDate,
    firstDayOfWeek: DayOfWeek,
) {
    public var startDate: LocalDate by mutableStateOf(startDate)
        private set
    public var endDate: LocalDate by mutableStateOf(endDate)
        private set
    public var firstDayOfWeek: DayOfWeek by mutableStateOf(firstDayOfWeek)
        private set

    internal val listState: LazyListState =
        LazyListState(firstVisibleItemIndex = indexOf(firstVisibleDate))

    public val weekCount: Int
        get() = CalendarPages.weekCount(startDate, endDate, firstDayOfWeek)

    /** Start-of-week date of the first visible column. Snapshot-observable. */
    public val firstVisibleWeekStart: LocalDate
        get() = weekStartAt(listState.firstVisibleItemIndex)

    internal fun weekStartAt(index: Int): LocalDate =
        CalendarPages.weekStart(startDate, firstDayOfWeek).plus(index * 7, DateTimeUnit.DAY)

    public suspend fun scrollToDate(date: LocalDate) {
        listState.scrollToItem(indexOf(date))
    }

    public suspend fun animateScrollToDate(date: LocalDate) {
        listState.animateScrollToItem(indexOf(date))
    }

    private fun indexOf(date: LocalDate): Int =
        CalendarPages.weekIndex(startDate, date, firstDayOfWeek).coerceIn(0, weekCount - 1)

    public companion object {
        public val Saver: Saver<HeatmapCalendarState, Any> =
            listSaver(
                save = {
                    listOf(
                        it.startDate.toString(),
                        it.endDate.toString(),
                        it.firstVisibleWeekStart.toString(),
                        it.firstDayOfWeek.ordinal,
                    )
                },
                restore = {
                    HeatmapCalendarState(
                        startDate = LocalDate.parse(it[0] as String),
                        endDate = LocalDate.parse(it[1] as String),
                        firstVisibleDate = LocalDate.parse(it[2] as String),
                        firstDayOfWeek = DayOfWeek.entries[it[3] as Int],
                    )
                },
            )
    }
}

/**
 * Remembers saveable [HeatmapCalendarState]. Defaults to the trailing 365
 * days ending today, scrolled to the range end (most recent data first).
 */
@Composable
public fun rememberHeatmapCalendarState(
    startDate: LocalDate = currentDate().minus(364, DateTimeUnit.DAY),
    endDate: LocalDate = currentDate(),
    firstVisibleDate: LocalDate = endDate,
    firstDayOfWeek: DayOfWeek = firstDayOfWeekFromLocale(),
): HeatmapCalendarState =
    rememberSaveable(saver = HeatmapCalendarState.Saver) {
        HeatmapCalendarState(startDate, endDate, firstVisibleDate, firstDayOfWeek)
    }

/**
 * GitHub-style contribution graph: one column per week, seven tile rows,
 * horizontally scrollable across the state's date range. [intensity]
 * returns a 0..1 heat value per day, or null for no data. Cells are
 * clickable only when [onDayClick] is set. Accessibility descriptions
 * bucket intensity into five levels regardless of [colorScale].
 */
@Composable
public fun HeatmapCalendar(
    intensity: (LocalDate) -> Float?,
    state: HeatmapCalendarState = rememberHeatmapCalendarState(),
    modifier: Modifier = Modifier,
    onDayClick: ((LocalDate) -> Unit)? = null,
    colorScale: (Float) -> Color = HeatmapDefaults.colorScale(),
    emptyCellColor: Color = HeatmapDefaults.emptyCellColor(),
    cellSize: Dp = 12.dp,
    cellSpacing: Dp = 2.dp,
    cellShape: Shape = RoundedCornerShape(2.dp),
    showMonthLabels: Boolean = true,
    showWeekdayLabels: Boolean = true,
    showLegend: Boolean = true,
    summary: (@Composable (HeatmapSummary) -> Unit)? = { HeatmapDefaults.Summary(it) },
    strings: HeatmapStrings = HeatmapDefaults.strings(),
) {
    val dateFormatter = remember { DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM) }
    val labelHeight = if (showMonthLabels) 16.dp else 0.dp
    Column(modifier) {
        Row {
            if (showWeekdayLabels) {
                WeekdayLabelColumn(
                    firstDayOfWeek = state.firstDayOfWeek,
                    cellSize = cellSize,
                    cellSpacing = cellSpacing,
                    topOffset = labelHeight,
                )
            }
            LazyRow(state = state.listState) {
                items(count = state.weekCount) { index ->
                    WeekColumn(
                        state = state,
                        index = index,
                        intensity = intensity,
                        onDayClick = onDayClick,
                        colorScale = colorScale,
                        emptyCellColor = emptyCellColor,
                        cellSize = cellSize,
                        cellSpacing = cellSpacing,
                        cellShape = cellShape,
                        showMonthLabels = showMonthLabels,
                        labelHeight = labelHeight,
                        dateFormatter = dateFormatter,
                        strings = strings,
                    )
                }
            }
        }
        if (showLegend || summary != null) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (summary != null) {
                    val computed =
                        heatmapSummary(state.startDate..state.endDate) { (intensity(it) ?: 0f) > 0f }
                    summary(computed)
                }
                Spacer(Modifier.weight(1f))
                if (showLegend) {
                    Legend(colorScale, emptyCellColor, cellSize, cellSpacing, cellShape, strings)
                }
            }
        }
    }
}

@Composable
private fun WeekColumn(
    state: HeatmapCalendarState,
    index: Int,
    intensity: (LocalDate) -> Float?,
    onDayClick: ((LocalDate) -> Unit)?,
    colorScale: (Float) -> Color,
    emptyCellColor: Color,
    cellSize: Dp,
    cellSpacing: Dp,
    cellShape: Shape,
    showMonthLabels: Boolean,
    labelHeight: Dp,
    dateFormatter: DateTimeFormatter,
    strings: HeatmapStrings,
) {
    val weekStart = state.weekStartAt(index)
    Column(
        modifier = Modifier.padding(end = cellSpacing),
        verticalArrangement = Arrangement.spacedBy(cellSpacing),
    ) {
        if (showMonthLabels) {
            Box(Modifier.height(labelHeight)) {
                monthLabelFor(state, index)?.let { label ->
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        repeat(7) { row ->
            val date = weekStart.plus(row, DateTimeUnit.DAY)
            HeatCell(
                date = date,
                inRange = date in state.startDate..state.endDate,
                intensity = intensity,
                onDayClick = onDayClick,
                colorScale = colorScale,
                emptyCellColor = emptyCellColor,
                cellSize = cellSize,
                cellShape = cellShape,
                dateFormatter = dateFormatter,
                strings = strings,
            )
        }
    }
}

/**
 * Short month name when this column contains the 1st of a month and the
 * previous month's label sits at least 3 columns back; null otherwise.
 */
private fun monthLabelFor(
    state: HeatmapCalendarState,
    index: Int,
): String? {
    val weekStart = state.weekStartAt(index)
    val firstOfMonth =
        generateSequence(weekStart) { it.plus(1, DateTimeUnit.DAY) }
            .take(7)
            .firstOrNull { it.day == 1 && it in state.startDate..state.endDate }
            ?: return null
    val previousFirst = firstOfMonth.minus(1, DateTimeUnit.MONTH).let { LocalDate(it.year, it.month, 1) }
    if (previousFirst >= state.startDate) {
        val previousColumn = CalendarPages.weekIndex(state.startDate, previousFirst, state.firstDayOfWeek)
        if (index - previousColumn < 3) return null
    }
    return firstOfMonth.month.displayName(short = true)
}

@Composable
private fun WeekdayLabelColumn(
    firstDayOfWeek: DayOfWeek,
    cellSize: Dp,
    cellSpacing: Dp,
    topOffset: Dp,
) {
    val days = daysOfWeek(firstDayOfWeek)
    Column(
        modifier = Modifier.padding(end = 4.dp),
        verticalArrangement = Arrangement.spacedBy(cellSpacing),
    ) {
        if (topOffset > 0.dp) {
            Spacer(Modifier.height(topOffset))
        }
        days.forEachIndexed { row, day ->
            Box(Modifier.height(cellSize), contentAlignment = Alignment.CenterStart) {
                if (row % 2 == 0) {
                    Text(
                        text = day.displayName(),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun HeatCell(
    date: LocalDate,
    inRange: Boolean,
    intensity: (LocalDate) -> Float?,
    onDayClick: ((LocalDate) -> Unit)?,
    colorScale: (Float) -> Color,
    emptyCellColor: Color,
    cellSize: Dp,
    cellShape: Shape,
    dateFormatter: DateTimeFormatter,
    strings: HeatmapStrings,
) {
    if (!inRange) {
        Spacer(Modifier.size(cellSize))
        return
    }
    val value = intensity(date)
    val description =
        if (value == null) {
            strings.dayDescriptionEmpty.format(date.toJavaLocalDate().format(dateFormatter))
        } else {
            strings.dayDescription.format(
                date.toJavaLocalDate().format(dateFormatter),
                heatLevel(value),
                HeatmapLevels,
            )
        }
    Box(
        Modifier
            .size(cellSize)
            .clip(cellShape)
            .background(if (value == null) emptyCellColor else colorScale(value))
            .then(
                if (onDayClick != null) Modifier.clickable { onDayClick(date) } else Modifier,
            ).semantics { contentDescription = description },
    )
}

@Composable
private fun Legend(
    colorScale: (Float) -> Color,
    emptyCellColor: Color,
    cellSize: Dp,
    cellSpacing: Dp,
    cellShape: Shape,
    strings: HeatmapStrings,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(cellSpacing),
    ) {
        Text(
            text = strings.legendLess,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        (listOf(emptyCellColor) + listOf(0f, 0.25f, 0.5f, 0.75f, 1f).map(colorScale)).forEach { color ->
            Box(Modifier.size(cellSize).clip(cellShape).background(color))
        }
        Text(
            text = strings.legendMore,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
