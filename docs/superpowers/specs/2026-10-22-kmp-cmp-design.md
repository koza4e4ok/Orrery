# Kotlin & Compose Multiplatform (0.6.0) — Design

**Date:** 2026-10-22
**Status:** Approved (signed off 2026-10-23)
**Issue:** #20 (Tracking roadmap: #26)
**Owners:** Ritsuko (design), Maya (implementation), Asuka (review)

## Context

Orrery `0.5.x` is published as two pure-JVM modules (`orrery-core`, `orrery-lunar`) and one Android library module (`orrery-compose`). Multiplatform Compose calendars with rich selection, lunar/almanac providers, heatmaps, and schedule views are rare across the Kotlin ecosystem; supporting Kotlin Multiplatform (KMP) and Compose Multiplatform (CMP) is Orrery's primary strategic differentiator.

A live codebase audit of `0.5.1-SNAPSHOT` confirms how close the modules already are:

- **`orrery-core`**: 100% pure Kotlin built on `kotlinx-datetime` (`0.8.0`). Zero `java.*` or `android.*` imports in `src/main` or `src/test`.
- **`orrery-lunar`**: 100% pure Kotlin + `kotlinx-datetime` in `src/main` (`HashMap` resolves to `kotlin.collections.HashMap`). Only `src/test` (`GoldenFixturesTest.kt` and `LunarDateTest.kt`) touches `java.util.zip.GZIPInputStream` and `Class.getResourceAsStream` to stream the 73,049-row gzipped golden CSV.
- **`orrery-compose`**: Built on Compose Foundation + Material 3 + `kotlinx-datetime`. Every `java.*` and `android.*` import in `src/main` is confined to locale/date formatting in six files (`Locale.kt`, `DateInput.kt`, `DefaultDay.kt`, `DatePickerDialog.kt`, `DateRangePickerDialog.kt`, `HeatmapCalendar.kt`, `HeatmapMonth.kt`). Haptics (`LocalHapticFeedback`) is already part of `androidx.compose.ui.platform` in Compose Multiplatform.

**Deliberate non-goals (what we are NOT building):**

