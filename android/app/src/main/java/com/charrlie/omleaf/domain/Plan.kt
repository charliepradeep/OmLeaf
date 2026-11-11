package com.charrlie.omleaf.domain

import kotlin.math.roundToLong

/** One timed piece of a session, such as "inhale through the left nostril for 4 seconds". */
data class Step(
    val index: Int,
    val round: Int,
    val phase: Phase,
    val startMs: Long,
    val durationMs: Long,
    val nostril: Nostril = Nostril.BOTH,
    val chant: ChantSound = ChantSound.NONE,
    val stroke: Int = 0,
    val strokeTotal: Int = 0,
    /** For meditation steps: ring a bowl as this step begins. */
    val bell: Boolean = false,
) {
    val endMs: Long get() = startMs + durationMs
}

class Plan(
    val steps: List<Step>,
    val rounds: Int,
    /** Non-null for a sound-only meditation. */
    val style: MeditationStyle? = null,
    /** The tone fades out over this long before the end. */
    val fadeOutMs: Long = 0L,
) {
    val totalMs: Long = steps.lastOrNull()?.endMs ?: 0L

    /** The step playing at [ms]; clamped to the first or last step. */
    fun indexAt(ms: Long): Int {
        if (steps.isEmpty()) return 0
        if (ms <= 0) return 0
        if (ms >= totalMs) return steps.lastIndex
        var lo = 0
        var hi = steps.lastIndex
        while (lo < hi) {
            val mid = (lo + hi + 1) / 2
            if (steps[mid].startMs <= ms) lo = mid else hi = mid - 1
        }
        return lo
    }

    /** How far through the current step [ms] is, 0..1. */
    fun progressAt(ms: Long): Double {
        if (steps.isEmpty()) return 0.0
        val s = steps[indexAt(ms)]
        if (s.durationMs <= 0) return 1.0
        return ((ms - s.startMs).toDouble() / s.durationMs).coerceIn(0.0, 1.0)
    }
}

/** Everything the user can change before starting. */
data class PlanRequest(
    val kind: Kind,
    val pattern: BreathPattern,
    val rounds: Int,
    val chant: ChantSound,
    val inhaleSides: List<Nostril> = listOf(Nostril.BOTH),
    val pump: PumpSettings? = null,
    val restSeconds: Double = 0.0,
    val meditation: MeditationSettings? = null,
)

object Planner {
    const val MAX_ROUNDS = 200
    const val CLOSING_MS = 6_000L
    const val CHANT_MS = 12_000L
    const val PAUSE_MS = 4_000L
    const val FADE_OUT_MS = 15_000L

    /** A sound-only meditation: no breaths, just listening. */
    fun meditationRequest(m: MeditationSettings) = PlanRequest(
        kind = Kind.MEDITATION, pattern = BreathPattern(4.0, 0.0, 6.0, 0.0), rounds = 1, chant = ChantSound.NONE, meditation = m,
    )

    fun requestFor(practice: Practice) = PlanRequest(
        kind = practice.kind,
        pattern = practice.pattern,
        rounds = practice.rounds,
        chant = practice.chant,
        inhaleSides = practice.inhaleSides,
        pump = practice.pump,
        restSeconds = practice.restSeconds,
    )

    fun build(request: PlanRequest): Plan {
        if (request.kind == Kind.MEDITATION) return buildMeditation((request.meditation ?: MeditationSettings()).sanitized())
        val rounds = request.rounds.coerceIn(1, MAX_ROUNDS)
        val steps = ArrayList<Step>()
        var t = 0L

        fun add(round: Int, phase: Phase, seconds: Double, nostril: Nostril = Nostril.BOTH, chant: ChantSound = ChantSound.NONE, stroke: Int = 0, total: Int = 0) {
            val ms = (seconds * 1000).roundToLong()
            if (ms <= 0) return
            steps += Step(steps.size, round, phase, t, ms, nostril, chant, stroke, total)
            t += ms
        }

        when (request.kind) {
            Kind.RHYTHM, Kind.ALTERNATE -> {
                val p = request.pattern.clamped()
                val sides = if (request.kind == Kind.ALTERNATE && request.inhaleSides.isNotEmpty()) request.inhaleSides else listOf(Nostril.BOTH)
                for (r in 1..rounds) {
                    for (side in sides) {
                        add(r, Phase.INHALE, p.inhale, side)
                        add(r, Phase.HOLD_IN, p.holdIn)
                        add(r, Phase.EXHALE, p.exhale, side.opposite(), request.chant)
                        add(r, Phase.HOLD_OUT, p.holdOut)
                    }
                    if (r < rounds) add(r, Phase.REST, request.restSeconds.coerceIn(0.0, 300.0))
                }
            }
            Kind.PUMP -> {
                val pump = request.pump ?: PumpSettings(30, 60, 4.0, 8.0, 6.0)
                val n = pump.strokes.coerceIn(5, 200)
                val period = 60.0 / pump.strokesPerMinute.coerceIn(20, 140)
                for (r in 1..rounds) {
                    for (s in 1..n) add(r, Phase.STROKE, period, stroke = s, total = n)
                    add(r, Phase.INHALE, pump.finishInhale.coerceIn(1.0, 30.0))
                    add(r, Phase.HOLD_IN, pump.finishHold.coerceIn(0.0, 60.0))
                    add(r, Phase.EXHALE, pump.finishExhale.coerceIn(1.0, 30.0))
                    if (r < rounds) add(r, Phase.REST, request.restSeconds.coerceIn(0.0, 300.0))
                }
            }
            Kind.MEDITATION -> Unit // handled above
        }
        return Plan(steps, rounds)
    }

    /**
     * Listening steps for the whole time, ending with a short closing step. With interval bells the time is cut into
     * segments, and a segment starts with a bowl. In AUM style a segment is filled with chant, pause, chant, pause...
     */
    private fun buildMeditation(m: MeditationSettings): Plan {
        val totalMs = m.minutes * 60_000L
        val mainMs = totalMs - minOf(CLOSING_MS, totalMs / 2)
        val interval = if (m.bellEveryMinutes in 1 until m.minutes) m.bellEveryMinutes * 60_000L else mainMs
        val steps = ArrayList<Step>()
        var t = 0L

        fun add(ms: Long, chant: ChantSound, bell: Boolean) {
            if (ms <= 0) return
            steps += Step(steps.size, 1, Phase.LISTEN, t, ms, chant = chant, bell = bell)
            t += ms
        }

        var segmentStart = 0L
        var segment = 0
        while (segmentStart < mainMs) {
            val segmentEnd = minOf(segmentStart + interval, mainMs)
            var bell = if (segment == 0) m.bells else true
            if (m.style == MeditationStyle.AUM) {
                while (t < segmentEnd) {
                    add(minOf(CHANT_MS, segmentEnd - t), ChantSound.AUM, bell)
                    bell = false
                    add(minOf(PAUSE_MS, segmentEnd - t), ChantSound.NONE, false)
                }
            } else {
                add(segmentEnd - t, ChantSound.NONE, bell)
            }
            segmentStart = segmentEnd
            segment++
        }
        add(totalMs - t, ChantSound.NONE, m.bells)
        return Plan(steps, 1, m.style, minOf(FADE_OUT_MS, totalMs / 2))
    }
}
