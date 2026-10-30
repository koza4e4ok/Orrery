package me.kozakov.orrery.compose

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.Alignment
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
import me.kozakov.orrery.core.CalendarEvent
import me.kozakov.orrery.core.DayPosition
import me.kozakov.orrery.core.EventSpan
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = RobolectricDeviceQualifiers.Pixel5)
class WeekEventBannersSnapshotTest {
    @get:Rule val rule = createComposeRule()

    private fun event(
        id: String,
        start: LocalDate,
        end: LocalDate,
        color: Long?,
    ) = CalendarEvent<Nothing>(id = id, title = id, span = EventSpan.AllDay(start, end), colorArgb = color)

    private val events =
        listOf(
            // Continues from June into the first row.
            event("Beach week", LocalDate(2026, 6, 26), LocalDate(2026, 7, 2), 0xFFFFB74D),
            // Wraps from the second row into the third.
            event("Summer trip with a very long title", LocalDate(2026, 7, 9), LocalDate(2026, 7, 15), 0xFF64B5F6),
            event("Conference", LocalDate(2026, 7, 7), LocalDate(2026, 7, 8), 0xFFE57373),
            event("Release", LocalDate(2026, 7, 16), LocalDate(2026, 7, 16), null),
            // Four events on Wed Jul 22 with three lanes: one overflows as +1.
            event("Sprint", LocalDate(2026, 7, 20), LocalDate(2026, 7, 24), 0xFF81C784),
            event("Offsite", LocalDate(2026, 7, 22), LocalDate(2026, 7, 23), 0xFFBA68C8),
            event("Demo", LocalDate(2026, 7, 22), LocalDate(2026, 7, 22), 0xFF4DB6AC),
            event("Retro", LocalDate(2026, 7, 22), LocalDate(2026, 7, 22), 0xFFF06292),
            // Continues into August from the last row.
            event("Vacation", LocalDate(2026, 7, 30), LocalDate(2026, 8, 4), 0xFF9575CD),
        )

    private fun content() {
        rule.setContent {
            MaterialTheme(
                colorScheme = if (isSystemInDarkTheme()) darkColorScheme() else lightColorScheme(),
            ) {
                Surface {
                    HorizontalCalendar(
                        state =
                            rememberCalendarState(
                                startMonth = YearMonth(2026, 7),
                                endMonth = YearMonth(2026, 7),
                                firstVisibleMonth = YearMonth(2026, 7),
                                firstDayOfWeek = DayOfWeek.MONDAY,
                            ),
                        monthHeader = { CalendarDefaults.MonthTitle(it) },
                        weekOverlay = { week -> WeekEventBanners(week = week, events = events) },
                        monthFooter = { month ->
                            Text(
                                text = "${events.size} events in ${month.yearMonth}",
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                            )
                        },
                        dayContent = { day ->
                            Box(Modifier.fillMaxWidth().height(24.dp), contentAlignment = Alignment.TopCenter) {
                                Text(
                                    text = day.date.day.toString(),
                                    style = MaterialTheme.typography.labelMedium,
                                    color =
                                        if (day.position == DayPosition.MonthDate) {
                                            MaterialTheme.colorScheme.onSurface
                                        } else {
                                            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                                        },
                                    modifier = Modifier.padding(top = 4.dp),
                                )
                            }
                        },
                    )
                }
            }
        }
    }

    @Test
    fun weekEventBannersLight() {
        content()
        rule.onRoot().captureRoboImage()
    }

    @Test
    @Config(qualifiers = "+night")
    fun weekEventBannersDark() {
        content()
        rule.onRoot().captureRoboImage()
    }
}
