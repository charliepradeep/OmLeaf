package com.charrlie.omleaf.domain

import kotlin.math.PI
import kotlin.math.exp
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
    const val MIN_HZ = 80.0
    const val MAX_HZ = 600.0

    val all = listOf(
        FrequencyPreset("OM tuning", 136.1),
        FrequencyPreset("Low drone", 110.0),
        FrequencyPreset("Sa (C4)", 261.6),
        FrequencyPreset("432 Hz", 432.0),
        FrequencyPreset("528 Hz", 528.0),
    )
}

/**
 * Turns a [Plan] into audio, one sample at a time, from the position in the plan.
 * Pure maths with no Android classes, so it can be tested on a computer.
 */
class PlanSynth(private val plan: Plan, settings: SoundSettings, val sampleRate: Int = 22_050) {
    private val s = settings.sanitized()
    private val f = s.frequencyHz
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

            if (s.chimes) {
                var j = idx
                while (j >= 0 && tMs - plan.steps[j].startMs < CHIME_WINDOW_MS) {
                    mix += chime(plan.steps[j], tSec - plan.steps[j].startMs / 1000.0, tSec)
                    j--
                }
            }
            if (s.guideTone && inSession) mix += guide(plan.steps[idx], tMs, tSec)
            if (s.drone && inSession) mix += drone(tSec)

            val v = tanh(mix * (0.6 + s.volume)) * s.volume.coerceAtLeast(0f) * 0.95
            out[i] = (v * Short.MAX_VALUE).toInt().coerceIn(-32767, 32767).toShort()
        }
    }

    private fun chime(step: Step, dt: Double, t: Double): Double {
        if (dt < 0) return 0.0
        val (hz, gain, tau) = when (step.phase) {
            Phase.INHALE -> Triple(f * 2.0, 0.32, 0.45)
            Phase.HOLD_IN -> Triple(f * 1.5, 0.26, 0.40)
            Phase.EXHALE -> Triple(f, 0.32, 0.50)
            Phase.HOLD_OUT -> Triple(f * 0.75, 0.24, 0.40)
            Phase.REST -> Triple(f, 0.20, 0.60)
            Phase.STROKE -> Triple(f * 3.0, 0.30, 0.035)
        }
        val attack = (dt / 0.008).coerceAtMost(1.0)
        val env = attack * exp(-dt / tau)
        if (env < 1e-4) return 0.0
        val w = 2 * PI * hz * t
        return gain * env * (sin(w) + 0.30 * sin(2 * w) + 0.12 * sin(2.76 * w))
    }

    private fun guide(step: Step, tMs: Double, t: Double): Double {
        if (step.phase != Phase.EXHALE || step.chant == ChantSound.NONE || step.durationMs <= 0) return 0.0
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
        const val FADE_FRACTION = 0.04
    }
}
