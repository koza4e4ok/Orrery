package me.kozakov.orrery.compose

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
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
     * label ("All day" or `HH:mm – HH:mm` clamped to the rendered day's
     * `00:00..24:00` window), the event title, and an optional subtitle.
     */
    @Composable
    public fun EventRow(
        event: CalendarEvent<*>,
        modifier: Modifier = Modifier,
        onClick: (() -> Unit)? = null,
        strings: AgendaStrings = strings(),
        date: LocalDate? = LocalAgendaDate.current,
    ) {
        val barColor = event.colorArgb?.let(::Color) ?: MaterialTheme.colorScheme.primary
        val timeText = eventTimeLabel(event.span, date, strings)
        val subtitle = event.subtitle
        val barHeight = if (subtitle != null) 44.dp else 36.dp
        Row(
            modifier =
                modifier
                    .fillMaxWidth()
                    .then(
                        if (onClick != null) {
                            Modifier.clickable(role = Role.Button, onClick = onClick)
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
                modifier = Modifier.width(104.dp),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = event.title,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                if (subtitle != null) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
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
): String =
    when (span) {
        is EventSpan.AllDay -> strings.allDayLabel
        is EventSpan.Timed -> {
            val startLabel =
                if (date != null) {
                    if (span.start.date < date) "00:00" else span.start.time.formatHhMm()
                } else {
                    if (span.start.date < span.end.date) "00:00" else span.start.time.formatHhMm()
                }
            val endLabel =
                if (date != null && span.end.date > date) {
                    "24:00"
                } else {
                    span.end.time.formatHhMm()
                }
            "$startLabel – $endLabel"
        }
    }

private fun LocalTime.formatHhMm(): String = hour.toString().padStart(2, '0') + ":" + minute.toString().padStart(2, '0')
