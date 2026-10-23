package me.kozakov.orrery.baselineprofile

import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Generates the orrery-compose baseline profile by walking the sample app.
 *
 * Run with `./gradlew :orrery-compose:generateBaselineProfile`; the rules are filtered to
 * library packages and written to orrery-compose/src/main/generated/baselineProfiles.
 */
@RunWith(AndroidJUnit4::class)
class BaselineProfileGenerator {
    @get:Rule
    val rule = BaselineProfileRule()

    @Test
    fun generate() =
        rule.collect(packageName = TARGET_PACKAGE) {
            pressHome()
            startActivityAndWait()
            // Agenda is the start tab; visit every tab so their composables get profiled.
            openTab("Almanac")
            scrollHorizontalCalendar()
            openTab("Trips")
            scrollVerticalCalendar()
            openTab("Habits")
            openTab("Pickers")
            openTab("Agenda")
        }
}
