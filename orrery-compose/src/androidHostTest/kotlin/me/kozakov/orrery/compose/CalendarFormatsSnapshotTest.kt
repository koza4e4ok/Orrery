package me.kozakov.orrery.compose

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.github.takahirom.roborazzi.captureRoboImage
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.YearMonth
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
class CalendarFormatsSnapshotTest {
    @get:Rule val rule = createComposeRule()

    private val workWeek =
        setOf(
            DayOfWeek.MONDAY,
            DayOfWeek.TUESDAY,
            DayOfWeek.WEDNESDAY,
            DayOfWeek.THURSDAY,
            DayOfWeek.FRIDAY,
        )

    private fun capture(body: @Composable () -> Unit) {
        rule.setContent {
            MaterialTheme(
                colorScheme = if (isSystemInDarkTheme()) darkColorScheme() else lightColorScheme(),
            ) {
                Surface {
                    body()
                }
            }
        }
        rule.onRoot().captureRoboImage()
    }

    @Suppress("TestFunctionName")
    @Composable
    private fun TwoWeekSample() {
        val selection =
            rememberCalendarSelectionState(
                mode = SelectionMode.Range(),
                initialSelection =
                    Selection(
                        rangeStart = LocalDate(2026, 7, 8),
                        rangeEnd = LocalDate(2026, 7, 15),
                    ),
            )
        WeekCalendar(
            state =
                rememberWeekCalendarState(
                    startDate = LocalDate(2026, 7, 6),
                    endDate = LocalDate(2026, 7, 19),
                    firstVisibleDate = LocalDate(2026, 7, 6),
                    firstDayOfWeek = DayOfWeek.MONDAY,
                    visibleWeeksCount = 2,
                ),
            visibleWeeksCount = 2,
            dayContent = { day ->
                DefaultDay(
                    day = day,
                    selectionState = selection,
                    today = LocalDate(2026, 7, 10),
                    animateSelection = false,
                )
            },
        )
    }

    @Suppress("TestFunctionName")
    @Composable
    private fun WorkWeekMonthSample() {
        val selection =
            rememberCalendarSelectionState(
                mode = SelectionMode.Range(),
                initialSelection =
                    Selection(
                        rangeStart = LocalDate(2026, 7, 13),
                        rangeEnd = LocalDate(2026, 7, 17),
                    ),
            )
        HorizontalCalendar(
            state =
                rememberCalendarState(
                    startMonth = YearMonth(2026, 7),
                    endMonth = YearMonth(2026, 7),
                    firstVisibleMonth = YearMonth(2026, 7),
                    firstDayOfWeek = DayOfWeek.MONDAY,
                    visibleDaysOfWeek = workWeek,
                ),
            visibleDaysOfWeek = workWeek,
            dayContent = { day ->
                DefaultDay(
                    day = day,
                    selectionState = selection,
                    today = LocalDate(2026, 7, 8),
                    animateSelection = false,
                )
            },
        )
    }

    @Suppress("TestFunctionName")
    @Composable
    private fun RowHeightSample(
        rowHeight: CalendarRowHeight,
        modifier: Modifier = Modifier,
    ) {
        val selection =
            rememberCalendarSelectionState(
                mode = SelectionMode.Single(),
                initialSelection = Selection(single = LocalDate(2026, 7, 15)),
            )
        HorizontalCalendar(
            state =
                rememberCalendarState(
                    startMonth = YearMonth(2026, 7),
                    endMonth = YearMonth(2026, 7),
                    firstVisibleMonth = YearMonth(2026, 7),
                    firstDayOfWeek = DayOfWeek.MONDAY,
                ),
            modifier = modifier,
            rowHeight = rowHeight,
            dayContent = { day ->
                DefaultDay(
                    day = day,
                    style = CalendarDefaults.borderedCardStyle(),
                    selectionState = selection,
                    today = LocalDate(2026, 7, 8),
                    animateSelection = false,
                )
            },
        )
    }

    @Test
    fun twoWeekLight() = capture { TwoWeekSample() }

    @Test
    @Config(qualifiers = "+night")
    fun twoWeekDark() = capture { TwoWeekSample() }

    @Test
    fun workWeekMonthLight() = capture { WorkWeekMonthSample() }

    @Test
    @Config(qualifiers = "+night")
    fun workWeekMonthDark() = capture { WorkWeekMonthSample() }

    @Test
    fun fixedRowHeightLight() =
        capture {
            RowHeightSample(rowHeight = CalendarRowHeight.Fixed(64.dp))
        }

    @Test
    fun fillViewportLight() =
        capture {
            RowHeightSample(
                rowHeight = CalendarRowHeight.FillViewport(),
                modifier = Modifier.fillMaxWidth().height(420.dp),
            )
        }

    @Test
    @Config(qualifiers = "+night")
    fun fillViewportDark() =
        capture {
            RowHeightSample(
                rowHeight = CalendarRowHeight.FillViewport(),
                modifier = Modifier.fillMaxWidth().height(420.dp),
            )
        }
}
