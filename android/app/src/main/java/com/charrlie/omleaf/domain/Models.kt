package com.charrlie.omleaf.domain

/** What the breath is doing during one step of a session. */
enum class Phase(val label: String) {
    INHALE("Inhale"),
    HOLD_IN("Hold"),
    EXHALE("Exhale"),
    HOLD_OUT("Hold"),
    STROKE("Pump"),
    REST("Breathe normally"),
    /** Sound-only meditation: nothing to do but listen. */
    LISTEN("Listen"),
}

/** Which nostril is open for a step (the other is closed). BOTH for ordinary breathing and for holds. */
enum class Nostril(val label: String) {
    BOTH(""),
    LEFT("Left nostril"),
    RIGHT("Right nostril");

    fun opposite(): Nostril = when (this) {
        LEFT -> RIGHT
        RIGHT -> LEFT
        BOTH -> BOTH
    }
}

/**
 * A sound to chant on the exhale. AUM is split into A, U and M in the traditional 1:1:2 proportion,
 * so the final M (humming) gets half of the exhale.
 */
enum class ChantSound(val label: String, val parts: List<Pair<String, Double>>) {
    NONE("No chant", emptyList()),
    A("A (ahh)", listOf("A" to 1.0)),
    U("U (ooo)", listOf("U" to 1.0)),
    M("M (hum)", listOf("M" to 1.0)),
    AUM("AUM / OM", listOf("A" to 0.25, "U" to 0.25, "M" to 0.5)),
    HUM("Humming bee", listOf("M" to 1.0));

    /** Index of the part being chanted at [fraction] (0..1) of the exhale, or -1 if there is no chant. */
    fun partAt(fraction: Double): Int {
        if (parts.isEmpty()) return -1
        var acc = 0.0
        for ((i, p) in parts.withIndex()) {
            acc += p.second
            if (fraction < acc) return i
        }
        return parts.lastIndex
    }
}

/** Seconds for each phase of one breath. A zero means the phase is skipped. */
data class BreathPattern(val inhale: Double, val holdIn: Double, val exhale: Double, val holdOut: Double) {
    val cycleSeconds: Double get() = inhale + holdIn + exhale + holdOut

    fun clamped() = BreathPattern(
        inhale = inhale.coerceIn(1.0, 60.0),
        holdIn = holdIn.coerceIn(0.0, 120.0),
        exhale = exhale.coerceIn(1.0, 60.0),
        holdOut = holdOut.coerceIn(0.0, 120.0),
    )
}

/**
 * Settings for rapid "pumping" practices (Kapalabhati, Bhastrika): [strokes] quick breaths at
 * [strokesPerMinute], then one slow finishing breath.
 */
data class PumpSettings(
    val strokes: Int,
    val strokesPerMinute: Int,
    val finishInhale: Double,
    val finishHold: Double,
    val finishExhale: Double,
)

enum class Category(val title: String) {
    CALMING("Calming"),
    BALANCING("Balancing"),
    ENERGIZING("Energizing"),
    COOLING("Cooling"),
    CHANTING("Chanting"),
    CUSTOM("Your own"),
}

/** How a practice turns into steps. */
enum class Kind { RHYTHM, ALTERNATE, PUMP, MEDITATION }

/** What the sound-only meditation plays. */
enum class MeditationStyle(val label: String, val blurb: String) {
    DRONE("Steady tone", "A soft, continuous tone to rest in."),
    BOWL("Singing bowl", "Quiet, with a bowl struck at the start, the end and at the intervals you choose."),
    AUM("AUM chanting", "A sung AUM again and again, with a short pause between."),
}

/** Settings for the sound-only meditation. There are no breaths to follow. */
data class MeditationSettings(
    val minutes: Int = 10,
    val style: MeditationStyle = MeditationStyle.DRONE,
    /** Ring a bowl every this many minutes. 0 means no interval bells. */
    val bellEveryMinutes: Int = 0,
    /** A bowl at the very start and a closing bowl at the end. */
    val bells: Boolean = true,
    /** Sleep mode: no bowls, a very slow fade over the last minutes, then everything stops by itself. */
    val sleep: Boolean = false,
) {
    fun sanitized() = copy(minutes = minutes.coerceIn(MIN_MINUTES, MAX_MINUTES), bellEveryMinutes = bellEveryMinutes.coerceIn(0, MAX_MINUTES))

    companion object {
        const val MIN_MINUTES = 1
        const val MAX_MINUTES = 180
    }
}

data class Practice(
    val id: String,
    val name: String,
    val sanskrit: String,
    val category: Category,
    val summary: String,
    val howTo: List<String>,
    val caution: String?,
    val kind: Kind,
    val pattern: BreathPattern,
    val rounds: Int,
    val chant: ChantSound = ChantSound.NONE,
    /** For ALTERNATE: the nostril used for each inhale in one round. The exhale uses the other one. */
    val inhaleSides: List<Nostril> = listOf(Nostril.BOTH),
    val pump: PumpSettings? = null,
    val restSeconds: Double = 0.0,
)
