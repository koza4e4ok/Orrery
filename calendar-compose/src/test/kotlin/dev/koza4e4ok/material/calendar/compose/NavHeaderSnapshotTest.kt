package dev.koza4e4ok.material.calendar.compose

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
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
class NavHeaderSnapshotTest {
    @get:Rule val rule = createComposeRule()

    private fun content(layoutDirection: LayoutDirection = LayoutDirection.Ltr) {
        rule.setContent {
            MaterialTheme(
                colorScheme = if (isSystemInDarkTheme()) darkColorScheme() else lightColorScheme(),
            ) {
                Surface {
                    CompositionLocalProvider(LocalLayoutDirection provides layoutDirection) {
                        HeaderAndCalendar()
                    }
                }
            }
        }
    }

    @Suppress("TestFunctionName")
    @Composable
    private fun HeaderAndCalendar() {
        val state =
            rememberCalendarState(
                startMonth = YearMonth(2026, 6),
                endMonth = YearMonth(2026, 8),
                firstVisibleMonth = YearMonth(2026, 7),
                firstDayOfWeek = DayOfWeek.MONDAY,
            )
        val selection =
            rememberCalendarSelectionState(
                mode = SelectionMode.Single(),
                initialSelection = Selection(single = LocalDate(2026, 7, 8)),
            )
        Column {
            CalendarNavHeader(state = state, onTitleClick = {})
            HorizontalCalendar(
                state = state,
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
    }

    @Test
    fun navHeaderLight() {
        content()
        rule.onRoot().captureRoboImage()
    }

    @Test
    @Config(qualifiers = "+night")
    fun navHeaderDark() {
        content()
        rule.onRoot().captureRoboImage()
    }

    @Test
    fun navHeaderRtl() {
        content(layoutDirection = LayoutDirection.Rtl)
        rule.onRoot().captureRoboImage()
    }
}
