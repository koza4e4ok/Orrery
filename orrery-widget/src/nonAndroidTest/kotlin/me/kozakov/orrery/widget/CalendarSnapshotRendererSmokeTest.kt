@file:OptIn(ExperimentalOrreryApi::class)

package me.kozakov.orrery.widget

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.font.createFontFamilyResolver
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import kotlinx.coroutines.test.runTest
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
import kotlin.test.assertTrue

/** Smoke test of the headless renderer on desktop, iOS and wasmJs; no goldens, fonts differ per platform. */
class CalendarSnapshotRendererSmokeTest {
    private val resolver = createFontFamilyResolver()
    private val today = LocalDate(2026, 11, 14)

    private val specs =
        listOf(
            CalendarSnapshotSpec(
                range = CalendarSnapshotRange.Month(YearMonth(2026, 11)),
                today = today,
                selection = Selection(rangeStart = LocalDate(2026, 11, 16), rangeEnd = LocalDate(2026, 11, 19)),
                eventCounts = mapOf(today to 2),
            ),
            CalendarSnapshotSpec(CalendarSnapshotRange.Week(today), today = today),
            CalendarSnapshotSpec(
                range = CalendarSnapshotRange.Agenda(today),
                today = today,
                agendaEvents =
                    listOf(
                        CalendarEvent<Nothing>("a", "Stand-up", EventSpan.Timed(at(14, 9), at(14, 10))),
                        CalendarEvent<Nothing>("b", "Holiday", EventSpan.AllDay(LocalDate(2026, 11, 15))),
                    ),
            ),
        )

    @Test
    fun rendersTheRequestedSizeNotBlankAndTheSameTwice() =
        runTest {
            val colors = CalendarSnapshotDefaults.colors(lightColorScheme())
            listOf(LayoutDirection.Ltr, LayoutDirection.Rtl).forEach { direction ->
                val output = CalendarSnapshotOutput(330, 250, Density(2f, 1.3f), resolver, direction)
                specs.forEach { spec ->
                    val first = renderCalendarSnapshot(spec, output, colors)
                    assertEquals(330, first.width, "${spec.range}")
                    assertEquals(250, first.height, "${spec.range}")
                    val pixels = first.pixels()
                    val container = colors.container.toArgb()
                    // Text antialiasing alone gives dozens of shades; a blank render has two (container, corners).
                    assertTrue(pixels.toSet().size > 16, "${spec.range} $direction: ${pixels.toSet().size} colours")
                    assertTrue(pixels.count { it != container && it != 0 } > 300, "${spec.range} $direction")
                    assertContentEquals(pixels, renderCalendarSnapshot(spec, output, colors).pixels(), "${spec.range}")
                }
            }
        }

    @Test
    fun coloursAndLayoutDirectionChangeThePixels() =
        runTest {
            val output = CalendarSnapshotOutput(330, 250, Density(2f), resolver)
            val light = renderCalendarSnapshot(specs[0], output, CalendarSnapshotDefaults.colors(lightColorScheme()))
            val dark = renderCalendarSnapshot(specs[0], output, CalendarSnapshotDefaults.colors(darkColorScheme()))
            val rtl =
                renderCalendarSnapshot(
                    specs[0],
                    output.copy(layoutDirection = LayoutDirection.Rtl),
                    CalendarSnapshotDefaults.colors(lightColorScheme()),
                )
            val darkContainer = darkColorScheme().surface.toArgb()
            assertEquals(darkContainer, dark.pixels()[5 * 330 + 165], "dark container in the top padding")
            assertTrue(!light.pixels().contentEquals(dark.pixels()))
            assertTrue(!light.pixels().contentEquals(rtl.pixels()))
        }

    @Test
    fun outputSizeLimitsNameTheValues() {
        val error =
            assertFailsWith<IllegalArgumentException> { CalendarSnapshotOutput(5000, 10, Density(1f), resolver) }
        assertTrue("5000 x 10" in error.message.orEmpty(), error.message)
    }

    private fun ImageBitmap.pixels(): IntArray = IntArray(width * height).also { readPixels(it) }

    private fun at(
        day: Int,
        hour: Int,
    ) = LocalDateTime(2026, 11, day, hour, 0)
}
