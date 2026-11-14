import Foundation

extension Comparable {
    func clamped(_ range: ClosedRange<Self>) -> Self {
        min(max(self, range.lowerBound), range.upperBound)
    }
}

/// What the breath is doing during one step of a session.
enum Phase: String, Codable {
    case inhale, holdIn, exhale, holdOut, stroke, rest
    /// Sound-only meditation: nothing to do but listen.
    case listen

    var label: String {
        switch self {
        case .inhale: return "Inhale"
        case .holdIn, .holdOut: return "Hold"
        case .exhale: return "Exhale"
        case .stroke: return "Pump"
        case .rest: return "Breathe normally"
        case .listen: return "Listen"
        }
    }
}

/// Which nostril is open for a step (the other is closed). `both` for ordinary breathing and for holds.
enum Nostril: String, Codable {
    case both, left, right

    var label: String {
        switch self {
        case .both: return ""
        case .left: return "Left nostril"
        case .right: return "Right nostril"
        }
    }

    var opposite: Nostril {
        switch self {
        case .left: return .right
        case .right: return .left
        case .both: return .both
        }
    }
}

struct ChantPart: Equatable {
    let label: String
    let weight: Double
}

/// A sound to chant on the exhale. AUM is split into A, U and M in the traditional 1:1:2 proportion,
/// so the final M (humming) gets half of the exhale.
enum ChantSound: String, Codable, CaseIterable, Identifiable {
    case none, a, u, m, aum, hum

    var id: String { rawValue }

    var label: String {
        switch self {
        case .none: return "No chant"
        case .a: return "A (ahh)"
        case .u: return "U (ooo)"
        case .m: return "M (hum)"
        case .aum: return "AUM / OM"
        case .hum: return "Humming bee"
        }
    }

    var parts: [ChantPart] {
        switch self {
        case .none: return []
        case .a: return [ChantPart(label: "A", weight: 1)]
        case .u: return [ChantPart(label: "U", weight: 1)]
        case .m, .hum: return [ChantPart(label: "M", weight: 1)]
        case .aum:
            return [ChantPart(label: "A", weight: 0.25), ChantPart(label: "U", weight: 0.25), ChantPart(label: "M", weight: 0.5)]
        }
    }

    /// Index of the part being chanted at `fraction` (0...1) of the exhale, or -1 if there is no chant.
    func partAt(_ fraction: Double) -> Int {
        let list = parts
        if list.isEmpty { return -1 }
        var acc = 0.0
        for (index, part) in list.enumerated() {
            acc += part.weight
            if fraction < acc { return index }
        }
        return list.count - 1
    }
}

/// Seconds for each phase of one breath. A zero means the phase is skipped.
struct BreathPattern: Equatable {
    var inhale: Double
    var holdIn: Double
    var exhale: Double
    var holdOut: Double

    var cycleSeconds: Double { inhale + holdIn + exhale + holdOut }

    func clamped() -> BreathPattern {
        BreathPattern(
            inhale: inhale.clamped(1...60),
            holdIn: holdIn.clamped(0...120),
            exhale: exhale.clamped(1...60),
            holdOut: holdOut.clamped(0...120)
        )
    }
}

/// Settings for rapid "pumping" practices (Kapalabhati, Bhastrika): `strokes` quick breaths at
/// `strokesPerMinute`, then one slow finishing breath.
struct PumpSettings: Equatable {
    var strokes: Int
    var strokesPerMinute: Int
    var finishInhale: Double
    var finishHold: Double
    var finishExhale: Double
}

enum Category: CaseIterable {
    case calming, balancing, energizing, cooling, chanting, custom

    var title: String {
        switch self {
        case .calming: return "Calming"
        case .balancing: return "Balancing"
        case .energizing: return "Energizing"
        case .cooling: return "Cooling"
        case .chanting: return "Chanting"
        case .custom: return "Your own"
        }
    }
}

/// How a practice turns into steps.
enum Kind {
    case rhythm, alternate, pump, meditation
}

/// What the sound-only meditation plays.
enum MeditationStyle: String, Codable, CaseIterable, Identifiable {
    case drone, bowl, aum

    var id: String { rawValue }

    var label: String {
        switch self {
        case .drone: return "Steady tone"
        case .bowl: return "Singing bowl"
        case .aum: return "AUM chanting"
        }
    }

    var blurb: String {
        switch self {
        case .drone: return "A soft, continuous tone to rest in."
        case .bowl: return "Quiet, with a bowl struck at the start, the end and at the intervals you choose."
        case .aum: return "A sung AUM again and again, with a short pause between."
        }
    }
}

/// Settings for the sound-only meditation. There are no breaths to follow.
struct MeditationSettings: Codable, Equatable {
    static let minMinutes = 1
    static let maxMinutes = 180

    var minutes = 10
    var style = MeditationStyle.drone
    /// Ring a bowl every this many minutes. 0 means no interval bells.
    var bellEveryMinutes = 0
    /// A bowl at the very start and a closing bowl at the end.
    var bells = true
    /// Sleep mode: no bowls, a very slow fade over the last minutes, then everything stops by itself.
    var sleep = false

    init(minutes: Int = 10, style: MeditationStyle = .drone, bellEveryMinutes: Int = 0, bells: Bool = true, sleep: Bool = false) {
        self.minutes = minutes
        self.style = style
        self.bellEveryMinutes = bellEveryMinutes
        self.bells = bells
        self.sleep = sleep
    }

    /// Settings saved before sleep mode existed have no "sleep" key.
    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        minutes = try c.decodeIfPresent(Int.self, forKey: .minutes) ?? 10
        style = try c.decodeIfPresent(MeditationStyle.self, forKey: .style) ?? .drone
        bellEveryMinutes = try c.decodeIfPresent(Int.self, forKey: .bellEveryMinutes) ?? 0
        bells = try c.decodeIfPresent(Bool.self, forKey: .bells) ?? true
        sleep = try c.decodeIfPresent(Bool.self, forKey: .sleep) ?? false
    }

    private enum CodingKeys: String, CodingKey {
        case minutes, style, bellEveryMinutes, bells, sleep
    }

    func sanitized() -> MeditationSettings {
        var copy = self
        copy.minutes = minutes.clamped(Self.minMinutes...Self.maxMinutes)
        copy.bellEveryMinutes = bellEveryMinutes.clamped(0...Self.maxMinutes)
        return copy
    }
}

struct Practice: Identifiable, Equatable {
    let id: String
    let name: String
    let sanskrit: String
    let category: Category
    let summary: String
    let howTo: [String]
    let caution: String?
    let kind: Kind
    let pattern: BreathPattern
    let rounds: Int
    var chant: ChantSound = .none
    /// For `alternate`: the nostril used for each inhale in one round. The exhale uses the other one.
    var inhaleSides: [Nostril] = [.both]
    var pump: PumpSettings?
    var restSeconds: Double = 0
}
