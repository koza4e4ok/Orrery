package dev.koza4e4ok.orrery.compose

import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.koza4e4ok.orrery.core.Selection
import dev.koza4e4ok.orrery.core.SelectionMode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.YearMonth
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ObservationTest {
    @get:Rule
    val rule = createComposeRule()

    @Test
    fun visibleMonthsEmitsTheInitialAndChangedMonths() {
        val months = mutableListOf<YearMonth>()
        lateinit var state: CalendarState
        lateinit var scope: CoroutineScope
        rule.setContent {
            state =
                rememberCalendarState(
                    startMonth = YearMonth(2026, 7),
                    endMonth = YearMonth(2026, 9),
                    firstVisibleMonth = YearMonth(2026, 7),
                    firstDayOfWeek = DayOfWeek.MONDAY,
                )
            scope = rememberCoroutineScope()
            LaunchedEffect(state) { state.visibleMonths().collect { months.add(it) } }
            HorizontalCalendar(
                state = state,
                dayContent = { day -> DefaultDay(day = day, animateSelection = false) },
            )
        }
        rule.waitForIdle()
        assertEquals(listOf(YearMonth(2026, 7)), months)
        rule.runOnIdle { scope.launch { state.scrollToMonth(YearMonth(2026, 8)) } }
        rule.waitForIdle()
        assertEquals(listOf(YearMonth(2026, 7), YearMonth(2026, 8)), months)
    }

    @Test
    fun selectionChangesEmitsOnClicksAndDedupes() {
        val selections = mutableListOf<Selection>()
        lateinit var selection: CalendarSelectionState
        rule.setContent {
            selection = rememberCalendarSelectionState(mode = SelectionMode.Single())
            LaunchedEffect(selection) { selection.selectionChanges().collect { selections.add(it) } }
        }
        rule.waitForIdle()
        assertEquals(listOf(Selection.Empty), selections)
        rule.runOnIdle { selection.click(LocalDate(2026, 7, 6)) }
        rule.waitForIdle()
        rule.runOnIdle { selection.click(LocalDate(2026, 7, 6)) } // same date again
        rule.waitForIdle()
        assertEquals(
            listOf(Selection.Empty, Selection(single = LocalDate(2026, 7, 6))),
            selections,
        )
    }
}
