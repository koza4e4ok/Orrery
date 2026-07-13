package dev.koza4e4ok.orrery.compose

import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.koza4e4ok.orrery.core.CalendarDay
import dev.koza4e4ok.orrery.core.DayPosition
import kotlinx.datetime.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DayDecoratorTest {
    @get:Rule
    val rule = createComposeRule()

    @Test
    fun defaultLayerIsBehind() {
        val decorator = DayDecorator { }
        assertEquals(DecoratorLayer.Behind, decorator.layer)
    }

    // Draw-pass invocation and Behind/Over ordering are verified visually by
    // DecoratorsSnapshotTest goldens; Robolectric runs no draw pass in plain
    // unit tests, so this only pins that both layers compose together.
    @Test
    fun behindAndOverDecoratorsComposeTogether() {
        rule.setContent {
            DefaultDay(
                day = CalendarDay(LocalDate(2026, 7, 6), DayPosition.MonthDate),
                decorators =
                    listOf(
                        DayDecorator { },
                        object : DayDecorator {
                            override val layer: DecoratorLayer
                                get() = DecoratorLayer.Over

                            override fun DrawScope.draw(day: CalendarDay) {
                                drawRect(color = androidx.compose.ui.graphics.Color.Red)
                            }
                        },
                    ),
            )
        }
        rule.onNodeWithText("6").assertExists()
    }
}
