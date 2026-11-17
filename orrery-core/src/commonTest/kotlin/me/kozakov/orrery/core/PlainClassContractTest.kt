package me.kozakov.orrery.core

import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.YearMonth
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals
import kotlin.test.assertNull

/**
 * The tier A and B types of orrery-core are plain classes since 1.0 (#89, ledger section D).
 * Their hand-written equals, hashCode, copy and toString must behave like the data classes
 * they replace.
 */
class PlainClassContractTest {
    private val jul9 = LocalDate(2026, 7, 9)
    private val jul10 = LocalDate(2026, 7, 10)
    private val jul11 = LocalDate(2026, 7, 11)
    private val jul12 = LocalDate(2026, 7, 12)
    private val rule = RecurrenceRule.weekly(DayOfWeek.FRIDAY)
    private val instance = RecurrenceInstance(seriesId = "series", recurrenceId = jul10)

    private val event: CalendarEvent<String> =
        CalendarEvent(
            id = "a",
            title = "Standup",
            span = EventSpan.AllDay(jul10, jul11),
            subtitle = "Room 1",
            colorArgb = 0xFF336699,
            payload = "p",
            recurrence = rule,
            instance = instance,
        )
    private val otherEvent: CalendarEvent<String> =
        CalendarEvent(id = "b", title = "Review", span = EventSpan.AllDay(jul11))

    private fun newEvent(): CalendarEvent<String> =
        CalendarEvent("a", "Standup", EventSpan.AllDay(jul10, jul11), "Room 1", 0xFF336699, "p", rule, instance)

    private val dayInfo =
        DayInfo(
            label = "Holiday",
            isHoliday = true,
            tags = listOf("public"),
            priority = DayInfoPriority.PUBLIC_HOLIDAY,
            colorArgb = 0xFFC62828,
            payload = 42,
        )

    private val placed = PositionedEvent(event, jul10, 60, 90, 120, 1, 2, 0.5f, 0.25f)

    /**
     * [equal] is built separately from [base]; [copied] is `base.copy()` with no arguments; each of [differing]
     * changes one property of [base] to a value with a different hash.
     *
     * Every property of [base] differs from its constructor default, from null and from every other property of
     * the same type, so [copied] only equals [base] if each `copy` default reads its own property.
     */
    private fun <T : Any> assertContract(
        base: T,
        equal: T,
        copied: T,
        vararg differing: T,
    ) {
        assertEquals(base, equal)
        assertEquals(equal, base)
        assertEquals(base.hashCode(), equal.hashCode())
        assertEquals(base.toString(), equal.toString())
        assertEquals(base, copied, "copy() with no arguments")
        assertEquals(base.hashCode(), copied.hashCode(), "copy() with no arguments")
        assertNotEquals<Any?>(base, null)
        assertNotEquals<Any>(base, "not a $base")
        differing.forEachIndexed { index, variant ->
            assertNotEquals(base, variant, "variant $index: $variant")
            assertNotEquals(variant, base, "variant $index: $variant")
            assertNotEquals(base.hashCode(), variant.hashCode(), "hashCode of variant $index: $variant")
        }
    }

    @Test
    fun `CalendarEvent compares every property`() {
        assertContract(
            event,
            newEvent(),
            event.copy(),
            event.copy(id = "x"),
            event.copy(title = "x"),
            event.copy(span = EventSpan.AllDay(jul10)),
            event.copy(subtitle = null),
            event.copy(colorArgb = null),
            event.copy(payload = "q"),
            event.copy(recurrence = null),
            event.copy(instance = null),
        )
    }

    @Test
    fun `CalendarEvent equality ignores the type argument like the data class`() {
        val text: CalendarEvent<String> = CalendarEvent("a", "A", EventSpan.AllDay(jul10))
        val number: CalendarEvent<Int> = CalendarEvent("a", "A", EventSpan.AllDay(jul10))
        assertEquals<Any>(text, number)
        assertEquals(text.hashCode(), number.hashCode())
    }

