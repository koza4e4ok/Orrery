package me.kozakov.orrery.compose

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.requestFocus
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.test.withKeyDown
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import me.kozakov.orrery.core.CalendarEvent
import me.kozakov.orrery.core.EventSpan
import me.kozakov.orrery.core.eventsByDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Editing gestures of the schedule. The grid starts at the top of the node (no
 * day header, no all-day events) scrolled to 08:00 with 60.dp hours, so a point
 * y.dp below the top is 08:00 plus y minutes; the hour gutter is 56.dp wide.
 */
@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
class ScheduleEditGesturesTest {
    @get:Rule val rule = createComposeRule()

    @Before
    fun use24HourClock() = setSystem24HourClock(true)

    private val jul15 = LocalDate(2026, 7, 15)
    private val jul16 = LocalDate(2026, 7, 16)

    private fun timed(
        id: String,
        title: String,
        start: LocalDateTime,
        end: LocalDateTime,
    ): CalendarEvent<String> = CalendarEvent(id = id, title = title, span = EventSpan.Timed(start, end), payload = id)

    private val standup =
        timed("standup", "Standup", LocalDateTime(2026, 7, 15, 9, 0), LocalDateTime(2026, 7, 15, 9, 30))
    private val review = timed("review", "Review", LocalDateTime(2026, 7, 15, 11, 0), LocalDateTime(2026, 7, 15, 12, 0))
    private val events = listOf(standup, review).eventsByDate(LocalDate(2026, 7, 1)..LocalDate(2026, 7, 31))

    private val longPresses = mutableListOf<Pair<LocalDate, LocalTime>>()
    private val created = mutableListOf<Triple<LocalDate, LocalTime, LocalTime>>()
    private val moves = mutableListOf<String>()
    private val resizes = mutableListOf<String>()
    private lateinit var state: ScheduleState

    private fun schedule(
        week: Boolean = false,
        longPress: Boolean = false,
        create: Boolean = false,
        move: Boolean = false,
        resize: Boolean = false,
        snapMinutes: Int = 15,
    ) {
        val onLongPress: ((LocalDate, LocalTime) -> Unit)? =
            if (longPress) { date, time -> longPresses += date to time } else null
        val onCreate: ((LocalDate, LocalTime, LocalTime) -> Unit)? =
            if (create) { date, start, end -> created += Triple(date, start, end) } else null
        val onMove: ((CalendarEvent<String>, LocalDate, LocalTime, LocalTime) -> Unit)? =
            if (move) { event, date, start, end -> moves += "${event.id} $date $start-$end" } else null
        val onResize: ((CalendarEvent<String>, LocalTime, LocalTime) -> Unit)? =
            if (resize) { event, start, end -> resizes += "${event.id} $start-$end" } else null
        rule.setContent {
            state =
                rememberScheduleState(
                    firstVisibleDate = jul15,
                    firstDayOfWeek = DayOfWeek.MONDAY,
                    initialScrollTime = LocalTime(8, 0),
                )
            MaterialTheme {
                val modifier = Modifier.testTag("schedule").fillMaxWidth().height(400.dp)
                if (week) {
                    WeekSchedule(
                        events = { date -> events[date].orEmpty() },
                        modifier = modifier,
                        state = state,
                        today = jul15,
                        onEmptySlotLongPress = onLongPress,
                        onSlotCreate = onCreate,
                        onEventMove = onMove,
                        onEventResize = onResize,
                        snapMinutes = snapMinutes,
                        dayHeader = null,
                    )
                } else {
                    DaySchedule(
                        events = { date -> events[date].orEmpty() },
                        modifier = modifier,
                        state = state,
                        today = jul15,
                        onEmptySlotLongPress = onLongPress,
                        onSlotCreate = onCreate,
                        onEventMove = onMove,
                        onEventResize = onResize,
                        snapMinutes = snapMinutes,
                        dayHeader = null,
                    )
                }
            }
        }
        rule.waitForIdle()
    }

    /** A point in the single day column at [minutesAfter8] minutes after 08:00. */
    private fun dayPoint(minutesAfter8: Int): Offset =
        with(rule.density) { Offset(150.dp.toPx(), minutesAfter8.dp.toPx()) }

    private fun dp(value: Int): Float = with(rule.density) { value.dp.toPx() }

