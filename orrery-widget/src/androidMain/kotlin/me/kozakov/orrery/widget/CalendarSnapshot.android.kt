@file:OptIn(InternalOrreryApi::class)

package me.kozakov.orrery.widget

import android.content.Context
import android.content.res.Configuration
import android.os.Build
import android.text.format.DateFormat
import android.view.View
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.createFontFamilyResolver
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.datetime.YearMonth
import kotlinx.datetime.toJavaLocalDate
import me.kozakov.orrery.ExperimentalOrreryApi
import me.kozakov.orrery.InternalOrreryApi
import me.kozakov.orrery.compose.displayName
import me.kozakov.orrery.compose.internal.PlatformDateText
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

/**
 * Draws [spec] into a new [widthPx] × [heightPx] bitmap, for an app widget or a share
 * image. Uses [context]'s resources: density, font scale, locale, layout direction and
 * 24-hour clock setting, and its night mode and dynamic colour for the default [colors].
 *
 * [context] is read synchronously before the drawing starts and is not retained, so an
 * `Activity` or the `Context` of `GlanceAppWidget.provideGlance` is safe to pass. The
 * drawing runs on [Dispatchers.Default]; see the common [renderCalendarSnapshot] for the
 * determinism and drawing rules. Render at the widget's actual pixel size: RemoteViews
 * limits the bitmap memory of a widget.
 *
 * @throws IllegalArgumentException if [widthPx] or [heightPx] is outside `1..4096`, or
 *   `widthPx × heightPx` is above 4 194 304 pixels (about 16 MB at ARGB_8888).
 */
@ExperimentalOrreryApi
@Suppress("LongParameterList")
public suspend fun renderCalendarSnapshot(
    context: Context,
    spec: CalendarSnapshotSpec,
    widthPx: Int,
    heightPx: Int,
    colors: CalendarSnapshotColors = CalendarSnapshotDefaults.colors(context),
    style: CalendarSnapshotStyle = CalendarSnapshotDefaults.style(),
): ImageBitmap {
    checkSnapshotSize(widthPx, heightPx)
    val environment = AndroidSnapshotEnvironment.of(context)
    val output =
        CalendarSnapshotOutput(
            widthPx = widthPx,
            heightPx = heightPx,
            density = environment.density,
            fontFamilyResolver = environment.fontFamilyResolver,
            layoutDirection = environment.layoutDirection,
        )
    val formatter = environment.formatter()
    return withContext(Dispatchers.Default) { drawCalendarSnapshot(spec, output, colors, style, formatter) }
}

/**
 * Default snapshot colours for [context]: the dynamic (wallpaper) colour scheme on
 * Android 12 (API 31) and later, otherwise the baseline Material 3 light or dark scheme,
 * picked by the night mode of [context]'s configuration. Mapped as in
 * [CalendarSnapshotDefaults.colors] with a colour scheme. Does not retain [context].
 */
@ExperimentalOrreryApi
public fun CalendarSnapshotDefaults.colors(context: Context): CalendarSnapshotColors {
    val night = context.resources.configuration.isNight()
    val scheme =
        when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
                if (night) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)

            night -> darkColorScheme()

            else -> lightColorScheme()
        }
    return colors(scheme)
}

/**
 * The content description of [spec]'s snapshot in [context]'s locale: the month title, or
 * the spoken date range of a week or agenda strip, the same text the [CalendarSnapshot]
 * composable announces. A bitmap has no semantics, so set this on the host view that
 * shows it, for example the `contentDescription` of a Glance `Image`. Reads [context]
 * synchronously and does not retain it.
 */
@ExperimentalOrreryApi
public fun calendarSnapshotDescription(
    context: Context,
    spec: CalendarSnapshotSpec,
): String {
    val formatter =
        LocaleSnapshotFormatter(context.resources.configuration.primaryLocale(), DateFormat.is24HourFormat(context))
    return snapshotDescription(spec, formatter)
}

