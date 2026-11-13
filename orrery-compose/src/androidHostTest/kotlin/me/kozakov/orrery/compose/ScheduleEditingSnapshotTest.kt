package me.kozakov.orrery.compose

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.github.takahirom.roborazzi.captureRoboImage
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import me.kozakov.orrery.core.CalendarEvent
import me.kozakov.orrery.core.EventSpan
import me.kozakov.orrery.core.eventsByDate
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Mid-drag frames of the editable schedule: the ghost, its time chip, the dimmed
 * source tile and the resize handles. The ghost is pinned with
 * [LocalScheduleDragOverride], so no pointer has to be held.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = RobolectricDeviceQualifiers.Pixel5)
class ScheduleEditingSnapshotTest {
    @get:Rule val rule = createComposeRule()

    @Before
    fun use24HourClock() = setSystem24HourClock(true)

    private val today = LocalDate(2026, 7, 15)

    private fun timed(
        id: String,
        title: String,
        day: Int,
        start: LocalTime,
        end: LocalTime,
        colorArgb: Long? = null,
    ): CalendarEvent<String> =
        CalendarEvent(
            id = id,
            title = title,
            span =
                EventSpan.Timed(
                    start = LocalDateTime(LocalDate(2026, 7, day), start),
                    end = LocalDateTime(LocalDate(2026, 7, day), end),
                ),
            colorArgb = colorArgb,
            payload = id,
        )

    private val standup = timed("standup", "Team standup", 15, LocalTime(9, 0), LocalTime(9, 30), 0xFF81C784)
    private val review = timed("review", "Design review", 15, LocalTime(11, 0), LocalTime(12, 0), 0xFFE57373)
    private val retro = timed("retro", "Retro", 17, LocalTime(10, 0), LocalTime(11, 0), 0xFF4DB6AC)
    private val events =
        listOf(standup, review, retro).eventsByDate(LocalDate(2026, 7, 1)..LocalDate(2026, 7, 31))

    private fun content(
        week: Boolean,
        preview: ScheduleDragPreview<String>,
        layoutDirection: LayoutDirection = LayoutDirection.Ltr,
    ) {
        rule.setContent {
            MaterialTheme(
                colorScheme = if (isSystemInDarkTheme()) darkColorScheme() else lightColorScheme(),
            ) {
                Surface {
                    CompositionLocalProvider(
                        LocalLayoutDirection provides layoutDirection,
                        LocalScheduleDragOverride provides preview,
                    ) {
                        val state =
                            rememberScheduleState(
                                firstVisibleDate = today,
                                firstDayOfWeek = DayOfWeek.MONDAY,
                                initialScrollTime = LocalTime(8, 0),
                            )
                        val modifier = Modifier.fillMaxWidth().height(640.dp)
                        if (week) {
                            WeekSchedule(
                                events = { date -> events[date].orEmpty() },
                                modifier = modifier,
                                state = state,
                                today = today,
                                onSlotCreate = { _, _, _ -> },
                                onEventMove = { _, _, _, _ -> },
                                onEventResize = { _, _, _ -> },
                            )
                        } else {
                            DaySchedule(
                                events = { date -> events[date].orEmpty() },
                                modifier = modifier,
                                state = state,
                                today = today,
                                onSlotCreate = { _, _, _ -> },
                                onEventMove = { _, _, _, _ -> },
                                onEventResize = { _, _, _ -> },
                            )
                        }
                    }
                }
            }
        }
    }

    private fun preview(
        kind: ScheduleDragKind,
        event: CalendarEvent<String>?,
        date: LocalDate,
        start: LocalTime,
        end: LocalTime,
    ) = ScheduleDragPreview(kind = kind, event = event, date = date, startTime = start, endTime = end)

    private val moveStandup = preview(ScheduleDragKind.Move, standup, today, LocalTime(10, 15), LocalTime(10, 45))

    @Test
    fun moveGhostDayLight() {
        content(week = false, preview = moveStandup)
        rule.onRoot().captureRoboImage()
    }

    @Test
    @Config(qualifiers = "+night")
    fun moveGhostDayDark() {
        content(week = false, preview = moveStandup)
        rule.onRoot().captureRoboImage()
    }

    @Test
    fun createGhostWeekLight() {
        val create = preview(ScheduleDragKind.Create, null, LocalDate(2026, 7, 16), LocalTime(13, 0), LocalTime(14, 30))
        content(week = true, preview = create)
        rule.onRoot().captureRoboImage()
    }

    @Test
    fun resizeGhostWeekRtl() {
        val resize = preview(ScheduleDragKind.ResizeEnd, review, today, LocalTime(11, 0), LocalTime(13, 0))
        content(week = true, preview = resize, layoutDirection = LayoutDirection.Rtl)
        rule.onRoot().captureRoboImage()
    }
}
