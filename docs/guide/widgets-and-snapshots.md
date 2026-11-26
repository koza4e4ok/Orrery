# Widgets and snapshots

> **Experimental (#89).** Everything on this page lives in
> `me.kozakov.orrery:orrery-widget` (on Maven Central from `1.0.0-rc.1`) and is marked
> `@ExperimentalOrreryApi`: it can change in any release of the 1.0.x line and graduates in 1.1.

Home-screen widgets (Glance or RemoteViews), notifications and share images cannot host a
composable calendar. `orrery-widget` draws a compact **month**, **week** or **agenda strip**
into an `ImageBitmap` instead: no composition, no window, software drawing only, so it runs
inside a widget update or a worker. The `CalendarSnapshot` composable shows the same pixels
in the app, for a preview that matches the widget exactly.

Orrery does not depend on Glance. The widget glue on this page comes from the sample app,
[`sample/.../widget/OrreryCalendarWidgets.kt`](../../sample/src/main/kotlin/me/kozakov/orrery/sample/widget/OrreryCalendarWidgets.kt),
which CI builds; copy it into your app and add Glance there.

## Setup

Use **one version for every `orrery-*` artifact**. `orrery-widget` calls internal drawing
code of `orrery-compose` and requires exactly the same version: its Gradle metadata says
`strictly` and its POM an exact `[x.y.z]` range, so in Gradle a mismatch fails dependency
resolution instead of crashing at run time.

```kotlin
// app/build.gradle.kts
dependencies {
    implementation("me.kozakov.orrery:orrery-compose:<version>")
    implementation("me.kozakov.orrery:orrery-widget:<version>") // the same <version>
    implementation("androidx.glance:glance-appwidget:1.2.0") // only for a Glance widget
}
```

Every declaration needs an opt-in (a warning without it). Opt in per file:

```kotlin
@file:OptIn(ExperimentalOrreryApi::class) // import me.kozakov.orrery.ExperimentalOrreryApi
```

or for the whole module:

```kotlin
// app/build.gradle.kts
kotlin {
    compilerOptions {
        optIn.add("me.kozakov.orrery.ExperimentalOrreryApi")
    }
}
```

## What to draw: `CalendarSnapshotSpec`

```kotlin
val month = CalendarSnapshotSpec(
    range = CalendarSnapshotRange.Month(YearMonth(today.year, today.month)),
    today = today,
    eventCounts = eventsByDay.mapValues { (_, events) -> events.size }, // 1-3 dots, more draw three
    firstDayOfWeek = firstDayOfWeekFromLocale(),
)

val agenda = CalendarSnapshotSpec(
    range = CalendarSnapshotRange.Agenda(start = today, days = 7, maxRows = 8),
    today = today,
    agendaEvents = events, // CalendarEvent<*>, each event once, recurrences already expanded
)
```

The snapshot never reads the clock or the locale's first day of the week: pass `today` and
`firstDayOfWeek` yourself, so the same spec always draws the same picture. Other fields:
`selection` (single, range or multiple days), `dayInfo` (holiday tint and priority colours
from any `DayInfoProvider`), `showTitle`, `showWeekdayLabels` and `showOutsideDays`.
`CalendarSnapshotRange.Week(date)` draws the week that contains `date`.

Agenda events come in as a flat list. `eventsByDate` lists a multi-day event under every
day it covers, so de-duplicate a list made from it (the sample uses
`.values.flatten().distinctBy { it.id to it.span }`). Recurring events must be expanded
first (`eventsByDate` and `expandRecurring` do that); the renderer draws the events it is given.

## Render on Android

```kotlin
val bitmap: ImageBitmap = renderCalendarSnapshot(context, spec, widthPx, heightPx)
```

The `Context` overload takes density, font scale, locale, layout direction (RTL), the
24-hour clock setting and the default colours from the context, reads them before drawing
and does not keep the context, so the `Context` of a widget update is safe to pass. It is a
`suspend` function and draws on `Dispatchers.Default` by itself.

## A Glance widget

This is the sample's widget base class with its two helpers, copied from the sample (CI
checks that they match). Glance has no custom drawing, so the snapshot goes into a Glance
`Image`:

<!-- snippet: sample/src/main/kotlin/me/kozakov/orrery/sample/widget/OrreryCalendarWidgets.kt -->
```kotlin
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
```

<!-- snippet: sample/src/main/kotlin/me/kozakov/orrery/sample/widget/OrreryCalendarWidgets.kt -->
```kotlin
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
```

