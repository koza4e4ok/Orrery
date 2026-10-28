package me.kozakov.orrery.compose

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable

/**
 * Built-in UI strings. On Android they are the `orrery_*` string resources;
 * other platforms use the bundled translations in OrreryStrings.nonAndroid.kt.
 */
internal enum class OrreryString(
    /** Name of the backing Android string resource. */
    val resourceName: String,
) {
    DatePickerTitle("orrery_date_picker_title"),
    DatePickerRangeTitle("orrery_date_picker_range_title"),
    DatePickerHeadlinePlaceholder("orrery_date_picker_headline_placeholder"),
    DatePickerRangeStartPlaceholder("orrery_date_picker_range_start_placeholder"),
    DatePickerRangeEndPlaceholder("orrery_date_picker_range_end_placeholder"),
    DatePickerInputLabel("orrery_date_picker_input_label"),
    DatePickerRangeStartInputLabel("orrery_date_picker_range_start_input_label"),
    DatePickerRangeEndInputLabel("orrery_date_picker_range_end_input_label"),
    DatePickerInvalidFormatError("orrery_date_picker_invalid_format_error"),
    DatePickerOutOfRangeError("orrery_date_picker_out_of_range_error"),
    DatePickerDisabledDateError("orrery_date_picker_disabled_date_error"),
    DatePickerInvalidRangeError("orrery_date_picker_invalid_range_error"),
    DatePickerSwitchToInput("orrery_date_picker_switch_to_input"),
    DatePickerSwitchToPicker("orrery_date_picker_switch_to_picker"),
    DatePickerPresetLast7Days("orrery_date_picker_preset_last_7_days"),
    DatePickerPresetLast30Days("orrery_date_picker_preset_last_30_days"),
    DatePickerPresetThisWeek("orrery_date_picker_preset_this_week"),
    DatePickerPresetThisMonth("orrery_date_picker_preset_this_month"),
    PreviousMonth("orrery_previous_month"),
    NextMonth("orrery_next_month"),
    Close("orrery_close"),
    MonthYearPickerDecade("orrery_month_year_picker_decade"),
    HeatmapLegendLess("orrery_heatmap_legend_less"),
    HeatmapLegendMore("orrery_heatmap_legend_more"),
    HeatmapDayDescription("orrery_heatmap_day_description"),
    HeatmapDayDescriptionEmpty("orrery_heatmap_day_description_empty"),
    HeatmapSummaryActiveDays("orrery_heatmap_summary_active_days"),
    HeatmapSummaryStreak("orrery_heatmap_summary_streak"),
    DayRangeStart("orrery_day_state_range_start"),
    DayRangeEnd("orrery_day_state_range_end"),
    DayInRange("orrery_day_state_in_range"),
    DayToday("orrery_day_state_today"),
    DayUnavailable("orrery_day_state_unavailable"),
    AgendaTodayPrefix("orrery_agenda_today_prefix"),
    AgendaAllDay("orrery_agenda_all_day"),
    AgendaNoEvents("orrery_agenda_no_events"),
    ScheduleNow("orrery_schedule_now"),
    DateTimePickerTitle("orrery_date_time_picker_title"),
    DateTimePickerNext("orrery_date_time_picker_next"),
    DateTimePickerBack("orrery_date_time_picker_back"),
    DateTimePickerTimeTitle("orrery_date_time_picker_time_title"),
    DateTimePickerSwitchToClock("orrery_date_time_picker_switch_to_clock"),
    DateTimePickerSwitchToKeyboard("orrery_date_time_picker_switch_to_keyboard"),
    MonthPickerTitle("orrery_month_picker_title"),
    MonthPickerHeadlinePlaceholder("orrery_month_picker_headline_placeholder"),
}

/**
 * Resolves a built-in string for the current locale. The only lookup
 * point for library text, so the backing source can be swapped here
 * without touching callers. Templates are returned raw; callers format them.
 */
@Composable
@ReadOnlyComposable
internal expect fun orreryString(string: OrreryString): String
