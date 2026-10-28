package me.kozakov.orrery.sample

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import kotlinx.coroutines.launch
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.YearMonth
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import kotlinx.datetime.toJavaLocalDate
import me.kozakov.orrery.calendars.hijri.HijriDate
import me.kozakov.orrery.calendars.hijri.HijriDayInfoProvider
import me.kozakov.orrery.calendars.hijri.HijriStrings
import me.kozakov.orrery.calendars.persian.PersianDate
import me.kozakov.orrery.calendars.persian.PersianDayInfoProvider
import me.kozakov.orrery.calendars.persian.PersianStrings
import me.kozakov.orrery.compose.CalendarDefaults
import me.kozakov.orrery.compose.CalendarNavHeader
import me.kozakov.orrery.compose.CalendarSelectionState
import me.kozakov.orrery.compose.CalendarState
import me.kozakov.orrery.compose.CollapsibleCalendarScaffold
import me.kozakov.orrery.compose.CollapsibleCalendarState
import me.kozakov.orrery.compose.DayDecorator
import me.kozakov.orrery.compose.DayDecorators
import me.kozakov.orrery.compose.DefaultDay
import me.kozakov.orrery.compose.MonthYearPicker
import me.kozakov.orrery.compose.TodayIndicator
import me.kozakov.orrery.compose.WeekCalendarState
import me.kozakov.orrery.compose.YearCalendar
import me.kozakov.orrery.compose.YearCalendarState
import me.kozakov.orrery.compose.currentDate
import me.kozakov.orrery.compose.rememberCalendarSelectionState
import me.kozakov.orrery.compose.rememberCalendarState
import me.kozakov.orrery.compose.rememberCollapsibleCalendarState
import me.kozakov.orrery.compose.rememberWeekCalendarState
import me.kozakov.orrery.compose.rememberYearCalendarState
import me.kozakov.orrery.core.CalendarDay
import me.kozakov.orrery.core.CalendarEvent
import me.kozakov.orrery.core.CalendarPages
import me.kozakov.orrery.core.DayInfoProvider
import me.kozakov.orrery.core.DayPosition
import me.kozakov.orrery.core.EventSpan
import me.kozakov.orrery.core.OutDateStyle
import me.kozakov.orrery.core.Selection
import me.kozakov.orrery.core.SelectionMode
import me.kozakov.orrery.core.monthGrid
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
import java.util.Locale

internal enum class AlmanacSystem(
    val label: String,
) {
    Lunar("Lunar"),
    Hijri("Hijri"),
    Persian("Persian"),
}

internal enum class HolidayCountry(
    val label: String,
    val code: String?,
) {
    None("None", null),
    US("US", "US"),
    GB("GB", "GB"),
    DE("DE", "DE"),
    UA("UA", "UA"),
}

internal enum class ShowcaseThemePreset(
    val label: String,
) {
    M3Default("M3 Default"),
    MeizuBadges("Meizu Badges"),
    ColorfulGlow("Colorful Glow"),
    SolarProgress("Solar Progress"),
    StackedAndSpanning("Stacked & Spanning"),
    RoundedAndCard("Rounded & Card"),
}

internal enum class StudioSelectionChoice(
    val label: String,
) {
    Single("Single"),
    Multiple("Multiple"),
    Range("Range"),
    Week("Week"),
    Month("Month"),
}

internal enum class FirstDayOption(
    val label: String,
    val dayOfWeek: DayOfWeek,
) {
    Monday("Mon", DayOfWeek.MONDAY),
    Sunday("Sun", DayOfWeek.SUNDAY),
    Saturday("Sat", DayOfWeek.SATURDAY),
}

internal enum class OutDatesOption(
    val label: String,
    val outDateStyle: OutDateStyle,
    val showOutDates: Boolean,
) {
    EndOfRow("All (End of Row)", OutDateStyle.EndOfRow, true),
    OnlyCurrent("Only Current", OutDateStyle.EndOfRow, false),
    FixedSixWeeks("Fixed 6 Weeks", OutDateStyle.EndOfGrid, true),
}

