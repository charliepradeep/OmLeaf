package com.charrlie.omleaf.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PlannerTest {
    private fun req(kind: Kind = Kind.RHYTHM, p: BreathPattern = BreathPattern(4.0, 0.0, 6.0, 0.0), rounds: Int = 3, sides: List<Nostril> = listOf(Nostril.BOTH), rest: Double = 0.0, pump: PumpSettings? = null, chant: ChantSound = ChantSound.NONE) =
        PlanRequest(kind, p, rounds, chant, sides, pump, rest)

    @Test fun rhythmSkipsZeroHolds() {
        val plan = Planner.build(req())
        assertEquals(6, plan.steps.size)
        assertEquals(30_000L, plan.totalMs)
        assertEquals(listOf(Phase.INHALE, Phase.EXHALE), plan.steps.take(2).map { it.phase })
    }

    @Test fun boxBreathingHasFourPhasesPerRound() {
        val plan = Planner.build(req(p = BreathPattern(4.0, 4.0, 4.0, 4.0), rounds = 2))
        assertEquals(8, plan.steps.size)
        assertEquals(32_000L, plan.totalMs)
    }

    @Test fun stepsAreContiguous() {
        val plan = Planner.build(req(p = BreathPattern(4.0, 7.0, 8.0, 1.5), rounds = 5, rest = 3.0))
        plan.steps.zipWithNext().forEach { (a, b) -> assertEquals(a.endMs, b.startMs) }
        plan.steps.forEachIndexed { i, s -> assertEquals(i, s.index) }
    }

    @Test fun alternateNostrilUsesOppositeSideOnExhale() {
        val plan = Planner.build(req(Kind.ALTERNATE, BreathPattern(4.0, 4.0, 8.0, 0.0), 1, listOf(Nostril.LEFT, Nostril.RIGHT)))
        val seq = plan.steps.filter { it.phase == Phase.INHALE || it.phase == Phase.EXHALE }.map { it.phase to it.nostril }
        assertEquals(
            listOf(Phase.INHALE to Nostril.LEFT, Phase.EXHALE to Nostril.RIGHT, Phase.INHALE to Nostril.RIGHT, Phase.EXHALE to Nostril.LEFT),
            seq,
        )
    }

    @Test fun chantOnlyOnExhale() {
        val plan = Planner.build(req(chant = ChantSound.AUM, rounds = 2))
        plan.steps.forEach { assertEquals(it.phase == Phase.EXHALE, it.chant == ChantSound.AUM) }
    }

    @Test fun restBetweenRoundsButNotAfterLast() {
        val plan = Planner.build(req(rounds = 3, rest = 5.0))
        assertEquals(2, plan.steps.count { it.phase == Phase.REST })
        assertTrue(plan.steps.last().phase != Phase.REST)
    }

    @Test fun pumpMakesOneStepPerStroke() {
        val plan = Planner.build(req(Kind.PUMP, rounds = 2, pump = PumpSettings(30, 60, 4.0, 8.0, 6.0), rest = 10.0))
        assertEquals(60, plan.steps.count { it.phase == Phase.STROKE })
        assertEquals(1000L, plan.steps.first().durationMs)
        assertEquals(30, plan.steps[29].stroke)
        assertEquals(30, plan.steps[29].strokeTotal)
    }

    @Test fun roundsAndTimesAreClamped() {
        val plan = Planner.build(req(p = BreathPattern(0.0, -3.0, 500.0, 0.0), rounds = 9999))
        assertEquals(Planner.MAX_ROUNDS, plan.rounds)
        assertEquals(1000L, plan.steps.first().durationMs)
        assertTrue(plan.steps.all { it.durationMs <= 60_000L })
    }

    @Test fun indexAtFindsTheRightStep() {
        val plan = Planner.build(req(rounds = 1))
        assertEquals(0, plan.indexAt(0))
        assertEquals(0, plan.indexAt(3_999))
        assertEquals(1, plan.indexAt(4_000))
        assertEquals(1, plan.indexAt(999_999))
        assertEquals(0.5, plan.progressAt(2_000), 1e-9)
    }

    @Test fun everyBuiltInPracticeBuilds() {
        Practices.all.forEach { p ->
            val plan = Planner.build(Planner.requestFor(p))
            assertTrue("${p.id} has steps", plan.steps.isNotEmpty())
            assertTrue("${p.id} has a time", plan.totalMs > 0)
        }
        assertEquals(Practices.all.size, Practices.all.map { it.id }.toSet().size)
    }

    @Test fun chantPartsAddUp() {
        ChantSound.entries.filter { it.parts.isNotEmpty() }.forEach { c ->
            assertEquals(1.0, c.parts.sumOf { it.second }, 1e-9)
        }
        assertEquals(listOf("A", "U", "M"), ChantSound.AUM.parts.map { it.first })
        assertEquals(0, ChantSound.AUM.partAt(0.1))
        assertEquals(1, ChantSound.AUM.partAt(0.3))
        assertEquals(2, ChantSound.AUM.partAt(0.6))
        assertEquals(-1, ChantSound.NONE.partAt(0.5))
    }
}
