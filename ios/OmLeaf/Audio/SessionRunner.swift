import AVFoundation
import os

/// Counts the frames the audio engine has asked for. Written on the audio thread, read on the main thread.
final class FrameClock: @unchecked Sendable {
    var frames = 0
}

/// Plays one session. The audio is made on the fly and the audio clock is also the session clock,
/// so what you hear and what the screen shows cannot drift apart. When every sound is switched off it
/// still plays silence, which keeps the timing identical. If the audio engine cannot start, the
/// wall clock is used so the guide still works.
@MainActor
final class SessionRunner {
    private static let log = Logger(subsystem: "com.charrlie.omleaf", category: "audio")

    private let plan: Plan
    private let synth: PlanSynth
    private let engine = AVAudioEngine()
    private let clock = FrameClock()
    private var interruptionObserver: NSObjectProtocol?
    private var wallStart: Date?
    private var wallElapsed = 0.0
    private var usesWallClock = false

    private(set) var paused = false
    private(set) var stopped = false
    /// Called when the system takes the audio away (a call, an alarm, Siri).
    var onInterruption: (() -> Void)?

    init(plan: Plan, sound: SoundSettings) {
        self.plan = plan
        self.synth = PlanSynth(plan: plan, settings: sound)
    }

    func start() {
        let session = AVAudioSession.sharedInstance()
        do {
            try session.setCategory(.playback, mode: .default)
            try session.setActive(true)
            try startEngine()
        } catch {
            Self.log.error("Audio could not start: \(error.localizedDescription, privacy: .public)")
            usesWallClock = true
            wallStart = Date()
        }
        interruptionObserver = NotificationCenter.default.addObserver(
            forName: AVAudioSession.interruptionNotification, object: nil, queue: .main
        ) { [weak self] note in
            let raw = note.userInfo?[AVAudioSessionInterruptionTypeKey] as? UInt
            guard raw == AVAudioSession.InterruptionType.began.rawValue else { return }
            Task { @MainActor in
                self?.pause()
                self?.onInterruption?()
            }
        }
    }

    private func startEngine() throws {
        guard let format = AVAudioFormat(standardFormatWithSampleRate: Double(synth.sampleRate), channels: 1) else {
            throw NSError(domain: "OmLeaf", code: 1)
        }
        let node = Self.makeNode(synth: synth, clock: clock, format: format)
        engine.attach(node)
        engine.connect(node, to: engine.mainMixerNode, format: format)
        engine.prepare()
        try engine.start()
    }

    /// Built outside the main actor because the render block runs on the real-time audio thread.
    private nonisolated static func makeNode(synth: PlanSynth, clock: FrameClock, format: AVAudioFormat) -> AVAudioSourceNode {
        AVAudioSourceNode(format: format) { _, _, frameCount, audioBufferList -> OSStatus in
            let buffers = UnsafeMutableAudioBufferListPointer(audioBufferList)
            guard let first = buffers.first, let data = first.mData else { return noErr }
            let pointer = data.assumingMemoryBound(to: Float.self)
            let start = clock.frames
            synth.render(startFrame: start, into: pointer, count: Int(frameCount))
            clock.frames = start + Int(frameCount)
            return noErr
        }
    }

    /// Milliseconds of the session played so far, never more than the plan's length.
    func elapsedMs() -> Int {
        let seconds: Double
        if usesWallClock {
            seconds = wallElapsed + (wallStart.map { Date().timeIntervalSince($0) } ?? 0)
        } else {
            seconds = Double(clock.frames) / Double(synth.sampleRate) - engine.outputNode.presentationLatency
        }
        return Int(seconds * 1000).clamped(0...plan.totalMs)
    }

    func pause() {
        if paused || stopped { return }
        paused = true
        if usesWallClock {
            wallElapsed += wallStart.map { Date().timeIntervalSince($0) } ?? 0
            wallStart = nil
        } else {
            engine.pause()
        }
    }

    func resume() {
        if !paused || stopped { return }
        paused = false
        if usesWallClock {
            wallStart = Date()
        } else {
            try? AVAudioSession.sharedInstance().setActive(true)
            try? engine.start()
        }
    }

    func stop() {
        if stopped { return }
        stopped = true
        engine.stop()
        if let observer = interruptionObserver { NotificationCenter.default.removeObserver(observer) }
        interruptionObserver = nil
        try? AVAudioSession.sharedInstance().setActive(false, options: .notifyOthersOnDeactivation)
    }
}
