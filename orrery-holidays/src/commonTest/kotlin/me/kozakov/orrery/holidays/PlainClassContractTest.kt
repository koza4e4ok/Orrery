package me.kozakov.orrery.holidays

import kotlinx.datetime.LocalDate
import kotlinx.datetime.Month
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals
import kotlin.test.assertNull

/**
 * The tier B types of orrery-holidays are plain classes since 1.0 (#89, ledger section D).
 * Their hand-written equals, hashCode, copy and toString must behave like the data classes
 * they replace.
 */
class PlainClassContractTest {
    private val may8 = LocalDate(2026, 5, 8)

    private val rule =
        HolidayRule(
            id = "de.test-day",
            names = mapOf("en" to "Test Day", "de" to "Testtag"),
            date = HolidayDate.Fixed(Month.MAY, 8),
            type = HolidayType.Bank,
            years = 2000..2030,
            observance = Observance.NextWorkingDay,
            subdivisions = setOf("DE-BY"),
        )

    private val holiday =
        PublicHoliday(
            date = may8,
            ruleId = "de.test-day",
            name = "Test Day",
            type = HolidayType.Bank,
            isSubstitute = true,
            isEstimated = true,
        )

    /**
     * [equal] is built separately from [base]; [copied] is `base.copy()` with no arguments; each of [differing]
     * changes one property of [base] to a value with a different hash.
     *
     * Every property of [base] differs from its constructor default, from null and from every other property of
     * the same type, so [copied] only equals [base] if each `copy` default reads its own property. The exception
     * is the two `PublicHoliday` flags, both `true`, which `PublicHoliday keeps its two flags apart` covers.
     */
    private fun <T : Any> assertContract(
        base: T,
        equal: T,
        copied: T,
        vararg differing: T,
    ) {
        assertEquals(base, equal)
        assertEquals(equal, base)
        assertEquals(base.hashCode(), equal.hashCode())
        assertEquals(base.toString(), equal.toString())
        assertEquals(base, copied, "copy() with no arguments")
        assertEquals(base.hashCode(), copied.hashCode(), "copy() with no arguments")
        assertNotEquals<Any?>(base, null)
        assertNotEquals<Any>(base, "not a $base")
        differing.forEachIndexed { index, variant ->
            assertNotEquals(base, variant, "variant $index: $variant")
            assertNotEquals(variant, base, "variant $index: $variant")
            assertNotEquals(base.hashCode(), variant.hashCode(), "hashCode of variant $index: $variant")
        }
    }

    @Test
    fun `HolidayRule compares every property`() {
        assertContract(
            rule,
            HolidayRule(
                "de.test-day",
                mapOf("en" to "Test Day", "de" to "Testtag"),
                HolidayDate.Fixed(Month.MAY, 8),
                HolidayType.Bank,
                2000..2030,
                Observance.NextWorkingDay,
                setOf("DE-BY"),
            ),
            rule.copy(),
            rule.copy(id = "de.other-day"),
            rule.copy(names = mapOf("en" to "Test Day")),
            rule.copy(date = HolidayDate.Fixed(Month.MAY, 9)),
            rule.copy(type = HolidayType.Optional),
            rule.copy(years = 2001..2030),
            rule.copy(observance = Observance.SundayToMonday),
            rule.copy(subdivisions = null),
            rule.copy(subdivisions = setOf("DE-BW")),
        )
    }

    @Test
    fun `HolidayRule compares maps and sets by content like the data class`() {
        val reordered =
            rule.copy(names = mapOf("de" to "Testtag", "en" to "Test Day"), subdivisions = setOf("DE-BY"))
        assertEquals(rule, reordered)
        assertEquals(rule.hashCode(), reordered.hashCode())
    }

