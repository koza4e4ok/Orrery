package dev.koza4e4ok.material.calendar.compose

import androidx.compose.ui.graphics.drawscope.DrawScope
import dev.koza4e4ok.material.calendar.core.CalendarDay

/** Draw layer for a [DayDecorator]: behind or over the day content. */
public enum class DecoratorLayer { Behind, Over }

/**
 * Custom drawing stacked into [DefaultDay]'s cell. Implementations draw
 * with plain DrawScope commands — no extra composables per cell.
 * [layer] picks whether drawing happens behind the day number (the
 * default) or over it (e.g. strikethrough). Within a layer, decorators
 * draw in list order.
 */
public fun interface DayDecorator {
    public val layer: DecoratorLayer
        get() = DecoratorLayer.Behind

    public fun DrawScope.draw(day: CalendarDay)
}
