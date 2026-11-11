@file:OptIn(ExperimentalLayoutApi::class)

package com.charrlie.omleaf.ui.setup

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.charrlie.omleaf.data.AppSettings
import com.charrlie.omleaf.data.CustomPractice
import com.charrlie.omleaf.domain.BreathPattern
import com.charrlie.omleaf.domain.Category
import com.charrlie.omleaf.domain.ChantSound
import com.charrlie.omleaf.domain.FrequencyPresets
import com.charrlie.omleaf.domain.Kind
import com.charrlie.omleaf.domain.PlanRequest
import com.charrlie.omleaf.domain.Planner
import com.charrlie.omleaf.domain.Practice
import com.charrlie.omleaf.domain.Practices
import com.charrlie.omleaf.domain.PumpSettings
import com.charrlie.omleaf.domain.SoundSettings
import com.charrlie.omleaf.ui.AppViewModel
import com.charrlie.omleaf.ui.SessionSpec
import com.charrlie.omleaf.ui.components.Card
import com.charrlie.omleaf.ui.components.PitchControl
import com.charrlie.omleaf.ui.components.SectionTitle
import com.charrlie.omleaf.ui.components.Stepper
import com.charrlie.omleaf.ui.components.formatDuration
import com.charrlie.omleaf.ui.components.formatSeconds
import java.util.UUID

private fun blankCustom() = Practice(
    id = "new", name = "My practice", sanskrit = "Your own", category = Category.CUSTOM,
    summary = "", howTo = emptyList(), caution = null, kind = Kind.RHYTHM,
    pattern = BreathPattern(4.0, 0.0, 6.0, 0.0), rounds = 10,
)

@Composable
fun SetupScreen(vm: AppViewModel, practiceId: String, onBegin: () -> Unit, onBack: () -> Unit) {
    val custom by vm.custom.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val practice: Practice? = when (practiceId) {
        "new" -> blankCustom()
        else -> Practices.byId(practiceId) ?: custom.firstOrNull { it.id == practiceId }?.toPractice()
    }
    if (practice == null || settings == null) {
        // Settings or the custom list are still loading, or the practice was deleted.
        return
    }
    SetupContent(vm, practice, settings!!, onBegin, onBack)
}

