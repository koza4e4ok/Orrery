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

---

## 2. 1.0.0 Public API Freeze Plan

### 2.1 Where we are (facts from main at b21ee08)

| Area | State |
|---|---|
| Explicit API mode | on for all five modules (`explicitApi()` in the build-logic conventions) |
| ABI validation | KGP built-in `abiValidation` (successor of binary-compatibility-validator) in every convention: JVM dumps (`orrery-core`, `-lunar`, `-calendars`, `-holidays`: `*.api`; `orrery-compose`: `api/android` and `api/desktop`) plus merged klib dumps (`*.klib.api`: iOS and wasmJs) |
| CI gate | `checkKotlinAbi` in the PR job; on failure CI runs `updateKotlinAbi` and uploads the dumps |
| Tags | `v0.9.2` (be5b1d5) is the latest release |
| Deprecations | 43 `@Deprecated`, **all** `level = HIDDEN` (binary-compat bridges for ctor/copy/overload signatures). No WARNING or ERROR deprecations exist; nothing to graduate |
| Opt-in API | none exposed. `@OptIn(ExperimentalFoundationApi)` appears only inside implementations (`Agenda`, `Calendars`) |
| KDoc | no gate. A rough line scan (public declaration with no KDoc on the previous line) flags about 350 of 714 declarations in the five modules, but many are primary-constructor properties covered by a class-level `@property` tag, so the real number must come from a real tool (task 6). Largest gaps by scan: `CalendarDefaults`, `ScheduleDefaults`, `DatePickerDefaults`, `ScheduleLayout`, `Selection`, `CalendarEvent`, `DayInfoProvider`, `RecurrenceRule`, `YearCalendar` |
| Docs | Dokka 2.2.0 aggregated; deployed on `v*` tags (`docs.yml`) |
| Publish | `publish.yml` on `v*` tags runs `publishToMavenCentral` directly: **no ABI check, no tests** before an irreversible Central publish |
| Repo security | secret scanning and push protection enabled; Dependabot alerts and security updates disabled; no `dependabot.yml`; no `SECURITY.md` |

**The gap.** `checkKotlinAbi` compares the code with the dump committed in the same PR. A PR that deletes a public method and also commits the shrunken dump is green. Until now that was acceptable (pre-1.0); at 1.0 it is exactly the failure the gate must catch.

### 2.2 1.0 API rules (apply to new and existing public API)

1. **Growth.** New configuration, state and result types are plain classes with defaults and a hand-written `copy` (no new public `data class` except tiny value types that can never grow, e.g. `ByDay`). Existing `data class`es stay (open decision 8); their growth uses the HIDDEN-bridge pattern with a `BinaryCompat` test.
2. **Parameters.** New parameters are appended with defaults; the old signature stays as a HIDDEN bridge. Lesson from #63: a HIDDEN bridge of a non-composable function with defaults loses its `$default` method, so add an overload instead.
3. **Enums and sealed types.** Public enums and sealed interfaces that may gain members (`Frequency`, `SelectionMode`, `RangeEndpointStyle`, `CalendarSnapshotRange`, …) document "do not rely on exhaustive `when` across versions" in KDoc. The ledger (2.4) lists each one.
4. **Visibility.** Everything not meant for callers is `internal`. Nothing public mentions an internal or experimental type in its signature. `@OptIn` markers stay out of public signatures (no opt-in propagation).
5. **Compose signatures.** Composables keep `modifier` as the first optional parameter, state and slots as documented parameters, no default argument that reads mutable global state.
6. **Deprecation policy from 1.0.** WARNING for at least one minor release (with `ReplaceWith`), then HIDDEN, then removal only in a major. The 43 existing bridges are HIDDEN today and stay HIDDEN unless section 2.4 removes them.

### 2.3 ABI baseline and CI gate

**Goal:** a PR may only **add** to the public ABI relative to the last release; removals and signature changes must be impossible to merge silently.

Design (open decision 7 has the alternatives):

