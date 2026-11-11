import Foundation

struct SoundSettings: Codable, Equatable {
    /// Base pitch for chimes, the chant guide tone and the drone.
    var frequencyHz = 136.1
    var chimes = true
    var guideTone = true
    var drone = false
    var volume = 0.7

    func sanitized() -> SoundSettings {
        var copy = self
        copy.frequencyHz = frequencyHz.clamped(FrequencyPresets.minHz...FrequencyPresets.maxHz)
        copy.volume = volume.clamped(0...1)
        return copy
    }
}

struct FrequencyPreset: Identifiable {
    let name: String
    let hz: Double
    var id: String { name }
}

/// Popular pitches for chanting. They are tuning choices, not health claims.
enum FrequencyPresets {
    /// 0 is allowed so any value can be typed. Below about 40 Hz a phone speaker plays almost nothing.
    static let minHz = 0.0
    /// The top of the usual chanting and "solfeggio" range.
    static let maxHz = 1000.0

    /// Reads a typed pitch such as "136.1" or "136,1". Returns nil if it is not a number.
    static func parse(_ text: String) -> Double? {
        let cleaned = text.trimmingCharacters(in: .whitespaces).replacingOccurrences(of: ",", with: ".")
        guard let value = Double(cleaned), value.isFinite else { return nil }
        return value.clamped(minHz...maxHz)
    }

    static let all = [
        FrequencyPreset(name: "OM tuning", hz: 136.1),
        FrequencyPreset(name: "Low drone", hz: 110.0),
        FrequencyPreset(name: "Sa (C4)", hz: 261.6),
        FrequencyPreset(name: "432 Hz", hz: 432.0),
        FrequencyPreset(name: "528 Hz", hz: 528.0),
        FrequencyPreset(name: "963 Hz", hz: 963.0),
    ]
}

/// Names the nearest musical note for a pitch, which helps when tuning an instrument or your voice.
enum Pitch {
    private static let names = ["C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B"]

    /// For example 440 gives "A4" and 136.1 gives "C#3 -31¢". Nil below 16 Hz, where there is no musical note.
    static func noteName(_ hz: Double) -> String? {
        if hz < 16 { return nil }
        let midi = 12 * log2(hz / 440) + 69
        let nearest = Int(midi.rounded())
        let cents = Int(((midi - Double(nearest)) * 100).rounded())
        let name = names[((nearest % 12) + 12) % 12] + String(Int((Double(nearest) / 12).rounded(.down)) - 1)
        if cents == 0 { return name }
        return cents > 0 ? "\(name) +\(cents)¢" : "\(name) -\(abs(cents))¢"
    }

    /// "136.1" for a pitch; whole numbers have no decimal.
    static func format(_ hz: Double) -> String {
        hz == hz.rounded() ? String(Int(hz)) : String(format: "%.1f", hz)
    }
}

private struct ChimeVoice {
    let hz: Double
    let gain: Double
    let tau: Double
}

private struct Brightness {
    let second: Double
    let third: Double
}

/// Turns a `Plan` into audio, one sample at a time, from the position in the plan.
/// Pure maths with no audio classes, so it can be tested on a computer.
final class PlanSynth: @unchecked Sendable {
    private static let chimeWindowMs = 1800.0
    private static let bellWindowMs = 9000.0
    private static let maxPartialHz = 10_000.0
    private static let fadeFraction = 0.04

    let sampleRate: Int
    let totalFrames: Int
    /// Silence after the last step so the final chime can ring out.
    let tailFrames: Int
    private let plan: Plan
    private let settings: SoundSettings
    private let base: Double
    private let style: MeditationStyle?
    private let chimeWindow: Double

    init(plan: Plan, settings: SoundSettings, sampleRate: Int = 22_050) {
        self.plan = plan
        self.settings = settings.sanitized()
        self.base = self.settings.frequencyHz
        self.style = plan.style
        self.chimeWindow = plan.style != nil ? Self.bellWindowMs : Self.chimeWindowMs
        self.sampleRate = sampleRate
        self.totalFrames = plan.totalMs * sampleRate / 1000
        self.tailFrames = sampleRate * 2
    }

    /// Returns `count` mono samples starting at `startFrame`.
    func render(startFrame: Int, count: Int) -> [Float] {
        var out = [Float](repeating: 0, count: count)
        out.withUnsafeMutableBufferPointer { buffer in
            if let base = buffer.baseAddress { render(startFrame: startFrame, into: base, count: count) }
        }
        return out
    }

    /// Fills `out` with `count` mono samples starting at `startFrame`.
    func render(startFrame: Int, into out: UnsafeMutablePointer<Float>, count: Int) {
        let steps = plan.steps
        if steps.isEmpty {
            for i in 0..<count { out[i] = 0 }
            return
        }
        let volume = settings.volume
        var idx = plan.indexAt(ms: startFrame * 1000 / sampleRate)
        for i in 0..<count {
            let frame = startFrame + i
            let tSec = Double(frame) / Double(sampleRate)
            let tMs = tSec * 1000.0
            while idx < steps.count - 1 && Double(steps[idx + 1].startMs) <= tMs { idx += 1 }
            let inSession = frame < totalFrames
            var mix = 0.0
            // A meditation brings its own sounds and ignores the breath-guide switches.
            if settings.chimes || style != nil {
                var j = idx
                while j >= 0 && tMs - Double(steps[j].startMs) < chimeWindow {
                    mix += chime(steps[j], dt: tSec - Double(steps[j].startMs) / 1000.0, t: tSec)
                    j -= 1
                }
            }
            if inSession {
                let fade = fadeAt(tMs)
                if settings.guideTone || style != nil { mix += guide(steps[idx], tMs: tMs, t: tSec) * fade }
                if style == .drone {
                    mix += pad(t: tSec) * fade
                } else if style == nil && settings.drone {
                    mix += drone(t: tSec)
                }
            }
            let value = tanh(mix * (0.6 + volume)) * max(volume, 0) * 0.95
            out[i] = Float(value)
        }
    }

