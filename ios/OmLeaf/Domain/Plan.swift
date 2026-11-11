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
    /// For meditation steps: ring a bowl as this step begins.
    var bell = false

    var endMs: Int { startMs + durationMs }
}

struct Plan {
    let steps: [Step]
    let rounds: Int
    let totalMs: Int
    /// Non-nil for a sound-only meditation.
    let style: MeditationStyle?
    /// The tone fades out over this long before the end.
    let fadeOutMs: Int

    init(steps: [Step], rounds: Int, style: MeditationStyle? = nil, fadeOutMs: Int = 0) {
        self.steps = steps
        self.rounds = rounds
        self.totalMs = steps.last?.endMs ?? 0
        self.style = style
        self.fadeOutMs = fadeOutMs
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
    var meditation: MeditationSettings?
}

private struct StepBuilder {
    var steps: [Step] = []
    var time = 0

    mutating func add(
        round: Int, phase: Phase, seconds: Double,
        nostril: Nostril = .both, chant: ChantSound = .none, stroke: Int = 0, total: Int = 0, bell: Bool = false
    ) {
        let ms = Int((seconds * 1000).rounded())
        if ms <= 0 { return }
        steps.append(Step(
            index: steps.count, round: round, phase: phase, startMs: time, durationMs: ms,
            nostril: nostril, chant: chant, stroke: stroke, strokeTotal: total, bell: bell
        ))
        time += ms
    }
}

enum Planner {
    static let maxRounds = 200
    static let closingMs = 6_000
    static let chantMs = 12_000
    static let pauseMs = 4_000
    static let fadeOutMs = 15_000

    /// A sound-only meditation: no breaths, just listening.
    static func meditationRequest(_ settings: MeditationSettings) -> PlanRequest {
        PlanRequest(
            kind: .meditation, pattern: BreathPattern(inhale: 4, holdIn: 0, exhale: 6, holdOut: 0),
            rounds: 1, chant: .none, meditation: settings
        )
    }

    static func request(for practice: Practice) -> PlanRequest {
        PlanRequest(
            kind: practice.kind, pattern: practice.pattern, rounds: practice.rounds, chant: practice.chant,
            inhaleSides: practice.inhaleSides, pump: practice.pump, restSeconds: practice.restSeconds
        )
    }

    static func build(_ request: PlanRequest) -> Plan {
        if request.kind == .meditation { return buildMeditation((request.meditation ?? MeditationSettings()).sanitized()) }
        let rounds = request.rounds.clamped(1...maxRounds)
        var builder = StepBuilder()
        switch request.kind {
        case .rhythm, .alternate:
            addBreaths(request, rounds: rounds, to: &builder)
        case .pump:
            addPumps(request, rounds: rounds, to: &builder)
        case .meditation:
            break
        }
        return Plan(steps: builder.steps, rounds: rounds)
    }

    /// Listening steps for the whole time, ending with a short closing step. With interval bells the time is cut into
    /// segments, and a segment starts with a bowl. In AUM style a segment is filled with chant, pause, chant, pause...
    private static func buildMeditation(_ med: MeditationSettings) -> Plan {
        let totalMs = med.minutes * 60_000
        let mainMs = totalMs - min(closingMs, totalMs / 2)
        let interval = (1..<med.minutes).contains(med.bellEveryMinutes) ? med.bellEveryMinutes * 60_000 : mainMs
        var builder = StepBuilder()
        var time: Int { builder.time }

        func add(_ ms: Int, chant: ChantSound, bell: Bool) {
            builder.add(round: 1, phase: .listen, seconds: Double(ms) / 1000, chant: chant, bell: bell)
        }

        var segmentStart = 0
        var segment = 0
        while segmentStart < mainMs {
            let segmentEnd = min(segmentStart + interval, mainMs)
            var bell = segment == 0 ? med.bells : true
            if med.style == .aum {
                while time < segmentEnd {
                    add(min(chantMs, segmentEnd - time), chant: .aum, bell: bell)
                    bell = false
                    add(min(pauseMs, segmentEnd - time), chant: .none, bell: false)
                }
            } else {
                add(segmentEnd - time, chant: .none, bell: bell)
            }
            segmentStart = segmentEnd
            segment += 1
        }
        add(totalMs - time, chant: .none, bell: med.bells)
        return Plan(steps: builder.steps, rounds: 1, style: med.style, fadeOutMs: min(fadeOutMs, totalMs / 2))
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
