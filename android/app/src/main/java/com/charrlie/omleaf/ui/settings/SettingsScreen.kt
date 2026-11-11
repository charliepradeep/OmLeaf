@file:OptIn(ExperimentalLayoutApi::class)

package com.charrlie.omleaf.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.charrlie.omleaf.audio.SessionRunner
import com.charrlie.omleaf.data.AppSettings
import com.charrlie.omleaf.domain.BreathPattern
import com.charrlie.omleaf.domain.ChantSound
import com.charrlie.omleaf.domain.FrequencyPresets
import com.charrlie.omleaf.domain.Kind
import com.charrlie.omleaf.domain.PlanRequest
import com.charrlie.omleaf.domain.Planner
import com.charrlie.omleaf.domain.SoundSettings
import com.charrlie.omleaf.ui.AppViewModel
import com.charrlie.omleaf.ui.components.Card
import com.charrlie.omleaf.ui.components.PitchControl
import com.charrlie.omleaf.ui.components.LargeTitle
import com.charrlie.omleaf.ui.components.SectionTitle
import kotlinx.coroutines.delay

@Composable
fun SettingsScreen(vm: AppViewModel) {
    val loaded by vm.settings.collectAsStateWithLifecycle()
    val settings = loaded ?: return
    SettingsContent(vm, settings)
}

@Composable
private fun SettingsContent(vm: AppViewModel, settings: AppSettings) {
    val context = LocalContext.current
    var sound by remember(settings.sound) { mutableStateOf(settings.sound) }
    var confirmClear by remember { mutableStateOf(false) }
    var previewing by remember { mutableStateOf(false) }

    // A short sample: one breath in, then an AUM on the exhale, in the chosen pitch.
    if (previewing) {
        val runner = remember {
            val plan = Planner.build(PlanRequest(Kind.RHYTHM, BreathPattern(1.5, 0.0, 4.0, 0.0), 1, ChantSound.AUM))
            SessionRunner(context, plan, sound).also { it.start() }
        }
        DisposableEffect(Unit) { onDispose { runner.stop() } }
        LaunchedEffect(Unit) { delay(6500); previewing = false }
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 24.dp)) {
        LargeTitle("Settings")

        SectionTitle("Sound")
        Card {
            Column(Modifier.padding(vertical = 8.dp)) {
                Text("Volume", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(horizontal = 16.dp))
                Slider(
                    value = sound.volume,
                    onValueChange = { sound = sound.copy(volume = it) },
                    onValueChangeFinished = { vm.saveSound(sound) },
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
                PitchControl(sound.frequencyHz, onChange = { hz, final -> val s = sound.copy(frequencyHz = hz); if (final) { sound = s; vm.saveSound(s) } else { sound = s } })
                SwitchRow("Phase chimes", sound.chimes) { sound = sound.copy(chimes = it); vm.saveSound(sound) }
                SwitchRow("Guide tone while chanting", sound.guideTone) { sound = sound.copy(guideTone = it); vm.saveSound(sound) }
                SwitchRow("Soft drone", sound.drone) { sound = sound.copy(drone = it); vm.saveSound(sound) }
                OutlinedButton(
                    onClick = { vm.saveSound(sound); previewing = true },
                    enabled = !previewing,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                ) { Text(if (previewing) "Playing…" else "Try this sound") }
                Text(
                    "Pitches are tuning choices to chant along with. They are not medical claims.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                )
            }
        }

        SectionTitle("Feel")
        Card {
            SwitchRow("Vibrate at each change of breath", settings.haptics) { vm.saveHaptics(it) }
        }

        SectionTitle("Data")
        Card {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Your practice history is stored only on this phone.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                OutlinedButton(onClick = { confirmClear = true }, modifier = Modifier.fillMaxWidth()) { Text("Clear practice history") }
            }
        }

        SectionTitle("About")
        Card {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("OmLeaf ${versionName(context)}", style = MaterialTheme.typography.titleMedium)
                Text(
                    "No account, no ads, no analytics and no internet permission. The sounds are made on your phone.",
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    "OmLeaf is a timer and guide, not medical advice. Do not strain. If you are pregnant or have a heart condition, " +
                        "high blood pressure, epilepsy or another medical condition, ask your doctor before practising. Stop if you feel dizzy.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text("Clear history?") },
            text = { Text("This removes your sessions and streak from this phone. It cannot be undone.") },
            confirmButton = { TextButton(onClick = { vm.clearHistory(); confirmClear = false }) { Text("Clear") } },
            dismissButton = { TextButton(onClick = { confirmClear = false }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun SwitchRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

private fun versionName(context: android.content.Context): String =
    try {
        context.packageManager.getPackageInfo(context.packageName, 0).versionName.orEmpty()
    } catch (_: Exception) {
        ""
    }