private fun Configuration.isNight(): Boolean =
    (uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES

/** Everything the Android overload reads from a `Context`, without keeping the context. */
internal class AndroidSnapshotEnvironment(
    val density: Density,
    val fontFamilyResolver: FontFamily.Resolver,
    val layoutDirection: LayoutDirection,
    val locale: Locale,
    val is24Hour: Boolean,
) {
    /** Date text in [locale], on the clock of [is24Hour]. */
    fun formatter(): SnapshotFormatter = LocaleSnapshotFormatter(locale, is24Hour)

    companion object {
        /** Reads [context]'s resources now; the font resolver is shared, see [fontFamilyResolver]. */
        fun of(context: Context): AndroidSnapshotEnvironment {
            val configuration = context.resources.configuration
            return AndroidSnapshotEnvironment(
                density = Density(context),
                fontFamilyResolver = fontFamilyResolver(context),
                layoutDirection =
                    if (configuration.layoutDirection == View.LAYOUT_DIRECTION_RTL) {
                        LayoutDirection.Rtl
                    } else {
                        LayoutDirection.Ltr
                    },
                locale = configuration.primaryLocale(),
                is24Hour = DateFormat.is24HourFormat(context),
            )
        }

        private val lock = Any()
        private var cachedKey: ResolverKey? = null
        private var cachedResolver: FontFamily.Resolver? = null

        /**
         * One resolver (with its font cache and loading scope) for every render with the same
         * application context and font weight adjustment, instead of a new one per call. The
         * resolver keeps only the application context ([createFontFamilyResolver] hands the
         * font loader `context.applicationContext`) and the weight adjustment as a number, so
         * caching it does not retain an `Activity`. One entry: a new application or a changed
         * bold-text setting replaces it.
         */
        fun fontFamilyResolver(context: Context): FontFamily.Resolver {
            val weightAdjustment = context.resources.configuration.fontWeightAdjustmentOrZero()
            val key = ResolverKey(context.applicationContext, weightAdjustment)
            synchronized(lock) {
                val cached = cachedResolver
                if (cached != null && cachedKey == key) return cached
                return createFontFamilyResolver(context).also {
                    cachedKey = key
                    cachedResolver = it
                }
            }
        }

        /** Drops the cached resolver, for tests that need a fresh one. */
        fun clearFontFamilyResolverCache() {
            synchronized(lock) {
                cachedKey = null
                cachedResolver = null
            }
        }
    }

    private data class ResolverKey(
        val application: Context,
        val fontWeightAdjustment: Int,
    )
}

/** Weekday, abbreviated month and day, in the locale's order. */
private const val DAY_HEADING_SKELETON = "EEEMMMd"

private fun Configuration.fontWeightAdjustmentOrZero(): Int =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) fontWeightAdjustment else 0

private fun Configuration.primaryLocale(): Locale =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
        locales[0]
    } else {
        @Suppress("DEPRECATION")
        locale
    }

/**
 * Date text in an explicit [locale], with the same patterns as the platform formatting of
 * `orrery-compose`, except the day heading: that uses the locale's own order for the
 * `EEEMMMd` skeleton (`DateFormat.getBestDateTimePattern`), so Ukrainian reads
 * "сб, 14 лист." like its titles, and English still reads "Sat, Nov 14".
 */
internal class LocaleSnapshotFormatter(
    private val locale: Locale,
    private val is24Hour: Boolean,
) : SnapshotFormatter {
    private val medium = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(locale)
    private val long = DateTimeFormatter.ofLocalizedDate(FormatStyle.LONG).withLocale(locale)
    private val heading =
        DateTimeFormatter.ofPattern(DateFormat.getBestDateTimePattern(locale, DAY_HEADING_SKELETON), locale)

    override fun monthTitle(month: YearMonth): String = month.displayName(locale)

    override fun dateRange(
        start: LocalDate,
        end: LocalDate,
    ): String = dateRangeText(start, end) { medium.format(it.toJavaLocalDate()) }

    override fun accessibleDateRange(
        start: LocalDate,
        end: LocalDate,
    ): String = dateRangeText(start, end) { long.format(it.toJavaLocalDate()) }

    override fun weekdayLabel(dayOfWeek: DayOfWeek): String = dayOfWeek.displayName(narrow = true, locale = locale)

    override fun dayHeading(date: LocalDate): String = heading.format(date.toJavaLocalDate())

    override fun time(time: LocalTime): String =
        PlatformDateText.time(time.hour, time.minute, is24Hour, locale.toLanguageTag())
}
