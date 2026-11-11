@file:OptIn(ExperimentalLayoutApi::class)

package com.charrlie.omleaf.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.charrlie.omleaf.domain.FrequencyPresets
import com.charrlie.omleaf.domain.Pitch
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Pick a pitch exactly: type it, nudge it by 1 Hz, tap a preset, or drag the slider (0 to 1000 Hz).
 * [onChange] is called with `final = false` while a value is still being typed or dragged, and with
 * `final = true` when it has settled and is worth saving.
 */
@Composable
fun PitchControl(hz: Double, onChange: (hz: Double, final: Boolean) -> Unit, modifier: Modifier = Modifier) {
    var text by remember { mutableStateOf(Pitch.format(hz)) }
    var hadFocus by remember { mutableStateOf(false) }
    val focusManager = LocalFocusManager.current

    // Follow changes made with the slider, the presets or the buttons, but never overwrite what is being typed.
    LaunchedEffect(hz) {
        val typed = FrequencyPresets.parse(text)
        if (typed == null || abs(typed - hz) > 0.0005) text = Pitch.format(hz)
    }

    fun commit() {
        val typed = FrequencyPresets.parse(text)
        if (typed != null) { text = Pitch.format(typed); onChange(typed, true) } else text = Pitch.format(hz)
    }

    fun nudge(by: Double) {
        val next = (hz + by).coerceIn(FrequencyPresets.MIN_HZ, FrequencyPresets.MAX_HZ)
        onChange(next, true)
    }

    Column(modifier) {
        Row(Modifier.padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            IconButton(onClick = { nudge(-1.0) }, enabled = hz > FrequencyPresets.MIN_HZ) { Icon(Icons.Filled.Remove, contentDescription = "1 Hz lower") }
            OutlinedTextField(
                value = text,
                onValueChange = { raw ->
                    val cleaned = raw.filter { it.isDigit() || it == '.' || it == ',' }.take(7)
                    text = cleaned
                    FrequencyPresets.parse(cleaned)?.let { onChange(it, false) }
                },
                label = { Text("Pitch in Hz (0 to 1000)") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { commit(); focusManager.clearFocus() }),
                modifier = Modifier.weight(1f).onFocusChanged { state ->
                    if (hadFocus && !state.isFocused) commit()
                    hadFocus = state.isFocused
                },
            )
            IconButton(onClick = { nudge(1.0) }, enabled = hz < FrequencyPresets.MAX_HZ) { Icon(Icons.Filled.Add, contentDescription = "1 Hz higher") }
        }
        val note = Pitch.noteName(hz)
        Text(
            if (note != null) "Nearest note: $note" else "Too low to be a musical note; most phone speakers play very little below 40 Hz.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 2.dp),
        )
        FlowRow(Modifier.padding(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FrequencyPresets.all.forEach { f ->
                FilterChip(
                    selected = abs(hz - f.hz) < 0.05,
                    onClick = { onChange(f.hz, true) },
                    label = { Text("${f.name} · ${Pitch.format(f.hz)}") },
                )
            }
        }
        Slider(
            value = hz.toFloat().coerceIn(FrequencyPresets.MIN_HZ.toFloat(), FrequencyPresets.MAX_HZ.toFloat()),
            onValueChange = { onChange((it * 10).roundToInt() / 10.0, false) },
            onValueChangeFinished = { onChange(hz, true) },
            valueRange = FrequencyPresets.MIN_HZ.toFloat()..FrequencyPresets.MAX_HZ.toFloat(),
            modifier = Modifier.padding(horizontal = 16.dp),
        )
    }
}
