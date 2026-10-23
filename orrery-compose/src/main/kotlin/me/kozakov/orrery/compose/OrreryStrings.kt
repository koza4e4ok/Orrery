package me.kozakov.orrery.compose

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.res.stringResource

/** Built-in UI strings, backed by the `orrery_*` Android string resources. */
internal enum class OrreryString(
    @StringRes val resId: Int,
) {
    DatePickerTitle(R.string.orrery_date_picker_title),
    DatePickerRangeTitle(R.string.orrery_date_picker_range_title),
    DatePickerHeadlinePlaceholder(R.string.orrery_date_picker_headline_placeholder),
    DatePickerRangeStartPlaceholder(R.string.orrery_date_picker_range_start_placeholder),
    DatePickerRangeEndPlaceholder(R.string.orrery_date_picker_range_end_placeholder),
    DatePickerInputLabel(R.string.orrery_date_picker_input_label),
    DatePickerRangeStartInputLabel(R.string.orrery_date_picker_range_start_input_label),
    DatePickerRangeEndInputLabel(R.string.orrery_date_picker_range_end_input_label),
    DatePickerInvalidFormatError(R.string.orrery_date_picker_invalid_format_error),
    DatePickerOutOfRangeError(R.string.orrery_date_picker_out_of_range_error),
    DatePickerDisabledDateError(R.string.orrery_date_picker_disabled_date_error),
    DatePickerInvalidRangeError(R.string.orrery_date_picker_invalid_range_error),
    DatePickerSwitchToInput(R.string.orrery_date_picker_switch_to_input),
    DatePickerSwitchToPicker(R.string.orrery_date_picker_switch_to_picker),
    PreviousMonth(R.string.orrery_previous_month),
    NextMonth(R.string.orrery_next_month),
    Close(R.string.orrery_close),
    MonthYearPickerDecade(R.string.orrery_month_year_picker_decade),
    HeatmapLegendLess(R.string.orrery_heatmap_legend_less),
    HeatmapLegendMore(R.string.orrery_heatmap_legend_more),
    HeatmapDayDescription(R.string.orrery_heatmap_day_description),
    HeatmapDayDescriptionEmpty(R.string.orrery_heatmap_day_description_empty),
    HeatmapSummaryActiveDays(R.string.orrery_heatmap_summary_active_days),
    HeatmapSummaryStreak(R.string.orrery_heatmap_summary_streak),
    AgendaTodayPrefix(R.string.orrery_agenda_today_prefix),
    AgendaAllDay(R.string.orrery_agenda_all_day),
    AgendaNoEvents(R.string.orrery_agenda_no_events),
}

/**
 * Resolves a built-in string for the current locale. The only lookup
 * point for library text, so the backing source can be swapped here
 * (e.g. for Compose Multiplatform resources) without touching callers.
 * Templates are returned raw; callers format them.
 */
@Composable
@ReadOnlyComposable
internal fun orreryString(string: OrreryString): String = stringResource(string.resId)
