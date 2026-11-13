# v1.0.0 Phase 3: Snapshot/Widget Renderer and 1.0 API Freeze — Design

**Date:** 2026-11-13
**Status:** Decided (Andrii, 2026-11-13, NERV Dev); revised to his choices. Implementation by Maya, review/QA by Asuka, API review and security audit by Ritsuko
**Issue:** #89 (tracking roadmap: #91)
**Base:** main at b17cdaa (v0.9.2 plus Phase 1 and #88 steps 1-2)
**Owners:** Ritsuko (architecture, API review, security and dependency audit), Maya (implementation), Asuka (review, final QA)
**Decision record:** shared ADR 0007 (accepted)

## Context

Phase 1 (#86 recurrence, #87 viewport flows) and Phase 2 (#88 dual-month picker, interactive schedule) add the last big features. Phase 3 does two things before 1.0.0:

1. **Offscreen snapshot/widget renderer** (upstream motivation: kizitonwose/Calendar#647). Android home-screen widgets (Glance), notifications and share cards cannot host an interactive Compose calendar. They need an `ImageBitmap` of a compact month, week or agenda strip, in the app's theme, produced without a window or a lifecycle. It ships as the new, **experimental** module `orrery-widget`.
2. **1.0.0 API freeze.** After 1.0.0 every stable public signature is permanent. The ABI machinery exists (see 2.1) but only proves that the dump committed in a PR matches the code in that PR. Phase 3 closes that gap, completes KDoc, and uses the 1.0.0 boundary for a **deliberate, one-time break** of 0.9.x binaries (all 43 HIDDEN bridges removed, growth-prone data classes converted to plain classes), documented in a migration guide.

Ground rules:

- One new published module, `orrery-widget` (decision 1). `orrery-core` gains two small annotations; everything else stays where it is. Pure date logic stays in `orrery-core`; shared drawing primitives stay in `orrery-compose`.
- New stable API follows the 1.0 rules in 2.2 from the first commit (plain classes with defaults, no new `data class` for configuration). The renderer API is experimental (2.2, rule 7) and carries no compatibility promise in 1.0.x.
- **1.0.0 is a deliberate break against 0.9.x** (decisions 5 and 8). It is a one-time event, announced by the RC, the CHANGELOG and the migration guide. From `1.0.0-rc.1` on, compatibility is protected by the gate (2.3).
- Gradle runs only in CI (no JDK on the agent host); every PR must show green CI. Every implementation PR runs `apiDump` (`updateKotlinAbi`) and commits the dumps.

### Existing solutions check

- **Capturing real composables offscreen.** `GraphicsLayer.toImageBitmap()` only works from inside a composition that is attached to a window. An offscreen `ComposeView` needs a hand-built `ViewTreeLifecycleOwner`, a Recomposer and a measure/layout/draw pass; it is the usual widget hack and is flaky across Android versions. `ImageComposeScene` is desktop/Skia-only and Experimental. Roborazzi and Paparazzi are test-time tools, not runtime dependencies.
- **Glance.** `androidx.glance` is a separate UI tree (RemoteViews). It cannot embed Orrery composables, and it has no custom drawing. Its escape hatch is `ImageProvider(Bitmap)`, which is exactly what a bitmap renderer feeds. Orrery must not depend on Glance.
- **Reuse inside Orrery.** Phase 1 already ships an internal `MiniMonthPainter.draw(DrawScope)` with a cached grid model and a `TextMeasurer` cache (`MiniMonthCanvas.kt`). A `DrawScope` painter needs no composition, no window and no lifecycle: `ImageBitmap` + `Canvas` + `CanvasDrawScope` is enough.

**Decision:** render snapshots with a pure `DrawScope` painter that reuses the MiniMonth primitives. Do not capture real composables. The cost is that the snapshot looks like a "compact month" (the MiniMonth family), not a pixel copy of `HorizontalCalendar`. That is what a widget needs anyway (decision 2, decided).

**Deliberately NOT building in Phase 3:**

- A Glance composable (`GlanceCalendar`) or any dependency on Glance in a published module. The guide and `:sample` show the glue (decision 4).
- iOS WidgetKit or desktop widget helpers. The renderer compiles and is tested on all targets, but only Android has a platform convenience overload.
- Interactivity, animation, tap targets or scrolling in the snapshot. Interactive widgets belong to the host (Glance actions).
- Time-grid schedule, heatmap or year snapshots. Month, week and an agenda strip only (decision 11).
- PNG/JPEG encoding or a share-sheet helper. The guide shows `asAndroidBitmap().compress(...)`.
- Week numbers, lunar or holiday labels as text in the snapshot (holiday days are tinted). No new `orrery_*` string resources.
- A stable renderer API in 1.0.x (decision 3): it graduates in 1.1.
- Gradle dependency verification metadata (decision 10, deferred).

---

## 1. Snapshot/Widget Renderer (`orrery-widget`, experimental)

### 1.1 Placement and targets

| Item | Decision |
|---|---|
| Module | **new `orrery-widget`**, artifact `me.kozakov.orrery:orrery-widget`, package `me.kozakov.orrery.widget` (decision 1) |
| Build | conventions `orrery.cmp-library` and `orrery.publish`, `gradle.properties` with `POM_ARTIFACT_ID=orrery-widget` / name / description, Android namespace `me.kozakov.orrery.widget`; same targets as `orrery-compose` |
| Dependencies | `api(project(":orrery-core"))` (`Selection`, `CalendarEvent`, `DayInfoProvider` appear in signatures); `implementation(project(":orrery-compose"))` for the shared primitives (1.4), `PlatformDateFormat` and the colour mapping; the same Compose dependency blocks as `orrery-compose`. The split does not make the dependency graph lighter (Material3 is still needed for `ColorScheme`); it isolates the experimental API in its own artefact, dump and release notes |
| Files | `CalendarSnapshot.kt` (public model, defaults, composable), `CalendarSnapshotRenderer.kt` (render functions), `CalendarSnapshotPainter.kt` (internal), `CalendarSnapshot.android.kt` (Android overload) |
| Shared source set | `commonMain`: model, painter, composable, `renderCalendarSnapshot` |
| Android | `androidMain`: `Context` overload and Android defaults (dynamic colour, night mode) |
| Desktop JVM, iosArm64, iosSimulatorArm64, wasmJs | same common code; no platform extras |
| Stability | every public declaration is `@ExperimentalOrreryApi` (2.2 rule 7); the module's ABI dumps are committed but contain no declarations, and CI asserts that (2.3) |
| Wiring | `settings.gradle.kts` include; `ci.yml` (test tasks, `publishToMavenLocal`, AAR and POM check, iOS job); Dokka aggregation (`dokka(project(":orrery-widget"))`); README module table and install snippet; `docs/RELEASING.md`; Dokka and docs site pick it up from the existing tag workflows |
| New dependencies | none in published modules. `:sample` only: `androidx.glance:glance-appwidget` (latest stable; Ritsuko checks it in 3.1) |

### 1.2 Public API

Everything growth-prone is a plain class with defaults and a hand-written `copy` (pattern of Phase 1's `RecurrenceRule`), so adding a field later is additive. Every public declaration in this section carries `@ExperimentalOrreryApi` (omitted from the snippets): the 1.0.x line makes no compatibility promise for it (decision 3).

```kotlin
package me.kozakov.orrery.widget

/** What to draw: one month, one week or an agenda strip. */
public sealed interface CalendarSnapshotRange {
    public class Month(public val month: YearMonth) : CalendarSnapshotRange
    /** The week (per [CalendarSnapshotSpec.firstDayOfWeek]) that contains [date]. */
    public class Week(public val date: LocalDate) : CalendarSnapshotRange
    /** Events grouped by day from [start] for [days] days, at most [maxRows] event rows; events come from [CalendarSnapshotSpec.agendaEvents]. */
    public class Agenda(public val start: LocalDate, public val days: Int = 7, public val maxRows: Int = 4) : CalendarSnapshotRange
}

public class CalendarSnapshotSpec(
    public val range: CalendarSnapshotRange,
    public val today: LocalDate? = null,                     // highlighted when inside the range
    public val selection: Selection = Selection.None,        // reuses orrery-core Selection (single/range/multi)
    public val dayInfo: DayInfoProvider? = null,              // holiday tint and priority colours (DayInfo)
    public val eventCounts: Map<LocalDate, Int> = emptyMap(), // 1..3 dots per day, > 3 draws three
    public val agendaEvents: List<CalendarEvent<*>> = emptyList(), // Agenda range only; caller expands recurrences with the Phase 1 API
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
2. **Month mode** draws a title row (month name and year via the existing `PlatformDateFormat`), a weekday row and 4 to 6 week rows, in a grid that fills the content box. Week mode draws title (range of the week), weekday row and one day row. **Agenda mode** draws a title (the date range) and up to `maxRows` event rows under day headings (`PlatformDateFormat`): a colour bar (event colour, else `eventDot`), an optional start time and a one-line title ellipsised to the width; all-day events come first within a day; rows that do not fit are dropped, never clipped; an empty agenda draws the title only. Only caller data and date formatting are drawn, no new resources. `showOutsideDays` draws leading and trailing days with `outsideDay` colour and no marks.
3. **Marks** are painted in this order: range band, selection pill (RTL-aware rounded ends as in MiniMonth), today ring or fill, day number, holiday tint (`DayInfo.isHoliday` or priority colour when set), up to three event dots. Selected days use `selectionContent` for the number.
4. **Sizing.** Cell size is derived from the output size. Text size is `min(requested, cell × factor)` and never below `minTextScale × requested`; below that, labels are dropped (title first, then weekday row) instead of overlapping. Font scale comes from `density.fontScale` and is honoured, but the grid never overflows: cells shrink first, labels drop second.
5. **Limits.** `widthPx` and `heightPx` must be in `1..4096` and `widthPx × heightPx ≤ 4_194_304` (about 16 MB at ARGB_8888); violations throw `IllegalArgumentException` naming the values. Android's RemoteViews also caps total bitmap memory per widget, so the guide tells widget authors to render at the widget's actual pixel size, not larger.
6. **No leaks.** The Android overload reads resources synchronously and keeps no reference to `Context`; the painter keeps no static state (the text cache lives inside one render call).
7. **Accessibility.** The composable sets one `contentDescription` (month or week title plus the range). The bitmap has no semantics; the guide requires the host to set `contentDescription` on the Glance `Image`. No new string resources are added.
8. **Threading.** All drawing is software (Canvas into an in-memory bitmap): no blur, shadow, `RenderEffect` or layer APIs, so results do not depend on a GPU. `renderCalendarSnapshot` switches to `Dispatchers.Default` itself.

### 1.4 Implementation notes

- **Sharing the MiniMonth drawing primitives across modules.** The primitives (selection pill, today ring, dot row, text cache, month grid model) are `internal` in `orrery-compose`, and Kotlin `internal` does not cross module boundaries. Options:

| Option | Verdict |
|---|---|
| A. Extract a minimal set into `orrery-compose` as public `@InternalOrreryApi` (`@RequiresOptIn(ERROR)`, in `orrery-core`, excluded from the ABI dump and from Dokka) | **Chosen.** One implementation, MiniMonth goldens guard it, no new artefact. Cost: a few public-but-unsupported declarations and a same-version rule |
| B. Copy about 150 lines into `orrery-widget` | Fallback if the dump filter cannot be made to work (spike, task 1). Zero public surface but the two painters can drift (parity test only covers widget paths) |
| C. Third module (`orrery-draw`) | Rejected: another published artefact for internal code |
| D. Move `MiniMonthPainter` into the widget module | Rejected: `orrery-compose` would depend on the experimental module |

  The shared set is deliberately small: `drawSelectionPill`, `drawTodayMark`, `drawEventDots`, a per-call text cache and `MonthGridModel`. Same-version rule: `orrery-widget` is published with an exact pin on the `orrery-compose` version (project dependency, verified in the spike and in the POM check), and the docs say to use one version for all `orrery-*` artefacts. `@InternalOrreryApi` declarations get no compatibility promise and no gate coverage (2.3); the MiniMonth goldens must not change in the extraction PR.
- `CalendarSnapshotColors` from a `ColorScheme`: container `surface`, title `onSurface`, weekday `onSurfaceVariant`, day `onSurface`, outside `outline`, today `primary` / `onPrimary`, selection `primary` / `onPrimary`, rangeBand `primaryContainer`, holiday `tertiary`, eventDot `secondary`. These match MiniMonth's mapping so the two stay visually related.
- The renderer builds an `ImageBitmap(widthPx, heightPx)`, wraps it in `Canvas`, and calls `CanvasDrawScope().draw(density, layoutDirection, canvas, size) { painter.draw(this) }`. A `TextMeasurer(fontFamilyResolver, density, layoutDirection, cacheSize = 0)` is created per call.
- **Known unknown (spike first, task 1):** whether a `FontFamily.Resolver` can be created outside composition on desktop, iOS and wasmJs (on Android `createFontFamilyResolver(context)` is public). Fallback if not: the common `renderCalendarSnapshot` stays, and non-Android callers obtain the resolver from `LocalFontFamilyResolver` in a composable (`rememberCalendarSnapshotBitmap` already covers this). The public signatures do not change either way because the resolver is an input of `CalendarSnapshotOutput`.
- **Second unknown (same spike):** the KGP `abiValidation { filters { exclude { annotatedWith } } }` block (names to be verified) hides `@ExperimentalOrreryApi` and `@InternalOrreryApi` declarations from the JVM and klib dumps. Fallback: exclude `me.kozakov.orrery.widget.**` by package and list the internal primitives' package.

### 1.5 Tests

All widget tests live in `orrery-widget` (same source-set layout and Roborazzi setup as `orrery-compose`); the MiniMonth goldens stay in `orrery-compose` and must not change.

| Layer | Source set | What |
|---|---|---|
| Layout model | `commonTest` | week/month grid for all `firstDayOfWeek` values; 4, 5 and 6-row months; February 2026 (4 rows exactly); outside days; today outside the range; selection and range masks; dot capping; size limit validation; text-drop thresholds as pure functions; agenda row packing (all-day first, row cap, empty agenda, multi-day events, long and RTL titles) |
| Golden screenshots | `androidHostTest` (Roborazzi, same style as `MiniMonth`/`CalendarStyleSnapshotTest`) | month, week and agenda strip × light/dark; selection range + holidays + dots; 2×2 widget (110×110 dp) and 4×2 (250×110 dp); RTL; font scale 1.3 and 2.0; one non-Latin locale (uk) for the title |
| Parity | `androidHostTest` | the composable and `renderCalendarSnapshot` produce identical pixels for the same input (guards against the two paths drifting) |
| Android overload | `androidHostTest` (Robolectric) | night mode switches default colours; font scale and density from the context; context is not retained (weak-reference check after the call) |
| Cross-platform smoke | `desktopTest`, `wasmJsTest`, `iosSimulatorArm64Test` (macOS CI job) | render returns the requested size; bitmap is not blank (distinct pixel count above a threshold); two renders of the same input are equal. No goldens: fonts differ per platform |
| Sample | CI `:sample:assembleDebug` | the Glance widget compiles; no instrumented widget test |

Why goldens only on Android: the repo already records Roborazzi there, and text rasterisation is not portable across Skia targets, so a cross-target golden would be permanently flaky.

### 1.6 Risks

| # | Risk | Mitigation |
|---|---|---|
| R1 | Font resolver off-composition is not available on every target | spike (task 1); fallback in 1.4 |
| R2 | Snapshot drifts visually from MiniMonth | shared `@InternalOrreryApi` primitives (1.4), parity test, same colour mapping |
| R3 | Widget bitmap memory (RemoteViews limit) crashes the host launcher | size and area caps (1.3.5), guide section "Sizing", sample renders at widget pixel size |
| R4 | Glance API churn | Glance only in `:sample`; library API has no Glance types |
| R5 | Dynamic colour on API 31+ differs from static fallback | explicit `colors` parameter wins; both paths get a golden |
| R6 | Brand-new API without field experience | decided: `@ExperimentalOrreryApi` (WARNING) for all of 1.0.x, graduation in 1.1; plain classes + `Output` grouping keep the later freeze additive |
| R7 | Locale and time zone of the widget process differ from the app | locale comes from the Context's configuration; "today" is always passed in; documented |
| R8 | Painter cost on frequent widget updates | one cheap software draw (< 5 ms target at 4096 px² on desktop, informational, not a CI gate); Android widgets update at most every 30 minutes by platform rule |
| R9 | Public `@InternalOrreryApi` primitives get used by apps, or `orrery-widget` and `orrery-compose` versions are mixed (runtime `NoSuchMethodError`) | ERROR-level opt-in, dump- and Dokka-excluded, exact version pin in the widget POM, guide says one version for all `orrery-*`; spike checks Gradle resolution |
| R10 | Agenda strip is the first feature drawing caller-supplied text (titles) | one line, ellipsis, row and size caps, RTL and non-Latin goldens, titles never logged |
| R11 | The annotation-based ABI filter is experimental or behaves differently on klib dumps | spike (task 1); fallback to package exclusion (1.4); the CI check that the widget dump is empty catches a leak |
| R12 | Experimental module stays experimental forever | graduation criteria in 2.2 rule 7, planned for 1.1, tracked on #91 |

---

## 2. 1.0.0 Public API Freeze Plan

### 2.1 Where we are (facts from main at b17cdaa)

| Area | State |
|---|---|
| Explicit API mode | on for all five modules (`explicitApi()` in the build-logic conventions) |
| ABI validation | KGP built-in `abiValidation` (successor of binary-compatibility-validator) in every convention: JVM dumps (`orrery-core`, `-lunar`, `-calendars`, `-holidays`: `*.api`; `orrery-compose`: `api/android` and `api/desktop`) plus merged klib dumps (`*.klib.api`: iOS and wasmJs) |
| CI gate | `checkKotlinAbi` in the PR job; on failure CI runs `updateKotlinAbi` and uploads the dumps |
| Tags | `v0.9.2` (be5b1d5) is the latest release |
| Deprecations | 43 `@Deprecated`, **all** `level = HIDDEN` (binary-compat bridges for ctor/copy/overload signatures). No WARNING or ERROR deprecations exist; nothing to graduate. All 43 are removed at 1.0.0 (decision 5, 2.4) |
| Opt-in API | none exposed; Phase 3 adds `@ExperimentalOrreryApi` and `@InternalOrreryApi` (2.2). `@OptIn(ExperimentalFoundationApi)` appears only inside implementations (`Agenda`, `Calendars`) |
| KDoc | no gate. A rough line scan (public declaration with no KDoc on the previous line) flags about 350 of 714 declarations in the five modules, but many are primary-constructor properties covered by a class-level `@property` tag, so the real number must come from a real tool (task 12). Largest gaps by scan: `CalendarDefaults`, `ScheduleDefaults`, `DatePickerDefaults`, `ScheduleLayout`, `Selection`, `CalendarEvent`, `DayInfoProvider`, `RecurrenceRule`, `YearCalendar` |
| Docs | Dokka 2.2.0 aggregated; deployed on `v*` tags (`docs.yml`) |
| Publish | `publish.yml` on `v*` tags runs `publishToMavenCentral` directly: **no ABI check, no tests** before an irreversible Central publish |
| Repo security | secret scanning and push protection enabled; Dependabot alerts and security updates disabled; no `dependabot.yml`; no `SECURITY.md` |

**The gap.** `checkKotlinAbi` compares the code with the dump committed in the same PR. A PR that deletes a public method and also commits the shrunken dump is green. Until now that was acceptable (pre-1.0); at 1.0 it is exactly the failure the gate must catch.

### 2.2 1.0 API rules (apply to new and existing public API)

1. **Growth.** New, and after the 1.0 conversion (2.4) existing, growth-prone configuration, state and result types are plain classes with defaults and a hand-written `copy`. A public `data class` is only a small closed value type that can never grow (e.g. `ByDay`, `LunarDate`); the ledger lists each remaining one with the reason.
2. **Parameters.** New parameters are appended with defaults; the old signature stays as a HIDDEN bridge from then on (every bridge after 1.0.0 is new; 1.0.0 itself ships none). Lesson from #63: a HIDDEN bridge of a non-composable function with defaults loses its `$default` method, so add an overload instead.
3. **Enums and sealed types.** Public enums and sealed interfaces that may gain members (`Frequency`, `SelectionMode`, `RangeEndpointStyle`, `CalendarSnapshotRange`, …) document "do not rely on exhaustive `when` across versions" in KDoc. The ledger lists each one.
4. **Visibility.** Everything not meant for callers is `internal`. Stable API never mentions an internal, `@InternalOrreryApi` or `@ExperimentalOrreryApi` type in its signature. `@OptIn` markers stay out of public signatures.
5. **Compose signatures.** Composables keep `modifier` as the first optional parameter, state and slots as documented parameters, no default argument that reads mutable global state.
6. **Deprecation policy from 1.0.** WARNING for at least one minor release (with `ReplaceWith`), then HIDDEN, then removal only in a major. 1.0.0 starts with no HIDDEN bridges.
7. **Experimental API.** `@ExperimentalOrreryApi` (`@RequiresOptIn(level = WARNING)`, defined in `orrery-core`, binary retention) marks every public declaration of `orrery-widget`. No source or binary promise in 1.0.x; changes are listed under an "Experimental" heading in the CHANGELOG; the declarations are excluded from the ABI dump and the compat gate. **Graduation in 1.1:** remove the annotation, review the API (Ritsuko), let the dumps gain the declarations (a pure addition), baseline moves to the 1.1 release candidate. Criteria: used by the sample and at least one real app for one release, no open API change on #91.
8. **Internal cross-module API.** `@InternalOrreryApi` (`@RequiresOptIn(level = ERROR)`, in `orrery-core`) is for declarations that must be public only so a sibling `orrery-*` module can use them. No promise, excluded from dumps and Dokka, used only inside this repository.

### 2.3 ABI baseline and CI gate

**Goal:** from the RC on, a PR may only **add** to the stable public ABI relative to the last release; removals and signature changes must be impossible to merge silently. The 1.0 breaks themselves (2.4) pass through an explicit allow-list.

Design (decision 7, mechanism chosen: `tools/api-compat.py` against a baseline tag):

- **Baseline = dumps at a git ref** (variable `API_BASELINE_REF` in `ci.yml`). It is `v0.9.2` while the 1.0 breaks are being built; **`v1.0.0-rc.1` from the first commit after that tag exists**; `v1.0.0` after the release. The job checks the baseline dumps out of git history (`git show <ref>:<module>/api/...`) next to the PR's dumps. A module that does not exist in the baseline (`orrery-widget`) counts as all additions.
- **Script `tools/api-compat.py`** (stdlib Python; unit-tested with fixture dumps in `tools/api-compat-selftest/`): for every `*.api` and `*.klib.api` pair, normalise whitespace and drop comment lines, then fail if **any baseline line is missing from the PR dump** (a removed class, member or changed signature). For `*.klib.api` the `// Targets: [...]` header of each baseline file must be a subset of the PR's. Additions are printed in the job summary so reviewers see the delta. `--emit-removed` prints the missing lines grouped by class, for generating allow-list entries from the ledger (2.4).
- **Allow-list `tools/api-compat-allow.txt`.** Entries are either one dump line or a whole class: `<module> | <dump line or class name> | <reason: 1.0-bridge or 1.0-dataclass> | <ledger row>`. Entries are generated from the ledger by the script, never typed freehand, and Ritsuko reviews the generated file. Lifecycle: it is used with baseline `v0.9.2` (PR gate and the `publish.yml` gate of the RC tag itself); **the first PR after `v1.0.0-rc.1` is tagged switches the baseline to the RC and empties the file**, and CI fails if the baseline is `rc.1` or later and the file is not empty. A break discovered during the soak needs Andrii's approval and a new RC (`rc.2`) with temporary entries against `rc.1`.
- **Experimental and internal API (decision 3).** `@ExperimentalOrreryApi` and `@InternalOrreryApi` declarations are filtered out of the dumps by the `abiValidation` filters in the conventions, so the gate cannot see them: this is intended, not a hole. To make sure the filter really covers the module, `api-compat.py --require-empty orrery-widget` fails if the `orrery-widget` dump contains any declaration (an unannotated public declaration is exactly that). The check is removed when the module graduates in 1.1.
- **Jobs:** (1) PR job (`ci.yml`): `checkKotlinAbi` (dump matches code) **and** `python3 tools/api-compat.py` (dump compatible with baseline). (2) `publish.yml`: before `publishToMavenCentral` run `./gradlew checkKotlinAbi`, `api-compat.py` with `API_BASELINE_REF` = the previous tag, and the module test tasks of `ci.yml` that are cheap on Linux. A tag that does not pass never publishes.
- **Binary-compat tests** stay for bridges added after 1.0.0 (`DayInfoBinaryCompatTest` pattern); the tests for the 43 removed bridges are deleted with them.
- **Report mode first.** Until every 1.0 break PR (tasks 9 and 10) is merged, the job runs with `continue-on-error` and only reports the delta against `v0.9.2`; it becomes required at the freeze (task 14).

**Not covered by a text dump** (documented in `docs/API-STABILITY.md`): Compose compiler-generated artefacts (`$default`, group keys) and behaviour. Behaviour compatibility is protected by the Roborazzi goldens and unit tests.

**`docs/API-STABILITY.md` states (decision 12):** the promise covers Orrery's own API only (the six modules' stable declarations; not `@ExperimentalOrreryApi`, not `@InternalOrreryApi`); Orrery is built and tested with Kotlin 2.4.21, Compose Multiplatform 1.12.2 with material3 1.9.0, and on Android Compose BOM 2026.09.00 (the "tested with" versions are updated per release); Compose compiler internals and Compose's own API changes are outside the promise; a Compose version range is not promised (no CI matrix).

### 2.4 The 1.0 breaks and the audit ledger

Andrii chose a clean 1.0.0 over compatibility with 0.9.x (decisions 5 and 8). It is a one-time break, executed once, documented once.

**5B: remove all 43 HIDDEN bridges.** They are invisible to source, so a recompile against 1.0 needs no source change; the break is binary only: an app or library **compiled against ≤ 0.9.2** that links an old signature fails with `NoSuchMethodError` until it is rebuilt against 1.0. Counts (main at b17cdaa): `orrery-compose` 36 (`CalendarDefaults` 10, `ScheduleDefaults` 6, `Calendars` 5, `ScheduleView` 4, `DateRangePickerDialog`, `DayDecorators`, `NavHeader` 2 each, `AgendaDefaults`, `CollapsibleCalendarScaffold`, `DatePickerState`, `SelectionState`, `YearCalendar` 1 each), `orrery-core` 7 (`CalendarEvent`, `DayInfoProvider`, `Selection` 2 each, `SelectionEngine` 1). Their `*BinaryCompatTest` files go with them.

**8B: convert growth-prone public data classes to plain classes** (defaults on the constructor, hand-written `copy`, `equals`, `hashCode`, `toString`, no `componentN`). Effects: destructuring declarations stop compiling (source break), generated members disappear from the dump (binary break), constructor and `copy` call sites keep their shape where the hand-written members mirror the old ones. Criterion: the type is configuration, state or a result that plausibly gains fields, or already needed a bridge. Proposed split (final in the ledger; Andrii may veto the "B" list):

| Tier | Types |
|---|---|
| **A: convert (already needed bridges, proven growth)** | `CalendarEvent` (core), `DayInfo` (core), `SelectionMode.Single` (core; its siblings `Range`, `Multi`, `Week`, `Month` convert with it for consistency) |
| **B: convert (config, state, result types)** | core: `Selection`, `SelectionResult`, `HeatmapSummary`, `PositionedEvent`, `DayScheduleLayout`, `AllDayBanner`, `WeekEventBanner`, `WeekBannerPackResult`, `CalendarDay`, `CalendarWeek`, `CalendarMonth`; compose: `VisibleMonthChange`, `VisibleWeekChange`, `CalendarDefaults.Ring`, `CalendarFormats.Fixed` and `FillViewport`; holidays: `HolidayRule`, `PublicHoliday`; lunar: `LunarDayMetadata` |
| **C: keep as `data class` (closed value types)** | `ByDay`, `LunarDate`, `HijriDate`, `PersianDate`, `CalendarEvent.AllDay` and `Timed` (time values), the `HolidayRule` variants (`Fixed`, `NthWeekday`, `WeekdayRelative`, `EasterOffset`, `HijriFixed`, `PersianFixed`, `OneOff`: closed rule shapes), the `SelectionResult` reasons (`OutOfRange`, `Intercepted`, `RangeTooShort`, `RangeTooLong`, `TooManyDays`) |

**Ledger.** Task 8 produces `docs/api-audit-1.0.md`, generated mechanically from the dumps (`git diff v0.9.2 main -- '*/api/*'`) and the sources:

1. Every bridge: module, old signature, the signature it forwards to, first release containing the old signature (informational: it tells the migration guide which app vintages are affected).
2. Every public `data class`: tier A/B/C with the reason, and the `componentN`/`copy` surface that goes away.
3. The other audit items per module: public enums and sealed types (rule 3), public types whose KDoc says "experimental", "internal use" or "subject to change", `Defaults` members that duplicate another entry point, any public declaration that exposes an internal type.
4. The allow-list (2.3) is generated from rows 1 and 2.

Ritsuko signs the ledger (API review owner in #89); Maya generates it and applies the removals and conversions; Asuka verifies with the gate.

**Migration guide `docs/migration/0.9-to-1.0.md`** (task 11, written from the ledger): who is affected (compiled-against-0.9 apps and libraries; the transitive-library case), the 43 removed bridges as a table (signature, replacement; "recompile, no source change" for all), the converted data classes with before/after (destructuring to properties, `copy`, named arguments), the new experimental `orrery-widget`, the RC and gate policy, a CHANGELOG `BREAKING` section and a pointer from the README.

### 2.5 KDoc coverage

- **Gate (decision 9):** enable detekt `comments` rules `UndocumentedPublicClass`, `UndocumentedPublicFunction`, `UndocumentedPublicProperty` for the main source sets of the six modules, in the existing `detekt` CI step (it already reports file and line). Fatal, **zero baseline file**. Experimental declarations are documented like any other.
- **Burn-down.** Task 12 runs the rules once to get the real list, then adds KDoc module by module until the count is zero in the same PR series that turns the rules on. It runs after the break PRs (tasks 9 and 10), so removed bridges are never documented and converted classes are documented once. Order: `orrery-core` → small modules (`lunar`, `calendars`, `holidays`) → `orrery-compose` (two PRs: Defaults/State/Style types, then composables) → `orrery-widget`.
- **Quality bar:** a one-sentence summary; for composables, every parameter that is not self-evident (state, slots, callbacks, nullability meaning); for enums and sealed types, one line per member; `@since` is required on anything added after 1.0.
- **Dokka:** in task 12 also fix broken `[links]` (build with Dokka warnings visible in the log of `./gradlew :dokkaGeneratePublicationHtml`). Making Dokka warnings fatal is optional and only for the release branch. `@InternalOrreryApi` declarations are suppressed in Dokka.
- **README/guide:** each public concept has a section in `docs/guide`; add `widgets-and-snapshots.md` (task 5), `API-STABILITY.md` and the migration guide (task 11).

### 2.6 Accessibility audit (Asuka, task 13)

Not an API review, but part of the #89 title. Checklist on `:sample` and the picker dialogs: TalkBack announces day state (`stateDescription` from #16) and focus order in month, week and range pickers; touch targets of interactive controls are at least 48 dp; fonts at 130 % and 200 % do not clip (existing `FontScaleLayoutTest` plus the new snapshot goldens); RTL layouts mirror (existing from #103); keyboard navigation parity (#62, #103); contrast of `ScheduleDefaults` and snapshot colours against WCAG AA for the default Material colour schemes; the snapshot `contentDescription` is announced when the widget sample is focused. Findings become issues; blocking ones fix before the RC.

---

## 3. Security and Dependency Audit Checklist (Ritsuko)

Scope: what ships to Maven Central and GitHub Pages, and the pipeline that publishes it. Output: a short report (findings with severity, owner, action) in the Security topic and as a comment on #89. Account, repository-setting, firewall and credential changes are Andrii's decision; Ritsuko prepares the exact steps.

### 3.1 Dependencies

- [ ] Inventory the versions in `gradle/libs.versions.toml` (Kotlin 2.4.21, kotlinx-datetime 0.8.0, AGP 9.4.1, Compose BOM 2026.09.00, Compose Multiplatform 1.12.2 and material3 1.9.0, Robolectric 4.17, ICU4J 79.1, detekt 2.0.0-alpha.6, Dokka 2.2.0, maven-publish plugin 0.37.0, klibs-io-notifier 0.0.1, Roborazzi 1.77.0, benchmark 1.5.0) and check each against GitHub Advisory Database/OSV. Record "no known advisory" or the CVE and fix version.
- [ ] Published runtime graph only: run `publishToMavenLocal` (CI already does) and inspect the `.pom` and `.module` files of all six modules (`orrery-widget` must pin `orrery-compose` to the same version). Confirm ICU4J, Roborazzi, Robolectric, test libraries, Glance and the sample/benchmark dependencies **do not** appear.
- [ ] Glance is added only to `:sample` in this phase. Confirm it is absent from every published artefact.
- [ ] Python tooling used for fixtures (python-dateutil 3.0.0, python-holidays 0.107, in `tools/golden/`) is test-time only; check the pins and that no generated data is fetched at build time.
- [ ] Review pre-release and alpha pins (detekt alpha, klibs-io-notifier 0.0.1): confirm they are build-time only, note an exit plan (#35).

### 3.2 Repository and pipeline (findings for Andrii's decision)

- [ ] **Dependabot:** alerts and security updates are disabled and the API returns 403. Decided (10A): Andrii enables Dependabot alerts and security updates; add `.github/dependabot.yml` for `gradle` and `github-actions` (weekly, grouped). Ritsuko drafts the file; Maya commits it.
- [ ] **Workflow permissions:** each workflow declares least-privilege `permissions:` (`contents: read` by default; `pages: write` and `id-token: write` only in `docs.yml`). Verify and list gaps.
- [ ] **Action pinning:** actions are referenced by tag (`@v4`). Decided (10A): pin every action in `publish.yml` and `docs.yml` by commit SHA with a version comment (Dependabot keeps them fresh).
- [ ] **Publish path:** `publish.yml` triggers on `v*` tags with five secrets (`MAVEN_CENTRAL_USERNAME`, `MAVEN_CENTRAL_PASSWORD`, `SIGNING_KEY`, `SIGNING_KEY_ID`, `SIGNING_KEY_PASSWORD`). Decided (10A): a tag ruleset protects `v*` tags, and the publish job runs in the Environment `release` (`environment: release` added to the workflow first, then the setting) with a required reviewer and the five secrets; check also that the new pre-publish ABI/test gate (2.3) is in place; signing key expiry and namespace verification are current (Andrii).
- [ ] **Branch protection on `main`:** required status check "build" and "ios", no direct pushes (matches the team rule; verify it is enforced by the host, not only by convention).
- [ ] **`SECURITY.md`** with a private reporting route, and "Private vulnerability reporting" enabled (Andrii). Ritsuko drafts the text.
- [ ] **Gradle wrapper:** wrapper JAR is validated (`gradle/actions/setup-gradle` validates by default; confirm) and `distributionSha256Sum` is set in `gradle-wrapper.properties`. Gradle dependency verification metadata (`verification-metadata.xml`) is deferred (decision 10).
- [ ] Secret scan of the working tree and history with the tool already available on the host; report only names and locations, never values.

### 3.3 Code-level review (library and the new phase)

- [ ] **Input hardening:** `RecurrenceRule.parse` and `parseExDates` reject oversize input (set a documented maximum length) and cannot loop unboundedly: the 1000-empty-period bound (Phase 1 1.4) and a cap on materialised results of `occurrences` for a wide window (e.g. daily rule over 9999 years). Confirm a maximum, or document the caller's responsibility.
- [ ] **Snapshot renderer (`orrery-widget`):** size caps enforced before any allocation (1.3.5); agenda titles are drawn, never logged; no file, network or reflection use; no logging of dates or events; `Context` not retained; no `WebView`, `Intent` or `PendingIntent` created by the library.
- [ ] **Android AAR manifest:** the merged manifests of `orrery-compose-android` and `orrery-widget-android` declare no permissions, services, receivers, providers or exported components (`profileinstaller` adds its own initializer; confirm it is the only entry and that it is not exported unexpectedly).
- [ ] **Logging and PII:** no `println`, `Log.*` or analytics calls in library sources (`grep`), and nothing writes to storage.
- [ ] **Licences:** `LICENSE` and `NOTICE` cover shipped content; list third-party data in artefacts (holiday rules derive from the documented rules, fixtures from python-holidays are test resources only; confirm they are not packaged).
- [ ] **Docs site:** Pages deploys only Dokka output and the Wasm demo from the tag build; no secrets or `.map` sources (the workflow already strips `*.map`).

### 3.4 Exit criteria

No open High/Critical finding. Medium findings have an owner and a date or an explicit "accepted" from Andrii. The report states "not checked" for anything that could not be verified (for example Dependabot alerts while the feature is disabled).

### 3.5 Exact steps for Andrii (10A, repository settings are his decision)

Order matters: step 1 first, step 2 (Maya's PR) merged before steps 4 and 5, all of them before the `v1.0.0-rc.1` tag. Nothing here needs a secret value to be shown or copied into chat.

1. **Alerts and updates.** Settings → Advanced Security: enable Dependency graph, Dependabot alerts and Dependabot security updates (CLI: `gh api -X PUT repos/koza4e4ok/Orrery/vulnerability-alerts` and `gh api -X PUT repos/koza4e4ok/Orrery/automated-security-fixes`).
2. **Merge the pipeline PR** (task 7): `.github/dependabot.yml`, SHA-pinned actions in `publish.yml` and `docs.yml`, `environment: release` and `permissions:` in `publish.yml`, `SECURITY.md`.
3. **Private vulnerability reporting.** Settings → Advanced Security → Private vulnerability reporting: Enable (CLI: `gh api -X PUT repos/koza4e4ok/Orrery/private-vulnerability-reporting`).
4. **Protect release tags.** Settings → Rules → Rulesets → New tag ruleset: name `release-tags`, enforcement Active, target pattern `v*`, rules: restrict creations, restrict updates, restrict deletions; bypass list: only you.
5. **Publish environment.** Settings → Environments → New environment `release`: required reviewers = you (turn "Prevent self-review" off while you are the only reviewer), deployment tags limited to `v*`; then move the five publishing secrets (`MAVEN_CENTRAL_USERNAME`, `MAVEN_CENTRAL_PASSWORD`, `SIGNING_KEY`, `SIGNING_KEY_ID`, `SIGNING_KEY_PASSWORD`) from repository secrets into the environment and delete the repository-level copies.
6. **Tell Ritsuko** when done; she verifies read-only (`gh api repos/koza4e4ok/Orrery/rulesets`, environment and alert endpoints) and records "checked" or "not checked" per item in the report.

---

## 4. Compatibility Summary

**1.0.0 is a deliberate binary and source break against 0.9.x** (decisions 5 and 8); it is the only such break, and it is bounded by the ledger (2.4).

| Area | Change | Compatibility |
|---|---|---|
| HIDDEN bridges | all 43 removed | binary break for anything compiled against ≤ 0.9.2 (recompile, no source change); migration guide |
| Growth-prone data classes | tier A and B converted to plain classes (2.4) | source break for destructuring; binary break for the generated members; migration guide |
| Remaining data classes, enums, sealed types | tier C stays; documented rules | none |
| `orrery-widget` (new module) | month, week, agenda-strip renderer, composable, Android overload | new artefact; `@ExperimentalOrreryApi`: no promise in 1.0.x, graduates in 1.1 |
| `orrery-core` | `@ExperimentalOrreryApi`, `@InternalOrreryApi` | additive |
| `orrery-compose` | `@InternalOrreryApi` drawing primitives extracted from MiniMonth | additive; MiniMonth goldens unchanged |
| Gate | `tools/api-compat.py`, CI and publish gates; allow-list until `rc.1`, baseline = `rc.1` afterwards | tooling only |
| KDoc, detekt rules | documentation and lint, fatal, zero baseline | no binary change |
| Sample | Glance widget (dependency in `:sample` only) | not published |
| Build | `VERSION_NAME` moves to `1.0.0-rc.1` then `1.0.0` | per release tasks |
| Promise | Orrery's own stable API only; Compose versions stated, not promised | `docs/API-STABILITY.md` |

### Risks of the 1.0 break and the release

| # | Risk | Mitigation |
|---|---|---|
| B1 | Libraries or apps compiled against 0.9.x crash with `NoSuchMethodError` | RC soak, CHANGELOG `BREAKING`, README note, migration guide with the transitive-library case |
| B2 | The allow-list hides an accidental break | generated from the ledger, reviewed by Ritsuko, emptied at `rc.1`, baseline then moves to the RC |
| B3 | The ledger misses a data class or bridge | derived mechanically from the dump diff; gate in report mode shows every removal before the list is final |
| B4 | Break PRs collide with #88 and the feature track | ordered after #88 and tasks 2-4 (section 5); small PRs per module |
| B5 | Experimental module and internal primitives get treated as stable | WARNING opt-in, KDoc/Dokka notes, guide, ERROR opt-in for primitives (R9), empty-dump check |
| B6 | Supply-chain settings block the first tag or are misconfigured | settings done before the RC tag, read-only verification by Ritsuko; Central is immutable, so the pre-publish gate is the guard |
| B7 | The soak finds another break | `rc.2` with Andrii's approval and temporary allow-list entries against `rc.1` |

## 5. Implementation Tasks (ordered)

Gradle runs in CI only; every PR shows green CI and, where it touches public API, the committed dumps. Reviewer for every PR is Asuka unless noted. **Order:** 1 → 2 → 3 → 4 → 5 is the feature track; 6 and 7 run alongside from the start; 8 → 9 → 10 → 11 start after #88 and tasks 2-4 are merged (so the break PRs do not fight changing API); 12 follows 11; then 13, 14, 15.

| # | Task | Owner | Done when |
|---|---|---|---|
| 1 | **Spike (throwaway).** (a) headless `FontFamily.Resolver` + `TextMeasurer` into an `ImageBitmap` on Android host test, desktop, wasmJs, iOS simulator; (b) `abiValidation` filter by annotation hides marked declarations in JVM and klib dumps; (c) a project dependency between two published KMP modules gets an exact version pin in POM and Gradle module metadata | Maya | verdict per item on #89; fallbacks of 1.4 chosen; no code merged |
| 2 | **#89a Annotations, module and shared primitives.** `ExperimentalOrreryApi`, `InternalOrreryApi` and dump filters in the conventions; `orrery-widget` scaffold (settings, build, POM, targets, `ci.yml` lists, Dokka, README module table, `RELEASING.md`); extract the `@InternalOrreryApi` primitives from `MiniMonthCanvas.kt` | Maya | CI green; MiniMonth goldens unchanged; empty widget dump committed; `publishToMavenLocal` shows the widget POM with the exact compose pin and no Glance |
| 3 | **#89b Model and painter.** `CalendarSnapshotSpec/Range/Colors/Style/Output/Defaults` with `Month`, `Week` and `Agenda`, internal `CalendarSnapshotPainter`, agenda row packing, all `@ExperimentalOrreryApi` | Maya | commonTest layout/model tests green (1.5); dumps committed |
| 4 | **#89c Renderer and composable.** `renderCalendarSnapshot` (common and Android), `CalendarSnapshot`, `rememberCalendarSnapshotBitmap`, size limits | Maya | Roborazzi goldens for month, week and agenda strip (1.5) recorded and reviewed by Rei for the visual pass; parity and Robolectric tests; desktop/wasm/iOS smoke tests green; widget dump still empty |
| 5 | **#89d Sample widget and guide.** Glance widget in `:sample` (month and agenda sizes), `docs/guide/widgets-and-snapshots.md` (sizing, dark mode, update cadence, contentDescription, experimental opt-in, one version for all `orrery-*`), README feature row, CHANGELOG `[Unreleased]` | Maya | `:sample:assembleDebug` green; guide compiles against the real API; Glance absent from published POMs (Ritsuko spot check) |
| 6 | **#89e ABI gate.** `tools/api-compat.py` with selftest (removal, signature change, target loss, addition, new module, line- and class-level allow-list, `--emit-removed`, `--require-empty`, "baseline ≥ rc.1 needs empty allow-list"), CI job, `publish.yml` pre-gate, `API_BASELINE_REF` | Maya | selftest passes; a deliberately broken sample PR fails the job (shown once, then reverted); first run in report mode against `v0.9.2` |
| 7 | **Security and supply-chain audit (3).** Report; drafts of `dependabot.yml`, `SECURITY.md`, SHA-pinned `publish.yml`/`docs.yml` with `environment: release` and `permissions:`; exact steps for Andrii (3.5) | Ritsuko audits and drafts; Maya commits the PR; Asuka reviews | report posted to the Security topic and #89; no open High/Critical; PR merged before Andrii's steps 4-5; his steps done before the RC tag |
| 8 | **#89f 1.0 break ledger.** `docs/api-audit-1.0.md` (2.4): all 43 bridges, every public `data class` with tier and reason, enums, sealed types, leaks; generated allow-list | Maya generates; Ritsuko signs | ledger covers all 43 bridges and every public data class; tier B list accepted or amended by Andrii; allow-list produced by the script |
| 9 | **#89g Remove the 43 bridges.** Delete bridges and their `*BinaryCompatTest`s, `apiDump`, CHANGELOG `BREAKING` | Maya | CI green; gate (report mode) shows only allow-listed removals |
| 10 | **#89h Convert data classes.** Per-module PRs (core; holidays and lunar; compose) for tiers A and B: plain class, defaults, hand-written `copy`/`equals`/`hashCode`/`toString`, equality and `copy` tests, call sites in `:sample`, `:demo` and docs updated | Maya | CI green; gate (report mode) shows only allow-listed removals; tier C untouched |
| 11 | **#89i Migration guide and API-STABILITY.** `docs/migration/0.9-to-1.0.md` and `docs/API-STABILITY.md` (2.3, 2.4), README and CHANGELOG pointers | Maya drafts from the ledger; Ritsuko reviews | every ledger row appears in the guide; Compose versions stated |
| 12 | **#89j KDoc burn-down and gate.** detekt undocumented-public rules on (fatal, zero baseline), KDoc module by module (2.5), Dokka link warnings fixed | Maya | detekt reports zero undocumented public declarations in all six modules; Dokka HTML builds without unresolved-link warnings |
| 13 | **Accessibility and visual QA (2.6).** TalkBack, font scale, RTL, widget sample on a device or emulator | Asuka (Rei visual pass on goldens) | findings filed; blockers fixed or explicitly accepted by Andrii |
| 14 | **Freeze and RC.** Gate required, allow-list complete, release PR with `VERSION_NAME=1.0.0-rc.1`, CHANGELOG, docs; after the tag, first PR sets `API_BASELINE_REF=v1.0.0-rc.1` and empties the allow-list | Maya | CI green; Asuka final QA approves; Ritsuko signs off; Andrii's settings (3.5) done; Andrii tags `v1.0.0-rc.1`; Maven Central shows the RC |
| 15 | **1.0.0 release.** About a week of soak, release PR `VERSION_NAME=1.0.0`, tag, Central and Pages publish verified, baseline ref moved to `v1.0.0`, close #89 and #91 | Maya (PR), Andrii (tag), Asuka (verify) | Maven Central and Pages show `1.0.0`; gate baseline is `v1.0.0`; issues closed; 1.1 graduation of `orrery-widget` opened as an issue |

## 6. Decisions (all decided by Andrii, 2026-11-13)

| # | Question | Decision | Where |
|---|---|---|---|
| 1 | Where does the renderer live? | **B:** new published module `orrery-widget`; shares MiniMonth primitives through `@InternalOrreryApi` in `orrery-compose` | 1.1, 1.4 |
| 2 | Rendering strategy | **A:** pure `DrawScope` painter | 1.4 |
| 3 | Is the renderer API stable at 1.0? | **B:** whole renderer API `@ExperimentalOrreryApi` (WARNING), excluded from the ABI dump and compat gate for 1.0.x, graduates in 1.1 | 2.2, 2.3 |
| 4 | Glance in `:sample` | **A:** real widget in `:sample`, unpublished | task 5 |
| 5 | The 43 HIDDEN bridges | **B:** remove all at 1.0.0, document the break in `docs/migration` | 2.4, task 9, 11 |
| 6 | Release shape | **A:** `1.0.0-rc.1` to Maven Central, about a week of soak, then `1.0.0` | tasks 14, 15 |
| 7 | ABI gate mechanism | **A:** `tools/api-compat.py` against a baseline tag; 1.0 breaks via the allow-list, then baseline = RC tag | 2.3 |
| 8 | Growth-prone data classes | **B:** convert to plain classes with defaults and hand-written `copy` at 1.0 (one-time break); list in the ledger and the guide | 2.4, task 10 |
| 9 | KDoc gate | **A:** detekt undocumented-public rules, zero baseline, fatal | 2.5, task 12 |
| 10 | Supply-chain hardening | **A:** full set (Dependabot alerts and updates and `dependabot.yml`, SHA-pinned actions in `publish.yml`/`docs.yml`, protected `v*` tags, publish environment reviewer, `SECURITY.md`, private vulnerability reporting); Gradle verification metadata deferred. Repo settings are Andrii's: steps in 3.5 | 3.2, 3.5, task 7 |
| 11 | Snapshot scope | **B:** month, week and agenda strip (experimental too) | 1.2, 1.3 |
| 12 | Platform statement | **A:** promise covers Orrery's own API only; Compose versions stated | 2.3 |
| 13 | Security audit owner | **A:** Ritsuko runs it | 3, task 7 |

### Confirmations requested (defaults apply unless Andrii objects)

1. **Tier B conversion list** (2.4): approve or strike types before the ledger is final (task 8). Default: as listed.
2. **Primitive sharing** (1.4): public `@InternalOrreryApi` in `orrery-compose` (ERROR opt-in, dump- and Dokka-excluded) instead of duplicating about 150 lines. Default: shared.
3. **Agenda events input** (1.2): `List<CalendarEvent<*>>` from `orrery-core` with recurrences expanded by the caller. Default: yes.
4. **Soak rule** (2.3): a break found during the soak means `rc.2` and another soak. Default: yes.
