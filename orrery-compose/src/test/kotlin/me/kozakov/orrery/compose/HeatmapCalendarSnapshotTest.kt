package me.kozakov.orrery.compose

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.github.takahirom.roborazzi.captureRoboImage
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = RobolectricDeviceQualifiers.Pixel5)
class HeatmapCalendarSnapshotTest {
    @get:Rule val rule = createComposeRule()

    private fun intensity(date: LocalDate): Float? = if (date.day % 5 == 0) null else (date.day % 5) / 4f

    private fun content(
        showMonthLabels: Boolean = true,
        showWeekdayLabels: Boolean = true,
        showLegend: Boolean = true,
        showSummary: Boolean = true,
    ) {
        rule.setContent {
            MaterialTheme(
                colorScheme = if (isSystemInDarkTheme()) darkColorScheme() else lightColorScheme(),
            ) {
                Surface {
                    HeatmapCalendar(
                        intensity = ::intensity,
                        state =
                            rememberHeatmapCalendarState(
                                startDate = LocalDate(2025, 7, 16),
                                endDate = LocalDate(2026, 7, 15),
                                firstDayOfWeek = DayOfWeek.MONDAY,
                            ),
                        onDayClick = {},
                        showMonthLabels = showMonthLabels,
                        showWeekdayLabels = showWeekdayLabels,
                        showLegend = showLegend,
                        summary = if (showSummary) ({ HeatmapDefaults.Summary(it) }) else null,
                    )
                }
            }
        }
    }

    @Test
    fun graphLight() {
        content()
        rule.onRoot().captureRoboImage()
    }

    @Test
    @Config(qualifiers = "+night")
    fun graphDark() {
        content()
        rule.onRoot().captureRoboImage()
    }

    @Test
    fun graphNoChrome() {
        content(showMonthLabels = false, showWeekdayLabels = false, showLegend = false, showSummary = false)
        rule.onRoot().captureRoboImage()
    }
}
