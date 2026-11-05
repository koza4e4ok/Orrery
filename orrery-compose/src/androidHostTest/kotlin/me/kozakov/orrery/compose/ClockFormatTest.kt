package me.kozakov.orrery.compose

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import me.kozakov.orrery.core.CalendarEvent
import me.kozakov.orrery.core.EventSpan
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

// en-US with "Use 24-hour format" off: times read like "12:30 PM".
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "en-rUS")
class ClockFormatTest {
    @get:Rule val rule = createComposeRule()

    private val day = LocalDate(2026, 7, 15)
    private val lunch =
        CalendarEvent<Nothing>(
            id = "l",
            title = "Lunch",
            span = EventSpan.Timed(LocalDateTime(2026, 7, 15, 12, 30), LocalDateTime(2026, 7, 15, 13, 30)),
        )

    @Before
    fun use12HourClock() = setSystem24HourClock(false)

    // CLDR may put a narrow no-break space before "PM", so match the parts separately.
    private fun clock(time: String) = hasText(time, substring = true) and hasText("PM", substring = true)

    @Test
    fun agendaRowFollowsTheDeviceClock() {
        rule.setContent { MaterialTheme { AgendaDefaults.EventRow(lunch, date = day) } }
        rule.onNode(clock("12:30"), useUnmergedTree = true).assertExists()
    }

    @Test
    fun scheduleTileAndHourLabelsFollowTheDeviceClock() {
        rule.setContent {
            MaterialTheme {
                DaySchedule(
                    events = { if (it == day) listOf(lunch) else emptyList() },
                    modifier = Modifier.fillMaxWidth().height(600.dp),
                    state =
                        rememberScheduleState(
                            firstVisibleDate = day,
                            firstDayOfWeek = DayOfWeek.MONDAY,
                            initialScrollTime = LocalTime(11, 0),
                        ),
                    today = day,
                )
            }
        }
        rule.onNode(clock("1:00") and !hasText("11:00", substring = true), useUnmergedTree = true).assertExists()
        rule.onNode(clock("12:30"), useUnmergedTree = true).assertExists()
    }

    @Test
    fun explicit24HourOverridesTheDevice() {
        rule.setContent { MaterialTheme { AgendaDefaults.EventRow(lunch, date = day, is24Hour = true) } }
        rule.onNodeWithText("12:30 – 13:30", useUnmergedTree = true).assertExists()
    }
}
