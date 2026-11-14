import SwiftUI
import UIKit

private let minSavedSeconds = 30

struct SessionView: View {
    @EnvironmentObject private var store: AppStore
    @Environment(\.scenePhase) private var scenePhase

    let spec: SessionSpec
    let onClose: () -> Void

    private let plan: Plan
    @State private var runner: SessionRunner?
    @State private var countdown = 3
    @State private var started = false
    @State private var paused = false
    @State private var finished = false
    @State private var elapsed = 0
    @State private var confirmEnd = false
    @State private var saved = false

    private let ticker = Timer.publish(every: 1.0 / 30.0, on: .main, in: .common).autoconnect()

    init(spec: SessionSpec, onClose: @escaping () -> Void) {
        self.spec = spec
        self.onClose = onClose
        self.plan = Planner.build(spec.request)
    }

    private var stepIndex: Int { plan.indexAt(ms: elapsed) }

    var body: some View {
        ZStack {
            Color(.systemBackground).ignoresSafeArea()
            if finished {
                finishedView
            } else if !started {
                getReadyView
            } else {
                RunningView(
                    title: spec.title, plan: plan, step: plan.steps[stepIndex], progress: plan.progressAt(ms: elapsed),
                    elapsed: elapsed, paused: paused, meditation: plan.style,
                    onPauseResume: togglePause, onStop: { confirmEnd = true }
                )
            }
        }
        .task { await countDown() }
        .onReceive(ticker) { _ in tick() }
        .onChange(of: scenePhase) { _, phase in
            if phase == .background && started && !finished && !spec.keepPlaying { pause() }
        }
        .onChange(of: stepIndex) { _, _ in
            let step = plan.steps[stepIndex]
            // A meditation is silent to the touch except for the bowls.
            if started && !finished && spec.haptics && step.phase != .stroke && (plan.style == nil || step.bell) {
                UIImpactFeedbackGenerator(style: .light).impactOccurred()
            }
        }
        .onAppear { UIApplication.shared.isIdleTimerDisabled = spec.request.meditation?.sleep != true }
        .onDisappear {
            UIApplication.shared.isIdleTimerDisabled = false
            runner?.stop()
            LockScreen.clear()
        }
        .confirmationDialog("End this session?", isPresented: $confirmEnd, titleVisibility: .visible) {
            Button("End", role: .destructive) {
                saveIfWorthIt(seconds: elapsed / 1000, rounds: plan.style != nil ? 0 : plan.steps[stepIndex].round)
                onClose()
            }
            Button("Keep going", role: .cancel) {}
        } message: {
            Text(elapsed / 1000 >= minSavedSeconds
                 ? "Your practice so far will be saved to Progress."
                 : "Sessions shorter than 30 seconds are not saved.")
        }
    }

    private var getReadyView: some View {
        VStack(spacing: 8) {
            Text("Get comfortable").font(.title2.weight(.semibold))
            Text("\(countdown)")
                .font(.system(size: 88, weight: .light, design: .rounded))
                .foregroundStyle(Color.omTeal)
            Text("Sit tall, relax the shoulders, close your eyes if you like.")
                .foregroundStyle(.secondary)
                .multilineTextAlignment(.center)
                .padding(.horizontal, 32)
        }
    }

    private var finishedView: some View {
        VStack(spacing: 12) {
            Text("Well done").font(.largeTitle.bold())
            Text(plan.style != nil
                 ? "Sound meditation · \(formatDuration(ms: plan.totalMs))"
                 : "\(plan.rounds) rounds · \(formatDuration(ms: plan.totalMs))")
                .font(.headline).foregroundStyle(.secondary)
            Text(plan.style != nil
                 ? "Stay with the quiet for a moment before you get up."
                 : "Breathe normally and sit quietly for a moment before you get up.")
                .foregroundStyle(.secondary)
                .multilineTextAlignment(.center)
                .padding(.vertical, 16)
            Button("Done") { onClose() }.buttonStyle(.borderedProminent)
        }
        .padding(32)
    }

    /// Three quiet seconds to get ready, then the audio starts.
    private func countDown() async {
        while countdown > 0 {
            try? await Task.sleep(nanoseconds: 1_000_000_000)
            if Task.isCancelled { return }
            countdown -= 1
        }
        let newRunner = SessionRunner(plan: plan, sound: spec.sound)
        newRunner.onInterruption = { paused = true }
        newRunner.start()
        runner = newRunner
        started = true
        if spec.keepPlaying {
            LockScreen.show(
                title: spec.title, durationMs: plan.totalMs,
                onPlay: { if runner?.paused == true { togglePause() } },
                onPause: { if runner?.paused == false { togglePause() } }
            )
        }
    }

    /// The animation is driven from the audio clock, so sound and screen cannot drift apart.
    private func tick() {
        guard started, !finished, let runner else { return }
        elapsed = runner.elapsedMs()
        paused = runner.paused
        if spec.keepPlaying { LockScreen.update(elapsedMs: elapsed, paused: paused) }
        if elapsed >= plan.totalMs {
            finished = true
            saveIfWorthIt(seconds: plan.totalMs / 1000, rounds: plan.style != nil ? 0 : plan.rounds)
            Task {
                // Let the last chime ring, then release the audio.
                try? await Task.sleep(nanoseconds: 2_500_000_000)
                runner.stop()
            }
        }
    }

    private func pause() {
        runner?.pause()
        paused = true
    }

