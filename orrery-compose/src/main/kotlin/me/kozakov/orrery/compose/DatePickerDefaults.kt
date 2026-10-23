package me.kozakov.orrery.compose

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable

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
}
