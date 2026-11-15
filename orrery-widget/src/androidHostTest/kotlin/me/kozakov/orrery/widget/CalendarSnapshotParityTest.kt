@file:OptIn(ExperimentalOrreryApi::class)

package me.kozakov.orrery.widget

import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.State
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFontFamilyResolver
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.YearMonth
import me.kozakov.orrery.ExperimentalOrreryApi
import me.kozakov.orrery.core.CalendarEvent
import me.kozakov.orrery.core.DayInfo
import me.kozakov.orrery.core.DayInfoProvider
import me.kozakov.orrery.core.EventSpan
import me.kozakov.orrery.core.Selection
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** The composable, the remembered bitmap and the headless renderer must draw the same pixels. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = RobolectricDeviceQualifiers.Pixel5)
class CalendarSnapshotParityTest {
    @get:Rule val rule = createComposeRule()

    private val colors = CalendarSnapshotDefaults.colors(lightColorScheme())

    // Square corners: the bitmap is opaque everywhere, so the window behind the composable never shows.
    private val style = CalendarSnapshotDefaults.style().copy(cornerRadius = 0.dp)
    private val today = LocalDate(2026, 11, 14)
    private val width = 690
    private val height = 600

    private val month =
        CalendarSnapshotSpec(
            range = CalendarSnapshotRange.Month(YearMonth(2026, 11)),
            today = today,
            selection = Selection(rangeStart = LocalDate(2026, 11, 17), rangeEnd = LocalDate(2026, 11, 21)),
            dayInfo =
                object : DayInfoProvider {
                    override fun info(date: LocalDate): DayInfo? =
                        if (date.day == 11) DayInfo(label = null, isHoliday = true) else null
                },
            eventCounts = mapOf(today to 2, LocalDate(2026, 11, 24) to 4),
            showOutsideDays = true,
        )

    private val agenda =
        CalendarSnapshotSpec(
            range = CalendarSnapshotRange.Agenda(today),
            today = today,
            agendaEvents =
                listOf(
                    CalendarEvent<Nothing>(
                        "a",
                        "Design review",
                        EventSpan.Timed(LocalDateTime(2026, 11, 14, 10, 0), LocalDateTime(2026, 11, 14, 11, 0)),
                        colorArgb = 0xFF6A1B9A,
                    ),
                    CalendarEvent<Nothing>("b", "Trip", EventSpan.AllDay(today, LocalDate(2026, 11, 16))),
                ),
        )

    @Test
    fun monthComposableMatchesTheRenderer() = assertComposableParity(month, LayoutDirection.Ltr)

    @Test
    fun rtlAgendaComposableMatchesTheRenderer() = assertComposableParity(agenda, LayoutDirection.Rtl)

    @Test
    fun rememberedBitmapMatchesTheRenderer() {
        var environment: Pair<Density, FontFamily.Resolver>? = null
        var state: State<ImageBitmap?>? = null
        rule.setContent {
            environment = LocalDensity.current to LocalFontFamilyResolver.current
            state = rememberCalendarSnapshotBitmap(month, width, height, colors, style)
        }
        rule.waitUntil(timeoutMillis = 10_000) { state?.value != null }
        val (density, resolver) = checkNotNull(environment)
        val expected = render(month, CalendarSnapshotOutput(width, height, density, resolver))
        assertSamePixels(expected, checkNotNull(state?.value))
    }

    private fun assertComposableParity(
        spec: CalendarSnapshotSpec,
        direction: LayoutDirection,
    ) {
        var environment: Pair<Density, FontFamily.Resolver>? = null
        rule.setContent {
            CompositionLocalProvider(LocalLayoutDirection provides direction) {
                environment = LocalDensity.current to LocalFontFamilyResolver.current
                CalendarSnapshot(spec, Modifier.exactSize(width, height), colors, style)
            }
        }
        val description = snapshotDescription(spec, PlatformSnapshotFormatter())
        // One node carries the whole description.
        val node = rule.onNodeWithContentDescription(description).fetchSemanticsNode()
        assertEquals(listOf(description), node.config.getOrNull(SemanticsProperties.ContentDescription))
        assertEquals(1, rule.onAllNodes(hasContentDescription(description)).fetchSemanticsNodes().size)
        val (density, resolver) = checkNotNull(environment)
        val expected = render(spec, CalendarSnapshotOutput(width, height, density, resolver, direction))
        // The composable renders on Dispatchers.Default, which the compose idling does not track.
        val capture = { rule.onNodeWithContentDescription(description).captureToImage() }
        runCatching { rule.waitUntil(timeoutMillis = 10_000) { differentPixels(expected, capture()) == 0 } }
        assertSamePixels(expected, capture())
    }

    private fun render(
        spec: CalendarSnapshotSpec,
        output: CalendarSnapshotOutput,
    ): ImageBitmap = runBlocking { renderCalendarSnapshot(spec, output, colors, style) }

    private fun assertSamePixels(
        expected: ImageBitmap,
        actual: ImageBitmap,
    ) {
        assertEquals(expected.width to expected.height, actual.width to actual.height, "size")
        val different = differentPixels(expected, actual)
        assertTrue(different == 0, "$different of ${expected.width * expected.height} pixels differ")
    }

    private fun differentPixels(
        expected: ImageBitmap,
        actual: ImageBitmap,
    ): Int {
        if (expected.width != actual.width || expected.height != actual.height) return -1
        val want = IntArray(expected.width * expected.height).also { expected.readPixels(it) }
        val got = IntArray(actual.width * actual.height).also { actual.readPixels(it) }
        return want.indices.count { want[it] != got[it] }
    }

    private fun Modifier.exactSize(
        widthPx: Int,
        heightPx: Int,
    ): Modifier =
        layout { measurable, _ ->
            val placeable = measurable.measure(Constraints.fixed(widthPx, heightPx))
            layout(widthPx, heightPx) { placeable.place(0, 0) }
        }
}