The sample adds two widgets on top of it, `OrreryMonthWidget` (2×2 and larger) and
`OrreryAgendaWidget` (4×2 and larger), with their receivers, `res/xml` provider info and
manifest entries. Tapping a widget opens the app.

## Sizing

- **Render at the widget's real pixel size**, never larger. RemoteViews limits the bitmap
  memory of a widget (about 1.5 × the screen's size in ARGB pixels across all the widget's
  views) and every size you render is sent to the launcher. With `SizeMode.Exact` that is
  usually two bitmaps, portrait and landscape. `SizeMode.Responsive` with a few sizes also
  works; then render each declared size and let `ContentScale.Fit` handle the rest.
- Convert dp to px with `resources.displayMetrics.density`. The renderer accepts 1 to 4096 px
  a side and at most 4 194 304 px in total and throws `IllegalArgumentException` otherwise;
  the sample caps each side at 2048 px, far above any widget.
- The picture adapts to the box instead of overflowing. Cells take the space, text shrinks
  with them down to `CalendarSnapshotStyle.minTextScale` (0.6 by default) of its size, and
  when that still does not fit, the **title row is dropped first, then the weekday row**.
  Agenda rows that do not fit are dropped whole, never clipped, with no "+N more" marker.
- What that means at font scale 1 (from the golden screenshots): a month at 2×2 (110×110 dp)
  and 4×2 (250×110 dp) shows the weekday row and the days but no month title; at 250×250 dp
  it has the title. If the month name matters on a small widget, put it in a Glance `Text`
  above the image and pass `showTitle = false`. An agenda at 4×2 shows its title and about
  two event rows.
