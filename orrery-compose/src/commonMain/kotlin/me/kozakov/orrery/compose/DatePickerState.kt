package me.kozakov.orrery.compose

import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.YearMonth
import me.kozakov.orrery.core.DisabledDates
import me.kozakov.orrery.core.OutDateStyle
import me.kozakov.orrery.core.Selection
import me.kozakov.orrery.core.SelectionMode

/** Which face of a picker dialog is showing. */
public enum class DatePickerDisplayMode { Picker, Input }

/**
 * State for [OrreryDatePickerDialog]: the selected date, the display
 * mode, and the visible month. Writes to [selectedDate] are validated
 * like clicks (bounds and disabled dates); invalid writes are ignored.
 */
@Stable
public class OrreryDatePickerState internal constructor(
    initialDate: LocalDate?,
    initialDisplayedMonth: YearMonth,
    public val yearRange: IntRange,
    initialDisplayMode: DatePickerDisplayMode,
    disabledDates: DisabledDates,
    firstDayOfWeek: DayOfWeek,
    // Narrower selectable dates inside yearRange (OrreryDateTimePickerState's bounds).
    dateBounds: ClosedRange<LocalDate>? = null,
) {
    internal val bounds: ClosedRange<LocalDate> =
        dateBounds ?: (LocalDate(yearRange.first, 1, 1)..LocalDate(yearRange.last, 12, 31))

    internal val calendar: CalendarState =
        CalendarState(
            startMonth = YearMonth(yearRange.first, 1),
            endMonth = YearMonth(yearRange.last, 12),
            firstVisibleMonth = initialDisplayedMonth,
            firstDayOfWeek = firstDayOfWeek,
            outDateStyle = OutDateStyle.EndOfRow,
        )

    internal val selection: CalendarSelectionState =
        CalendarSelectionState(
            mode = SelectionMode.Single(),
            initialSelection = initialDate?.let { Selection(single = it) } ?: Selection.Empty,
            bounds = bounds,
            disabled = disabledDates,
            onEvent = {},
        )

    public var displayMode: DatePickerDisplayMode by mutableStateOf(initialDisplayMode)

    public var selectedDate: LocalDate?
        get() = selection.selection.single
        set(value) {
            if (value == null) selection.clear() else selection.set(Selection(single = value))
        }

    /** The month at the picker's first visible page. Snapshot-observable. */
    public val displayedMonth: YearMonth
        get() = calendar.firstVisibleMonth

    internal companion object {
        internal fun saver(
            yearRange: IntRange,
            disabledDates: DisabledDates,
            firstDayOfWeek: DayOfWeek,
        ): Saver<OrreryDatePickerState, Any> =
            listSaver(
                save = {
                    listOf(
                        it.selectedDate?.toString() ?: "",
                        it.displayMode.ordinal,
                        it.displayedMonth.toString(),
                    )
                },
                restore = {
                    OrreryDatePickerState(
                        initialDate = (it[0] as String).takeIf(String::isNotEmpty)?.let(LocalDate::parse),
                        initialDisplayedMonth = YearMonth.parse(it[2] as String),
                        yearRange = yearRange,
                        initialDisplayMode = DatePickerDisplayMode.entries[it[1] as Int],
                        disabledDates = disabledDates,
                        firstDayOfWeek = firstDayOfWeek,
                    )
                },
            )
    }
}

/** Remembers saveable [OrreryDatePickerState] for [OrreryDatePickerDialog]. */
@Composable
public fun rememberOrreryDatePickerState(
    initialDate: LocalDate? = null,
    initialDisplayedMonth: YearMonth =
        initialDate?.let { YearMonth(it.year, it.month) } ?: currentYearMonth(),
    yearRange: IntRange = 1971..2055,
    initialDisplayMode: DatePickerDisplayMode = DatePickerDisplayMode.Picker,
    disabledDates: DisabledDates = DisabledDates.None,
    firstDayOfWeek: DayOfWeek = PlatformDateFormat.firstDayOfWeek(),
): OrreryDatePickerState {
    val saver =
        remember(yearRange, firstDayOfWeek) {
            OrreryDatePickerState.saver(yearRange, disabledDates, firstDayOfWeek)
        }
    val state =
        rememberSaveable(yearRange, firstDayOfWeek, saver = saver) {
            OrreryDatePickerState(
                initialDate = initialDate,
                initialDisplayedMonth = initialDisplayedMonth,
                yearRange = yearRange,
                initialDisplayMode = initialDisplayMode,
                disabledDates = disabledDates,
                firstDayOfWeek = firstDayOfWeek,
            )
        }
    SideEffect { state.selection.disabled = disabledDates }
    return state
}