- **Baseline = dumps at a git ref** (variable `API_BASELINE_REF` in `ci.yml`; today `v0.9.2`, at the freeze `v1.0.0-rc.1`, after release `v1.0.0`). The job checks the baseline dumps out of git history (`git show <ref>:<module>/api/...`) next to the PR's dumps.
- **Script `tools/api-compat.py`** (stdlib Python, available on the CI runner; unit-tested with fixture dumps in `tools/api-compat-selftest/`): for every `*.api` and `*.klib.api` pair, normalise whitespace and drop comment lines, then fail if **any baseline line is missing from the PR dump** (a removed class, member or changed signature). For `*.klib.api` files the `// Targets: [...]` header of each baseline file must be a subset of the PR's (losing a target is a break). Additions are printed in the job summary so reviewers see the API delta.
- **Allow-list `tools/api-compat-allow.txt`.** One line per intentional removal: `<module> | <dump line> | <reason> | <PR>`. Before the freeze it may hold the removals approved in the ledger (2.4). After `1.0.0-rc.1` the file must be empty and CI fails if it is not.
- **Jobs:** (1) PR job (`ci.yml`): `checkKotlinAbi` (existing: dump matches code) **and** `python3 tools/api-compat.py` (new: dump compatible with baseline). (2) `publish.yml`: before `publishToMavenCentral` run `./gradlew checkKotlinAbi` and `api-compat.py` with `API_BASELINE_REF` = the previous tag, and the module test tasks of `ci.yml` that are cheap on Linux. A tag that does not pass never publishes.
- **Binary-compat tests** stay (`DayInfoBinaryCompatTest` pattern); every new HIDDEN bridge ships with one.
- **Report mode first.** Until the feature freeze (task 5) the job runs with `continue-on-error` and only reports the delta against `v0.9.2`; it becomes required at the freeze. That avoids blocking Phase 2 work (#125 and later PRs) that legitimately changes unreleased API.

**Not covered by a text dump** (documented in `docs/API-STABILITY.md`): Compose compiler-generated artefacts (`$default`, group keys) and behaviour. Behaviour compatibility is protected by the Roborazzi goldens and unit tests, not by ABI tooling.

### 2.4 Deprecations: the bridge ledger

All 43 bridges are HIDDEN: invisible to source, present in bytecode so that apps compiled against older Orrery keep working. They cost nothing for new users and carry a small maintenance and dump-size cost. 1.0.0 is the only moment a clean-up is free of semver arguments, so decide per bridge, not in bulk.

Rule: a bridge protects a signature that **shipped in a tag**. A bridge whose old signature only ever existed on unreleased main is dead weight.

Task 5 produces `docs/api-audit-1.0.md`, generated mechanically:

1. For each module diff the `v0.9.2` dump against main (`git diff v0.9.2 main -- '*/api/*'`).
2. For every HIDDEN member in main's dump, record the release it was introduced in (first tag whose dump contains the old signature the bridge preserves) and the signature it forwards to.
3. Classify:
   - **Keep:** preserves a signature present in any released tag (0.5.x to 0.9.2). Default for all bridges.
   - **Remove (dead):** preserves a signature that exists in no tag (typical for bridges added by Phase 1/2 for intermediate main states). Removal is a pure ABI-dump shrink relative to released versions, so it passes the gate without an allow-list entry.
   - **Remove (deliberate break):** only with Andrii's approval (open decision 5), listed in the allow-list and in `docs/migration`.
4. The same ledger lists the other 1.0 audit items per module: public `data class`es with the `componentN`/`copy` surface they freeze, public enums and sealed types (rule 3), public types whose KDoc says "experimental", "internal use" or "subject to change", public API in `Defaults` objects that duplicates another entry point, and any public declaration that exposes an internal type.

Ritsuko reviews the ledger (API review owner in #89); Maya applies the removals; Asuka verifies with the gate.

### 2.5 KDoc coverage

- **Gate:** enable detekt `comments` rules `UndocumentedPublicClass`, `UndocumentedPublicFunction`, `UndocumentedPublicProperty` for the main source sets of the five modules, in the existing `detekt` CI step (it already reports file and line). Open decision 9 lists alternatives.
- **Burn-down, no baseline file.** Task 6 runs the rules once to get the real list, then adds KDoc module by module until the count is zero in the same PR series that turns the rules on. Order: `orrery-core` → small modules (`lunar`, `calendars`, `holidays`) → `orrery-compose` (split in two PRs: Defaults/State/Style types, then composables).
- **Quality bar:** a one-sentence summary; for composables, every parameter that is not self-evident (state, slots, callbacks, nullability meaning); for enums and sealed types, one line per member; "@since 1.0.0" is not required, but anything added after 1.0 must carry `@since`.
- **Dokka:** in task 6 also fix broken `[links]` (build with Dokka warnings visible in the log of `./gradlew :dokkaGeneratePublicationHtml`). Making Dokka warnings fatal is optional and only for the release branch (open decision 9).
- **README/guide:** each public concept has a section in `docs/guide`; add `widgets-and-snapshots.md` (task 4) and `API-STABILITY.md` (task 5).

### 2.6 Accessibility audit (Asuka, task 9)

Not an API review, but part of the #89 title. Checklist on `:sample` and the picker dialogs: TalkBack announces day state (`stateDescription` from #16) and focus order in month, week and range pickers; touch targets of interactive controls are at least 48 dp; fonts at 130 % and 200 % do not clip (existing `FontScaleLayoutTest` plus the new snapshot goldens); RTL layouts mirror (existing from #103); keyboard navigation parity (#62, #103); contrast of `ScheduleDefaults` and snapshot colours against WCAG AA for the default Material colour schemes; the snapshot `contentDescription` is announced when the widget sample is focused. Findings become issues; blocking ones fix before the RC.

---

## 3. Security and Dependency Audit Checklist (Kaji)

Scope: what ships to Maven Central and GitHub Pages, and the pipeline that publishes it. Output: a short report (findings with severity, owner, action) in the Security topic and as a comment on #89. Account, repository-setting, firewall and credential changes are Andrii's decision; Kaji prepares the exact steps.

### 3.1 Dependencies

- [ ] Inventory the versions in `gradle/libs.versions.toml` (Kotlin 2.4.21, kotlinx-datetime 0.8.0, AGP 9.4.1, Compose BOM 2026.09.00, Compose Multiplatform 1.12.2 and material3 1.9.0, Robolectric 4.17, ICU4J 79.1, detekt 2.0.0-alpha.6, Dokka 2.2.0, maven-publish plugin 0.37.0, klibs-io-notifier 0.0.1, Roborazzi 1.77.0, benchmark 1.5.0) and check each against GitHub Advisory Database/OSV. Record "no known advisory" or the CVE and fix version.
- [ ] Published runtime graph only: run `publishToMavenLocal` (CI already does) and inspect the `.pom` and `.module` files of all five modules. Confirm ICU4J, Roborazzi, Robolectric, test libraries, Glance and the sample/benchmark dependencies **do not** appear.
- [ ] Glance is added only to `:sample` in this phase. Confirm it is absent from every published artefact.
- [ ] Python tooling used for fixtures (python-dateutil 3.0.0, python-holidays 0.107, in `tools/golden/`) is test-time only; check the pins and that no generated data is fetched at build time.
- [ ] Review pre-release and alpha pins (detekt alpha, klibs-io-notifier 0.0.1): confirm they are build-time only, note an exit plan (#35).

### 3.2 Repository and pipeline (findings for Andrii's decision)

- [ ] **Dependabot:** alerts and security updates are disabled and the API returns 403. Proposal: enable Dependabot alerts and security updates (Andrii) and add `.github/dependabot.yml` for `gradle` and `github-actions` (weekly, grouped). Kaji drafts the file; Andrii enables the setting.
- [ ] **Workflow permissions:** each workflow declares least-privilege `permissions:` (`contents: read` by default; `pages: write` and `id-token: write` only in `docs.yml`). Verify and list gaps.
- [ ] **Action pinning:** actions are referenced by tag (`@v4`). Proposal: pin `publish.yml` and `docs.yml` actions by commit SHA with a version comment (Dependabot keeps them fresh).
- [ ] **Publish path:** `publish.yml` triggers on `v*` tags with five secrets (`MAVEN_CENTRAL_USERNAME`, `MAVEN_CENTRAL_PASSWORD`, `SIGNING_KEY`, `SIGNING_KEY_ID`, `SIGNING_KEY_PASSWORD`). Check: a tag ruleset restricts who can create `v*` tags; secrets live in an Environment (`release`) with a required reviewer, or an explicit decision not to; the new pre-publish ABI/test gate (2.3) is in place; signing key expiry and namespace verification are current (Andrii).
- [ ] **Branch protection on `main`:** required status check "build" and "ios", no direct pushes (matches the team rule; verify it is enforced by the host, not only by convention).
- [ ] **`SECURITY.md`** with a private reporting route, and "Private vulnerability reporting" enabled (Andrii). Kaji drafts the text.
- [ ] **Gradle wrapper:** wrapper JAR is validated (`gradle/actions/setup-gradle` validates by default; confirm) and `distributionSha256Sum` is set in `gradle-wrapper.properties`.
- [ ] Secret scan of the working tree and history with the tool already available on the host; report only names and locations, never values.

### 3.3 Code-level review (library and the new phase)

- [ ] **Input hardening:** `RecurrenceRule.parse` and `parseExDates` reject oversize input (set a documented maximum length) and cannot loop unboundedly: the 1000-empty-period bound (Phase 1 1.4) and a cap on materialised results of `occurrences` for a wide window (e.g. daily rule over 9999 years). Confirm a maximum, or document the caller's responsibility.
- [ ] **Snapshot renderer:** size caps enforced before any allocation (1.3.5); no file, network or reflection use; no logging of dates or events; `Context` not retained; no `WebView`, `Intent` or `PendingIntent` created by the library.
- [ ] **Android AAR manifest:** the merged manifest of `orrery-compose-android` declares no permissions, services, receivers, providers or exported components (`profileinstaller` adds its own initializer; confirm it is the only entry and that it is not exported unexpectedly).
- [ ] **Logging and PII:** no `println`, `Log.*` or analytics calls in library sources (`grep`), and nothing writes to storage.
- [ ] **Licences:** `LICENSE` and `NOTICE` cover shipped content; list third-party data in artefacts (holiday rules derive from the documented rules, fixtures from python-holidays are test resources only; confirm they are not packaged).
- [ ] **Docs site:** Pages deploys only Dokka output and the Wasm demo from the tag build; no secrets or `.map` sources (the workflow already strips `*.map`).

### 3.4 Exit criteria

No open High/Critical finding. Medium findings have an owner and a date or an explicit "accepted" from Andrii. The report states "not checked" for anything that could not be verified (for example Dependabot alerts while the feature is disabled).

---

## 4. Compatibility Summary

| Area | Change | Compatibility |
|---|---|---|
| Snapshot API | new: `CalendarSnapshotSpec`, `-Range`, `-Colors`, `-Style`, `-Output`, `-Defaults`, `CalendarSnapshot`, `renderCalendarSnapshot` (common and Android), `rememberCalendarSnapshotBitmap` | additive; plain classes |
| MiniMonth | internal refactor to share drawing primitives | no public change; goldens unchanged |
| Bridges | dead bridges removed per ledger (2.4) | no change for apps built against any release; ABI dump shrinks |
| Gate | `tools/api-compat.py`, CI and publish gates | tooling only |
| KDoc, detekt rules | documentation and lint | no binary change |
| Sample | Glance widget (new dependency in `:sample` only) | not published |
| Build | `VERSION_NAME` moves to `1.0.0-rc.1` then `1.0.0` | per release tasks |

## 5. Implementation Tasks (ordered)

Gradle runs in CI only; every PR shows green CI and, where it touches public API, the committed dumps. Reviewer for every PR is Asuka unless noted. Tasks 1 to 4 are the feature track; 7 can start immediately in report mode; 5 and 6 wait for the feature freeze (task 4 merged, and #88 merged) so they do not fight with changing API.

| # | Task | Owner | Done when |
|---|---|---|---|
| 1 | **Spike: headless text and bitmap.** Throwaway branch (spike skill): create `FontFamily.Resolver` and draw a `TextMeasurer` string into an `ImageBitmap` on Android host test, desktop, wasmJs and iOS simulator | Maya | verdict posted on #89 (works / fallback from 1.4 needed per target); no code merged |
| 2 | **#89a Snapshot model and painter.** `CalendarSnapshotSpec/Range/Colors/Style/Output/Defaults`, internal `CalendarSnapshotPainter`, shared primitives extracted from `MiniMonthCanvas` | Maya | commonTest layout/model tests green; MiniMonth goldens unchanged; `apiDump` committed; CI green |
| 3 | **#89b Renderer and composable.** `renderCalendarSnapshot` (common and Android), `CalendarSnapshot`, `rememberCalendarSnapshotBitmap`, size limits | Maya | Roborazzi goldens (1.5) recorded and reviewed by Rei for the visual pass; parity and Robolectric tests; desktop/wasm/iOS smoke tests green; dumps committed |
| 4 | **#89c Sample widget and docs.** Glance widget in `:sample`, `docs/guide/widgets-and-snapshots.md` (sizing, dark mode, update cadence, contentDescription), README feature row, CHANGELOG `[Unreleased]` | Maya | `:sample:assembleDebug` green; guide compiles against the real API; Glance absent from published POMs (Kaji spot check) |
| 5 | **#89d API audit and bridge ledger.** `docs/api-audit-1.0.md` per 2.4, `docs/API-STABILITY.md`, removal of dead bridges, rule 3 KDoc notes on enums/sealed types | Maya generates and applies; Ritsuko signs the ledger decisions | ledger covers all 43 bridges, all public `data class`es, enums and sealed types; every removal is "dead" or has Andrii's approval; dumps and migration notes updated; gate (task 7) green with the allow-list |
| 6 | **#89e KDoc burn-down and gate.** detekt undocumented-public rules on, KDoc added module by module, Dokka link warnings fixed | Maya | detekt reports zero undocumented public declarations in all five modules; Dokka HTML builds without unresolved-link warnings |
| 7 | **#89f ABI gate.** `tools/api-compat.py` with selftest, CI job, `publish.yml` pre-gate, `API_BASELINE_REF` | Maya | selftest covers removal, signature change, target loss, addition and allow-list; a deliberately broken sample PR fails the job (shown once, then reverted); first run in report mode against `v0.9.2` |
| 8 | **Security and dependency audit (3).** Report plus drafts: `dependabot.yml`, `SECURITY.md`, workflow permissions and pinning proposals, exact repo-setting steps for Andrii | Kaji | report posted to Security topic and #89; no open High/Critical; Maya's fixes (if any) merged; setting changes handed to Andrii as numbered steps |
| 9 | **Accessibility and visual QA (2.6).** TalkBack, font scale, RTL, widget sample on a device or emulator | Asuka (Rei visual pass on goldens) | findings filed; blockers fixed or explicitly accepted by Andrii |
| 10 | **Freeze and RC.** Flip the gate to required, empty the allow-list, set `API_BASELINE_REF` to the RC tag, release PR with `VERSION_NAME=1.0.0-rc.1`, CHANGELOG, docs | Maya | CI green; Asuka final QA approves; Kaji signs off; Andrii tags `v1.0.0-rc.1` (open decision 6) |
| 11 | **1.0.0 release.** Soak period, release PR `VERSION_NAME=1.0.0`, tag, Central and Pages publish verified, baseline ref moved to `v1.0.0`, close #89 and #91 | Maya (PR), Andrii (tag), Asuka (verify) | Maven Central and Pages show `1.0.0`; gate baseline is `v1.0.0`; issues closed |

Suggested PR order: 1, 2, 3, 4 (feature track) with 7 and 8 running alongside; 5 and 6 after the feature freeze; then 9, 10, 11.

## 6. Open Decisions for Andrii (with recommendations)

1. **Where does the renderer live?** (a) *Recommended:* in `orrery-compose`: no new artefact, shares the MiniMonth primitives and the Compose dependency that is needed anyway. (b) New `orrery-widget` module: lighter dependency story in theory, but it still needs Compose UI, adds a publication to maintain before 1.0 and splits shared drawing code.
2. **Rendering strategy.** (a) *Recommended:* pure `DrawScope` painter, no composition. Deterministic, no lifecycle, works on all targets; looks like the compact MiniMonth family. (b) Capture real composables offscreen (`ComposeView`/`GraphicsLayer`): pixel-identical to the live calendar, but fragile on Android and not available headless on other targets.
3. **Is the new renderer API stable at 1.0?** (a) *Recommended:* stable; growth-safe by design (plain classes, `Output` grouping), no new concepts for users. (b) Mark it `@ExperimentalOrreryApi` (`@RequiresOptIn(WARNING)`, excluded from the ABI dump) for the 1.0.x line and graduate in 1.1: safer if you expect the API to change after real widget use, at the cost of an annotation and a dump filter.
4. **Glance in `:sample`.** (a) *Recommended:* yes, a real widget in `:sample` (compile-checked in CI, never published). (b) Guide only, no sample dependency (less to audit; the guide can rot).
5. **The 43 HIDDEN bridges.** (a) *Recommended:* keep every bridge that preserves a signature present in a release tag; remove only dead bridges (no tag ever contained the old signature). (b) Remove all at 1.0.0 and document the break in `docs/migration`: cleanest dump, but breaks any app compiled against 0.9.x without recompiling. (c) Keep everything including dead ones: zero analysis, permanent dead code.
6. **Release shape.** (a) *Recommended:* publish `1.0.0-rc.1` to Maven Central, soak for about a week (sample app and Andrii's own apps), then `1.0.0` from the same commit if nothing changes. (b) Tag `1.0.0` directly after Phase 3 merges: faster, but Central is immutable and the gate has never run against a real consumer.
7. **ABI gate mechanism.** (a) *Recommended:* `tools/api-compat.py` comparing dumps against the baseline tag (covers JVM and klib dumps with one tool, no new dependency). (b) `japicmp` on the published 0.9.2/RC jars: mature, JVM only, so klib targets would stay on `checkKotlinAbi` only. (c) Keep `checkKotlinAbi` alone and rely on review: cheapest, does not close the gap in 2.1.
8. **Existing `data class`es that may need to grow.** (a) *Recommended:* keep them; growth continues through HIDDEN bridges and `BinaryCompat` tests; new types follow rule 1. (b) Convert the growth-prone ones to plain classes now (a one-time source/binary break at 1.0, avoids future bridges).
9. **KDoc gate.** (a) *Recommended:* detekt undocumented-public rules on, zero baseline, fatal in the existing step. (b) Dokka `reportUndocumented` with warnings as errors: closer to the docs users see, but slower and less actionable. (c) Ratchet with a baseline file: easier to land, never reaches 100 %.
10. **Supply-chain hardening.** *Recommended:* enable Dependabot alerts and security updates and add `dependabot.yml` (gradle and github-actions); pin actions in `publish.yml`/`docs.yml` by SHA; protect `v*` tags and require a reviewer for the publish environment; add `SECURITY.md` and enable private vulnerability reporting (steps prepared by Kaji for you). Defer Gradle dependency verification (`verification-metadata.xml`): large churn with KMP, wasm and yarn lock files, little extra protection for a library. Alternative: do only the Dependabot part.
11. **Snapshot scope.** (a) *Recommended:* month and week only for 1.0; schedule/agenda/year snapshots and WidgetKit helpers after 1.0 on demand. (b) Add an agenda-strip snapshot now: more useful widgets, more API frozen at 1.0 without field feedback.
12. **Minimum platform statement in `API-STABILITY.md`.** *Recommended:* promise binary compatibility of Orrery's own API only; state the Compose BOM/Compose Multiplatform versions it is built and tested with and that Compose compiler internals are outside the promise. Alternative: promise a range of Compose versions (needs a CI matrix we do not have).

