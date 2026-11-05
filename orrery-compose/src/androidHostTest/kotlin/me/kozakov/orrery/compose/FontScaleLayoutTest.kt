package me.kozakov.orrery.compose

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertIsNotDisplayed
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.YearMonth
import me.kozakov.orrery.core.CalendarDay
import me.kozakov.orrery.core.CalendarEvent
import me.kozakov.orrery.core.DayInfo
import me.kozakov.orrery.core.DayPosition
import me.kozakov.orrery.core.EventSpan
import me.kozakov.orrery.core.packWeekEventBanners
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

// Large system text (1.3×) with real font metrics.
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(fontScale = 1.3f)
class FontScaleLayoutTest {
    @get:Rule val rule = createComposeRule()

    private fun SemanticsNodeInteraction.lineCount(): Int {
        val results = mutableListOf<TextLayoutResult>()
        performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(results) }
        return results.single().lineCount
    }

    @Test
    fun labelledDayLeavesRoomForDecoratorsBelowTheLabel() {
        rule.setContent {
            MaterialTheme {
                DefaultDay(
                    day = CalendarDay(LocalDate(2026, 7, 8), DayPosition.MonthDate),
                    modifier = Modifier.width(52.dp).testTag("cell"),
                    today = LocalDate(2026, 7, 15),
                    info = DayInfo(label = "廿四"),
                    decorators = listOf(DayDecorators.eventDots { listOf(Color.Red) }),
                )
            }
        }
        val cell = rule.onNodeWithTag("cell").getUnclippedBoundsInRoot()
        val label = rule.onNodeWithText("廿四", useUnmergedTree = true).getUnclippedBoundsInRoot()
        assertTrue(
            "label bottom ${label.bottom}, cell bottom ${cell.bottom}",
            cell.bottom - label.bottom >= 5.9.dp,
        )
    }

    private fun scaffold(anchor: CollapsibleCalendarAnchor) {
        rule.setContent {
            MaterialTheme {
                CollapsibleCalendarScaffold(
                    calendarState =
                        rememberCalendarState(
                            startMonth = YearMonth(2026, 7),
                            endMonth = YearMonth(2026, 7),
                            firstVisibleMonth = YearMonth(2026, 7),
                            firstDayOfWeek = DayOfWeek.MONDAY,
                        ),
                    weekState =
                        rememberWeekCalendarState(
                            startDate = LocalDate(2026, 7, 1),
                            endDate = LocalDate(2026, 7, 31),
                            firstVisibleDate = LocalDate(2026, 7, 13),
                            firstDayOfWeek = DayOfWeek.MONDAY,
                        ),
                    state = rememberCollapsibleCalendarState(initialAnchor = anchor, enableTwoWeekStop = true),
                    modifier = Modifier.testTag("scaffold"),
                    // Label plus a decorator: rows grow past the 48dp dayHeight, as in the sample's Almanac.
                    dayContent = { day ->
                        DefaultDay(
                            day = day,
                            today = LocalDate(2026, 7, 15),
                            info = DayInfo(label = "初一"),
                            decorators = listOf(DayDecorators.eventDots { listOf(Color.Red) }),
                        )
                    },
                ) {
                    Box(Modifier.fillMaxWidth().height(10.dp).testTag("below"))
                }
            }
        }
    }

    private fun cell(description: String) =
        rule.onAllNodes(hasContentDescription(description, substring = true)).onFirst()

    @Test
    fun expandedScaffoldShowsTheLastRowUnclipped() {
        scaffold(CollapsibleCalendarAnchor.Month)
        val lastCell = cell("July 31, 2026").getUnclippedBoundsInRoot()
        val below = rule.onNodeWithTag("below").getUnclippedBoundsInRoot()
        assertTrue(
            "last row ends ${lastCell.bottom}, content starts ${below.top}",
            lastCell.bottom <= below.top + 0.5.dp,
        )
    }

    @Test
    fun twoWeekAnchorShowsExactlyTheAnchorRowAndItsNeighbour() {
        scaffold(CollapsibleCalendarAnchor.TwoWeeks)
        val top = rule.onNodeWithTag("scaffold").getUnclippedBoundsInRoot().top
        val anchorCell = cell("July 13, 2026").getUnclippedBoundsInRoot()
        val nextCell = cell("July 20, 2026").getUnclippedBoundsInRoot()
        val below = rule.onNodeWithTag("below").getUnclippedBoundsInRoot()
        assertTrue("anchor row top ${anchorCell.top} is above $top", anchorCell.top >= top)
        assertTrue("second row ends ${nextCell.bottom} past ${below.top}", nextCell.bottom <= below.top + 0.5.dp)
        cell("July 27, 2026").assertIsNotDisplayed()
    }

    @Test
    fun bannerLaneIsAtLeastOneLineOfText() {
        val week = (6..12).map { LocalDate(2026, 7, it) }
        val gym = CalendarEvent<Nothing>(id = "g", title = "Gym", span = EventSpan.AllDay(LocalDate(2026, 7, 8)))
        rule.setContent {
            MaterialTheme {
                WeekEventBanners(
                    packResult = packWeekEventBanners(week, listOf(gym)),
                    modifier = Modifier.testTag("banners"),
                    topInset = 0.dp,
                )
            }
        }
        val lane = rule.onNodeWithTag("banners").getUnclippedBoundsInRoot()
        val layouts = mutableListOf<TextLayoutResult>()
        rule
            .onNodeWithText("Gym", useUnmergedTree = true)
            .performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
        // The chip clamps the Text node, so compare with the text's natural line height.
        val textHeight =
            with(rule.density) {
                layouts
                    .single()
                    .multiParagraph.height
                    .toDp()
            }
        val laneHeight = lane.bottom - lane.top
        assertTrue("lane $laneHeight < text $textHeight", laneHeight >= textHeight)
    }

    @Test
    fun agendaTimeLabelStaysOnOneLine() {
        val lunch =
            CalendarEvent<Nothing>(
                id = "l",
                title = "Lunch with Sam",
                span = EventSpan.Timed(LocalDateTime(2026, 7, 15, 12, 30), LocalDateTime(2026, 7, 15, 13, 30)),
            )
        rule.setContent {
            MaterialTheme { AgendaDefaults.EventRow(lunch, date = LocalDate(2026, 7, 15)) }
        }
        assertEquals(1, rule.onNodeWithText("12:30 – 13:30", useUnmergedTree = true).lineCount())
    }
}
