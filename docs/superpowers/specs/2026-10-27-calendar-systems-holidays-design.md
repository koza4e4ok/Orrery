# Calendar Systems and Public Holidays — Design

**Date:** 2026-10-27
**Status:** Proposed (awaiting Andrii's sign-off)
**Issue:** #23 (Tracking roadmap: #26)
**Owners:** Ritsuko (design), Maya (implementation), Asuka (review), Kaji (license check)

## Context

`orrery-lunar` proves the plug-in model: a pure-Kotlin KMP module (`orrery.kmp-library`:
jvm, iosArm64/SimulatorArm64/X64, wasmJs) converts `LocalDate` ↔ `LunarDate` from
compact tables and exposes `LunarDayInfoProvider : DayInfoProvider`. Its tests are
split: `commonTest` holds portable boundary cases and invariants; `jvmTest`
(`GoldenFixturesTest`) checks every day 1900–2099 against gzipped golden CSVs and a
`java.time` oracle. Since #19, `DayInfo` carries `priority`, `colorArgb` and a typed
`payload`, and providers compose with `+`.

#23 asks for the first 1–2 calendar systems, a public-holidays provider, licensing
checked, and golden tests like `orrery-lunar`.

**Not building:** Hebrew calendar (next candidate after this), Japanese era (cheap
follow-up: Gregorian dates plus an era table, no conversion engine), sighting-based or
locally announced Hijri dates, regional observances beyond the listed subdivisions,
working-day/business-day arithmetic, any network lookup, any runtime resource files.

---

## 1. Calendar systems: Hijri + Persian (Jalali)

### Choice

| Candidate | Users | Engine cost | Data | Verdict |
|-----------|-------|-------------|------|---------|
| **Hijri** (Umm al-Qura + tabular) | ~1.9 bn; official in Saudi Arabia, religious dates worldwide | table + arithmetic | Umm al-Qura month lengths 1300–1600 AH | **First** |
| **Persian/Jalali** | Official civil calendar in Iran and Afghanistan | arithmetic only | none | **Second** |
| Japanese era | Japan | trivial | era start dates | Follow-up, not a conversion engine |
| Hebrew | Israel, religious | arithmetic (molad) | none | Later |

Persian over Japanese era: it is a real conversion calendar with a large user base and
needs no data table, so it exercises the module design better; Japanese era can ship
later as a formatter.

### Module

New module `orrery-calendars` (`orrery.kmp-library` + `orrery.publish`, depends on
`orrery-core` and kotlinx-datetime only), packages `me.kozakov.orrery.calendars.hijri`
and `...calendars.persian`. One module instead of one per system: both engines are a
few hundred lines and the holidays module needs both.

### Hijri API

```kotlin
public enum class HijriVariant { UmmAlQura, Tabular }   // Tabular = civil epoch (16 Jul 622 Julian), leap years 2,5,7,10,13,16,18,21,24,26,29

public data class HijriDate(val year: Int, val month: Int, val day: Int, val variant: HijriVariant)

public fun LocalDate.toHijriDate(variant: HijriVariant = HijriVariant.UmmAlQura): HijriDate
public fun HijriDate.toLocalDate(): LocalDate
public fun HijriDate.monthLength(): Int

public class HijriDayInfoProvider(
    variant: HijriVariant = HijriVariant.UmmAlQura,
    strings: HijriStrings = HijriStrings.English,   // also Arabic
) : DayInfoProvider
```

- **Umm al-Qura:** supported range 1300-01-01 … 1600-12-29 AH (1882-11-12 … 2174-11-25 CE).
  Table = per-year start offset + 12-bit month-length mask, ≈1.2 KB as a Kotlin
  `IntArray`/string literal (same technique as `LunarTables`). Outside the range,
  conversion throws `IllegalArgumentException` and the provider returns `null`; no silent
  fallback to tabular, which can be a day or two off.
- **Tabular:** pure arithmetic, unbounded within `LocalDate`.
- Provider: day 1 shows the month name, other days the day number (as
  `LunarDayInfoProvider`), `priority = SECONDARY`, `tags = ["hijri"]`, `payload = HijriDate`.
- KDoc states plainly that Umm al-Qura is a calculated calendar; religious dates announced
  by moon sighting may differ by ±1 day.

### Persian API

```kotlin
public data class PersianDate(val year: Int, val month: Int, val day: Int)
public fun LocalDate.toPersianDate(): PersianDate
public fun PersianDate.toLocalDate(): LocalDate
public fun PersianDate.isLeapYear(): Boolean
public class PersianDayInfoProvider(strings: PersianStrings = PersianStrings.English /* also Persian */) : DayInfoProvider
```

- Algorithm: Borkowski's arithmetic with the published "breaks" table (the algorithm used
  by jalaali-js, MIT), reimplemented from the paper, valid for −61 … 3177 AP. It matches
  the official (astronomical, Tehran-noon) calendar over the supported range, unlike the
  plain 33-year cycle.
