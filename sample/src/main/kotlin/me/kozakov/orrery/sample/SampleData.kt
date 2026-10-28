package me.kozakov.orrery.sample

import androidx.compose.ui.graphics.Color
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.YearMonth
import kotlinx.datetime.daysUntil
import kotlinx.datetime.minus
import kotlinx.datetime.number
import kotlinx.datetime.plus
import me.kozakov.orrery.core.CalendarEvent
import me.kozakov.orrery.core.DisabledDates
import me.kozakov.orrery.core.EventSpan
import me.kozakov.orrery.core.eventsByDate

internal data class Habit(
    val name: String,
    val color: Color,
)

private data class EventTemplate(
    val start: LocalTime?,
    val end: LocalTime?,
    val title: String,
    val subtitle: String? = null,
    val colorArgb: Long,
)

/** Deterministic fake data anchored to the current date — no randomness. */
internal object SampleData {
    private const val WORK_COLOR = 0xFF1E88E5L
    private const val PERSONAL_COLOR = 0xFFE53935L
    private const val HEALTH_COLOR = 0xFF43A047L
    private const val TRAVEL_COLOR = 0xFF8E24AAL

    val habits =
        listOf(
            Habit("Water", Color(0xFF4FC3F7)),
            Habit("Run", Color(0xFFFF8A65)),
            Habit("Read", Color(0xFFBA68C8)),
        )

    private val eventPool =
        listOf(
            EventTemplate(LocalTime(8, 0), LocalTime(8, 45), "Morning run", "Riverside park", HEALTH_COLOR),
            EventTemplate(LocalTime(9, 0), LocalTime(9, 30), "Team standup", null, WORK_COLOR),
            EventTemplate(LocalTime(11, 30), LocalTime(12, 30), "Design review", "Room 302", WORK_COLOR),
            EventTemplate(LocalTime(12, 30), LocalTime(13, 30), "Lunch with Sam", null, PERSONAL_COLOR),
            EventTemplate(LocalTime(14, 0), LocalTime(15, 0), "Dentist", null, HEALTH_COLOR),
            EventTemplate(LocalTime(16, 0), LocalTime(16, 45), "1:1 with manager", null, WORK_COLOR),
            EventTemplate(LocalTime(18, 0), LocalTime(19, 0), "Gym", null, HEALTH_COLOR),
            EventTemplate(LocalTime(19, 30), LocalTime(21, 0), "Book club", null, PERSONAL_COLOR),
            EventTemplate(null, null, "Sprint planning day", null, WORK_COLOR),
        )

    /** Named events on ~40% of days in [range], 1..3 per day, indexed by date. */
    fun eventsFor(range: ClosedRange<LocalDate>): Map<LocalDate, List<CalendarEvent<Nothing>>> {
        val startEpoch = range.start.toEpochDays()
        val endEpoch = range.endInclusive.toEpochDays()
        val rawEvents =
            buildList<CalendarEvent<Nothing>> {
                for (epoch in startEpoch..endEpoch) {
                    val date = LocalDate.fromEpochDays(epoch)
                    val roll = (date.day * 7 + date.month.number * 3) % 10
                    if (roll <= 3) {
                        val count = roll % 3 + 1
                        val startIndex = (date.day * 3) % eventPool.size
                        for (i in 0 until count) {
                            val tpl = eventPool[(startIndex + i * 2) % eventPool.size]
                            val span =
                                if (tpl.start != null && tpl.end != null) {
                                    EventSpan.Timed(
                                        start = LocalDateTime(date, tpl.start),
                                        end = LocalDateTime(date, tpl.end),
                                    )
                                } else {
                                    EventSpan.AllDay(startDate = date)
                                }
                            add(
                                CalendarEvent<Nothing>(
                                    id = "$date-$i",
                                    title = tpl.title,
                                    span = span,
                                    subtitle = tpl.subtitle,
                                    colorArgb = tpl.colorArgb,
                                ),
                            )
                        }
                    }
                }
            }
        return (spanningEventsFor(range) + rawEvents).eventsByDate(range)
    }

