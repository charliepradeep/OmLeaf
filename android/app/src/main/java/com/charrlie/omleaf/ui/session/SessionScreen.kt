package com.charrlie.omleaf.ui.session

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import android.os.VibrationEffect
import android.os.Vibrator
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.charrlie.omleaf.audio.SessionRunner
import com.charrlie.omleaf.audio.SessionService
import com.charrlie.omleaf.domain.ChantSound
import com.charrlie.omleaf.domain.Nostril
import com.charrlie.omleaf.domain.Phase
import com.charrlie.omleaf.domain.Planner
import com.charrlie.omleaf.domain.SessionRecord
import com.charrlie.omleaf.domain.Step
import com.charrlie.omleaf.ui.AppViewModel
import com.charrlie.omleaf.ui.SessionSpec
import com.charrlie.omleaf.ui.components.formatDuration
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import java.time.LocalDate
import kotlin.math.PI
import kotlin.math.ceil
import kotlin.math.sin

private const val MIN_SAVED_SECONDS = 30

@Composable
fun SessionScreen(vm: AppViewModel, spec: SessionSpec, onClose: () -> Unit) {
    val context = LocalContext.current
    val view = LocalView.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val plan = remember(spec) { Planner.build(spec.request) }
    val runner = remember(spec) { SessionRunner(context, plan, spec.sound) }

    var countdown by remember(spec) { mutableIntStateOf(3) }
    var started by remember(spec) { mutableStateOf(false) }
    var paused by remember(spec) { mutableStateOf(false) }
    var finished by remember(spec) { mutableStateOf(false) }
    var elapsed by remember(spec) { mutableLongStateOf(0L) }
    var confirmEnd by remember(spec) { mutableStateOf(false) }
    var saved by remember(spec) { mutableStateOf(false) }

    val step: Step = plan.steps[plan.indexAt(elapsed)]
    val progress = plan.progressAt(elapsed)

    fun saveIfWorthIt(seconds: Int, rounds: Int) {
        if (saved || seconds < MIN_SAVED_SECONDS) return
        saved = true
        vm.addSession(SessionRecord(LocalDate.now().toEpochDay(), spec.title, seconds, rounds))
    }

    // Keep the screen awake, stop the audio when the screen is left.
    DisposableEffect(spec) {
        view.keepScreenOn = true
        onDispose {
            view.keepScreenOn = false
            runner.stop()
            SessionService.stop(context)
        }
    }

    // Pause when the app goes to the background; the person resumes with the button.
    DisposableEffect(lifecycle, spec) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP && started && !finished && !spec.keepPlaying) {
                runner.pause()
                paused = true
            }
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }

    // Android 13+ asks once so the "playing" notification can show; sound works either way.
    val askNotifications = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    LaunchedEffect(spec) {
        if (spec.keepPlaying && Build.VERSION.SDK_INT >= 33 &&
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) askNotifications.launch(Manifest.permission.POST_NOTIFICATIONS)
    }

    // Three quiet seconds to get ready, then the audio starts.
    LaunchedEffect(spec) {
        while (countdown > 0) { delay(1000); countdown-- }
        started = true
        runner.start()
        if (spec.keepPlaying) SessionService.start(context, spec.title)
    }

    // The frame loop stops with the screen, so poll the clock for the end while locked.
    LaunchedEffect(started, spec) {
        if (!started) return@LaunchedEffect
        while (isActive && !finished) {
            delay(500)
            if (runner.elapsedMs() >= plan.totalMs) {
                elapsed = runner.elapsedMs()
                finished = true
                saveIfWorthIt((plan.totalMs / 1000).toInt(), plan.rounds)
            }
        }
    }

    // One frame loop drives the animation from the audio clock.
    LaunchedEffect(started) {
        if (!started) return@LaunchedEffect
        while (isActive) {
            androidx.compose.runtime.withFrameNanos { }
            elapsed = runner.elapsedMs()
            paused = runner.paused
            if (elapsed >= plan.totalMs) {
                finished = true
                saveIfWorthIt((plan.totalMs / 1000).toInt(), plan.rounds)
                break
            }
        }
    }

    // Let the last chime ring, then release the audio.
    LaunchedEffect(finished) {
        if (finished) { delay(2500); runner.stop(); SessionService.stop(context) }
    }

    // A short tap at every change of phase.
    LaunchedEffect(step.index, started) {
        // A meditation is silent to the touch except for the bowls.
        val meditating = plan.style != null
        if (started && !finished && spec.haptics && step.phase != Phase.STROKE && (!meditating || step.bell)) vibrate(context)
    }

    Box(Modifier.fillMaxSize().safeDrawingPadding()) {
        when {
            finished -> Finished(plan.totalMs, plan.rounds, plan.style != null, onClose)
            !started -> Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Get comfortable", style = MaterialTheme.typography.titleLarge)
                Text("$countdown", fontSize = 88.sp, fontWeight = FontWeight.Light, color = MaterialTheme.colorScheme.primary)
                Text("Sit tall, relax the shoulders, close your eyes if you like.", color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center, modifier = Modifier.padding(horizontal = 32.dp))
            }
            else -> Running(
                title = spec.title, plan = plan, step = step, progress = progress, elapsed = elapsed, paused = paused,
                onPauseResume = { if (runner.paused) { runner.resume(); paused = false } else { runner.pause(); paused = true } },
                onStop = { confirmEnd = true },
            )
        }
    }

    if (confirmEnd) {
        AlertDialog(
            onDismissRequest = { confirmEnd = false },
            title = { Text("End this session?") },
            text = { Text(if (elapsed / 1000 >= MIN_SAVED_SECONDS) "Your practice so far will be saved to Progress." else "Sessions shorter than 30 seconds are not saved.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmEnd = false
                    saveIfWorthIt((elapsed / 1000).toInt(), step.round)
                    onClose()
                }) { Text("End") }
            },
            dismissButton = { TextButton(onClick = { confirmEnd = false }) { Text("Keep going") } },
        )
    }
}

