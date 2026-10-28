// TimePicker, TimeInput and rememberTimePickerState are experimental in Material 3;
// the opt-in stays inside this file, so callers need none.
@file:OptIn(ExperimentalMaterial3Api::class)

package me.kozakov.orrery.compose

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimeInput
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TimePickerLayoutType
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.launch
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.YearMonth
import me.kozakov.orrery.core.DisabledDates

/** Which step of [OrreryDateTimePickerDialog] is showing. */
public enum class DateTimePickerStep { Date, Time }

/**
 * State for [OrreryDateTimePickerDialog]: the date (through [dateState], with
 * its bounds and disabled dates), the time of day and the visible [step].
 * Writes outside 0..23 to [hour] or 0..59 to [minute] are ignored.
 * [selectedDateTime] is a [LocalDateTime]; attach a TimeZone yourself.
 */
@Stable
public class OrreryDateTimePickerState internal constructor(
    public val dateState: OrreryDatePickerState,
    initialHour: Int,
    initialMinute: Int,
    is24Hour: Boolean,
    initialStep: DateTimePickerStep,
) {
    private var hourValue by mutableIntStateOf(initialHour.coerceIn(0, 23))
    private var minuteValue by mutableIntStateOf(initialMinute.coerceIn(0, 59))

    /** Hour of day, 0..23. */
    public var hour: Int
        get() = hourValue
        set(value) {
            if (value in 0..23) hourValue = value
        }

    /** Minute of the hour, 0..59. */
    public var minute: Int
        get() = minuteValue
        set(value) {
            if (value in 0..59) minuteValue = value
        }

    /** Shows the time step on a 24-hour dial (true) or a 12-hour dial with AM/PM (false). */
    public var is24Hour: Boolean by mutableStateOf(is24Hour)

    /**
     * The visible step. Setting [DateTimePickerStep.Time] before a date is
     * picked is allowed: the Time step then shows its confirm button while
     * [selectedDateTime] is still null, so handle null in confirmButton.
     */
    public var step: DateTimePickerStep by mutableStateOf(initialStep)

    /** The picked date at [hour]:[minute], or null until a date is picked. Snapshot-observable. */
    public val selectedDateTime: LocalDateTime?
        get() = dateState.selectedDate?.let { LocalDateTime(it, LocalTime(hour, minute)) }

    internal companion object {
        private const val NO_DATE = Long.MIN_VALUE

        internal fun saver(
            yearRange: IntRange,
            bounds: ClosedRange<LocalDate>?,
            disabledDates: DisabledDates,
            firstDayOfWeek: DayOfWeek,
        ): Saver<OrreryDateTimePickerState, Any> =
            listSaver(
                save = {
                    listOf(
                        it.dateState.selectedDate?.toEpochDays() ?: NO_DATE,
                        it.hour,
                        it.minute,
                        it.step.ordinal,
                        it.is24Hour,
                        it.dateState.displayMode.ordinal,
                        it.dateState.displayedMonth.toString(),
                    )
                },
                restore = {
                    val epochDay = it[0] as Long
                    OrreryDateTimePickerState(
                        dateState =
                            OrreryDatePickerState(
                                initialDate = if (epochDay == NO_DATE) null else LocalDate.fromEpochDays(epochDay),
                                initialDisplayedMonth = YearMonth.parse(it[6] as String),
                                yearRange = yearRange,
                                initialDisplayMode = DatePickerDisplayMode.entries[it[5] as Int],
                                disabledDates = disabledDates,
                                firstDayOfWeek = firstDayOfWeek,
                                dateBounds = bounds,
                            ),
                        initialHour = it[1] as Int,
                        initialMinute = it[2] as Int,
                        is24Hour = it[4] as Boolean,
                        initialStep = DateTimePickerStep.entries[it[3] as Int],
                    )
                },
            )
    }
}

/**
 * Remembers saveable [OrreryDateTimePickerState] for [OrreryDateTimePickerDialog].
 * [bounds] and [disabled] restrict the date only. [is24Hour] defaults to the
 * device's clock: the "Use 24-hour format" setting on Android, the current
 * locale's clock on Desktop, iOS and Web.
 *
 * [initialDateTime] and [is24Hour] are read once, when the state is first
 * created (as with rememberOrreryDatePickerState); changing them later has no
 * effect. Write to the state's hour, minute or is24Hour properties instead.
 */
