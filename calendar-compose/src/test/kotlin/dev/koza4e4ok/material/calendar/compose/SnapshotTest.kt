package dev.koza4e4ok.material.calendar.compose

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.LayoutDirection
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.github.takahirom.roborazzi.captureRoboImage
import dev.koza4e4ok.material.calendar.core.Selection
import dev.koza4e4ok.material.calendar.core.SelectionMode
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.YearMonth
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = RobolectricDeviceQualifiers.Pixel5)
class SnapshotTest {
    @get:Rule val rule = createComposeRule()

    private fun content(layoutDirection: LayoutDirection = LayoutDirection.Ltr) {
        rule.setContent {
            MaterialTheme(
                colorScheme = if (isSystemInDarkTheme()) darkColorScheme() else lightColorScheme(),
            ) {
                Surface {
                    CompositionLocalProvider(LocalLayoutDirection provides layoutDirection) {
                        Calendar()
                    }
                }
            }
        }
    }

    @Suppress("TestFunctionName")
    @androidx.compose.runtime.Composable
    private fun Calendar() {
        val selection =
            rememberCalendarSelectionState(
                mode = SelectionMode.Range(),
                initialSelection =
                    Selection(
                        rangeStart = LocalDate(2026, 7, 8),
                        rangeEnd = LocalDate(2026, 7, 12),
                    ),
            )
        HorizontalCalendar(
            state =
                rememberCalendarState(
                    startMonth = YearMonth(2026, 7),
                    endMonth = YearMonth(2026, 7),
                    firstVisibleMonth = YearMonth(2026, 7),
                    firstDayOfWeek = DayOfWeek.MONDAY,
                ),
            showWeekNumbers = true,
            dayContent = { day ->
                DefaultDay(
                    day = day,
                    selectionState = selection,
                    today = LocalDate(2026, 7, 15),
                    animateSelection = false,
                )
            },
        )
    }

    @Test
    fun monthLight() {
        content()
        rule.onRoot().captureRoboImage()
    }

    @Test
    @Config(qualifiers = "+night")
    fun monthDark() {
        content()
        rule.onRoot().captureRoboImage()
    }

    @Test
    fun monthRtl() {
        // Robolectric's default test manifest lacks supportsRtl, so the +ldrtl
        // qualifier is inert; drive the composition local directly instead.
        content(layoutDirection = LayoutDirection.Rtl)
        rule.onRoot().captureRoboImage()
    }
}
