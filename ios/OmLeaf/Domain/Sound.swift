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
    static let minHz = 80.0
    static let maxHz = 600.0

    static let all = [
        FrequencyPreset(name: "OM tuning", hz: 136.1),
        FrequencyPreset(name: "Low drone", hz: 110.0),
        FrequencyPreset(name: "Sa (C4)", hz: 261.6),
        FrequencyPreset(name: "432 Hz", hz: 432.0),
        FrequencyPreset(name: "528 Hz", hz: 528.0),
    ]
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
    private static let fadeFraction = 0.04

    let sampleRate: Int
    let totalFrames: Int
    /// Silence after the last step so the final chime can ring out.
    let tailFrames: Int
    private let plan: Plan
    private let settings: SoundSettings
    private let base: Double

    init(plan: Plan, settings: SoundSettings, sampleRate: Int = 22_050) {
        self.plan = plan
        self.settings = settings.sanitized()
        self.base = self.settings.frequencyHz
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
            if settings.chimes {
                var j = idx
                while j >= 0 && tMs - Double(steps[j].startMs) < Self.chimeWindowMs {
                    mix += chime(steps[j], dt: tSec - Double(steps[j].startMs) / 1000.0, t: tSec)
                    j -= 1
                }
            }
            if settings.guideTone && inSession { mix += guide(steps[idx], tMs: tMs, t: tSec) }
            if settings.drone && inSession { mix += drone(t: tSec) }
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
        }
    }

    private func chime(_ step: Step, dt: Double, t: Double) -> Double {
        if dt < 0 { return 0 }
        let v = voice(for: step.phase)
        let attack = min(dt / 0.008, 1.0)
        let env = attack * exp(-dt / v.tau)
        if env < 1e-4 { return 0 }
        let w = 2 * Double.pi * v.hz * t
        return v.gain * env * (sin(w) + 0.30 * sin(2 * w) + 0.12 * sin(2.76 * w))
    }

    private func guide(_ step: Step, tMs: Double, t: Double) -> Double {
        if step.phase != .exhale || step.chant == .none || step.durationMs <= 0 { return 0 }
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