    @Test
    fun `HolidayRule copy preserves unspecified fields`() {
        val moved = rule.copy(date = HolidayDate.EasterOffset(1))
        assertEquals(HolidayDate.EasterOffset(1), moved.date)
        assertEquals(rule.id, moved.id)
        assertEquals(rule.names, moved.names)
        assertEquals(HolidayType.Bank, moved.type)
        assertEquals(2000..2030, moved.years)
        assertEquals(Observance.NextWorkingDay, moved.observance)
        assertEquals(setOf("DE-BY"), moved.subdivisions)

        val national = rule.copy(subdivisions = null, years = 2010..2020)
        assertNull(national.subdivisions)
        assertEquals(2010..2020, national.years)
        assertEquals(Observance.NextWorkingDay, national.observance)
        assertEquals("Testtag", national.name("de-DE"))
    }

    @Test
    fun `HolidayRule copy runs the init check`() {
        assertFailsWith<IllegalArgumentException> { rule.copy(names = mapOf("de" to "Testtag")) }
    }

    @Test
    fun `HolidayRule toString keeps the data class format`() {
        assertEquals(
            "HolidayRule(id=de.test-day, names={en=Test Day, de=Testtag}, date=Fixed(month=MAY, day=8), " +
                "type=Bank, years=2000..2030, observance=NextWorkingDay, subdivisions=[DE-BY])",
            rule.toString(),
        )
        assertEquals(
            "HolidayRule(id=x, names={en=X}, date=OneOff(date=2026-05-08), type=Public, " +
                "years=-2147483648..2147483647, observance=None, subdivisions=null)",
            HolidayRule("x", mapOf("en" to "X"), HolidayDate.OneOff(may8)).toString(),
        )
    }

    @Test
    fun `PublicHoliday compares every property`() {
        assertContract(
            holiday,
            PublicHoliday(may8, "de.test-day", "Test Day", HolidayType.Bank, true, true),
            holiday.copy(),
            holiday.copy(date = LocalDate(2026, 5, 11)),
            holiday.copy(ruleId = "de.other-day"),
            holiday.copy(name = "Testtag"),
            holiday.copy(type = HolidayType.Public),
            holiday.copy(isSubstitute = false),
            holiday.copy(isEstimated = false),
        )
    }

    @Test
    fun `PublicHoliday keeps its two flags apart`() {
        // Both flags are Boolean, so the base above cannot tell them apart; these two can.
        val substitute = holiday.copy(isSubstitute = true, isEstimated = false)
        val estimated = holiday.copy(isSubstitute = false, isEstimated = true)
        assertNotEquals(substitute, estimated)
        assertNotEquals(substitute.hashCode(), estimated.hashCode())
        assertEquals(substitute, substitute.copy())
        assertEquals(estimated, estimated.copy())
        assertEquals(substitute.hashCode(), substitute.copy().hashCode())
        assertEquals(estimated.hashCode(), estimated.copy().hashCode())
    }

    @Test
    fun `PublicHoliday copy preserves unspecified fields`() {
        val renamed = holiday.copy(name = "Testtag")
        assertEquals("Testtag", renamed.name)
        assertEquals(may8, renamed.date)
        assertEquals("de.test-day", renamed.ruleId)
        assertEquals(HolidayType.Bank, renamed.type)
        assertEquals(true, renamed.isSubstitute)
        assertEquals(true, renamed.isEstimated)

        val moved = holiday.copy(date = LocalDate(2026, 5, 11), isSubstitute = false)
        assertEquals("Test Day", moved.name)
        assertEquals(false, moved.isSubstitute)
        assertEquals(true, moved.isEstimated)
    }

    @Test
    fun `PublicHoliday toString keeps the data class format`() {
        assertEquals(
            "PublicHoliday(date=2026-05-08, ruleId=de.test-day, name=Test Day, type=Bank, isSubstitute=true, " +
                "isEstimated=true)",
            holiday.toString(),
        )
        assertEquals(
            "PublicHoliday(date=2026-05-08, ruleId=r, name=N, type=Public, isSubstitute=false, isEstimated=false)",
            PublicHoliday(may8, "r", "N", HolidayType.Public).toString(),
        )
    }
}
