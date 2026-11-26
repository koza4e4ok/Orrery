package me.kozakov.orrery.sample

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.github.takahirom.roborazzi.RoborazziOptions
import com.github.takahirom.roborazzi.captureScreenRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * TEMPORARY: walks the sample app frame by frame on Robolectric and writes one PNG per
 * frame to build/tour-frames, which .github/workflows/demo-gifs.yml turns into the README GIF.
 */
@OptIn(ExperimentalRoborazziApi::class)
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = RobolectricDeviceQualifiers.Pixel5)
class SampleTourRecorder {
    @get:Rule
    val rule = createAndroidComposeRule<MainActivity>()

    private val dir = File(System.getProperty("user.dir"), "build/tour-frames").apply { mkdirs() }
    private var frame = 0
    private val options = RoborazziOptions(recordOptions = RoborazziOptions.RecordOptions(resizeScale = 0.5))
    private val isTab = SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Tab)

    private fun step() {
        rule.mainClock.advanceTimeBy(FRAME_MS)
        captureScreenRoboImage(File(dir, "frame_%05d.png".format(frame++)), options)
    }

    private fun hold(ms: Long) = repeat((ms / FRAME_MS).toInt()) { step() }

    private fun attempt(
        what: String,
        action: () -> Unit,
    ) {
        runCatching(action).onFailure { println("TOUR: $what failed: $it") }
    }

    private fun px(dp: Int): Float = with(rule.density) { dp.dp.toPx() }

    private fun tab(label: String) = attempt("tab $label") { rule.onNode(hasText(label) and isTab).performClick() }

    private fun chip(label: String) =
        attempt("chip $label") {
            val node = rule.onAllNodes(hasText(label) and hasClickAction() and !isTab).onFirst()
            runCatching { node.performScrollTo() }
            node.performClick()
        }

    /** Presses at [start], waits [pressMs] (long press), drags by [dyDp] over [frames] frames, holds [holdMs], lifts. */
    private fun drag(
        start: Offset,
        dyDp: Int,
        frames: Int,
        pressMs: Long = 0,
        holdMs: Long = 0,
    ) = attempt("drag $dyDp at $start") {
        val root = rule.onRoot()
        val dy = px(dyDp) / frames
        root.performTouchInput { down(start) }
        if (pressMs > 0) hold(pressMs)
        repeat(frames) {
            root.performTouchInput { moveBy(Offset(0f, dy)) }
            step()
        }
        if (holdMs > 0) hold(holdMs)
        root.performTouchInput { up() }
    }

    /** Y of the [hour] line of the visible time grid, from its hour label. */
    private fun hourY(hour: Int): Float? {
        val h12 = (hour + 11) % 12 + 1
        val amPm = if (hour < 12) "AM" else "PM"
        val labels = listOf("%02d:00".format(hour), "$h12:00\u202F$amPm", "$h12:00 $amPm")
        for (label in labels) {
            val nodes = rule.onAllNodes(hasText(label), useUnmergedTree = true).fetchSemanticsNodes()
            if (nodes.isNotEmpty()) return nodes.first().boundsInRoot.center.y
        }
        println("TOUR: no hour label for $hour")
        return null
    }

    @Test
    fun recordTour() {
        rule.mainClock.autoAdvance = false
        hold(1000)
        val width = rule.onRoot().fetchSemanticsNode().size.width.toFloat()
        val height = rule.onRoot().fetchSemanticsNode().size.height.toFloat()
        // Agenda: collapse the calendar Month -> TwoWeeks -> Week with direct drags, then expand it.
        var gridY = 0f
        attempt("locate grid") {
            val chipBounds =
                rule
                    .onAllNodes(hasText("Agenda") and hasClickAction() and !isTab)
                    .onFirst()
                    .fetchSemanticsNode()
                    .boundsInRoot
            gridY = chipBounds.bottom + px(90)
        }
        if (gridY > 0f) {
            drag(Offset(width / 2, gridY), -150, 10)
            hold(700)
            drag(Offset(width / 2, gridY), -80, 8)
            hold(700)
            drag(Offset(width / 2, gridY), 280, 12)
            hold(700)
        }
        // Month grid with multi-day event banners and the RRULE series.
        chip("Month")
        hold(1600)
        // Day schedule in Edit mode: drag out a new event, stretch its end, then move it.
        chip("Day")
        hold(600)
        chip("Edit")
        hold(600)
        val y10 = hourY(10)
        val y11 = hourY(11)
        if (y10 != null && y11 != null) {
            val x = width * 0.6f
            val hourPx = y11 - y10
            val hourDp = with(rule.density) { hourPx.toDp().value.toInt() }
            println("TOUR: hour lines at $y10 / $y11 px")
            drag(Offset(x, y10 + px(4)), hourDp, 10, pressMs = 640, holdMs = 400)
            hold(700)
            drag(Offset(x, y11 - px(6)), hourDp * 3 / 4, 8, holdMs = 300)
            hold(700)
            drag(Offset(x, y10 + hourPx * 0.8f), hourDp, 10, pressMs = 640, holdMs = 400)
            hold(900)
        }
        chip("Edit")
        chip("Week")
        hold(1400)
        chip("Agenda")
        // Trips: hospitality check-in / check-out halves.
        tab("Trips")
        hold(800)
        chip("Next 7 days")
        hold(1100)
        chip("Split")
        hold(1100)
        chip("Diagonal")
        // Habits: two-week strip, the Mon-Fri filter, then the heatmaps further down.
        tab("Habits")
        hold(900)
        chip("Mon–Fri")
        hold(1300)
        chip("Mon–Fri")
        hold(300)
        drag(Offset(width / 2, height * 0.8f), -700, 14)
        hold(1500)
        // Almanac and the pickers.
        tab("Almanac")
        hold(1500)
        tab("Pickers")
        hold(700)
        chip("Pick range")
        hold(900)
        chip("Last 7 days")
        hold(1200)
        chip("Save")
        hold(500)
        tab("Agenda")
        hold(500)
        println("TOUR: recorded $frame frames to $dir")
    }

    private companion object {
        const val FRAME_MS = 80L
    }
}
