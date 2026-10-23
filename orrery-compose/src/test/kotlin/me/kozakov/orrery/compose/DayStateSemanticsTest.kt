package me.kozakov.orrery.compose

import androidx.compose.foundation.layout.Row
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.datetime.LocalDate
import me.kozakov.orrery.core.CalendarDay
import me.kozakov.orrery.core.DayPosition
import me.kozakov.orrery.core.Selection
import me.kozakov.orrery.core.SelectionMode
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
class DayStateSemanticsTest {
    @get:Rule
    val rule = createComposeRule()

    private val start = LocalDate(2026, 7, 8)
    private val middle = LocalDate(2026, 7, 9)
    private val end = LocalDate(2026, 7, 10)
    private val farAway = LocalDate(2026, 1, 1)

    private fun hasState(value: String) = SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, value)

    private val hasNoState = SemanticsMatcher.keyNotDefined(SemanticsProperties.StateDescription)

    private fun range(today: LocalDate = farAway) {
        rule.setContent {
            val selection =
                rememberCalendarSelectionState(
                    mode = SelectionMode.Range(),
                    initialSelection = Selection(rangeStart = start, rangeEnd = end),
                )
            Row {
                listOf(start, middle, end).forEach { date ->
                    DefaultDay(
                        day = CalendarDay(date, DayPosition.MonthDate),
                        modifier = Modifier.weight(1f).testTag("day-$date"),
                        selectionState = selection,
                        today = today,
                    )
                }
            }
        }
    }

    private fun single(
        today: LocalDate = farAway,
        enabled: Boolean = true,
        strings: (@Composable () -> CalendarDayStrings)? = null,
    ) {
        rule.setContent {
            DefaultDay(
                day = CalendarDay(middle, DayPosition.MonthDate),
                modifier = Modifier.testTag("day"),
                today = today,
                enabled = enabled,
                strings = strings?.invoke() ?: CalendarDefaults.dayStrings(),
            )
        }
    }

    @Test
    fun plainDayHasNoStateDescription() {
        single()
        rule.onNodeWithTag("day").assert(hasNoState)
    }

    @Test
    fun todayIsAnnounced() {
        single(today = middle)
        rule.onNodeWithTag("day").assert(hasState("Today"))
    }

    @Test
    fun unavailableIsAnnounced() {
        single(enabled = false)
        rule.onNodeWithTag("day").assert(hasState("Unavailable"))
    }

    @Test
    fun rangeStartEndAndInRangeAreAnnounced() {
        range()
        rule.onNodeWithTag("day-$start").assert(hasState("Range start"))
        rule.onNodeWithTag("day-$middle").assert(hasState("In range"))
        rule.onNodeWithTag("day-$end").assert(hasState("Range end"))
    }

    @Test
    fun combinedStatesAreJoined() {
        range(today = start)
        rule.onNodeWithTag("day-$start").assert(hasState("Range start, Today"))
        rule.onNodeWithTag("day-$middle").assert(hasState("In range"))
    }

    @Test
    fun todayAndUnavailableAreJoined() {
        single(today = middle, enabled = false)
        rule.onNodeWithTag("day").assert(hasState("Today, Unavailable"))
    }

    @Test
    @Config(qualifiers = "de")
    fun statesAreLocalized() {
        range(today = start)
        rule.onNodeWithTag("day-$start").assert(hasState("Beginn des Zeitraums, Heute"))
        rule.onNodeWithTag("day-$middle").assert(hasState("Im Zeitraum"))
        rule.onNodeWithTag("day-$end").assert(hasState("Ende des Zeitraums"))
    }

    @Test
    @Config(qualifiers = "de")
    fun overrideWins() {
        single(today = middle, enabled = false, strings = { CalendarDefaults.dayStrings(today = "Now") })
        rule.onNodeWithTag("day").assert(hasState("Now, Nicht verfügbar"))
    }
}