    private fun hasCustomAction(label: String) =
        SemanticsMatcher("has custom action '$label'") { node ->
            node.config.getOrNull(SemanticsActions.CustomActions)?.any { it.label == label } == true
        }

    /** Runs the custom action [label]; [tileTitle] picks one tile when several tiles carry the same action. */
    private fun performCustomAction(
        label: String,
        tileTitle: String? = null,
    ) {
        val matcher = if (tileTitle == null) hasCustomAction(label) else hasCustomAction(label) and hasText(tileTitle)
        val node = rule.onNode(matcher).fetchSemanticsNode()
        val action = node.config[SemanticsActions.CustomActions].first { it.label == label }
        rule.runOnUiThread { action.action() }
        rule.waitForIdle()
    }

    @Test
    fun longPressOnEmptySlotReportsTheSnappedTime() {
        schedule(longPress = true)
        rule.onNodeWithTag("schedule").performTouchInput {
            down(dayPoint(140))
            advanceEventTime(1000)
            up()
        }
        rule.runOnIdle { assertEquals(listOf(jul15 to LocalTime(10, 15)), longPresses) }
    }

    @Test
    fun longPressThenDragCreatesTheDraggedRange() {
        schedule(longPress = true, create = true)
        rule.onNodeWithTag("schedule").performTouchInput {
            down(dayPoint(140))
            advanceEventTime(1000)
            moveBy(Offset(0f, dp(25)))
            moveBy(Offset(0f, dp(25)))
            up()
        }
        rule.runOnIdle {
            assertEquals(listOf(Triple(jul15, LocalTime(10, 15), LocalTime(11, 15))), created)
            assertTrue(longPresses.isEmpty())
        }
    }

    @Test
    fun longPressWithoutMovingFiresOnlyTheLongPressCallback() {
        schedule(longPress = true, create = true)
        rule.onNodeWithTag("schedule").performTouchInput {
            down(dayPoint(140))
            advanceEventTime(1000)
            up()
        }
        rule.runOnIdle {
            assertEquals(listOf(jul15 to LocalTime(10, 15)), longPresses)
            assertTrue(created.isEmpty())
        }
    }

    @Test
    fun longPressWithoutMovingCreatesOneHourWhenOnlyCreateIsSet() {
        schedule(create = true)
        rule.onNodeWithTag("schedule").performTouchInput {
            down(dayPoint(140))
            advanceEventTime(1000)
            up()
        }
        rule.runOnIdle { assertEquals(listOf(Triple(jul15, LocalTime(10, 15), LocalTime(11, 15))), created) }
    }

    @Test
    fun dragShowsTheGhostWithItsTimeChip() {
        schedule(create = true)
        rule.onNodeWithTag("schedule").performTouchInput {
            down(dayPoint(140))
            advanceEventTime(1000)
            moveBy(Offset(0f, dp(25)))
            moveBy(Offset(0f, dp(25)))
        }
        rule.onNodeWithText("10:15 – 11:15").assertExists()
        rule.onNodeWithTag("schedule").performTouchInput { up() }
        rule.onNodeWithText("10:15 – 11:15").assertDoesNotExist()
    }

    @Test
    fun mousePressAndDragCreatesWithoutLongPress() {
        schedule(create = true)
        rule.onNodeWithTag("schedule").performMouseInput {
            moveTo(dayPoint(140))
            press()
            moveBy(Offset(0f, dp(20)))
            moveBy(Offset(0f, dp(20)))
            release()
        }
        rule.runOnIdle { assertEquals(listOf(Triple(jul15, LocalTime(10, 15), LocalTime(11, 0))), created) }
    }

    @Test
    fun swipeOnEmptySpaceStillScrolls() {
        schedule(longPress = true, create = true, move = true, resize = true)
        val before = state.verticalScrollState.value
        rule.onNodeWithTag("schedule").performTouchInput {
            swipeUp(startY = dp(300), endY = dp(100))
        }
        rule.runOnIdle {
            assertTrue(state.verticalScrollState.value > before)
            assertTrue(longPresses.isEmpty() && created.isEmpty() && moves.isEmpty() && resizes.isEmpty())
        }
    }

