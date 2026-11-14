package me.kozakov.orrery.compose

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.datetime.LocalDate
import me.kozakov.orrery.core.CalendarDay
import me.kozakov.orrery.core.DayPosition
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.GraphicsMode

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class OutDateDimmingTest {
    @get:Rule val rule = createComposeRule()

    private val red = Color(0xFFE53935)

    private fun colors(): CalendarDayColors {
        lateinit var colors: CalendarDayColors
        rule.setContent { MaterialTheme { colors = CalendarDefaults.dayColors() } }
        return rule.runOnIdle { colors }
    }

    @Test
    fun outDateLabelUsesTheOutDateColorOverItsOwnColor() {
        val colors = colors()
        val color =
            resolveDayLabelColor(
                colors,
                available = true,
                onRangeEndpoint = false,
                isSelected = false,
                isOutDate = true,
                colorArgb = 0xFFE53935,
                isHoliday = true,
            )
        assertEquals(colors.outDateContentColor, color)
    }

    @Test
    fun selectedOutDateLabelStillUsesTheSelectedColor() {
        val colors = colors()
        val color =
            resolveDayLabelColor(
                colors,
                available = true,
                onRangeEndpoint = false,
                isSelected = true,
                isOutDate = true,
                colorArgb = null,
                isHoliday = false,
            )
        assertEquals(colors.selectedContentColor, color)
    }

    private fun dotGreen(position: DayPosition): Float {
        rule.setContent {
            MaterialTheme {
                Box(Modifier.testTag("cell").size(48.dp).background(Color.White)) {
                    DefaultDay(
                        day = CalendarDay(LocalDate(2026, 8, 1), position),
                        today = LocalDate(2026, 7, 15),
                        decorators = listOf(DayDecorators.eventDots(maxDots = 1) { listOf(red) }),
                    )
                }
            }
        }
        val map = rule.onNodeWithTag("cell").captureToImage().toPixelMap()
        val (x, y) = with(rule.density) { 24.dp.roundToPx() to (48.dp - 6.dp - DefaultDayVerticalInset).roundToPx() }
        return map[x, y].green
    }

    @Test
    fun outDateDecoratorsAreDimmed() {
        // 0xE53935 at 38% over white has green ≈ 0.70; at full strength ≈ 0.22.
        val green = dotGreen(DayPosition.OutDate)
        assertTrue("dot green $green", green > 0.6f)
    }

    @Test
    fun inMonthDecoratorsKeepFullStrength() {
        val green = dotGreen(DayPosition.MonthDate)
        assertTrue("dot green $green", green < 0.35f)
    }
}
