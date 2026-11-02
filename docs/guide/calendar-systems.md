# Calendar systems and holidays

## Day info and the lunar module

`DayInfoProvider` (in `orrery-core`) supplies a secondary per-day label —
holidays, prices, anything. `orrery-lunar` ships one for the Chinese lunisolar
calendar (valid 1900–2099), plus the underlying conversions:

```kotlin
val lunar = remember { LunarDayInfoProvider() } // or ChineseVariant.TRADITIONAL_TW / _HK

HorizontalCalendar(state = state) { day ->
    DefaultDay(day = day, info = lunar.info(day.date))
    // label priority: solar term > Gregorian festival > lunar festival > lunar day
}

date.toLunarDate()                  // Gregorian -> LunarDate (and back: toLocalDate())
solarTermsFor(2026)                 // Map<LocalDate, SolarTerm>, all 24 terms
SolarTerm.entries[0].displayName(ChineseVariant.SIMPLIFIED)
trunkBranchYear(2026)               // 干支 year name
```

Your own provider is one function:

```kotlin
val holidays = object : DayInfoProvider {
    override fun info(date: LocalDate): DayInfo? =
        publicHolidays[date]?.let { DayInfo(label = it, isHoliday = true) }
}
```

## Other calendars and public holidays

`orrery-calendars` labels days with Hijri or Persian dates, and `orrery-holidays`
marks public holidays. Both are `DayInfoProvider`s, so they compose with `+`;
the holiday wins the label and the other provider fills the rest:

```kotlin
val info = remember {
    PublicHolidayDayInfoProvider(PublicHolidays.forCountry("DE")!!, subdivision = "DE-BY", language = "de") +
        HijriDayInfoProvider(strings = HijriStrings.Arabic) // or PersianDayInfoProvider()
}

LocalDate(2026, 2, 18).toHijriDate()   // HijriDate(1447, 9, 1, UmmAlQura)
LocalDate(2026, 3, 21).toPersianDate() // PersianDate(1405, 1, 1)
PublicHolidays.forCountry("GB")!!.holidaysIn(2026, subdivision = "GB-SCT")
```

Umm al-Qura is a calculated calendar; dates announced after a moon sighting can
differ by a day, and holidays from Hijri rules are marked `isEstimated`. Build
your own `HolidayCalendar` (company days off) from the same `HolidayRule` types:
fixed dates, nth weekdays, Easter offsets (Western or Orthodox), Hijri or Persian
dates, one-off days, year ranges and weekend substitutes.