- Font scale is honoured (it comes with the context's density). At 2.0 a 4×2 month drops the
  weekday row as well; the day numbers stay.

## Dark mode and colours

`renderCalendarSnapshot(context, ...)` defaults to `CalendarSnapshotDefaults.colors(context)`:
the wallpaper-based dynamic colour scheme on Android 12 (API 31) and later, otherwise the
Material 3 baseline light or dark scheme, picked by the night mode of the context. To use
your own theme, map your colour schemes and choose by night mode:

```kotlin
val night = (context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
    Configuration.UI_MODE_NIGHT_YES
val colors = CalendarSnapshotDefaults.colors(if (night) AppDarkColors else AppLightColors)
val bitmap = renderCalendarSnapshot(context, spec, widthPx, heightPx, colors = colors)
```

`CalendarSnapshotColors` has a `copy` for single changes, and `CalendarSnapshotStyle` holds
the corner radius, padding and text sizes.

A bitmap cannot follow a theme switch by itself, unlike Glance `ColorProvider`s: the colours
are fixed when it is drawn, and the system does not ask your widget to update when the
night mode or the wallpaper changes. Re-render on your update cadence (below); if your app
is running, you can also call `updateAll` when it sees a configuration change.

Colours you pass in data (event colours, `DayInfo` priority colours) are drawn as they are
in both themes, so choose ones that read on light and dark surfaces.

## Update cadence

A calendar widget only has to change when "today", the data or the theme changes. The sample:

- Re-renders at **local midnight** with an inexact, non-waking `AlarmManager.set(RTC, ...)`
  alarm (no exact-alarm permission; it fires when the device is next awake). In Doze an
  inexact alarm, like the hourly update, can be deferred to the next maintenance window, so
  the widget can show the previous day for a while after midnight. The alarm is set again on
  every update, so a reboot or app update (which drop alarms) is covered by the next system
  update.
- Re-renders on `TIME_SET`, `TIMEZONE_CHANGED` and `LOCALE_CHANGED`, which manifest
  receivers may still receive.
- Uses `android:updatePeriodMillis="3600000"` (hourly) as a safety net that also picks up
  night-mode and wallpaper colour changes within the hour. The system does not allow less
  than 30 minutes.
- In your app, also call `updateAll(context)` on your widget (Glance) after changing the data
  it shows; the sample's data is generated, so it has nothing to call this for.

Each update renders one bitmap per reported size on `Dispatchers.Default`.

The refresh code, copied from
[`sample/.../widget/OrreryWidgetRefresh.kt`](../../sample/src/main/kotlin/me/kozakov/orrery/sample/widget/OrreryWidgetRefresh.kt)
without its package and imports. The receiver runs the update in `goAsync()`, catches and
logs failures, because an exception that escapes would kill the app process on a background
broadcast, and times out so that `finish()` always runs within the broadcast's limit:

<!-- snippet: sample/src/main/kotlin/me/kozakov/orrery/sample/widget/OrreryWidgetRefresh.kt -->
```kotlin
class OrreryWidgetRefreshReceiver : BroadcastReceiver() {
    // An exception that escapes this scope would kill the app process on a background
    // broadcast, so log it instead. The timeout and finally make sure finish() runs before
    // the system's goAsync() limit (about 10 s), even when an update hangs.
    @Suppress("TooGenericExceptionCaught")
    override fun onReceive(
        context: Context,
        intent: Intent,
    ) {
        val app = context.applicationContext
        val pending = goAsync()
        CoroutineScope(Dispatchers.Default).launch {
            try {
                withTimeout(REFRESH_TIMEOUT) { refreshOrreryWidgets(app) }
            } catch (e: TimeoutCancellationException) {
                Log.w(TAG, "Widget refresh timed out", e)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Widget refresh failed", e)
            } finally {
                pending.finish()
            }
        }
    }
}

/** Re-renders every placed calendar widget and keeps the midnight alarm while any is placed. */
internal suspend fun refreshOrreryWidgets(context: Context) {
    OrreryMonthWidget().updateAll(context)
    OrreryAgendaWidget().updateAll(context)
    val manager = GlanceAppWidgetManager(context)
    val placed =
        manager.getGlanceIds(OrreryMonthWidget::class.java).isNotEmpty() ||
            manager.getGlanceIds(OrreryAgendaWidget::class.java).isNotEmpty()
    if (placed) scheduleMidnightRefresh(context)
}

/**
 * Sets an inexact, non-waking alarm for the next local midnight. It fires when the device is
 * next awake after midnight, which is soon enough for a calendar and needs no exact-alarm
 * permission; in Doze it can wait for the next maintenance window. Setting it again replaces
 * the previous one.
 */
internal fun scheduleMidnightRefresh(context: Context) {
    val alarms = context.getSystemService(AlarmManager::class.java) ?: return
    val intent = Intent(context, OrreryWidgetRefreshReceiver::class.java).setAction(ACTION_MIDNIGHT)
    val operation =
        PendingIntent.getBroadcast(
            context,
            0,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    val midnight =
        Calendar.getInstance().apply {
            add(Calendar.DAY_OF_YEAR, 1)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
    alarms.set(AlarmManager.RTC, midnight.timeInMillis, operation)
}

private const val ACTION_MIDNIGHT = "me.kozakov.orrery.sample.widget.MIDNIGHT"

private const val TAG = "OrreryWidgets"

private val REFRESH_TIMEOUT = 8.seconds
```

## Accessibility: content description

The bitmap has no semantics, so **always set a content description** on the view that
shows it. `calendarSnapshotDescription(context, spec)` gives the text the `CalendarSnapshot`
composable announces, in the context's locale: the month and year for a month ("November
2026"), the spoken date range for a week or an agenda strip ("November 9, 2026 – November 15,
2026"):

```kotlin
Image(
    provider = ImageProvider(bitmap.asAndroidBitmap()), // bitmap: ImageBitmap from renderCalendarSnapshot
    contentDescription = calendarSnapshotDescription(context, spec),
    modifier = GlanceModifier.fillMaxSize(),
)
```

Day marks (today, selection, holidays, event dots) and agenda rows are not part of that
text. If they matter, append your own summary (for example "3 events today").

## In the app, share images and other platforms

```kotlin
// The same pixels as the widget, for a preview. Not interactive.
CalendarSnapshot(spec, Modifier.size(250.dp, 110.dp))

// A bitmap from composition, for a share card or a notification.
val bitmap by rememberCalendarSnapshotBitmap(spec, widthPx = 1080, heightPx = 1080)

// Headless on any target: you supply density, fonts and layout direction.
val image = renderCalendarSnapshot(
    spec,
    CalendarSnapshotOutput(widthPx, heightPx, density, fontFamilyResolver, LayoutDirection.Ltr),
    CalendarSnapshotDefaults.colors(colorScheme),
)
```

`CalendarSnapshot` has no size of its own; give it one with the modifier. The common
`renderCalendarSnapshot` formats dates for the current default locale. Outside Android a
`FontFamily.Resolver` usually comes from `LocalFontFamilyResolver`, which is what
`rememberCalendarSnapshotBitmap` does for you.
