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
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import me.kozakov.orrery.core.CalendarEvent
import me.kozakov.orrery.core.EventOverlapMode
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
class ScheduleViewSnapshotTest {
    @get:Rule val rule = createComposeRule()

    private val today = LocalDate(2026, 7, 15)
    private val now = LocalTime(10, 15)

    private fun timed(
        id: String,
        title: String,
        day: Int,
        start: LocalTime,
        end: LocalTime,
        colorArgb: Long? = null,
        subtitle: String? = null,
    ): CalendarEvent<Nothing> =
        CalendarEvent(
            id = id,
            title = title,
            span =
                EventSpan.Timed(
                    start = LocalDateTime(LocalDate(2026, 7, day), start),
                    end = LocalDateTime(LocalDate(2026, 7, day), end),
                ),
            subtitle = subtitle,
            colorArgb = colorArgb,
        )

    private val events =
        listOf(
            CalendarEvent<Nothing>(
                id = "offsite",
                title = "Offsite Summit",
                span = EventSpan.AllDay(LocalDate(2026, 7, 15), LocalDate(2026, 7, 16)),
                colorArgb = 0xFF64B5F6,
            ),
            CalendarEvent<Nothing>(
                id = "release",
                title = "Release 0.7",
                span = EventSpan.AllDay(LocalDate(2026, 7, 17)),
            ),
            timed("run", "Morning run", 13, LocalTime(8, 0), LocalTime(9, 0), 0xFF81C784),
            timed("planning", "Sprint planning", 14, LocalTime(10, 0), LocalTime(11, 30), 0xFFBA68C8),
            timed("standup", "Team standup", 15, LocalTime(9, 0), LocalTime(9, 30), 0xFF81C784),
            timed("review", "Design review", 15, LocalTime(9, 15), LocalTime(10, 30), 0xFFE57373, "Room 302"),
            timed("one-on-one", "1:1 with Alex", 15, LocalTime(9, 30), LocalTime(10, 0)),
            timed("lunch", "Lunch", 15, LocalTime(12, 0), LocalTime(13, 0), 0xFFFFB74D),
            timed("retro", "Retro", 17, LocalTime(11, 0), LocalTime(12, 0), 0xFF4DB6AC),
            timed("brunch", "Brunch", 18, LocalTime(11, 0), LocalTime(12, 30), 0xFFBA68C8),
        ).eventsByDate(LocalDate(2026, 7, 1)..LocalDate(2026, 7, 31))

    // Pale and very bright custom colors: event text must stay legible on both themes.
    private val paleEvents =
        listOf(
            timed("sun", "Sunrise walk", 15, LocalTime(8, 0), LocalTime(9, 0), 0xFFFFFF00, "Park"),
            timed("mint", "Mint review", 15, LocalTime(9, 0), LocalTime(10, 0), 0xFFB9F6CA, "Room 1"),
            timed("snow", "Snow day", 15, LocalTime(10, 0), LocalTime(11, 0), 0xFFFFFFFF, "Remote"),
            timed("cyan", "Cyan sync", 15, LocalTime(11, 0), LocalTime(12, 0), 0xFF00FFFF, "Room 2"),
        ).eventsByDate(LocalDate(2026, 7, 1)..LocalDate(2026, 7, 31))

    // A four-event overlap cluster for the stacked overlap mode.
    private val stackedEvents =
        listOf(
            timed("standup", "Team standup", 15, LocalTime(9, 0), LocalTime(9, 30), 0xFF81C784),
            timed("review", "Design review", 15, LocalTime(9, 15), LocalTime(11, 0), 0xFFE57373, "Room 302"),
            timed("sync", "API sync", 15, LocalTime(9, 45), LocalTime(10, 45), 0xFF64B5F6),
            timed("interview", "Interview", 15, LocalTime(10, 15), LocalTime(11, 30), 0xFFFFB74D, "Zoom"),
            timed("lunch", "Lunch", 15, LocalTime(12, 0), LocalTime(13, 0), 0xFFBA68C8),
        ).eventsByDate(LocalDate(2026, 7, 1)..LocalDate(2026, 7, 31))

    // Working-hours week: events before 08:00 and after 18:00 are clipped or hidden.
    private val workingHoursEvents =
        listOf(
            timed("gym", "Gym", 13, LocalTime(6, 30), LocalTime(8, 30), 0xFF81C784),
            timed("planning", "Sprint planning", 14, LocalTime(10, 0), LocalTime(11, 30), 0xFFBA68C8),
            timed("standup", "Standup", 15, LocalTime(9, 0), LocalTime(9, 30), 0xFF81C784),
            timed("review", "Design review", 15, LocalTime(9, 15), LocalTime(10, 30), 0xFFE57373),
            timed("demo", "Demo", 16, LocalTime(17, 0), LocalTime(19, 0), 0xFF64B5F6),
            timed("dinner", "Dinner", 17, LocalTime(19, 0), LocalTime(21, 0), 0xFFFFB74D),
        ).eventsByDate(LocalDate(2026, 7, 1)..LocalDate(2026, 7, 31))

