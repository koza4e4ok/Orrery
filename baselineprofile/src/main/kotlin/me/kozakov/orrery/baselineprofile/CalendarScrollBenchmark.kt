package me.kozakov.orrery.baselineprofile

import androidx.benchmark.macro.BaselineProfileMode
import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.FrameTimingMetric
import androidx.benchmark.macro.MacrobenchmarkScope
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Frame timing while scrolling HorizontalCalendar (Almanac tab) and VerticalCalendar
 * (Trips tab), without AOT compilation versus with the shipped baseline profile.
 *
 * Run with `./gradlew :baselineprofile:pixel6Api34BenchmarkReleaseAndroidTest
 * -Pandroid.testInstrumentationRunnerArguments.androidx.benchmark.enabledRules=Macrobenchmark`.
 */
@RunWith(AndroidJUnit4::class)
class CalendarScrollBenchmark {
    @get:Rule
    val rule = MacrobenchmarkRule()

    @Test
    fun horizontalScrollCompilationNone() = horizontalScroll(CompilationMode.None())

    @Test
    fun horizontalScrollBaselineProfile() = horizontalScroll(CompilationMode.Partial(BaselineProfileMode.Require))

    @Test
    fun verticalScrollCompilationNone() = verticalScroll(CompilationMode.None())

    @Test
    fun verticalScrollBaselineProfile() = verticalScroll(CompilationMode.Partial(BaselineProfileMode.Require))

    private fun horizontalScroll(compilationMode: CompilationMode) =
        measureScroll(compilationMode, tab = "Almanac") { scrollHorizontalCalendar() }

    private fun verticalScroll(compilationMode: CompilationMode) =
        measureScroll(compilationMode, tab = "Trips") { scrollVerticalCalendar() }

    private fun measureScroll(
        compilationMode: CompilationMode,
        tab: String,
        scroll: MacrobenchmarkScope.() -> Unit,
    ) {
        rule.measureRepeated(
            packageName = TARGET_PACKAGE,
            metrics = listOf(FrameTimingMetric()),
            compilationMode = compilationMode,
            iterations = ITERATIONS,
            setupBlock = {
                // A fresh process per iteration, so each one measures the first scroll after launch
                // instead of code the JIT already warmed up in earlier iterations.
                killProcess()
                startActivityAndWait()
                openTab(tab)
            },
            measureBlock = scroll,
        )
    }

    private companion object {
        const val ITERATIONS = 10
    }
}
