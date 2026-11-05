package me.kozakov.orrery.compose

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.YearMonth
import me.kozakov.orrery.core.CalendarDay
import me.kozakov.orrery.core.CalendarEvent
import me.kozakov.orrery.core.CalendarWeek
import me.kozakov.orrery.core.DayPosition
import me.kozakov.orrery.core.EventSpan
import me.kozakov.orrery.core.PositionedEvent
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.GraphicsMode
import kotlin.math.abs

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class AuditPolishTest {
    @get:Rule val rule = createComposeRule()

    private fun pixel(
        tag: String,
        x: Dp,
        y: Dp,
    ): Color {
        val map = rule.onNodeWithTag(tag).captureToImage().toPixelMap()
        val (px, py) = with(rule.density) { x.roundToPx() to y.roundToPx() }
        return map[px.coerceAtMost(map.width - 1), py.coerceAtMost(map.height - 1)]
    }

    private fun assertNear(
        expected: Color,
        actual: Color,
        tolerance: Float = 0.06f,
    ) {
        val close =
            abs(expected.red - actual.red) <= tolerance &&
                abs(expected.green - actual.green) <= tolerance &&
                abs(expected.blue - actual.blue) <= tolerance
        assertTrue("expected ~$expected but was $actual", close)
    }

    @Test
    fun progressRingLeavesRoomForTheCellOutline() {
        rule.setContent {
            MaterialTheme {
                val ring = DayDecorators.progressRing(color = Color.Red, trackColor = Color.Transparent) { 1f }
                Box(
                    Modifier.testTag("cell").size(48.dp).background(Color.White).drawBehind {
                        drawDecorator(
                            ring,
                            CalendarDay(LocalDate(2026, 7, 8), DayPosition.MonthDate),
                            DayCellDrawState.Unknown,
                        )
                    },
                )
            }
        }
        // Stroke 2dp, inset 3dp: the ring spans y = 3..5dp at the top; the outer 0..2dp stays free for the outline.
        assertNear(Color.White, pixel("cell", 24.dp, 1.dp))
        assertNear(Color.Red, pixel("cell", 24.dp, 4.dp))
    }

    @Test
    fun fullCellTurnoverDayIsFilled() {
        var inRange = Color.Unspecified
        rule.setContent {
            MaterialTheme {
                val style = CalendarDefaults.hospitalityStyle(rangeEndpointStyle = RangeEndpointStyle.FullCell)
                inRange = style.colors.inRangeContainerColor
                Box(Modifier.testTag("cell").size(48.dp).background(Color.White)) {
                    DefaultDay(
                        day = CalendarDay(LocalDate(2026, 7, 14), DayPosition.MonthDate),
                        style = style,
                        turnoverDates = setOf(LocalDate(2026, 7, 14)),
                        today = LocalDate(2026, 7, 1),
                    )
                }
            }
        }
        val fill = rule.runOnIdle { inRange }
        val expected = fill.copy(alpha = 1f).compositeOverWhite(fill.alpha)
        // Two corners on opposite sides of the divider, away from the day number.
        assertNear(expected, pixel("cell", 6.dp, 6.dp))
        assertNear(expected, pixel("cell", 42.dp, 42.dp))
    }

    private fun Color.compositeOverWhite(alpha: Float) =
        Color(red * alpha + (1 - alpha), green * alpha + (1 - alpha), blue * alpha + (1 - alpha))

    @Test
    fun shortScheduleCapsAllDayRows() {
        val day = LocalDate(2026, 7, 15)
        val allDay =
            (1..3).map {
                CalendarEvent<Nothing>(
                    id = "a$it",
                    title = "Trip $it",
                    span = EventSpan.AllDay(day),
                )
            }
        rule.setContent {
            MaterialTheme {
                DaySchedule(
                    events = { if (it == day) allDay else emptyList() },
                    modifier = Modifier.fillMaxWidth().height(200.dp),
                    state = rememberScheduleState(firstVisibleDate = day, firstDayOfWeek = DayOfWeek.MONDAY),
                    today = day,
                    dayHeader = null,
                )
            }
        }
        // 200dp / 4 = 50dp fits 2 rows of 20dp + 2dp gap: one chip and a "+2" overflow.
        rule.onNodeWithText("+2", useUnmergedTree = true).assertExists()
        rule.onNodeWithText("Trip 3", useUnmergedTree = true).assertDoesNotExist()
    }

    @Test
    fun yearGridOutlinesTheCurrentYear() {
        var primary = Color.Unspecified
        rule.setContent {
            MaterialTheme {
                primary = MaterialTheme.colorScheme.primary
                MonthYearPicker(
                    current = YearMonth(2026, 7),
                    range = YearMonth(2024, 1)..YearMonth(2028, 12),
                    onSelect = {},
                    modifier = Modifier.testTag("picker"),
                )
            }
        }
        val year = rule.onNode(hasText("2026"), useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        val picker = rule.onNodeWithTag("picker").fetchSemanticsNode().boundsInRoot
        val map = rule.onNodeWithTag("picker").captureToImage().toPixelMap()
        // Scan left from just outside the year text (skipping its anti-aliased edge) to the outline.
        val y = (year.center.y - picker.top).toInt()
        // The year grid has 3 columns; stay inside the 2026 cell.
        val columnWidth = (picker.right - picker.left) / 3
        val cellLeft = (((year.center.x - picker.left) / columnWidth).toInt() * columnWidth).toInt()
        val background = map[1, y]
        val outline =
            ((year.left - picker.left).toInt() - GLYPH_EDGE_PX downTo cellLeft)
                .map { map[it, y] }
                .firstOrNull { abs(it.red - background.red) + abs(it.green - background.green) > 0.1f }
        assertTrue("no outline left of 2026", outline != null)
        assertNear(rule.runOnIdle { primary }, outline!!, tolerance = 0.15f)
    }

    @Test
    fun bannerOnlyOnOutOfMonthDaysIsDimmed() {
        // Mon Jul 27 – Sun Aug 2, July grid: Aug 1–2 are out-of-month.
        val week =
            CalendarWeek(
                (27..31).map { CalendarDay(LocalDate(2026, 7, it), DayPosition.MonthDate) } +
                    (1..2).map { CalendarDay(LocalDate(2026, 8, it), DayPosition.OutDate) },
            )
        val trip =
            CalendarEvent<Nothing>(
                id = "t",
                title = "",
                span = EventSpan.AllDay(LocalDate(2026, 8, 1), LocalDate(2026, 8, 2)),
                colorArgb = 0xFFE53935,
            )
        rule.setContent {
            MaterialTheme {
                Box(Modifier.testTag("row").size(350.dp, 40.dp).background(Color.White)) {
                    WeekEventBanners(
                        week = week,
                        events = listOf(trip),
                        modifier = Modifier.fillMaxWidth(),
                        topInset = 0.dp,
                    )
                }
            }
        }
        // Column 5 of 7 starts at 250dp; sample the lane's middle. Full red has green ≈ 0.22; at 38% over white ≈ 0.70.
        val green = pixel("row", 300.dp, 9.dp).green
        assertTrue("banner green $green", green > 0.6f)
    }

    @Test
    fun narrowTileShowsOnlyTheStartTimeWhenTheRangeDoesNotFit() {
        val review =
            PositionedEvent(
                event =
                    CalendarEvent<Nothing>(
                        id = "r",
                        title = "Code review",
                        span =
                            EventSpan.Timed(
                                LocalDateTime(2026, 7, 15, 9, 15),
                                LocalDateTime(2026, 7, 15, 10, 15),
                            ),
                    ),
                date = LocalDate(2026, 7, 15),
                startMinute = 9 * 60 + 15,
                endMinute = 10 * 60 + 15,
                displayEndMinute = 10 * 60 + 15,
                columnIndex = 0,
                columnCount = 1,
                leftFraction = 0f,
                widthFraction = 1f,
            )
        rule.setContent {
            MaterialTheme {
                Box(Modifier.size(64.dp, 80.dp)) {
                    ScheduleDefaults.EventTile(review, ScheduleDefaults.colors(), is24Hour = false)
                }
            }
        }
        // "9:15 AM – 10:15 AM" can't fit 64dp, so the tile shows its start time alone, never a cut-off range.
        rule.onNode(hasText("9:15", substring = true), useUnmergedTree = true).assertExists()
        rule.onNode(hasText("–", substring = true), useUnmergedTree = true).assertDoesNotExist()
    }
}

private const val GLYPH_EDGE_PX = 3
