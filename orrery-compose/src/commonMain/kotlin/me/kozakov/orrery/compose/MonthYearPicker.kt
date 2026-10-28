package me.kozakov.orrery.compose

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.datetime.Month
import kotlinx.datetime.YearMonth

/**
 * Month/year jump picker: a year grid drilling into a 3x4 month grid;
 * months outside [range] are disabled. Tapping the year grid's title
 * zooms out to a decade grid for fast far jumps. Selecting a month
 * fires [onSelect] — the host decides what to do (typically scroll a
 * calendar and dismiss). Host it in a dialog, dropdown, or swap it with
 * the calendar in-place. For calendars with unbounded state, pass a
 * finite window here — e.g. a hundred years around [currentYearMonth].
 */
@Composable
public fun MonthYearPicker(
    current: YearMonth,
    range: ClosedRange<YearMonth>,
    onSelect: (YearMonth) -> Unit,
    modifier: Modifier = Modifier,
) {
    MonthYearPickerContent(
        current = current,
        range = range,
        onSelect = onSelect,
        modifier = modifier,
    )
}

/**
 * Body of [MonthYearPicker], shared with [OrreryMonthPickerDialog]. With
 * [startInMonths] it opens on the month grid of [selected] (or [current])
 * clamped to [range]. With [selectable] the [selected] month is filled with
 * the primary color, [current] gets the "today" outline and months expose
 * selection semantics; otherwise it renders exactly like [MonthYearPicker].
 *
 * Escape / Back while a descendant has focus drills up one level — from a
 * year's month grid (unless the picker started there) or from the decade
 * grid back to the year grid — before reaching the host, so a dialog
 * dismisses only from the top level.
 */
@Composable
internal fun MonthYearPickerContent(
    current: YearMonth,
    range: ClosedRange<YearMonth>,
    onSelect: (YearMonth) -> Unit,
    modifier: Modifier = Modifier,
    selected: YearMonth? = null,
    startInMonths: Boolean = false,
    selectable: Boolean = false,
) {
    val startYear = initialPickerYear(selected, current, range)
    var pickedYear by rememberSaveable { mutableStateOf(if (startInMonths) startYear else null) }
    var showingDecades by rememberSaveable { mutableStateOf(false) }
    var focusYear by rememberSaveable { mutableStateOf(if (startInMonths) startYear else current.year) }
    // Set on a consumed Escape/Back key-down so its key-up is swallowed too
    // (the host dialog dismisses on the key-up it tracked from the key-down).
    val drillUpPressed = remember { mutableStateOf(false) }
    // The focused button leaves with the drilled-in grid: the year grid then
    // takes focus on its title so the next Escape still reaches this picker.
    val focusTitleOnEnter = remember { mutableStateOf(false) }
    AnimatedContent(
        targetState = pickedYear,
        modifier =
            modifier.onKeyEvent { event ->
                if (event.key != Key.Escape && event.key != Key.Back) return@onKeyEvent false
                when (event.type) {
                    KeyEventType.KeyDown -> {
                        val inDrilledMonths = pickedYear != null && !startInMonths
                        val canDrillUp = inDrilledMonths || (pickedYear == null && showingDecades)
                        drillUpPressed.value = canDrillUp
                        canDrillUp
                    }

                    KeyEventType.KeyUp -> {
                        if (drillUpPressed.value) {
                            drillUpPressed.value = false
                            if (pickedYear != null) pickedYear = null else showingDecades = false
                            focusTitleOnEnter.value = true
                            true
                        } else {
                            false
                        }
                    }

                    else -> {
                        false
                    }
                }
            },
        transitionSpec = { (fadeIn() + scaleIn(initialScale = 0.92f)).togetherWith(fadeOut()) },
        label = "monthYearPicker",
    ) { year ->
        if (year == null) {
            AnimatedContent(
                targetState = showingDecades,
                transitionSpec = { (fadeIn() + scaleIn(initialScale = 0.92f)).togetherWith(fadeOut()) },
                label = "yearDecade",
            ) { decades ->
                if (decades) {
                    DecadeGrid(
                        currentYear = current.year,
                        range = range,
                        onDecadeClick = { decadeStart ->
                            focusYear =
                                decadeStart.coerceIn(range.start.year, range.endInclusive.year)
                            showingDecades = false
                        },
                    )
                } else {
                    YearGrid(
                        currentYear = current.year,
                        focusYear = focusYear,
                        range = range,
                        onTitleClick = { showingDecades = true },
                        onYearClick = { pickedYear = it },
                        focusTitleOnEnter = focusTitleOnEnter,
                    )
                }
            }
        } else {
            MonthGrid(
                year = year,
                current = current,
                range = range,
                selected = selected,
                selectable = selectable,
                onBack = { pickedYear = null },
                onMonthClick = { month -> onSelect(YearMonth(year, month)) },
            )
        }
    }
}

