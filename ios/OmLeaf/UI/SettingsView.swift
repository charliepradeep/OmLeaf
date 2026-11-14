import SwiftUI

struct SettingsView: View {
    @EnvironmentObject private var store: AppStore
    @State private var confirmClear = false
    @State private var preview: SessionRunner?
    @State private var previewTask: Task<Void, Never>?

    private var reminderTime: Binding<Date> {
        Binding(
            get: {
                let m = store.settings.reminderMinute
                return Calendar.current.date(bySettingHour: m / 60, minute: m % 60, second: 0, of: Date()) ?? Date()
            },
            set: { date in
                let c = Calendar.current.dateComponents([.hour, .minute], from: date)
                store.saveReminder(on: true, minute: (c.hour ?? 7) * 60 + (c.minute ?? 0))
            }
        )
    }

    var body: some View {
        let sound = store.settings.sound
        Form {
            Section("Sound") {
                VStack(alignment: .leading) {
                    Text("Volume")
                    Slider(value: soundBinding(\.volume), in: 0...1)
                }
                PitchControl(hz: soundBinding(\.frequencyHz))
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
                Toggle(
                    "Keep playing when the screen is locked",
                    isOn: Binding(get: { store.settings.keepPlaying }, set: { store.saveKeepPlaying($0) })
                )
            }
            Section("Reminder") {
                Toggle("Remind me every day", isOn: Binding(
                    get: { store.settings.reminderOn },
                    set: { store.saveReminder(on: $0, minute: store.settings.reminderMinute) }
                ))
                if store.settings.reminderOn {
                    DatePicker("Time", selection: reminderTime, displayedComponents: .hourAndMinute)
                }
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