@Composable
private fun Running(
    title: String,
    plan: com.charrlie.omleaf.domain.Plan,
    step: Step,
    progress: Double,
    elapsed: Long,
    paused: Boolean,
    onPauseResume: () -> Unit,
    onStop: () -> Unit,
) {
    val meditation = plan.style != null
    // In a meditation the circle only glows very slowly; it must not suggest a breathing pace.
    val scale = if (meditation) 0.8f + 0.03f * sin(elapsed / 1000.0 * 2 * PI / 20.0).toFloat() else circleScale(step, progress)
    val primary = MaterialTheme.colorScheme.primary
    val color = when (step.phase) {
        Phase.INHALE -> primary
        Phase.HOLD_IN, Phase.HOLD_OUT -> MaterialTheme.colorScheme.tertiary
        Phase.EXHALE, Phase.STROKE -> MaterialTheme.colorScheme.secondary
        Phase.REST -> MaterialTheme.colorScheme.onSurfaceVariant
        Phase.LISTEN -> primary
    }
    val ring = MaterialTheme.colorScheme.outlineVariant
    val remainingSeconds = ceil((step.endMs - elapsed) / 1000.0).toInt().coerceAtLeast(0)
    val next = plan.steps.getOrNull(step.index + 1)

    Column(Modifier.fillMaxSize().padding(horizontal = 24.dp, vertical = 16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        Text(
            if (meditation) plan.style?.label.orEmpty() else "Round ${step.round} of ${plan.rounds}",
            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
            Canvas(Modifier.size(300.dp)) {
                val c = Offset(size.width / 2, size.height / 2)
                val full = size.minDimension / 2
                drawCircle(ring, radius = full - 2.dp.toPx(), center = c, style = Stroke(width = 2.dp.toPx()))
                drawCircle(color.copy(alpha = 0.16f), radius = full * scale * 1.0f, center = c)
                drawCircle(color.copy(alpha = 0.40f), radius = full * scale * 0.80f, center = c)
                drawCircle(color, radius = full * scale * 0.55f, center = c, alpha = 0.9f)
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    when {
                        meditation -> formatDuration((plan.totalMs - elapsed).coerceAtLeast(0))
                        step.phase == Phase.STROKE -> "${step.stroke}/${step.strokeTotal}"
                        else -> "$remainingSeconds"
                    },
                    fontSize = if (meditation) 44.sp else 56.sp, fontWeight = FontWeight.Light, color = Color.White,
                )
            }
        }

        Text(step.phase.label, style = MaterialTheme.typography.headlineMedium)
        if (step.nostril != Nostril.BOTH) {
            Text(step.nostril.label, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
        }
        if ((step.phase == Phase.EXHALE || step.phase == Phase.LISTEN) && step.chant != ChantSound.NONE) ChantLetters(step.chant, progress)
        if (!meditation) Text(
            next?.let { "Next: ${it.phase.label}" + (if (it.nostril != Nostril.BOTH) " · ${it.nostril.label}" else "") } ?: "Last breath",
            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 6.dp),
        )

        Column(Modifier.padding(top = 16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            LinearProgressIndicator(progress = { (elapsed.toFloat() / plan.totalMs).coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth())
            Text("${formatDuration((plan.totalMs - elapsed).coerceAtLeast(0))} left", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 6.dp))
            Row(Modifier.padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                FilledTonalIconButton(onClick = onStop, modifier = Modifier.size(56.dp)) { Icon(Icons.Filled.Stop, contentDescription = "End session") }
                FilledTonalIconButton(onClick = onPauseResume, modifier = Modifier.size(56.dp)) {
                    Icon(if (paused) Icons.Filled.PlayArrow else Icons.Filled.Pause, contentDescription = if (paused) "Resume" else "Pause")
                }
            }
        }
    }
}

/** A, U, M shown as letters; the one being chanted now is large and bright. */
@Composable
private fun ChantLetters(chant: ChantSound, progress: Double) {
    val current = chant.partAt(progress)
    Row(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
        chant.parts.forEachIndexed { i, (label, _) ->
            Text(
                label,
                fontSize = if (i == current) 40.sp else 26.sp,
                fontWeight = if (i == current) FontWeight.Bold else FontWeight.Normal,
                color = if (i == current) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun Finished(totalMs: Long, rounds: Int, meditation: Boolean, onClose: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(32.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
        Text("Well done", style = MaterialTheme.typography.headlineLarge)
        Text(
            if (meditation) "Sound meditation · ${formatDuration(totalMs)}" else "$rounds rounds · ${formatDuration(totalMs)}",
            style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 8.dp),
        )
        Text(
            if (meditation) "Stay with the quiet for a moment before you get up." else "Breathe normally and sit quietly for a moment before you get up.",
            textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(vertical = 24.dp),
        )
        Button(onClick = onClose) { Text("Done") }
    }
}

/** 0.45 when the lungs are empty, 1.0 when full, easing smoothly between. */
internal fun circleScale(step: Step, p: Double): Float {
    fun ease(x: Double) = x * x * (3 - 2 * x)
    val v = when (step.phase) {
        Phase.INHALE -> 0.45 + 0.55 * ease(p)
        Phase.HOLD_IN -> 1.0
        Phase.EXHALE -> 1.0 - 0.55 * ease(p)
        Phase.HOLD_OUT -> 0.45
        Phase.STROKE -> 0.55 + 0.35 * (1 - p)
        Phase.REST -> 0.7
        Phase.LISTEN -> 0.8
    }
    return v.toFloat()
}

@Suppress("DEPRECATION")
private fun vibrate(context: Context) {
    val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator ?: return
    if (vibrator.hasVibrator()) vibrator.vibrate(VibrationEffect.createOneShot(30, VibrationEffect.DEFAULT_AMPLITUDE))
}
