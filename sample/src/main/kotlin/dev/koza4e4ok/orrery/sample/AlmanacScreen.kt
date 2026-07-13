package dev.koza4e4ok.orrery.sample

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.koza4e4ok.orrery.compose.CalendarNavHeader
import dev.koza4e4ok.orrery.compose.DefaultDay
import dev.koza4e4ok.orrery.compose.HorizontalCalendar
import dev.koza4e4ok.orrery.compose.currentDate
import dev.koza4e4ok.orrery.compose.rememberCalendarSelectionState
import dev.koza4e4ok.orrery.compose.rememberCalendarState
import dev.koza4e4ok.orrery.core.Selection
import dev.koza4e4ok.orrery.core.SelectionMode
import dev.koza4e4ok.orrery.lunar.ChineseVariant
import dev.koza4e4ok.orrery.lunar.LunarDayInfoProvider
import dev.koza4e4ok.orrery.lunar.displayName
import dev.koza4e4ok.orrery.lunar.solarTermsFor
import dev.koza4e4ok.orrery.lunar.toLunarDate
import dev.koza4e4ok.orrery.lunar.trunkBranchYear
import kotlinx.datetime.LocalDate
import kotlinx.datetime.toJavaLocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/** Chinese almanac: lunar-labeled month with a detail card for the selected day. */
@Composable
internal fun AlmanacScreen() {
    val today = remember { currentDate() }
    val provider = remember { LunarDayInfoProvider() }
    val calendarState = rememberCalendarState()
    val selection =
        rememberCalendarSelectionState(
            mode = SelectionMode.Single(),
            initialSelection = Selection(single = today),
        )
    val selectedDate = selection.selection.single ?: today

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        CalendarNavHeader(state = calendarState)
        HorizontalCalendar(
            state = calendarState,
            dayContent = { day ->
                DefaultDay(
                    day = day,
                    selectionState = selection,
                    today = today,
                    info = provider.info(day.date),
                )
            },
        )
        AlmanacDetailCard(selectedDate, provider)
    }
}

@Composable
private fun AlmanacDetailCard(
    date: LocalDate,
    provider: LunarDayInfoProvider,
) {
    val lunar = remember(date) { date.toLunarDate() }
    val term = remember(date) { solarTermsFor(date.year)[date]?.displayName(ChineseVariant.SIMPLIFIED) }
    val info = remember(date) { provider.info(date) }
    Card(Modifier.fillMaxWidth().padding(16.dp)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                date.toJavaLocalDate().format(DateTimeFormatter.ofLocalizedDate(FormatStyle.FULL)),
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                "Lunar ${lunar.month}/${lunar.day}${if (lunar.isLeapMonth) " (leap month)" else ""} · ${trunkBranchYear(
                    lunar.year,
                )}",
                style = MaterialTheme.typography.bodyMedium,
            )
            if (term != null) {
                Text("Solar term: $term", style = MaterialTheme.typography.bodyMedium)
            }
            val label = info?.label
            if (label != null && label != term) {
                Text(
                    if (info.isHoliday) "Festival: $label" else "Lunar day: $label",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }
}
