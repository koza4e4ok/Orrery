package me.kozakov.orrery.compose

import androidx.compose.runtime.Immutable

/**
 * English default strings for the date picker dialogs. Callers
 * localize by passing their own values to [DatePickerDefaults.strings].
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
    public val closeDescription: String,
)

/** Defaults for the date picker dialogs. */
public object DatePickerDefaults {
    /**
     * Builds [DatePickerStrings], defaulting to English. Callers
     * localize by overriding the parameters they need.
     */
    public fun strings(
        title: String = "Select date",
        rangeTitle: String = "Select dates",
        headlinePlaceholder: String = "Selected date",
        rangeStartPlaceholder: String = "Start date",
        rangeEndPlaceholder: String = "End date",
        inputLabel: String = "Date",
        rangeStartInputLabel: String = "Start date",
        rangeEndInputLabel: String = "End date",
        invalidFormatError: String = "Date must match %s",
        outOfRangeError: String = "Date out of allowed range",
        disabledDateError: String = "Date is unavailable",
        invalidRangeError: String = "Invalid date range",
        switchToInputDescription: String = "Switch to text input",
        switchToPickerDescription: String = "Switch to calendar",
        closeDescription: String = "Close",
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
            closeDescription = closeDescription,
        )
}
