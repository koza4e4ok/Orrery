package me.kozakov.orrery.compose

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.github.takahirom.roborazzi.captureRoboImage
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
class CollapsibleScaffoldSnapshotTest {
    @get:Rule val rule = createComposeRule()

    private fun content(anchor: CollapsibleCalendarAnchor) {
        rule.setContent {
            MaterialTheme(
                colorScheme = if (isSystemInDarkTheme()) darkColorScheme() else lightColorScheme(),
            ) {
                Surface {
                    CollapsibleCalendarScaffold(
                        calendarState =
                            rememberCalendarState(
                                startMonth = YearMonth(2026, 6),
                                endMonth = YearMonth(2026, 8),
                                firstVisibleMonth = YearMonth(2026, 7),
                                firstDayOfWeek = DayOfWeek.MONDAY,
                            ),
                        weekState =
                            rememberWeekCalendarState(
                                startDate = LocalDate(2026, 6, 1),
                                endDate = LocalDate(2026, 8, 31),
                                firstVisibleDate = LocalDate(2026, 7, 15),
                                firstDayOfWeek = DayOfWeek.MONDAY,
                            ),
                        state = rememberCollapsibleCalendarState(initialAnchor = anchor, enableTwoWeekStop = true),
                        dragHandle = { CalendarDefaults.CollapsibleDragHandle() },
                        dayContent = { day ->
                            DefaultDay(day = day, today = LocalDate(2026, 7, 15), animateSelection = false)
                        },
                    ) { Text("Agenda for the selected week") }
                }
            }
        }
    }

    @Test
    fun collapsibleMonth() {
        content(CollapsibleCalendarAnchor.Month)
        rule.onRoot().captureRoboImage()
    }

    @Test
    fun collapsibleTwoWeeks() {
        content(CollapsibleCalendarAnchor.TwoWeeks)
        rule.onRoot().captureRoboImage()
    }

    @Test
    fun collapsibleWeek() {
        content(CollapsibleCalendarAnchor.Week)
        rule.onRoot().captureRoboImage()
    }

    @Test
    @Config(qualifiers = "+night")
    fun collapsibleTwoWeeksDark() {
        content(CollapsibleCalendarAnchor.TwoWeeks)
        rule.onRoot().captureRoboImage()
    }
}
