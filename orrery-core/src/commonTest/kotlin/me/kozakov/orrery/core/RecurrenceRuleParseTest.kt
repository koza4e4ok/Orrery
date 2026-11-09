package me.kozakov.orrery.core

import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class RecurrenceRuleParseTest {
    private fun d(iso: String): LocalDate = LocalDate.parse(iso)

    @Test
    fun `parse accepts the RRULE prefix whitespace and lower case and formats canonically`() {
        val rule = RecurrenceRule.parse("  rrule:freq=weekly;interval=2;byday=su,tu;wkst=su;count=4 ")
        assertEquals(Frequency.WEEKLY, rule.frequency)
        assertEquals(2, rule.interval)
        assertEquals(setOf(ByDay(DayOfWeek.TUESDAY), ByDay(DayOfWeek.SUNDAY)), rule.byDay)
        assertEquals(DayOfWeek.SUNDAY, rule.weekStart)
        assertEquals(4, rule.count)
        assertEquals("RRULE:FREQ=WEEKLY;INTERVAL=2;COUNT=4;BYDAY=TU,SU;WKST=SU", rule.toRfc5545String())
        assertEquals("FREQ=WEEKLY;INTERVAL=2;COUNT=4;BYDAY=TU,SU;WKST=SU", rule.toRfc5545String(includePrefix = false))
    }

    @Test
    fun `canonical format orders parts and values and omits defaults`() {
        val rule = RecurrenceRule.parse("BYSETPOS=1,-1;BYDAY=-1FR,+2MO,MO;BYMONTH=12,3;FREQ=YEARLY;WKST=MO;INTERVAL=1")
        assertEquals("RRULE:FREQ=YEARLY;BYMONTH=3,12;BYDAY=MO,2MO,-1FR;BYSETPOS=-1,1", rule.toRfc5545String())
        assertEquals(
            "RRULE:FREQ=MONTHLY;UNTIL=20261231;BYMONTHDAY=-1,1,15",
            RecurrenceRule.parse("FREQ=MONTHLY;BYMONTHDAY=15,-1,1;UNTIL=20261231T235959Z").toRfc5545String(),
        )
        assertEquals("RRULE:FREQ=DAILY", RecurrenceRule.parse("FREQ=DAILY;").toRfc5545String())
    }

    @Test
    fun `every RFC example used by the expansion tests round trips`() {
        val examples =
            listOf(
                "FREQ=MONTHLY;COUNT=3",
                "FREQ=MONTHLY;COUNT=3;BYMONTHDAY=-1",
                "FREQ=MONTHLY;COUNT=3;BYDAY=2MO",
                "FREQ=YEARLY;COUNT=3;BYDAY=20MO",
                "FREQ=YEARLY;COUNT=3;BYMONTH=11;BYDAY=4TH",
                "FREQ=MONTHLY;COUNT=3;BYDAY=MO,TU,WE,TH,FR;BYSETPOS=-1",
                "FREQ=WEEKLY;INTERVAL=2;COUNT=4;BYDAY=TU,SU;WKST=SU",
                "FREQ=DAILY;UNTIL=20260105",
            )
        for (example in examples) {
            assertEquals("RRULE:$example", RecurrenceRule.parse(example).toRfc5545String(), example)
        }
    }

    @Test
    fun `unsupported parts and frequencies are rejected with the part name`() {
        val cases =
            mapOf(
                "FREQ=YEARLY;BYYEARDAY=1" to "Unsupported RRULE part BYYEARDAY",
                "FREQ=YEARLY;BYWEEKNO=20" to "Unsupported RRULE part BYWEEKNO",
                "FREQ=DAILY;BYHOUR=9" to "Unsupported RRULE part BYHOUR",
                "FREQ=DAILY;BYMINUTE=0" to "Unsupported RRULE part BYMINUTE",
                "FREQ=DAILY;BYSECOND=0" to "Unsupported RRULE part BYSECOND",
                "RDATE:20260101" to "Unsupported RRULE part RDATE",
                "EXRULE:FREQ=DAILY" to "Unsupported RRULE part EXRULE",
                "FREQ=HOURLY" to "Unsupported RRULE part FREQ=HOURLY",
                "FREQ=MINUTELY" to "Unsupported RRULE part FREQ=MINUTELY",
                "FREQ=SECONDLY" to "Unsupported RRULE part FREQ=SECONDLY",
                "FREQ=DAILY;FREQ=WEEKLY" to "Duplicate RRULE part FREQ",
                "FREQ=DAILY;INTERVAL=" to "Empty value for RRULE part INTERVAL",
                "FREQ=DAILY;FOO=1" to "Unknown RRULE part FOO",
                "INTERVAL=2" to "FREQ",
                "FREQ=DAILY;INTERVAL=abc" to "INTERVAL",
                "FREQ=DAILY;UNTIL=2026-01-01" to "UNTIL",
                "FREQ=DAILY;UNTIL=20260231" to "UNTIL",
                "FREQ=MONTHLY;BYDAY=XX" to "BYDAY",
                "FREQ=MONTHLY;BYDAY=0MO" to "BYDAY",
                "FREQ=MONTHLY;BYDAY=123MO" to "BYDAY",
                "FREQ=WEEKLY;WKST=XY" to "WKST",
            )
        for ((input, message) in cases) {
            val error = assertFailsWith<IllegalArgumentException>(input) { RecurrenceRule.parse(input) }
            assertTrue(error.message.orEmpty().contains(message), "$input: ${error.message}")
            assertNull(RecurrenceRule.parseOrNull(input), input)
        }
    }

    @Test
    fun `invalid combinations are rejected by parse and by the constructor`() {
        val invalid =
            listOf(
                "FREQ=MONTHLY;BYMONTH=13",
                "FREQ=MONTHLY;BYMONTHDAY=0",
                "FREQ=MONTHLY;BYMONTHDAY=32",
                "FREQ=WEEKLY;BYMONTHDAY=1",
                "FREQ=DAILY;BYDAY=1MO",
                "FREQ=WEEKLY;BYDAY=-1FR",
                "FREQ=MONTHLY;BYDAY=6MO",
                "FREQ=YEARLY;BYDAY=54MO",
                "FREQ=DAILY;COUNT=2;UNTIL=20260101",
                "FREQ=DAILY;COUNT=0",
                "FREQ=DAILY;INTERVAL=0",
                "FREQ=DAILY;BYSETPOS=1",
                "FREQ=MONTHLY;BYDAY=MO;BYSETPOS=367",
            )
        for (input in invalid) {
            assertFailsWith<IllegalArgumentException>(input) { RecurrenceRule.parse(input) }
            assertNull(RecurrenceRule.parseOrNull(input), input)
        }
        assertFailsWith<IllegalArgumentException> { ByDay(DayOfWeek.MONDAY, 0) }
        assertFailsWith<IllegalArgumentException> { RecurrenceRule(Frequency.WEEKLY, byMonthDay = setOf(1)) }
        assertFailsWith<IllegalArgumentException> { RecurrenceRule.daily().copy(count = 1, until = d("2026-01-01")) }
        assertNotNull(RecurrenceRule.parseOrNull("FREQ=MONTHLY;BYDAY=-1FR;BYMONTHDAY=13"))
    }

    @Test
    fun `round trip holds over a generated corpus`() {
        val random = Random(86)
        var checked = 0
        repeat(2000) {
            val rule = randomRuleOrNull(random) ?: return@repeat
            val text = rule.toRfc5545String()
            assertEquals(rule, RecurrenceRule.parse(text, rule.exDates), text)
            assertEquals(text, RecurrenceRule.parse(text).toRfc5545String(), text)
            assertEquals(text, RecurrenceRule.parse(text.lowercase()).toRfc5545String(), text)
            checked++
        }
        assertTrue(checked > 500, "only $checked valid rules generated")
    }

    private fun Random.subset(
        values: List<Int>,
        max: Int,
    ): Set<Int> = values.shuffled(this).take(nextInt(max + 1)).toSet()

    private fun randomRuleOrNull(random: Random): RecurrenceRule? {
        val days = DayOfWeek.entries.shuffled(random).take(random.nextInt(4))
        val ordinals = listOf(null, null, 1, 2, 5, -1, -5, 20, -53)
        val until = LocalDate(2026 + random.nextInt(5), 1 + random.nextInt(12), 28)
        return try {
            RecurrenceRule(
                frequency = Frequency.entries[random.nextInt(Frequency.entries.size)],
                interval = 1 + random.nextInt(3),
                byDay = days.mapTo(LinkedHashSet()) { ByDay(it, ordinals[random.nextInt(ordinals.size)]) },
                byMonthDay = random.subset(listOf(1, 15, 31, -1, -31), 2),
                byMonth = random.subset((1..12).toList(), 2),
                bySetPos = random.subset(listOf(1, -1, 366), 1),
                count = if (random.nextBoolean()) 1 + random.nextInt(50) else null,
                until = if (random.nextInt(3) == 0) until else null,
                weekStart = DayOfWeek.entries[random.nextInt(7)],
                exDates = if (random.nextBoolean()) setOf(LocalDate(2026, 3, 31)) else emptySet(),
            )
        } catch (ignored: IllegalArgumentException) {
            null
        }
    }

    @Test
    fun `EXDATE lists round trip`() {
        val exDates = setOf(d("2026-04-30"), d("2026-03-31"))
        val rule = RecurrenceRule.monthlyOnLastDay().copy(exDates = exDates)
        assertEquals("EXDATE;VALUE=DATE:20260331,20260430", rule.exDatesToRfc5545String())
        val formatted = checkNotNull(rule.exDatesToRfc5545String())
        assertEquals(exDates, RecurrenceRule.parseExDates(formatted.lowercase()))
        assertEquals(exDates, RecurrenceRule.parseExDates("20260331,20260430"))
        assertEquals(setOf(d("2026-03-31")), RecurrenceRule.parseExDates("EXDATE:20260331T090000Z"))
        assertEquals(emptySet(), RecurrenceRule.parseExDates(""))
        assertNull(RecurrenceRule.monthlyOnLastDay().exDatesToRfc5545String())
        assertFailsWith<IllegalArgumentException> { RecurrenceRule.parseExDates("RDATE:20260331") }
        assertFailsWith<IllegalArgumentException> { RecurrenceRule.parseExDates("2026-03-31") }
        assertEquals(
            rule,
            RecurrenceRule.parse(rule.toRfc5545String(), RecurrenceRule.parseExDates("20260430,20260331")),
        )
    }

    @Test
    fun `equality is set based and copy toString and hashCode are consistent`() {
        val a = RecurrenceRule(Frequency.WEEKLY, byDay = setOf(ByDay(DayOfWeek.MONDAY), ByDay(DayOfWeek.FRIDAY)))
        val b = RecurrenceRule.weekly(DayOfWeek.FRIDAY, DayOfWeek.MONDAY)
        assertEquals(a, b)
        assertEquals(a.hashCode(), b.hashCode())
        assertNotEquals(a, a.copy(interval = 2))
        assertEquals(5, a.copy(count = 5).count)
        assertEquals(a.byDay, a.copy(count = 5).byDay)
        assertTrue(a.toString().contains("frequency=WEEKLY"), a.toString())
        assertEquals(RecurrenceInstance("s", d("2026-01-01")), RecurrenceInstance("s", d("2026-01-01")))
        assertNotEquals(RecurrenceInstance("s", d("2026-01-01")), RecurrenceInstance("t", d("2026-01-01")))
    }

    @Test
    fun `DSL and factories build the expected rules`() {
        val rule =
            recurrenceRule(Frequency.MONTHLY) {
                nth(2, DayOfWeek.MONDAY)
                count = 12
                exDate(LocalDate(2026, 5, 11))
            }
        assertEquals(
            RecurrenceRule(
                frequency = Frequency.MONTHLY,
                byDay = setOf(ByDay(DayOfWeek.MONDAY, 2)),
                count = 12,
                exDates = setOf(LocalDate(2026, 5, 11)),
            ),
            rule,
        )
        val weekly =
            recurrenceRule(Frequency.WEEKLY) {
                byDay(DayOfWeek.TUESDAY, DayOfWeek.THURSDAY)
                interval = 2
                weekStart = DayOfWeek.SUNDAY
                until = LocalDate(2026, 12, 31)
            }
        assertEquals("RRULE:FREQ=WEEKLY;INTERVAL=2;UNTIL=20261231;BYDAY=TU,TH;WKST=SU", weekly.toRfc5545String())
        val yearly =
            recurrenceRule(Frequency.YEARLY) {
                byMonth(11)
                last(DayOfWeek.THURSDAY)
                byMonthDay(24, 25, 26, 27, 28, 29, 30)
                bySetPos(1)
            }
        assertEquals(
            "RRULE:FREQ=YEARLY;BYMONTH=11;BYMONTHDAY=24,25,26,27,28,29,30;BYDAY=-1TH;BYSETPOS=1",
            yearly.toRfc5545String(),
        )
        assertFailsWith<IllegalArgumentException> { recurrenceRule(Frequency.DAILY) { nth(1, DayOfWeek.MONDAY) } }
    }

    @Test
    fun `companion factories format as documented`() {
        assertEquals("RRULE:FREQ=DAILY;INTERVAL=2", RecurrenceRule.daily(2).toRfc5545String())
        assertEquals(
            "RRULE:FREQ=WEEKLY;BYDAY=MO,WE",
            RecurrenceRule.weekly(DayOfWeek.WEDNESDAY, DayOfWeek.MONDAY).toRfc5545String(),
        )
        assertEquals("RRULE:FREQ=WEEKLY", RecurrenceRule.weekly().toRfc5545String())
        assertEquals("RRULE:FREQ=MONTHLY;BYMONTHDAY=15", RecurrenceRule.monthlyOnDay(15).toRfc5545String())
        assertEquals("RRULE:FREQ=MONTHLY;BYMONTHDAY=-1", RecurrenceRule.monthlyOnLastDay().toRfc5545String())
        assertEquals(
            "RRULE:FREQ=MONTHLY;BYDAY=-1FR",
            RecurrenceRule.monthlyOnNth(-1, DayOfWeek.FRIDAY).toRfc5545String(),
        )
        assertEquals("RRULE:FREQ=YEARLY", RecurrenceRule.yearly().toRfc5545String())
        assertEquals(recurrenceRule(Frequency.YEARLY), RecurrenceRule.yearly())
    }
}
