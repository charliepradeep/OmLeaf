package com.charrlie.omleaf.ui.history

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.charrlie.omleaf.domain.SessionRecord
import com.charrlie.omleaf.domain.StatsCalculator
import com.charrlie.omleaf.ui.AppViewModel
import com.charrlie.omleaf.ui.components.Card
import com.charrlie.omleaf.ui.components.EmptyNote
import com.charrlie.omleaf.ui.components.LargeTitle
import com.charrlie.omleaf.ui.components.SectionTitle
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun ProgressScreen(vm: AppViewModel) {
    val history by vm.history.collectAsStateWithLifecycle()
    val today = LocalDate.now()
    val stats = StatsCalculator.compute(history, today.toEpochDay())
    val recent = history.sortedByDescending { it.epochDay }.take(20)
    val dayFormat = DateTimeFormatter.ofPattern("d MMM", Locale.getDefault())

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
        item { LargeTitle("Progress", "Small, steady practice adds up") }

        item {
            Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(0.dp)) {
                StatTile("Streak", "${stats.currentStreak}", "days", Modifier.weight(1f))
                StatTile("Best", "${stats.bestStreak}", "days", Modifier.weight(1f))
            }
        }
        item {
            Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp)) {
                StatTile("Sessions", "${stats.sessions}", "total", Modifier.weight(1f))
                StatTile("Time", "${stats.totalMinutes}", "minutes", Modifier.weight(1f))
            }
        }

        item { SectionTitle("Last 7 days") }
        item {
            Card {
                Column(Modifier.padding(16.dp)) {
                    WeekBars(stats.last7Minutes, today)
                }
            }
        }

        item { SectionTitle("Recent sessions") }
        if (recent.isEmpty()) {
            item { EmptyNote("Nothing yet. Finish a practice and it will appear here.") }
        } else {
            items(recent.size) { i -> SessionRow(recent[i], dayFormat) }
        }
    }
}

@Composable
private fun StatTile(label: String, value: String, unit: String, modifier: Modifier) {
    Card(modifier) {
        Column(Modifier.padding(16.dp)) {
            Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(verticalAlignment = Alignment.Bottom) {
                Text(value, style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.primary)
                Text(unit, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = 6.dp, bottom = 4.dp))
            }
        }
    }
}

@Composable
private fun WeekBars(minutes: List<Int>, today: LocalDate) {
    val max = (minutes.maxOrNull() ?: 0).coerceAtLeast(1)
    val bar = MaterialTheme.colorScheme.primary
    val empty = MaterialTheme.colorScheme.surfaceVariant
    Canvas(Modifier.fillMaxWidth().height(96.dp)) {
        val gap = 10.dp.toPx()
        val w = (size.width - gap * 6) / 7
        minutes.forEachIndexed { i, m ->
            val h = if (m == 0) 6.dp.toPx() else (size.height * m / max).coerceAtLeast(10.dp.toPx())
            drawRoundRect(
                color = if (m == 0) empty else bar,
                topLeft = Offset(i * (w + gap), size.height - h),
                size = Size(w, h),
                cornerRadius = CornerRadius(6.dp.toPx()),
            )
        }
    }
    Row(Modifier.fillMaxWidth().padding(top = 6.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        for (back in 6 downTo 0) {
            Text(
                today.minusDays(back.toLong()).dayOfWeek.getDisplayName(java.time.format.TextStyle.NARROW, Locale.getDefault()),
                style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun SessionRow(r: SessionRecord, fmt: DateTimeFormatter) {
    Card {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(r.practice, style = MaterialTheme.typography.titleMedium)
                Text(LocalDate.ofEpochDay(r.epochDay).format(fmt), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text("${(r.seconds / 60).coerceAtLeast(1)} min", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
        }
    }
}