    @Test
    fun `CalendarEvent copy preserves unspecified fields`() {
        val renamed = event.copy(title = "Renamed")
        assertEquals("Renamed", renamed.title)
        assertEquals(event.id, renamed.id)
        assertEquals(event.span, renamed.span)
        assertEquals(event.subtitle, renamed.subtitle)
        assertEquals(event.colorArgb, renamed.colorArgb)
        assertEquals(event.payload, renamed.payload)
        assertEquals(rule, renamed.recurrence)
        assertEquals(instance, renamed.instance)

        val moved = event.copy(span = EventSpan.AllDay(jul11), payload = "q")
        assertEquals(rule, moved.recurrence)
        assertEquals(instance, moved.instance)
        assertEquals("q", moved.payload)
        assertNull(event.copy(recurrence = null).recurrence)
        assertEquals(instance, event.copy(recurrence = null).instance)
    }

    @Test
    fun `CalendarEvent toString keeps the data class format`() {
        assertEquals(
            "CalendarEvent(id=a, title=Standup, span=${event.span}, subtitle=Room 1, colorArgb=4281558681, " +
                "payload=p, recurrence=$rule, instance=$instance)",
            event.toString(),
        )
    }

    @Test
    fun `DayInfo compares every property`() {
        assertContract(
            dayInfo,
            DayInfo("Holiday", true, listOf("public"), DayInfoPriority.PUBLIC_HOLIDAY, 0xFFC62828, 42),
            dayInfo.copy(),
            dayInfo.copy(label = null),
            dayInfo.copy(isHoliday = false),
            dayInfo.copy(tags = emptyList()),
            dayInfo.copy(priority = DayInfoPriority.DEFAULT),
            dayInfo.copy(colorArgb = null),
            dayInfo.copy(payload = 43),
        )
    }

    @Test
    fun `DayInfo copy preserves unspecified fields`() {
        val relabelled = dayInfo.copy(label = "Other")
        assertEquals("Other", relabelled.label)
        assertEquals(dayInfo.isHoliday, relabelled.isHoliday)
        assertEquals(dayInfo.tags, relabelled.tags)
        assertEquals(DayInfoPriority.PUBLIC_HOLIDAY, relabelled.priority)
        assertEquals(0xFFC62828, relabelled.colorArgb)
        assertEquals(42, relabelled.payload)

        val flagged = dayInfo.copy(isHoliday = false, tags = listOf("x"))
        assertEquals(DayInfoPriority.PUBLIC_HOLIDAY, flagged.priority)
        assertEquals(0xFFC62828, flagged.colorArgb)
        assertEquals(42, flagged.payload)
    }

    @Test
    fun `DayInfo toString keeps the data class format`() {
        assertEquals(
            "DayInfo(label=Holiday, isHoliday=true, tags=[public], priority=40, colorArgb=4291176488, payload=42)",
            dayInfo.toString(),
        )
        assertEquals(
            "DayInfo(label=null, isHoliday=false, tags=[], priority=0, colorArgb=null, payload=null)",
            DayInfo(label = null).toString(),
        )
    }

