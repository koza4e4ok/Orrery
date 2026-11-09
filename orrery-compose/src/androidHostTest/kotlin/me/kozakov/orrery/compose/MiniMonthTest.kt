package me.kozakov.orrery.compose

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.datetime.LocalDate
import kotlinx.datetime.YearMonth
import me.kozakov.orrery.core.DayInfo
import me.kozakov.orrery.core.Selection
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.GraphicsMode

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class MiniMonthTest {
    @get:Rule
    val rule = createComposeRule()

    private val red = Color(0xFFFF0000)
    private val green = Color(0xFF00FF00)

    @Test
    fun dayInfoIsQueriedOnlyForDatesOfTheMonth() {
        val queried = mutableSetOf<LocalDate>()
        rule.setContent {
            MaterialTheme {
                CalendarDefaults.MiniMonth(
                    YearMonth(2026, 2),
                    today = LocalDate(2026, 2, 10),
                    selection = Selection(rangeStart = LocalDate(2026, 1, 28), rangeEnd = LocalDate(2026, 2, 3)),
                    dayInfo = { date ->
                        queried += date
                        DayInfo(label = null, isHoliday = date.day == 14)
                    },
                )
            }
        }
        rule.waitForIdle()
        assertEquals(28, queried.size)
        assertTrue(queried.all { it.year == 2026 && it.month.ordinal == 1 })
    }

    @Test
    fun isOneNodeDescribedByMonthWithTodayAndSelectedState() {
        rule.setContent {
            MaterialTheme {
                CalendarDefaults.MiniMonth(
                    YearMonth(2026, 7),
                    modifier = Modifier.testTag("mini"),
                    today = LocalDate(2026, 7, 15),
                    selection = Selection(single = LocalDate(2026, 7, 20)),
                    dayInfo = { null },
                )
            }
        }
        rule
            .onNodeWithTag("mini", useUnmergedTree = true)
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.ContentDescription, listOf("July 2026")))
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "Today"))
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Selected, true))
        // Day numbers are drawn, not composed as Text nodes.
        assertEquals(0, rule.onAllNodesWithText("15", useUnmergedTree = true).fetchSemanticsNodes().size)
        assertEquals(0, rule.onAllNodesWithText("31", useUnmergedTree = true).fetchSemanticsNodes().size)
    }

    @Test
    fun monthWithoutTodayOrSelectionHasNoState() {
        rule.setContent {
            MaterialTheme {
                CalendarDefaults.MiniMonth(
                    YearMonth(2026, 3),
                    modifier = Modifier.testTag("mini"),
                    today = LocalDate(2026, 7, 15),
                    selection = Selection(single = LocalDate(2026, 7, 20)),
                )
            }
        }
        rule
            .onNodeWithTag("mini", useUnmergedTree = true)
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.ContentDescription, listOf("March 2026")))
            .assert(SemanticsMatcher.keyNotDefined(SemanticsProperties.StateDescription))
            .assert(SemanticsMatcher.keyNotDefined(SemanticsProperties.Selected))
    }

    @Test
    fun drawsTodayCircleInTheCurrentMonth() {
        assertTrue(redPixels(YearMonth(2026, 7), today = LocalDate(2026, 7, 15)) > 0)
    }

    @Test
    fun otherMonthsDrawNothingInPrimary() {
        assertEquals(0, redPixels(YearMonth(2026, 3), today = LocalDate(2026, 7, 15)))
    }

    @Test
    fun drawsHolidayNumbersInPrimary() {
        // Today is in another month, so only the holiday number can be red.
        assertTrue(redPixels(YearMonth(2026, 3), today = LocalDate(2026, 7, 15), holiday = 20) > 0)
    }

    @Test
    fun drawsSelectionPillsForSelectedDates() {
        assertTrue(greenPixels(Selection(rangeStart = LocalDate(2026, 3, 18), rangeEnd = LocalDate(2026, 3, 25))) > 0)
    }

    @Test
    fun drawsNoPillsWithoutSelectedDatesInTheMonth() {
        assertEquals(0, greenPixels(Selection(single = LocalDate(2026, 4, 2))))
    }

    @Test
    fun largerFontScaleMakesTheMonthTaller() {
        rule.setContent {
            MaterialTheme {
                Column {
                    CalendarDefaults.MiniMonth(YearMonth(2026, 3), Modifier.testTag("small"))
                    CompositionLocalProvider(
                        LocalDensity provides Density(LocalDensity.current.density, fontScale = 2f),
                    ) {
                        CalendarDefaults.MiniMonth(YearMonth(2026, 3), Modifier.testTag("large"))
                    }
                }
            }
        }
        val small = heightOf("small")
        val large = heightOf("large")
        assertTrue("large ($large) should be taller than small ($small)", large > small * 3 / 2)
    }

    private fun heightOf(tag: String): Int {
        val node = rule.onNodeWithTag(tag, useUnmergedTree = true).fetchSemanticsNode()
        return node.size.height
    }

    private fun redPixels(
        month: YearMonth,
        today: LocalDate,
        holiday: Int = 0,
    ): Int {
        rule.setContent {
            MaterialTheme(colorScheme = lightColorScheme(primary = red)) {
                CalendarDefaults.MiniMonth(
                    month,
                    modifier = Modifier.width(220.dp).testTag("mini"),
                    today = today,
                    dayInfo = { date -> DayInfo(label = null, isHoliday = date.day == holiday) },
                )
            }
        }
        return count("mini") { it.red > 0.8f && it.green < 0.25f && it.blue < 0.25f }
    }

    private fun greenPixels(selection: Selection): Int {
        rule.setContent {
            MaterialTheme(colorScheme = lightColorScheme(primaryContainer = green)) {
                CalendarDefaults.MiniMonth(
                    YearMonth(2026, 3),
                    modifier = Modifier.width(220.dp).testTag("mini"),
                    today = LocalDate(2026, 7, 15),
                    selection = selection,
                )
            }
        }
        return count("mini") { it.green > 0.8f && it.red < 0.25f && it.blue < 0.25f }
    }

    private fun count(
        tag: String,
        predicate: (Color) -> Boolean,
    ): Int {
        val pixels = rule.onNodeWithTag(tag, useUnmergedTree = true).captureToImage().toPixelMap()
        var count = 0
        for (x in 0 until pixels.width) {
            for (y in 0 until pixels.height) {
                if (predicate(pixels[x, y])) count++
            }
        }
        return count
    }
}
