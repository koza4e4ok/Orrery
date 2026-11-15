@file:OptIn(ExperimentalOrreryApi::class)

package me.kozakov.orrery.widget

import android.content.Context
import android.content.ContextWrapper
import android.content.res.Configuration
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.LayoutDirection
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.datetime.YearMonth
import me.kozakov.orrery.ExperimentalOrreryApi
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.lang.ref.ReferenceQueue
import java.lang.ref.WeakReference
import java.util.Locale
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNotSame
import kotlin.test.assertSame
import kotlin.test.assertTrue

/** The Android overload: what it reads from the Context, and that it does not keep the Context. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = RobolectricDeviceQualifiers.Pixel5)
class CalendarSnapshotAndroidTest {
    private val app: Context get() = RuntimeEnvironment.getApplication()
    private val spec =
        CalendarSnapshotSpec(CalendarSnapshotRange.Month(YearMonth(2026, 11)), today = LocalDate(2026, 11, 14))

    @Test
    fun nightModeSwitchesTheDefaultColours() {
        val day = nightMode(false)
        val night = nightMode(true)
        val dayColors = CalendarSnapshotDefaults.colors(day)
        val nightColors = CalendarSnapshotDefaults.colors(night)
        assertNotEquals(dayColors.container, nightColors.container)
        // API 35 here: the dynamic schemes.
        assertEquals(CalendarSnapshotDefaults.colors(dynamicLightColorScheme(day)), dayColors)
        assertEquals(CalendarSnapshotDefaults.colors(dynamicDarkColorScheme(night)), nightColors)
        // The overload's default colours follow the context; the top padding shows the container.
        assertEquals(nightColors.container.toArgb(), render(night).asAndroidBitmap().getPixel(150, 4))
        assertEquals(dayColors.container.toArgb(), render(day).asAndroidBitmap().getPixel(150, 4))
    }

    @Test
    @Config(sdk = [30])
    fun belowApi31TheStaticSchemesFollowTheNightMode() {
        val day = nightMode(false)
        val night = nightMode(true)
        assertEquals(CalendarSnapshotDefaults.colors(lightColorScheme()), CalendarSnapshotDefaults.colors(day))
        assertEquals(CalendarSnapshotDefaults.colors(darkColorScheme()), CalendarSnapshotDefaults.colors(night))
    }

    @Test
    fun densityFontScaleLocaleAndDirectionComeFromTheContext() {
        val large =
            configured {
                densityDpi = 480
                fontScale = 2f
                setLocale(Locale.forLanguageTag("uk-UA"))
            }
        val environment = AndroidSnapshotEnvironment.of(large)
        assertEquals(3f, environment.density.density)
        assertEquals(2f, environment.density.fontScale)
        assertEquals(LayoutDirection.Ltr, environment.layoutDirection)
        assertEquals(Locale.forLanguageTag("uk-UA"), environment.locale)
        val title = environment.formatter().monthTitle(YearMonth(2026, 11))
        assertTrue("листопад" in title && "2026" in title, title)
        assertTrue(environment.formatter().time(LocalTime(14, 5)).isNotBlank())

        val hebrew = configured { setLocale(Locale.forLanguageTag("he-IL")) }
        assertEquals(LayoutDirection.Rtl, AndroidSnapshotEnvironment.of(hebrew).layoutDirection)
        val englishRtl = configured { setLayoutDirection(Locale.forLanguageTag("ar")) }
        assertEquals(LayoutDirection.Rtl, AndroidSnapshotEnvironment.of(englishRtl).layoutDirection)

        // The font scale reaches the pixels (at a size where the text is not fit-limited), and the
        // same context draws the same pixels twice.
        val normal =
            configured {
                densityDpi = 480
                fontScale = 1f
                setLocale(Locale.forLanguageTag("uk-UA"))
            }
        val big = render(large, 1200, 1050).pixels()
        assertFalse(big.contentEquals(render(normal, 1200, 1050).pixels()))
        assertTrue(big.contentEquals(render(large, 1200, 1050).pixels()))
    }

    @Test
    fun sizeLimitsNameTheValues() {
        val side =
            assertFailsWith<IllegalArgumentException> { runBlocking { renderCalendarSnapshot(app, spec, 0, 100) } }
        assertTrue("0 x 100" in side.message.orEmpty(), side.message)
        val area =
            assertFailsWith<IllegalArgumentException> { runBlocking { renderCalendarSnapshot(app, spec, 3000, 3000) } }
        assertTrue("3000 x 3000 = 9000000" in area.message.orEmpty(), area.message)
    }

    @Test
    fun theFontResolverIsSharedPerApplication() {
        val first = AndroidSnapshotEnvironment.of(app).fontFamilyResolver
        assertSame(first, AndroidSnapshotEnvironment.of(nightMode(true)).fontFamilyResolver)
        assertSame(first, AndroidSnapshotEnvironment.of(object : ContextWrapper(app) {}).fontFamilyResolver)
        val bold = configured { fontWeightAdjustment = 300 }
        assertNotSame(first, AndroidSnapshotEnvironment.of(bold).fontFamilyResolver)
    }

    // A leak check has to ask for collection; the loop stops as soon as the context is collected.
    @Suppress("ExplicitGarbageCollectionCall")
    @Test
    fun theContextIsNotRetained() {
        // A fresh cache, so the shared resolver is created from the throwaway context and is still
        // held after the call: the check below then also covers what the resolver keeps.
        AndroidSnapshotEnvironment.clearFontFamilyResolverCache()
        val queue = ReferenceQueue<Context>()
        val reference = renderWithThrowawayContext(queue)
        val deadline = System.nanoTime() + GC_TIMEOUT_MS * NANOS_PER_MS
        var collected = false
        while (!collected && System.nanoTime() < deadline) {
            System.gc()
            System.runFinalization()
            collected = queue.remove(GC_PAUSE_MS) != null
        }
        assertTrue(
            collected || reference.get() == null,
            "the Context passed to renderCalendarSnapshot is still reachable",
        )
    }

    /** Renders with a context that nothing else references, and returns only a weak reference to it. */
    private fun renderWithThrowawayContext(queue: ReferenceQueue<Context>): WeakReference<Context> {
        val context = object : ContextWrapper(app) {}
        val bitmap = render(context)
        assertEquals(300, bitmap.width)
        return WeakReference(context, queue)
    }

    @Test
    fun dayHeadingsFollowTheLocaleOrder() {
        val date = LocalDate(2026, 11, 14)
        val english = AndroidSnapshotEnvironment.of(configured { setLocale(Locale.US) }).formatter()
        assertEquals("Sat, Nov 14", english.dayHeading(date))
        val ukrainian = configured { setLocale(Locale.forLanguageTag("uk-UA")) }
        val heading = AndroidSnapshotEnvironment.of(ukrainian).formatter().dayHeading(date)
        // Day before month, as in the Ukrainian title.
        assertTrue(heading.indexOf("14") < heading.indexOf("лист"), heading)
    }

    private fun render(
        context: Context,
        widthPx: Int = 300,
        heightPx: Int = 240,
    ): ImageBitmap = runBlocking { renderCalendarSnapshot(context, spec, widthPx, heightPx) }

    private fun nightMode(night: Boolean): Context =
        configured {
            val mode = if (night) Configuration.UI_MODE_NIGHT_YES else Configuration.UI_MODE_NIGHT_NO
            uiMode = uiMode and Configuration.UI_MODE_NIGHT_MASK.inv() or mode
        }

    private fun configured(block: Configuration.() -> Unit): Context =
        app.createConfigurationContext(Configuration(app.resources.configuration).apply(block))

    private fun ImageBitmap.pixels(): IntArray = IntArray(width * height).also { readPixels(it) }

    private companion object {
        const val GC_TIMEOUT_MS = 10_000L
        const val GC_PAUSE_MS = 100L
        const val NANOS_PER_MS = 1_000_000L
    }
}
