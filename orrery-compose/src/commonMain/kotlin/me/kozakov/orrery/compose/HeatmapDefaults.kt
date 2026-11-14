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

/** 1-based bucket of a clamped 0..1 intensity among [levels]: 0f -> 1, 1f -> [levels]. */
internal fun heatLevel(
    value: Float,
    levels: Int,
): Int = min(levels, (value.coerceIn(0f, 1f) * levels).toInt() + 1)

/** Share of the way from `from` to `to` at level 1, so the lowest level stands apart from an empty cell. */
private const val LOWEST_LEVEL_FRACTION: Float = 0.35f

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
     * Number of intensity levels the heatmaps announce to accessibility
     * services and show in the legend. Fixed regardless of a custom color
     * scale; [colorScale] uses it as its default `levels`.
     */
    @Suppress("ktlint:standard:property-naming") // Public API name, in the style of Compose's *Defaults objects.
    public const val Levels: Int = 5

    /**
     * The 1-based level of a 0..1 intensity [value], from 1 to [Levels]:
     * `0f` is level 1 and `1f` is level [Levels]. Values outside 0..1 are
     * clamped. `Float.NaN` counts as 0 (level 1, not "no data"; pass `null`
     * intensity for that), `+Infinity` as 1 and `-Infinity` as 0. This is
     * the level the cell description announces, and the default [colorScale]
     * picks its color from the same level, so the two always agree.
     */
    public fun levelOf(value: Float): Int = heatLevel(value, Levels)

    /**
     * Quantized color scale: 0..1 split into [levels] buckets (see [levelOf]
     * for the default five), lerped from [from] toward [to]. Level 1 starts
     * 35% of the way to [to], so it stays distinguishable from
     * [emptyCellColor], and the top level is [to] itself. Values are clamped.
     *
     * Pass another [to], for example `tertiary` or `error`, for data where
     * more is worse or for a second heatmap on the same screen.
     *
     * @param levels number of buckets, at least 1.
     * @param from color the ramp starts from; level 1 is 35% of the way from it.
     * @param to color of the top level.
     */
    @Composable
    public fun colorScale(
        levels: Int = Levels,
        from: Color = MaterialTheme.colorScheme.surfaceVariant,
        to: Color = MaterialTheme.colorScheme.primary,
    ): (Float) -> Color {
        require(levels >= 1) { "levels must be at least 1, was $levels" }
        return remember(levels, from, to) {
            { value ->
                val bucket = heatLevel(value, levels) - 1
                val fraction =
                    if (levels == 1) {
                        1f
                    } else {
                        LOWEST_LEVEL_FRACTION + (1f - LOWEST_LEVEL_FRACTION) * bucket / (levels - 1)
                    }
                lerp(from, to, fraction)
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