    private fun allDay(
        id: String,
        title: String,
        startDay: Int,
        endDay: Int,
        colorArgb: Long? = null,
    ): CalendarEvent<Nothing> =
        CalendarEvent(
            id = id,
            title = title,
            span = EventSpan.AllDay(LocalDate(2026, 7, startDay), LocalDate(2026, 7, endDay)),
            colorArgb = colorArgb,
        )

    // Multi-day all-day banners, including ones clipped at both page edges.
    private val spanningAllDayEvents =
        listOf(
            allDay("conference", "KotlinConf trip", 10, 14, 0xFF64B5F6),
            allDay("offsite", "Offsite Summit", 15, 17, 0xFFBA68C8),
            allDay("holiday", "Summer holiday", 18, 24, 0xFF81C784),
            allDay("release", "Release 0.9", 16, 16),
            allDay("oncall", "On call", 14, 16, 0xFFFFB74D),
            timed("standup", "Standup", 15, LocalTime(9, 0), LocalTime(9, 30), 0xFF81C784),
        ).eventsByDate(LocalDate(2026, 7, 1)..LocalDate(2026, 7, 31))

    private fun content(
        week: Boolean,
        events: Map<LocalDate, List<CalendarEvent<Nothing>>> = this.events,
        minHour: Int = 0,
        maxHour: Int = 24,
        overlapMode: EventOverlapMode = EventOverlapMode.SideBySide,
    ) {
        rule.setContent {
            MaterialTheme(
                colorScheme = if (isSystemInDarkTheme()) darkColorScheme() else lightColorScheme(),
            ) {
                Surface {
                    val state =
                        rememberScheduleState(
                            firstVisibleDate = today,
                            firstDayOfWeek = DayOfWeek.MONDAY,
                            initialScrollTime = LocalTime(8, 0),
                            minHour = minHour,
                            maxHour = maxHour,
                        )
                    val modifier = Modifier.fillMaxWidth().height(640.dp)
                    if (week) {
                        WeekSchedule(
                            events = { date -> events[date].orEmpty() },
                            modifier = modifier,
                            state = state,
                            today = today,
                            currentTime = now,
                            overlapMode = overlapMode,
                        )
                    } else {
                        DaySchedule(
                            events = { date -> events[date].orEmpty() },
                            modifier = modifier,
                            state = state,
                            today = today,
                            currentTime = now,
                            showHalfHourLines = true,
                            overlapMode = overlapMode,
                        )
                    }
                }
            }
        }
    }

    @Test
    fun dayScheduleLight() {
        content(week = false)
        rule.onRoot().captureRoboImage()
    }

    @Test
    @Config(qualifiers = "+night")
    fun dayScheduleDark() {
        content(week = false)
        rule.onRoot().captureRoboImage()
    }

    @Test
    fun weekScheduleLight() {
        content(week = true)
        rule.onRoot().captureRoboImage()
    }

    @Test
    @Config(qualifiers = "+night")
    fun weekScheduleDark() {
        content(week = true)
        rule.onRoot().captureRoboImage()
    }

    @Test
    fun paleEventColorsLight() {
        content(week = false, events = paleEvents)
        rule.onRoot().captureRoboImage()
    }

    @Test
    @Config(qualifiers = "+night")
    fun paleEventColorsDark() {
        content(week = false, events = paleEvents)
        rule.onRoot().captureRoboImage()
    }

    @Test
    fun stackedOverlapLight() {
        content(week = false, events = stackedEvents, overlapMode = EventOverlapMode.Stacked())
        rule.onRoot().captureRoboImage()
    }

    @Test
    @Config(qualifiers = "+night")
    fun stackedOverlapDark() {
        content(week = false, events = stackedEvents, overlapMode = EventOverlapMode.Stacked())
        rule.onRoot().captureRoboImage()
    }

    @Test
    fun workingHoursWeekLight() {
        content(week = true, events = workingHoursEvents, minHour = 8, maxHour = 18)
        rule.onRoot().captureRoboImage()
    }

    @Test
    @Config(qualifiers = "+night")
    fun workingHoursWeekDark() {
        content(week = true, events = workingHoursEvents, minHour = 8, maxHour = 18)
        rule.onRoot().captureRoboImage()
    }

    @Test
    fun spanningAllDayHeaderLight() {
        content(week = true, events = spanningAllDayEvents)
        rule.onRoot().captureRoboImage()
    }

    @Test
    @Config(qualifiers = "+night")
    fun spanningAllDayHeaderDark() {
        content(week = true, events = spanningAllDayEvents)
        rule.onRoot().captureRoboImage()
    }
}
