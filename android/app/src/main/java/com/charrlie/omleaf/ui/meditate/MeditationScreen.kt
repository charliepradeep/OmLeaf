@file:OptIn(ExperimentalLayoutApi::class)

package com.charrlie.omleaf.ui.meditate

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.charrlie.omleaf.domain.MeditationSettings
import com.charrlie.omleaf.domain.MeditationStyle
import com.charrlie.omleaf.domain.Planner
import com.charrlie.omleaf.domain.SoundSettings
import com.charrlie.omleaf.ui.AppViewModel
import com.charrlie.omleaf.ui.SessionSpec
import com.charrlie.omleaf.ui.components.Card
import com.charrlie.omleaf.ui.components.PitchControl
import com.charrlie.omleaf.ui.components.SectionTitle

private val durations = listOf(5, 10, 15, 20, 30, 45, 60)
private val intervals = listOf(0, 1, 2, 3, 5, 10, 15)

/** Sound only: pick how long, what to hear and which pitch. There are no breaths to follow. */
@Composable
fun MeditationScreen(vm: AppViewModel, onBegin: () -> Unit, onBack: () -> Unit) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    val loaded = settings ?: return
    var med by remember { mutableStateOf(loaded.meditation) }
    var sound by remember { mutableStateOf(loaded.sound) }
    var minutesText by remember { mutableStateOf(med.minutes.toString()) }

    fun update(m: MeditationSettings) { med = m; vm.saveMeditation(m) }

    // A typed time, kept in step with the chips.
    LaunchedEffect(med.minutes) {
        if (minutesText.toIntOrNull() != med.minutes) minutesText = med.minutes.toString()
    }

    Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(bottom = 32.dp)) {
        Row(Modifier.padding(start = 8.dp, top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
        }
        Column(Modifier.padding(horizontal = 20.dp)) {
            Text("Sound meditation", style = MaterialTheme.typography.headlineLarge)
            Text(
                "Just sit and listen. There are no breaths to follow.",
                style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        SectionTitle("How long")
        Card {
            Column(Modifier.padding(vertical = 8.dp)) {
                FlowRow(Modifier.padding(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    durations.forEach { m ->
                        FilterChip(selected = med.minutes == m, onClick = { update(med.copy(minutes = m)) }, label = { Text("$m min") })
                    }
                }
                OutlinedTextField(
                    value = minutesText,
                    onValueChange = { raw ->
                        val digits = raw.filter { it.isDigit() }.take(3)
                        minutesText = digits
                        digits.toIntOrNull()?.let { update(med.copy(minutes = it.coerceIn(MeditationSettings.MIN_MINUTES, MeditationSettings.MAX_MINUTES))) }
                    },
                    label = { Text("Minutes (1 to 180)") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }
        }

        SectionTitle("What you hear")
        Card {
            Column(Modifier.padding(vertical = 4.dp)) {
                MeditationStyle.entries.forEach { style ->
                    Row(
                        Modifier.fillMaxWidth().clickable { update(med.copy(style = style)) }.padding(horizontal = 8.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = med.style == style, onClick = { update(med.copy(style = style)) })
                        Column(Modifier.padding(start = 4.dp)) {
                            Text(style.label, style = MaterialTheme.typography.titleMedium)
                            Text(style.blurb, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }

        SectionTitle("Bowls")
        Card {
            Column(Modifier.padding(vertical = 8.dp)) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("Opening and closing bowl", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                    Switch(checked = med.bells, onCheckedChange = { update(med.copy(bells = it)) })
                }
                Text(
                    "Ring a bowl every",
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(start = 16.dp, top = 8.dp, bottom = 4.dp),
                )
                FlowRow(Modifier.padding(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    intervals.forEach { m ->
                        FilterChip(
                            selected = med.bellEveryMinutes == m,
                            onClick = { update(med.copy(bellEveryMinutes = m)) },
                            label = { Text(if (m == 0) "Never" else "$m min") },
                        )
                    }
                }
            }
        }

        SectionTitle("Pitch")
        Card {
            Column(Modifier.padding(vertical = 8.dp)) {
                PitchControl(sound.frequencyHz, onChange = { hz, final ->
                    sound = sound.copy(frequencyHz = hz)
                    if (final) vm.saveSound(sound)
                })
            }
        }

        Column(Modifier.padding(horizontal = 20.dp, vertical = 20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
                "${med.minutes} min · fades out gently at the end",
                style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Button(
                onClick = {
                    vm.pending = SessionSpec("Sound meditation", Planner.meditationRequest(med), sound, loaded.haptics, loaded.keepPlaying)
                    onBegin()
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.Filled.PlayArrow, contentDescription = null)
                Text("Begin", modifier = Modifier.padding(start = 8.dp))
            }
        }
    }
}
