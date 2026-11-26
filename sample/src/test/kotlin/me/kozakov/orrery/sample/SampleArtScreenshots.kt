@file:OptIn(ExperimentalOrreryApi::class)

package me.kozakov.orrery.sample

import android.graphics.Bitmap
import android.os.Looper
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.captureScreenRoboImage
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.YearMonth
import kotlinx.datetime.plus
import kotlinx.datetime.toJavaLocalDate
import me.kozakov.orrery.ExperimentalOrreryApi
import me.kozakov.orrery.compose.currentDate
import me.kozakov.orrery.core.EventSpan
import me.kozakov.orrery.sample.widget.OrreryAgendaWidget
import me.kozakov.orrery.sample.widget.OrreryMonthWidget
import me.kozakov.orrery.widget.renderCalendarSnapshot
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.io.FileOutputStream
import java.time.Duration
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/** TEMPORARY: the README screenshots (art/screenshots), 360x780 dp at 2x = 720x1560 px. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w360dp-h780dp-xhdpi")
class SampleArtScreenshots {
    @get:Rule
    val rule = createAndroidComposeRule<MainActivity>()

    private val dir = File(System.getProperty("user.dir"), "build/art-screens").apply { mkdirs() }
    private val isTab = SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Tab)
    private val today = currentDate()

    // Small clock steps with the Robolectric main looper run for the same time after each one.
    // advanceTimeBy alone leaves the looper paused, so after a tab switch the new screen was
    // never laid out and the next tap found no node ("Failed to perform OnClick action").
    // The frame recorder gets the same effect from capturing a frame after every step.
    // Taps go through the semantics OnClick action; only the drags inject touch input.
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

    private fun drag(
        start: Offset,
        dyDp: Int,
        steps: Int = 10,
        pressMs: Long = 0,
        lift: Boolean = true,
    ) = attempt("drag $dyDp") {
        val root = rule.onRoot()
        val dy = px(dyDp) / steps
        root.performTouchInput { down(start) }
        if (pressMs > 0) settle(pressMs)
        repeat(steps) {
            root.performTouchInput { moveBy(Offset(0f, dy)) }
            settle(32)
        }
        if (lift) {
            root.performTouchInput { up() }
            settle()
        }
    }

    private fun width() =
        rule
            .onRoot()
            .fetchSemanticsNode()
            .size
            .width
            .toFloat()

    private fun height() =
        rule
            .onRoot()
            .fetchSemanticsNode()
            .size
            .height
            .toFloat()

    private fun hourY(hour: Int): Float? {
        val h12 = (hour + 11) % 12 + 1
        val amPm = if (hour < 12) "AM" else "PM"
        for (label in listOf("%02d:00".format(hour), "$h12:00\u202F$amPm", "$h12:00 $amPm")) {
            val nodes =
                rule
                    .onAllNodes(hasText(label), useUnmergedTree = true)
                    .fetchSemanticsNodes()
            val center = nodes.firstOrNull()?.boundsInRoot?.center
            if (center != null) return center.y
        }
        println("SHOT: no hour label for $hour")
        return null
    }

    private fun fullDate(date: LocalDate): String =
        date.toJavaLocalDate().format(DateTimeFormatter.ofLocalizedDate(FormatStyle.FULL))

    private fun printDayAnnouncements(tag: String) {
        val nodes =
            rule
                .onAllNodes(
                    SemanticsMatcher("today") {
                        it.config.getOrNull(SemanticsProperties.StateDescription)?.contains("oday") == true ||
                            it.config.getOrNull(SemanticsProperties.ContentDescription)?.any { d ->
                                d.contains(fullDate(today))
                            } == true
                    },
                ).fetchSemanticsNodes()
        nodes.take(4).forEach {
            val content = it.config.getOrNull(SemanticsProperties.ContentDescription)
            val state = it.config.getOrNull(SemanticsProperties.StateDescription)
            val selected = it.config.getOrNull(SemanticsProperties.Selected)
            println("A11Y $tag: content=$content state=$state selected=$selected")
        }
    }

    private fun collapse(dyDp: Int) {
        val chip = rule.onAllNodes(hasText("Agenda") and hasClickAction() and !isTab).onFirst()
        val y = chip.fetchSemanticsNode().boundsInRoot.bottom + px(90)
        drag(Offset(width() / 2, y), dyDp)
    }

    private fun timedEvents(): Map<LocalDate, Int> {
        val month = YearMonth(today.year, today.month)
        return SampleData.eventsFor(month.firstDay..month.lastDay).mapValues { (_, list) ->
            list.count { it.span is EventSpan.Timed }
        }
    }

    /** The day of this month with the most timed events: overlapping ones show the overlap modes. */
    private fun busiestDay(): LocalDate = timedEvents().maxByOrNull { it.value }?.key ?: today

    /** The Wednesday of the week of this month with the most events: all-day banners and overlaps. */
    private fun busiestWeek(): LocalDate {
        val month = YearMonth(today.year, today.month)
        val counts = SampleData.eventsFor(month.firstDay..month.lastDay).mapValues { it.value.size }
        return counts.keys
            .filter { it.dayOfWeek == DayOfWeek.WEDNESDAY }
            .maxByOrNull { wed -> (-3..3).sumOf { counts[wed.plus(it, DateTimeUnit.DAY)] ?: 0 } } ?: today
    }

    private fun selectInMonth(date: LocalDate) {
        chip("Month")
        attempt("select $date") {
            rule.onNode(hasContentDescription(fullDate(date))).performSemanticsAction(SemanticsActions.OnClick)
            settle()
        }
    }

    @Test
    fun agendaList() {
        settle()
        printDayAnnouncements("agenda")
        shot("agenda_list")
    }

    @Test
    fun agendaMonthBanners() {
        chip("Month")
        printDayAnnouncements("banners")
        shot("agenda_month_banners")
    }

    @Test
    fun agendaTwoWeeks() {
        settle()
        collapse(-150)
        shot("agenda_twoweeks")
    }

    @Test
    fun agendaWeekCollapsed() {
        settle()
        collapse(-150)
        collapse(-80)
        shot("agenda_week_collapsed")
    }

    @Test
    @Config(qualifiers = "+night")
    fun agendaDark() {
        shot("agenda_dark")
    }

    @Test
    fun yearOverview() {
        attempt("year overview") {
            rule.onNode(hasTestTag("hero_year_overview_button")).performSemanticsAction(SemanticsActions.OnClick)
        }
        shot("year_overview")
    }

    @Test
    fun scheduleWeek() {
        selectInMonth(busiestWeek())
        chip("Week")
        shot("schedule_week_side_by_side")
    }

    @Test
    fun scheduleDayStacked() {
        selectInMonth(busiestDay())
        chip("Day")
        chip("Stacked overlap")
        shot("schedule_week_stacked")
    }

    @Test
    fun scheduleEditDrag() {
        chip("Day")
        chip("Edit")
        val y10 = hourY(10)
        val y11 = hourY(11)
        if (y10 == null || y11 == null) return
        val x = width() * 0.6f
        val hourDp = with(rule.density) { (y11 - y10).toDp().value.toInt() }
        // Drag out a new event, then lift it and hold it an hour later: the ghost shows the new time.
        drag(Offset(x, y10 + px(4)), hourDp, pressMs = 640)
        shot("schedule_edit_created")
        drag(Offset(x, y10 + (y11 - y10) * 0.5f), hourDp + hourDp / 2, pressMs = 640, lift = false)
        shot("schedule_edit_drag")
    }

    @Test
    fun rangeDiagonal() {
        tab("Trips")
        chip("Next 7 days")
        shot("range_diagonal")
    }

    @Test
    fun rangeSplit() {
        tab("Trips")
        chip("Next 7 days")
        chip("Split")
        shot("range_split_horizontal")
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

    @Test
    fun habitsHeatmaps() {
        tab("Habits")
        repeat(3) { drag(Offset(width() / 2, height() * 0.8f), -400) }
        shot("year_and_heatmap")
    }

    @Test
    fun almanac() {
        tab("Almanac")
        chip("Meizu Badges")
        shot("style_presets")
    }

    @Test
    fun pickerDate() {
        tab("Pickers")
        chip("Pick date")
        shot("picker_date_modal")
    }

    @Test
    fun pickerRange() {
        tab("Pickers")
        chip("Pick range")
        chip("Last 7 days")
        shot("picker_range_modal")
    }

    @Test
    fun widgets() {
        val context = rule.activity

        fun save(
            image: ImageBitmap,
            name: String,
        ) = FileOutputStream(File(dir, "$name.png")).use {
            image.asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it)
            println("SHOT: $name")
        }
        val month = runBlocking { renderCalendarSnapshot(context, OrreryMonthWidget().spec(today), 600, 600) }
        save(month, "widget_month")
        val agenda = runBlocking { renderCalendarSnapshot(context, OrreryAgendaWidget().spec(today), 720, 360) }
        save(agenda, "widget_agenda")
    }

    private companion object {
        const val STEP_MS = 80L
    }

    @Test
    @Config(fontScale = 2.0f)
    fun font200() {
        settle()
        shot("font200_agenda")
        chip("Day")
        shot("font200_day")
        chip("Month")
        shot("font200_month")
        tab("Trips")
        shot("font200_trips")
        tab("Habits")
        shot("font200_habits")
        tab("Almanac")
        shot("font200_almanac")
        tab("Pickers")
        shot("font200_pickers")
        chip("Pick range")
        shot("font200_picker_range")
    }
}