    private func voice(for phase: Phase) -> ChimeVoice {
        switch phase {
        case .inhale: return ChimeVoice(hz: base * 2.0, gain: 0.32, tau: 0.45)
        case .holdIn: return ChimeVoice(hz: base * 1.5, gain: 0.26, tau: 0.40)
        case .exhale: return ChimeVoice(hz: base, gain: 0.32, tau: 0.50)
        case .holdOut: return ChimeVoice(hz: base * 0.75, gain: 0.24, tau: 0.40)
        case .rest: return ChimeVoice(hz: base, gain: 0.20, tau: 0.60)
        case .stroke: return ChimeVoice(hz: base * 3.0, gain: 0.30, tau: 0.035)
        case .listen: return ChimeVoice(hz: base, gain: 0, tau: 0.1)
        }
    }

    /// Fades in over three seconds and out over the plan's fade time (meditation only).
    private func fadeAt(_ tMs: Double) -> Double {
        if style == nil { return 1 }
        let fadeIn = (tMs / 3000).clamped(0...1)
        let fadeOut = plan.fadeOutMs > 0 ? ((Double(plan.totalMs) - tMs) / Double(plan.fadeOutMs)).clamped(0...1) : 1
        return min(fadeIn, fadeOut)
    }

    /// A singing-bowl strike: a fundamental with two inharmonic partials and a slow beat.
    private func bowl(dt: Double, t: Double) -> Double {
        let attack = min(dt / 0.012, 1.0)
        let hz = base * 2.0
        var v = sin(2 * Double.pi * hz * t) * exp(-dt / 3.2) + 0.6 * sin(2 * Double.pi * hz * 1.004 * t) * exp(-dt / 3.6)
        if hz * 2.76 < Self.maxPartialHz { v += 0.35 * sin(2 * Double.pi * hz * 2.76 * t) * exp(-dt / 1.8) }
        if hz * 5.4 < Self.maxPartialHz { v += 0.15 * sin(2 * Double.pi * hz * 5.4 * t) * exp(-dt / 0.9) }
        return 0.26 * attack * v
    }

    /// A fuller, slowly swelling drone for the steady-tone meditation.
    private func pad(t: Double) -> Double {
        let swell = 0.8 + 0.2 * sin(2 * Double.pi * 0.07 * t)
        let w = 2 * Double.pi * base * t
        return 0.17 * swell * (sin(w) + 0.5 * sin(1.5 * w) + 0.35 * sin(2 * w) + 0.15 * sin(3 * w)) / 2.0
    }

    private func chime(_ step: Step, dt: Double, t: Double) -> Double {
        if dt < 0 { return 0 }
        if step.phase == .listen {
            return step.bell && dt < Self.bellWindowMs / 1000 ? bowl(dt: dt, t: t) : 0
        }
        let v = voice(for: step.phase)
        let attack = min(dt / 0.008, 1.0)
        let env = attack * exp(-dt / v.tau)
        if env < 1e-4 { return 0 }
        let w = 2 * Double.pi * v.hz * t
        return v.gain * env * (sin(w) + 0.30 * sin(2 * w) + 0.12 * sin(2.76 * w))
    }

    private func guide(_ step: Step, tMs: Double, t: Double) -> Double {
        if (step.phase != .exhale && step.phase != .listen) || step.chant == .none || step.durationMs <= 0 { return 0 }
        let local = ((tMs - Double(step.startMs)) / Double(step.durationMs)).clamped(0...1)
        let durSec = Double(step.durationMs) / 1000.0
        let localSec = local * durSec
        let env = min(localSec / 0.30, 1.0) * ((durSec - localSec) / 0.40).clamped(0...1)
        if env <= 0 { return 0 }
        let tone = brightness(step.chant, local: local)
        let w = 2 * Double.pi * base * t
        var v = (sin(w) + tone.second * sin(2 * w) + tone.third * sin(3 * w)) / (1.0 + tone.second + tone.third)
        if step.chant == .hum { v *= 1.0 + 0.08 * sin(2 * Double.pi * 5.5 * t) }
        return 0.30 * env * v
    }

    private func brightnessOf(_ label: String) -> Brightness {
        switch label {
        case "A": return Brightness(second: 0.55, third: 0.30)
        case "U": return Brightness(second: 0.12, third: 0.03)
        default: return Brightness(second: 0.04, third: 0.0)
        }
    }

    /// Open vowels sound brighter than humming, so the guide tone itself shows A to U to M.
    private func brightness(_ chant: ChantSound, local: Double) -> Brightness {
        let parts = chant.parts
        if parts.isEmpty { return Brightness(second: 0, third: 0) }
        let index = chant.partAt(local)
        let current = brightnessOf(parts[index].label)
        var start = 0.0
        for k in 0..<index { start += parts[k].weight }
        let into = local - start
        if index > 0 && into < Self.fadeFraction {
            let previous = brightnessOf(parts[index - 1].label)
            let a = into / Self.fadeFraction
            return Brightness(
                second: previous.second + (current.second - previous.second) * a,
                third: previous.third + (current.third - previous.third) * a
            )
        }
        return current
    }

    private func drone(t: Double) -> Double {
        let trem = 0.85 + 0.15 * sin(2 * Double.pi * 0.2 * t)
        let w = 2 * Double.pi * base * t
        return 0.07 * trem * (sin(w) + 0.5 * sin(1.5 * w) + 0.3 * sin(2 * w)) / 1.8
    }
}
