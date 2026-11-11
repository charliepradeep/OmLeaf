package com.charrlie.omleaf

import com.charrlie.omleaf.domain.ChantSound
import com.charrlie.omleaf.domain.FrequencyPresets
import com.charrlie.omleaf.domain.MeditationSettings
import com.charrlie.omleaf.domain.MeditationStyle
import com.charrlie.omleaf.domain.Phase
import com.charrlie.omleaf.domain.Pitch
import com.charrlie.omleaf.domain.PlanSynth
import com.charrlie.omleaf.domain.Planner
import com.charrlie.omleaf.domain.SoundSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MeditationTest {
    private fun plan(m: MeditationSettings) = Planner.build(Planner.meditationRequest(m))

    private fun render(s: PlanSynth, fromSec: Double, seconds: Double): ShortArray {
        val out = ShortArray((seconds * s.sampleRate).toInt())
        s.render((fromSec * s.sampleRate).toLong(), out, out.size)
        return out
    }

    private fun rms(a: ShortArray) = Math.sqrt(a.fold(0.0) { acc, v -> acc + v.toDouble() * v } / a.size)

    @Test fun totalIsExactlyTheChosenMinutes() {
        for (style in MeditationStyle.entries) {
            val p = plan(MeditationSettings(minutes = 7, style = style, bellEveryMinutes = 2))
            assertEquals("$style", 7 * 60_000L, p.totalMs)
            assertTrue(p.steps.all { it.phase == Phase.LISTEN })
            assertEquals(style, p.style)
        }
    }

    @Test fun stepsAreContiguous() {
        val p = plan(MeditationSettings(minutes = 5, style = MeditationStyle.AUM, bellEveryMinutes = 1))
        p.steps.zipWithNext { a, b -> assertEquals(a.endMs, b.startMs) }
        p.steps.forEachIndexed { i, s -> assertEquals(i, s.index) }
    }

    @Test fun bellsAtStartIntervalsAndEnd() {
        val p = plan(MeditationSettings(minutes = 10, style = MeditationStyle.BOWL, bellEveryMinutes = 3))
        val bellStarts = p.steps.filter { it.bell }.map { it.startMs }
        assertEquals(listOf(0L, 180_000L, 360_000L, 540_000L, 594_000L), bellStarts)
        assertEquals(594_000L, p.steps.last().startMs)
    }

    @Test fun bellsCanBeSwitchedOff() {
        val p = plan(MeditationSettings(minutes = 5, bellEveryMinutes = 0, bells = false))
        assertTrue(p.steps.none { it.bell })
    }

    @Test fun intervalLongerThanSessionMeansNoIntervalBells() {
        val p = plan(MeditationSettings(minutes = 5, bellEveryMinutes = 5))
        assertEquals(listOf(0L, 294_000L), p.steps.filter { it.bell }.map { it.startMs })
    }

    @Test fun aumStyleChantsAndPauses() {
        val p = plan(MeditationSettings(minutes = 2, style = MeditationStyle.AUM))
        assertEquals(ChantSound.AUM, p.steps[0].chant)
        assertEquals(12_000L, p.steps[0].durationMs)
        assertEquals(ChantSound.NONE, p.steps[1].chant)
        assertEquals(4_000L, p.steps[1].durationMs)
        assertEquals(ChantSound.NONE, p.steps.last().chant)
    }

    @Test fun minutesAreClamped() {
        assertEquals(60_000L, plan(MeditationSettings(minutes = 0)).totalMs)
        assertEquals(180 * 60_000L, plan(MeditationSettings(minutes = 9999)).totalMs)
    }

    @Test fun droneIsHeardEvenWithBreathSwitchesOff() {
        val off = SoundSettings(chimes = false, guideTone = false, drone = false)
        val s = PlanSynth(plan(MeditationSettings(minutes = 2, style = MeditationStyle.DRONE, bells = false)), off)
        assertTrue(rms(render(s, 20.0, 1.0)) > 100)
    }

    @Test fun bowlStyleIsQuietBetweenBells() {
        val s = PlanSynth(plan(MeditationSettings(minutes = 3, style = MeditationStyle.BOWL, bells = true)), SoundSettings())
        assertTrue(rms(render(s, 0.0, 1.0)) > 100)
        assertEquals(0.0, rms(render(s, 60.0, 1.0)), 0.0)
    }

    @Test fun aumStyleSoundsDuringChantAndNotDuringPause() {
        val s = PlanSynth(plan(MeditationSettings(minutes = 2, style = MeditationStyle.AUM, bells = false)), SoundSettings(chimes = false))
        assertTrue(rms(render(s, 5.0, 1.0)) > 100)
        assertEquals(0.0, rms(render(s, 13.0, 2.5)), 0.0)
    }

    @Test fun toneFadesOutAtTheEnd() {
        val s = PlanSynth(plan(MeditationSettings(minutes = 2, style = MeditationStyle.DRONE, bells = false)), SoundSettings())
        assertTrue(rms(render(s, 60.0, 1.0)) > 3 * rms(render(s, 118.5, 0.5)))
    }

    @Test fun volumeZeroIsSilent() {
        val s = PlanSynth(plan(MeditationSettings(minutes = 2)), SoundSettings(volume = 0f))
        assertEquals(0.0, rms(render(s, 10.0, 1.0)), 0.0)
    }

    @Test fun pitchRangeAllowsTypedValues() {
        assertEquals(0.0, SoundSettings(frequencyHz = -5.0).sanitized().frequencyHz, 0.0)
        assertEquals(1000.0, SoundSettings(frequencyHz = 5000.0).sanitized().frequencyHz, 0.0)
        assertEquals(963.0, SoundSettings(frequencyHz = 963.0).sanitized().frequencyHz, 0.0)
    }

    @Test fun typedPitchIsParsed() {
        assertEquals(136.1, FrequencyPresets.parse("136.1")!!, 1e-9)
        assertEquals(136.1, FrequencyPresets.parse(" 136,1 ")!!, 1e-9)
        assertEquals(1000.0, FrequencyPresets.parse("99999")!!, 0.0)
        assertNull(FrequencyPresets.parse(""))
        assertNull(FrequencyPresets.parse("abc"))
        assertNull(FrequencyPresets.parse("NaN"))
    }

    @Test fun noteNames() {
        assertEquals("A4", Pitch.noteName(440.0))
        assertEquals("C4", Pitch.noteName(261.63))
        assertEquals("C#3 -31¢", Pitch.noteName(136.1))
        assertEquals("A4 +4¢", Pitch.noteName(441.0))
        assertNull(Pitch.noteName(5.0))
        assertNotNull(Pitch.noteName(963.0))
    }

    @Test fun pitchFormatting() {
        assertEquals("432", Pitch.format(432.0))
        assertEquals("136.1", Pitch.format(136.1))
    }

}
