package me.kozakov.orrery.compose

import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.datetime.LocalDate
import me.kozakov.orrery.core.CalendarEvent
import me.kozakov.orrery.core.EventSpan

internal val LocalAgendaDate = compositionLocalOf<LocalDate?> { null }

/**
 * Localized strings for [Agenda]. [AgendaDefaults.strings] resolves them
 * from the current locale's resources; callers can override any of them.
 */
@Immutable
public class AgendaStrings(
    public val todayPrefix: String,
    public val allDayLabel: String,
    public val noEventsLabel: String,
)

/** Defaults for [Agenda]. */
public object AgendaDefaults {
    /**
     * Builds [AgendaStrings]. Defaults are resolved from the current
     * locale's resources; override any parameter to replace a string.
     */
    @Composable
    @ReadOnlyComposable
    public fun strings(
        todayPrefix: String = orreryString(OrreryString.AgendaTodayPrefix),
        allDayLabel: String = orreryString(OrreryString.AgendaAllDay),
        noEventsLabel: String = orreryString(OrreryString.AgendaNoEvents),
    ): AgendaStrings =
        AgendaStrings(
            todayPrefix = todayPrefix,
            allDayLabel = allDayLabel,
            noEventsLabel = noEventsLabel,
        )

    /** Default day section header in [Agenda]. */
    @Composable
    public fun DayHeader(
        date: LocalDate,
        isToday: Boolean,
        modifier: Modifier = Modifier,
        strings: AgendaStrings = strings(),
    ) {
        val label = date.formatDayHeader()
        Text(
            text =
                if (isToday) {
                    "${strings.todayPrefix} · $label"
                } else {
                    PlatformDateFormat.uppercase(label)
                },
            modifier =
                modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(horizontal = 16.dp, vertical = 12.dp),
            style = MaterialTheme.typography.labelLarge,
            color =
                if (isToday) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
        )
    }

    /**
     * Default event row in [Agenda]: a leading vertical color bar, a time
     * label ("All day" or a start – end time clamped to the rendered day's
     * `00:00..24:00` window), the event title, and an optional subtitle.
     * The time column is at least 104dp and widens to fit its longest label;
     * the title shows up to two lines and the subtitle one, both ellipsized.
     *
     * @param is24Hour Clock for the time label; defaults to the device setting,
     *   like the date-time picker.
     */
    @Composable
    public fun EventRow(
        event: CalendarEvent<*>,
        modifier: Modifier = Modifier,
        onClick: (() -> Unit)? = null,
        strings: AgendaStrings = strings(),
        date: LocalDate? = LocalAgendaDate.current,
        is24Hour: Boolean = systemUses24HourClock(),
    ) {
        val barColor = event.colorArgb?.let(::Color) ?: MaterialTheme.colorScheme.primary
        val timeText = eventTimeLabel(event.span, date, strings, is24Hour)
        val subtitle = event.subtitle
        val barHeight = if (subtitle != null) 44.dp else 36.dp
        val timeStyle = MaterialTheme.typography.labelLarge
        val textMeasurer = rememberTextMeasurer()
        val density = LocalDensity.current
        // Same width on every row so titles line up; at least 104dp, wider when large text needs it.
        val timeColumnWidth =
            remember(textMeasurer, timeStyle, density, strings, is24Hour) {
                val widest =
                    textMeasurer
                        .measure(
                            widestTimeLabel(strings, is24Hour),
                            timeStyle,
                            maxLines = 1,
                        ).size.width
                maxOf(104.dp, with(density) { widest.toDp() })
            }
        val interactionSource = remember { MutableInteractionSource() }
        val isFocused by interactionSource.collectIsFocusedAsState()
        Row(
            modifier =
                modifier
                    .fillMaxWidth()
                    .eventFocusRing(onClick != null && isFocused, RectangleShape, MaterialTheme.colorScheme.onSurface)
                    .then(
                        if (onClick != null) {
                            Modifier.clickable(
                                interactionSource = interactionSource,
                                indication = LocalIndication.current,
                                role = Role.Button,
                                onClick = onClick,
                            )
                        } else {
                            Modifier
                        },
                    ).padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier
                    .width(4.dp)
                    .height(barHeight)
                    .background(barColor, RoundedCornerShape(2.dp)),
            )
            Spacer(Modifier.width(12.dp))
            Text(
                text = timeText,
                modifier = Modifier.width(timeColumnWidth),
                style = timeStyle,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                softWrap = false,
            )
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = event.title,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                if (subtitle != null) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }

    @Deprecated("Kept for binary compatibility; use the overload with is24Hour.", level = DeprecationLevel.HIDDEN)
    @Composable
    public fun EventRow(
        event: CalendarEvent<*>,
        modifier: Modifier = Modifier,
        onClick: (() -> Unit)? = null,
        strings: AgendaStrings = strings(),
        date: LocalDate? = LocalAgendaDate.current,
    ) {
        EventRow(event, modifier, onClick, strings, date, is24Hour = systemUses24HourClock())
    }

    /** Default placeholder for a day without events when `showEmptyDays = true`. */
    @Composable
    public fun EmptyDay(
        modifier: Modifier = Modifier,
        strings: AgendaStrings = strings(),
    ) {
        Text(
            text = strings.noEventsLabel,
            modifier = modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }

    /** Default placeholder when no events exist in the agenda's date range. */
    @Composable
    public fun EmptyRange(
        modifier: Modifier = Modifier,
        strings: AgendaStrings = strings(),
    ) {
        Box(
            modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 24.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = strings.noEventsLabel,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

internal fun LocalDate.formatDayHeader(): String = PlatformDateFormat.dayHeader(this)

internal fun eventTimeLabel(
    span: EventSpan,
    date: LocalDate?,
    strings: AgendaStrings,
    is24Hour: Boolean,
): String =
    when (span) {
        is EventSpan.AllDay -> {
            strings.allDayLabel
        }

        is EventSpan.Timed -> {
            val startsBefore = if (date != null) span.start.date < date else span.start.date < span.end.date
            val startMinute = if (startsBefore) 0 else span.start.time.hour * 60 + span.start.time.minute
            val endMinute =
                if (date != null && span.end.date > date) {
                    MINUTES_PER_DAY
                } else {
                    span.end.time.hour * 60 + span.end.time.minute
                }
            clockLabel(startMinute, is24Hour) + " – " + clockLabel(endMinute, is24Hour)
        }
    }

private const val MINUTES_PER_DAY = 24 * 60

/**
 * A minute of the day on the given clock. 24:00 (an event running past midnight)
 * keeps its 24-hour spelling, and becomes 12:00 AM on a 12-hour clock.
 */
internal fun clockLabel(
    minuteOfDay: Int,
    is24Hour: Boolean,
): String =
    if (is24Hour) {
        (minuteOfDay / 60).toString().padStart(2, '0') + ":" + (minuteOfDay % 60).toString().padStart(2, '0')
    } else {
        PlatformDateFormat.formatTime((minuteOfDay / 60) % 24, minuteOfDay % 60, is24Hour = false)
    }

/** The widest label the time column has to fit: an all-day label or a full-day time range. */
private fun widestTimeLabel(
    strings: AgendaStrings,
    is24Hour: Boolean,
): String =
    listOf(strings.allDayLabel, clockLabel(0, is24Hour) + " – " + clockLabel(MINUTES_PER_DAY, is24Hour))
        .maxBy { it.length }
