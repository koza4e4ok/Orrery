package me.kozakov.orrery.compose

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.github.takahirom.roborazzi.captureRoboImage
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.YearMonth
import me.kozakov.orrery.core.DayInfo
import me.kozakov.orrery.core.Selection
import me.kozakov.orrery.core.SelectionMode
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = RobolectricDeviceQualifiers.Pixel5)
class CalendarStyleSnapshotTest {
    @get:Rule val rule = createComposeRule()

    private val labels =
        mapOf(
            LocalDate(2026, 7, 4) to DayInfo(label = "Indep.", isHoliday = true),
            LocalDate(2026, 7, 15) to DayInfo(label = "初一"),
            LocalDate(2026, 7, 22) to DayInfo(label = "Event"),
        )

    private fun content(style: @Composable () -> CalendarStyle) {
        rule.setContent {
            MaterialTheme(
                colorScheme = if (isSystemInDarkTheme()) darkColorScheme() else lightColorScheme(),
            ) {
                Surface {
                    val calendarStyle = style()
                    val selection =
                        rememberCalendarSelectionState(
                            mode = SelectionMode.Range(),
                            initialSelection =
                                Selection(rangeStart = LocalDate(2026, 7, 8), rangeEnd = LocalDate(2026, 7, 14)),
                        )
                    HorizontalCalendar(
                        state =
                            rememberCalendarState(
                                startMonth = YearMonth(2026, 7),
                                endMonth = YearMonth(2026, 7),
                                firstVisibleMonth = YearMonth(2026, 7),
                                firstDayOfWeek = DayOfWeek.MONDAY,
                            ),
                        dayContent = { day ->
                            DefaultDay(
                                day = day,
                                style = calendarStyle,
                                selectionState = selection,
                                today = LocalDate(2026, 7, 20),
                                info = labels[day.date],
                                animateSelection = false,
                            )
                        },
                    )
                }
            }
        }
        rule.onRoot().captureRoboImage()
    }

    @Test
    fun compactLight() = content { CalendarDefaults.style(density = CalendarDensity.Compact) }

    @Test
    @Config(qualifiers = "+night")
    fun compactDark() = content { CalendarDefaults.style(density = CalendarDensity.Compact) }

    @Test
    fun comfortableLight() = content { CalendarDefaults.style(density = CalendarDensity.Comfortable) }

    @Test
    @Config(qualifiers = "+night")
    fun comfortableDark() = content { CalendarDefaults.style(density = CalendarDensity.Comfortable) }

    @Test
    fun expandedLight() = content { CalendarDefaults.style(density = CalendarDensity.Expanded) }

    @Test
    @Config(qualifiers = "+night")
    fun expandedDark() = content { CalendarDefaults.style(density = CalendarDensity.Expanded) }

    @Test
    fun roundedSquareLight() = content { CalendarDefaults.roundedSquareStyle() }

    @Test
    @Config(qualifiers = "+night")
    fun roundedSquareDark() = content { CalendarDefaults.roundedSquareStyle() }

    @Test
    fun segmentedRangeLight() = content { CalendarDefaults.segmentedRangeStyle() }

    @Test
    @Config(qualifiers = "+night")
    fun segmentedRangeDark() = content { CalendarDefaults.segmentedRangeStyle() }

    @Test
    fun borderedCardLight() = content { CalendarDefaults.borderedCardStyle() }

    @Test
    @Config(qualifiers = "+night")
    fun borderedCardDark() = content { CalendarDefaults.borderedCardStyle() }
}
