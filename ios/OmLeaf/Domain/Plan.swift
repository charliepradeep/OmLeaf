import Foundation

/// One timed piece of a session, such as "inhale through the left nostril for 4 seconds".
struct Step: Equatable {
    let index: Int
    let round: Int
    let phase: Phase
    let startMs: Int
    let durationMs: Int
    var nostril: Nostril = .both
    var chant: ChantSound = .none
    var stroke = 0
    var strokeTotal = 0

    var endMs: Int { startMs + durationMs }
}

struct Plan {
    let steps: [Step]
    let rounds: Int
    let totalMs: Int

    init(steps: [Step], rounds: Int) {
        self.steps = steps
        self.rounds = rounds
        self.totalMs = steps.last?.endMs ?? 0
    }

    /// The step playing at `ms`; clamped to the first or last step.
    func indexAt(ms: Int) -> Int {
        if steps.isEmpty || ms <= 0 { return 0 }
        if ms >= totalMs { return steps.count - 1 }
        var lo = 0
        var hi = steps.count - 1
        while lo < hi {
            let mid = (lo + hi + 1) / 2
            if steps[mid].startMs <= ms { lo = mid } else { hi = mid - 1 }
        }
        return lo
    }

    /// How far through the current step `ms` is, 0...1.
    func progressAt(ms: Int) -> Double {
        if steps.isEmpty { return 0 }
        let step = steps[indexAt(ms: ms)]
        if step.durationMs <= 0 { return 1 }
        return (Double(ms - step.startMs) / Double(step.durationMs)).clamped(0...1)
    }
}

/// Everything the user can change before starting.
struct PlanRequest: Equatable {
    var kind: Kind
    var pattern: BreathPattern
    var rounds: Int
    var chant: ChantSound
    var inhaleSides: [Nostril] = [.both]
    var pump: PumpSettings?
    var restSeconds: Double = 0
}

private struct StepBuilder {
    var steps: [Step] = []
    var time = 0

    mutating func add(
        round: Int, phase: Phase, seconds: Double,
        nostril: Nostril = .both, chant: ChantSound = .none, stroke: Int = 0, total: Int = 0
    ) {
        let ms = Int((seconds * 1000).rounded())
        if ms <= 0 { return }
        steps.append(Step(
            index: steps.count, round: round, phase: phase, startMs: time, durationMs: ms,
            nostril: nostril, chant: chant, stroke: stroke, strokeTotal: total
        ))
        time += ms
    }
}

enum Planner {
    static let maxRounds = 200

    static func request(for practice: Practice) -> PlanRequest {
        PlanRequest(
            kind: practice.kind, pattern: practice.pattern, rounds: practice.rounds, chant: practice.chant,
            inhaleSides: practice.inhaleSides, pump: practice.pump, restSeconds: practice.restSeconds
        )
    }

    static func build(_ request: PlanRequest) -> Plan {
        let rounds = request.rounds.clamped(1...maxRounds)
        var builder = StepBuilder()
        switch request.kind {
        case .rhythm, .alternate:
            addBreaths(request, rounds: rounds, to: &builder)
        case .pump:
            addPumps(request, rounds: rounds, to: &builder)
        }
        return Plan(steps: builder.steps, rounds: rounds)
    }

    private static func addBreaths(_ request: PlanRequest, rounds: Int, to builder: inout StepBuilder) {
        let pattern = request.pattern.clamped()
        let useSides = request.kind == .alternate && !request.inhaleSides.isEmpty
        let sides: [Nostril] = useSides ? request.inhaleSides : [.both]
        let rest = request.restSeconds.clamped(0...300)
        for round in 1...rounds {
            for side in sides {
                builder.add(round: round, phase: .inhale, seconds: pattern.inhale, nostril: side)
                builder.add(round: round, phase: .holdIn, seconds: pattern.holdIn)
                builder.add(round: round, phase: .exhale, seconds: pattern.exhale, nostril: side.opposite, chant: request.chant)
                builder.add(round: round, phase: .holdOut, seconds: pattern.holdOut)
            }
            if round < rounds { builder.add(round: round, phase: .rest, seconds: rest) }
        }
    }

    private static func addPumps(_ request: PlanRequest, rounds: Int, to builder: inout StepBuilder) {
        let pump = request.pump ?? PumpSettings(strokes: 30, strokesPerMinute: 60, finishInhale: 4, finishHold: 8, finishExhale: 6)
        let count = pump.strokes.clamped(5...200)
        let period = 60.0 / Double(pump.strokesPerMinute.clamped(20...140))
        let rest = request.restSeconds.clamped(0...300)
        for round in 1...rounds {
            for stroke in 1...count {
                builder.add(round: round, phase: .stroke, seconds: period, stroke: stroke, total: count)
            }
            builder.add(round: round, phase: .inhale, seconds: pump.finishInhale.clamped(1...30))
            builder.add(round: round, phase: .holdIn, seconds: pump.finishHold.clamped(0...60))
            builder.add(round: round, phase: .exhale, seconds: pump.finishExhale.clamped(1...30))
            if round < rounds { builder.add(round: round, phase: .rest, seconds: rest) }
        }
    }
}
