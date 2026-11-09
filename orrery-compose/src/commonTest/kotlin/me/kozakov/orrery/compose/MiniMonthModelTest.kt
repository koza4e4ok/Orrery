package me.kozakov.orrery.compose

import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.YearMonth
import me.kozakov.orrery.core.DayInfo
import me.kozakov.orrery.core.OutDateStyle
import me.kozakov.orrery.core.Selection
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotSame
import kotlin.test.assertSame

class MiniMonthModelTest {
    @Test
    fun modelDescribesTheGridWithoutOutDates() {
        // 1 Feb 2026 is a Sunday.
        val monday = MiniMonthGridModel(YearMonth(2026, 2), DayOfWeek.MONDAY)
        assertEquals(6, monday.leadingBlanks)
        assertEquals(28, monday.dayCount)
        assertEquals(5, monday.rowCount)
        assertEquals(6, monday.column(1))
        assertEquals(0, monday.row(1))
        assertEquals(0, monday.column(2))
        assertEquals(1, monday.row(2))
        val sunday = MiniMonthGridModel(YearMonth(2026, 2), DayOfWeek.SUNDAY)
        assertEquals(0, sunday.leadingBlanks)
        assertEquals(4, sunday.rowCount)
        assertEquals(6, MiniMonthGridModel(YearMonth(2026, 8), DayOfWeek.MONDAY).rowCount)
    }

    @Test
    fun cacheReturnsTheSameInstanceForTheSameKey() {
        val cache = MiniMonthGridModelCache()
        val first = cache[YearMonth(2026, 7), DayOfWeek.MONDAY]
        assertSame(first, cache[YearMonth(2026, 7), DayOfWeek.MONDAY])
        assertNotSame(first, cache[YearMonth(2026, 7), DayOfWeek.SUNDAY])
        assertNotSame(first, cache[YearMonth(2027, 7), DayOfWeek.MONDAY])
        assertEquals(3, cache.misses)
        assertEquals(3, cache.size)
    }

    @Test
    fun cacheEvictsTheLeastRecentlyUsedModel() {
        val cache = MiniMonthGridModelCache(capacity = 2)
        val january = cache[YearMonth(2026, 1), DayOfWeek.MONDAY]
        cache[YearMonth(2026, 2), DayOfWeek.MONDAY]
        cache[YearMonth(2026, 1), DayOfWeek.MONDAY] // January becomes the most recent
        cache[YearMonth(2026, 3), DayOfWeek.MONDAY] // evicts February
        assertEquals(2, cache.size)
        assertSame(january, cache[YearMonth(2026, 1), DayOfWeek.MONDAY])
        assertEquals(3, cache.misses)
        cache[YearMonth(2026, 2), DayOfWeek.MONDAY]
        assertEquals(4, cache.misses)
        assertFailsWith<IllegalArgumentException> { MiniMonthGridModelCache(capacity = 0) }
    }

    @Test
    fun dayStateFoldsTodaySelectionAndHolidaysIntoMasks() {
        val model = MiniMonthGridModel(YearMonth(2026, 3), DayOfWeek.MONDAY)
        val queried = mutableListOf<LocalDate>()
        val selection =
            Selection(
                rangeStart = LocalDate(2026, 2, 25),
                rangeEnd = LocalDate(2026, 3, 3),
                multi = setOf(LocalDate(2026, 3, 20)),
            )
        val state =
            miniMonthDayState(
                model,
                today = LocalDate(2026, 3, 12),
                selection = selection,
                dayInfo = { date ->
                    queried += date
                    DayInfo(label = null, isHoliday = date.day == 8)
                },
            )
        assertEquals(12, state.todayDay)
        assertEquals((1..3).toList() + 20, (1..31).filter(state::isSelected))
        assertEquals((1..3).toList(), (1..31).filter(state::isInRange))
        assertEquals(listOf(8), (1..31).filter(state::isHoliday))
        assertEquals(0, state.rangeStartDay) // the range starts in February
        assertEquals(3, state.rangeEndDay)
        assertEquals(31, queried.size)
        val again =
            miniMonthDayState(model, LocalDate(2026, 3, 12), selection) { DayInfo(null, isHoliday = it.day == 8) }
        assertEquals(state, again)
        assertEquals(0, miniMonthDayState(model, LocalDate(2026, 4, 12), null, null).todayDay)
    }

    @Test
    fun monthGridBoundsFollowTheOutDateStyle() {
        val february = YearMonth(2026, 2)
        assertEquals(LocalDate(2026, 1, 26), monthGridStart(february, DayOfWeek.MONDAY, OutDateStyle.EndOfRow))
        assertEquals(LocalDate(2026, 3, 1), monthGridEnd(february, DayOfWeek.MONDAY, OutDateStyle.EndOfRow))
        assertEquals(LocalDate(2026, 3, 8), monthGridEnd(february, DayOfWeek.MONDAY, OutDateStyle.EndOfGrid))
        assertEquals(LocalDate(2026, 2, 1), monthGridStart(february, DayOfWeek.MONDAY, OutDateStyle.None))
        assertEquals(LocalDate(2026, 2, 28), monthGridEnd(february, DayOfWeek.MONDAY, OutDateStyle.None))
        assertEquals(LocalDate(2026, 2, 1), monthGridStart(february, DayOfWeek.SUNDAY, OutDateStyle.EndOfRow))
    }

    @Test
    fun widenWithinClampsTheWideningButNeverTheExactRange() {
        val exact = LocalDate(2026, 1, 26)..LocalDate(2026, 3, 1)
        assertEquals(
            LocalDate(2026, 1, 1)..LocalDate(2026, 3, 31),
            widenWithin(exact, LocalDate(2026, 1, 1), LocalDate(2026, 3, 31), null, null),
        )
        assertEquals(
            LocalDate(2026, 1, 26)..LocalDate(2026, 3, 1),
            widenWithin(
                exact = exact,
                wantedStart = LocalDate(2026, 1, 1),
                wantedEnd = LocalDate(2026, 3, 31),
                minBound = LocalDate(2026, 2, 1),
                maxBound = LocalDate(2026, 2, 28),
            ),
        )
    }
}
