package dev.koza4e4ok.material.calendar.compose

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.github.takahirom.roborazzi.captureRoboImage
import dev.koza4e4ok.material.calendar.core.DisabledDates
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
class StylesSnapshotTest {
    @get:Rule val rule = createComposeRule()

    private fun setThemedContent(
        layoutDirection: LayoutDirection = LayoutDirection.Ltr,
        content: @Composable () -> Unit,
    ) {
        rule.setContent {
            MaterialTheme(
                colorScheme = if (isSystemInDarkTheme()) darkColorScheme() else lightColorScheme(),
            ) {
                Surface {
                    CompositionLocalProvider(LocalLayoutDirection provides layoutDirection) {
                        content()
                    }
                }
            }
        }
    }

    @Composable
    private fun july(): CalendarState =
        rememberCalendarState(
            startMonth = YearMonth(2026, 7),
            endMonth = YearMonth(2026, 7),
            firstVisibleMonth = YearMonth(2026, 7),
            firstDayOfWeek = DayOfWeek.MONDAY,
        )

    @Suppress("TestFunctionName")
    @Composable
    private fun UnavailableCalendar() {
        val selection =
            rememberCalendarSelectionState(
                mode = SelectionMode.Range(),
                initialSelection =
                    Selection(
                        rangeStart = LocalDate(2026, 7, 6),
                        rangeEnd = LocalDate(2026, 7, 10),
                    ),
                disabled =
                    DisabledDates {
                        daysOfWeek(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY)
                        range(LocalDate(2026, 7, 20)..LocalDate(2026, 7, 24))
                    },
            )
        HorizontalCalendar(
            state = july(),
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

    @Suppress("TestFunctionName")
    @Composable
    private fun StyledCalendar(shapes: CalendarDayShapes) {
        val selection =
            rememberCalendarSelectionState(
                mode = SelectionMode.Single(),
                initialSelection = Selection(single = LocalDate(2026, 7, 8)),
            )
        HorizontalCalendar(
            state = july(),
            dayContent = { day ->
                DefaultDay(
                    day = day,
                    selectionState = selection,
                    shapes = shapes,
                    today = LocalDate(2026, 7, 15),
                    animateSelection = false,
                )
            },
        )
    }

    @Test
    fun unavailableLight() {
        setThemedContent { UnavailableCalendar() }
        rule.onRoot().captureRoboImage()
    }

    @Test
    @Config(qualifiers = "+night")
    fun unavailableDark() {
        setThemedContent { UnavailableCalendar() }
        rule.onRoot().captureRoboImage()
    }

    @Test
    fun unavailableRtl() {
        setThemedContent(layoutDirection = LayoutDirection.Rtl) { UnavailableCalendar() }
        rule.onRoot().captureRoboImage()
    }

    @Test
    fun roundedShapes() {
        setThemedContent {
            StyledCalendar(
                CalendarDefaults.dayShapes(
                    dayShape = RoundedCornerShape(12.dp),
                    selectedShape = RoundedCornerShape(8.dp),
                ),
            )
        }
        rule.onRoot().captureRoboImage()
    }

    @Test
    fun todayFilledCircle() {
        setThemedContent {
            StyledCalendar(CalendarDefaults.dayShapes(todayIndicator = TodayIndicator.FilledCircle))
        }
        rule.onRoot().captureRoboImage()
    }

    @Test
    fun todayUnderline() {
        setThemedContent {
            StyledCalendar(CalendarDefaults.dayShapes(todayIndicator = TodayIndicator.Underline))
        }
        rule.onRoot().captureRoboImage()
    }
}
