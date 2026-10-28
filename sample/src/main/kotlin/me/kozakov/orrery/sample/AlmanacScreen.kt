package me.kozakov.orrery.sample

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import kotlinx.datetime.LocalDate
import kotlinx.datetime.toJavaLocalDate
import me.kozakov.orrery.calendars.hijri.HijriDate
import me.kozakov.orrery.calendars.hijri.HijriDayInfoProvider
import me.kozakov.orrery.calendars.hijri.HijriStrings
import me.kozakov.orrery.calendars.persian.PersianDate
import me.kozakov.orrery.calendars.persian.PersianDayInfoProvider
import me.kozakov.orrery.calendars.persian.PersianStrings
import me.kozakov.orrery.compose.CalendarNavHeader
import me.kozakov.orrery.compose.DefaultDay
import me.kozakov.orrery.compose.HorizontalCalendar
import me.kozakov.orrery.compose.currentDate
import me.kozakov.orrery.compose.rememberCalendarSelectionState
import me.kozakov.orrery.compose.rememberCalendarState
import me.kozakov.orrery.core.DayInfoProvider
import me.kozakov.orrery.core.Selection
import me.kozakov.orrery.core.SelectionMode
import me.kozakov.orrery.core.payloadAs
import me.kozakov.orrery.holidays.PublicHolidayDayInfoProvider
import me.kozakov.orrery.holidays.PublicHolidays
import me.kozakov.orrery.lunar.ChineseVariant
import me.kozakov.orrery.lunar.LunarDayInfoProvider
import me.kozakov.orrery.lunar.LunarDayMetadata
import me.kozakov.orrery.lunar.displayName
import me.kozakov.orrery.lunar.trunkBranchYear
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

private enum class AlmanacSystem(
    val label: String,
) {
    Lunar("Lunar"),
    Hijri("Hijri"),
    Persian("Persian"),
}

private enum class HolidayCountry(
    val label: String,
    val code: String?,
) {
    None("None", null),
    US("US", "US"),
    GB("GB", "GB"),
    DE("DE", "DE"),
    UA("UA", "UA"),
}

/** Almanac: secondary calendar labels (Lunar, Hijri, Persian) composed with country public holidays. */
@Composable
internal fun AlmanacScreen() {
    val today = remember { currentDate() }
    var system by rememberSaveable { mutableStateOf(AlmanacSystem.Lunar) }
    var country by rememberSaveable { mutableStateOf(HolidayCountry.None) }

    val lunarProvider = remember { LunarDayInfoProvider() }
    val hijriProvider = remember { HijriDayInfoProvider() }
    val persianProvider = remember { PersianDayInfoProvider() }

    val calendarProvider =
        when (system) {
            AlmanacSystem.Lunar -> lunarProvider
            AlmanacSystem.Hijri -> hijriProvider
            AlmanacSystem.Persian -> persianProvider
        }
    val holidayCalendar = remember(country) { country.code?.let(PublicHolidays::forCountry) }
    val provider: DayInfoProvider =
        remember(calendarProvider, holidayCalendar) {
            if (holidayCalendar != null) {
                PublicHolidayDayInfoProvider(holidayCalendar) + calendarProvider
            } else {
                calendarProvider
            }
        }

    val calendarState = rememberCalendarState()
    val selection =
        rememberCalendarSelectionState(
            mode = SelectionMode.Single(),
            initialSelection = Selection(single = today),
        )
    val selectedDate = selection.selection.single ?: today

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        AlmanacControls(
            system = system,
            onSystemChange = { system = it },
            country = country,
            onCountryChange = { country = it },
        )
        CalendarNavHeader(state = calendarState)
        HorizontalCalendar(
            state = calendarState,
            modifier = Modifier.testTag("almanac_calendar"),
            dayContent = { day ->
                DefaultDay(
                    day = day,
                    selectionState = selection,
                    today = today,
                    info = provider.info(day.date),
                )
            },
        )
        AlmanacDetailCard(
            date = selectedDate,
            system = system,
            calendarProvider = calendarProvider,
            country = country,
        )
    }
}

@Composable
private fun AlmanacControls(
    system: AlmanacSystem,
    onSystemChange: (AlmanacSystem) -> Unit,
    country: HolidayCountry,
    onCountryChange: (HolidayCountry) -> Unit,
) {
    Column(
        Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row(
            Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            AlmanacSystem.entries.forEach { entry ->
                FilterChip(
                    selected = system == entry,
                    onClick = { onSystemChange(entry) },
                    label = { Text(entry.label) },
                )
            }
        }
        Row(
            Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            HolidayCountry.entries.forEach { entry ->
                FilterChip(
                    selected = country == entry,
                    onClick = { onCountryChange(entry) },
                    label = { Text(if (entry == HolidayCountry.None) "No holidays" else "${entry.label} holidays") },
                )
            }
        }
    }
}

@Composable
private fun AlmanacDetailCard(
    date: LocalDate,
    system: AlmanacSystem,
    calendarProvider: DayInfoProvider,
    country: HolidayCountry,
) {
    val calInfo = remember(date, calendarProvider) { calendarProvider.info(date) }
    val holidays =
        remember(date, country) {
            country.code?.let { PublicHolidays.forCountry(it)?.holidaysOn(date) }.orEmpty()
        }
    Card(Modifier.fillMaxWidth().padding(16.dp)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                date.toJavaLocalDate().format(DateTimeFormatter.ofLocalizedDate(FormatStyle.FULL)),
                style = MaterialTheme.typography.titleMedium,
            )
            when (system) {
                AlmanacSystem.Lunar -> {
                    val metadata = calInfo?.payloadAs<LunarDayMetadata>()
                    val lunar = metadata?.lunarDate
                    val term = metadata?.solarTerm?.displayName(ChineseVariant.SIMPLIFIED)
                    if (lunar != null) {
                        val leapSuffix = if (lunar.isLeapMonth) " (leap month)" else ""
                        Text(
                            "Lunar ${lunar.month}/${lunar.day}$leapSuffix · ${trunkBranchYear(lunar.year)}",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                    if (term != null) {
                        Text("Solar term: $term", style = MaterialTheme.typography.bodyMedium)
                    }
                    val label = calInfo?.label
                    if (label != null && label != term) {
                        Text(
                            if (calInfo.isHoliday) "Festival: $label" else "Lunar day: $label",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
                AlmanacSystem.Hijri -> {
                    val hijri = calInfo?.payloadAs<HijriDate>()
                    if (hijri != null) {
                        val monthName = HijriStrings.English.monthNames[hijri.month - 1]
                        Text(
                            "Hijri: ${hijri.day} $monthName ${hijri.year} AH (${hijri.variant.name})",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
                AlmanacSystem.Persian -> {
                    val persian = calInfo?.payloadAs<PersianDate>()
                    if (persian != null) {
                        val monthName = PersianStrings.English.monthNames[persian.month - 1]
                        Text(
                            "Persian: ${persian.day} $monthName ${persian.year} AP",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
            }
            holidays.forEach { holiday ->
                val sub = if (holiday.isSubstitute) " (substitute)" else ""
                Text(
                    "Holiday (${country.label}): ${holiday.name}$sub",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}
