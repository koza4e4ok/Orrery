# Richer DayInfo for Custom Providers (0.6.0 / 0.7.0) — Design

**Date:** 2026-10-22
**Status:** Proposed (awaiting Andrii sign-off)
**Issue:** #19 (Tracking roadmap: #26)
**Owners:** Ritsuko (design), Maya (implementation), Asuka (review)

## Context

`orrery-core` defines `DayInfoProvider` and `DayInfo` in `DayInfoProvider.kt`:

```kotlin
public interface DayInfoProvider {
    public fun info(date: LocalDate): DayInfo?
}

public data class DayInfo(
    val label: String?,
    val isHoliday: Boolean = false,
    val tags: List<String> = emptyList(),
)
```

While sufficient for basic secondary text in `LunarDayInfoProvider`, custom `DayInfoProvider` implementations hit three structural bottlenecks:

1. **No priority or provider composition:** Combining a holiday provider with a lunar or shift provider requires manual wrapper code because `DayInfo` has no `priority` field to decide which provider's `label` wins when two providers return data for the same date.
2. **No semantic or visual color hint:** `DefaultDay` renders `info.label` with a single static `colors.labelColor` (it does not even highlight `info.isHoliday == true`), and custom providers (e.g. hotel pricing tiers, shift types, festival categories) cannot supply a color hint without coupling `orrery-core` to Compose `Color`.
3. **No typed domain payload:** In `sample/AlmanacScreen.kt`, `AlmanacDetailCard` must re-run `date.toLunarDate()` and `solarTermsFor(date.year)` separately from `provider.info(date)` because `LunarDayInfoProvider` cannot attach its computed `LunarDate` / `SolarTerm` metadata to `DayInfo`.

**Deliberate non-goals (what we are NOT building):**

- No Compose `Color` or UI types inside `orrery-core`.
- No generic type parameter on `DayInfoProvider` (which would break source compatibility and prevent composing heterogeneous providers in a single list).

---

## 1. Options Evaluated (MAGI Consensus)

### Option A: Extra Primitive Fields Only
Add `priority: Int = 0` and `colorArgb: Long? = null` to `DayInfo`.
- **Pros:** Simple, flat value semantics, easy serialization.
- **Cons:** Custom providers (`LunarDayInfoProvider`, pricing/rate providers, liturgical calendars) still cannot pass structured domain models to custom `dayContent` or detail cards without string-encoding hacks in `tags` or separate lookup maps.

### Option B: Generic `DayInfo<out T>` and `DayInfoProvider<out T>`
Parameterize `DayInfo<out T>` and `DayInfoProvider<out T>`.
- **Pros:** Compile-time type safety when a screen uses a single provider.
- **Cons:** Breaks source and binary signatures of `DayInfoProvider`; composing a `LunarDayInfoProvider` (`DayInfo<LunarAlmanac>`) with a `HolidayProvider` (`DayInfo<Holiday>`) degrades to `DayInfo<Any?>` anyway while infectious `<*>` projections pollute `DefaultDay` and `CalendarDefaults`.

