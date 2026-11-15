@file:OptIn(ExperimentalOrreryApi::class)

package me.kozakov.orrery.widget

import android.content.Context
import android.content.res.Configuration
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.test.ext.junit.runners.AndroidJUnit4
import me.kozakov.orrery.ExperimentalOrreryApi
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RuntimeEnvironment
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Day numbers inside a selected range must stay legible on the range band, whatever the colours. */
@RunWith(AndroidJUnit4::class)
class CalendarSnapshotContrastTest {
    private val priorityColors =
        listOf(0xFFC62828, 0xFF2E7D32, 0xFFEF6C00, 0xFF6A1B9A, 0xFF1565C0, 0xFF757575, 0xFFFFEB3B).map(::Color)

    @Test
    fun rangeDayNumbersReachTheContrastFloor() {
        for ((name, colors) in schemes()) {
            val band = colors.rangeBand.compositeOver(colors.container)
            for (color in listOf(colors.day, colors.holiday) + priorityColors) {
                val ratio = contrastRatio(colors.rangeDayColor(color), band)
                println("Range band contrast ($name): $color -> $ratio")
                assertTrue(ratio >= MIN_RANGE_TEXT_CONTRAST, "$name: $color on the band is $ratio:1")
            }
        }
    }

    @Test
    fun legibleColoursAreKept() {
        val colors = CalendarSnapshotDefaults.colors(lightColorScheme())
        assertEquals(colors.day, colors.rangeDayColor(colors.day), "onSurface on primaryContainer already passes")
    }

    @Test
    fun theGoldenRedStartsBelowThreeToOneOnTheDarkBand() {
        // The red priority day of the goldens on the dark band: a case the floor has to fix.
        val dark = CalendarSnapshotDefaults.colors(darkColorScheme())
        val band = dark.rangeBand.compositeOver(dark.container)
        val ratio = contrastRatio(Color(0xFFC62828), band)
        assertTrue(ratio < 3f, "red on the dark band was $ratio:1")
    }

    private fun schemes(): Map<String, CalendarSnapshotColors> {
        val app: Context = RuntimeEnvironment.getApplication()
        return mapOf(
            "light" to CalendarSnapshotDefaults.colors(lightColorScheme()),
            "dark" to CalendarSnapshotDefaults.colors(darkColorScheme()),
            // Dynamic (wallpaper) colours on API 31+, as in dynamicColorsLight and dynamicColorsDark.
            "dynamicLight" to CalendarSnapshotDefaults.colors(app.withNightMode(Configuration.UI_MODE_NIGHT_NO)),
            "dynamicDark" to CalendarSnapshotDefaults.colors(app.withNightMode(Configuration.UI_MODE_NIGHT_YES)),
        )
    }

    private fun Context.withNightMode(nightMode: Int): Context {
        val configuration = Configuration(resources.configuration)
        configuration.uiMode = (configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or nightMode
        return createConfigurationContext(configuration)
    }
}
