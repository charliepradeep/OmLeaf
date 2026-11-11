package com.charrlie.omleaf.domain

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.tanh

data class SoundSettings(
    /** Base pitch for chimes, the chant guide tone and the drone. */
    val frequencyHz: Double = 136.1,
    val chimes: Boolean = true,
    val guideTone: Boolean = true,
    val drone: Boolean = false,
    val volume: Float = 0.7f,
) {
    fun sanitized() = copy(frequencyHz = frequencyHz.coerceIn(FrequencyPresets.MIN_HZ, FrequencyPresets.MAX_HZ), volume = volume.coerceIn(0f, 1f))
}

data class FrequencyPreset(val name: String, val hz: Double)

/** Popular pitches for chanting. They are tuning choices, not health claims. */
object FrequencyPresets {
    /** 0 is allowed so any value can be typed. Below about 40 Hz a phone speaker plays almost nothing. */
    const val MIN_HZ = 0.0
    /** The top of the usual chanting and "solfeggio" range. */
    const val MAX_HZ = 1000.0

    /** Reads a typed pitch such as "136.1" or "136,1". Returns null if it is not a number. */
    fun parse(text: String): Double? = text.trim().replace(',', '.').toDoubleOrNull()?.takeIf { it.isFinite() }?.coerceIn(MIN_HZ, MAX_HZ)

    val all = listOf(
        FrequencyPreset("OM tuning", 136.1),
        FrequencyPreset("Low drone", 110.0),
        FrequencyPreset("Sa (C4)", 261.6),
        FrequencyPreset("432 Hz", 432.0),
        FrequencyPreset("528 Hz", 528.0),
        FrequencyPreset("963 Hz", 963.0),
    )
}

/** Names the nearest musical note for a pitch, which helps when tuning an instrument or your voice. */
object Pitch {
    private val names = listOf("C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B")

    /** For example 440.0 gives "A4" and 136.1 gives "C#3 -33¢". Null below 16 Hz, where there is no musical note. */
    fun noteName(hz: Double): String? {
        if (hz < 16.0) return null
        val midi = 12 * ln(hz / 440.0) / ln(2.0) + 69
        val nearest = midi.roundToInt()
        val cents = ((midi - nearest) * 100).roundToInt()
        val name = names[((nearest % 12) + 12) % 12] + (Math.floorDiv(nearest, 12) - 1)
        return when {
            cents == 0 -> name
            cents > 0 -> "$name +$cents¢"
            else -> "$name -${abs(cents)}¢"
        }
    }

    /** "136.1" for a pitch; whole numbers have no decimal. */
    fun format(hz: Double): String = if (hz == hz.toLong().toDouble()) hz.toLong().toString() else "%.1f".format(java.util.Locale.US, hz)
}

/**
 * Turns a [Plan] into audio, one sample at a time, from the position in the plan.
 * Pure maths with no Android classes, so it can be tested on a computer.
 */
class PlanSynth(private val plan: Plan, settings: SoundSettings, val sampleRate: Int = 22_050) {
    private val s = settings.sanitized()
    private val f = s.frequencyHz
    private val med = plan.style
    private val chimeWindowMs = if (med != null) BELL_WINDOW_MS else CHIME_WINDOW_MS
    val totalFrames: Long = plan.totalMs * sampleRate / 1000
    /** Silence after the last step so the final chime can ring out. */
    val tailFrames: Long = sampleRate * 2L

    /** Fills [out] with [count] mono 16-bit samples starting at [startFrame]. */
    fun render(startFrame: Long, out: ShortArray, count: Int) {
        if (plan.steps.isEmpty()) { out.fill(0, 0, count); return }
        var idx = plan.indexAt(startFrame * 1000 / sampleRate)
        for (i in 0 until count) {
            val frame = startFrame + i
            val tSec = frame.toDouble() / sampleRate
            val tMs = tSec * 1000.0
            while (idx < plan.steps.lastIndex && plan.steps[idx + 1].startMs <= tMs) idx++
            val inSession = frame < totalFrames
            var mix = 0.0

            // A meditation brings its own sounds and ignores the breath-guide switches.
            if (s.chimes || med != null) {
                var j = idx
                while (j >= 0 && tMs - plan.steps[j].startMs < chimeWindowMs) {
                    mix += chime(plan.steps[j], tSec - plan.steps[j].startMs / 1000.0, tSec)
                    j--
                }
            }
            if (inSession) {
                val fade = fadeAt(tMs)
                if (s.guideTone || med != null) mix += guide(plan.steps[idx], tMs, tSec) * fade
                if (med == MeditationStyle.DRONE) mix += pad(tSec) * fade else if (med == null && s.drone) mix += drone(tSec)
            }

            val v = tanh(mix * (0.6 + s.volume)) * s.volume.coerceAtLeast(0f) * 0.95
            out[i] = (v * Short.MAX_VALUE).toInt().coerceIn(-32767, 32767).toShort()
        }
    }

    /** Fades in over three seconds and out over the plan's fade time (meditation only). */
    private fun fadeAt(tMs: Double): Double {
        if (med == null) return 1.0
        val fadeIn = (tMs / 3000.0).coerceIn(0.0, 1.0)
        val fadeOut = if (plan.fadeOutMs > 0) ((plan.totalMs - tMs) / plan.fadeOutMs).coerceIn(0.0, 1.0) else 1.0
        return minOf(fadeIn, fadeOut)
    }

