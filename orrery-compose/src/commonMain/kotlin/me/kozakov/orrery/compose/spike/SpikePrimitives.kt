package me.kozakov.orrery.compose.spike

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import me.kozakov.orrery.spike.markers.SpikeExperimentalApi
import me.kozakov.orrery.spike.markers.SpikeInternalApi

// Spike (#89 task 1, item b): shapes of the planned shared primitives and widget API,
// declared in a cmp-library module (android + desktop JVM dumps, merged klib dump).

/** Internal drawing primitive (like drawSelectionPill). */
@SpikeInternalApi
public fun DrawScope.spikeDrawPill(color: Color) {
    drawRect(color = color)
}

/** Internal model class (like MonthGridModel). */
@SpikeInternalApi
public class SpikeGridModel(
    /** Number of rows. */
    public val rows: Int,
)

/** Experimental composable (like CalendarSnapshot). */
@SpikeExperimentalApi
@Composable
public fun SpikeSnapshot(modifier: Modifier = Modifier) {
    Box(modifier = modifier)
}