    private func togglePause() {
        guard let runner else { return }
        if runner.paused {
            runner.resume()
            paused = false
        } else {
            pause()
        }
    }

    private func saveIfWorthIt(seconds: Int, rounds: Int) {
        if saved || seconds < minSavedSeconds { return }
        saved = true
        store.addSession(SessionRecord(epochDay: EpochDay.of(Date()), practice: spec.title, seconds: seconds, rounds: rounds))
    }
}

private struct RunningView: View {
    let title: String
    let plan: Plan
    let step: Step
    let progress: Double
    let elapsed: Int
    let paused: Bool
    let meditation: MeditationStyle?
    let onPauseResume: () -> Void
    let onStop: () -> Void

    var body: some View {
        VStack(spacing: 6) {
            Text(title).font(.headline)
            Text(meditation?.label ?? "Round \(step.round) of \(plan.rounds)").font(.subheadline).foregroundStyle(.secondary)
            Spacer(minLength: 0)
            breathCircle
            Spacer(minLength: 0)
            Text(step.phase.label).font(.largeTitle.weight(.semibold))
            if step.nostril != .both {
                Text(step.nostril.label).font(.headline).foregroundStyle(Color.omTeal)
            }
            if (step.phase == .exhale || step.phase == .listen) && step.chant != .none {
                ChantLetters(chant: step.chant, progress: progress)
            }
            if meditation == nil {
                Text(nextText).font(.subheadline).foregroundStyle(.secondary).padding(.top, 4)
            }
            controls
        }
        .padding(.horizontal, 24)
        .padding(.vertical, 16)
    }

    private var phaseColor: Color {
        switch step.phase {
        case .inhale: return .omTeal
        case .holdIn, .holdOut: return .omViolet
        case .exhale, .stroke: return .omSaffron
        case .rest: return .secondary
        case .listen: return .omTeal
        }
    }

    private var breathCircle: some View {
        // In a meditation the circle only glows very slowly; it must not suggest a breathing pace.
        let scale = meditation != nil
            ? 0.8 + 0.03 * sin(Double(elapsed) / 1000 * 2 * Double.pi / 20)
            : circleScale(step: step, progress: progress)
        let remaining = max(0, Int((Double(step.endMs - elapsed) / 1000).rounded(.up)))
        return ZStack {
            Circle().stroke(Color.secondary.opacity(0.3), lineWidth: 2)
            Circle().fill(phaseColor.opacity(0.16)).scaleEffect(scale)
            Circle().fill(phaseColor.opacity(0.40)).scaleEffect(scale * 0.8)
            Circle().fill(phaseColor.opacity(0.9)).scaleEffect(scale * 0.55)
            Text(centerText(remaining: remaining))
                .font(.system(size: meditation != nil ? 44 : 56, weight: .light, design: .rounded))
                .foregroundStyle(.white)
        }
        .frame(maxWidth: 300, maxHeight: 300)
        .aspectRatio(1, contentMode: .fit)
    }

    private func centerText(remaining: Int) -> String {
        if meditation != nil { return formatDuration(ms: max(0, plan.totalMs - elapsed)) }
        return step.phase == .stroke ? "\(step.stroke)/\(step.strokeTotal)" : "\(remaining)"
    }

    private var nextText: String {
        guard let next = plan.steps[safe: step.index + 1] else { return "Last breath" }
        let side = next.nostril == .both ? "" : " · \(next.nostril.label)"
        return "Next: \(next.phase.label)\(side)"
    }

    private var controls: some View {
        VStack(spacing: 10) {
            ProgressView(value: Double(elapsed), total: Double(max(plan.totalMs, 1)))
            Text("\(formatDuration(ms: max(0, plan.totalMs - elapsed))) left")
                .font(.caption).foregroundStyle(.secondary)
            HStack(spacing: 28) {
                Button(action: onStop) { Image(systemName: "stop.fill").frame(width: 44, height: 44) }
                    .buttonStyle(.bordered)
                    .accessibilityLabel("End session")
                Button(action: onPauseResume) { Image(systemName: paused ? "play.fill" : "pause.fill").frame(width: 44, height: 44) }
                    .buttonStyle(.bordered)
                    .accessibilityLabel(paused ? "Resume" : "Pause")
            }
        }
        .padding(.top, 12)
    }
}

/// A, U, M shown as letters; the one being chanted now is large and bright.
private struct ChantLetters: View {
    let chant: ChantSound
    let progress: Double

    var body: some View {
        let current = chant.partAt(progress)
        HStack(spacing: 14) {
            ForEach(Array(chant.parts.enumerated()), id: \.offset) { index, part in
                Text(part.label)
                    .font(.system(size: index == current ? 40 : 26, weight: index == current ? .bold : .regular))
                    .foregroundStyle(index == current ? Color.omTeal : Color.secondary)
            }
        }
    }
}

/// 0.45 when the lungs are empty, 1.0 when full, easing smoothly between.
func circleScale(step: Step, progress p: Double) -> Double {
    func ease(_ x: Double) -> Double { x * x * (3 - 2 * x) }
    switch step.phase {
    case .inhale: return 0.45 + 0.55 * ease(p)
    case .holdIn: return 1.0
    case .exhale: return 1.0 - 0.55 * ease(p)
    case .holdOut: return 0.45
    case .stroke: return 0.55 + 0.35 * (1 - p)
    case .rest: return 0.7
    case .listen: return 0.8
    }
}

private extension Array {
    subscript(safe index: Int) -> Element? {
        indices.contains(index) ? self[index] : nil
    }
}
