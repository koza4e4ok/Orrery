package dev.koza4e4ok.orrery.compose

import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.koza4e4ok.orrery.core.DayPosition
import dev.koza4e4ok.orrery.core.OutDateStyle
import dev.koza4e4ok.orrery.core.monthGrid
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.YearMonth
import kotlinx.datetime.toJavaLocalDate

/** State holder for [YearCalendar]. */
@Stable
public class YearCalendarState internal constructor(
    public val startYear: Int,
    public val endYear: Int,
    firstVisibleYear: Int,
) {
    internal val listState: LazyListState =
        LazyListState(
            firstVisibleItemIndex = indexOf(firstVisibleYear),
        )

    public val yearCount: Int
        get() = endYear - startYear + 1

    public val firstVisibleYear: Int
        get() = startYear + listState.firstVisibleItemIndex

    public suspend fun scrollToYear(year: Int) {
        listState.scrollToItem(indexOf(year))
    }

    public suspend fun animateScrollToYear(year: Int) {
        listState.animateScrollToItem(indexOf(year))
    }

    private fun indexOf(year: Int): Int = (year - startYear).coerceIn(0, endYear - startYear)

    public companion object {
        public val Saver: Saver<YearCalendarState, Any> =
            listSaver(
                save = { listOf(it.startYear, it.endYear, it.firstVisibleYear) },
                restore = { YearCalendarState(it[0], it[1], it[2]) },
            )
    }
}

@Composable
public fun rememberYearCalendarState(
    startYear: Int = 1971,
    endYear: Int = 2055,
    firstVisibleYear: Int = currentYearMonth().year,
): YearCalendarState =
    rememberSaveable(saver = YearCalendarState.Saver) {
        YearCalendarState(startYear, endYear, firstVisibleYear)
    }

/** Year overview: one year per page, months in a [columns]-wide grid. */
@Composable
public fun YearCalendar(
    state: YearCalendarState = rememberYearCalendarState(),
    modifier: Modifier = Modifier,
    userScrollEnabled: Boolean = true,
    columns: Int = 3,
    onMonthClick: (YearMonth) -> Unit,
    monthContent: @Composable BoxScope.(YearMonth) -> Unit = { CalendarDefaults.MiniMonth(it) },
) {
    LazyRow(
        state = state.listState,
        modifier = modifier,
        flingBehavior = rememberSnapFlingBehavior(state.listState),
        userScrollEnabled = userScrollEnabled,
    ) {
        items(count = state.yearCount, key = { it }) { index ->
            val year = state.startYear + index
            Column(Modifier.fillParentMaxWidth()) {
                for (rowMonths in (1..12).chunked(columns)) {
                    Row(Modifier.fillMaxWidth()) {
                        rowMonths.forEach { m ->
                            val ym = YearMonth(year, m)
                            Box(
                                Modifier
                                    .weight(1f)
                                    .clickable { onMonthClick(ym) },
                            ) { monthContent(ym) }
                        }
                    }
                }
            }
        }
    }
}

/** Default mini-month cell for [YearCalendar]. */
@Composable
public fun CalendarDefaults.MiniMonth(
    yearMonth: YearMonth,
    modifier: Modifier = Modifier,
    firstDayOfWeek: DayOfWeek = DayOfWeek.MONDAY,
    today: LocalDate = currentDate(),
) {
    val isCurrentMonth = yearMonth.year == today.year && yearMonth.month == today.month
    Column(modifier.padding(8.dp)) {
        Text(
            text = yearMonth.firstDay.toJavaMonthName(),
            style = MaterialTheme.typography.titleSmall,
            color =
                if (isCurrentMonth) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
        )
        monthGrid(yearMonth, firstDayOfWeek, OutDateStyle.None).weeks.forEach { week ->
            Row(Modifier.fillMaxWidth()) {
                week.days.forEach { day ->
                    Text(
                        text = if (day.position == DayPosition.MonthDate) day.date.day.toString() else "",
                        modifier = Modifier.weight(1f),
                        fontSize = 9.sp,
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

private fun LocalDate.toJavaMonthName(): String =
    toJavaLocalDate()
        .month
        .getDisplayName(java.time.format.TextStyle.SHORT_STANDALONE, java.util.Locale.getDefault())
