package me.kozakov.orrery.sample

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
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

    private fun tab(label: String) = attempt("tab $label") { rule.onNode(hasText(label) and isTab).performClick() }

    private fun chip(label: String) =
        attempt("chip $label") {
            rule.onAllNodes(hasText(label) and hasClickAction() and !isTab).onFirst().performClick()
        }

    /** Drags vertically from [startY] (px) by [dyDp] over [frames] recorded frames. */
    private fun drag(
        startY: Float,
        dyDp: Int,
        frames: Int,
    ) = attempt("drag $dyDp") {
        val root = rule.onRoot()
        val x = root.fetchSemanticsNode().size.width / 2f
        val dy = with(rule.density) { dyDp.dp.toPx() } / frames
        root.performTouchInput { down(Offset(x, startY)) }
        repeat(frames) {
            root.performTouchInput { moveBy(Offset(0f, dy)) }
            step()
        }
        root.performTouchInput { up() }
    }

    @Test
    fun recordTour() {
        rule.mainClock.autoAdvance = false
        hold(1500)
        // Agenda: collapse the calendar Month -> TwoWeeks -> Week with direct drags, then expand it.
        var gridY = 0f
        attempt("locate grid") {
            val chipBounds =
                rule
                    .onAllNodes(hasText("Agenda") and hasClickAction() and !isTab)
                    .onFirst()
                    .fetchSemanticsNode()
                    .boundsInRoot
            gridY = chipBounds.bottom + with(rule.density) { 90.dp.toPx() }
        }
        if (gridY > 0f) {
            drag(gridY, -150, 10)
            hold(1000)
            drag(gridY, -80, 8)
            hold(1000)
            drag(gridY, 280, 12)
            hold(1000)
        }
        // Month grid with multi-day event banners, then the working-hours Week schedule.
        chip("Month")
        hold(2400)
        chip("Week")
        hold(1500)
        chip("Stacked overlap")
        hold(1800)
        chip("Agenda")
        // Trips: hospitality check-in / check-out halves.
        tab("Trips")
        hold(1200)
        chip("Weekend")
        hold(1400)
        chip("Next 7 days")
        hold(1400)
        chip("Split")
        hold(1400)
        chip("Diagonal")
        hold(800)
        // Habits: two-week strip and the Mon-Fri filter.
        tab("Habits")
        hold(1400)
        chip("Mon–Fri")
        hold(1800)
        chip("Mon–Fri")
        // Almanac and the pickers.
        tab("Almanac")
        hold(2200)
        tab("Pickers")
        hold(1000)
        chip("Pick range")
        hold(2000)
        chip("Save")
        hold(800)
        tab("Agenda")
        hold(800)
        println("TOUR: recorded $frame frames to $dir")
    }

    private companion object {
        const val FRAME_MS = 80L
    }
}
