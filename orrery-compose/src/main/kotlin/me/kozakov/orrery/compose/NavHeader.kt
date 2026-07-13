package me.kozakov.orrery.compose

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import kotlinx.datetime.YearMonth
import me.kozakov.orrery.core.CalendarPages

/**
 * Month navigation header: previous/next buttons around an animated
 * month/year title. Place it above a [HorizontalCalendar] sharing the
 * same [CalendarState]; buttons disable at the state's month range
 * bounds. When [onTitleClick] is set the title becomes clickable and
 * shows a dropdown affordance — the hook for hosting [MonthYearPicker].
 */
@Composable
public fun CalendarNavHeader(
    state: CalendarState,
    modifier: Modifier = Modifier,
    onTitleClick: (() -> Unit)? = null,
    previousMonthContentDescription: String = "Previous month",
    nextMonthContentDescription: String = "Next month",
    title: @Composable (YearMonth) -> Unit = { month ->
        NavHeaderTitle(month, showDropdown = onTitleClick != null)
    },
) {
    val scope = rememberCoroutineScope()
    val visibleMonth = state.firstVisibleMonth
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        IconButton(
            onClick = {
                scope.launch { state.animateScrollToMonth(CalendarPages.monthAt(state.firstVisibleMonth, -1)) }
            },
            enabled = visibleMonth > state.startMonth,
        ) {
            NavChevron(forward = false, contentDescription = previousMonthContentDescription)
        }
        Box(
            modifier =
                Modifier
                    .weight(1f)
                    .then(
                        if (onTitleClick != null) {
                            Modifier.clip(MaterialTheme.shapes.small).clickable(onClick = onTitleClick)
                        } else {
                            Modifier
                        },
                    ),
            contentAlignment = Alignment.Center,
        ) {
            val isRtl = LocalLayoutDirection.current == LayoutDirection.Rtl
            AnimatedContent(
                targetState = visibleMonth,
                transitionSpec = {
                    val forward = (targetState > initialState) != isRtl
                    val sign = if (forward) 1 else -1
                    (slideInHorizontally { (it / 2) * sign } + fadeIn())
                        .togetherWith(slideOutHorizontally { -(it / 2) * sign } + fadeOut())
                },
                label = "navHeaderTitle",
            ) { month -> title(month) }
        }
        IconButton(
            onClick = {
                scope.launch { state.animateScrollToMonth(CalendarPages.monthAt(state.firstVisibleMonth, 1)) }
            },
            enabled = visibleMonth < state.endMonth,
        ) {
            NavChevron(forward = true, contentDescription = nextMonthContentDescription)
        }
    }
}

@Composable
private fun NavHeaderTitle(
    month: YearMonth,
    showDropdown: Boolean,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
    ) {
        Text(month.displayName(), style = MaterialTheme.typography.titleMedium)
        if (showDropdown) {
            DropdownTriangle()
        }
    }
}

/** Canvas chevron — material3 1.4 no longer bundles material-icons. */
@Composable
private fun NavChevron(
    forward: Boolean,
    contentDescription: String,
) {
    val color = LocalContentColor.current
    val isRtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val pointsRight = forward != isRtl
    Canvas(
        Modifier
            .size(24.dp)
            .semantics { this.contentDescription = contentDescription },
    ) {
        val tipX = if (pointsRight) size.width * 0.6f else size.width * 0.4f
        val baseX = if (pointsRight) size.width * 0.4f else size.width * 0.6f
        val stroke = 2.dp.toPx()
        drawLine(color, Offset(baseX, size.height * 0.3f), Offset(tipX, size.height * 0.5f), stroke, StrokeCap.Round)
        drawLine(color, Offset(tipX, size.height * 0.5f), Offset(baseX, size.height * 0.7f), stroke, StrokeCap.Round)
    }
}

@Composable
private fun DropdownTriangle() {
    val color = LocalContentColor.current
    Canvas(Modifier.padding(start = 4.dp).size(12.dp)) {
        val path =
            Path().apply {
                moveTo(size.width * 0.2f, size.height * 0.35f)
                lineTo(size.width * 0.8f, size.height * 0.35f)
                lineTo(size.width * 0.5f, size.height * 0.7f)
                close()
            }
        drawPath(path, color)
    }
}