/**
 * State for [OrreryDateRangePickerDialog]: the selected range, the
 * display mode, and the visible month. Writes to [setSelection] are
 * validated like clicks (bounds, disabled dates, and range length);
 * an end before the start is dropped, other invalid writes are ignored.
 *
 * [disallowDisabledInRange], [minRangeDays] and [maxRangeDays] are the
 * booking range guardrails of [CalendarSelectionState] (inclusive day
 * counts). Grid taps follow them (a span across a disabled date or over
 * [maxRangeDays] restarts at the tapped date when allowed, a span under
 * [minRangeDays] is ignored); typed input and [setSelection] that break
 * them are rejected, and the input face shows its invalid-range error.
 */
@Stable
public class OrreryDateRangePickerState internal constructor(
    initialStartDate: LocalDate?,
    initialEndDate: LocalDate?,
    initialDisplayedMonth: YearMonth,
    public val yearRange: IntRange,
    initialDisplayMode: DatePickerDisplayMode,
    minDays: Int?,
    maxDays: Int?,
    disabledDates: DisabledDates,
    firstDayOfWeek: DayOfWeek,
    /**
     * Whether a range may not span a disabled date. When true a completing
     * grid tap across one restarts the range at the tapped date, and typed
     * input or [setSelection] spanning one is rejected. A disabled start or
     * end is always rejected.
     */
    public val disallowDisabledInRange: Boolean = false,
    /** Minimum inclusive range length in days (`>= 1`). */
    public val minRangeDays: Int = 1,
    /** Maximum inclusive range length in days, or null for unbounded. */
    public val maxRangeDays: Int? = null,
) {
    internal val bounds: ClosedRange<LocalDate> =
        LocalDate(yearRange.first, 1, 1)..LocalDate(yearRange.last, 12, 31)

    internal val calendar: CalendarState =
        CalendarState(
            startMonth = YearMonth(yearRange.first, 1),
            endMonth = YearMonth(yearRange.last, 12),
            firstVisibleMonth = initialDisplayedMonth,
            firstDayOfWeek = firstDayOfWeek,
            outDateStyle = OutDateStyle.EndOfRow,
        )

    internal val selection: CalendarSelectionState =
        CalendarSelectionState(
            mode = SelectionMode.Range(minDays = minDays, maxDays = maxDays),
            initialSelection =
                if (initialStartDate != null) {
                    Selection(rangeStart = initialStartDate, rangeEnd = initialEndDate)
                } else {
                    Selection.Empty
                },
            bounds = bounds,
            disabled = disabledDates,
            onEvent = {},
            disallowDisabledInRange = disallowDisabledInRange,
            minRangeDays = minRangeDays,
            maxRangeDays = maxRangeDays,
        )

    public var displayMode: DatePickerDisplayMode by mutableStateOf(initialDisplayMode)

    public val selectedStartDate: LocalDate?
        get() = selection.selection.rangeStart

    public val selectedEndDate: LocalDate?
        get() = selection.selection.rangeEnd

    /**
     * Replaces the range through selection-engine validation; null [start]
     * clears. A reversed pair (`end` before `start`) is clamped to a
     * half-open range (`end = null`) before proposing, since the engine's
     * length check only rejects reversed pairs when `minDays`/`maxDays`
     * are configured.
     */
    public fun setSelection(
        start: LocalDate?,
        end: LocalDate?,
    ) {
        if (start == null) {
            selection.clear()
        } else {
            val clampedEnd = if (end != null && end < start) null else end
            selection.set(Selection(rangeStart = start, rangeEnd = clampedEnd))
        }
    }

    /** The month at the picker's first visible page. Snapshot-observable. */
    public val displayedMonth: YearMonth
        get() = calendar.firstVisibleMonth

    internal companion object {
        internal fun saver(
            yearRange: IntRange,
            minDays: Int?,
            maxDays: Int?,
            disabledDates: DisabledDates,
            firstDayOfWeek: DayOfWeek,
            disallowDisabledInRange: Boolean = false,
            minRangeDays: Int = 1,
            maxRangeDays: Int? = null,
        ): Saver<OrreryDateRangePickerState, Any> =
            listSaver(
                save = {
                    listOf(
                        it.selectedStartDate?.toString() ?: "",
                        it.selectedEndDate?.toString() ?: "",
                        it.displayMode.ordinal,
                        it.displayedMonth.toString(),
                    )
                },
                restore = {
                    OrreryDateRangePickerState(
                        initialStartDate = (it[0] as String).takeIf(String::isNotEmpty)?.let(LocalDate::parse),
                        initialEndDate = (it[1] as String).takeIf(String::isNotEmpty)?.let(LocalDate::parse),
                        initialDisplayedMonth = YearMonth.parse(it[3] as String),
                        yearRange = yearRange,
                        initialDisplayMode = DatePickerDisplayMode.entries[it[2] as Int],
                        minDays = minDays,
                        maxDays = maxDays,
                        disabledDates = disabledDates,
                        firstDayOfWeek = firstDayOfWeek,
                        disallowDisabledInRange = disallowDisabledInRange,
                        minRangeDays = minRangeDays,
                        maxRangeDays = maxRangeDays,
                    )
                },
            )
    }
}

