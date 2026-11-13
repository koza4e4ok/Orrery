# v1.0.0 Phase 3: Snapshot/Widget Renderer and 1.0 API Freeze — Design

**Date:** 2026-11-13
**Status:** Draft for review (Andrii); implementation by Maya, review/QA by Asuka, security audit by Kaji
**Issue:** #89 (tracking roadmap: #91)
**Base:** main at b21ee08 (v0.9.2 plus Phase 1 and the first #88 steps)
**Owners:** Ritsuko (architecture, API review), Maya (implementation), Asuka (review, final QA), Kaji (security and dependency audit)

## Context

Phase 1 (#86 recurrence, #87 viewport flows) and Phase 2 (#88 dual-month picker, interactive schedule) add the last big features. Phase 3 does two things before 1.0.0:

1. **Offscreen snapshot/widget renderer** (upstream motivation: kizitonwose/Calendar#647). Android home-screen widgets (Glance), notifications and share cards cannot host an interactive Compose calendar. They need an `ImageBitmap` of a compact month or week, in the app's theme, produced without a window or a lifecycle.
2. **1.0.0 API freeze.** After 1.0.0 every public signature is permanent. The ABI machinery already exists (see 2.1), but it only proves that the dump committed in a PR matches the code in that PR. It does not prove that the PR is compatible with what users already run. Phase 3 closes that gap, completes KDoc and removes dead compatibility code.

Ground rules (same as Phases 1 and 2):

- Everything lives in the existing modules; **no new Maven module** unless open decision 1 says otherwise. Pure date logic stays in `orrery-core` (nothing new is needed there); drawing and rendering live in `orrery-compose`.
- New API follows the 1.0 rules in section 2.2 from the first commit: plain classes with defaults for anything that may grow, no new `data class` for configuration.
- Binary compatibility with 0.9.2 is kept (HIDDEN bridges, `*BinaryCompatTest` style) except where section 2.4 explicitly removes a bridge.
- Gradle runs only in CI (no JDK on the agent host); every PR must show green CI. Every implementation PR runs `apiDump` (`updateKotlinAbi`) and commits the dumps.

### Existing solutions check

- **Capturing real composables offscreen.** `GraphicsLayer.toImageBitmap()` only works from inside a composition that is attached to a window. An offscreen `ComposeView` needs a hand-built `ViewTreeLifecycleOwner`, a Recomposer and a measure/layout/draw pass; it is the usual widget hack and is flaky across Android versions. `ImageComposeScene` is desktop/Skia-only and Experimental. Roborazzi and Paparazzi are test-time tools, not runtime dependencies.
- **Glance.** `androidx.glance` is a separate UI tree (RemoteViews). It cannot embed Orrery composables, and it has no custom drawing. Its escape hatch is `ImageProvider(Bitmap)`, which is exactly what a bitmap renderer feeds. Orrery must not depend on Glance.
- **Reuse inside Orrery.** Phase 1 already ships an internal `MiniMonthPainter.draw(DrawScope)` with a cached grid model and a `TextMeasurer` cache (`MiniMonthCanvas.kt`). A `DrawScope` painter needs no composition, no window and no lifecycle: `ImageBitmap` + `Canvas` + `CanvasDrawScope` is enough.

**Decision:** render snapshots with a pure `DrawScope` painter that reuses the MiniMonth primitives. Do not capture real composables. The cost is that the snapshot looks like a "compact month" (the MiniMonth family), not a pixel copy of `HorizontalCalendar`. That is what a widget needs anyway (open decision 2).

**Deliberately NOT building in Phase 3:**

- A Glance composable (`GlanceCalendar`) or any dependency on Glance in a published module. The guide and `:sample` show the 10-line glue.
- iOS WidgetKit or desktop widget helpers. The renderer compiles and is tested on all targets, but only Android has a platform convenience overload.
- Interactivity, animation, tap targets or scrolling in the snapshot. Interactive widgets belong to the host (Glance actions).
- Schedule, agenda, heatmap or year snapshots. Month and week only (open decision 11).
- PNG/JPEG encoding or a share-sheet helper. The guide shows `asAndroidBitmap().compress(...)`; encoding is not an Orrery concern.
- Week numbers, lunar or holiday labels as text in the snapshot (holiday days are tinted, nothing more). No new `orrery_*` string resources.
- Dependency verification metadata for Gradle (open decision 10).

---

## 1. Snapshot/Widget Renderer (`orrery-compose`)

### 1.1 Placement and targets

| Item | Decision |
|---|---|
| Module | `orrery-compose`, package `me.kozakov.orrery.compose` (no new artifact; open decision 1) |
| Files | `CalendarSnapshot.kt` (public model, defaults, composable), `CalendarSnapshotRenderer.kt` (public render functions), `CalendarSnapshotPainter.kt` (internal), `CalendarSnapshot.android.kt` (Android overload) |
| Shared source set | `commonMain`: model, painter, composable, `renderCalendarSnapshot` |
| Android | `androidMain`: `Context` overload and Android defaults (dynamic colour, night mode) |
| Desktop JVM, iosArm64, iosSimulatorArm64, wasmJs | same common code; no platform extras |
| New dependencies | none in published modules. `:sample` only: `androidx.glance:glance-appwidget` (latest stable; Kaji checks it in 3.1) |

### 1.2 Public API

Everything growth-prone is a plain class with defaults and a hand-written `copy` (pattern of Phase 1's `RecurrenceRule`), so adding a field after 1.0 is additive.

```kotlin
package me.kozakov.orrery.compose

/** What to draw: one month or one week. */
public sealed interface CalendarSnapshotRange {
    public class Month(public val month: YearMonth) : CalendarSnapshotRange
    /** The week (per [CalendarSnapshotSpec.firstDayOfWeek]) that contains [date]. */
    public class Week(public val date: LocalDate) : CalendarSnapshotRange
}

public class CalendarSnapshotSpec(
    public val range: CalendarSnapshotRange,
    public val today: LocalDate? = null,                     // highlighted when inside the range
    public val selection: Selection = Selection.None,        // reuses orrery-core Selection (single/range/multi)
    public val dayInfo: DayInfoProvider? = null,              // holiday tint and priority colours (DayInfo)
    public val eventCounts: Map<LocalDate, Int> = emptyMap(), // 1..3 dots per day, > 3 draws three
    public val firstDayOfWeek: DayOfWeek = DayOfWeek.MONDAY,
    public val showTitle: Boolean = true,
    public val showWeekdayLabels: Boolean = true,
    public val showOutsideDays: Boolean = false,             // month mode: leading/trailing days of other months
) {
    public fun copy(/* all fields with current values as defaults */): CalendarSnapshotSpec
    // hand-written equals / hashCode / toString
}

public class CalendarSnapshotColors(
    public val container: Color, public val title: Color, public val weekday: Color,
    public val day: Color, public val outsideDay: Color,
    public val today: Color, public val todayContent: Color,
    public val selection: Color, public val selectionContent: Color, public val rangeBand: Color,
    public val holiday: Color, public val eventDot: Color,
) { public fun copy(/* ... */): CalendarSnapshotColors }

public class CalendarSnapshotStyle(
    public val cornerRadius: Dp = 12.dp,        // container background
    public val contentPadding: Dp = 8.dp,
    public val titleTextSize: TextUnit = 14.sp,
    public val dayTextSize: TextUnit = 12.sp,
    public val weekdayTextSize: TextUnit = 10.sp,
    public val minTextScale: Float = 0.6f,      // text shrinks with the cell down to this factor, then stops
) { public fun copy(/* ... */): CalendarSnapshotStyle }

public object CalendarSnapshotDefaults {
    /** Theme-aware colours from the current [MaterialTheme.colorScheme]. */
    @Composable public fun colors(): CalendarSnapshotColors
    /** Same mapping from an explicit scheme, usable outside composition. */
    public fun colors(colorScheme: ColorScheme): CalendarSnapshotColors
    public fun style(): CalendarSnapshotStyle
}

/** Everything the renderer needs to know about the destination, grouped so that new fields stay additive. */
public class CalendarSnapshotOutput(
    public val widthPx: Int,
    public val heightPx: Int,
    public val density: Density,                      // carries fontScale
    public val fontFamilyResolver: FontFamily.Resolver,
    public val layoutDirection: LayoutDirection = LayoutDirection.Ltr,
) { public fun copy(/* ... */): CalendarSnapshotOutput }

/** In-app composable; same painter as the renderer, so previews match widgets exactly. Not interactive. */
@Composable
public fun CalendarSnapshot(
    spec: CalendarSnapshotSpec,
    modifier: Modifier = Modifier,
    colors: CalendarSnapshotColors = CalendarSnapshotDefaults.colors(),
    style: CalendarSnapshotStyle = CalendarSnapshotDefaults.style(),
)

/** Headless render. Safe to call from any dispatcher; runs the drawing on Dispatchers.Default. */
public suspend fun renderCalendarSnapshot(
    spec: CalendarSnapshotSpec,
    output: CalendarSnapshotOutput,
    colors: CalendarSnapshotColors,
    style: CalendarSnapshotStyle = CalendarSnapshotDefaults.style(),
): ImageBitmap

/** For a composable that needs the bitmap (share-card preview, notification builder). Recomputes when inputs change. */
@Composable
public fun rememberCalendarSnapshotBitmap(
    spec: CalendarSnapshotSpec,
    widthPx: Int, heightPx: Int,
    colors: CalendarSnapshotColors = CalendarSnapshotDefaults.colors(),
    style: CalendarSnapshotStyle = CalendarSnapshotDefaults.style(),
): State<ImageBitmap?>
```

Android convenience (`androidMain`, same file name with the `.android` suffix):

```kotlin
/** Uses the context's resources: density, font scale, locale, layout direction, and its night mode and dynamic colour for the default colours. Does not retain [context]. */
public suspend fun renderCalendarSnapshot(
    context: Context,
    spec: CalendarSnapshotSpec,
    widthPx: Int, heightPx: Int,
    colors: CalendarSnapshotColors = CalendarSnapshotDefaults.colors(context),
    style: CalendarSnapshotStyle = CalendarSnapshotDefaults.style(),
): ImageBitmap

public fun CalendarSnapshotDefaults.colors(context: Context): CalendarSnapshotColors
    // dynamicLightColorScheme/dynamicDarkColorScheme on API 31+, otherwise the static Orrery light/dark scheme by uiMode
```

Glance glue shown in the guide and `:sample` (not shipped in the library):

```kotlin
override suspend fun provideGlance(context: Context, id: GlanceId) {
    val px = widthHeightPxFor(id)                       // from LocalSize via the widget's size mode
    val bitmap = renderCalendarSnapshot(context, spec(Clock.System.todayIn(TimeZone.currentSystemDefault())), px.width, px.height)
    provideContent { Image(ImageProvider(bitmap.asAndroidBitmap()), contentDescription = "Calendar", modifier = GlanceModifier.fillMaxSize()) }
}
```

### 1.3 Behaviour contract

1. **Determinism.** The same spec, colours, style and output give the same pixels on the same platform and font setup. No clock reads: "today" comes from the spec.
2. **Month mode** draws a title row (month name and year via the existing `PlatformDateFormat`), a weekday row and 4 to 6 week rows, in a grid that fills the content box. Week mode draws title (range of the week), weekday row and one day row. `showOutsideDays` draws leading and trailing days with `outsideDay` colour and no marks.
3. **Marks** are painted in this order: range band, selection pill (RTL-aware rounded ends as in MiniMonth), today ring or fill, day number, holiday tint (`DayInfo.isHoliday` or priority colour when set), up to three event dots. Selected days use `selectionContent` for the number.
4. **Sizing.** Cell size is derived from the output size. Text size is `min(requested, cell × factor)` and never below `minTextScale × requested`; below that, labels are dropped (title first, then weekday row) instead of overlapping. Font scale comes from `density.fontScale` and is honoured, but the grid never overflows: cells shrink first, labels drop second.
5. **Limits.** `widthPx` and `heightPx` must be in `1..4096` and `widthPx × heightPx ≤ 4_194_304` (about 16 MB at ARGB_8888); violations throw `IllegalArgumentException` naming the values. Android's RemoteViews also caps total bitmap memory per widget, so the guide tells widget authors to render at the widget's actual pixel size, not larger.
6. **No leaks.** The Android overload reads resources synchronously and keeps no reference to `Context`; the painter keeps no static state (the text cache lives inside one render call).
7. **Accessibility.** The composable sets one `contentDescription` (month or week title plus the range). The bitmap has no semantics; the guide requires the host to set `contentDescription` on the Glance `Image`. No new string resources are added.
8. **Threading.** All drawing is software (Canvas into an in-memory bitmap): no blur, shadow, `RenderEffect` or layer APIs, so results do not depend on a GPU. `renderCalendarSnapshot` switches to `Dispatchers.Default` itself.

### 1.4 Implementation notes

- Add `CalendarSnapshotPainter` (internal) next to `MiniMonthPainter`. Extract the shared drawing primitives (pill, today ring, dot row, text-cache access) from `MiniMonthCanvas.kt` into internal helpers used by both painters; the MiniMonth goldens must not change (that is the regression test for the refactor).
- `CalendarSnapshotColors` from a `ColorScheme`: container `surface`, title `onSurface`, weekday `onSurfaceVariant`, day `onSurface`, outside `outline`, today `primary` / `onPrimary`, selection `primary` / `onPrimary`, rangeBand `primaryContainer`, holiday `tertiary`, eventDot `secondary`. These match MiniMonth's mapping so the two stay visually related.
- The renderer builds an `ImageBitmap(widthPx, heightPx)`, wraps it in `Canvas`, and calls `CanvasDrawScope().draw(density, layoutDirection, canvas, size) { painter.draw(this) }`. A `TextMeasurer(fontFamilyResolver, density, layoutDirection, cacheSize = 0)` is created per call.
- **Known unknown (spike first, task 1):** whether a `FontFamily.Resolver` can be created outside composition on desktop, iOS and wasmJs (on Android `createFontFamilyResolver(context)` is public). Fallback if not: the common `renderCalendarSnapshot` stays, and non-Android callers obtain the resolver from `LocalFontFamilyResolver` in a composable (`rememberCalendarSnapshotBitmap` already covers this). The public signatures above do not change either way because the resolver is an input of `CalendarSnapshotOutput`.

### 1.5 Tests

| Layer | Source set | What |
|---|---|---|
| Layout model | `commonTest` | week/month grid for all `firstDayOfWeek` values; 4, 5 and 6-row months; February 2026 (4 rows exactly); outside days; today outside the range; selection and range masks; dot capping; size limit validation; text-drop thresholds as pure functions |
| Golden screenshots | `androidHostTest` (Roborazzi, same style as `MiniMonth`/`CalendarStyleSnapshotTest`) | month and week × light/dark; selection range + holidays + dots; 2×2 widget (110×110 dp) and 4×2 (250×110 dp); RTL; font scale 1.3 and 2.0; one non-Latin locale (uk) for the title |
| Parity | `androidHostTest` | the composable and `renderCalendarSnapshot` produce identical pixels for the same input (guards against the two paths drifting) |
| Android overload | `androidHostTest` (Robolectric) | night mode switches default colours; font scale and density from the context; context is not retained (weak-reference check after the call) |
| Cross-platform smoke | `desktopTest`, `wasmJsTest`, `iosSimulatorArm64Test` (macOS CI job) | render returns the requested size; bitmap is not blank (distinct pixel count above a threshold); two renders of the same input are equal. No goldens: fonts differ per platform |
| Sample | CI `:sample:assembleDebug` | the Glance widget compiles; no instrumented widget test |

Why goldens only on Android: the repo already records Roborazzi there, and text rasterisation is not portable across Skia targets, so a cross-target golden would be permanently flaky.

### 1.6 Risks

| # | Risk | Mitigation |
|---|---|---|
| R1 | Font resolver off-composition is not available on every target | spike (task 1); fallback in 1.4 |
| R2 | Snapshot drifts visually from MiniMonth | shared primitives (1.4), parity test, same colour mapping |
| R3 | Widget bitmap memory (RemoteViews limit) crashes the host launcher | size and area caps (1.3.5), guide section "Sizing", sample renders at widget pixel size |
| R4 | Glance API churn | Glance only in `:sample`; library API has no Glance types |
| R5 | Dynamic colour on API 31+ differs from static fallback | explicit `colors` parameter wins; both paths get a golden |
| R6 | Brand-new API frozen at 1.0 without field experience | plain classes + `Output` grouping keep growth additive; alternative: opt-in annotation (open decision 3) |
| R7 | Locale and time zone of the widget process differ from the app | locale comes from the Context's configuration; "today" is always passed in; documented |
| R8 | Painter cost on frequent widget updates | one cheap software draw (< 5 ms target at 4096 px² on desktop, informational, not a CI gate); Android widgets update at most every 30 minutes by platform rule |
