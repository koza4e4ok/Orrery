package me.kozakov.orrery.sample

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import me.kozakov.orrery.compose.OrreryDatePickerDialog
import me.kozakov.orrery.compose.OrreryDateRangePickerDialog
import me.kozakov.orrery.compose.rememberOrreryDatePickerState
import me.kozakov.orrery.compose.rememberOrreryDateRangePickerState

/** Dialog gallery: single-date and date-range picker dialogs opened from buttons. */
@Composable
internal fun PickersScreen() {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        SingleDateCard()
        DateRangeCard()
    }
}

@Composable
private fun SingleDateCard() {
    var showDialog by rememberSaveable { mutableStateOf(false) }
    var result by rememberSaveable { mutableStateOf<String?>(null) }

    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Single date", style = MaterialTheme.typography.titleMedium)
            Text(
                result?.let { "Selected: $it" } ?: "No date selected",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Button(onClick = { showDialog = true }) { Text("Pick date") }
        }
    }

    if (showDialog) {
        val state = rememberOrreryDatePickerState()
        OrreryDatePickerDialog(
            onDismissRequest = { showDialog = false },
            confirmButton = {
                Button(
                    onClick = {
                        result = state.selectedDate?.toString()
                        showDialog = false
                    },
                    enabled = state.selectedDate != null,
                ) { Text("OK") }
            },
            dismissButton = { Button(onClick = { showDialog = false }) { Text("Cancel") } },
            state = state,
        )
    }
}

@Composable
private fun DateRangeCard() {
    var showDialog by rememberSaveable { mutableStateOf(false) }
    var result by rememberSaveable { mutableStateOf<String?>(null) }

    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Date range", style = MaterialTheme.typography.titleMedium)
            Text(
                result?.let { "Selected: $it" } ?: "No range selected",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Button(onClick = { showDialog = true }) { Text("Pick range") }
        }
    }

    if (showDialog) {
        val state = rememberOrreryDateRangePickerState()
        OrreryDateRangePickerDialog(
            onDismissRequest = { showDialog = false },
            confirmButton = {
                Button(onClick = {
                    val start = state.selectedStartDate
                    val end = state.selectedEndDate
                    result = if (start != null && end != null) "$start – $end" else null
                    showDialog = false
                }) { Text("Save") }
            },
            state = state,
        )
    }
}
