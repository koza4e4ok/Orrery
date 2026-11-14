package me.kozakov.orrery.widget

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import me.kozakov.orrery.ExperimentalOrreryApi

/** Default colours and style of calendar snapshots. */
@ExperimentalOrreryApi
public object CalendarSnapshotDefaults {
    /** Colours from the current [MaterialTheme.colorScheme]; see [colors] with a [ColorScheme] for the mapping. */
    @Composable
    @ReadOnlyComposable
    public fun colors(): CalendarSnapshotColors = colors(MaterialTheme.colorScheme)

    /**
     * Colours from [colorScheme], usable outside composition (for example in a widget
     * update). The mapping matches `CalendarDefaults.MiniMonth`: container `surface`,
     * title and day `onSurface`, weekday `onSurfaceVariant`, outside days `outline`,
     * today and selection `primary` with `onPrimary` content, range band
     * `primaryContainer`, holiday `tertiary` and event dots `secondary`.
     */
    public fun colors(colorScheme: ColorScheme): CalendarSnapshotColors =
        CalendarSnapshotColors(
            container = colorScheme.surface,
            title = colorScheme.onSurface,
            weekday = colorScheme.onSurfaceVariant,
            day = colorScheme.onSurface,
            outsideDay = colorScheme.outline,
            today = colorScheme.primary,
            todayContent = colorScheme.onPrimary,
            selection = colorScheme.primary,
            selectionContent = colorScheme.onPrimary,
            rangeBand = colorScheme.primaryContainer,
            holiday = colorScheme.tertiary,
            eventDot = colorScheme.secondary,
        )

    /** The default [CalendarSnapshotStyle]. */
    public fun style(): CalendarSnapshotStyle = CalendarSnapshotStyle()
}
