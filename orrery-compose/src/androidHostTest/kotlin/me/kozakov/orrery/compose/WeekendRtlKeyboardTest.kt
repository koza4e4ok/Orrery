package me.kozakov.orrery.compose

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.input.InputModeManager
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.LocalInputModeManager
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.KeyInjectionScope
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.requestFocus
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.YearMonth
import me.kozakov.orrery.core.CalendarDay
import me.kozakov.orrery.core.DayPosition
import me.kozakov.orrery.core.Selection
import me.kozakov.orrery.core.SelectionMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * #103: Weekend styling, RTL mirroring helpers, and keyboard navigation parity.
 *
 * Keyboard tests use Monday first-day: July 2026 has five rows (Jun 29 – Aug 2,
 * with Jul 12 and Jul 26 in the last column) and August six. The two-week
 * [WeekCalendar] starts on Monday Jul 6, so its first page shows Jul 6 – 19.
 */
@RunWith(AndroidJUnit4::class)
class WeekendRtlKeyboardTest {
    @get:Rule
    val rule = createComposeRule()

    private lateinit var inputModeManager: InputModeManager

    private fun labelColor(text: String): Color {
        val layouts = mutableListOf<TextLayoutResult>()
        val node = rule.onNodeWithText(text, useUnmergedTree = true).fetchSemanticsNode()
        node.config[SemanticsActions.GetTextLayoutResult].action?.invoke(layouts)
        return layouts[0].layoutInput.style.color
    }

    private fun weekdayLabel(day: DayOfWeek): String = PlatformDateFormat.weekdayName(day, narrow = false)

    /**
     * A 360dp-tall [VerticalCalendar] with 48dp rows and no week header:
     * July fits fully and August only peeks in below it.
     */
    private fun verticalContent(layoutDirection: LayoutDirection = LayoutDirection.Ltr): () -> CalendarState {
        lateinit var state: CalendarState
        rule.setContent {
            inputModeManager = LocalInputModeManager.current
            state =
                rememberCalendarState(
                    startMonth = YearMonth(2026, 7),
                    endMonth = YearMonth(2026, 12),
                    firstVisibleMonth = YearMonth(2026, 7),
                    firstDayOfWeek = DayOfWeek.MONDAY,
                )
            CompositionLocalProvider(LocalLayoutDirection provides layoutDirection) {
                MaterialTheme {
                    VerticalCalendar(
                        state = state,
                        modifier = Modifier.height(360.dp),
                        rowHeight = CalendarRowHeight.Fixed(48.dp),
                        weekHeader = null,
                        dayContent = { day ->
                            DefaultDay(
                                day = day,
                                // Out-dates repeat the neighbouring month's dates; tag month dates only.
                                modifier =
                                    if (day.position == DayPosition.MonthDate) {
                                        Modifier.testTag("day-${day.date}")
                                    } else {
                                        Modifier
                                    },
                                today = LocalDate(2026, 7, 1),
                                animateSelection = false,
                            )
                        },
                    )
                }
            }
        }
        rule.waitForIdle()
        return { state }
    }

    private fun weekContent(layoutDirection: LayoutDirection = LayoutDirection.Ltr): () -> WeekCalendarState {
        lateinit var state: WeekCalendarState
        rule.setContent {
            inputModeManager = LocalInputModeManager.current
            state =
                rememberWeekCalendarState(
                    startDate = LocalDate(2026, 7, 6),
                    endDate = LocalDate(2026, 9, 27),
                    firstVisibleDate = LocalDate(2026, 7, 6),
                    firstDayOfWeek = DayOfWeek.MONDAY,
                    visibleWeeksCount = 2,
                )
            CompositionLocalProvider(LocalLayoutDirection provides layoutDirection) {
                MaterialTheme {
                    WeekCalendar(
                        state = state,
                        visibleWeeksCount = 2,
                        dayContent = { day ->
                            DefaultDay(
                                day = day,
                                modifier = Modifier.testTag("day-${day.date}"),
                                today = LocalDate(2026, 7, 1),
                                animateSelection = false,
                            )
                        },
                    )
                }
            }
        }
        rule.waitForIdle()
        return { state }
    }

    /** Focuses [date]'s cell in keyboard input mode and sends [keys] to it. */
    @OptIn(ExperimentalComposeUiApi::class)
    private fun press(
        date: String,
        keys: KeyInjectionScope.() -> Unit,
    ) {
        rule.runOnIdle { inputModeManager.requestInputMode(InputMode.Keyboard) }
        rule.onNodeWithTag("day-$date").requestFocus()
        rule.onNodeWithTag("day-$date").performKeyInput(keys)
        rule.waitForIdle()
    }

    private fun assertFocused(date: String) {
        rule.onNodeWithTag("day-$date").assertIsFocused()
    }

    private fun WeekCalendarState.showsDate(date: LocalDate): Boolean =
        firstVisibleWeeks.any { week -> week.days.any { it.date == date } }