    /** A singing-bowl strike: a fundamental with two inharmonic partials and a slow beat. */
    private fun bowl(dt: Double, t: Double): Double {
        val attack = (dt / 0.012).coerceAtMost(1.0)
        val hz = f * 2.0
        var v = sin(2 * PI * hz * t) * exp(-dt / 3.2) + 0.6 * sin(2 * PI * hz * 1.004 * t) * exp(-dt / 3.6)
        if (hz * 2.76 < MAX_PARTIAL_HZ) v += 0.35 * sin(2 * PI * hz * 2.76 * t) * exp(-dt / 1.8)
        if (hz * 5.4 < MAX_PARTIAL_HZ) v += 0.15 * sin(2 * PI * hz * 5.4 * t) * exp(-dt / 0.9)
        return 0.26 * attack * v
    }

    /** A fuller, slowly swelling drone for the steady-tone meditation. */
    private fun pad(t: Double): Double {
        val swell = 0.8 + 0.2 * sin(2 * PI * 0.07 * t)
        val w = 2 * PI * f * t
        return 0.17 * swell * (sin(w) + 0.5 * sin(1.5 * w) + 0.35 * sin(2 * w) + 0.15 * sin(3 * w)) / 2.0
    }

    private fun chime(step: Step, dt: Double, t: Double): Double {
        if (dt < 0) return 0.0
        if (step.phase == Phase.LISTEN) return if (step.bell && dt < BELL_WINDOW_MS / 1000.0) bowl(dt, t) else 0.0
        val (hz, gain, tau) = when (step.phase) {
            Phase.INHALE -> Triple(f * 2.0, 0.32, 0.45)
            Phase.HOLD_IN -> Triple(f * 1.5, 0.26, 0.40)
            Phase.EXHALE -> Triple(f, 0.32, 0.50)
            Phase.HOLD_OUT -> Triple(f * 0.75, 0.24, 0.40)
            Phase.REST -> Triple(f, 0.20, 0.60)
            Phase.STROKE -> Triple(f * 3.0, 0.30, 0.035)
            Phase.LISTEN -> Triple(f, 0.0, 0.1) // handled above
        }
        val attack = (dt / 0.008).coerceAtMost(1.0)
        val env = attack * exp(-dt / tau)
        if (env < 1e-4) return 0.0
        val w = 2 * PI * hz * t
        return gain * env * (sin(w) + 0.30 * sin(2 * w) + 0.12 * sin(2.76 * w))
    }

    private fun guide(step: Step, tMs: Double, t: Double): Double {
        if ((step.phase != Phase.EXHALE && step.phase != Phase.LISTEN) || step.chant == ChantSound.NONE || step.durationMs <= 0) return 0.0
        val local = ((tMs - step.startMs) / step.durationMs).coerceIn(0.0, 1.0)
        val durSec = step.durationMs / 1000.0
        val localSec = local * durSec
        val env = (localSec / 0.30).coerceAtMost(1.0) * ((durSec - localSec) / 0.40).coerceIn(0.0, 1.0)
        if (env <= 0) return 0.0
        val (h2, h3) = brightness(step.chant, local)
        val w = 2 * PI * f * t
        var v = (sin(w) + h2 * sin(2 * w) + h3 * sin(3 * w)) / (1.0 + h2 + h3)
        if (step.chant == ChantSound.HUM) v *= 1.0 + 0.08 * sin(2 * PI * 5.5 * t)
        return 0.30 * env * v
    }

    /** Open vowels sound brighter than humming, so the guide tone itself shows A to U to M. */
    private fun brightness(chant: ChantSound, local: Double): Pair<Double, Double> {
        fun of(label: String) = when (label) {
            "A" -> 0.55 to 0.30
            "U" -> 0.12 to 0.03
            else -> 0.04 to 0.0
        }
        val parts = chant.parts
        if (parts.isEmpty()) return 0.0 to 0.0
        val i = chant.partAt(local)
        val cur = of(parts[i].first)
        // Blend over a short window after each boundary so the change is smooth, not a click.
        var start = 0.0
        for (k in 0 until i) start += parts[k].second
        val into = local - start
        if (i > 0 && into < FADE_FRACTION) {
            val prev = of(parts[i - 1].first)
            val a = into / FADE_FRACTION
            return (prev.first + (cur.first - prev.first) * a) to (prev.second + (cur.second - prev.second) * a)
        }
        return cur
    }

    private fun drone(t: Double): Double {
        val trem = 0.85 + 0.15 * sin(2 * PI * 0.2 * t)
        val w = 2 * PI * f * t
        return 0.07 * trem * (sin(w) + 0.5 * sin(1.5 * w) + 0.3 * sin(2 * w)) / 1.8
    }

    private companion object {
        const val CHIME_WINDOW_MS = 1800.0
        const val BELL_WINDOW_MS = 9000.0
        const val MAX_PARTIAL_HZ = 10_000.0
        const val FADE_FRACTION = 0.04
    }
}
