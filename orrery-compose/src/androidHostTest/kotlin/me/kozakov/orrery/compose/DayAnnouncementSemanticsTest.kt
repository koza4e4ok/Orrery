package me.kozakov.orrery.compose

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.datetime.LocalDate
import me.kozakov.orrery.core.CalendarDay
import me.kozakov.orrery.core.CalendarEvent
import me.kozakov.orrery.core.DayInfo
import me.kozakov.orrery.core.DayPosition
import me.kozakov.orrery.core.EventSpan
import me.kozakov.orrery.core.Selection
import me.kozakov.orrery.core.SelectionMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** What [DefaultDay] puts into its content description, and the describeDay hook (#89 task 13, M1). */
@RunWith(AndroidJUnit4::class)
class DayAnnouncementSemanticsTest {
    @get:Rule
    val rule = createComposeRule()

    private val date = LocalDate(2026, 7, 9)
    private val farAway = LocalDate(2026, 1, 1)
    private val christmas = DayInfo(label = "Christmas", isHoliday = true)

    private fun hasContent(value: String) =
        SemanticsMatcher.expectValue(SemanticsProperties.ContentDescription, listOf(value))

    private fun hasState(value: String) = SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, value)

    private val hasNoState = SemanticsMatcher.keyNotDefined(SemanticsProperties.StateDescription)

    private val day get() = rule.onNodeWithTag("day")

    private fun show(
        today: LocalDate = farAway,
        info: DayInfo? = null,
        selected: Boolean = false,
        decorators: @Composable () -> List<DayDecorator> = { emptyList() },
        describeDay: ((DayAnnouncement) -> String)? = null,
    ) {
        rule.setContent {
            val selection =
                rememberCalendarSelectionState(
                    mode = SelectionMode.Single(),
                    initialSelection = if (selected) Selection(single = date) else Selection.Empty,
                )
            DefaultDay(
                day = CalendarDay(date, DayPosition.MonthDate),
                modifier = Modifier.testTag("day"),
                selectionState = selection,
                today = today,
                info = info,
                decorators = decorators(),
                describeDay = describeDay,
            )
        }
    }

    private fun showStyled(
        density: CalendarDensity,
        info: DayInfo?,
        describeDay: ((DayAnnouncement) -> String)? = null,
    ) {
        rule.setContent {
            DefaultDay(
                day = CalendarDay(date, DayPosition.MonthDate),
                style = CalendarDefaults.style(density = density),
                modifier = Modifier.testTag("day"),
                today = farAway,
                info = info,
                describeDay = describeDay,
            )
        }
    }

    @Test
    fun dateIncludesTheWeekday() {
        show()
        day.assert(hasContent("Thursday, July 9, 2026")).assert(hasNoState)
    }

    @Test
    @Config(qualifiers = "de")
    fun dateIncludesTheWeekdayInGerman() {
        show()
        day.assert(hasContent("Donnerstag, 9. Juli 2026"))
    }

    @Test
    fun dateMatchesTheFullPlatformFormat() {
        show()
        day.assert(hasContent(PlatformDateFormat.formatDate(date, DateFormattingStyle.Full)))
    }

    @Test
    fun visibleLabelIsAnnounced() {
        show(info = christmas)
        day.assert(hasContent("Thursday, July 9, 2026, Christmas")).assert(hasState("Holiday"))
    }

    @Test
    fun blankLabelIsNotAnnounced() {
        show(info = DayInfo(label = "  "))
        day.assert(hasContent("Thursday, July 9, 2026")).assert(hasNoState)
    }

    @Test
    fun holidayWithoutLabelIsAnnounced() {
        show(info = DayInfo(label = null, isHoliday = true))
        day.assert(hasContent("Thursday, July 9, 2026")).assert(hasState("Holiday"))
    }

    @Test
    fun compactDensityDoesNotAnnounceTheHiddenLabel() {
        showStyled(CalendarDensity.Compact, christmas)
        day.assert(hasContent("Thursday, July 9, 2026")).assert(hasState("Holiday"))
    }

    @Test
    fun comfortableDensityAnnouncesTheShownLabel() {
        showStyled(CalendarDensity.Comfortable, christmas)
        day.assert(hasContent("Thursday, July 9, 2026, Christmas")).assert(hasState("Holiday"))
    }

    @Test
    fun describeDayCanAnnounceAHiddenLabel() {
        showStyled(CalendarDensity.Compact, christmas, describeDay = { a ->
            listOfNotNull(a.contentDescription, a.info?.label).joinToString(", ")
        })
        day.assert(hasContent("Thursday, July 9, 2026, Christmas"))
    }

    @Test
    fun eventDotsAnnounceTheFullCountNotTheDrawnDots() {
        val five = List(5) { Color(0xFF000000 + it) }
        show(decorators = { listOf(DayDecorators.eventDots(maxDots = 3) { five }) })
        day.assert(hasContent("Thursday, July 9, 2026, Events: 5"))
    }

    @Test
    fun badgeAnnouncesItsCount() {
        show(decorators = { listOf(DayDecorators.badge(maxCount = 9) { 12 }) })
        day.assert(hasContent("Thursday, July 9, 2026, Events: 12"))
    }

    @Test
    fun dotsAndBadgeAnnounceTheLargerCount() {
        show(
            decorators = {
                listOf(
                    DayDecorators.eventDots { listOf(Color.Red, Color.Blue) },
                    DayDecorators.badge { 4 },
                )
            },
        )
        day.assert(hasContent("Thursday, July 9, 2026, Events: 4"))
    }

    @Test
    fun eventDotsForEventsAnnounceEveryEventNotTheDistinctColors() {
        val events = List(3) { CalendarEvent<Unit>(id = "e$it", title = "Event $it", span = EventSpan.AllDay(date)) }
        show(
            decorators = {
                listOf(DayDecorators.eventDotsForEvents(eventsByDate = { events }, defaultColor = Color.Red))
            },
        )
        day.assert(hasContent("Thursday, July 9, 2026, Events: 3"))
    }

    @Test
    fun spanningEventBarsAnnounceTheirCount() {
        val events = List(2) { CalendarEvent<Unit>(id = "e$it", title = "Event $it", span = EventSpan.AllDay(date)) }
        show(decorators = { listOf(DayDecorators.spanningEventBars(eventsByDate = { events }, maxBars = 1)) })
        day.assert(hasContent("Thursday, July 9, 2026, Events: 2"))
    }

    @Test
    fun selectedColorEventDotsAnnounceTheirCount() {
        show(
            selected = true,
            decorators = {
                listOf(DayDecorators.eventDots(selectedColor = Color.White) { listOf(Color.Red, Color.Blue) })
            },
        )
        day.assert(hasContent("Thursday, July 9, 2026, Events: 2")).assert(hasState("Selected"))
    }

    @Test
    fun stackedBarsAreNotAnnounced() {
        show(decorators = { listOf(DayDecorators.stackedBars { listOf(Color.Red, Color.Blue) }) })
        day.assert(hasContent("Thursday, July 9, 2026"))
    }

    @Test
    fun noEventsAddNoEventText() {
        show(decorators = { listOf(DayDecorators.eventDots { emptyList() }, DayDecorators.badge { 0 }) })
        day.assert(hasContent("Thursday, July 9, 2026"))
    }

    @Test
    fun customDecoratorsAreNotAnnounced() {
        show(decorators = { listOf(DayDecorator { }) })
        day.assert(hasContent("Thursday, July 9, 2026"))
    }

    @Test
    fun labelComesBeforeEvents() {
        show(info = christmas, decorators = { listOf(DayDecorators.badge { 2 }) })
        day.assert(hasContent("Thursday, July 9, 2026, Christmas, Events: 2"))
    }

    @Test
    fun describeDayReplacesTheContentAndKeepsTheState() {
        var received: DayAnnouncement? = null
        show(today = date, selected = true, describeDay = { a ->
            received = a
            "${a.contentDescription}, booked"
        })
        day
            .assert(hasContent("Thursday, July 9, 2026, booked"))
            .assert(hasState("Selected, Today"))
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Selected, true))
        val announcement = checkNotNull(received)
        assertEquals("Selected, Today", announcement.stateDescription)
        assertTrue(announcement.isSelected && announcement.isToday)
    }

    @Test
    fun describeDayWorksOnTheTurnoverOverload() {
        var received: DayAnnouncement? = null
        rule.setContent {
            DefaultDay(
                day = CalendarDay(date, DayPosition.MonthDate),
                style = CalendarDefaults.style(density = CalendarDensity.Compact),
                turnoverDates = setOf(date),
                modifier = Modifier.testTag("day"),
                today = farAway,
                info = christmas,
                describeDay = { a ->
                    received = a
                    listOfNotNull(a.contentDescription, a.info?.label).joinToString(", ")
                },
            )
        }
        day.assert(hasContent("Thursday, July 9, 2026, Christmas")).assert(hasState("Holiday"))
        assertEquals(christmas, checkNotNull(received).info)
    }

    @Test
    @Config(qualifiers = "de")
    fun stringsAreLocalizedInGerman() = assertLocalized("Termine")

    @Test
    @Config(qualifiers = "es")
    fun stringsAreLocalizedInSpanish() = assertLocalized("Eventos")

    @Test
    @Config(qualifiers = "ja")
    fun stringsAreLocalizedInJapanese() = assertLocalized("予定")

    @Test
    @Config(qualifiers = "uk")
    fun stringsAreLocalizedInUkrainian() = assertLocalized("Події")

    @Test
    @Config(qualifiers = "zh-rCN")
    fun stringsAreLocalizedInSimplifiedChinese() = assertLocalized("日程")

    /** Selected + today + holiday with two events, against the English texts and the localized join. */
    private fun assertLocalized(eventsWord: String) {
        lateinit var strings: CalendarDayStrings
        rule.setContent {
            strings = CalendarDefaults.dayStrings()
            val selection =
                rememberCalendarSelectionState(
                    mode = SelectionMode.Single(),
                    initialSelection = Selection(single = date),
                )
            DefaultDay(
                day = CalendarDay(date, DayPosition.MonthDate),
                modifier = Modifier.testTag("day"),
                selectionState = selection,
                today = date,
                info = DayInfo(label = null, isHoliday = true),
                decorators = listOf(DayDecorators.badge { 2 }),
                strings = strings,
            )
        }
        for ((localized, english) in listOf(
            strings.selected to "Selected",
            strings.holiday to "Holiday",
            strings.events to "Events: %1\$d",
        )) {
            assertTrue(localized.isNotBlank())
            assertNotEquals(english, localized)
        }
        assertTrue(strings.events, "%1\$d" in strings.events && eventsWord in strings.events)
        day.assert(hasState("${strings.selected}, ${strings.today}, ${strings.holiday}"))
        val dateText = PlatformDateFormat.formatDate(date, DateFormattingStyle.Full)
        day.assert(hasContent("$dateText, ${strings.events.formatTemplate(2)}"))
    }
}