- Provider mirrors Hijri (`tags = ["persian"]`, `payload = PersianDate`).

### Licensing

| Input | License | Use |
|-------|---------|-----|
| Umm al-Qura month lengths | Derived from ICU4J `IslamicCalendar` UMALQURA table (Unicode License v3, permissive) | Encoded once into our table; attribution in NOTICE |
| Tabular, Persian algorithms | Published algorithms (facts/math) | Own implementation |
| OpenJDK `hijrah-config-*.properties` | GPLv2 + CE | **Not copied.** `HijrahDate` is used only as a test oracle at test time |
| ICU4J | Unicode License v3 | `jvmTest` dependency only, never in published POMs |

Kaji confirms the NOTICE wording in the implementation PR.

---

## 2. Public holidays: rule engine

### Options

| Option | Verdict |
|--------|---------|
| Runtime API (Nager.Date, Calendarific) | No: network, availability, some paid |
| Bundle date-holidays data | No: its data is CC BY-SA 3.0 (share-alike), not compatible with an Apache-2.0 bundle |
| Wrap jollyday (Apache-2.0) | No: JVM-only, XML + JAXB, not KMP |
| Port python-holidays (MIT) | Code port is heavy and Python-shaped; use it as a **test oracle** instead |
| **Own small rule engine; rules written in Kotlin from primary official sources** | **Chosen**: holiday dates are facts; zero restrictive data licenses; KMP; no resources |

### Module and API

New module `orrery-holidays` (`orrery.kmp-library` + `orrery.publish`), depends on
`orrery-core` and `orrery-calendars` (for Hijri/Persian-dated rules).

```kotlin
public sealed interface HolidayDate {
    public data class Fixed(val month: Month, val day: Int) : HolidayDate
    public data class NthWeekday(val month: Month, val dayOfWeek: DayOfWeek, val n: Int) : HolidayDate // n = -1: last
    public data class WeekdayRelative(val month: Month, val day: Int, val dayOfWeek: DayOfWeek, val after: Boolean) : HolidayDate
    public data class EasterOffset(val days: Int, val church: Church = Church.Western) : HolidayDate // Church.Orthodox = Julian computus
    public data class HijriFixed(val month: Int, val day: Int) : HolidayDate     // Umm al-Qura, marked estimated
    public data class PersianFixed(val month: Int, val day: Int) : HolidayDate
    public data class OneOff(val date: LocalDate) : HolidayDate                  // e.g. a coronation bank holiday
}

public enum class Observance { None, NearestWeekday /* Sat→Fri, Sun→Mon */, NextWorkingDay /* UK substitute */ }
public enum class HolidayType { Public, Bank, Optional, Observance }

public data class HolidayRule(
    val id: String,                         // stable, e.g. "us.independence-day"
    val names: Map<String, String>,         // BCP-47 language → name; must contain "en" and the local language
    val date: HolidayDate,
    val type: HolidayType = HolidayType.Public,
    val years: IntRange = Int.MIN_VALUE..Int.MAX_VALUE,
    val observance: Observance = Observance.None,
    val subdivisions: Set<String>? = null,  // ISO 3166-2 codes; null = nationwide
)

public data class PublicHoliday(
    val date: LocalDate, val ruleId: String, val name: String, val type: HolidayType,
    val isSubstitute: Boolean, val isEstimated: Boolean,
)

public class HolidayCalendar(public val country: String, public val rules: List<HolidayRule>) {
    public fun holidaysIn(year: Int, subdivision: String? = null): List<PublicHoliday>
    public fun holidaysOn(date: LocalDate, subdivision: String? = null): List<PublicHoliday>
}

public object PublicHolidays {
    public val supportedCountries: Set<String>
    public fun forCountry(isoCode: String): HolidayCalendar?   // ISO 3166-1 alpha-2
}

public class PublicHolidayDayInfoProvider(
    calendar: HolidayCalendar,
    subdivision: String? = null,
    language: String? = null,               // falls back to "en"
    types: Set<HolidayType> = setOf(HolidayType.Public, HolidayType.Bank),
) : DayInfoProvider
```

