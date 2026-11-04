import XCTest
@testable import OmLeaf

final class PlannerTests: XCTestCase {
    private func request(
        kind: Kind = .rhythm,
        pattern: BreathPattern = BreathPattern(inhale: 4, holdIn: 0, exhale: 6, holdOut: 0),
        rounds: Int = 3,
        sides: [Nostril] = [.both],
        rest: Double = 0,
        pump: PumpSettings? = nil,
        chant: ChantSound = .none
    ) -> PlanRequest {
        PlanRequest(kind: kind, pattern: pattern, rounds: rounds, chant: chant, inhaleSides: sides, pump: pump, restSeconds: rest)
    }

    func testRhythmSkipsZeroHolds() {
        let plan = Planner.build(request())
        XCTAssertEqual(plan.steps.count, 6)
        XCTAssertEqual(plan.totalMs, 30_000)
        XCTAssertEqual(plan.steps.prefix(2).map(\.phase), [.inhale, .exhale])
    }

    func testBoxBreathingHasFourPhasesPerRound() {
        let plan = Planner.build(request(pattern: BreathPattern(inhale: 4, holdIn: 4, exhale: 4, holdOut: 4), rounds: 2))
        XCTAssertEqual(plan.steps.count, 8)
        XCTAssertEqual(plan.totalMs, 32_000)
    }

    func testStepsAreContiguous() {
        let plan = Planner.build(request(pattern: BreathPattern(inhale: 4, holdIn: 7, exhale: 8, holdOut: 1.5), rounds: 5, rest: 3))
        for (first, second) in zip(plan.steps, plan.steps.dropFirst()) {
            XCTAssertEqual(first.endMs, second.startMs)
        }
        for (index, step) in plan.steps.enumerated() {
            XCTAssertEqual(step.index, index)
        }
    }

    func testAlternateNostrilUsesOppositeSideOnExhale() {
        let plan = Planner.build(request(
            kind: .alternate, pattern: BreathPattern(inhale: 4, holdIn: 4, exhale: 8, holdOut: 0), rounds: 1, sides: [.left, .right]
        ))
        let sides = plan.steps.filter { $0.phase == .inhale || $0.phase == .exhale }.map(\.nostril)
        XCTAssertEqual(sides, [.left, .right, .right, .left])
    }

    func testChantOnlyOnExhale() {
        let plan = Planner.build(request(rounds: 2, chant: .aum))
        for step in plan.steps {
            XCTAssertEqual(step.phase == .exhale, step.chant == .aum)
        }
    }

    func testRestBetweenRoundsButNotAfterLast() {
        let plan = Planner.build(request(rounds: 3, rest: 5))
        XCTAssertEqual(plan.steps.filter { $0.phase == .rest }.count, 2)
        XCTAssertNotEqual(plan.steps.last?.phase, .rest)
    }

    func testPumpMakesOneStepPerStroke() {
        let pump = PumpSettings(strokes: 30, strokesPerMinute: 60, finishInhale: 4, finishHold: 8, finishExhale: 6)
        let plan = Planner.build(request(kind: .pump, rounds: 2, rest: 10, pump: pump))
        XCTAssertEqual(plan.steps.filter { $0.phase == .stroke }.count, 60)
        XCTAssertEqual(plan.steps[0].durationMs, 1000)
        XCTAssertEqual(plan.steps[29].stroke, 30)
        XCTAssertEqual(plan.steps[29].strokeTotal, 30)
    }

    func testRoundsAndTimesAreClamped() {
        let plan = Planner.build(request(pattern: BreathPattern(inhale: 0, holdIn: -3, exhale: 500, holdOut: 0), rounds: 9999))
        XCTAssertEqual(plan.rounds, Planner.maxRounds)
        XCTAssertEqual(plan.steps[0].durationMs, 1000)
        XCTAssertTrue(plan.steps.allSatisfy { $0.durationMs <= 60_000 })
    }

    func testIndexAtFindsTheRightStep() {
        let plan = Planner.build(request(rounds: 1))
        XCTAssertEqual(plan.indexAt(ms: 0), 0)
        XCTAssertEqual(plan.indexAt(ms: 3_999), 0)
        XCTAssertEqual(plan.indexAt(ms: 4_000), 1)
        XCTAssertEqual(plan.indexAt(ms: 999_999), 1)
        XCTAssertEqual(plan.progressAt(ms: 2_000), 0.5, accuracy: 1e-9)
    }

    func testEveryBuiltInPracticeBuilds() {
        for practice in Practices.all {
            let plan = Planner.build(Planner.request(for: practice))
            XCTAssertFalse(plan.steps.isEmpty, "\(practice.id) has steps")
            XCTAssertGreaterThan(plan.totalMs, 0, "\(practice.id) has a time")
        }
        XCTAssertEqual(Practices.all.count, Set(Practices.all.map(\.id)).count)
        XCTAssertEqual(Practices.all.count, 14)
    }

    func testChantPartsAddUp() {
        for chant in ChantSound.allCases where !chant.parts.isEmpty {
            XCTAssertEqual(chant.parts.reduce(0) { $0 + $1.weight }, 1.0, accuracy: 1e-9)
        }
        XCTAssertEqual(ChantSound.aum.parts.map(\.label), ["A", "U", "M"])
        XCTAssertEqual(ChantSound.aum.partAt(0.1), 0)
        XCTAssertEqual(ChantSound.aum.partAt(0.3), 1)
        XCTAssertEqual(ChantSound.aum.partAt(0.6), 2)
        XCTAssertEqual(ChantSound.none.partAt(0.5), -1)
    }

    func testCircleScaleStaysInRange() {
        let plan = Planner.build(request(pattern: BreathPattern(inhale: 4, holdIn: 4, exhale: 4, holdOut: 4), rounds: 1))
        for step in plan.steps {
            for p in stride(from: 0.0, through: 1.0, by: 0.25) {
                let scale = circleScale(step: step, progress: p)
                XCTAssertGreaterThanOrEqual(scale, 0.44)
                XCTAssertLessThanOrEqual(scale, 1.0)
            }
        }
    }
}
