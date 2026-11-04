package com.charrlie.omleaf.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.charrlie.omleaf.domain.Kind
import com.charrlie.omleaf.domain.Plan
import com.charrlie.omleaf.domain.Planner
import com.charrlie.omleaf.domain.Practice
import com.charrlie.omleaf.domain.Practices
import com.charrlie.omleaf.domain.StatsCalculator
import com.charrlie.omleaf.ui.AppViewModel
import com.charrlie.omleaf.ui.components.Card
import com.charrlie.omleaf.ui.components.LargeTitle
import com.charrlie.omleaf.ui.components.Pill
import com.charrlie.omleaf.ui.components.SectionTitle
import com.charrlie.omleaf.ui.components.formatDuration
import java.time.LocalDate

@Composable
fun PracticesScreen(vm: AppViewModel, onOpen: (String) -> Unit) {
    val custom by vm.custom.collectAsStateWithLifecycle()
    val history by vm.history.collectAsStateWithLifecycle()
    val stats = StatsCalculator.compute(history, LocalDate.now().toEpochDay())

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
        item { LargeTitle("OmLeaf", "Breath, sound and stillness") }

        item {
            Card {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            if (stats.currentStreak > 0) "${stats.currentStreak}-day streak" else "Start your streak today",
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Text(
                            "${stats.last7Minutes.last()} min today · ${stats.last7Minutes.sum()} min this week",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }

        Practices.grouped().forEach { (category, list) ->
            item(key = "h-${category.name}") { SectionTitle(category.title) }
            list.forEach { p -> item(key = p.id) { PracticeCard(p, onClick = { onOpen(p.id) }) } }
        }

        item(key = "h-custom") { SectionTitle("Your own") }
        custom.forEach { c -> item(key = "c-${c.id}") { PracticeCard(c.toPractice(), onClick = { onOpen(c.id) }) } }
        item(key = "new") {
            Card(onClick = { onOpen("new") }) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Icon(Icons.Filled.Add, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Text("Create your own pattern", style = MaterialTheme.typography.titleMedium)
                }
            }
        }
    }
}

@Composable
private fun PracticeCard(p: Practice, onClick: () -> Unit) {
    val plan: Plan = Planner.build(Planner.requestFor(p))
    Card(onClick = onClick) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(p.name, style = MaterialTheme.typography.titleMedium)
            Text(p.sanskrit, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(p.summary, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Pill(badge(p))
                Pill("${p.rounds} rounds · ${formatDuration(plan.totalMs)}")
            }
        }
    }
}

fun badge(p: Practice): String =
    if (p.kind == Kind.PUMP && p.pump != null) "${p.pump.strokes} pumps"
    else {
        val b = p.pattern
        listOf(b.inhale, b.holdIn, b.exhale, b.holdOut).joinToString("·") { if (it % 1.0 == 0.0) it.toInt().toString() else it.toString() }
    }