### Option C (Recommended): Hybrid — Semantic Fields + Optional `payload: Any? = null` with Reified Accessor
Keep `DayInfo` and `DayInfoProvider` non-generic, add `priority: Int = 0`, `colorArgb: Long? = null`, and `payload: Any? = null` with an inline reified helper `inline fun <reified T> DayInfo.payloadAs(): T?`, plus an operator `DayInfoProvider.plus(other: DayInfoProvider)` for zero-boilerplate provider composition.
- **Melchior (Technical):** Preserves clean non-generic signatures across `DayInfoProvider` and `DefaultDay` while enabling type-safe extraction (`info.payloadAs<LunarMetadata>()`) when needed.
- **Balthasar (Operational):** 100% binary-compatible with `0.5.0` if a 3-argument secondary constructor and 3-argument `copy` bridge are retained (see §3).
- **Casper (Product):** Immediately simplifies `AlmanacScreen`, enables automatic holiday/custom label tinting in `DefaultDay`, and unlocks multi-provider composition (#23).

---

## 2. Recommended API (`orrery-core` & `orrery-compose`)

### `orrery-core/.../DayInfoProvider.kt`

```kotlin
package me.kozakov.orrery.core

import kotlinx.datetime.LocalDate

public interface DayInfoProvider {
    public fun info(date: LocalDate): DayInfo?

    /**
     * Combines this provider with [other]. When both return non-null [DayInfo]
     * for a date:
     * - The entry with the higher [DayInfo.priority] supplies the primary
     *   [DayInfo.label], [DayInfo.colorArgb], and [DayInfo.payload] (ties favor `this`).
     * - [DayInfo.isHoliday] is `this.isHoliday || other.isHoliday`.
     * - [DayInfo.tags] is the deduplicated union of both tag lists in order.
     */
    public operator fun plus(other: DayInfoProvider): DayInfoProvider =
        CompositeDayInfoProvider(this, other)
}

/** Standard priority tiers for [DayInfo.priority] when composing providers. */
public object DayInfoPriority {
    public const val DEFAULT: Int = 0
    public const val SECONDARY: Int = 10
    public const val SOLAR_TERM: Int = 20
    public const val FESTIVAL: Int = 30
    public const val PUBLIC_HOLIDAY: Int = 40
}

public data class DayInfo(
    public val label: String?,
    public val isHoliday: Boolean = false,
    public val tags: List<String> = emptyList(),
    public val priority: Int = DayInfoPriority.DEFAULT,
    public val colorArgb: Long? = null,
    public val payload: Any? = null,
) {
    /** Binary-compatible 3-argument constructor for 0.5.x callers. */
    public constructor(
        label: String?,
        isHoliday: Boolean,
        tags: List<String>,
    ) : this(
        label = label,
        isHoliday = isHoliday,
        tags = tags,
        priority = DayInfoPriority.DEFAULT,
        colorArgb = null,
        payload = null,
    )
}

/** Type-safe extraction of [DayInfo.payload], returning null if absent or of a different type. */
public inline fun <reified T> DayInfo.payloadAs(): T? = payload as? T
```

### `orrery-lunar` Enrichment (`LunarDayInfoProvider.kt`)

`LunarDayInfoProvider` populates both `priority` and a typed `LunarDayMetadata` payload so callers like `AlmanacScreen` never have to recompute lunar dates or solar terms:

```kotlin
public data class LunarDayMetadata(
    public val lunarDate: LunarDate,
    public val solarTerm: SolarTerm? = null,
    public val gregorianFestival: String? = null,
    public val traditionalFestival: String? = null,
)
```

- Priority assignment in `LunarDayInfoProvider`:
  - Traditional or Gregorian festival: `DayInfoPriority.FESTIVAL` (`30`)
  - Solar term: `DayInfoPriority.SOLAR_TERM` (`20`)
  - Ordinary lunar day: `DayInfoPriority.DEFAULT` (`0`)

### `orrery-compose` Rendering (`CalendarDefaults.kt` & `DefaultDay.kt`)

1. **`CalendarDayColors`**: Add `holidayLabelColor: Color = MaterialTheme.colorScheme.primary` to `CalendarDefaults.dayColors(...)`.
2. **Label color resolution in `DefaultDay`**:
   ```kotlin
   val resolvedLabelColor = when {
       !available -> colors.unavailableContentColor
       isSelected -> colors.selectedContentColor
       info.colorArgb != null -> Color(info.colorArgb)
       info.isHoliday -> colors.holidayLabelColor
       else -> colors.labelColor
   }
   ```

---

## 3. Binary Compatibility Impact (`orrery-core.api`)

Because `DayInfo` is a Kotlin `data class`, expanding its primary constructor from 3 parameters to 6 parameters affects generated JVM bytecode signatures in `orrery-core/api/orrery-core.api`:

| Symbol | Impact | Mitigation |
|---|---|---|
| `<init>(Ljava/lang/String;ZLjava/util/List;)V` | Preserved via explicit 3-arg secondary constructor | 100% binary compatible |
| `<init>(Ljava/lang/String;ZLjava/util/List;ILjava/lang/Long;Ljava/lang/Object;)V` | Added (new 6-arg primary constructor) | Additive |
| `getPriority()`, `getColorArgb()`, `getPayload()`, `component4..6()` | Added | Additive |
| `copy$default` synthetic mask (3-arg vs 6-arg) | Changes from 3-arg `copy` to 6-arg `copy` | Pre-1.0 acceptable (or preserve a `@Deprecated(HIDDEN)` 3-arg `copy` bridge if strict `0.5.0` `copy()` binary compatibility is desired) |

Running `./gradlew updateKotlinAbi` updates `orrery-core/api/orrery-core.api` and `orrery-lunar/api/orrery-lunar.api`, verified by `checkKotlinAbi`.

---

## 4. Testing & Sample App Plan

- **Core unit tests (`DayInfoProviderTest`):**
  - `DayInfo` default values, 3-arg constructor compatibility, and `payloadAs<T>()` type matching / mismatch returns `null`.
  - `providerA + providerB` composition: higher `priority` wins `label`/`colorArgb`/`payload`, ties prefer left-hand side, `isHoliday` ORs together, `tags` union preserves order without duplicates, null on one side returns the other side.
- **Lunar unit tests (`LunarDayInfoProviderTest`):**
  - Verifies `info.payloadAs<LunarDayMetadata>()` contains the matching `LunarDate` and `SolarTerm`, and `priority` reflects festival vs solar term vs ordinary lunar day.
- **Compose unit & snapshot tests (`DefaultDayTest` / `StylesSnapshotTest`):**
  - `DefaultDay` tints `info.label` with `holidayLabelColor` when `info.isHoliday == true`, and with `Color(info.colorArgb)` when `colorArgb != null` (while keeping `selectedContentColor` when the day is selected).
- **Sample app (`sample/AlmanacScreen.kt` & `sample/TripsScreen.kt`):**
  - `AlmanacDetailCard` reads `info?.payloadAs<LunarDayMetadata>()` directly from `provider.info(date)` instead of recomputing `toLunarDate()` and `solarTermsFor()`.

---

## 5. Open Decisions for Sign-Off

1. **Strict 0.5.x binary compatibility for `DayInfo.copy(...)` vs clean pre-1.0 data class:** Since Orrery is pre-1.0 (`0.5.x`), should we keep `DayInfo` as a clean 6-property `data class` (with the 3-arg secondary constructor for Java/Kotlin construction compatibility) and accept the `copy$default` signature update in `orrery-core.api`, or add a `@Deprecated(level = DeprecationLevel.HIDDEN)` 3-arg `copy` bridge? *(Recommendation: clean 6-property `data class` + 3-arg secondary constructor; pre-1.0 minor releases explicitly allow ABI evolution.)*