private val EmeraldHeaderBg = Color(0xFF009688)
private val EmeraldAccent = Color(0xFF00796B)
private val ColorfulPalette =
    listOf(
        Color(0xFF26A69A),
        Color(0xFF42A5F5),
        Color(0xFFAB47BC),
        Color(0xFFFF7043),
        Color(0xFFEC407A),
    )

/**
 * Universal Calendar Showcase & Style Studio inspired by huanghaibin-dev/CalendarView:
 * combines SampleHeroHeader, YearCalendar overview jump, CollapsibleCalendarScaffold,
 * live-switchable visual theme presets, and a dynamic controls bottom sheet.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AlmanacScreen() {
    val today = remember { currentDate() }
    var system by rememberSaveable { mutableStateOf(AlmanacSystem.Lunar) }
    var country by rememberSaveable { mutableStateOf(HolidayCountry.US) }
    var themePreset by rememberSaveable { mutableStateOf(ShowcaseThemePreset.M3Default) }
    var selectionChoice by rememberSaveable { mutableStateOf(StudioSelectionChoice.Single) }
    var firstDayOption by rememberSaveable { mutableStateOf(FirstDayOption.Monday) }
    var outDatesOption by rememberSaveable { mutableStateOf(OutDatesOption.EndOfRow) }
    var showWeekNumbers by rememberSaveable { mutableStateOf(false) }
    var showYearOverview by rememberSaveable { mutableStateOf(false) }
    var showControlsSheet by rememberSaveable { mutableStateOf(false) }
    var showMonthYearJump by rememberSaveable { mutableStateOf(false) }
    var cardGridSubMode by rememberSaveable { mutableStateOf(false) }

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

    val calendarState = rememberCalendarState(firstDayOfWeek = firstDayOption.dayOfWeek)
    val weekState = rememberWeekCalendarState(firstDayOfWeek = firstDayOption.dayOfWeek)
    val yearState = rememberYearCalendarState(firstVisibleYear = today.year)
    val collapsible = rememberCollapsibleCalendarState(initialExpanded = true)

    LaunchedEffect(firstDayOption) {
        calendarState.firstDayOfWeek = firstDayOption.dayOfWeek
        weekState.firstDayOfWeek = firstDayOption.dayOfWeek
    }
    LaunchedEffect(outDatesOption) {
        calendarState.outDateStyle = outDatesOption.outDateStyle
    }

    val resolvedMode =
        remember(selectionChoice, firstDayOption) {
            when (selectionChoice) {
                StudioSelectionChoice.Single -> SelectionMode.Single()
                StudioSelectionChoice.Multiple -> SelectionMode.Multiple()
                StudioSelectionChoice.Range -> SelectionMode.Range()
                StudioSelectionChoice.Week -> SelectionMode.Week(firstDayOfWeek = firstDayOption.dayOfWeek)
                StudioSelectionChoice.Month -> SelectionMode.Month()
            }
        }
    val selection =
        rememberCalendarSelectionState(
            mode = resolvedMode,
            initialSelection =
                when (selectionChoice) {
                    StudioSelectionChoice.Single -> Selection(single = today)
                    StudioSelectionChoice.Multiple -> Selection(multi = setOf(today, today.plus(2, DateTimeUnit.DAY)))
                    StudioSelectionChoice.Range ->
                        Selection(
                            rangeStart = today,
                            rangeEnd = today.plus(4, DateTimeUnit.DAY),
                        )
                    StudioSelectionChoice.Week, StudioSelectionChoice.Month -> Selection(rangeStart = today)
                },
        )

    val activeDate =
        selection.selection.single
            ?: selection.selection.rangeStart
            ?: selection.selection.multi.firstOrNull()
            ?: today

    val eventRangeStart = remember(today) { today.minus(180, DateTimeUnit.DAY) }
    val eventRangeEnd = remember(today) { today.plus(180, DateTimeUnit.DAY) }
    val events = remember(eventRangeStart, eventRangeEnd) { SampleData.eventsFor(eventRangeStart..eventRangeEnd) }

    val heroSubtitle =
        remember(activeDate, provider, system, country) {
            val info = provider.info(activeDate)
            val baseLabel = info?.label ?: system.label
            val countrySuffix = if (country != HolidayCountry.None) " · ${country.label}" else ""
            "$baseLabel$countrySuffix"
        }

    val scope = rememberCoroutineScope()
    val isEmeraldHero = themePreset == ShowcaseThemePreset.SolarProgress

    Column(Modifier.fillMaxSize()) {
        SampleHeroHeader(
            date = activeDate,
            today = today,
            subtitle = heroSubtitle,
            yearOverviewActive = showYearOverview,
            onTodayClick = {
                showYearOverview = false
                scope.launch {
                    calendarState.animateScrollToToday()
                    weekState.animateScrollToDate(today)
                    yearState.animateScrollToYear(today.year)
                }
                selection.click(today)
            },
            onToggleYearOverview = {
                val next = !showYearOverview
                showYearOverview = next
                if (next) {
                    scope.launch { yearState.scrollToYear(calendarState.firstVisibleMonth.year) }
                }
            },
            onOpenControls = { showControlsSheet = true },
            onTitleClick = { showMonthYearJump = true },
            containerColor = if (isEmeraldHero) EmeraldHeaderBg else MaterialTheme.colorScheme.surface,
            contentColor = if (isEmeraldHero) Color.White else MaterialTheme.colorScheme.onSurface,
            accentColor = if (isEmeraldHero) Color(0xFFB2DFDB) else MaterialTheme.colorScheme.primary,
        )

        ThemePresetStrip(
            selected = themePreset,
            cardGridSubMode = cardGridSubMode,
            onSelect = { preset ->
                if (themePreset == ShowcaseThemePreset.RoundedAndCard &&
                    preset == ShowcaseThemePreset.RoundedAndCard
                ) {
                    cardGridSubMode = !cardGridSubMode
                } else {
                    themePreset = preset
                }
            },
        )

        if (showYearOverview) {
            YearOverviewSection(
                yearState = yearState,
                today = today,
                firstDayOfWeek = firstDayOption.dayOfWeek,
                provider = provider,
                onMonthClick = { ym ->
                    showYearOverview = false
                    scope.launch {
                        calendarState.scrollToMonth(ym)
                        weekState.scrollToDate(ym.firstDay)
                    }
                },
            )
        } else {
            CalendarNavHeader(
                state = calendarState,
                onTitleClick = { showMonthYearJump = true },
            )
            val decorators =
                rememberShowcaseDecorators(
                    themePreset = themePreset,
                    events = events,
                    selection = selection,
                    today = today,
                )
            CollapsibleCalendarScaffold(
                calendarState = calendarState,
                weekState = weekState,
                state = collapsible,
                modifier = Modifier.weight(1f).testTag("almanac_calendar"),
                weekHeader = { days ->
                    CalendarDefaults.WeekHeader(days, leadingSpacer = showWeekNumbers)
                },
                dayContent = { day ->
                    ShowcaseDayCell(
                        day = day,
                        today = today,
                        selection = selection,
                        provider = provider,
                        themePreset = themePreset,
                        cardGridSubMode = cardGridSubMode,
                        showOutDates = outDatesOption.showOutDates,
                        decorators = decorators,
                    )
                },
            ) {
                ShowcaseDetailList(
                    activeDate = activeDate,
                    selection = selection,
                    system = system,
                    calendarProvider = calendarProvider,
                    country = country,
                    eventsOnDate = events[activeDate].orEmpty(),
                    themePreset = themePreset,
                    collapsible = collapsible,
                    onOpenControls = { showControlsSheet = true },
                )
            }
        }
    }

    if (showControlsSheet) {
        ModalBottomSheet(
            onDismissRequest = { showControlsSheet = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            modifier = Modifier.testTag("almanac_controls_sheet"),
        ) {
            DynamicControlsSheetContent(
                themePreset = themePreset,
                onThemePresetChange = { themePreset = it },
                cardGridSubMode = cardGridSubMode,
                onCardGridSubModeChange = { cardGridSubMode = it },
                collapsible = collapsible,
                calendarState = calendarState,
                weekState = weekState,
                today = today,
                firstDayOption = firstDayOption,
                onFirstDayChange = { firstDayOption = it },
                outDatesOption = outDatesOption,
                onOutDatesChange = { outDatesOption = it },
                showWeekNumbers = showWeekNumbers,
                onShowWeekNumbersChange = { showWeekNumbers = it },
                selectionChoice = selectionChoice,
                onSelectionChoiceChange = { selectionChoice = it },
                system = system,
                onSystemChange = { system = it },
                country = country,
                onCountryChange = { country = it },
            )
        }
    }

    if (showMonthYearJump) {
        Dialog(onDismissRequest = { showMonthYearJump = false }) {
            Surface(
                shape = RoundedCornerShape(24.dp),
                tonalElevation = 6.dp,
                modifier = Modifier.fillMaxWidth().padding(16.dp),
            ) {
                val baseMonth = YearMonth(today.year, today.month)
                Column(Modifier.padding(16.dp)) {
                    Text(
                        "Jump to Month & Year",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(bottom = 8.dp),
                    )
                    MonthYearPicker(
                        current = calendarState.firstVisibleMonth,
                        range = baseMonth.minus(50, DateTimeUnit.YEAR)..baseMonth.plus(50, DateTimeUnit.YEAR),
                        onSelect = { ym ->
                            showMonthYearJump = false
                            showYearOverview = false
                            scope.launch {
                                calendarState.animateScrollToMonth(ym)
                                weekState.scrollToDate(ym.firstDay)
                            }
                        },
                        modifier = Modifier.height(300.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun ThemePresetStrip(
    selected: ShowcaseThemePreset,
    cardGridSubMode: Boolean,
    onSelect: (ShowcaseThemePreset) -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        ShowcaseThemePreset.entries.forEach { preset ->
            val label =
                if (preset == ShowcaseThemePreset.RoundedAndCard) {
                    if (cardGridSubMode) "Card Grid" else "Rounded Square"
                } else {
                    preset.label
                }
            FilterChip(
                selected = selected == preset,
                onClick = { onSelect(preset) },
                label = { Text(label) },
                modifier = Modifier.testTag("theme_preset_${preset.name.lowercase()}"),
            )
        }
    }
}

@Composable
private fun rememberShowcaseDecorators(
    themePreset: ShowcaseThemePreset,
    events: Map<LocalDate, List<CalendarEvent<Nothing>>>,
    selection: CalendarSelectionState,
    today: LocalDate,
): List<DayDecorator> {
    val dotPalette =
        remember {
            listOf(Color(0xFF1E88E5), Color(0xFFE53935), Color(0xFF43A047))
        }
    val previewTarget =
        selection.selection.rangeStart?.let { start ->
            if (selection.selection.rangeEnd == null) start.plus(4, DateTimeUnit.DAY) else null
        }
    val rangePreviewDec =
        DayDecorators.rangePreview(
            selectionState = selection,
            targetDate = previewTarget,
        )
    return when (themePreset) {
        ShowcaseThemePreset.M3Default ->
            listOf(
                DayDecorators.eventDotsForEvents(eventsByDate = { events[it].orEmpty() }),
                rangePreviewDec,
            )

        ShowcaseThemePreset.MeizuBadges ->
            listOf(
                DayDecorators.eventDots(maxDots = 1, dotSize = 4.5.dp) { date ->
                    if (events[date].isNullOrEmpty()) emptyList() else listOf(Color(0xFF26A69A))
                },
                meizuTagDecorator { date ->
                    when {
                        date.day % 9 == 0 -> "H" to Color(0xFFE53935)
                        date.day % 11 == 0 -> "W" to Color(0xFFFB8C00)
                        date.day % 7 == 0 -> "N" to Color(0xFF1E88E5)
                        else -> null
                    }
                },
                rangePreviewDec,
            )

        ShowcaseThemePreset.ColorfulGlow ->
            listOf(
                DayDecorators.eventDots(selectedColor = Color.White, maxDots = 2) { date ->
                    if (date.day % 4 == 0) listOf(Color.White) else emptyList()
                },
                rangePreviewDec,
            )

        ShowcaseThemePreset.SolarProgress ->
            listOf(
                DayDecorators.progressRing(
                    strokeWidth = 2.5.dp,
                    color = EmeraldHeaderBg,
                    trackColor = EmeraldHeaderBg.copy(alpha = 0.16f),
                ) { date ->
                    if (date <= today || date.day % 3 == 0) {
                        ((date.day * 19) % 100) / 100f
                    } else {
                        null
                    }
                },
                rangePreviewDec,
            )

        ShowcaseThemePreset.StackedAndSpanning ->
            listOf(
                DayDecorators.stackedBars(
                    maxBars = 3,
                    barWidth = 14.dp,
                    barHeight = 2.5.dp,
                    spacing = 1.5.dp,
                ) { date ->
                    val count = (date.day % 4)
                    dotPalette.take(count)
                },
                DayDecorators.spanningEventBars(
                    eventsByDate = { date ->
                        events[date].orEmpty().filter { it.span is EventSpan.AllDay }
                    },
                    maxBars = 2,
                    barHeight = 3.5.dp,
                ),
                rangePreviewDec,
            )

        ShowcaseThemePreset.RoundedAndCard ->
            listOf(
                DayDecorators.eventDotsForEvents(eventsByDate = { events[it].orEmpty() }),
                DayDecorators.badge(maxCount = 9) { date ->
                    val count = events[date].orEmpty().size
                    if (count >= 2) count else 0
                },
                rangePreviewDec,
            )
    }
}

@Composable
private fun ShowcaseDayCell(
    day: CalendarDay,
    today: LocalDate,
    selection: CalendarSelectionState,
    provider: DayInfoProvider,
    themePreset: ShowcaseThemePreset,
    cardGridSubMode: Boolean,
    showOutDates: Boolean,
    decorators: List<DayDecorator>,
) {
    val info = provider.info(day.date)
    when (themePreset) {
        ShowcaseThemePreset.M3Default -> {
            DefaultDay(
                day = day,
                selectionState = selection,
                today = today,
                info = info,
                showOutDates = showOutDates,
                decorators = decorators,
            )
        }

        ShowcaseThemePreset.MeizuBadges -> {
            val colors =
                CalendarDefaults.dayColors(
                    selectedContainerColor = Color(0xFF1E88E5),
                    selectedContentColor = Color.White,
                    todayIndicatorColor = Color(0xFF1E88E5),
                    todayContentColor = Color(0xFF1E88E5),
                    holidayLabelColor = Color(0xFFE53935),
                )
            DefaultDay(
                day = day,
                selectionState = selection,
                today = today,
                colors = colors,
                info = info,
                showOutDates = showOutDates,
                selectedElevation = 4.dp,
                decorators = decorators,
            )
        }

        ShowcaseThemePreset.ColorfulGlow -> {
            val isColoredDay = day.position == DayPosition.MonthDate && (day.date.day % 3 == 0)
            val fill = ColorfulPalette[day.date.day % ColorfulPalette.size]
            val colors =
                if (isColoredDay) {
                    CalendarDefaults.dayColors(
                        contentColor = Color.White,
                        labelColor = Color.White.copy(alpha = 0.9f),
                        holidayLabelColor = Color.White,
                        selectedContainerColor = Color(0xFF1A237E),
                        selectedContentColor = Color.White,
                    )
                } else {
                    CalendarDefaults.dayColors()
                }
            val glowDecorator =
                remember(isColoredDay, fill) {
                    DayDecorator {
                        if (isColoredDay) {
                            drawCircle(color = fill, radius = size.minDimension / 2f - 2.dp.toPx())
                        }
                    }
                }
            DefaultDay(
                day = day,
                selectionState = selection,
                today = today,
                colors = colors,
                info = info,
                showOutDates = showOutDates,
                decorators = listOf(glowDecorator) + decorators,
            )
        }

        ShowcaseThemePreset.SolarProgress -> {
            val colors =
                CalendarDefaults.dayColors(
                    selectedContainerColor = EmeraldHeaderBg,
                    selectedContentColor = Color.White,
                    todayIndicatorColor = EmeraldAccent,
                    todayContentColor = EmeraldAccent,
                    inRangeContainerColor = EmeraldHeaderBg.copy(alpha = 0.22f),
                    holidayLabelColor = EmeraldAccent,
                )
            DefaultDay(
                day = day,
                selectionState = selection,
                today = today,
                colors = colors,
                shapes = CalendarDefaults.dayShapes(todayIndicator = TodayIndicator.Underline),
                info = info,
                showOutDates = showOutDates,
                decorators = decorators,
            )
        }

        ShowcaseThemePreset.StackedAndSpanning -> {
            DefaultDay(
                day = day,
                selectionState = selection,
                today = today,
                info = info,
                showOutDates = showOutDates,
                decorators = decorators,
            )
        }

        ShowcaseThemePreset.RoundedAndCard -> {
            val corner = RoundedCornerShape(10.dp)
            val shapes =
                CalendarDefaults.dayShapes(
                    dayShape = corner,
                    selectedShape = corner,
                    inRangeShape = if (cardGridSubMode) RoundedCornerShape(8.dp) else RectangleShape,
                    todayIndicator = TodayIndicator.Ring(width = 1.5.dp),
                )
            val cellModifier =
                if (cardGridSubMode) {
                    Modifier
                        .padding(1.5.dp)
                        .border(
                            width = 1.dp,
                            color = MaterialTheme.colorScheme.outlineVariant,
                            shape = corner,
                        )
                } else {
                    Modifier
                }
            DefaultDay(
                day = day,
                modifier = cellModifier,
                selectionState = selection,
                today = today,
                shapes = shapes,
                info = info,
                showOutDates = showOutDates,
                selectedElevation = if (cardGridSubMode) 2.dp else 0.dp,
                decorators = decorators,
            )
        }
    }
}

@Composable
private fun YearOverviewSection(
    yearState: YearCalendarState,
    today: LocalDate,
    firstDayOfWeek: DayOfWeek,
    provider: DayInfoProvider,
    onMonthClick: (YearMonth) -> Unit,
) {
    val scope = rememberCoroutineScope()
    Column(Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            TextButton(onClick = { scope.launch { yearState.animateScrollToYear(yearState.firstVisibleYear - 1) } }) {
                Text("← ${yearState.firstVisibleYear - 1}")
            }
            Text(
                text = yearState.firstVisibleYear.toString(),
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
            )
            TextButton(onClick = { scope.launch { yearState.animateScrollToYear(yearState.firstVisibleYear + 1) } }) {
                Text("${yearState.firstVisibleYear + 1} →")
            }
        }
        YearCalendar(
            state = yearState,
            modifier = Modifier.fillMaxSize().padding(horizontal = 8.dp).testTag("almanac_year_calendar"),
            onMonthClick = onMonthClick,
            monthContent = { ym ->
                RichMiniMonth(
                    yearMonth = ym,
                    today = today,
                    firstDayOfWeek = firstDayOfWeek,
                    provider = provider,
                )
            },
        )
    }
}

@Composable
private fun RichMiniMonth(
    yearMonth: YearMonth,
    today: LocalDate,
    firstDayOfWeek: DayOfWeek,
    provider: DayInfoProvider,
) {
    val isCurrentMonth = yearMonth.year == today.year && yearMonth.month == today.month
    Column(Modifier.padding(6.dp)) {
        Text(
            text =
                yearMonth.month.name
                    .take(3)
                    .lowercase()
                    .replaceFirstChar { it.titlecase(Locale.ENGLISH) },
            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
            color = if (isCurrentMonth) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
        )
        Spacer(Modifier.height(2.dp))
        monthGrid(yearMonth, firstDayOfWeek, OutDateStyle.None).weeks.forEach { week ->
            Row(Modifier.fillMaxWidth()) {
                week.days.forEach { day ->
                    val isMonthDate = day.position == DayPosition.MonthDate
                    val isToday = isMonthDate && day.date == today
                    val isHoliday = isMonthDate && provider.info(day.date)?.isHoliday == true
                    Box(
                        modifier =
                            Modifier
                                .weight(1f)
                                .then(
                                    if (isToday) {
                                        Modifier.clip(CircleShape).background(MaterialTheme.colorScheme.primary)
                                    } else {
                                        Modifier
                                    },
                                ),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = if (isMonthDate) day.date.day.toString() else "",
                            fontSize = 9.sp,
                            textAlign = TextAlign.Center,
                            fontWeight = if (isToday || isHoliday) FontWeight.Bold else FontWeight.Normal,
                            color =
                                when {
                                    isToday -> MaterialTheme.colorScheme.onPrimary
                                    isHoliday -> MaterialTheme.colorScheme.primary
                                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                                },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ShowcaseDetailList(
    activeDate: LocalDate,
    selection: CalendarSelectionState,
    system: AlmanacSystem,
    calendarProvider: DayInfoProvider,
    country: HolidayCountry,
    eventsOnDate: List<CalendarEvent<Nothing>>,
    themePreset: ShowcaseThemePreset,
    collapsible: CollapsibleCalendarState,
    onOpenControls: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val calInfo = remember(activeDate, calendarProvider) { calendarProvider.info(activeDate) }
    val holidays =
        remember(activeDate, country) {
            country.code?.let { PublicHolidays.forCountry(it)?.holidaysOn(activeDate) }.orEmpty()
        }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                AssistChip(
                    onClick = {
                        scope.launch {
                            if (collapsible.isExpanded) collapsible.collapse() else collapsible.expand()
                        }
                    },
                    label = { Text(if (collapsible.isExpanded) "Collapse to Week" else "Expand to Month") },
                    modifier = Modifier.testTag("almanac_expand_collapse_chip"),
                )
                AssistChip(
                    onClick = onOpenControls,
                    label = { Text("Theme: ${themePreset.label} · Customize") },
                )
            }
        }

        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = activeDate.toJavaLocalDate().format(DateTimeFormatter.ofLocalizedDate(FormatStyle.FULL)),
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    )
                    val range = selection.selection.range
                    if (range != null && range.start != range.endInclusive) {
                        Text(
                            text = "Selected range: ${range.start} → ${range.endInclusive}",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    } else if (selection.selection.multi.size > 1) {
                        Text(
                            text = "${selection.selection.multi.size} dates selected",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
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
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            }
        }

        if (eventsOnDate.isNotEmpty()) {
            item {
                Text(
                    text = "Events on this day (${eventsOnDate.size})",
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                )
            }
            items(eventsOnDate, key = { it.id }) { event ->
                val accent = event.colorArgb?.let(::Color) ?: MaterialTheme.colorScheme.primary
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    border = BorderStroke(1.dp, accent.copy(alpha = 0.35f)),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Box(
                            Modifier
                                .size(width = 4.dp, height = 36.dp)
                                .clip(RoundedCornerShape(2.dp))
                                .background(accent),
                        )
                        Column(Modifier.weight(1f)) {
                            Text(
                                text = event.title,
                                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
                            )
                            val spanText =
                                when (val span = event.span) {
                                    is EventSpan.AllDay ->
                                        if (span.startDate == span.endDate) {
                                            "All day"
                                        } else {
                                            "${span.startDate} → ${span.endDate}"
                                        }
                                    is EventSpan.Timed -> "${span.start.time} – ${span.end.time}"
                                }
                            val sub = event.subtitle?.let { "$spanText · $it" } ?: spanText
                            Text(
                                text = sub,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DynamicControlsSheetContent(
    themePreset: ShowcaseThemePreset,
    onThemePresetChange: (ShowcaseThemePreset) -> Unit,
    cardGridSubMode: Boolean,
    onCardGridSubModeChange: (Boolean) -> Unit,
    collapsible: CollapsibleCalendarState,
    calendarState: CalendarState,
    weekState: WeekCalendarState,
    today: LocalDate,
    firstDayOption: FirstDayOption,
    onFirstDayChange: (FirstDayOption) -> Unit,
    outDatesOption: OutDatesOption,
    onOutDatesChange: (OutDatesOption) -> Unit,
    showWeekNumbers: Boolean,
    onShowWeekNumbersChange: (Boolean) -> Unit,
    selectionChoice: StudioSelectionChoice,
    onSelectionChoiceChange: (StudioSelectionChoice) -> Unit,
    system: AlmanacSystem,
    onSystemChange: (AlmanacSystem) -> Unit,
    country: HolidayCountry,
    onCountryChange: (HolidayCountry) -> Unit,
) {
    val scope = rememberCoroutineScope()
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = "Style Studio & Dynamic Controls",
            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
        )

        ControlSectionTitle("Visual Theme Preset")
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            ShowcaseThemePreset.entries.forEach { preset ->
                FilterChip(
                    selected = themePreset == preset,
                    onClick = { onThemePresetChange(preset) },
                    label = { Text(preset.label) },
                )
            }
        }
        if (themePreset == ShowcaseThemePreset.RoundedAndCard) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = !cardGridSubMode,
                    onClick = { onCardGridSubModeChange(false) },
                    label = { Text("Rounded Square") },
                )
                FilterChip(
                    selected = cardGridSubMode,
                    onClick = { onCardGridSubModeChange(true) },
                    label = { Text("Bordered Card Grid") },
                )
            }
        }

        HorizontalDivider()
        ControlSectionTitle("Layout & Navigation")
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            AssistChip(
                onClick = {
                    scope.launch {
                        if (collapsible.isExpanded) collapsible.collapse() else collapsible.expand()
                    }
                },
                label = { Text(if (collapsible.isExpanded) "Collapse to Week" else "Expand to Month") },
            )
            AssistChip(
                onClick = {
                    scope.launch {
                        calendarState.animateScrollToToday()
                        weekState.animateScrollToDate(today)
                    }
                },
                label = { Text("Scroll to Today") },
            )
            AssistChip(
                onClick = {
                    scope.launch {
                        calendarState.animateScrollToMonth(
                            CalendarPages.monthAt(calendarState.firstVisibleMonth, -1),
                        )
                    }
                },
                label = { Text("Prev Month") },
            )
            AssistChip(
                onClick = {
                    scope.launch {
                        calendarState.animateScrollToMonth(
                            CalendarPages.monthAt(calendarState.firstVisibleMonth, 1),
                        )
                    }
                },
                label = { Text("Next Month") },
            )
        }

        ControlSectionTitle("First Day of Week & Grid")
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            FirstDayOption.entries.forEach { option ->
                FilterChip(
                    selected = firstDayOption == option,
                    onClick = { onFirstDayChange(option) },
                    label = { Text(option.label) },
                )
            }
            FilterChip(
                selected = showWeekNumbers,
                onClick = { onShowWeekNumbersChange(!showWeekNumbers) },
                label = { Text("Week Numbers") },
            )
        }

        ControlSectionTitle("Out-Dates Mode")
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutDatesOption.entries.forEach { option ->
                FilterChip(
                    selected = outDatesOption == option,
                    onClick = { onOutDatesChange(option) },
                    label = { Text(option.label) },
                )
            }
        }

        ControlSectionTitle("Selection Mode")
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            StudioSelectionChoice.entries.forEach { option ->
                FilterChip(
                    selected = selectionChoice == option,
                    onClick = { onSelectionChoiceChange(option) },
                    label = { Text(option.label) },
                )
            }
        }

        HorizontalDivider()
        ControlSectionTitle("Almanac System & Public Holidays")
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
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
            modifier = Modifier.horizontalScroll(rememberScrollState()),
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
        Spacer(Modifier.height(16.dp))
    }
}

@Composable
private fun ControlSectionTitle(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
        color = MaterialTheme.colorScheme.primary,
    )
}
