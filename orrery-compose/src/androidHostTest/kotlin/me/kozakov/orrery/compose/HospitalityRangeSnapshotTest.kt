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
class HospitalityRangeSnapshotTest {
    @get:Rule val rule = createComposeRule()

    /**
     * July 2026 with a stay from [start] (check-in PM) to [end] (check-out AM)
     * drawn with [endpointStyle]; [turnoverDates] share a check-out and a check-in.
     */
    private fun content(
        endpointStyle: RangeEndpointStyle,
        start: LocalDate = LocalDate(2026, 7, 8),
        end: LocalDate = LocalDate(2026, 7, 12),
        turnoverDates: Set<LocalDate> = emptySet(),
    ) {
        rule.setContent {
            MaterialTheme(
                colorScheme = if (isSystemInDarkTheme()) darkColorScheme() else lightColorScheme(),
            ) {
                Surface {
                    val selection =
                        rememberCalendarSelectionState(
                            mode = SelectionMode.Range(),
                            initialSelection = Selection(rangeStart = start, rangeEnd = end),
                        )
                    val style = CalendarDefaults.hospitalityStyle(rangeEndpointStyle = endpointStyle)
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
                                style = style,
                                turnoverDates = turnoverDates,
                                selectionState = selection,
                                today = LocalDate(2026, 7, 27),
                                animateSelection = false,
                            )
                        },
                    )
                }
            }
        }
    }

    private fun turnover(endpointStyle: RangeEndpointStyle) =
        content(
            endpointStyle = endpointStyle,
            start = LocalDate(2026, 7, 14),
            end = LocalDate(2026, 7, 18),
            // A previous stay checks out on the 14th and the next checks in on the 18th.
            turnoverDates = setOf(LocalDate(2026, 7, 14), LocalDate(2026, 7, 18)),
        )

    @Test
    fun splitHorizontalLight() {
        content(RangeEndpointStyle.SplitHorizontal)
        rule.onRoot().captureRoboImage()
    }

    @Test
    @Config(qualifiers = "+night")
    fun splitHorizontalDark() {
        content(RangeEndpointStyle.SplitHorizontal)
        rule.onRoot().captureRoboImage()
    }

    @Test
    fun splitDiagonalLight() {
        content(RangeEndpointStyle.SplitDiagonal)
        rule.onRoot().captureRoboImage()
    }

    @Test
    @Config(qualifiers = "+night")
    fun splitDiagonalDark() {
        content(RangeEndpointStyle.SplitDiagonal)
        rule.onRoot().captureRoboImage()
    }

    @Test
    fun turnoverHorizontalLight() {
        turnover(RangeEndpointStyle.SplitHorizontal)
        rule.onRoot().captureRoboImage()
    }

    @Test
    @Config(qualifiers = "+night")
    fun turnoverHorizontalDark() {
        turnover(RangeEndpointStyle.SplitHorizontal)
        rule.onRoot().captureRoboImage()
    }

    @Test
    fun turnoverDiagonalLight() {
        turnover(RangeEndpointStyle.SplitDiagonal)
        rule.onRoot().captureRoboImage()
    }

    @Test
    @Config(qualifiers = "+night")
    fun turnoverDiagonalDark() {
        turnover(RangeEndpointStyle.SplitDiagonal)
        rule.onRoot().captureRoboImage()
    }
}