@Composable
public fun rememberOrreryDateTimePickerState(
    initialDateTime: LocalDateTime? = null,
    bounds: ClosedRange<LocalDate>? = null,
    disabled: DisabledDates = DisabledDates.None,
    is24Hour: Boolean = systemUses24HourClock(),
): OrreryDateTimePickerState {
    val yearRange = bounds?.let { it.start.year..it.endInclusive.year } ?: DEFAULT_YEAR_RANGE
    val firstDayOfWeek = PlatformDateFormat.firstDayOfWeek()
    val saver =
        remember(yearRange, bounds, firstDayOfWeek) {
            OrreryDateTimePickerState.saver(yearRange, bounds, disabled, firstDayOfWeek)
        }
    val state =
        rememberSaveable(bounds, firstDayOfWeek, saver = saver) {
            val initialDate = initialDateTime?.date
            val month = initialDate?.let { YearMonth(it.year, it.month) } ?: currentYearMonth()
            OrreryDateTimePickerState(
                dateState =
                    OrreryDatePickerState(
                        initialDate = initialDate,
                        initialDisplayedMonth = month.coerceInBounds(bounds, yearRange),
                        yearRange = yearRange,
                        initialDisplayMode = DatePickerDisplayMode.Picker,
                        disabledDates = disabled,
                        firstDayOfWeek = firstDayOfWeek,
                        dateBounds = bounds,
                    ),
                initialHour = initialDateTime?.hour ?: 0,
                initialMinute = initialDateTime?.minute ?: 0,
                is24Hour = is24Hour,
                initialStep = DateTimePickerStep.Date,
            )
        }
    SideEffect { state.dateState.selection.disabled = disabled }
    return state
}

private val DEFAULT_YEAR_RANGE = 1971..2055

/** Opens the calendar on a month that has selectable dates. */
private fun YearMonth.coerceInBounds(
    bounds: ClosedRange<LocalDate>?,
    years: IntRange,
): YearMonth =
    if (bounds != null) {
        coerceIn(
            YearMonth(bounds.start.year, bounds.start.month),
            YearMonth(bounds.endInclusive.year, bounds.endInclusive.month),
        )
    } else {
        coerceIn(YearMonth(years.first, 1), YearMonth(years.last, 12))
    }

/**
 * Material-3-shaped dialog that picks a date, then a time of day, in one
 * surface: the Date step is the [OrreryDatePickerDialog] body (calendar or
 * text input); **Next** opens the Time step, a Material 3 TimePicker dial
 * with a toggle to TimeInput. The headline shows both parts, with the
 * active step's part on a secondaryContainer pill; tap either to jump to its
 * step. Steps slide and fade into each other, and on short windows the body
 * scrolls so the button row stays visible. Read state.selectedDateTime in [confirmButton], which
 * shows on the Time step. [dateStrings] localizes the date step.
 */
@Composable
public fun OrreryDateTimePickerDialog(
    onDismissRequest: () -> Unit,
    confirmButton: @Composable () -> Unit,
    state: OrreryDateTimePickerState = rememberOrreryDateTimePickerState(),
    modifier: Modifier = Modifier,
    dismissButton: (@Composable () -> Unit)? = null,
    colors: CalendarDayColors = CalendarDefaults.dayColors(),
    shapes: CalendarDayShapes = CalendarDefaults.dayShapes(),
    today: LocalDate = currentDate(),
    strings: DateTimePickerStrings = DatePickerDefaults.dateTimeStrings(),
    dateStrings: DatePickerStrings = DatePickerDefaults.strings(),
) {
    var timeInput by rememberSaveable { mutableStateOf(false) }
    val date = state.dateState.selectedDate
    val onDateStep = state.step == DateTimePickerStep.Date
    Dialog(
        onDismissRequest = onDismissRequest,
        // See OrreryDatePickerDialog: a fixed width keeps the text field from thrashing measurement.
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            shape = MaterialTheme.shapes.extraLarge,
            tonalElevation = 6.dp,
            modifier = modifier.requiredWidth(360.dp),
        ) {
            Column(Modifier.padding(24.dp)) {
                Text(
                    if (onDateStep) strings.title else strings.timeTitle,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    // The pills share one weighted row so the date only wraps when the
                    // time and the toggle leave it too little room.
                    Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                        HeadlineStepPill(
                            text =
                                date?.let { PlatformDateFormat.formatDate(it, DateFormattingStyle.Medium) }
                                    ?: dateStrings.headlinePlaceholder,
                            active = onDateStep,
                            onClick = { state.step = DateTimePickerStep.Date },
                            modifier = Modifier.weight(1f, fill = false),
                        )
                        HeadlineStepPill(
                            text = PlatformDateFormat.formatTime(state.hour, state.minute, state.is24Hour),
                            active = !onDateStep,
                            enabled = date != null,
                            onClick = { state.step = DateTimePickerStep.Time },
                        )
                    }
                    if (onDateStep) {
                        DisplayModeToggle(state.dateState, dateStrings)
                    } else {
                        IconButton(onClick = { timeInput = !timeInput }) {
                            if (timeInput) {
                                ClockGlyph(contentDescription = strings.switchToClock)
                            } else {
                                KeyboardGlyph(contentDescription = strings.switchToKeyboard)
                            }
                        }
                    }
                }
                HorizontalDivider(Modifier.padding(vertical = 8.dp))
                // Takes only the height left over by the headline and buttons, scrolling
                // on short windows so the button row is never pushed out of the dialog.
                AnimatedContent(
                    targetState = state.step,
                    transitionSpec = { stepTransition() },
                    label = "dateTimeStep",
                    modifier = Modifier.weight(1f, fill = false),
                ) { step ->
                    Box(Modifier.verticalScroll(rememberScrollState())) {
                        if (step == DateTimePickerStep.Date) {
                            DatePickerContent(state.dateState, colors, shapes, today, dateStrings)
                        } else {
                            TimeStep(state, timeInput)
                        }
                    }
                }
                Row(
                    Modifier.fillMaxWidth().padding(top = 8.dp),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (!onDateStep) {
                        TextButton(onClick = { state.step = DateTimePickerStep.Date }) { Text(strings.back) }
                    }
                    Spacer(Modifier.weight(1f))
                    dismissButton?.invoke()
                    if (onDateStep) {
                        TextButton(
                            onClick = { state.step = DateTimePickerStep.Time },
                            enabled = date != null,
                        ) { Text(strings.next) }
                    } else {
                        confirmButton()
                    }
                }
            }
        }
    }
}

