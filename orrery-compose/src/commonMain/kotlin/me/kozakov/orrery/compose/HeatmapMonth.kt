package me.kozakov.orrery.compose

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.YearMonth
import me.kozakov.orrery.core.DayPosition
import me.kozakov.orrery.core.OutDateStyle
import me.kozakov.orrery.core.daysOfWeek
import me.kozakov.orrery.core.monthGrid

/**
 * Static month grid of heat tiles over [monthGrid]. Cells divide the
 * available width equally; leading/trailing out-dates render blank.
 * Accessibility descriptions bucket intensity into
 * [HeatmapDefaults.Levels] levels ([HeatmapDefaults.levelOf]) regardless
 * of [colorScale].
 *
 * @param showLegend draws the same "less … more" legend row as
 * [HeatmapCalendar] under the grid, using [strings]' `legendLess` /
 * `legendMore`, [colorScale], [emptyCellColor] and [cellShape]. Off by
 * default.
 */
@Composable
public fun HeatmapMonth(
    yearMonth: YearMonth,
    intensity: (LocalDate) -> Float?,
    modifier: Modifier = Modifier,
    onDayClick: ((LocalDate) -> Unit)? = null,
    colorScale: (Float) -> Color = HeatmapDefaults.colorScale(),
    emptyCellColor: Color = HeatmapDefaults.emptyCellColor(),
    cellShape: Shape = RoundedCornerShape(4.dp),
    showDayNumbers: Boolean = false,
    showWeekdayHeader: Boolean = true,
    firstDayOfWeek: DayOfWeek = PlatformDateFormat.firstDayOfWeek(),
    strings: HeatmapStrings = HeatmapDefaults.strings(),
    showLegend: Boolean = false,
) {
    val month =
        remember(yearMonth, firstDayOfWeek) {
            monthGrid(yearMonth, firstDayOfWeek, OutDateStyle.EndOfRow)
        }
    val dateFormatter: (LocalDate) -> String =
        remember { { date -> PlatformDateFormat.formatDate(date, DateFormattingStyle.Medium) } }
    Column(modifier) {
        if (showWeekdayHeader) {
            CalendarDefaults.WeekHeader(daysOfWeek = daysOfWeek(firstDayOfWeek))
        }
        month.weeks.forEach { week ->
            Row {
                week.days.forEach { day ->
                    Box(
                        Modifier
                            .weight(1f)
                            .aspectRatio(1f)
                            .padding(1.dp),
                    ) {
                        if (day.position == DayPosition.MonthDate) {
                            MonthHeatCell(
                                date = day.date,
                                intensity = intensity,
                                onDayClick = onDayClick,
                                colorScale = colorScale,
                                emptyCellColor = emptyCellColor,
                                cellShape = cellShape,
                                showDayNumber = showDayNumbers,
                                dateFormatter = dateFormatter,
                                strings = strings,
                            )
                        }
                    }
                }
            }
        }
        if (showLegend) {
            Row(Modifier.fillMaxWidth().padding(top = 8.dp)) {
                Spacer(Modifier.weight(1f))
                HeatmapLegend(
                    colorScale = colorScale,
                    emptyCellColor = emptyCellColor,
                    cellSize = LegendCellSize,
                    cellSpacing = LegendCellSpacing,
                    cellShape = cellShape,
                    strings = strings,
                )
            }
        }
    }
}

@Deprecated("Kept for binary compatibility; use the overload with showLegend.", level = DeprecationLevel.HIDDEN)
@Composable
public fun HeatmapMonth(
    yearMonth: YearMonth,
    intensity: (LocalDate) -> Float?,
    modifier: Modifier = Modifier,
    onDayClick: ((LocalDate) -> Unit)? = null,
    colorScale: (Float) -> Color = HeatmapDefaults.colorScale(),
    emptyCellColor: Color = HeatmapDefaults.emptyCellColor(),
    cellShape: Shape = RoundedCornerShape(4.dp),
    showDayNumbers: Boolean = false,
    showWeekdayHeader: Boolean = true,
    firstDayOfWeek: DayOfWeek = PlatformDateFormat.firstDayOfWeek(),
    strings: HeatmapStrings = HeatmapDefaults.strings(),
) {
    HeatmapMonth(
        yearMonth = yearMonth,
        intensity = intensity,
        modifier = modifier,
        onDayClick = onDayClick,
        colorScale = colorScale,
        emptyCellColor = emptyCellColor,
        cellShape = cellShape,
        showDayNumbers = showDayNumbers,
        showWeekdayHeader = showWeekdayHeader,
        firstDayOfWeek = firstDayOfWeek,
        strings = strings,
        showLegend = false,
    )
}

// Same swatch size and spacing as HeatmapCalendar's default legend.
private val LegendCellSize = 12.dp
private val LegendCellSpacing = 2.dp

@Composable
private fun MonthHeatCell(
    date: LocalDate,
    intensity: (LocalDate) -> Float?,
    onDayClick: ((LocalDate) -> Unit)?,
    colorScale: (Float) -> Color,
    emptyCellColor: Color,
    cellShape: Shape,
    showDayNumber: Boolean,
    dateFormatter: (LocalDate) -> String,
    strings: HeatmapStrings,
) {
    val value = intensity(date)
    val background = if (value == null) emptyCellColor else colorScale(value)
    val description =
        if (value == null) {
            strings.dayDescriptionEmpty.formatTemplate(dateFormatter(date))
        } else {
            strings.dayDescription.formatTemplate(
                dateFormatter(date),
                HeatmapDefaults.levelOf(value),
                HeatmapDefaults.Levels,
            )
        }
    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .clip(cellShape)
                .background(background)
                .then(
                    if (onDayClick != null) Modifier.clickable(role = Role.Button) { onDayClick(date) } else Modifier,
                ).semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        if (showDayNumber) {
            Text(
                text = date.day.toString(),
                style = MaterialTheme.typography.labelSmall,
                color = if (background.luminance() > 0.5f) Color.Black else Color.White,
            )
        }
    }
}
