package me.kozakov.orrery.compose

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import me.kozakov.orrery.core.HeatmapSummary
import kotlin.math.min

/** Number of a11y/legend buckets; fixed regardless of a custom color scale. */
internal const val HEATMAP_LEVELS: Int = 5

/** 1-based bucket of a clamped 0..1 intensity: 0f -> 1, 1f -> [HEATMAP_LEVELS]. */
internal fun heatLevel(value: Float): Int = min(HEATMAP_LEVELS, (value.coerceIn(0f, 1f) * HEATMAP_LEVELS).toInt() + 1)

/**
 * Strings for the heatmap composables. [HeatmapDefaults.strings]
 * resolves them from the current locale's resources; callers can
 * override any of them. The day-description
 * templates receive the formatted date, the level, and the max level in
 * that order.
 */
@Immutable
public class HeatmapStrings(
    public val legendLess: String,
    public val legendMore: String,
    public val dayDescription: String,
    public val dayDescriptionEmpty: String,
    public val summaryActiveDays: String,
    public val summaryStreak: String,
)

/** Defaults for [HeatmapCalendar] and [HeatmapMonth]. */
public object HeatmapDefaults {
    /**
     * Quantized color scale: 0..1 split into [levels] buckets, each lerped
     * from `surfaceVariant` toward `primary`. Values are clamped.
     */
    @Composable
    public fun colorScale(levels: Int = HEATMAP_LEVELS): (Float) -> Color {
        val from = MaterialTheme.colorScheme.surfaceVariant
        val to = MaterialTheme.colorScheme.primary
        return remember(levels, from, to) {
            { value ->
                val clamped = value.coerceIn(0f, 1f)
                val bucket = min(levels - 1, (clamped * levels).toInt())
                lerp(from, to, (bucket + 1).toFloat() / levels)
            }
        }
    }

    /** Cell color when a day has no data. */
    @Composable
    public fun emptyCellColor(): Color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)

    /**
     * Builds [HeatmapStrings]. Defaults are resolved from the current
     * locale's resources; override any parameter to replace a string.
     */
    @Composable
    @ReadOnlyComposable
    public fun strings(
        legendLess: String = orreryString(OrreryString.HeatmapLegendLess),
        legendMore: String = orreryString(OrreryString.HeatmapLegendMore),
        dayDescription: String = orreryString(OrreryString.HeatmapDayDescription),
        dayDescriptionEmpty: String = orreryString(OrreryString.HeatmapDayDescriptionEmpty),
        summaryActiveDays: String = orreryString(OrreryString.HeatmapSummaryActiveDays),
        summaryStreak: String = orreryString(OrreryString.HeatmapSummaryStreak),
    ): HeatmapStrings =
        HeatmapStrings(
            legendLess = legendLess,
            legendMore = legendMore,
            dayDescription = dayDescription,
            dayDescriptionEmpty = dayDescriptionEmpty,
            summaryActiveDays = summaryActiveDays,
            summaryStreak = summaryStreak,
        )

    /** Default summary line: "N active days · M-day streak". */
    @Composable
    public fun Summary(
        summary: HeatmapSummary,
        strings: HeatmapStrings = strings(),
    ) {
        Text(
            text =
                strings.summaryActiveDays.formatTemplate(summary.activeDays) +
                    " · " +
                    strings.summaryStreak.formatTemplate(summary.currentStreak),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
