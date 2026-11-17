package me.kozakov.orrery.lunar

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNull

/**
 * The tier B type of orrery-lunar is a plain class since 1.0 (#89, ledger section D). Its
 * hand-written equals, hashCode, copy and toString must behave like the data class it replaces.
 */
class PlainClassContractTest {
    private val lunarDate = LunarDate(2026, 5, 5, isLeapMonth = true)

    private val metadata =
        LunarDayMetadata(
            lunarDate = lunarDate,
            solarTerm = SolarTerm.XIAZHI,
            gregorianFestival = "Gregorian",
            traditionalFestival = "Dragon Boat",
        )

    /**
     * [equal] is built separately from [base]; [copied] is `base.copy()` with no arguments; each of [differing]
     * changes one property of [base] to a value with a different hash.
     *
     * Every property of [base] differs from its constructor default, from null and from every other property of
     * the same type, so [copied] only equals [base] if each `copy` default reads its own property.
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
    fun `LunarDayMetadata compares every property`() {
        assertContract(
            metadata,
            LunarDayMetadata(LunarDate(2026, 5, 5, true), SolarTerm.XIAZHI, "Gregorian", "Dragon Boat"),
            metadata.copy(),
            metadata.copy(lunarDate = LunarDate(2026, 5, 6, isLeapMonth = true)),
            metadata.copy(solarTerm = null),
            metadata.copy(solarTerm = SolarTerm.MANGZHONG),
            metadata.copy(gregorianFestival = null),
            metadata.copy(gregorianFestival = "Other"),
            metadata.copy(traditionalFestival = null),
            metadata.copy(traditionalFestival = "Other"),
            metadata.copy(gregorianFestival = "Dragon Boat", traditionalFestival = "Gregorian"),
        )
    }

    @Test
    fun `LunarDayMetadata copy preserves unspecified fields`() {
        val noTerm = metadata.copy(solarTerm = null)
        assertNull(noTerm.solarTerm)
        assertEquals(lunarDate, noTerm.lunarDate)
        assertEquals("Gregorian", noTerm.gregorianFestival)
        assertEquals("Dragon Boat", noTerm.traditionalFestival)

        val moved = metadata.copy(lunarDate = LunarDate(2026, 5, 6), traditionalFestival = null)
        assertEquals(LunarDate(2026, 5, 6), moved.lunarDate)
        assertEquals(SolarTerm.XIAZHI, moved.solarTerm)
        assertEquals("Gregorian", moved.gregorianFestival)
        assertNull(moved.traditionalFestival)
    }

    @Test
    fun `LunarDayMetadata toString keeps the data class format`() {
        assertEquals(
            "LunarDayMetadata(lunarDate=LunarDate(year=2026, month=5, day=5, isLeapMonth=true), " +
                "solarTerm=XIAZHI, gregorianFestival=Gregorian, traditionalFestival=Dragon Boat)",
            metadata.toString(),
        )
        assertEquals(
            "LunarDayMetadata(lunarDate=LunarDate(year=2026, month=5, day=5, isLeapMonth=true), " +
                "solarTerm=null, gregorianFestival=null, traditionalFestival=null)",
            LunarDayMetadata(lunarDate).toString(),
        )
    }
}