    @Test
    fun longPressDragMovesAnEventKeepingItsDuration() {
        schedule(move = true)
        rule.onNodeWithTag("schedule").performTouchInput {
            down(dayPoint(75))
            advanceEventTime(1000)
            moveBy(Offset(0f, dp(45)))
            moveBy(Offset(0f, dp(45)))
            up()
        }
        rule.runOnIdle { assertEquals(listOf("standup 2026-07-15 10:30-11:00"), moves) }
    }

    @Test
    fun dropInPlaceFiresNothing() {
        schedule(move = true)
        rule.onNodeWithTag("schedule").performTouchInput {
            down(dayPoint(75))
            advanceEventTime(1000)
            up()
        }
        rule.runOnIdle { assertTrue(moves.isEmpty()) }
    }

    @Test
    fun moveSnapsToTheConfiguredStep() {
        schedule(move = true, snapMinutes = 30)
        rule.onNodeWithTag("schedule").performTouchInput {
            down(dayPoint(75))
            advanceEventTime(1000)
            moveBy(Offset(0f, dp(20)))
            moveBy(Offset(0f, dp(20)))
            up()
        }
        rule.runOnIdle { assertEquals(listOf("standup 2026-07-15 09:30-10:00"), moves) }
    }

    @Test
    fun weekDragToTheNextColumnChangesTheDate() {
        schedule(week = true, move = true)
        rule.onNodeWithTag("schedule").performTouchInput {
            val columnWidth = (width - dp(56)) / 7f
            // Monday-first week: 15 July 2026 is the third column.
            down(Offset(dp(56) + columnWidth * 2.5f, dp(75)))
            advanceEventTime(1000)
            moveBy(Offset(columnWidth / 2f, 0f))
            moveBy(Offset(columnWidth / 2f, 0f))
            up()
        }
        rule.runOnIdle { assertEquals(listOf("standup $jul16 09:00-09:30"), moves) }
    }

    @Test
    fun draggingTheBottomHandleResizesTheEnd() {
        schedule(resize = true)
        val before = state.verticalScrollState.value
        rule.onNodeWithTag("schedule").performTouchInput {
            // Review is 11:00-12:00: its end handle zone is the bottom third around the centre.
            down(Offset((dp(56) + width) / 2f, dp(235)))
            moveBy(Offset(0f, dp(15)))
            moveBy(Offset(0f, dp(15)))
            up()
        }
        rule.runOnIdle {
            assertEquals(listOf("review 11:00-12:30"), resizes)
            assertEquals(before, state.verticalScrollState.value)
        }
    }

    @Test
    fun draggingTheTopHandleResizesTheStart() {
        schedule(resize = true)
        rule.onNodeWithTag("schedule").performTouchInput {
            down(Offset((dp(56) + width) / 2f, dp(185)))
            moveBy(Offset(0f, dp(-15)))
            moveBy(Offset(0f, dp(-15)))
            up()
        }
        rule.runOnIdle { assertEquals(listOf("review 10:30-12:00"), resizes) }
    }

    @Test
    fun altArrowsMoveAndResizeTheFocusedTile() {
        schedule(move = true, resize = true)
        val tile = rule.onNode(hasCustomAction("Move later") and hasText("Standup"))
        tile.requestFocus()
        tile.performKeyInput { withKeyDown(Key.AltLeft) { pressKey(Key.DirectionDown) } }
        tile.performKeyInput {
            withKeyDown(Key.AltLeft) { withKeyDown(Key.ShiftLeft) { pressKey(Key.DirectionDown) } }
        }
        rule.runOnIdle {
            assertEquals(listOf("standup 2026-07-15 09:15-09:45"), moves)
            assertEquals(listOf("standup 09:00-09:45"), resizes)
        }
    }

    @Test
    fun customActionsRunTheSameEdits() {
        schedule(longPress = true, move = true, resize = true)
        performCustomAction("Move earlier", tileTitle = "Standup")
        performCustomAction("Extend end", tileTitle = "Standup")
        performCustomAction("Create event at 08:00")
        rule.runOnIdle {
            assertEquals(listOf("standup 2026-07-15 08:45-09:15"), moves)
            assertEquals(listOf("standup 09:00-09:45"), resizes)
            assertEquals(listOf(jul15 to LocalTime(8, 0)), longPresses)
        }
    }

    @Test
    fun readOnlyScheduleAddsNoEditingSemantics() {
        schedule()
        rule.onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsActions.CustomActions)).assertCountEquals(0)
    }
}
