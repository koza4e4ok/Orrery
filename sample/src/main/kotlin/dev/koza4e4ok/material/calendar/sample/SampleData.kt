package dev.koza4e4ok.material.calendar.sample

import androidx.compose.ui.graphics.Color
import dev.koza4e4ok.material.calendar.core.DisabledDates
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.YearMonth
import kotlinx.datetime.daysUntil
import kotlinx.datetime.minus
import kotlinx.datetime.number
import kotlinx.datetime.plus

internal data class Event(
    val time: String,
    val title: String,
    val color: Color,
)

internal data class Habit(
    val name: String,
    val color: Color,
)

/** Deterministic fake data anchored to the current date — no randomness. */
internal object SampleData {
    private val work = Color(0xFF64B5F6)
    private val personal = Color(0xFFE57373)
    private val health = Color(0xFF81C784)

    val habits =
        listOf(
            Habit("Water", Color(0xFF4FC3F7)),
            Habit("Run", Color(0xFFFF8A65)),
            Habit("Read", Color(0xFFBA68C8)),
        )

    private val eventPool =
        listOf(
            Event("08:00", "Morning run", health),
            Event("09:00", "Team standup", work),
            Event("11:30", "Design review", work),
            Event("12:30", "Lunch with Sam", personal),
            Event("14:00", "Dentist", health),
            Event("16:00", "1:1 with manager", work),
            Event("18:00", "Gym", health),
            Event("19:30", "Book club", personal),
        )

    /** Named events on ~40% of days, 1..3 per day, sorted by time. */
    fun eventsFor(month: YearMonth): Map<LocalDate, List<Event>> {
        val last = month.firstDay.plus(1, DateTimeUnit.MONTH).minus(1, DateTimeUnit.DAY)
        return generateSequence(month.firstDay) { it.plus(1, DateTimeUnit.DAY) }
            .takeWhile { it <= last }
            .mapNotNull { date ->
                val roll = (date.day * 7 + date.month.number * 3) % 10
                if (roll > 3) return@mapNotNull null
                val count = roll % 3 + 1
                val startIndex = (date.day * 3) % eventPool.size
                val events =
                    (0 until count)
                        .map { eventPool[(startIndex + it * 2) % eventPool.size] }
                        .distinct()
                        .sortedBy { it.time }
                date to events
            }.toMap()
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
        return generateSequence(month.firstDay) { it.plus(1, DateTimeUnit.DAY) }
            .takeWhile { it <= last }
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
    fun rangePrice(range: ClosedRange<LocalDate>): Int =
        generateSequence(range.start) { it.plus(1, DateTimeUnit.DAY) }
            .takeWhile { it < range.endInclusive }
            .sumOf { nightlyPrice(it) }
}
