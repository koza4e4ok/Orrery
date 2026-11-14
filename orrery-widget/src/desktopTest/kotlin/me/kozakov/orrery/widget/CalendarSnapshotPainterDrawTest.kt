@file:OptIn(ExperimentalOrreryApi::class)

package me.kozakov.orrery.widget

import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.PixelMap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.text.font.createFontFamilyResolver
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.YearMonth
import me.kozakov.orrery.ExperimentalOrreryApi
import me.kozakov.orrery.core.CalendarEvent
import me.kozakov.orrery.core.EventSpan
import me.kozakov.orrery.core.Selection
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class CalendarSnapshotPainterDrawTest {
    private val resolver = createFontFamilyResolver()
    private val colors = CalendarSnapshotDefaults.colors(lightColorScheme())
    private val november = CalendarSnapshotRange.Month(YearMonth(2026, 11))

    @Test
    fun everyRangeDrawsMoreThanTheContainerAndDrawsTheSameTwice() {
        val events =
            listOf(
                CalendarEvent<Nothing>(
                    id = "a",
                    title = "Stand-up",
                    span = EventSpan.Timed(at(14, 9), at(14, 10)),
                    colorArgb = 0xFF2E7D32L,
                ),
                CalendarEvent<Nothing>("b", "Holiday", EventSpan.AllDay(LocalDate(2026, 11, 15))),
            )
        val specs =
            listOf(
                spec(november, selection = Selection(single = LocalDate(2026, 11, 18))),
                spec(CalendarSnapshotRange.Week(LocalDate(2026, 11, 14))),
                spec(CalendarSnapshotRange.Agenda(LocalDate(2026, 11, 14)), agendaEvents = events),
            )
        specs.forEach { spec ->
            val first = render(spec).pixels()
            val container = colors.container.toArgb()
            assertTrue(first.count { it != container && it != 0 } > 200, "${spec.range}")
            assertContentEquals(first, render(spec).pixels(), "${spec.range}")
        }
    }

    @Test
    fun selectionTodayAndRangeBandLandInTheirCells() {
        // 40 px cells: content 8..288 wide; title 19.6 px and weekday 14 px rows above six week rows.
        val range = Selection(rangeStart = LocalDate(2026, 11, 16), rangeEnd = LocalDate(2026, 11, 18))
        val ltr = render(spec(november, today = LocalDate(2026, 11, 25), selection = range)).toPixelMap()
        // 18 Nov: Wednesday, column 2, row 3 (centre 108, ~182); 25 Nov one row lower; 17 Nov is mid-range.
        assertEquals(colors.selection, ltr[96, 182])
        assertEquals(colors.today, ltr[96, 222])
        assertEquals(colors.rangeBand, ltr[53, 182])
        val single = spec(november, selection = Selection(single = LocalDate(2026, 11, 18)))
        val rtl = render(single, LayoutDirection.Rtl).toPixelMap()
        // Mirrored: column 2 is the fifth cell from the left (centre 188).
        assertEquals(colors.selection, rtl[176, 182])
        assertEquals(colors.container, rtl[96, 182])
    }

    @Test
    fun rtlRangeBandRunsBetweenItsEndsAndRoundsOnlyTheOuterEdges() {
        // Mirrored row 3: 16 Nov (start) is the rightmost cell (248..288), 17 Nov 208..248, 18 Nov (end) 168..208.
        val range = Selection(rangeStart = LocalDate(2026, 11, 16), rangeEnd = LocalDate(2026, 11, 18))
        val rtl = render(spec(november, selection = range), LayoutDirection.Rtl).toPixelMap()
        assertEquals(colors.selection, rtl[280, 182], "start pill")
        assertEquals(colors.selection, rtl[176, 182], "end pill")
        assertEquals(colors.rangeBand, rtl[250, 182], "band inside the start cell, towards the middle")
        assertEquals(colors.rangeBand, rtl[212, 182], "middle day")
        assertEquals(colors.rangeBand, rtl[244, 182], "middle day")
        assertEquals(colors.rangeBand, rtl[205, 182], "band inside the end cell, towards the middle")
        assertEquals(colors.container, rtl[286, 182], "start is rounded on its outer (right) edge")
        assertEquals(colors.container, rtl[169, 182], "end is rounded on its outer (left) edge")
        assertEquals(colors.container, rtl[132, 182], "nothing past the end")
    }

    @Test
    fun largeFontScaleKeepsEveryDayNumberOnAFourByTwoWidget() {
        // 250 x 110 dp at density 3: content 24..726 x 24..306 px. August 2026 needs six rows,
        // which leave a bare grid of 100.3 x 47 px cells (1 Aug is a Saturday, column 5).
        val august = spec(CalendarSnapshotRange.Month(YearMonth(2026, 8)))
        listOf(1f, 1.3f, 2f).forEach { fontScale ->
            val pixels = render(august, width = 750, height = 330, density = Density(3f, fontScale)).toPixelMap()
            val cellWidth = 702f / 7
            for (day in 1..31) {
                val index = day - 1 + 5
                val left = (24 + index % 7 * cellWidth).toInt()
                val top = 24 + index / 7 * 47
                val ink = pixels.inkIn(left + 2 until left + cellWidth.toInt() - 2, top + 2 until top + 45)
                assertTrue(ink > 20, "font scale $fontScale: day $day has $ink ink pixels")
            }
        }
    }

    @Test
    fun longRtlAgendaTitlesStayInsideTheContentBox() {
        val title = "פגישת צוות שבועית ארוכה מאוד ".repeat(20)
        val events = (9..12).map { CalendarEvent<Nothing>("e$it", title, EventSpan.Timed(at(14, it), at(14, it + 1))) }
        val agenda = spec(CalendarSnapshotRange.Agenda(LocalDate(2026, 11, 14)), agendaEvents = events)
        listOf(LayoutDirection.Ltr, LayoutDirection.Rtl).forEach { direction ->
            val pixels = render(agenda, direction, width = 200, height = 140).toPixelMap()
            for (y in 12 until 128) {
                for (x in listOf(2, 5, 194, 197)) {
                    assertEquals(colors.container, pixels[x, y], "$direction ($x, $y)")
                }
            }
        }
    }

    @Test
    fun tinyOutputsDrawWithoutText() {
        listOf(1 to 1, 20 to 20, 60 to 30).forEach { (width, height) ->
            val reference = containerOnly(width, height).pixels()
            // No marks: no title, weekday or day text may appear, so only the container is drawn.
            val plain = render(spec(november), width = width, height = height).pixels()
            assertContentEquals(reference, plain, "$width x $height")
        }
        // With today set the mark is still drawn where text no longer fits.
        val reference = containerOnly(60, 30).pixels()
        val marked = render(spec(november, today = LocalDate(2026, 11, 14)), width = 60, height = 30).pixels()
        assertTrue(reference.indices.any { reference[it] != marked[it] }, "today mark at 60 x 30")
    }

    @Test
    fun outputValidatesItsSizeAndComparesByValue() {
        val output = CalendarSnapshotOutput(330, 330, Density(2f, 1.3f), resolver)
        assertEquals(LayoutDirection.Ltr, output.layoutDirection)
        assertEquals(output, output.copy())
        assertEquals(output.hashCode(), output.copy().hashCode())
        assertNotEquals(output, output.copy(layoutDirection = LayoutDirection.Rtl))
        CalendarSnapshotOutput(4096, 1024, Density(1f), resolver)
        assertFailsWith<IllegalArgumentException> { CalendarSnapshotOutput(0, 330, Density(1f), resolver) }
        assertFailsWith<IllegalArgumentException> { CalendarSnapshotOutput(4097, 1, Density(1f), resolver) }
        assertFailsWith<IllegalArgumentException> { CalendarSnapshotOutput(2049, 2048, Density(1f), resolver) }
        assertFailsWith<IllegalArgumentException> { output.copy(heightPx = 5000) }
    }

    private fun spec(
        range: CalendarSnapshotRange,
        today: LocalDate? = null,
        selection: Selection = Selection.Empty,
        agendaEvents: List<CalendarEvent<*>> = emptyList(),
    ) = CalendarSnapshotSpec(range, today, selection, agendaEvents = agendaEvents, firstDayOfWeek = DayOfWeek.MONDAY)

    private fun render(
        spec: CalendarSnapshotSpec,
        direction: LayoutDirection = LayoutDirection.Ltr,
        width: Int = 296,
        height: Int = 290,
        density: Density = Density(1f),
    ): ImageBitmap {
        val bitmap = ImageBitmap(width, height)
        val style = CalendarSnapshotDefaults.style()
        val painter = CalendarSnapshotPainter(spec, colors, style, resolver, FixedSnapshotFormatter)
        CanvasDrawScope().draw(density, direction, Canvas(bitmap), Size(width.toFloat(), height.toFloat())) {
            painter.draw(this)
        }
        return bitmap
    }

    /** Only the rounded container, as the painter draws it first. */
    private fun containerOnly(
        width: Int,
        height: Int,
    ): ImageBitmap {
        val bitmap = ImageBitmap(width, height)
        val radius = CalendarSnapshotDefaults.style().cornerRadius.value
        val size = Size(width.toFloat(), height.toFloat())
        CanvasDrawScope().draw(Density(1f), LayoutDirection.Ltr, Canvas(bitmap), size) {
            drawRoundRect(colors.container, cornerRadius = CornerRadius(radius))
        }
        return bitmap
    }

    private fun PixelMap.inkIn(
        xs: IntRange,
        ys: IntRange,
    ): Int = ys.sumOf { y -> xs.count { x -> this[x, y] != colors.container } }

    private fun ImageBitmap.pixels(): IntArray = IntArray(width * height).also { readPixels(it) }

    private fun at(
        day: Int,
        hour: Int,
    ) = LocalDateTime(2026, 11, day, hour, 0)
}
