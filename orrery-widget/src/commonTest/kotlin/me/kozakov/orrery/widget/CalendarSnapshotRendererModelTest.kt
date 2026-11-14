@file:OptIn(ExperimentalOrreryApi::class)

package me.kozakov.orrery.widget

import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.YearMonth
import me.kozakov.orrery.ExperimentalOrreryApi
import me.kozakov.orrery.core.CalendarEvent
import me.kozakov.orrery.core.EventSpan
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CalendarSnapshotRendererModelTest {
    private val day14 = LocalDate(2026, 11, 14)

    // One agenda day with one row, for fitAgenda.
    private val oneRow = packAgenda(listOf(CalendarEvent<Nothing>("a", "A", EventSpan.AllDay(day14))), day14, 1, 4)

    @Test
    fun roomyAgendaKeepsTheRequestedSizes() {
        val plan = planAgendaText(200f, hasRows = true, request())
        assertTrue(plan.showTitle)
        assertEquals(listOf(14f, 10f, 12f), listOf(plan.titleTextPx, plan.headingTextPx, plan.rowTextPx))
    }

    @Test
    fun agendaTextShrinksFirstThenDropsTheTitleThenKeepsItsFloor() {
        // Title, heading and one row need 19.6 + 14 + 18 = 51.6 px at the requested sizes.
        val shrunk = planAgendaText(40f, hasRows = true, request())
        assertTrue(shrunk.showTitle)
        assertTrue(shrunk.titleTextPx < 14f && shrunk.rowTextPx < 12f)
        assertTrue(shrunk.titleBand + shrunk.headingHeight + shrunk.rowHeight <= 40f)
        // At 28 px the three would go below their floors, so the title goes and the rest shrinks less.
        val noTitle = planAgendaText(28f, hasRows = true, request())
        assertFalse(noTitle.showTitle)
        assertTrue(noTitle.rowTextPx in 7.2f..12f && noTitle.headingTextPx in 6f..10f)
        assertTrue(noTitle.headingHeight + noTitle.rowHeight <= 28f)
        // Below the floor of the heading and row (19.2 px) the rows are dropped; the title still fits.
        val titleOnly = planAgendaText(18f, hasRows = true, request())
        assertTrue(titleOnly.showTitle)
        assertTrue(titleOnly.titleTextPx >= 8.4f && titleOnly.titleBand <= 18f)
        assertTrue(fitAgenda(oneRow, 18f - titleOnly.titleBand, titleOnly.headingHeight, titleOnly.rowHeight).isEmpty())
        assertFalse(planAgendaText(10f, hasRows = true, request()).showTitle)
    }

    @Test
    fun anEmptyAgendaSizesOnlyItsTitle() {
        assertEquals(14f, planAgendaText(30f, hasRows = false, request()).titleTextPx)
        val tight = planAgendaText(15f, hasRows = false, request())
        assertTrue(tight.showTitle && tight.titleTextPx < 14f && tight.titleBand <= 15f)
        assertFalse(planAgendaText(10f, hasRows = false, request()).showTitle)
        assertFalse(planAgendaText(200f, hasRows = true, request(showTitle = false)).showTitle)
    }

    @Test
    fun agendaFontScaleIsHonouredButNeverDropsARowThatFitsAtFontScaleOne() {
        for (height in 1..300) {
            val unscaled = planAgendaText(height.toFloat(), hasRows = true, request())
            val scaledRequest = request(fontScale = 2f)
            val scaled = planAgendaText(height.toFloat(), hasRows = true, scaledRequest)
            assertEquals(rows(unscaled, height), rows(scaled, height), "height $height")
            if (rows(scaled, height) > 0) {
                assertTrue(scaled.rowTextPx >= scaledRequest.dayFloorPx && scaled.rowTextPx <= 24f, "height $height")
                assertTrue(scaled.headingTextPx >= scaledRequest.weekdayFloorPx, "height $height")
                assertTrue(scaled.rowTextPx >= unscaled.rowTextPx - 0.01f, "height $height: never smaller")
            }
            if (scaled.showTitle) assertTrue(scaled.titleTextPx >= scaledRequest.titleFloorPx, "height $height")
        }
        // At 300 px the font scale 2 strip shows its text at twice the size.
        assertEquals(24f, planAgendaText(300f, hasRows = true, request(fontScale = 2f)).rowTextPx)
    }

    @Test
    fun oversizedComposablesAreDownscaledIntoTheLimits() {
        assertEquals(1f, snapshotDownscale(100, 100))
        assertEquals(1f, snapshotDownscale(4096, 1024))
        listOf(4097 to 10, 3000 to 3000, 8000 to 8000, 4096 to 4096, 2049 to 2048).forEach { (width, height) ->
            val scale = snapshotDownscale(width, height)
            assertTrue(scale < 1f, "$width x $height")
            checkSnapshotSize((width * scale).toInt(), (height * scale).toInt())
        }
    }

    @Test
    fun sizeErrorsNameTheValues() {
        val side = assertFailsWith<IllegalArgumentException> { checkSnapshotSize(0, 330) }
        assertTrue("0 x 330" in side.message.orEmpty(), side.message)
        val tall = assertFailsWith<IllegalArgumentException> { checkSnapshotSize(10, 4097) }
        assertTrue("10 x 4097" in tall.message.orEmpty(), tall.message)
        val area = assertFailsWith<IllegalArgumentException> { checkSnapshotSize(2049, 2048) }
        assertTrue("2049 x 2048 = 4196352" in area.message.orEmpty(), area.message)
    }

    @Test
    fun theContentDescriptionIsTheTitleOrTheSpokenRange() {
        val month = CalendarSnapshotSpec(CalendarSnapshotRange.Month(YearMonth(2026, 11)))
        assertEquals("2026-11", snapshotDescription(month, FixedSnapshotFormatter))
        val week = CalendarSnapshotSpec(CalendarSnapshotRange.Week(day14), firstDayOfWeek = DayOfWeek.SUNDAY)
        assertEquals("2026-11-08 to 2026-11-14", snapshotDescription(week, FixedSnapshotFormatter))
        val agenda = CalendarSnapshotSpec(CalendarSnapshotRange.Agenda(day14, days = 3))
        assertEquals("2026-11-14 to 2026-11-16", snapshotDescription(agenda, FixedSnapshotFormatter))
        assertEquals("2026-11-14", dateRangeText(day14, day14) { it.toString() })
        assertEquals("a \u2013 b", dateRangeText(day14, LocalDate(2026, 11, 15)) { if (it == day14) "a" else "b" })
    }

    private fun rows(
        plan: AgendaTextPlan,
        height: Int,
    ): Int = fitAgenda(oneRow, height - plan.titleBand, plan.headingHeight, plan.rowHeight).sumOf { it.rows.size }

    private fun request(
        showTitle: Boolean = true,
        fontScale: Float = 1f,
    ): GridTextRequest =
        GridTextRequest(14f * fontScale, 10f * fontScale, 12f * fontScale, 0.6f, showTitle, true, fontScale)
}
