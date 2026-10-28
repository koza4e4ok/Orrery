package me.kozakov.orrery.compose

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
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
class DefaultDayPolishSnapshotTest {
    @get:Rule val rule = createComposeRule()

    private val labels =
        mapOf(
            LocalDate(2026, 10, 1) to DayInfo(label = "Christmas Day (substitute)", isHoliday = true),
            LocalDate(2026, 10, 5) to DayInfo(label = "初五"),
            LocalDate(2026, 10, 14) to
                DayInfo(label = "Day of Defenders and Defendresses of Ukraine", isHoliday = true),
            LocalDate(2026, 10, 15) to DayInfo(label = "День захисників і захисниць України", isHoliday = true),
            LocalDate(2026, 10, 16) to DayInfo(label = "Tag der Deutschen Einheit", isHoliday = true),
            LocalDate(2026, 10, 21) to DayInfo(label = "Día de la Hispanidad", isHoliday = true),
            LocalDate(2026, 10, 22) to DayInfo(label = "秋分の日", isHoliday = true),
            LocalDate(2026, 10, 26) to DayInfo(label = "Rabi' al-Thani 15"),
        )

    private fun content(
        selection: Selection = Selection(),
        width: Dp? = null,
        decorated: Boolean = false,
    ) {
        rule.setContent {
            MaterialTheme(
                colorScheme = if (isSystemInDarkTheme()) darkColorScheme() else lightColorScheme(),
            ) {
                Surface(modifier = if (width != null) Modifier.width(width) else Modifier) {
                    PolishCalendar(selection, decorated)
                }
            }
        }
    }

    @Suppress("TestFunctionName")
    @Composable
    private fun PolishCalendar(
        initialSelection: Selection,
        decorated: Boolean,
    ) {
        val selectionState =
            rememberCalendarSelectionState(mode = SelectionMode.Range(), initialSelection = initialSelection)
        val decorators =
            if (decorated) {
                listOf(
                    DayDecorators.eventDots { date ->
                        when (date.day % 4) {
                            0 -> listOf(Color(0xFF1E88E5), Color(0xFFE53935), Color(0xFF43A047))
                            1 -> listOf(Color(0xFF1E88E5))
                            else -> emptyList()
                        }
                    },
                    DayDecorators.badge { date -> if (date.day % 3 == 0) date.day / 3 else 0 },
                )
            } else {
                emptyList()
            }
        HorizontalCalendar(
            state =
                rememberCalendarState(
                    startMonth = YearMonth(2026, 10),
                    endMonth = YearMonth(2026, 10),
                    firstVisibleMonth = YearMonth(2026, 10),
                    firstDayOfWeek = DayOfWeek.MONDAY,
                ),
            dayContent = { day ->
                DefaultDay(
                    day = day,
                    selectionState = selectionState,
                    today = LocalDate(2026, 10, 28),
                    info = labels[day.date],
                    animateSelection = false,
                    decorators = decorators,
                )
            },
        )
    }

    @Test
    fun longHolidayLabels() {
        content()
        rule.onRoot().captureRoboImage()
    }

    @Test
    fun longHolidayLabelsNarrow() {
        content(width = 280.dp)
        rule.onRoot().captureRoboImage()
    }

    @Test
    fun selectedRangeWithLabelsAndDecorators() {
        content(
            selection = Selection(rangeStart = LocalDate(2026, 10, 12), rangeEnd = LocalDate(2026, 10, 15)),
            decorated = true,
        )
        rule.onRoot().captureRoboImage()
    }

    @Test
    @Config(qualifiers = "+night")
    fun selectedRangeWithLabelsAndDecoratorsDark() {
        content(
            selection = Selection(rangeStart = LocalDate(2026, 10, 12), rangeEnd = LocalDate(2026, 10, 15)),
            decorated = true,
        )
        rule.onRoot().captureRoboImage()
    }
}
