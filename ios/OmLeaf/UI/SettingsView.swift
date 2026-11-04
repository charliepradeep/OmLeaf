import SwiftUI

struct SettingsView: View {
    @EnvironmentObject private var store: AppStore
    @State private var confirmClear = false
    @State private var preview: SessionRunner?
    @State private var previewTask: Task<Void, Never>?

    var body: some View {
        let sound = store.settings.sound
        Form {
            Section("Sound") {
                VStack(alignment: .leading) {
                    Text("Volume")
                    Slider(value: soundBinding(\.volume), in: 0...1)
                }
                PitchControls(sound: soundBinding(\.frequencyHz))
                Toggle("Phase chimes", isOn: soundBinding(\.chimes))
                Toggle("Guide tone while chanting", isOn: soundBinding(\.guideTone))
                Toggle("Soft drone", isOn: soundBinding(\.drone))
                Button(preview == nil ? "Try this sound" : "Playing…") { startPreview(sound) }
                    .disabled(preview != nil)
                Text("Pitches are tuning choices to chant along with. They are not medical claims.")
                    .font(.caption).foregroundStyle(.secondary)
            }
            Section("Feel") {
                Toggle("Vibrate at each change of breath", isOn: Binding(get: { store.settings.haptics }, set: { store.saveHaptics($0) }))
            }
            Section("Data") {
                Text("Your practice history is stored only on this phone.").font(.subheadline).foregroundStyle(.secondary)
                Button("Clear practice history", role: .destructive) { confirmClear = true }
            }
            Section("About") {
                Text("OmLeaf \(versionName())").font(.headline)
                Text("No account, no ads, no analytics and no internet access. The sounds are made on your phone.")
                    .font(.subheadline).foregroundStyle(.secondary)
                Text(
                    "OmLeaf is a timer and guide, not medical advice. Do not strain. If you are pregnant or have a heart condition, " +
                    "high blood pressure, epilepsy or another medical condition, ask your doctor before practising. " +
                    "Stop if you feel dizzy."
                )
                .font(.subheadline).foregroundStyle(.secondary)
            }
        }
        .navigationTitle("Settings")
        .confirmationDialog("Clear history?", isPresented: $confirmClear, titleVisibility: .visible) {
            Button("Clear", role: .destructive) { store.clearHistory() }
        } message: {
            Text("This removes your sessions and streak from this phone. It cannot be undone.")
        }
        .onDisappear {
            previewTask?.cancel()
            preview?.stop()
            preview = nil
        }
    }

    private func soundBinding<Value>(_ keyPath: WritableKeyPath<SoundSettings, Value>) -> Binding<Value> {
        Binding(
            get: { store.settings.sound[keyPath: keyPath] },
            set: { newValue in
                var sound = store.settings.sound
                sound[keyPath: keyPath] = newValue
                store.saveSound(sound)
            }
        )
    }

    /// A short sample: one breath in, then an AUM on the exhale.
    private func startPreview(_ sound: SoundSettings) {
        let pattern = BreathPattern(inhale: 2, holdIn: 0, exhale: 5, holdOut: 0)
        let plan = Planner.build(PlanRequest(kind: .rhythm, pattern: pattern, rounds: 1, chant: .aum))
        let runner = SessionRunner(plan: plan, sound: sound)
        runner.start()
        preview = runner
        previewTask = Task {
            try? await Task.sleep(nanoseconds: 9_000_000_000)
            if !Task.isCancelled {
                runner.stop()
                preview = nil
            }
        }
    }
}

/// The pitch readout, the preset chips and the free slider (shared with the setup screen).
struct PitchControls: View {
    @Binding var sound: Double

    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            Text("Pitch: \(String(format: "%.1f", sound)) Hz")
            ScrollView(.horizontal, showsIndicators: false) {
                HStack {
                    ForEach(FrequencyPresets.all) { preset in
                        let selected = abs(sound - preset.hz) < 0.05
                        Button("\(preset.name) · \(String(format: "%.1f", preset.hz))") { sound = preset.hz }
                            .buttonStyle(.bordered)
                            .tint(selected ? .omTeal : .secondary)
                    }
                }
            }
            Slider(value: $sound, in: FrequencyPresets.minHz...FrequencyPresets.maxHz)
        }
        .padding(.vertical, 4)
    }
}
