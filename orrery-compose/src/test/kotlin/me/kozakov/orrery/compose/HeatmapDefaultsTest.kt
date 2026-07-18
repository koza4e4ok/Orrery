package me.kozakov.orrery.compose

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import me.kozakov.orrery.core.HeatmapSummary
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
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
    fun heatLevelBuckets() {
        assertEquals(1, heatLevel(0f))
        assertEquals(3, heatLevel(0.45f))
        assertEquals(5, heatLevel(1f))
        assertEquals(5, heatLevel(9f))
        assertEquals(1, heatLevel(-1f))
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
}
