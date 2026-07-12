package dev.koza4e4ok.material.calendar.sample

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import kotlinx.datetime.plus

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(
                colorScheme = if (isSystemInDarkTheme()) darkColorScheme() else lightColorScheme(),
            ) {
                var screen by remember { mutableStateOf(0) }
                Scaffold { padding ->
                    Column(Modifier.fillMaxSize().padding(padding)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                            TextButton(onClick = { screen = 0 }) { Text("Agenda") }
                            TextButton(onClick = { screen = 1 }) { Text("Trips") }
                            TextButton(onClick = { screen = 2 }) { Text("Habits") }
                            TextButton(onClick = { screen = 3 }) { Text("Almanac") }
                        }
                        when (screen) {
                            0 -> AgendaScreen()
                            1 -> TripsScreen()
                            2 -> HabitsScreen()
                            else -> AlmanacScreen()
                        }
                    }
                }
            }
        }
    }
}