/** Date slides in from the left, Time from the right, each with a fade. */
private fun AnimatedContentTransitionScope<DateTimePickerStep>.stepTransition(): ContentTransform {
    val direction = if (targetState.ordinal > initialState.ordinal) 1 else -1
    return (slideInHorizontally { width -> direction * width / STEP_SLIDE_DIVISOR } + fadeIn())
        .togetherWith(slideOutHorizontally { width -> -direction * width / STEP_SLIDE_DIVISOR } + fadeOut())
}

/** Slide by a quarter of the body width: enough to read as direction, short enough to stay calm. */
private const val STEP_SLIDE_DIVISOR = 4

/**
 * One headline part (date or time). The active step's part sits on a
 * secondaryContainer pill; it is selectable, so accessibility services
 * announce which step is showing.
 */
@Composable
private fun HeadlineStepPill(
    text: String,
    active: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val colorScheme = MaterialTheme.colorScheme
    val container by animateColorAsState(
        if (active) colorScheme.secondaryContainer else Color.Transparent,
        label = "stepPillContainer",
    )
    val content by animateColorAsState(
        if (active) colorScheme.onSecondaryContainer else colorScheme.onSurfaceVariant,
        label = "stepPillContent",
    )
    Text(
        text,
        style = MaterialTheme.typography.headlineSmall,
        color = content,
        modifier =
            modifier
                .clip(RoundedCornerShape(16.dp))
                .background(container)
                .selectable(selected = active, enabled = enabled, role = Role.Tab, onClick = onClick)
                .padding(horizontal = 6.dp, vertical = 2.dp),
    )
}

/** Material 3 dial or keyboard input, kept in sync with [state] both ways. */
@Composable
private fun TimeStep(
    state: OrreryDateTimePickerState,
    useInput: Boolean,
) {
    val timeState =
        key(state.is24Hour) {
            rememberTimePickerState(initialHour = state.hour, initialMinute = state.minute, is24Hour = state.is24Hour)
        }
    LaunchedEffect(timeState) {
        launch {
            snapshotFlow { timeState.hour to timeState.minute }.collect { (hour, minute) ->
                state.hour = hour
                state.minute = minute
            }
        }
        snapshotFlow { state.hour to state.minute }.collect { (hour, minute) ->
            if (timeState.hour != hour) timeState.hour = hour
            if (timeState.minute != minute) timeState.minute = minute
        }
    }
    Box(Modifier.fillMaxWidth().padding(vertical = 8.dp), contentAlignment = Alignment.Center) {
        if (useInput) {
            TimeInput(state = timeState)
        } else {
            TimePicker(state = timeState, layoutType = TimePickerLayoutType.Vertical)
        }
    }
}