- No native SwiftUI or UIKit wrappers; iOS consumers render Orrery via Compose Multiplatform (`ComposeUIViewController`).
- No legacy `js(IR)` Web target; Web support uses `wasmJs` only.
- No desktop entry point in `:sample` during this migration (`:sample` stays Android-only for now; desktop sample deferred to a later phase).
- No Linux/Mingw native non-Compose targets for `orrery-core`/`orrery-lunar` in the initial wave (can be added trivially later if requested; initial targets match Compose Multiplatform's supported matrix).
- No third-party KMP file/compression library in `orrery-lunar` just to unzip test fixtures on non-JVM targets.
- No multiplatform pixel-snapshot framework replacing Roborazzi on non-Android targets.

---

## 1. Target Matrix

All three published modules (`orrery-core`, `orrery-lunar`, `orrery-compose`) target the standard Compose Multiplatform matrix:

| Target | Kotlin Target | Notes |
|---|---|---|
| **Android** | `androidTarget()` (or `androidLibrary`) | `minSdk = 23`, `compileSdk = 36`, JVM target 17 |
| **JVM / Desktop** | `jvm("desktop")` (or `jvm()` in core/lunar) | JDK 17 toolchain; backs Compose Desktop & server/CLI consumers |
| **iOS** | `iosArm64()`, `iosSimulatorArm64()`, `iosX64()` | Static framework export for Xcode / SwiftPM |
| **Web (Wasm)** | `wasmJs { browser() }` | Kotlin/Wasm Skiko-backed Compose for Web |

*Target naming note:* `orrery-core` and `orrery-lunar` do not depend on the Android SDK and therefore need only `jvm()` (usable by both Android and Desktop consumers) alongside `ios*` and `wasmJs`. `orrery-compose` declares both `androidTarget()` (for Android resources/Roborazzi/AAR metadata) and `jvm("desktop")`.

---

## 2. Module Layout & Source-Set Hierarchy

The repository keeps its existing four-module structure (`:orrery-core`, `:orrery-lunar`, `:orrery-compose`, `:sample`). No new published artifact modules are introduced.

```text
Orrery
├── build-logic/
│   └── src/main/kotlin/
│       ├── orrery.kmp-library.gradle.kts      (replaces orrery.kotlin-library)
│       ├── orrery.cmp-library.gradle.kts      (replaces orrery.android-library)
│       ├── orrery.lint.gradle.kts
│       └── orrery.publish.gradle.kts
├── orrery-core/
│   └── src/{commonMain,commonTest}/kotlin/me/kozakov/orrery/core/
├── orrery-lunar/
│   └── src/
│       ├── commonMain/kotlin/me/kozakov/orrery/lunar/
│       ├── commonTest/kotlin/me/kozakov/orrery/lunar/   (unit + sampled golden tests)
│       └── jvmTest/                                      (full 73k-row GZIP CSV tests + resources)
└── orrery-compose/
    └── src/
        ├── commonMain/kotlin/me/kozakov/orrery/compose/
        ├── jvmSharedMain/kotlin/me/kozakov/orrery/compose/  (shared java.time formatting)
        ├── androidMain/kotlin/me/kozakov/orrery/compose/    (DateFormat.getBestDateTimePattern)
        ├── desktopMain/kotlin/me/kozakov/orrery/compose/    (DateTimeFormatterBuilder pattern)
        ├── iosMain/kotlin/me/kozakov/orrery/compose/        (NSDateFormatter / NSCalendar)
        ├── wasmJsMain/kotlin/me/kozakov/orrery/compose/     (Intl.DateTimeFormat / Intl.Locale)
        ├── commonTest/kotlin/me/kozakov/orrery/compose/     (runComposeUiTest interaction suite)
        └── androidUnitTest/kotlin/me/kozakov/orrery/compose/ (Roborazzi snapshot suite)
```

### Platform Abstraction in `orrery-compose`

Instead of leaking `java.util.Locale` into public signatures on non-JVM targets, `Locale.kt` and `DateInput.kt` delegate to an internal multiplatform facade `PlatformDateFormat.kt`:

```kotlin
// commonMain: orrery-compose/.../PlatformDateFormat.kt
internal expect object PlatformDateFormat {
    /** Locale-preferred first day of week (e.g. Sunday in US, Monday in DE). */
    fun firstDayOfWeek(): DayOfWeek

    /** Localized weekday display name: short ("Mon") or narrow ("M"). */
    fun weekdayName(dayOfWeek: DayOfWeek, narrow: Boolean): String

    /** Localized standalone month name: full ("July") or short ("Jul"). */
    fun monthName(month: Month, short: Boolean): String

    /** Localized month + year header, e.g. "July 2026". */
    fun yearMonthTitle(yearMonth: YearMonth): String

    /** Localized date string for headlines (MEDIUM) or a11y descriptions (LONG / FULL). */
    fun formatDate(date: LocalDate, style: DateFormattingStyle): String

    /** Locale-ordered 4-digit-year input pattern (e.g. "MM/dd/yyyy" or "dd.MM.yyyy"). */
    fun inputDatePattern(): String
}

internal enum class DateFormattingStyle { Medium, Long, Full }
```

- **JVM/Android source-compatibility:** Existing public extensions in `Locale.kt` that take `java.util.Locale` (`firstDayOfWeekFromLocale(locale: Locale)`, `DayOfWeek.displayName(narrow, locale)`, `YearMonth.displayName(locale)`, `Month.displayName(short, locale)`) remain available in `jvmSharedMain` (so Android and Desktop callers have zero source-breaking changes), while `commonMain` exposes the parameterless overloads using the system default locale.
- **Strict date parsing in `DateInput.kt`:** Because `inputDatePattern()` always returns a normalized pattern of `y`/`M`/`d` tokens with literal separators, strict date parsing (`parseStrictDate(text: String, pattern: String): LocalDate?`) is implemented in **pure Kotlin in `commonMain`** (~45 lines: tokenizes `pattern`, extracts year/month/day integers, and constructs `LocalDate(year, month, day)` which validates leap years and month lengths natively in `kotlinx-datetime`). This eliminates `java.time.format.ResolverStyle.STRICT` differences across platforms and guarantees identical validation on Android, Desktop, iOS, and Wasm.

---

## 3. Test Strategy (Robolectric & Roborazzi Replacement / Scoping)

| Suite | Current Runner | Multiplatform Strategy |
|---|---|---|
| `orrery-core` unit tests | JUnit 5 (`useJUnitPlatform()`) | Move unchanged to `commonTest` (`kotlin.test`). Runs on JVM, iOS Simulator, and Wasm. |
| `orrery-lunar` unit tests | JUnit 5 | Move algorithmic tests (`LunarDateTest`, `SolarTermsTest`, `FestivalsTest`, `TrunkBranchTest`, `LunarDayInfoProviderTest`, `LunarStringsTest`) to `commonTest`. Keep `GoldenFixturesTest` (and the full 73,049-row `lunar-daily.csv.gz` loop) in `jvmTest`, while adding a compact 200-row boundary sample in `commonTest` so iOS/Wasm verify ephemeris math without needing a KMP gzip/filesystem library. |
| `orrery-compose` behavioral tests (`31 *Test.kt` files) | Robolectric + `createComposeRule()` | Migrate to Compose Multiplatform's `runComposeUiTest` (`org.jetbrains.compose.ui:ui-test`). Runs in `desktopTest` (fast, headless Skiko without Robolectric overhead) and `androidUnitTest`. `LocaleTest` stays in `jvmSharedTest` (tests explicit `java.util.Locale` overrides). |
| `orrery-compose` visual snapshots (`11 *SnapshotTest.kt` files) | Robolectric + Roborazzi | **Keep in `androidUnitTest`** under Roborazzi (`:orrery-compose:verifyRoborazziDebug`). Cross-platform Skia vs Android Canvas text rasterization differs by 1–2px; keeping Roborazzi on Android preserves all 28 existing reference PNGs with zero churn. |

---

## 4. Publishing & Binary Compatibility

### Maven Central Publishing (`com.vanniktech.maven.publish`)

`com.vanniktech.maven.publish` (`0.37.0`) supports Kotlin Multiplatform out of the box with zero changes to `orrery.publish.gradle.kts`!

- **Published artifacts:**
  - `orrery-core`: root KMP module (`me.kozakov.orrery:orrery-core`), `orrery-core-jvm`, `orrery-core-iosarm64`, `orrery-core-iossimulatorarm64`, `orrery-core-iosx64`, `orrery-core-wasm-js`.
  - `orrery-lunar`: same target artifacts as `orrery-core`.
  - `orrery-compose`: root KMP module (`me.kozakov.orrery:orrery-compose`), `orrery-compose-android`, `orrery-compose-desktop`, `orrery-compose-iosarm64`, `orrery-compose-iossimulatorarm64`, `orrery-compose-iosx64`, `orrery-compose-wasm-js`.
- **CI matrix note for iOS:** Maven Central allows publishing iOS klibs from Linux hosts in Kotlin `2.1+` (`kotlin.native.enableKlibsCrossCompilation=true`) when no custom `cinterop` is used. Because Orrery uses only standard `platform.Foundation` APIs in `iosMain`, Linux CI (`ubuntu-latest`) can compile and publish all targets including iOS klibs, with a macOS job (`macos-latest`) optional only if running `iosSimulatorArm64Test`.

### ABI Validation

Kotlin Gradle Plugin's built-in `abiValidation {}` supports KMP modules in Kotlin `2.4.0` (dumping JVM/Android binary signatures plus klib signatures where enabled). `checkKotlinAbi` continues to gate CI.

---

## 5. Alternatives Considered (MAGI Consensus)

1. **Single `jvm()` vs `jvm()` + `androidTarget()` in `orrery-core` / `orrery-lunar`:**
   - *Decision:* Use `jvm()` (no `androidTarget()`) in `orrery-core` and `orrery-lunar`. Neither module uses Android APIs or resources; a single `jvm()` target resolves seamlessly for both Android and Desktop consumers while cutting build/publish time in half.
2. **External KMP datetime formatting library (e.g. `kotlinx-datetime` format builders vs platform `expect`/`actual`):**
   - *Decision:* `kotlinx-datetime` `0.8.0`'s `LocalDate.Format` uses English/ISO names unless given explicit locale string tables. Delegating locale-sensitive month/weekday names and medium/long date formats to the OS (`java.time`, `NSDateFormatter`, `Intl.DateTimeFormat`) via `PlatformDateFormat` gives native localization across all 4 platforms with zero bundle size overhead.
3. **Strict date input parsing via platform formatters vs pure Kotlin parser:**
   - *Decision:* Pure Kotlin parser in `commonMain` consuming the locale's `inputDatePattern()` token order. Platform formatters (`NSDateFormatter`, JS `Date.parse`) are notoriously lenient with out-of-range days (e.g. rolling Feb 30 to Mar 2); parsing the 3 integer fields and validating via `LocalDate(year, month, day)` guarantees identical strictness everywhere.

---

## 6. Phased Rollout Plan (`core` → `lunar` → `compose`)

Each phase lands as an independently verifiable PR once this design is signed off:

### Phase 1: `orrery-core` to KMP
1. Add `kotlin-multiplatform` plugin alias to `gradle/libs.versions.toml` and `build-logic/build.gradle.kts`.
2. Create `build-logic/src/main/kotlin/orrery.kmp-library.gradle.kts` configuring `jvm()`, `iosArm64()`, `iosSimulatorArm64()`, `iosX64()`, `wasmJs { browser() }`, `explicitApi()`, and `abiValidation()`.
3. Switch `orrery-core/build.gradle.kts` to `id("orrery.kmp-library")`; move `src/main/kotlin` → `src/commonMain/kotlin` and `src/test/kotlin` → `src/commonTest/kotlin`.
4. Verify: `./gradlew :orrery-core:check checkKotlinAbi :orrery-core:publishToMavenLocal`.

### Phase 2: `orrery-lunar` to KMP
1. Switch `orrery-lunar/build.gradle.kts` to `id("orrery.kmp-library")`.
2. Move `src/main/kotlin` → `src/commonMain/kotlin`.
3. Move unit tests to `src/commonTest/kotlin`; keep `GoldenFixturesTest.kt`, the full CSV loop in `LunarDateTest.kt`, and `src/test/resources/golden/*` in `src/jvmTest/`, while adding a 200-entry boundary fixture test in `commonTest`.
4. Verify: `./gradlew :orrery-lunar:check checkKotlinAbi :orrery-lunar:publishToMavenLocal`.

### Phase 3: `orrery-compose` to CMP
1. Add JetBrains Compose Multiplatform plugin (`org.jetbrains.compose`) to `gradle/libs.versions.toml` and `build-logic`.
2. Create `orrery.cmp-library.gradle.kts` with `androidTarget()`, `jvm("desktop")`, `ios*`, and `wasmJs`.
3. Implement `PlatformDateFormat` (`commonMain`, `jvmSharedMain`, `androidMain`, `desktopMain`, `iosMain`, `wasmJsMain`) and the pure-Kotlin strict pattern parser in `DateInput.kt`.
4. Move `orrery-compose` sources to `commonMain`; keep `java.util.Locale` extension overloads in `jvmSharedMain`.
5. Migrate interaction tests to `runComposeUiTest` in `commonTest`/`desktopTest`; keep Roborazzi snapshot tests in `androidUnitTest`.
6. Verify: `./gradlew ktlintCheck detekt checkKotlinAbi :orrery-core:check :orrery-lunar:check :orrery-compose:testDebugUnitTest :orrery-compose:desktopTest :orrery-compose:verifyRoborazziDebug :sample:assembleDebug publishToMavenLocal`.

---

## 7. Risks & Resolved Decisions

### Risks & Mitigations
- **Compose BOM vs JetBrains Compose Multiplatform version alignment:** Android Jetpack Compose BOM `2026.06.01` maps to a specific JetBrains Compose Multiplatform release. *Mitigation:* Pin the matching stable `org.jetbrains.compose` version in `libs.versions.toml` during Phase 3 and verify all 28 Roborazzi snapshots remain pixel-identical.
- **Wasm `Intl.Locale` week-start support across older headless browsers:** *Mitigation:* Wrap `Intl.Locale` weekInfo lookup in `try/catch` in `wasmJsMain`, falling back to `DayOfWeek.MONDAY` (ISO) or `SUNDAY` for `en-US`.

### Resolved Decisions (Andrii, 2026-10-23)
1. **Web target scope:** `wasmJs` only — no `js(IR)` target.
2. **Sample app scope:** Keep `:sample` as Android-only for now; the desktop entry point for `:sample` is deferred to a later phase.