/** Year the picker opens on: [selected]'s, else [current]'s, clamped to [range]. */
internal fun initialPickerYear(
    selected: YearMonth?,
    current: YearMonth,
    range: ClosedRange<YearMonth>,
): Int = (selected ?: current).year.coerceIn(range.start.year, range.endInclusive.year)

@Composable
private fun YearGrid(
    currentYear: Int,
    focusYear: Int,
    range: ClosedRange<YearMonth>,
    onTitleClick: () -> Unit,
    onYearClick: (Int) -> Unit,
    focusTitleOnEnter: MutableState<Boolean>,
) {
    val titleFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        if (focusTitleOnEnter.value) {
            focusTitleOnEnter.value = false
            withFrameNanos { }
            // Throws if the title is not attached yet; focus then simply stays unset.
            runCatching { titleFocus.requestFocus() }
        }
    }
    val years = (range.start.year..range.endInclusive.year).toList()
    val initialIndex =
        (focusYear - range.start.year).coerceIn(0, years.lastIndex).let { it - it % 3 }
    Column {
        TextButton(onClick = onTitleClick, modifier = Modifier.focusRequester(titleFocus)) {
            Text(
                "${range.start.year} – ${range.endInclusive.year}",
                style = MaterialTheme.typography.titleMedium,
            )
        }
        LazyVerticalGrid(
            columns = GridCells.Fixed(3),
            state = rememberLazyGridState(initialFirstVisibleItemIndex = initialIndex),
        ) {
            items(years.size) { index ->
                val year = years[index]
                TextButton(onClick = { onYearClick(year) }) {
                    Text(
                        year.toString(),
                        color = if (year == currentYear) MaterialTheme.colorScheme.primary else Color.Unspecified,
                        fontWeight = if (year == currentYear) FontWeight.Bold else null,
                    )
                }
            }
        }
    }
}

@Composable
private fun DecadeGrid(
    currentYear: Int,
    range: ClosedRange<YearMonth>,
    onDecadeClick: (Int) -> Unit,
) {
    val decades = (range.start.year / 10..range.endInclusive.year / 10).map { it * 10 }
    val decadeLabel = orreryString(OrreryString.MonthYearPickerDecade)
    LazyVerticalGrid(columns = GridCells.Fixed(3), modifier = Modifier.fillMaxWidth()) {
        items(decades.size) { index ->
            val decade = decades[index]
            TextButton(onClick = { onDecadeClick(decade) }) {
                Text(
                    decadeLabel.formatTemplate(decade),
                    fontWeight = if (currentYear in decade until decade + 10) FontWeight.Bold else null,
                )
            }
        }
    }
}

@Composable
private fun MonthGrid(
    year: Int,
    current: YearMonth,
    range: ClosedRange<YearMonth>,
    selected: YearMonth?,
    selectable: Boolean,
    onBack: () -> Unit,
    onMonthClick: (Month) -> Unit,
) {
    Column {
        TextButton(onClick = onBack) {
            Text(year.toString(), style = MaterialTheme.typography.titleMedium)
        }
        LazyVerticalGrid(columns = GridCells.Fixed(3), modifier = Modifier.fillMaxWidth()) {
            items(12) { index ->
                val month = Month(index + 1)
                val yearMonth = YearMonth(year, month)
                val isSelected = selectable && yearMonth == selected
                TextButton(
                    onClick = { onMonthClick(month) },
                    enabled = yearMonth in range,
                    modifier = if (selectable) Modifier.semantics { this.selected = isSelected } else Modifier,
                    colors =
                        if (isSelected) {
                            ButtonDefaults.textButtonColors(
                                containerColor = MaterialTheme.colorScheme.primary,
                                contentColor = MaterialTheme.colorScheme.onPrimary,
                            )
                        } else {
                            ButtonDefaults.textButtonColors()
                        },
                    border =
                        if (selectable && !isSelected && yearMonth == current) {
                            BorderStroke(1.dp, MaterialTheme.colorScheme.primary)
                        } else {
                            null
                        },
                ) {
                    Text(
                        PlatformDateFormat.monthName(month, short = false),
                        fontWeight = if (yearMonth == current) FontWeight.Bold else null,
                    )
                }
            }
        }
    }
}