    @Test
    fun calendarDayColorsDefaultsToSaturdayAndSundayWeekends() {
        lateinit var colors: CalendarDayColors
        rule.setContent {
            MaterialTheme {
                colors =
                    CalendarDefaults.dayColors(
                        contentColor = Color.Black,
                        weekendContentColor = Color.Red,
                    )
            }
        }
        rule.waitForIdle()
        assertEquals(setOf(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY), colors.weekendDays)
        assertEquals(Color.Red, colors.weekendContentColor)
        assertEquals(Color.Black, colors.contentColor)
    }

    @Test
    fun horizontalArrowDayOffsetSwapsLeftAndRightInRtl() {
        assertEquals(-1, horizontalArrowDayOffset(Key.DirectionLeft, LayoutDirection.Ltr))
        assertEquals(1, horizontalArrowDayOffset(Key.DirectionRight, LayoutDirection.Ltr))
        assertEquals(1, horizontalArrowDayOffset(Key.DirectionLeft, LayoutDirection.Rtl))
        assertEquals(-1, horizontalArrowDayOffset(Key.DirectionRight, LayoutDirection.Rtl))
        assertEquals(null, horizontalArrowDayOffset(Key.DirectionUp, LayoutDirection.Rtl))
    }

    @Test
    fun rangeBandHalfBoundsMirrorsUnderRtl() {
        // LTR: towardPrevious is left half (0..50), towardNext is right half (50..100)
        assertEquals(0f to 50f, rangeBandHalfBounds(width = 100f, towardPrevious = true, isRtl = false))
        assertEquals(50f to 100f, rangeBandHalfBounds(width = 100f, towardPrevious = false, isRtl = false))
        // RTL: towardPrevious is right half (50..100), towardNext is left half (0..50)
        assertEquals(50f to 100f, rangeBandHalfBounds(width = 100f, towardPrevious = true, isRtl = true))
        assertEquals(0f to 50f, rangeBandHalfBounds(width = 100f, towardPrevious = false, isRtl = true))
    }

    @Test
    fun splitEndpointPolygonsMirrorsHorizontalAndDiagonalUnderRtl() {
        val (ltrAm, ltrPm) =
            splitEndpointPolygons(
                style = RangeEndpointStyle.SplitHorizontal,
                w = 100f,
                h = 40f,
                gap = 0f,
                isRtl = false,
            )
        val (rtlAm, rtlPm) =
            splitEndpointPolygons(
                style = RangeEndpointStyle.SplitHorizontal,
                w = 100f,
                h = 40f,
                gap = 0f,
                isRtl = true,
            )
        assertEquals(Offset(0f, 0f), ltrAm.first())
        assertEquals(Offset(100f, 0f), rtlAm.first())
        assertEquals(Offset(100f, 0f), ltrPm[1])
        assertEquals(Offset(0f, 0f), rtlPm[1])
    }

    @Test
    fun verticalCalendarTreatsAPeekingMonthAsNotVisible() {
        val state = verticalContent()
        rule.runOnIdle {
            val layoutInfo = state().listState.layoutInfo
            assertTrue(layoutInfo.visibleItemsInfo.any { it.index == 1 })
            assertTrue(state().isMonthVisible(YearMonth(2026, 7)))
            assertFalse(state().isMonthVisible(YearMonth(2026, 8)))
        }
    }

    @Test
    fun verticalCalendarRightOnTheLastColumnMovesToTheNextDayInLtr() {
        val state = verticalContent()
        press("2026-07-12") { pressKey(Key.DirectionRight) }
        assertFocused("2026-07-13")
        assertEquals(YearMonth(2026, 7), state().firstVisibleMonth)
    }

    @Test
    fun verticalCalendarLeftOnTheLastColumnMovesToTheNextDayInRtl() {
        val state = verticalContent(LayoutDirection.Rtl)
        press("2026-07-12") { pressKey(Key.DirectionLeft) }
        assertFocused("2026-07-13")
        assertEquals(YearMonth(2026, 7), state().firstVisibleMonth)
    }

    @Test
    fun verticalCalendarRightOnTheFirstColumnMovesToThePreviousDayInRtl() {
        verticalContent(LayoutDirection.Rtl)
        press("2026-07-13") { pressKey(Key.DirectionRight) }
        assertFocused("2026-07-12")
    }

    @Test
    fun verticalCalendarDownOnTheLastRowScrollsToAPeekingMonth() {
        val state = verticalContent()
        press("2026-07-28") { pressKey(Key.DirectionDown) }
        assertEquals(YearMonth(2026, 8), state().firstVisibleMonth)
        assertFocused("2026-08-04")
    }

    @Test
    fun verticalCalendarPageDownScrollsToTheNextMonthKeepingTheDay() {
        val state = verticalContent()
        press("2026-07-15") { pressKey(Key.PageDown) }
        assertEquals(YearMonth(2026, 8), state().firstVisibleMonth)
        assertFocused("2026-08-15")
    }

