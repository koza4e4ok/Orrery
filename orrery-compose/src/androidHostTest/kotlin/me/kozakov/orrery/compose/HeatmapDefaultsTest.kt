package me.kozakov.orrery.compose

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import me.kozakov.orrery.core.HeatmapSummary
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class HeatmapDefaultsTest {
    @get:Rule val rule = createComposeRule()

    @Test
    fun colorScaleQuantizesIntoLevels() {
        lateinit var scale: (Float) -> Color
        rule.setContent { MaterialTheme { scale = HeatmapDefaults.colorScale(levels = 5) } }
        rule.runOnIdle {
            assertEquals(scale(0.0f), scale(0.19f))
            assertNotEquals(scale(0.19f), scale(0.21f))
            assertEquals(scale(1.0f), scale(0.81f))
            assertEquals(scale(1.0f), scale(2f))
        }
    }

    @Test
    fun levelOfBuckets() {
        assertEquals(5, HeatmapDefaults.Levels)
        assertEquals(1, HeatmapDefaults.levelOf(0f))
        assertEquals(1, HeatmapDefaults.levelOf(0.19f))
        assertEquals(2, HeatmapDefaults.levelOf(0.2f))
        assertEquals(3, HeatmapDefaults.levelOf(0.45f))
        assertEquals(5, HeatmapDefaults.levelOf(1f))
        assertEquals(5, HeatmapDefaults.levelOf(9f))
        assertEquals(1, HeatmapDefaults.levelOf(-1f))
    }

    @Test
    fun colorBucketsMatchLevelOf() {
        lateinit var scale: (Float) -> Color
        rule.setContent { MaterialTheme { scale = HeatmapDefaults.colorScale() } }
        rule.runOnIdle {
            // Every 0.001 step plus the exact bucket edges and the floats just below them.
            val edges = (1 until HeatmapDefaults.Levels).map { it.toFloat() / HeatmapDefaults.Levels }
            val values = ((-10..1010).map { it / 1000f } + edges + edges.map { Math.nextDown(it) }).sorted()
            val colorOfLevel = mutableMapOf<Int, Color>()
            values.forEach { value ->
                val level = HeatmapDefaults.levelOf(value)
                val color = colorOfLevel.getOrPut(level) { scale(value) }
                assertEquals("color of $value (level $level)", color, scale(value))
            }
            assertEquals((1..HeatmapDefaults.Levels).toList(), colorOfLevel.keys.sorted())
            assertEquals(HeatmapDefaults.Levels, colorOfLevel.values.toSet().size)
        }
    }

    @Test
    fun colorScaleTakesCustomColors() {
        lateinit var scale: (Float) -> Color
        rule.setContent { MaterialTheme { scale = HeatmapDefaults.colorScale(from = Color.White, to = Color.Red) } }
        rule.runOnIdle {
            assertEquals(Color.Red, scale(1f))
            assertEquals(lerp(Color.White, Color.Red, 0.35f), scale(0f))
            assertNotEquals(Color.White, scale(0f))
        }
    }

    @Test
    fun colorScaleTargetsPrimaryByDefault() {
        lateinit var scale: (Float) -> Color
        lateinit var custom: (Float) -> Color
        lateinit var scheme: ColorScheme
        rule.setContent {
            MaterialTheme(colorScheme = lightColorScheme()) {
                scheme = MaterialTheme.colorScheme
                scale = HeatmapDefaults.colorScale()
                custom = HeatmapDefaults.colorScale(to = MaterialTheme.colorScheme.tertiary)
            }
        }
        rule.runOnIdle {
            assertEquals(scheme.primary, scale(1f))
            assertEquals(scheme.tertiary, custom(1f))
            assertNotEquals(scale(0f), custom(0f))
        }
    }

    @Test
    fun lowestLevelStandsApartFromEmptyCell() {
        val schemes = listOf("light" to lightColorScheme(), "dark" to darkColorScheme())
        val captured = mutableMapOf<String, Pair<(Float) -> Color, Color>>()
        rule.setContent {
            schemes.forEach { (name, scheme) ->
                MaterialTheme(colorScheme = scheme) {
                    captured[name] = HeatmapDefaults.colorScale() to HeatmapDefaults.emptyCellColor()
                }
            }
        }
        rule.runOnIdle {
            schemes.forEach { (name, scheme) ->
                val (scale, empty) = captured.getValue(name)
                val levels = (0 until HeatmapDefaults.Levels).map { scale(it.toFloat() / HeatmapDefaults.Levels) }
                val lowest = contrast(levels.first(), empty.compositeOver(scheme.surface))
                val adjacent = levels.zipWithNext(::contrast)
                println("Heatmap contrast ($name): level 1 vs empty $lowest, adjacent levels $adjacent")
                assertTrue(
                    "$name: level 1 vs empty cell is $lowest:1, below $MIN_LOWEST_LEVEL_CONTRAST:1",
                    lowest >= MIN_LOWEST_LEVEL_CONTRAST,
                )
            }
        }
    }

    private fun contrast(
        a: Color,
        b: Color,
    ): Float {
        val (light, dark) = listOf(a.luminance(), b.luminance()).sortedDescending()
        return (light + 0.05f) / (dark + 0.05f)
    }

    @Test
    fun summaryRendersCounters() {
        rule.setContent {
            MaterialTheme {
                HeatmapDefaults.Summary(
                    HeatmapSummary(activeDays = 42, currentStreak = 7, longestStreak = 9),
                )
            }
        }
        rule.onNodeWithText("42 active days · 7-day streak").assertExists()
    }

    private companion object {
        // Baseline Material 3 schemes, level 1 vs the empty cell over surface: 1.49 (light) and 2.29 (dark)
        // with the old 20% start, about 1.86 and 3.02 with the 35% start. 1.8 fails the old light ramp.
        const val MIN_LOWEST_LEVEL_CONTRAST = 1.8f
    }
}
