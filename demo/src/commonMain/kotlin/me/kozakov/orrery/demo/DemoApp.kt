package me.kozakov.orrery.demo

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.Month
import kotlinx.datetime.YearMonth
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import me.kozakov.orrery.compose.Agenda
import me.kozakov.orrery.compose.CalendarNavHeader
import me.kozakov.orrery.compose.DatePickerDefaults
import me.kozakov.orrery.compose.DayDecorators
import me.kozakov.orrery.compose.DaySchedule
import me.kozakov.orrery.compose.DefaultDay
import me.kozakov.orrery.compose.HorizontalCalendar
import me.kozakov.orrery.compose.OrreryDateRangePickerDialog
import me.kozakov.orrery.compose.WeekSchedule
import me.kozakov.orrery.compose.rememberAgendaState
import me.kozakov.orrery.compose.rememberCalendarSelectionState
import me.kozakov.orrery.compose.rememberCalendarState
import me.kozakov.orrery.compose.rememberOrreryDateRangePickerState
import me.kozakov.orrery.compose.rememberScheduleState
import me.kozakov.orrery.core.CalendarEvent
import me.kozakov.orrery.core.DayInfo
import me.kozakov.orrery.core.DayInfoPriority
import me.kozakov.orrery.core.DayInfoProvider
import me.kozakov.orrery.core.EventSpan
import me.kozakov.orrery.core.Selection
import me.kozakov.orrery.core.SelectionMode
import me.kozakov.orrery.core.eventsByDate

private val Today = LocalDate(2026, 10, 27)
private val Now = LocalTime(10, 24)

private const val WORK = 0xFF64B5F6L
private const val PERSONAL = 0xFFE57373L
private const val HEALTH = 0xFF81C784L
private const val FESTIVE = 0xFFFF8A65L
private const val MONEY = 0xFF26A69AL

private enum class Tab(
    val label: String,
    val color: Color,
) {
    Month("Month", Color(0xFF5C6BC0)),
    Agenda("Agenda", Color(0xFF64B5F6)),
    Schedule("Schedule", Color(0xFF81C784)),
    Pickers("Pickers", Color(0xFFFF8A65)),
}

private val demoEvents: Map<LocalDate, List<CalendarEvent<Nothing>>> = buildDemoEvents()

private fun buildDemoEvents(): Map<LocalDate, List<CalendarEvent<Nothing>>> {
    val range = Today.minus(60, DateTimeUnit.DAY)..Today.plus(60, DateTimeUnit.DAY)
    val events = mutableListOf<CalendarEvent<Nothing>>()
    for (offset in -60..60) {
        val date = Today.plus(offset, DateTimeUnit.DAY)
        fun timed(
            id: String,
            startMinutes: Int,
            durationMinutes: Int,
            title: String,
            subtitle: String?,
            color: Long,
        ) {
            val end = startMinutes + durationMinutes
            events +=
                CalendarEvent<Nothing>(
                    id = "$date-$id",
                    title = title,
                    span =
                        EventSpan.Timed(
                            start = LocalDateTime(date, LocalTime(startMinutes / 60, startMinutes % 60)),
                            end = LocalDateTime(date, LocalTime(end / 60, end % 60)),
                        ),
                    subtitle = subtitle,
                    colorArgb = color,
                )
        }
        val weekend = date.dayOfWeek == DayOfWeek.SATURDAY || date.dayOfWeek == DayOfWeek.SUNDAY
        if (!weekend) timed("standup", 9 * 60 + 30, 30, "Team standup", null, WORK)
        when (((offset % 4) + 4) % 4) {
            0 -> {
                timed("run", 7 * 60, 45, "Morning run", "Riverside park", HEALTH)
                timed("review", 9 * 60 + 45, 90, "Design review", "Room 302", WORK)
                timed("lunch", 12 * 60 + 30, 60, "Lunch with Sam", null, PERSONAL)
            }
            1 -> {
                timed("dentist", 14 * 60, 60, "Dentist", null, HEALTH)
                timed("book", 19 * 60 + 30, 90, "Book club", null, PERSONAL)
            }
            2 -> {
                events +=
                    CalendarEvent<Nothing>(
                        id = "$date-offsite",
                        title = "Sprint planning",
                        span = EventSpan.AllDay(startDate = date),
                        colorArgb = WORK,
                    )
                timed("oneonone", 11 * 60, 45, "1:1 with manager", null, WORK)
                timed("gym", 18 * 60, 60, "Gym", null, HEALTH)
            }
            else -> Unit
        }
    }
    return events.eventsByDate(range)
}