    @Test
    fun `SelectionMode variants compare every property`() {
        val single =
            SelectionMode.Single(autoAdvance = true, autoSelectDay = AutoSelectDay.LastSelectedDay, allowClear = true)
        assertContract(
            single,
            SelectionMode.Single(true, AutoSelectDay.LastSelectedDay, true),
            single.copy(),
            single.copy(autoAdvance = false),
            single.copy(autoSelectDay = AutoSelectDay.FirstDayOfMonth),
            single.copy(allowClear = false),
        )
        val range = SelectionMode.Range(minDays = 2, maxDays = 7)
        assertContract(
            range,
            SelectionMode.Range(2, 7),
            range.copy(),
            range.copy(minDays = null),
            range.copy(maxDays = 8),
        )
        assertEquals(7, range.copy(minDays = 3).maxDays)
        val multi = SelectionMode.Multi(maxCount = 3)
        assertContract(multi, SelectionMode.Multi(3), multi.copy(), multi.copy(maxCount = null))
        val week = SelectionMode.Week(firstDayOfWeek = DayOfWeek.SUNDAY, allowClear = false)
        assertContract(
            week,
            SelectionMode.Week(DayOfWeek.SUNDAY, false),
            week.copy(),
            week.copy(firstDayOfWeek = null),
            week.copy(allowClear = true),
        )
        assertEquals(DayOfWeek.SUNDAY, week.copy(allowClear = true).firstDayOfWeek)
        val month = SelectionMode.Month(allowClear = false)
        assertContract(month, SelectionMode.Month(false), month.copy(), month.copy(allowClear = true))
    }

    @Test
    fun `SelectionMode variants keep the data class toString`() {
        assertEquals(
            "Single(autoAdvance=false, autoSelectDay=FirstDayOfMonth, allowClear=false)",
            SelectionMode.Single().toString(),
        )
        assertEquals("Range(minDays=2, maxDays=null)", SelectionMode.Range(minDays = 2).toString())
        assertEquals("Multi(maxCount=null)", SelectionMode.Multi().toString())
        assertEquals("Week(firstDayOfWeek=MONDAY, allowClear=true)", SelectionMode.Week(DayOfWeek.MONDAY).toString())
        assertEquals("Month(allowClear=true)", SelectionMode.Month().toString())
    }

    @Test
    fun `Selection and SelectionResult compare every property`() {
        val selection = Selection(single = jul9, rangeStart = jul10, rangeEnd = jul11, multi = setOf(jul12))
        assertContract(
            selection,
            Selection(jul9, jul10, jul11, setOf(jul12)),
            selection.copy(),
            selection.copy(single = null),
            selection.copy(rangeStart = jul11),
            selection.copy(rangeEnd = null),
            selection.copy(multi = emptySet()),
        )
        assertEquals(Selection.Empty, Selection())
        assertEquals(setOf(jul12), selection.copy(single = jul11).multi)
        assertEquals(
            "Selection(single=2026-07-09, rangeStart=2026-07-10, rangeEnd=2026-07-11, multi=[2026-07-12])",
            selection.toString(),
        )

        val result = SelectionResult(selection, listOf(SelectionEvent.OutOfRange(jul11)))
        assertContract(
            result,
            SelectionResult(Selection(jul9, jul10, jul11, setOf(jul12)), listOf(SelectionEvent.OutOfRange(jul11))),
            result.copy(),
            result.copy(selection = Selection.Empty),
            result.copy(events = emptyList()),
        )
        assertEquals(result.events, result.copy(selection = Selection.Empty).events)
        assertEquals(
            "SelectionResult(selection=Selection(single=null, rangeStart=null, rangeEnd=null, multi=[]), events=[])",
            SelectionResult(Selection.Empty, emptyList()).toString(),
        )
    }

    @Test
    fun `calendar models compare every property`() {
        val day = CalendarDay(jul10, DayPosition.MonthDate)
        assertContract(
            day,
            CalendarDay(LocalDate(2026, 7, 10), DayPosition.MonthDate),
            day.copy(),
            day.copy(date = jul11),
            day.copy(position = DayPosition.OutDate),
        )
        assertEquals(DayPosition.MonthDate, day.copy(date = jul11).position)
        val week = CalendarWeek(listOf(day))
        assertContract(
            week,
            CalendarWeek(listOf(CalendarDay(jul10, DayPosition.MonthDate))),
            week.copy(),
            week.copy(days = emptyList()),
        )
        val month = CalendarMonth(YearMonth(2026, 7), listOf(week))
        assertContract(
            month,
            CalendarMonth(YearMonth(2026, 7), listOf(CalendarWeek(listOf(day)))),
            month.copy(),
            month.copy(yearMonth = YearMonth(2026, 8)),
            month.copy(weeks = emptyList()),
        )
        assertEquals(listOf(week), month.copy(yearMonth = YearMonth(2026, 8)).weeks)
        assertEquals(
            "CalendarMonth(yearMonth=2026-07, weeks=[CalendarWeek(days=[CalendarDay(date=2026-07-10, " +
                "position=MonthDate)])])",
            month.toString(),
        )
    }