    @Test
    fun twoWeekCalendarRightOnTheLastColumnWrapsToTheSecondRowInLtr() {
        val state = weekContent()
        press("2026-07-12") { pressKey(Key.DirectionRight) }
        assertFocused("2026-07-13")
        assertTrue(state().showsDate(LocalDate(2026, 7, 6)))
    }

    @Test
    fun twoWeekCalendarLeftOnTheLastColumnWrapsToTheSecondRowInRtl() {
        val state = weekContent(LayoutDirection.Rtl)
        press("2026-07-12") { pressKey(Key.DirectionLeft) }
        assertFocused("2026-07-13")
        assertTrue(state().showsDate(LocalDate(2026, 7, 6)))
    }

    @Test
    fun twoWeekCalendarRightOnTheFirstColumnMovesToThePreviousDayInRtl() {
        weekContent(LayoutDirection.Rtl)
        press("2026-07-13") { pressKey(Key.DirectionRight) }
        assertFocused("2026-07-12")
    }

    @Test
    fun twoWeekCalendarDownOnTheLastRowPagesForward() {
        val state = weekContent()
        press("2026-07-15") { pressKey(Key.DirectionDown) }
        assertTrue(state().showsDate(LocalDate(2026, 7, 22)))
        assertFocused("2026-07-22")
    }

    @Test
    fun twoWeekCalendarPageDownStepsOneMonthForward() {
        val state = weekContent()
        press("2026-07-15") { pressKey(Key.PageDown) }
        assertTrue(state().showsDate(LocalDate(2026, 8, 15)))
        assertFocused("2026-08-15")
    }

    @Test
    fun weekHeaderColorsWeekendLabels() {
        rule.setContent {
            MaterialTheme {
                CalendarDefaults.WeekHeader(
                    daysOfWeek = listOf(DayOfWeek.FRIDAY, DayOfWeek.SATURDAY, DayOfWeek.SUNDAY),
                    contentColor = Color.Black,
                    weekendContentColor = Color.Red,
                )
            }
        }
        assertEquals(Color.Black, labelColor(weekdayLabel(DayOfWeek.FRIDAY)))
        assertEquals(Color.Red, labelColor(weekdayLabel(DayOfWeek.SATURDAY)))
        assertEquals(Color.Red, labelColor(weekdayLabel(DayOfWeek.SUNDAY)))
    }

    @Test
    fun daysOfWeekHeaderHonorsCustomWeekendDays() {
        rule.setContent {
            MaterialTheme {
                CalendarDefaults.DaysOfWeekHeader(
                    daysOfWeek = listOf(DayOfWeek.THURSDAY, DayOfWeek.FRIDAY, DayOfWeek.SATURDAY),
                    contentColor = Color.Black,
                    weekendDays = setOf(DayOfWeek.FRIDAY),
                    weekendContentColor = Color.Red,
                )
            }
        }
        assertEquals(Color.Black, labelColor(weekdayLabel(DayOfWeek.THURSDAY)))
        assertEquals(Color.Red, labelColor(weekdayLabel(DayOfWeek.FRIDAY)))
        assertEquals(Color.Black, labelColor(weekdayLabel(DayOfWeek.SATURDAY)))
    }

    @Test
    fun defaultDayWeekendColorYieldsToTodaySelectedAndOutDateColors() {
        rule.setContent {
            MaterialTheme {
                val colors =
                    CalendarDefaults.dayColors(
                        contentColor = Color.Black,
                        todayContentColor = Color.Blue,
                        selectedContentColor = Color.Green,
                        outDateContentColor = Color.Gray,
                        weekendContentColor = Color.Red,
                    )
                val selection =
                    rememberCalendarSelectionState(
                        mode = SelectionMode.Single(),
                        initialSelection = Selection(single = LocalDate(2026, 7, 18)),
                    )
                // Thursday, Saturday, Sunday today, selected Saturday, out-date Saturday.
                Column {
                    listOf(
                        CalendarDay(LocalDate(2026, 7, 9), DayPosition.MonthDate),
                        CalendarDay(LocalDate(2026, 7, 11), DayPosition.MonthDate),
                        CalendarDay(LocalDate(2026, 7, 12), DayPosition.MonthDate),
                        CalendarDay(LocalDate(2026, 7, 18), DayPosition.MonthDate),
                        CalendarDay(LocalDate(2026, 8, 1), DayPosition.OutDate),
                    ).forEach { day ->
                        DefaultDay(
                            day = day,
                            selectionState = selection,
                            today = LocalDate(2026, 7, 12),
                            colors = colors,
                            animateSelection = false,
                        )
                    }
                }
            }
        }
        assertEquals(Color.Black, labelColor("9"))
        assertEquals(Color.Red, labelColor("11"))
        assertEquals(Color.Blue, labelColor("12"))
        assertEquals(Color.Green, labelColor("18"))
        assertEquals(Color.Gray, labelColor("1"))
    }
}
