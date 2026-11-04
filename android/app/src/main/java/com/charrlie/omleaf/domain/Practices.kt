package com.charrlie.omleaf.domain

/** Built-in practices. Numbers are gentle beginner defaults; everything can be changed before starting. */
object Practices {
    private const val PUMP_CAUTION =
        "A forceful practice. Skip it if you are pregnant, have high blood pressure, a heart condition, a hernia, " +
            "epilepsy or recent abdominal surgery, or ask your doctor first. Stop if you feel dizzy."

    private const val HOLD_CAUTION = "Never strain on the holds. If you feel uncomfortable, shorten them or set them to 0."

    val all: List<Practice> = listOf(
        Practice(
            "dirgha", "Deep belly breathing", "Dirgha", Category.CALMING,
            "Slow, full breaths into the belly. A good place to begin.",
            listOf("Sit comfortably with a straight back.", "Breathe in slowly and let the belly expand.", "Breathe out slowly and let the belly soften."),
            null, Kind.RHYTHM, BreathPattern(4.0, 0.0, 6.0, 0.0), 10,
        ),
        Practice(
            "sama", "Equal breathing", "Sama Vritti", Category.CALMING,
            "Inhale and exhale for the same length.",
            listOf("Breathe through the nose.", "Match the length of the inhale and the exhale."),
            null, Kind.RHYTHM, BreathPattern(4.0, 0.0, 4.0, 0.0), 12,
        ),
        Practice(
            "box", "Box breathing", "Sama Vritti with holds", Category.CALMING,
            "Four equal sides: in, hold, out, hold.",
            listOf("Breathe in for the count.", "Hold gently.", "Breathe out for the count.", "Hold gently with the lungs empty."),
            HOLD_CAUTION, Kind.RHYTHM, BreathPattern(4.0, 4.0, 4.0, 4.0), 8,
        ),
        Practice(
            "478", "4-7-8 breathing", "Relaxing breath", Category.CALMING,
            "A long exhale to help you unwind. Four rounds are plenty at first.",
            listOf("Inhale quietly through the nose.", "Hold.", "Exhale slowly through the mouth with a soft whoosh."),
            HOLD_CAUTION, Kind.RHYTHM, BreathPattern(4.0, 7.0, 8.0, 0.0), 4,
        ),
        Practice(
            "ujjayi", "Ocean breath", "Ujjayi", Category.CALMING,
            "A soft, audible breath with a slightly narrowed throat, like a quiet ocean.",
            listOf("Narrow the back of the throat slightly, as if fogging a mirror, but with the mouth closed.", "Breathe in and out through the nose with a gentle ocean sound."),
            null, Kind.RHYTHM, BreathPattern(4.0, 0.0, 6.0, 0.0), 10,
        ),
        Practice(
            "bhramari", "Humming bee breath", "Bhramari", Category.CALMING,
            "Breathe in, then hum on the exhale and feel the vibration in the head.",
            listOf("Close the lips and keep the teeth slightly apart.", "Breathe in through the nose.", "Exhale with a steady, smooth hum."),
            null, Kind.RHYTHM, BreathPattern(4.0, 0.0, 8.0, 0.0), 7, ChantSound.HUM,
        ),
        Practice(
            "nadi", "Alternate nostril breathing", "Nadi Shodhana (Anuloma Viloma)", Category.BALANCING,
            "In through one nostril, out through the other, then back.",
            listOf(
                "Use the right hand: thumb closes the right nostril, ring finger closes the left.",
                "Follow the on-screen nostril for each breath.",
                "One round is a full cycle: left in, right out, right in, left out.",
            ),
            HOLD_CAUTION, Kind.ALTERNATE, BreathPattern(4.0, 4.0, 8.0, 0.0), 6,
            inhaleSides = listOf(Nostril.LEFT, Nostril.RIGHT),
        ),
        Practice(
            "chandra", "Moon-piercing breath", "Chandra Bhedana", Category.COOLING,
            "Inhale through the left nostril, exhale through the right.",
            listOf("Close the right nostril and breathe in through the left.", "Close the left and breathe out through the right."),
            HOLD_CAUTION, Kind.ALTERNATE, BreathPattern(4.0, 2.0, 8.0, 0.0), 9,
            inhaleSides = listOf(Nostril.LEFT),
        ),
        Practice(
            "surya", "Sun-piercing breath", "Surya Bhedana", Category.ENERGIZING,
            "Inhale through the right nostril, exhale through the left.",
            listOf("Close the left nostril and breathe in through the right.", "Close the right and breathe out through the left."),
            HOLD_CAUTION + " Many teachers advise against it with high blood pressure.", Kind.ALTERNATE, BreathPattern(4.0, 2.0, 8.0, 0.0), 9,
            inhaleSides = listOf(Nostril.RIGHT),
        ),
        Practice(
            "sheetali", "Cooling breath", "Sheetali", Category.COOLING,
            "Breathe in through a curled tongue, out through the nose.",
            listOf("Roll the tongue into a tube and breathe in through it.", "Close the mouth and exhale through the nose.", "If you cannot curl the tongue, use Sheetkari: breathe in through the teeth."),
            "Avoid in very cold weather or if you have a cold.", Kind.RHYTHM, BreathPattern(4.0, 3.0, 6.0, 0.0), 9,
        ),
        Practice(
            "kapalabhati", "Skull-shining breath", "Kapalabhati", Category.ENERGIZING,
            "Quick, sharp exhales with a passive inhale, then one slow breath.",
            listOf(
                "Each pump is a short, sharp exhale from the belly. The inhale happens on its own.",
                "After the pumps, take a slow breath in, hold gently, and breathe out.",
                "Breathe normally in the rest between rounds.",
            ),
            PUMP_CAUTION, Kind.PUMP, BreathPattern(4.0, 8.0, 6.0, 0.0), 3,
            pump = PumpSettings(strokes = 30, strokesPerMinute = 60, finishInhale = 4.0, finishHold = 8.0, finishExhale = 6.0),
            restSeconds = 15.0,
        ),
        Practice(
            "bhastrika", "Bellows breath", "Bhastrika", Category.ENERGIZING,
            "Forceful, equal breaths in and out, then a slow breath.",
            listOf("Breathe in and out with equal force, like a bellows.", "After the breaths, take one slow breath, hold gently, and exhale."),
            PUMP_CAUTION, Kind.PUMP, BreathPattern(4.0, 8.0, 6.0, 0.0), 3,
            pump = PumpSettings(strokes = 20, strokesPerMinute = 40, finishInhale = 4.0, finishHold = 8.0, finishExhale = 6.0),
            restSeconds = 20.0,
        ),
        Practice(
            "aum", "AUM / OM chanting", "Omkara", Category.CHANTING,
            "Chant A, U, M on one exhale. The M takes half of the exhale.",
            listOf("Breathe in deeply.", "Exhale chanting A (open mouth), then U, then M (lips closed, humming).", "Follow the guide tone, tuned to the frequency you choose in Sound settings."),
            null, Kind.RHYTHM, BreathPattern(4.0, 0.0, 12.0, 0.0), 9, ChantSound.AUM,
        ),
        Practice(
            "aakaar", "A-sound chanting", "Akaar", Category.CHANTING,
            "Chant a steady open \"A\" on each exhale.",
            listOf("Breathe in deeply.", "Exhale chanting a long, relaxed \"ahh\" from the chest."),
            null, Kind.RHYTHM, BreathPattern(4.0, 0.0, 10.0, 0.0), 9, ChantSound.A,
        ),
    )

    fun byId(id: String): Practice? = all.firstOrNull { it.id == id }

    fun grouped(): List<Pair<Category, List<Practice>>> =
        Category.entries.mapNotNull { c -> all.filter { it.category == c }.takeIf { it.isNotEmpty() }?.let { c to it } }
}