    @Test
    fun `HeatmapSummary compares every property`() {
        val summary = HeatmapSummary(activeDays = 5, currentStreak = 2, longestStreak = 3)
        assertContract(
            summary,
            HeatmapSummary(5, 2, 3),
            summary.copy(),
            summary.copy(activeDays = 6),
            summary.copy(currentStreak = 0),
            summary.copy(longestStreak = 4),
        )
        assertEquals(HeatmapSummary(5, 9, 3), summary.copy(currentStreak = 9))
        // The data class formula: the first property's hash, then 31 * h + the next one.
        assertEquals((5 * 31 + 2) * 31 + 3, summary.hashCode())
        assertEquals("HeatmapSummary(activeDays=5, currentStreak=2, longestStreak=3)", summary.toString())
    }

    @Test
    fun `EventOverlapMode Stacked compares its property and keeps its check`() {
        val stacked = EventOverlapMode.Stacked(laneIndentFraction = 0.25f)
        assertContract(
            stacked,
            EventOverlapMode.Stacked(0.25f),
            stacked.copy(),
            stacked.copy(laneIndentFraction = 0.5f),
        )
        assertEquals(EventOverlapMode.Stacked(), EventOverlapMode.Stacked().copy())
        assertEquals("Stacked(laneIndentFraction=0.25)", stacked.toString())
        assertFailsWith<IllegalArgumentException> { EventOverlapMode.Stacked(1.5f) }
        assertFailsWith<IllegalArgumentException> { stacked.copy(laneIndentFraction = -0.25f) }
    }

    @Test
    fun `Float properties keep the data class equals semantics`() {
        // -0f and 0f differ.
        assertNotEquals(EventOverlapMode.Stacked(0f), EventOverlapMode.Stacked(-0f))
        assertNotEquals(placed.copy(leftFraction = 0f), placed.copy(leftFraction = -0f))
        assertNotEquals(placed.copy(widthFraction = 0f), placed.copy(widthFraction = -0f))

        // NaN equals NaN and hashes the same. Stacked rejects NaN, so only PositionedEvent can hold it.
        val nanLeft = placed.copy(leftFraction = Float.NaN)
        assertEquals(nanLeft, placed.copy(leftFraction = Float.NaN))
        assertEquals(nanLeft.hashCode(), placed.copy(leftFraction = Float.NaN).hashCode())
        val nanWidth = placed.copy(widthFraction = Float.NaN)
        assertEquals(nanWidth, placed.copy(widthFraction = Float.NaN))
        assertEquals(nanWidth.hashCode(), placed.copy(widthFraction = Float.NaN).hashCode())
        assertFailsWith<IllegalArgumentException> { EventOverlapMode.Stacked(Float.NaN) }
    }

    @Test
    fun `PositionedEvent compares every property`() {
        assertContract(
            placed,
            PositionedEvent(newEvent(), jul10, 60, 90, 120, 1, 2, 0.5f, 0.25f),
            placed.copy(),
            placed.copy(event = otherEvent),
            placed.copy(date = jul11),
            placed.copy(startMinute = 61),
            placed.copy(endMinute = 91),
            placed.copy(displayEndMinute = 121),
            placed.copy(columnIndex = 0),
            placed.copy(columnCount = 3),
            placed.copy(leftFraction = 0.25f),
            placed.copy(widthFraction = 0.5f),
        )
        assertEquals(PositionedEvent(event, jul10, 60, 90, 120, 1, 2, 0.5f, 0.75f), placed.copy(widthFraction = 0.75f))
        assertEquals(
            "PositionedEvent(event=$event, date=2026-07-10, startMinute=60, endMinute=90, displayEndMinute=120, " +
                "columnIndex=1, columnCount=2, leftFraction=0.5, widthFraction=0.25)",
            placed.toString(),
        )
    }