@Composable
private fun SetupContent(vm: AppViewModel, practice: Practice, settings: AppSettings, onBegin: () -> Unit, onBack: () -> Unit) {
    val isCustom = practice.category == Category.CUSTOM
    var name by remember(practice.id) { mutableStateOf(practice.name) }
    var rounds by remember(practice.id) { mutableStateOf(practice.rounds) }
    var inhale by remember(practice.id) { mutableStateOf(practice.pattern.inhale) }
    var holdIn by remember(practice.id) { mutableStateOf(practice.pattern.holdIn) }
    var exhale by remember(practice.id) { mutableStateOf(practice.pattern.exhale) }
    var holdOut by remember(practice.id) { mutableStateOf(practice.pattern.holdOut) }
    var chant by remember(practice.id) { mutableStateOf(practice.chant) }
    var rest by remember(practice.id) { mutableStateOf(practice.restSeconds) }
    val basePump = practice.pump
    var strokes by remember(practice.id) { mutableStateOf(basePump?.strokes ?: 30) }
    var pace by remember(practice.id) { mutableStateOf(basePump?.strokesPerMinute ?: 60) }
    var finIn by remember(practice.id) { mutableStateOf(basePump?.finishInhale ?: 4.0) }
    var finHold by remember(practice.id) { mutableStateOf(basePump?.finishHold ?: 8.0) }
    var finOut by remember(practice.id) { mutableStateOf(basePump?.finishExhale ?: 6.0) }
    var sound by remember(settings.sound) { mutableStateOf(settings.sound) }

    fun request() = PlanRequest(
        kind = practice.kind,
        pattern = BreathPattern(inhale, holdIn, exhale, holdOut),
        rounds = rounds,
        chant = chant,
        inhaleSides = practice.inhaleSides,
        pump = if (practice.kind == Kind.PUMP) PumpSettings(strokes, pace, finIn, finHold, finOut) else null,
        restSeconds = rest,
    )
    val total = Planner.build(request()).totalMs
    fun updateSound(s: SoundSettings) { sound = s; vm.saveSound(s) }

    Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(bottom = 32.dp)) {
        Row(Modifier.padding(start = 8.dp, top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
        }
        Column(Modifier.padding(horizontal = 20.dp)) {
            if (isCustom) {
                OutlinedTextField(
                    value = name, onValueChange = { name = it.take(40) }, label = { Text("Name") },
                    singleLine = true, modifier = Modifier.fillMaxWidth(),
                )
            } else {
                Text(practice.name, style = MaterialTheme.typography.headlineLarge)
                Text(practice.sanskrit, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(practice.summary, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(top = 8.dp))
            }
        }

        if (practice.howTo.isNotEmpty()) {
            SectionTitle("How to")
            Card {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    practice.howTo.forEachIndexed { i, line -> Text("${i + 1}. $line", style = MaterialTheme.typography.bodyMedium) }
                }
            }
        }
        practice.caution?.let {
            Card {
                Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Icon(Icons.Filled.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.secondary)
                    Text(it, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }

        SectionTitle("Pattern")
        Card {
            Column(Modifier.padding(vertical = 4.dp)) {
                Stepper("Rounds", "$rounds", { rounds = (rounds - 1).coerceAtLeast(1) }, { rounds = (rounds + 1).coerceAtMost(Planner.MAX_ROUNDS) })
                if (practice.kind == Kind.PUMP) {
                    Stepper("Pumps per round", "$strokes", { strokes = (strokes - 5).coerceAtLeast(10) }, { strokes = (strokes + 5).coerceAtMost(100) })
                    Stepper("Pace", "$pace /min", { pace = (pace - 5).coerceAtLeast(20) }, { pace = (pace + 5).coerceAtMost(140) })
                    Stepper("Slow breath in", formatSeconds(finIn), { finIn = (finIn - 0.5).coerceAtLeast(1.0) }, { finIn = finIn + 0.5 })
                    Stepper("Hold", formatSeconds(finHold), { finHold = (finHold - 1).coerceAtLeast(0.0) }, { finHold = finHold + 1 })
                    Stepper("Breath out", formatSeconds(finOut), { finOut = (finOut - 0.5).coerceAtLeast(1.0) }, { finOut = finOut + 0.5 })
                } else {
                    Stepper("Breathe in", formatSeconds(inhale), { inhale = (inhale - 0.5).coerceAtLeast(1.0) }, { inhale = (inhale + 0.5).coerceAtMost(60.0) })
                    Stepper("Hold after in", formatSeconds(holdIn), { holdIn = (holdIn - 0.5).coerceAtLeast(0.0) }, { holdIn = (holdIn + 0.5).coerceAtMost(120.0) })
                    Stepper("Breathe out", formatSeconds(exhale), { exhale = (exhale - 0.5).coerceAtLeast(1.0) }, { exhale = (exhale + 0.5).coerceAtMost(60.0) })
                    Stepper("Hold after out", formatSeconds(holdOut), { holdOut = (holdOut - 0.5).coerceAtLeast(0.0) }, { holdOut = (holdOut + 0.5).coerceAtMost(120.0) })
                }
                Stepper("Rest between rounds", formatSeconds(rest), { rest = (rest - 5).coerceAtLeast(0.0) }, { rest = (rest + 5).coerceAtMost(300.0) })
            }
        }

        if (practice.kind != Kind.PUMP) {
            SectionTitle("Chant on the exhale")
            Card {
                FlowRow(Modifier.padding(12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ChantSound.entries.forEach { c ->
                        FilterChip(selected = chant == c, onClick = { chant = c }, label = { Text(c.label) })
                    }
                }
                if (chant == ChantSound.AUM) {
                    Text(
                        "A and U each take a quarter of the exhale and M takes the other half.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 12.dp),
                    )
                }
            }
        }

        SectionTitle("Sound")
        Card {
            Column(Modifier.padding(vertical = 8.dp)) {
                PitchControl(sound.frequencyHz, onChange = { hz, final -> val s = sound.copy(frequencyHz = hz); if (final) { sound = s; vm.saveSound(s) } else { sound = s } })
                SwitchRow("Phase chimes", sound.chimes) { updateSound(sound.copy(chimes = it)) }
                SwitchRow("Guide tone while chanting", sound.guideTone) { updateSound(sound.copy(guideTone = it)) }
                SwitchRow("Soft drone", sound.drone) { updateSound(sound.copy(drone = it)) }
            }
        }

        Column(Modifier.padding(horizontal = 20.dp, vertical = 20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("About ${formatDuration(total)}", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Button(
                onClick = {
                    vm.pending = SessionSpec(name.ifBlank { practice.name }, request(), sound, settings.haptics)
                    onBegin()
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.Filled.PlayArrow, contentDescription = null)
                Text("Begin", modifier = Modifier.padding(start = 8.dp))
            }
            if (isCustom) {
                OutlinedButton(
                    onClick = {
                        val id = if (practice.id == "new") UUID.randomUUID().toString() else practice.id
                        vm.saveCustom(CustomPractice(id, name.ifBlank { "My practice" }, inhale, holdIn, exhale, holdOut, rounds, chant.name))
                        onBack()
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Save to my practices") }
                if (practice.id != "new") {
                    OutlinedButton(onClick = { vm.deleteCustom(practice.id); onBack() }, modifier = Modifier.fillMaxWidth()) { Text("Delete") }
                }
            }
        }
    }
}

@Composable
private fun SwitchRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}
