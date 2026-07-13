package dev.koza4e4ok.orrery.compose

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate

/**
 * Animates per-day values toward their targets and returns a lookup for
 * data-driven decorators (progressRing, progressBar, heatmap). New dates
 * animate from 0; changed dates ease to the new target; removed dates
 * disappear immediately. The lookup reads snapshot state, so decorators
 * redraw automatically while animating.
 */
@Composable
public fun rememberAnimatedDayValues(
    values: Map<LocalDate, Float>,
    animationSpec: AnimationSpec<Float> = tween(durationMillis = 300),
): (LocalDate) -> Float? {
    val animatables = remember { mutableStateMapOf<LocalDate, Animatable<Float, AnimationVector1D>>() }
    LaunchedEffect(values, animationSpec) {
        (animatables.keys - values.keys).forEach { animatables.remove(it) }
        values.forEach { (date, target) ->
            val animatable = animatables.getOrPut(date) { Animatable(0f) }
            if (animatable.targetValue != target) {
                launch { animatable.animateTo(target, animationSpec) }
            }
        }
    }
    return remember(animatables) { { date -> animatables[date]?.value } }
}
