package me.kozakov.orrery.compose

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import kotlinx.datetime.Month
import kotlinx.datetime.YearMonth

/**
 * Two-level month/year jump picker: a year grid drilling into a 3x4
 * month grid; months outside [range] are disabled. Selecting a month
 * fires [onSelect] — the host decides what to do (typically scroll a
 * calendar and dismiss). Host it in a dialog, dropdown, or swap it with
 * the calendar in-place.
 */
@Composable
public fun MonthYearPicker(
    current: YearMonth,
    range: ClosedRange<YearMonth>,
    onSelect: (YearMonth) -> Unit,
    modifier: Modifier = Modifier,
) {
    var pickedYear by rememberSaveable { mutableStateOf<Int?>(null) }
    AnimatedContent(
        targetState = pickedYear,
        modifier = modifier,
        transitionSpec = { (fadeIn() + scaleIn(initialScale = 0.92f)).togetherWith(fadeOut()) },
        label = "monthYearPicker",
    ) { year ->
        if (year == null) {
            YearGrid(currentYear = current.year, range = range, onYearClick = { pickedYear = it })
        } else {
            MonthGrid(
                year = year,
                current = current,
                range = range,
                onBack = { pickedYear = null },
                onMonthClick = { month -> onSelect(YearMonth(year, month)) },
            )
        }
    }
}

@Composable
private fun YearGrid(
    currentYear: Int,
    range: ClosedRange<YearMonth>,
    onYearClick: (Int) -> Unit,
) {
    val years = (range.start.year..range.endInclusive.year).toList()
    val initialIndex =
        (currentYear - range.start.year).coerceIn(0, years.lastIndex).let { it - it % 3 }
    LazyVerticalGrid(
        columns = GridCells.Fixed(3),
        state = rememberLazyGridState(initialFirstVisibleItemIndex = initialIndex),
    ) {
        items(years.size) { index ->
            val year = years[index]
            TextButton(onClick = { onYearClick(year) }) {
                Text(
                    year.toString(),
                    color = if (year == currentYear) MaterialTheme.colorScheme.primary else Color.Unspecified,
                    fontWeight = if (year == currentYear) FontWeight.Bold else null,
                )
            }
        }
    }
}

@Composable
private fun MonthGrid(
    year: Int,
    current: YearMonth,
    range: ClosedRange<YearMonth>,
    onBack: () -> Unit,
    onMonthClick: (Month) -> Unit,
) {
    Column {
        TextButton(onClick = onBack) {
            Text(year.toString(), style = MaterialTheme.typography.titleMedium)
        }
        LazyVerticalGrid(columns = GridCells.Fixed(3), modifier = Modifier.fillMaxWidth()) {
            items(12) { index ->
                val month = Month(index + 1)
                val yearMonth = YearMonth(year, month)
                TextButton(
                    onClick = { onMonthClick(month) },
                    enabled = yearMonth in range,
                ) {
                    Text(
                        month.displayName(),
                        fontWeight = if (yearMonth == current) FontWeight.Bold else null,
                    )
                }
            }
        }
    }
}