    private fun spanningEventsFor(range: ClosedRange<LocalDate>): List<CalendarEvent<Nothing>> =
        buildList {
            var cursor = YearMonth(range.start.year, range.start.month)
            val endMonth = YearMonth(range.endInclusive.year, range.endInclusive.month)
            while (cursor <= endMonth) {
                val mStart = cursor.firstDay
                add(
                    CalendarEvent(
                        id = "span-summit-$cursor",
                        title = "Architecture Summit",
                        subtitle = "Multi-day offsite",
                        span = EventSpan.AllDay(mStart.plus(5, DateTimeUnit.DAY), mStart.plus(8, DateTimeUnit.DAY)),
                        colorArgb = WORK_COLOR,
                    ),
                )
                add(
                    CalendarEvent(
                        id = "span-release-$cursor",
                        title = "Release Freeze & QA",
                        subtitle = "Cross-platform verification",
                        span =
                            EventSpan.AllDay(
                                mStart.plus(14, DateTimeUnit.DAY),
                                mStart.plus(17, DateTimeUnit.DAY),
                            ),
                        colorArgb = TRAVEL_COLOR,
                    ),
                )
                add(
                    CalendarEvent(
                        id = "span-hackathon-$cursor",
                        title = "Compose UI Sprint",
                        subtitle = "Design & animation",
                        span =
                            EventSpan.AllDay(
                                mStart.plus(16, DateTimeUnit.DAY),
                                mStart.plus(19, DateTimeUnit.DAY),
                            ),
                        colorArgb = PERSONAL_COLOR,
                    ),
                )
                cursor = cursor.plus(1, DateTimeUnit.MONTH)
            }
        }

    /** Progress for past days and today; the most recent days are complete so streaks show. */
    fun habitProgress(
        habit: Habit,
        month: YearMonth,
        today: LocalDate,
    ): Map<LocalDate, Float> {
        val seed = habits.indexOf(habit) * 31 + 17
        val last = minOf(today, month.firstDay.plus(1, DateTimeUnit.MONTH).minus(1, DateTimeUnit.DAY))
        if (last < month.firstDay) return emptyMap()
        val streakLength = seed % 4 + 2

        val startEpoch = month.firstDay.toEpochDays()
        val endEpoch = last.toEpochDays()

        // ⚡ Bolt optimization: Iterating over epoch days is significantly faster
        // than using `generateSequence` with `plus(1, DateTimeUnit.DAY)`
        return (startEpoch..endEpoch)
            .map { LocalDate.fromEpochDays(it) }
            .associateWith { date ->
                if (date.daysUntil(today) < streakLength) 1f else ((date.day * seed) % 100) / 100f
            }
    }

    /** Consecutive days ending [today] with progress >= 0.99f. */
    fun streak(
        progress: Map<LocalDate, Float>,
        today: LocalDate,
    ): Int {
        var count = 0
        var cursor = today
        while ((progress[cursor] ?: 0f) >= 0.99f) {
            count++
            cursor = cursor.minus(1, DateTimeUnit.DAY)
        }
        return count
    }

    /** Average progress as a percentage. */
    fun monthCompletion(progress: Map<LocalDate, Float>): Int =
        if (progress.isEmpty()) {
            0
        } else {
            (
                progress.values.average() *
                    100
            ).toInt()
        }

    /** Base 90, +40 on Friday/Saturday, +0..9 by day of month. */
    fun nightlyPrice(date: LocalDate): Int {
        val weekend = date.dayOfWeek == DayOfWeek.FRIDAY || date.dayOfWeek == DayOfWeek.SATURDAY
        return 90 + (if (weekend) 40 else 0) + date.day % 10
    }

    /** Past dates plus two fixed blackout windows in the coming weeks. */
    fun blackoutDates(from: LocalDate): DisabledDates =
        DisabledDates {
            before(from)
            range(from.plus(12, DateTimeUnit.DAY)..from.plus(14, DateTimeUnit.DAY))
            range(from.plus(33, DateTimeUnit.DAY)..from.plus(36, DateTimeUnit.DAY))
        }

    /** Sum of nightly prices, checkout day excluded. */
    fun rangePrice(range: ClosedRange<LocalDate>): Int {
        val startEpoch = range.start.toEpochDays()
        val endEpoch = range.endInclusive.toEpochDays() - 1
        if (startEpoch > endEpoch) return 0

        // ⚡ Bolt optimization: Iterating over epoch days is significantly faster
        // than using `generateSequence` with `plus(1, DateTimeUnit.DAY)`
        return (startEpoch..endEpoch)
            .sumOf { nightlyPrice(LocalDate.fromEpochDays(it)) }
    }
}
