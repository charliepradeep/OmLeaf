package com.charrlie.omleaf.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SynthStatsTest {
    private fun plan(chant: ChantSound = ChantSound.AUM) =
        Planner.build(PlanRequest(Kind.RHYTHM, BreathPattern(2.0, 0.0, 4.0, 0.0), 2, chant))

    private fun render(synth: PlanSynth, seconds: Double): ShortArray {
        val n = (seconds * synth.sampleRate).toInt()
        val out = ShortArray(n)
        synth.render(0, out, n)
        return out
    }

    @Test fun silentWhenEverythingIsOff() {
        val out = render(PlanSynth(plan(), SoundSettings(chimes = false, guideTone = false, drone = false)), 5.0)
        assertTrue(out.all { it == 0.toShort() })
    }

    @Test fun makesSoundAndStaysInRange() {
        val out = render(PlanSynth(plan(), SoundSettings(drone = true)), 12.0)
        assertTrue(out.any { it != 0.toShort() })
        assertTrue(out.all { it in -32767..32767 })
    }

    @Test fun volumeZeroIsSilent() {
        val out = render(PlanSynth(plan(), SoundSettings(volume = 0f)), 5.0)
        assertTrue(out.all { it == 0.toShort() })
    }

    @Test fun chunkedRenderMatchesOneShot() {
        val synth = PlanSynth(plan(), SoundSettings(drone = true))
        val whole = render(synth, 6.0)
        val pieces = ShortArray(whole.size)
        var pos = 0
        val chunk = ShortArray(1234)
        while (pos < whole.size) {
            val n = minOf(chunk.size, whole.size - pos)
            synth.render(pos.toLong(), chunk, n)
            System.arraycopy(chunk, 0, pieces, pos, n)
            pos += n
        }
        assertTrue(whole.contentEquals(pieces))
    }

    @Test fun guideToneOnlyDuringChantedExhale() {
        val synth = PlanSynth(plan(), SoundSettings(chimes = false, drone = false))
        val sr = synth.sampleRate
        val inhale = ShortArray(sr); synth.render(0, inhale, sr)                 // 0 to 1 s is inhale
        val exhale = ShortArray(sr); synth.render(3L * sr, exhale, sr)           // 3 to 4 s is exhale
        assertTrue(inhale.all { it == 0.toShort() })
        assertTrue(exhale.any { it != 0.toShort() })
    }

    @Test fun frequencyIsClamped() {
        assertEquals(FrequencyPresets.MAX_HZ, SoundSettings(frequencyHz = 5000.0).sanitized().frequencyHz, 0.0)
        assertEquals(FrequencyPresets.MIN_HZ, SoundSettings(frequencyHz = 1.0).sanitized().frequencyHz, 0.0)
    }

    private fun rec(day: Long, sec: Int = 600) = SessionRecord(day, "x", sec, 5)

    @Test fun streaksCountConsecutiveDays() {
        val s = StatsCalculator.compute(listOf(rec(10), rec(11), rec(12), rec(15), rec(16)), 16)
        assertEquals(2, s.currentStreak)
        assertEquals(3, s.bestStreak)
        assertEquals(5, s.sessions)
    }

    @Test fun streakSurvivesUntilTodayEnds() {
        assertEquals(2, StatsCalculator.compute(listOf(rec(9), rec(10)), 11).currentStreak)
        assertEquals(0, StatsCalculator.compute(listOf(rec(8), rec(9)), 11).currentStreak)
    }

    @Test fun last7AndTotals() {
        val s = StatsCalculator.compute(listOf(rec(20, 300), rec(20, 300), rec(14, 120)), 20)
        assertEquals(listOf(2, 0, 0, 0, 0, 0, 10), s.last7Minutes)
        assertEquals(12, s.totalMinutes)
        val none = StatsCalculator.compute(emptyList(), 20)
        assertEquals(0, none.currentStreak); assertEquals(0, none.sessions)
    }
}
