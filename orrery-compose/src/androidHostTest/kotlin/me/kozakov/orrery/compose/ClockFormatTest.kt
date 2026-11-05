package me.kozakov.orrery.compose

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import me.kozakov.orrery.core.CalendarEvent
import me.kozakov.orrery.core.EventSpan
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

// Native graphics for real text widths; en-US with "Use 24-hour format" off: times read like "12:30 PM".
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "en-rUS")
class ClockFormatTest {
    @get:Rule val rule = createComposeRule()

    private val day = LocalDate(2026, 7, 15)
    private val lunch =
        CalendarEvent<Nothing>(
            id = "l",
            title = "Lunch",
            span = EventSpan.Timed(LocalDateTime(2026, 7, 15, 12, 30), LocalDateTime(2026, 7, 15, 13, 30)),
        )

    @Before
    fun use12HourClock() = setSystem24HourClock(false)

    // CLDR may put a narrow no-break space before "PM", so match the parts separately.
    private fun clock(time: String) = hasText(time, substring = true) and hasText("PM", substring = true)

    @Test
    fun agendaRowFollowsTheDeviceClock() {
        rule.setContent { MaterialTheme { AgendaDefaults.EventRow(lunch, date = day) } }
        rule.onNode(clock("12:30"), useUnmergedTree = true).assertExists()
    }

    @Test
    fun scheduleTileAndHourLabelsFollowTheDeviceClock() {
        rule.setContent {
            MaterialTheme {
                DaySchedule(
                    events = { if (it == day) listOf(lunch) else emptyList() },
                    modifier = Modifier.fillMaxWidth().height(600.dp),
                    state =
                        rememberScheduleState(
                            firstVisibleDate = day,
                            firstDayOfWeek = DayOfWeek.MONDAY,
                            initialScrollTime = LocalTime(11, 0),
                        ),
                    today = day,
                )
            }
        }
        rule.onNode(clock("1:00") and !hasText("11:00", substring = true), useUnmergedTree = true).assertExists()
        rule.onNode(clock("12:30"), useUnmergedTree = true).assertExists()
    }

    @Test
    fun explicit24HourOverridesTheDevice() {
        rule.setContent { MaterialTheme { AgendaDefaults.EventRow(lunch, date = day, is24Hour = true) } }
        rule.onNodeWithText("12:30 – 13:30", useUnmergedTree = true).assertExists()
    }

    @Test
    fun twelveHourAgendaRangeBreaksAfterTheDashInANarrowColumn() {
        rule.setContent {
            MaterialTheme { AgendaDefaults.EventRow(lunch, modifier = Modifier.testTag("row"), date = day) }
        }
        val time = rule.onNode(clock("12:30"), useUnmergedTree = true)
        val bounds = time.getUnclippedBoundsInRoot()
        assertTrue(
            "time column ${bounds.right - bounds.left} is wider than 128dp",
            bounds.right - bounds.left <= 128.dp,
        )
        val layouts = mutableListOf<TextLayoutResult>()
        time.performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
        val layout = layouts.single()
        val firstLine =
            layout.layoutInput.text.text
                .substring(0, layout.getLineEnd(0))
                .trim()
        assertTrue("first line '$firstLine' should end at the dash", firstLine.endsWith("–"))
    }

    @Test
    fun widenedGutterKeepsSpaceBeforeTheHourLabels() {
        rule.setContent {
            MaterialTheme {
                DaySchedule(
                    events = { emptyList<CalendarEvent<Nothing>>() },
                    modifier = Modifier.fillMaxWidth().height(600.dp).testTag("schedule"),
                    state =
                        rememberScheduleState(
                            firstVisibleDate = day,
                            firstDayOfWeek = DayOfWeek.MONDAY,
                            initialScrollTime = LocalTime(9, 0),
                        ),
                    today = day,
                    dayHeader = null,
                )
            }
        }
        val start = rule.onNodeWithTag("schedule").getUnclippedBoundsInRoot().left
        val label =
            rule.onNode(
                hasText("10:00", substring = true) and hasText("AM", substring = true),
                useUnmergedTree = true,
            )
        val left = label.getUnclippedBoundsInRoot().left
        val layouts = mutableListOf<TextLayoutResult>()
        label.performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
        // The Text fills the gutter and right-aligns, so the glyphs start at its line left.
        val glyphLeft = left + with(rule.density) { layouts.single().getLineLeft(0).toDp() }
        assertTrue("label starts ${glyphLeft - start} from the edge", glyphLeft - start >= 4.dp)
    }
}
