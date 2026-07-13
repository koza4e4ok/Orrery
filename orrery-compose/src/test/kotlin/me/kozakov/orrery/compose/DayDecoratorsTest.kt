package me.kozakov.orrery.compose

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.datetime.LocalDate
import me.kozakov.orrery.core.CalendarDay
import me.kozakov.orrery.core.DayPosition
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DayDecoratorsTest {
    @get:Rule
    val rule = createComposeRule()

    @Test
    fun strikethroughDrawsOverEverythingElseBehind() {
        lateinit var layers: List<DecoratorLayer>
        rule.setContent {
            MaterialTheme {
                layers =
                    listOf(
                        DayDecorators.eventDots { emptyList() },
                        DayDecorators.progressRing { null },
                        DayDecorators.progressBar { null },
                        DayDecorators.underline { false },
                        DayDecorators.heatmap { null },
                        DayDecorators.strikethrough { false },
                    ).map { it.layer }
            }
        }
        assertEquals(
            listOf(
                DecoratorLayer.Behind,
                DecoratorLayer.Behind,
                DecoratorLayer.Behind,
                DecoratorLayer.Behind,
                DecoratorLayer.Behind,
                DecoratorLayer.Over,
            ),
            layers,
        )
    }

    @Test
    fun emptyDataDrawsWithoutCrashing() {
        rule.setContent {
            MaterialTheme {
                DefaultDay(
                    day = CalendarDay(LocalDate(2026, 7, 6), DayPosition.MonthDate),
                    decorators =
                        listOf(
                            DayDecorators.eventDots { emptyList() },
                            DayDecorators.progressRing { null },
                            DayDecorators.progressBar { null },
                            DayDecorators.strikethrough { false },
                            DayDecorators.underline { false },
                            DayDecorators.heatmap { null },
                        ),
                )
            }
        }
        rule.waitForIdle()
    }
}
