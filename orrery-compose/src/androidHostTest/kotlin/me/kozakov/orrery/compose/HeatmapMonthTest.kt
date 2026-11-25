package me.kozakov.orrery.compose

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHasNoClickAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.YearMonth
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.format.TextStyle
import java.util.Locale
import java.time.DayOfWeek as JavaDayOfWeek

@RunWith(AndroidJUnit4::class)
class HeatmapMonthTest {
    @get:Rule val rule = createComposeRule()

    private fun intensity(date: LocalDate): Float? = if (date.day % 5 == 0) null else (date.day % 5) / 4f

    private fun fullDate(date: LocalDate) = PlatformDateFormat.formatDate(date, DateFormattingStyle.Full)

    private fun content(
        onDayClick: ((LocalDate) -> Unit)? = null,
        showDayNumbers: Boolean = false,
        showLegend: Boolean = false,
    ) {
        rule.setContent {
            MaterialTheme {
                HeatmapMonth(
                    yearMonth = YearMonth(2026, 7),
                    intensity = ::intensity,
                    onDayClick = onDayClick,
                    showDayNumbers = showDayNumbers,
                    firstDayOfWeek = DayOfWeek.MONDAY,
                    showLegend = showLegend,
                )
            }
        }
    }

    @Test
    fun clickReportsDate() {
        var clicked: LocalDate? = null
        content(onDayClick = { clicked = it })
        rule.onNodeWithContentDescription("${fullDate(LocalDate(2026, 7, 8))}, level 4 of 5").performClick()
        rule.runOnIdle { assertEquals(LocalDate(2026, 7, 8), clicked) }
    }

    @Test
    fun outDatesAreNotClickable() {
        content(onDayClick = {})
        rule.onAllNodesWithText("29").assertCountEquals(0)
        rule.onNodeWithContentDescription("${fullDate(LocalDate(2026, 6, 29))}, no data").assertDoesNotExist()
    }

    @Test
    fun dayNumbersToggleOn() {
        content(showDayNumbers = true)
        rule.onAllNodesWithText("15").assertCountEquals(1)
    }

    @Test
    fun cellsNotClickableWithoutCallback() {
        content(onDayClick = null)
        rule.onNodeWithContentDescription("${fullDate(LocalDate(2026, 7, 8))}, level 4 of 5").assertHasNoClickAction()
    }

    @Test
    fun cellDescriptionIncludesTheWeekday() {
        // The weekday header is hidden from accessibility (#89 task 13, S3), so each cell says it.
        content()
        val weekday = JavaDayOfWeek.WEDNESDAY.getDisplayName(TextStyle.FULL, Locale.getDefault())
        val description =
            rule
                .onNodeWithContentDescription("July 8, 2026", substring = true)
                .fetchSemanticsNode()
                .config[SemanticsProperties.ContentDescription]
                .single()
        assertTrue("\"$description\" should name the weekday \"$weekday\"", description.contains(weekday))
        assertEquals("${fullDate(LocalDate(2026, 7, 8))}, level 4 of 5", description)
    }

    @Test
    fun legendIsOffByDefault() {
        content()
        rule.onAllNodesWithText("Less").assertCountEquals(0)
        rule.onAllNodesWithText("More").assertCountEquals(0)
    }

    @Test
    fun legendShowsLessAndMore() {
        content(showLegend = true)
        rule.onAllNodesWithText("Less").assertCountEquals(1)
        rule.onAllNodesWithText("More").assertCountEquals(1)
    }
}
