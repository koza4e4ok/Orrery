package me.kozakov.orrery.compose

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class EventContentColorTest {
    private val darkText = Color(0xFF1D1B20)
    private val lightText = Color(0xFFE6E0E9)
    private val lightSurface = Color(0xFFFEF7FF)
    private val darkSurface = Color(0xFF141218)

    @Test
    fun paleColorOnALightSurfaceGetsDarkText() {
        val background = Color(0xFFFFF59D).copy(alpha = 0.18f).compositeOver(lightSurface)
        assertEquals(darkText, readableContentColor(background, onSurface = darkText, inverseOnSurface = lightText))
    }

    @Test
    fun brightColorOnTheDefaultDarkSurfaceKeepsLightText() {
        // At 28 % alpha even pure yellow leaves the default dark surface dark.
        val background = Color(0xFFFFFF00).copy(alpha = 0.28f).compositeOver(darkSurface)
        assertEquals(lightText, readableContentColor(background, onSurface = lightText, inverseOnSurface = darkText))
    }

    @Test
    fun brightColorOnAMidToneSurfaceGetsDarkText() {
        // A custom dark scheme with a mid-gray surface: the yellow tint lifts it past
        // the point where the scheme's light onSurface stays legible.
        val midToneSurface = Color(0xFF5A5A5A)
        val background = Color(0xFFFFFF00).copy(alpha = 0.28f).compositeOver(midToneSurface)
        assertEquals(darkText, readableContentColor(background, onSurface = lightText, inverseOnSurface = darkText))
    }

    @Test
    fun deepColorOnADarkSurfaceKeepsLightText() {
        val background = Color(0xFF1A237E).copy(alpha = 0.28f).compositeOver(darkSurface)
        assertEquals(lightText, readableContentColor(background, onSurface = lightText, inverseOnSurface = darkText))
    }

    @Test
    fun solidColorsPickTheHigherContrast() {
        assertEquals(darkText, readableContentColor(Color.White, onSurface = lightText, inverseOnSurface = darkText))
        assertEquals(lightText, readableContentColor(Color.Black, onSurface = darkText, inverseOnSurface = lightText))
    }

    @Test
    fun contrastRatioMatchesWcag() {
        assertEquals(21f, contrastRatio(1f, 0f), 0.001f)
        assertEquals(1f, contrastRatio(0.4f, 0.4f), 0.001f)
        assertTrue(contrastRatio(0f, 1f) == contrastRatio(1f, 0f))
    }
}
