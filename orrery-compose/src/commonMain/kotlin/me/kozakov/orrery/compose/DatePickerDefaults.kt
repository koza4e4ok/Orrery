package me.kozakov.orrery.compose

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.YearMonth
import me.kozakov.orrery.core.Selection
import me.kozakov.orrery.core.SelectionPresets

/**
 * Strings for the date picker dialogs. [DatePickerDefaults.strings]
 * resolves them from the current locale's resources; callers can
 * override any of them.
 */
@Immutable
public class DatePickerStrings(
    public val title: String,
    public val rangeTitle: String,
    public val headlinePlaceholder: String,
    public val rangeStartPlaceholder: String,
    public val rangeEndPlaceholder: String,
    public val inputLabel: String,
    public val rangeStartInputLabel: String,
    public val rangeEndInputLabel: String,
    public val invalidFormatError: String,
    public val outOfRangeError: String,
    public val disabledDateError: String,
    public val invalidRangeError: String,
    public val switchToInputDescription: String,
    public val switchToPickerDescription: String,
    public val previousMonthDescription: String,
    public val nextMonthDescription: String,
    public val closeDescription: String,
)

/**
 * Strings for [OrreryDateTimePickerDialog]'s two steps and buttons.
 * [DatePickerDefaults.dateTimeStrings] resolves them from the current
 * locale's resources; callers can override any of them.
 */
@Immutable
public class DateTimePickerStrings(
    public val title: String,
    public val next: String,
    public val back: String,
    public val timeTitle: String,
    public val switchToClock: String,
    public val switchToKeyboard: String,
)

/**
 * Strings for [OrreryMonthPickerDialog]. [DatePickerDefaults.monthPickerStrings]
 * resolves them from the current locale's resources; callers can override
 * any of them.
 */
@Immutable
public class MonthPickerStrings(
    public val title: String,
    public val headlinePlaceholder: String,
)

/** Defaults for the date picker dialogs. */
public object DatePickerDefaults {
    /**
     * Builds [DatePickerStrings]. Defaults are resolved from the current
     * locale's resources; override any parameter to replace a string.
     * [invalidFormatError] is a template receiving the expected pattern.
     */
    @Composable
    @ReadOnlyComposable
    public fun strings(
        title: String = orreryString(OrreryString.DatePickerTitle),
        rangeTitle: String = orreryString(OrreryString.DatePickerRangeTitle),
        headlinePlaceholder: String = orreryString(OrreryString.DatePickerHeadlinePlaceholder),
        rangeStartPlaceholder: String = orreryString(OrreryString.DatePickerRangeStartPlaceholder),
        rangeEndPlaceholder: String = orreryString(OrreryString.DatePickerRangeEndPlaceholder),
        inputLabel: String = orreryString(OrreryString.DatePickerInputLabel),
        rangeStartInputLabel: String = orreryString(OrreryString.DatePickerRangeStartInputLabel),
        rangeEndInputLabel: String = orreryString(OrreryString.DatePickerRangeEndInputLabel),
        invalidFormatError: String = orreryString(OrreryString.DatePickerInvalidFormatError),
        outOfRangeError: String = orreryString(OrreryString.DatePickerOutOfRangeError),
        disabledDateError: String = orreryString(OrreryString.DatePickerDisabledDateError),
        invalidRangeError: String = orreryString(OrreryString.DatePickerInvalidRangeError),
        switchToInputDescription: String = orreryString(OrreryString.DatePickerSwitchToInput),
        switchToPickerDescription: String = orreryString(OrreryString.DatePickerSwitchToPicker),
        previousMonthDescription: String = orreryString(OrreryString.PreviousMonth),
        nextMonthDescription: String = orreryString(OrreryString.NextMonth),
        closeDescription: String = orreryString(OrreryString.Close),
    ): DatePickerStrings =
        DatePickerStrings(
            title = title,
            rangeTitle = rangeTitle,
            headlinePlaceholder = headlinePlaceholder,
            rangeStartPlaceholder = rangeStartPlaceholder,
            rangeEndPlaceholder = rangeEndPlaceholder,
            inputLabel = inputLabel,
            rangeStartInputLabel = rangeStartInputLabel,
            rangeEndInputLabel = rangeEndInputLabel,
            invalidFormatError = invalidFormatError,
            outOfRangeError = outOfRangeError,
            disabledDateError = disabledDateError,
            invalidRangeError = invalidRangeError,
            switchToInputDescription = switchToInputDescription,
            switchToPickerDescription = switchToPickerDescription,
            previousMonthDescription = previousMonthDescription,
            nextMonthDescription = nextMonthDescription,
            closeDescription = closeDescription,
        )

