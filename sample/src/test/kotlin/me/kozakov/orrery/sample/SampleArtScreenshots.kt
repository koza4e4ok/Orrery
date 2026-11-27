@file:OptIn(ExperimentalOrreryApi::class)

package me.kozakov.orrery.sample

import android.os.Looper
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.captureScreenRoboImage
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.YearMonth
import kotlinx.datetime.plus
import kotlinx.datetime.toJavaLocalDate
import me.kozakov.orrery.ExperimentalOrreryApi
import me.kozakov.orrery.compose.currentDate
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.time.Duration
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/** TEMPORARY: re-records four README screenshots (art/screenshots), 360x780 dp at 2x. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w360dp-h780dp-xhdpi")
class SampleArtScreenshots {
    @get:Rule
    val rule = createAndroidComposeRule<MainActivity>()

    private val dir = File(System.getProperty("user.dir"), "build/art-screens").apply { mkdirs() }
    private val isTab = SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Tab)
    private val today = currentDate()

    private fun settle(ms: Long = 1600) {
        rule.mainClock.autoAdvance = false
        repeat((ms / STEP_MS).toInt().coerceAtLeast(1)) {
            rule.mainClock.advanceTimeBy(STEP_MS)
            Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(STEP_MS))
        }
    }

    @Before
    fun pauseClock() = settle(1000)

    private fun shot(name: String) {
        settle()
        captureScreenRoboImage(File(dir, "$name.png"))
        println("SHOT: $name")
    }

    private fun attempt(
        what: String,
        action: () -> Unit,
    ) {
        runCatching(action).onFailure { println("SHOT: $what failed: $it") }
    }

    private fun px(dp: Int): Float = with(rule.density) { dp.dp.toPx() }

    private fun tab(label: String) =
        attempt("tab $label") {
            rule.onNode(hasText(label) and isTab).performSemanticsAction(SemanticsActions.OnClick)
            settle()
        }

    private fun chip(label: String) =
        attempt("chip $label") {
            val node = rule.onAllNodes(hasText(label) and hasClickAction() and !isTab).onFirst()
            runCatching { node.performScrollTo() }
            node.performSemanticsAction(SemanticsActions.OnClick)
            settle()
        }

    private fun bounds(matcher: SemanticsMatcher): Rect =
        rule
            .onAllNodes(matcher)
            .onFirst()
            .fetchSemanticsNode()
            .boundsInRoot

    /** Scrolls [scroller] so the top of [target] sits [marginDp] below the scroller's top. */
    private fun scrollTopTo(
        scroller: SemanticsMatcher,
        target: SemanticsMatcher,
        marginDp: Int,
    ) = attempt("scroll") {
        val dy = bounds(target).top - bounds(scroller).top - px(marginDp)
        println("SHOT: scroll by $dy")
        rule.onAllNodes(scroller).onFirst().performSemanticsAction(SemanticsActions.ScrollBy) { it(0f, dy) }
        settle()
    }

    private fun fullDate(date: LocalDate): String =
        date.toJavaLocalDate().format(DateTimeFormatter.ofLocalizedDate(FormatStyle.FULL))

    /** The Wednesday of this month with the most events on it and its neighbours. */
    private fun busiestWednesday(): LocalDate {
        val month = YearMonth(today.year, today.month)
        val counts = SampleData.eventsFor(month.firstDay..month.lastDay).mapValues { it.value.size }
        return counts.keys
            .filter { it.dayOfWeek == DayOfWeek.WEDNESDAY }
            .maxByOrNull { wed -> (-1..1).sumOf { counts[wed.plus(it, DateTimeUnit.DAY)] ?: 0 } } ?: today
    }

    private fun selectInMonth(date: LocalDate) {
        chip("Month")
        attempt("select $date") {
            rule.onNode(hasContentDescription(fullDate(date))).performSemanticsAction(SemanticsActions.OnClick)
            settle()
        }
    }

    private fun showStay() {
        tab("Trips")
        chip("Next 7 days")
        val calendar = hasTestTag("trips_calendar")
        scrollTopTo(
            scroller = hasScrollAction() and (calendar or hasAnyAncestor(calendar)),
            target = hasContentDescription(fullDate(today), substring = true),
            marginDp = 64,
        )
    }

    @Test
    fun scheduleWeek() {
        selectInMonth(busiestWednesday())
        chip("Week")
        shot("schedule_week_side_by_side")
    }

    @Test
    fun rangeDiagonal() {
        showStay()
        shot("range_diagonal")
    }

    @Test
    fun rangeSplit() {
        tab("Trips")
        chip("Split")
        showStay()
        shot("range_split_horizontal")
    }

    @Test
    fun habitsHeatmaps() {
        tab("Habits")
        scrollTopTo(
            scroller = SemanticsMatcher.keyIsDefined(SemanticsProperties.VerticalScrollAxisRange),
            target = hasText("Year in review"),
            marginDp = 28,
        )
        shot("year_and_heatmap")
    }

    @Test
    fun habitsMonth() {
        tab("Habits")
        shot("habits_progress_month")
    }

    @Test
    fun habitsWeekdays() {
        tab("Habits")
        chip("Run")
        chip("Mon–Fri")
        shot("habits_weekdays_only")
    }

    private companion object {
        const val STEP_MS = 80L
    }
}