private val holidays =
    object : DayInfoProvider {
        override fun info(date: LocalDate): DayInfo? {
            val label =
                when {
                    date.month == Month.OCTOBER && date.day == 31 -> "Halloween"
                    date.month == Month.NOVEMBER && date.day == 8 -> "Diwali"
                    date.month == Month.NOVEMBER && date.day == 11 -> "Veterans"
                    date.month == Month.NOVEMBER && date.day == 26 -> "Thanksg."
                    date.month == Month.DECEMBER && date.day == 25 -> "Xmas"
                    else -> null
                } ?: return null
            return DayInfo(label = label, isHoliday = true, priority = DayInfoPriority.PUBLIC_HOLIDAY, colorArgb = FESTIVE)
        }
    }

private val paydays =
    object : DayInfoProvider {
        override fun info(date: LocalDate): DayInfo? =
            if (date.day == 1 || date.day == 15) {
                DayInfo(label = "Payday", priority = DayInfoPriority.SECONDARY, colorArgb = MONEY)
            } else {
                null
            }
    }

private val dayInfo = holidays + paydays

/** Auto-playing tour of Orrery used to record the README GIFs. */
@Composable
public fun DemoApp(platform: String) {
    MaterialTheme(colorScheme = lightColorScheme()) {
        var tab by remember { mutableStateOf(Tab.Month) }
        var weekMode by remember { mutableStateOf(false) }
        var showPicker by remember { mutableStateOf(false) }
        var picked by remember { mutableStateOf<String?>(null) }
        val calendarState = rememberCalendarState(firstVisibleMonth = YearMonth(2026, Month.OCTOBER))
        val selection = rememberCalendarSelectionState(mode = SelectionMode.Range())
        val agendaState =
            rememberAgendaState(
                startDate = Today.minus(60, DateTimeUnit.DAY),
                endDate = Today.plus(60, DateTimeUnit.DAY),
                initialVisibleDate = Today,
            )
        val scheduleState =
            rememberScheduleState(
                startDate = Today.minus(60, DateTimeUnit.DAY),
                endDate = Today.plus(60, DateTimeUnit.DAY),
                firstVisibleDate = Today,
                initialScrollTime = LocalTime(7, 0),
            )
        val pickerState = rememberOrreryDateRangePickerState(initialDisplayedMonth = YearMonth(2026, Month.OCTOBER))
        val presets = DatePickerDefaults.rangePresets(today = Today)

        LaunchedEffect(Unit) {
            while (true) {
                // Marker the recording script waits for, so each GIF is exactly one loop.
                println("ORRERY_DEMO_LOOP")
                tab = Tab.Month
                selection.clear()
                delay(1600)
                selection.set(Selection(rangeStart = Today.plus(1, DateTimeUnit.DAY), rangeEnd = Today.plus(4, DateTimeUnit.DAY)))
                delay(2000)
                calendarState.animateScrollToMonth(YearMonth(2026, Month.NOVEMBER))
                delay(2200)
                calendarState.animateScrollToMonth(YearMonth(2026, Month.OCTOBER))
                delay(1200)

                tab = Tab.Agenda
                delay(1800)
                agendaState.animateScrollToDate(Today.plus(5, DateTimeUnit.DAY))
                delay(1800)
                agendaState.animateScrollToDate(Today)
                delay(1200)

                weekMode = false
                tab = Tab.Schedule
                delay(2200)
                scheduleState.animateScrollToDate(Today.plus(1, DateTimeUnit.DAY))
                delay(1600)
                scheduleState.animateScrollToDate(Today)
                delay(1000)
                weekMode = true
                delay(2800)

                tab = Tab.Pickers
                picked = null
                pickerState.setSelection(null, null)
                delay(1200)
                showPicker = true
                delay(2000)
                val preset = presets.first()
                pickerState.setSelection(preset.range.start, preset.range.endInclusive)
                delay(2200)
                showPicker = false
                picked = "${preset.label}: ${preset.range.start} to ${preset.range.endInclusive}"
                delay(2200)
            }
        }

        Scaffold(
            topBar = {
                Surface(tonalElevation = 2.dp) {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal))
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("Orrery", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        Text(
                            "  on $platform",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            },
            bottomBar = {
                NavigationBar {
                    Tab.entries.forEach { entry ->
                        NavigationBarItem(
                            selected = tab == entry,
                            onClick = { tab = entry },
                            icon = { Box(Modifier.size(12.dp).background(entry.color, CircleShape)) },
                            label = { Text(entry.label) },
                        )
                    }
                }
            },
        ) { padding ->
            Box(Modifier.fillMaxSize().padding(padding)) {
                when (tab) {
                    Tab.Month -> {
                        val dots = DayDecorators.eventDotsForEvents(eventsByDate = { demoEvents[it].orEmpty() })
                        Column(Modifier.fillMaxSize()) {
                            CalendarNavHeader(state = calendarState)
                            HorizontalCalendar(
                                state = calendarState,
                                dragSelection = selection,
                                dayContent = { day ->
                                    DefaultDay(
                                        day = day,
                                        selectionState = selection,
                                        today = Today,
                                        info = dayInfo.info(day.date),
                                        decorators = listOf(dots),
                                    )
                                },
                            )
                            val range = selection.selection.range
                            Card(Modifier.fillMaxWidth().padding(16.dp)) {
                                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Text("Trip", style = MaterialTheme.typography.titleMedium)
                                    Text(
                                        if (range == null) "Drag to select a range" else "${range.start} to ${range.endInclusive}",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                    }

                    Tab.Agenda ->
                        Agenda(
                            events = { demoEvents[it].orEmpty() },
                            state = agendaState,
                            today = Today,
                            modifier = Modifier.fillMaxSize(),
                        )

                    Tab.Schedule ->
                        Column(Modifier.fillMaxSize()) {
                            Row(Modifier.fillMaxWidth()) {
                                TextButton(onClick = { weekMode = false }) {
                                    Text("Day", fontWeight = if (!weekMode) FontWeight.Bold else FontWeight.Normal)
                                }
                                TextButton(onClick = { weekMode = true }) {
                                    Text("Week", fontWeight = if (weekMode) FontWeight.Bold else FontWeight.Normal)
                                }
                            }
                            if (weekMode) {
                                WeekSchedule(
                                    events = { demoEvents[it].orEmpty() },
                                    state = scheduleState,
                                    today = Today,
                                    currentTime = Now,
                                    modifier = Modifier.fillMaxSize(),
                                )
                            } else {
                                DaySchedule(
                                    events = { demoEvents[it].orEmpty() },
                                    state = scheduleState,
                                    today = Today,
                                    currentTime = Now,
                                    modifier = Modifier.fillMaxSize(),
                                )
                            }
                        }

                    Tab.Pickers ->
                        Column(Modifier.fillMaxSize().padding(16.dp)) {
                            Card(Modifier.fillMaxWidth()) {
                                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Text("Date range", style = MaterialTheme.typography.titleMedium)
                                    Text(
                                        picked ?: "No range selected",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                    Button(onClick = { showPicker = true }) { Text("Pick range") }
                                }
                            }
                        }
                }
            }
        }
        if (showPicker) {
            OrreryDateRangePickerDialog(
                onDismissRequest = { showPicker = false },
                confirmButton = { TextButton(onClick = { showPicker = false }) { Text("Save") } },
                state = pickerState,
                today = Today,
                presets = presets,
            )
        }
    }
}