/**
 * Remembers saveable [OrreryDateRangePickerState] for [OrreryDateRangePickerDialog].
 * Changing [minDays], [maxDays], [disallowDisabledInRange], [minRangeDays]
 * or [maxRangeDays] recreates the state (keeping the saved selection only
 * through restoration).
 */
@Composable
public fun rememberOrreryDateRangePickerState(
    initialStartDate: LocalDate? = null,
    initialEndDate: LocalDate? = null,
    initialDisplayedMonth: YearMonth =
        initialStartDate?.let { YearMonth(it.year, it.month) } ?: currentYearMonth(),
    yearRange: IntRange = 1971..2055,
    initialDisplayMode: DatePickerDisplayMode = DatePickerDisplayMode.Picker,
    minDays: Int? = null,
    maxDays: Int? = null,
    disabledDates: DisabledDates = DisabledDates.None,
    firstDayOfWeek: DayOfWeek = PlatformDateFormat.firstDayOfWeek(),
    disallowDisabledInRange: Boolean = false,
    minRangeDays: Int = 1,
    maxRangeDays: Int? = null,
): OrreryDateRangePickerState {
    requireValidRangeDays(minRangeDays, maxRangeDays)
    val saver =
        remember(yearRange, firstDayOfWeek, minDays, maxDays, disallowDisabledInRange, minRangeDays, maxRangeDays) {
            OrreryDateRangePickerState.saver(
                yearRange,
                minDays,
                maxDays,
                disabledDates,
                firstDayOfWeek,
                disallowDisabledInRange,
                minRangeDays,
                maxRangeDays,
            )
        }
    val state =
        rememberSaveable(
            yearRange,
            firstDayOfWeek,
            minDays,
            maxDays,
            disallowDisabledInRange,
            minRangeDays,
            maxRangeDays,
            saver = saver,
        ) {
            OrreryDateRangePickerState(
                initialStartDate = initialStartDate,
                initialEndDate = initialEndDate,
                initialDisplayedMonth = initialDisplayedMonth,
                yearRange = yearRange,
                initialDisplayMode = initialDisplayMode,
                minDays = minDays,
                maxDays = maxDays,
                disabledDates = disabledDates,
                firstDayOfWeek = firstDayOfWeek,
                disallowDisabledInRange = disallowDisabledInRange,
                minRangeDays = minRangeDays,
                maxRangeDays = maxRangeDays,
            )
        }
    SideEffect { state.selection.disabled = disabledDates }
    return state
}

@Deprecated("Kept for binary compatibility; use the overload with range guardrails.", level = DeprecationLevel.HIDDEN)
@Composable
public fun rememberOrreryDateRangePickerState(
    initialStartDate: LocalDate? = null,
    initialEndDate: LocalDate? = null,
    initialDisplayedMonth: YearMonth =
        initialStartDate?.let { YearMonth(it.year, it.month) } ?: currentYearMonth(),
    yearRange: IntRange = 1971..2055,
    initialDisplayMode: DatePickerDisplayMode = DatePickerDisplayMode.Picker,
    minDays: Int? = null,
    maxDays: Int? = null,
    disabledDates: DisabledDates = DisabledDates.None,
    firstDayOfWeek: DayOfWeek = PlatformDateFormat.firstDayOfWeek(),
): OrreryDateRangePickerState =
    rememberOrreryDateRangePickerState(
        initialStartDate = initialStartDate,
        initialEndDate = initialEndDate,
        initialDisplayedMonth = initialDisplayedMonth,
        yearRange = yearRange,
        initialDisplayMode = initialDisplayMode,
        minDays = minDays,
        maxDays = maxDays,
        disabledDates = disabledDates,
        firstDayOfWeek = firstDayOfWeek,
        disallowDisabledInRange = false,
    )
