package me.kozakov.orrery.sample

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.datetime.LocalDate
import kotlinx.datetime.toJavaLocalDate
import me.kozakov.orrery.compose.DayDecorator
import me.kozakov.orrery.compose.DecoratorLayer
import me.kozakov.orrery.core.CalendarDay
import java.time.format.DateTimeFormatter
import java.util.Locale

private val HeroMonthDayFormatter = DateTimeFormatter.ofPattern("MMM d", Locale.ENGLISH)

/**
 * Universal CalendarView-inspired hero header bar displaying the active date in bold
 * display typography, a two-line year + almanac/holiday subtitle, and quick-action
 * buttons for jumping to Today, toggling the 12-month Year Overview, and opening the
 * Style & Dynamic Controls sheet.
 */
@Composable
internal fun SampleHeroHeader(
    date: LocalDate,
    today: LocalDate,
    subtitle: String,
    onTodayClick: () -> Unit,
    modifier: Modifier = Modifier,
    yearOverviewActive: Boolean = false,
    onToggleYearOverview: (() -> Unit)? = null,
    onOpenControls: (() -> Unit)? = null,
    onTitleClick: (() -> Unit)? = null,
    containerColor: Color = MaterialTheme.colorScheme.surface,
    contentColor: Color = MaterialTheme.colorScheme.onSurface,
    accentColor: Color = MaterialTheme.colorScheme.primary,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = containerColor,
        contentColor = contentColor,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                modifier =
                    Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(8.dp))
                        .then(
                            if (onTitleClick != null) {
                                Modifier.clickable(role = Role.Button, onClick = onTitleClick)
                            } else {
                                Modifier
                            },
                        ).padding(vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = date.toJavaLocalDate().format(HeroMonthDayFormatter),
                    style =
                        MaterialTheme.typography.headlineMedium.copy(
                            fontWeight = FontWeight.ExtraBold,
                            letterSpacing = (-0.5).sp,
                        ),
                    color = contentColor,
                    maxLines = 1,
                )
                Spacer(Modifier.width(10.dp))
                Column(verticalArrangement = Arrangement.Center) {
                    Text(
                        text = date.year.toString(),
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                        color = contentColor.copy(alpha = 0.78f),
                        maxLines = 1,
                    )
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.labelSmall,
                        color = accentColor,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TodayBadgeButton(
                    todayDayOfMonth = today.day,
                    onClick = onTodayClick,
                    tint = accentColor,
                )
                if (onToggleYearOverview != null) {
                    IconButton(
                        onClick = onToggleYearOverview,
                        modifier = Modifier.testTag("hero_year_overview_button"),
                        colors =
                            if (yearOverviewActive) {
                                IconButtonDefaults.filledTonalIconButtonColors()
                            } else {
                                IconButtonDefaults.iconButtonColors(contentColor = contentColor)
                            },
                    ) {
                        Icon(
                            imageVector = Icons.Filled.DateRange,
                            contentDescription = "Year overview",
                        )
                    }
                }
                if (onOpenControls != null) {
                    IconButton(
                        onClick = onOpenControls,
                        modifier = Modifier.testTag("hero_controls_button"),
                        colors = IconButtonDefaults.iconButtonColors(contentColor = contentColor),
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Settings,
                            contentDescription = "Style and dynamic controls",
                        )
                    }
                }
            }
        }
    }
}

/**
 * Sleek rounded-square calendar frame badge showing [todayDayOfMonth] inside,
 * inspired by CalendarView's top-right [28] jump-to-today action icon.
 */
@Composable
internal fun TodayBadgeButton(
    todayDayOfMonth: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tint: Color = MaterialTheme.colorScheme.primary,
) {
    Box(
        modifier =
            modifier
                .size(40.dp)
                .clip(RoundedCornerShape(10.dp))
                .clickable(role = Role.Button, onClick = onClick)
                .testTag("hero_today_button")
                .semantics { contentDescription = "Jump to today, day $todayDayOfMonth" },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.size(24.dp)) {
            val strokePx = 1.8.dp.toPx()
            val cornerPx = 5.dp.toPx()
            val topInset = 3.dp.toPx()
            drawRoundRect(
                color = tint,
                topLeft = Offset(0f, topInset),
                size = Size(size.width, size.height - topInset),
                cornerRadius = CornerRadius(cornerPx, cornerPx),
                style = Stroke(width = strokePx),
            )
            // Top binding bar + two calendar rings
            drawLine(
                color = tint,
                start = Offset(2.dp.toPx(), topInset + 4.dp.toPx()),
                end = Offset(size.width - 2.dp.toPx(), topInset + 4.dp.toPx()),
                strokeWidth = 1.4.dp.toPx(),
            )
            drawLine(
                color = tint,
                start = Offset(size.width * 0.3f, 0.5.dp.toPx()),
                end = Offset(size.width * 0.3f, topInset + 2.dp.toPx()),
                strokeWidth = strokePx,
            )
            drawLine(
                color = tint,
                start = Offset(size.width * 0.7f, 0.5.dp.toPx()),
                end = Offset(size.width * 0.7f, topInset + 2.dp.toPx()),
                strokeWidth = strokePx,
            )
        }
        Text(
            text = todayDayOfMonth.toString(),
            modifier = Modifier.padding(top = 5.dp),
            style =
                MaterialTheme.typography.labelSmall.copy(
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 10.sp,
                ),
            color = tint,
        )
    }
}

/**
 * Compact colored corner tag badge (e.g. "H", "W", "N") drawn at
 * the top-end corner of a day cell for the Meizu / Almanac Badges showcase theme.
 */
@Composable
internal fun meizuTagDecorator(tagForDate: (LocalDate) -> Pair<String, Color>?): DayDecorator {
    val textMeasurer = rememberTextMeasurer()
    val baseStyle =
        MaterialTheme.typography.labelSmall.copy(
            fontSize = 8.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White,
        )
    return object : DayDecorator {
        override val layer: DecoratorLayer
            get() = DecoratorLayer.Over

        override fun DrawScope.draw(day: CalendarDay) {
            val (tag, bg) = tagForDate(day.date) ?: return
            val layout = textMeasurer.measure(tag, baseStyle)
            val bubbleHeight = layout.size.height + 2.dp.toPx()
            val bubbleWidth = maxOf(bubbleHeight, layout.size.width + 5.dp.toPx())
            val inset = 2.dp.toPx()
            val left = size.width - bubbleWidth - inset
            drawRoundRect(
                color = bg,
                topLeft = Offset(left, inset),
                size = Size(bubbleWidth, bubbleHeight),
                cornerRadius = CornerRadius(bubbleHeight / 2f, bubbleHeight / 2f),
            )
            drawText(
                textLayoutResult = layout,
                topLeft =
                    Offset(
                        left + (bubbleWidth - layout.size.width) / 2f,
                        inset + (bubbleHeight - layout.size.height) / 2f,
                    ),
            )
        }
    }
}