    @Test
    fun `DayScheduleLayout and AllDayBanner compare every property`() {
        val layout = DayScheduleLayout(jul10, listOf(otherEvent), listOf(placed))
        assertContract(
            layout,
            DayScheduleLayout(jul10, listOf(otherEvent), listOf(placed)),
            layout.copy(),
            layout.copy(date = jul11),
            layout.copy(allDayEvents = emptyList()),
            layout.copy(timedEvents = emptyList()),
        )
        assertEquals(listOf(placed), layout.copy(allDayEvents = emptyList()).timedEvents)
        assertEquals(
            "DayScheduleLayout(date=2026-07-10, allDayEvents=[], timedEvents=[])",
            DayScheduleLayout<Nothing>(jul10, emptyList(), emptyList()).toString(),
        )

        val banner = AllDayBanner(event, 0, 2, 1, clippedStart = true, clippedEnd = false)
        assertContract(
            banner,
            AllDayBanner(newEvent(), 0, 2, 1, true, false),
            banner.copy(),
            banner.copy(event = otherEvent),
            banner.copy(startColumn = 1),
            banner.copy(endColumnInclusive = 3),
            banner.copy(laneIndex = 0),
            banner.copy(clippedStart = false),
            banner.copy(clippedEnd = true),
        )
        assertEquals(AllDayBanner(event, 0, 2, 1, true, true), banner.copy(clippedEnd = true))
        assertEquals(
            "AllDayBanner(event=$event, startColumn=0, endColumnInclusive=2, laneIndex=1, clippedStart=true, " +
                "clippedEnd=false)",
            banner.toString(),
        )
    }

    @Test
    fun `week banner types compare every property`() {
        val banner = WeekEventBanner(event, 1, 3, 0, continuesFromPreviousWeek = false, continuesIntoNextWeek = true)
        assertContract(
            banner,
            WeekEventBanner(newEvent(), 1, 3, 0, false, true),
            banner.copy(),
            banner.copy(event = otherEvent),
            banner.copy(startColumn = 0),
            banner.copy(endColumnInclusive = 4),
            banner.copy(laneIndex = 1),
            banner.copy(continuesFromPreviousWeek = true),
            banner.copy(continuesIntoNextWeek = false),
        )
        assertEquals(3, banner.copy(laneIndex = 2).columnSpan)
        assertEquals(
            "WeekEventBanner(event=$event, startColumn=1, endColumnInclusive=3, laneIndex=0, " +
                "continuesFromPreviousWeek=false, continuesIntoNextWeek=true)",
            banner.toString(),
        )

        val result = WeekBannerPackResult(listOf(jul10, jul11), listOf(banner), listOf(0, 1))
        assertContract(
            result,
            WeekBannerPackResult(listOf(jul10, jul11), listOf(banner), listOf(0, 1)),
            result.copy(),
            result.copy(weekDates = listOf(jul10)),
            result.copy(banners = emptyList()),
            result.copy(overflowCountByColumn = listOf(0, 0)),
        )
        assertEquals(listOf(0, 1), result.copy(banners = emptyList()).overflowCountByColumn)
        assertEquals(
            "WeekBannerPackResult(weekDates=[2026-07-10, 2026-07-11], banners=[], overflowCountByColumn=[0])",
            WeekBannerPackResult<Nothing>(listOf(jul10, jul11), emptyList(), listOf(0)).toString(),
        )
    }
}