    /**
     * Builds [DateTimePickerStrings]. Defaults are resolved from the current
     * locale's resources; override any parameter to replace a string.
     */
    @Composable
    @ReadOnlyComposable
    public fun dateTimeStrings(
        title: String = orreryString(OrreryString.DateTimePickerTitle),
        next: String = orreryString(OrreryString.DateTimePickerNext),
        back: String = orreryString(OrreryString.DateTimePickerBack),
        timeTitle: String = orreryString(OrreryString.DateTimePickerTimeTitle),
        switchToClock: String = orreryString(OrreryString.DateTimePickerSwitchToClock),
        switchToKeyboard: String = orreryString(OrreryString.DateTimePickerSwitchToKeyboard),
    ): DateTimePickerStrings =
        DateTimePickerStrings(
            title = title,
            next = next,
            back = back,
            timeTitle = timeTitle,
            switchToClock = switchToClock,
            switchToKeyboard = switchToKeyboard,
        )

    /**
     * Builds [MonthPickerStrings]. Defaults are resolved from the current
     * locale's resources; override any parameter to replace a string.
     */
    @Composable
    @ReadOnlyComposable
    public fun monthPickerStrings(
        title: String = orreryString(OrreryString.MonthPickerTitle),
        headlinePlaceholder: String = orreryString(OrreryString.MonthPickerHeadlinePlaceholder),
    ): MonthPickerStrings = MonthPickerStrings(title = title, headlinePlaceholder = headlinePlaceholder)

    /**
     * Default selectable range for [rememberOrreryMonthPickerState]: January of
     * [today]'s year minus 100 through December of its year plus 100.
     */
    public fun monthRange(today: LocalDate = currentDate()): ClosedRange<YearMonth> =
        YearMonth(today.year - MONTH_RANGE_YEARS, 1)..YearMonth(today.year + MONTH_RANGE_YEARS, 12)

    /**
     * Standard presets for [OrreryDateRangePickerDialog]'s `presets`, built
     * from [SelectionPresets] around [today]: last 7 days, last 30 days,
     * this week (starting at [firstDayOfWeek]) and this month. Labels come
     * from the current locale's resources.
     */
    @Composable
    @ReadOnlyComposable
    public fun rangePresets(
        today: LocalDate = currentDate(),
        firstDayOfWeek: DayOfWeek = PlatformDateFormat.firstDayOfWeek(),
    ): List<DateRangePreset> =
        listOf(
            DateRangePreset(
                orreryString(OrreryString.DatePickerPresetLast7Days),
                SelectionPresets.lastNDays(today, 7).requireRange(),
            ),
            DateRangePreset(
                orreryString(OrreryString.DatePickerPresetLast30Days),
                SelectionPresets.lastNDays(today, 30).requireRange(),
            ),
            DateRangePreset(
                orreryString(OrreryString.DatePickerPresetThisWeek),
                SelectionPresets.thisWeek(today, firstDayOfWeek).requireRange(),
            ),
            DateRangePreset(
                orreryString(OrreryString.DatePickerPresetThisMonth),
                SelectionPresets.thisMonth(today).requireRange(),
            ),
        )
}

private const val MONTH_RANGE_YEARS = 100

private fun Selection.requireRange(): ClosedRange<LocalDate> = checkNotNull(range)
