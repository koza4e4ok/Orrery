@file:OptIn(ExperimentalOrreryApi::class)

package me.kozakov.orrery.sample.widget

import android.appwidget.AppWidgetManager
import android.content.Context
import android.graphics.Bitmap
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.unit.DpSize
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalSize
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.provideContent
import androidx.glance.layout.Box
import androidx.glance.layout.ContentScale
import androidx.glance.layout.fillMaxSize
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.YearMonth
import kotlinx.datetime.plus
import me.kozakov.orrery.ExperimentalOrreryApi
import me.kozakov.orrery.compose.currentDate
import me.kozakov.orrery.compose.firstDayOfWeekFromLocale
import me.kozakov.orrery.sample.MainActivity
import me.kozakov.orrery.sample.SampleData
import me.kozakov.orrery.widget.CalendarSnapshotRange
import me.kozakov.orrery.widget.CalendarSnapshotSpec
import me.kozakov.orrery.widget.calendarSnapshotDescription
import me.kozakov.orrery.widget.renderCalendarSnapshot
import kotlin.math.roundToInt

/**
 * A home-screen widget that shows an Orrery calendar snapshot: Glance has no custom
 * drawing, so the calendar is rendered into a bitmap with [renderCalendarSnapshot] and
 * shown in a Glance [Image]. Tapping it opens the sample app.
 */
abstract class OrreryCalendarWidget : GlanceAppWidget() {
    // Exact: LocalSize is the size the launcher really gives the widget, so the bitmap is
    // rendered at the widget's pixel size and not larger (RemoteViews caps bitmap memory).
    override val sizeMode: SizeMode = SizeMode.Exact

    /** What the widget shows on [today]. */
    abstract fun spec(today: LocalDate): CalendarSnapshotSpec

    override suspend fun provideGlance(
        context: Context,
        id: GlanceId,
    ) {
        val spec = spec(currentDate())
        // The bitmap has no semantics: TalkBack reads the Image's content description.
        val description = calendarSnapshotDescription(context, spec)
        // Render the sizes the launcher reports (usually portrait and landscape) before
        // composing, so the first RemoteViews already carry the picture.
        val sizes = GlanceAppWidgetManager(context).getAppWidgetSizes(id).distinct()
        val rendered = sizes.mapNotNull { size -> render(context, spec, size)?.let { size to it } }.toMap()
        provideContent {
            val size = LocalSize.current
            // A resize while the session is alive brings a size that was not rendered above.
            val bitmap by produceState(rendered[size] ?: rendered.values.firstOrNull(), size) {
                value = rendered[size] ?: render(context, spec, size) ?: value
            }
            SnapshotImage(bitmap, description)
        }
    }
}

/** The month of today with today marked and dots for the sample events. 2×2 and larger. */
class OrreryMonthWidget : OrreryCalendarWidget() {
    override fun spec(today: LocalDate): CalendarSnapshotSpec {
        val month = YearMonth(today.year, today.month)
        val events = SampleData.eventsFor(month.firstDay..month.lastDay)
        return CalendarSnapshotSpec(
            range = CalendarSnapshotRange.Month(month),
            today = today,
            eventCounts = events.mapValues { (_, dayEvents) -> dayEvents.size },
            // The snapshot never reads the locale itself, so pass the user's first day of the week.
            firstDayOfWeek = firstDayOfWeekFromLocale(),
        )
    }
}

/** The sample events of the next week, from today. 4×2 and larger. */
class OrreryAgendaWidget : OrreryCalendarWidget() {
    override fun spec(today: LocalDate): CalendarSnapshotSpec {
        val end = today.plus(AGENDA_DAYS - 1, DateTimeUnit.DAY)
        // eventsByDate lists a multi-day event under every day it covers (and has already
        // expanded the recurring ones); the agenda wants each event once.
        val events =
            SampleData
                .eventsFor(today..end)
                .values
                .flatten()
                .distinctBy { it.id to it.span }
        return CalendarSnapshotSpec(
            range = CalendarSnapshotRange.Agenda(start = today, days = AGENDA_DAYS, maxRows = AGENDA_MAX_ROWS),
            today = today,
            agendaEvents = events,
        )
    }

    private companion object {
        const val AGENDA_DAYS = 7

        // Rows that do not fit the widget are dropped, so this only caps tall widgets.
        const val AGENDA_MAX_ROWS = 8
    }
}

class OrreryMonthWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = OrreryMonthWidget()

    // Also after a reboot or an app update, which drop the alarm: the system sends an update then.
    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray,
    ) {
        super.onUpdate(context, appWidgetManager, appWidgetIds)
        scheduleMidnightRefresh(context)
    }
}

class OrreryAgendaWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = OrreryAgendaWidget()

    // Also after a reboot or an app update, which drop the alarm: the system sends an update then.
    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray,
    ) {
        super.onUpdate(context, appWidgetManager, appWidgetIds)
        scheduleMidnightRefresh(context)
    }
}

@Composable
private fun SnapshotImage(
    bitmap: Bitmap?,
    description: String,
) {
    val modifier = GlanceModifier.fillMaxSize().clickable(actionStartActivity<MainActivity>())
    if (bitmap == null) {
        Box(modifier) {}
    } else {
        Image(
            provider = ImageProvider(bitmap),
            contentDescription = description,
            modifier = modifier,
            // The bitmap already has the widget's aspect ratio; this only absorbs rounding.
            contentScale = ContentScale.FillBounds,
        )
    }
}

/** [spec] at [size] in [context]'s pixels, or null for an empty size. */
private suspend fun render(
    context: Context,
    spec: CalendarSnapshotSpec,
    size: DpSize,
): Bitmap? {
    val density = context.resources.displayMetrics.density
    val width = (size.width.value * density).roundToInt().coerceAtMost(MAX_SIDE_PX)
    val height = (size.height.value * density).roundToInt().coerceAtMost(MAX_SIDE_PX)
    if (width <= 0 || height <= 0) return null
    return renderCalendarSnapshot(context, spec, width, height).asAndroidBitmap()
}

// Far above any widget; 2048 × 2048 also stays inside the renderer's 4 194 304 px area limit.
private const val MAX_SIDE_PX = 2048