- Provider: `label = name`, `isHoliday = true`, `priority = DayInfoPriority.PUBLIC_HOLIDAY`,
  `tags = ["holiday", type]` (+ `"substitute"`, `"estimated"`), `payload = PublicHoliday`
  (several on one date → first by rule order, all in the payload list). Results are
  cached per year in a small map (bounded to 4 years) because `info` is called per cell.
- Apps compose it: `PublicHolidayDayInfoProvider(PublicHolidays.forCountry("US")!!) + LunarDayInfoProvider()`.
- Users can build their own `HolidayCalendar` (company holidays) with the same rules.
- Each country's rules live in one file `rules/<CC>.kt` with a header comment listing the
  primary sources (statute or government page) and the date checked.

### Initial countries

US (federal), GB (England & Wales, Scotland, Northern Ireland as subdivisions), DE
(nationwide + the 16 states), UA. These cover every rule type except Hijri/Persian,
including year-ranged changes (UA moved Christmas to 25 Dec from 2023; UA Orthodox
Easter; GB one-off bank holidays; US Juneteenth from 2021). Hijri/Persian-dated
countries (e.g. TR, AE, IR) follow in a second PR once the engine is merged; their Hijri
holidays carry `isEstimated = true`.

---

## 3. Golden tests (same shape as `orrery-lunar`)

| Layer | `orrery-calendars` | `orrery-holidays` |
|-------|--------------------|-------------------|
| `commonTest` (all targets) | Known dates (1 Ramadan 1447 = 2026-02-18 UAQ; Nowruz 1405 = 2026-03-21), range ends, leap rules, round-trip samples, month lengths 29/30 | Rule-type unit tests (Easter both churches 2000–2050 from a small inline table, nth/last weekday, observance shifts, year ranges, subdivisions) |
| `jvmTest` fixtures | `golden/hijri-uaq-daily.csv.gz` (every day 1882-11-12…2174-11-25), `golden/persian-daily.csv.gz` (1921-03-21…2121-03-20) | `golden/holidays-<cc>.csv` for 2000–2050: date, rule id, subdivision, substitute flag |
| `jvmTest` oracles | `java.time.chrono.HijrahDate` (Umm al-Qura) for every day; ICU4J `IslamicCalendar(CIVIL)` for tabular; ICU4J `PersianCalendar` | Fixtures generated by a pinned python-holidays (MIT) script in `tools/golden/`; jollyday (Apache-2.0) as a second oracle where it covers the country |

- Fixtures are generated once by checked-in scripts with pinned versions; the test fails on
  any difference.
- Known, sourced disagreements with an oracle go in `golden/<name>-exceptions.csv` (date,
  ours, oracle, source URL). An exception without a source fails review.
- Exhaustive daily round-trip loops run in `commonTest` only for a 10-year window to keep
  wasm/iOS test time low; the full range runs on JVM.

---

## Rollout

1. PR "feat(calendars): orrery-calendars with Hijri (Umm al-Qura, tabular) (#23)".
2. PR "feat(calendars): Persian/Jalali calendar (#23)".
3. PR "feat(holidays): orrery-holidays rule engine + US, GB, DE, UA (#23)".
4. Sample: an "Almanac" toggle for Hijri/Persian labels and a country holiday picker.
5. Follow-up issues: Hijri-dated countries, Japanese era, Hebrew.

Each PR: ABI dump, CHANGELOG, README module table, NOTICE attribution, Dokka inclusion.

## Risks

- **Hijri expectations:** users may expect sighting-based dates. Mitigation: `isEstimated`,
  `"estimated"` tag, KDoc.
- **Holiday law changes:** rules need upkeep. Mitigation: year-ranged rules, sources in
  each file, a yearly fixture refresh issue.
- **Size:** tables ≈ few KB; rules for four countries well under 50 KB of bytecode.

## Open decisions for Andrii

1. Second calendar: Persian (proposed) or Japanese era?
2. Initial countries: US, GB, DE, UA (proposed)? UA public holidays are not days off under
   martial law; propose listing them as `Public` with a KDoc note rather than modelling
   martial law.
3. Two new modules (`orrery-calendars`, `orrery-holidays`) as proposed, or one?
