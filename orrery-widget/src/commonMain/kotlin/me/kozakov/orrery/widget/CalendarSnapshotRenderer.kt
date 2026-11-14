@file:OptIn(ExperimentalOrreryApi::class, InternalOrreryApi::class)

package me.kozakov.orrery.widget

import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.produceState
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFontFamilyResolver
import androidx.compose.ui.platform.LocalLayoutDirection
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.datetime.YearMonth
import kotlinx.datetime.plus
import me.kozakov.orrery.ExperimentalOrreryApi
import me.kozakov.orrery.InternalOrreryApi
import me.kozakov.orrery.compose.internal.PlatformDateText

/**
 * Draws [spec] into a new bitmap of [output]'s size, headless: no composition, window or
 * GPU, only software drawing into an in-memory bitmap. The same inputs give the same
 * pixels on the same platform and fonts; "today" comes from [spec], never from the clock.
 *
 * Safe to call from any dispatcher: the drawing runs on [Dispatchers.Default]. Dates are
 * formatted for the current default locale. On Android, the `Context` overload takes the
 * density, font scale, locale and layout direction from the context instead.
 *
 * Size limits are checked by [CalendarSnapshotOutput].
 */
@ExperimentalOrreryApi
public suspend fun renderCalendarSnapshot(
    spec: CalendarSnapshotSpec,
    output: CalendarSnapshotOutput,
    colors: CalendarSnapshotColors,
    style: CalendarSnapshotStyle = CalendarSnapshotDefaults.style(),
): ImageBitmap {
    val formatter = PlatformSnapshotFormatter()
    return withContext(Dispatchers.Default) { drawCalendarSnapshot(spec, output, colors, style, formatter) }
}

/**
 * The snapshot of [spec] as a bitmap of [widthPx] × [heightPx], rendered off the main
 * thread with the density, font scale, fonts and layout direction of the composition.
 * Holds null until the first render finishes, and is re-rendered when an input changes
 * (the previous bitmap stays until the new one is ready). For a share-card preview or a
 * notification builder; to show a snapshot in the UI use [CalendarSnapshot].
 *
 * @throws IllegalArgumentException if [widthPx] or [heightPx] is outside `1..4096`, or
 *   `widthPx × heightPx` is above 4 194 304 pixels.
 */
@ExperimentalOrreryApi
@Composable
public fun rememberCalendarSnapshotBitmap(
    spec: CalendarSnapshotSpec,
    widthPx: Int,
    heightPx: Int,
    colors: CalendarSnapshotColors = CalendarSnapshotDefaults.colors(),
    style: CalendarSnapshotStyle = CalendarSnapshotDefaults.style(),
): State<ImageBitmap?> {
    checkSnapshotSize(widthPx, heightPx)
    val density = LocalDensity.current
    val fontFamilyResolver = LocalFontFamilyResolver.current
    val layoutDirection = LocalLayoutDirection.current
    return produceState<ImageBitmap?>(
        null,
        spec,
        widthPx,
        heightPx,
        colors,
        style,
        density,
        fontFamilyResolver,
        layoutDirection,
    ) {
        val output = CalendarSnapshotOutput(widthPx, heightPx, density, fontFamilyResolver, layoutDirection)
        value = renderCalendarSnapshot(spec, output, colors, style)
    }
}

/** Draws [spec] into a new bitmap of [output]'s size on the calling thread. */
internal fun drawCalendarSnapshot(
    spec: CalendarSnapshotSpec,
    output: CalendarSnapshotOutput,
    colors: CalendarSnapshotColors,
    style: CalendarSnapshotStyle,
    formatter: SnapshotFormatter,
): ImageBitmap {
    val bitmap = ImageBitmap(output.widthPx, output.heightPx)
    val painter = CalendarSnapshotPainter(spec, colors, style, output.fontFamilyResolver, formatter)
    val size = Size(output.widthPx.toFloat(), output.heightPx.toFloat())
    CanvasDrawScope().draw(output.density, output.layoutDirection, Canvas(bitmap), size) {
        painter.draw(this)
    }
    return bitmap
}

/**
 * The content description of [spec]'s snapshot: the month title, or the spoken date range
 * of a week or agenda strip.
 */
internal fun snapshotDescription(
    spec: CalendarSnapshotSpec,
    formatter: SnapshotFormatter,
): String =
    when (val range = spec.range) {
        is CalendarSnapshotRange.Month -> formatter.monthTitle(range.month)

        is CalendarSnapshotRange.Week -> {
            val start = weekStart(range.date, spec.firstDayOfWeek)
            formatter.accessibleDateRange(start, start.plus(DAYS_PER_WEEK - 1, DateTimeUnit.DAY))
        }

        is CalendarSnapshotRange.Agenda ->
            formatter.accessibleDateRange(range.start, range.start.plus(range.days - 1, DateTimeUnit.DAY))
    }

/** [start] alone when it equals [end], else both joined by an en dash. */
internal inline fun dateRangeText(
    start: LocalDate,
    end: LocalDate,
    format: (LocalDate) -> String,
): String = if (start == end) format(start) else "${format(start)} \u2013 ${format(end)}"

/**
 * Date text from the platform date formatting of `orrery-compose`, for the current
 * default locale, read when the formatter is created.
 */
internal class PlatformSnapshotFormatter : SnapshotFormatter {
    private val is24Hour = PlatformDateText.is24HourClock()

    override fun monthTitle(month: YearMonth): String = PlatformDateText.yearMonthTitle(month)

    override fun dateRange(
        start: LocalDate,
        end: LocalDate,
    ): String = dateRangeText(start, end, PlatformDateText::mediumDate)

    override fun accessibleDateRange(
        start: LocalDate,
        end: LocalDate,
    ): String = dateRangeText(start, end, PlatformDateText::longDate)

    override fun weekdayLabel(dayOfWeek: DayOfWeek): String = PlatformDateText.weekdayName(dayOfWeek, narrow = true)

    override fun dayHeading(date: LocalDate): String = PlatformDateText.dayHeader(date)

    override fun time(time: LocalTime): String = PlatformDateText.time(time.hour, time.minute, is24Hour)
}
