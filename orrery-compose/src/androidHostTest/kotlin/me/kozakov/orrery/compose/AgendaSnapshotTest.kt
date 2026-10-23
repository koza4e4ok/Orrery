package me.kozakov.orrery.compose

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.github.takahirom.roborazzi.captureRoboImage
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import me.kozakov.orrery.core.CalendarEvent
import me.kozakov.orrery.core.EventSpan
import me.kozakov.orrery.core.eventsByDate
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = RobolectricDeviceQualifiers.Pixel5)
class AgendaSnapshotTest {
    @get:Rule val rule = createComposeRule()

    private val start = LocalDate(2026, 7, 1)
    private val end = LocalDate(2026, 7, 31)
    private val today = LocalDate(2026, 7, 15)

    private val fixtureEvents =
        listOf(
            CalendarEvent<Nothing>(
                id = "1",
                title = "Offsite Summit",
                span = EventSpan.AllDay(LocalDate(2026, 7, 15), LocalDate(2026, 7, 16)),
                subtitle = "Kyiv Office",
                colorArgb = 0xFF64B5F6,
            ),
            CalendarEvent<Nothing>(
                id = "2",
                title = "Team standup",
                span =
                    EventSpan.Timed(
                        start = LocalDateTime(2026, 7, 15, 9, 0),
                        end = LocalDateTime(2026, 7, 15, 9, 30),
                    ),
                colorArgb = 0xFF81C784,
            ),
            CalendarEvent<Nothing>(
                id = "3",
                title = "Design review",
                span =
                    EventSpan.Timed(
                        start = LocalDateTime(2026, 7, 15, 14, 0),
                        end = LocalDateTime(2026, 7, 15, 15, 0),
                    ),
                subtitle = "Room 302",
                colorArgb = 0xFFE57373,
            ),
            CalendarEvent<Nothing>(
                id = "4",
                title = "Server maintenance",
                span =
                    EventSpan.Timed(
                        start = LocalDateTime(2026, 7, 15, 22, 0),
                        end = LocalDateTime(2026, 7, 16, 2, 0),
                    ),
                colorArgb = 0xFFFFB74D,
            ),
            CalendarEvent<Nothing>(
                id = "5",
                title = "Brunch with Alex",
                span =
                    EventSpan.Timed(
                        start = LocalDateTime(2026, 7, 18, 11, 0),
                        end = LocalDateTime(2026, 7, 18, 12, 30),
                    ),
                colorArgb = 0xFFBA68C8,
            ),
        )

    private fun content(empty: Boolean = false) {
        val indexed =
            if (empty) {
                emptyMap()
            } else {
                fixtureEvents.eventsByDate(start..end)
            }
        rule.setContent {
            MaterialTheme(
                colorScheme = if (isSystemInDarkTheme()) darkColorScheme() else lightColorScheme(),
            ) {
                Surface {
                    Agenda(
                        events = { date -> indexed[date].orEmpty() },
                        state =
                            rememberAgendaState(
                                startDate = start,
                                endDate = end,
                                initialVisibleDate = today,
                            ),
                        modifier = Modifier.fillMaxWidth().height(420.dp),
                        today = today,
                    )
                }
            }
        }
    }

    @Test
    fun agendaLight() {
        content()
        rule.onRoot().captureRoboImage()
    }

    @Test
    @Config(qualifiers = "+night")
    fun agendaDark() {
        content()
        rule.onRoot().captureRoboImage()
    }

    @Test
    fun agendaEmpty() {
        content(empty = true)
        rule.onRoot().captureRoboImage()
    }
}
