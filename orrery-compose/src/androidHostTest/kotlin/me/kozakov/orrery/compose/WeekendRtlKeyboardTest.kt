package me.kozakov.orrery.compose

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.LayoutDirection
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.YearMonth
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** #103: Weekend styling, RTL mirroring helpers, and keyboard navigation parity. */
@RunWith(AndroidJUnit4::class)
class WeekendRtlKeyboardTest {
    @get:Rule
    val rule = createComposeRule()

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
    fun verticalAndWeekCalendarsAcceptKeyboardNavigationFlag() {
        rule.setContent {
            MaterialTheme {
                VerticalCalendar(
                    state =
                        rememberCalendarState(
                            startMonth = YearMonth(2026, 7),
                            endMonth = YearMonth(2026, 8),
                            firstVisibleMonth = YearMonth(2026, 7),
                        ),
                    keyboardNavigation = true,
                    dayContent = { DefaultDay(day = it) },
                )
                WeekCalendar(
                    state =
                        rememberWeekCalendarState(
                            startDate = LocalDate(2026, 7, 1),
                            endDate = LocalDate(2026, 7, 31),
                            firstVisibleDate = LocalDate(2026, 7, 6),
                        ),
                    visibleWeeksCount = 2,
                    keyboardNavigation = true,
                    dayContent = { DefaultDay(day = it) },
                )
            }
        }
        rule.waitForIdle()
    }
}
