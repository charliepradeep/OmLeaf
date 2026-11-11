import SwiftUI

/// What the session screen needs to run.
struct SessionSpec: Identifiable {
    let id = UUID()
    let title: String
    let request: PlanRequest
    let sound: SoundSettings
    let haptics: Bool
}

struct SetupView: View {
    @EnvironmentObject private var store: AppStore
    let practiceId: String

    var body: some View {
        if practiceId == "new" {
            SetupForm(practice: Self.blankCustom)
        } else if let practice = store.practice(withId: practiceId) {
            SetupForm(practice: practice)
        } else {
            Text("This practice is no longer available.").foregroundStyle(.secondary)
        }
    }

    private static let blankCustom = Practice(
        id: "new", name: "My practice", sanskrit: "Your own", category: .custom, summary: "", howTo: [], caution: nil,
        kind: .rhythm, pattern: BreathPattern(inhale: 4, holdIn: 0, exhale: 6, holdOut: 0), rounds: 10
    )
}

private struct SetupForm: View {
    @EnvironmentObject private var store: AppStore
    @Environment(\.dismiss) private var dismiss

    let practice: Practice
    @State private var name: String
    @State private var rounds: Int
    @State private var pattern: BreathPattern
    @State private var chant: ChantSound
    @State private var rest: Double
    @State private var pump: PumpSettings
    @State private var session: SessionSpec?

    init(practice: Practice) {
        self.practice = practice
        _name = State(initialValue: practice.name)
        _rounds = State(initialValue: practice.rounds)
        _pattern = State(initialValue: practice.pattern)
        _chant = State(initialValue: practice.chant)
        _rest = State(initialValue: practice.restSeconds)
        _pump = State(initialValue: practice.pump ?? PumpSettings(
            strokes: 30, strokesPerMinute: 60, finishInhale: 4, finishHold: 8, finishExhale: 6
        ))
    }

    private var isCustom: Bool { practice.category == .custom }

    private var request: PlanRequest {
        PlanRequest(
            kind: practice.kind, pattern: pattern, rounds: rounds, chant: chant, inhaleSides: practice.inhaleSides,
            pump: practice.kind == .pump ? pump : nil, restSeconds: rest
        )
    }

    var body: some View {
        Form {
            headerSection
            if !practice.howTo.isEmpty {
                Section("How to") {
                    ForEach(Array(practice.howTo.enumerated()), id: \.offset) { index, line in
                        Text("\(index + 1). \(line)").font(.subheadline)
                    }
                }
            }
            if let caution = practice.caution {
                Section {
                    Label(caution, systemImage: "exclamationmark.triangle.fill")
                        .font(.subheadline)
                        .foregroundStyle(Color.omSaffron)
                }
            }
            patternSection
            if practice.kind != .pump { chantSection }
            soundSection
            actionSection
        }
        .navigationBarTitleDisplayMode(.inline)
        .fullScreenCover(item: $session) { spec in
            SessionView(spec: spec) { session = nil }
        }
    }

    private var headerSection: some View {
        Section {
            if isCustom {
                TextField("Name", text: $name)
            } else {
                VStack(alignment: .leading, spacing: 4) {
                    Text(practice.name).font(.title2.bold())
                    Text(practice.sanskrit).foregroundStyle(.secondary)
                    Text(practice.summary).padding(.top, 4)
                }
            }
        }
    }

    private var patternSection: some View {
        Section("Pattern") {
            Stepper("Rounds: \(rounds)", value: $rounds, in: 1...Planner.maxRounds)
            if practice.kind == .pump {
                Stepper("Pumps per round: \(pump.strokes)", value: $pump.strokes, in: 10...100, step: 5)
                Stepper("Pace: \(pump.strokesPerMinute) /min", value: $pump.strokesPerMinute, in: 20...140, step: 5)
                SecondsStepper(title: "Slow breath in", value: $pump.finishInhale, range: 1...30, step: 0.5)
                SecondsStepper(title: "Hold", value: $pump.finishHold, range: 0...60, step: 1)
                SecondsStepper(title: "Breath out", value: $pump.finishExhale, range: 1...30, step: 0.5)
            } else {
                SecondsStepper(title: "Breathe in", value: $pattern.inhale, range: 1...60, step: 0.5)
                SecondsStepper(title: "Hold after in", value: $pattern.holdIn, range: 0...120, step: 0.5)
                SecondsStepper(title: "Breathe out", value: $pattern.exhale, range: 1...60, step: 0.5)
                SecondsStepper(title: "Hold after out", value: $pattern.holdOut, range: 0...120, step: 0.5)
            }
            SecondsStepper(title: "Rest between rounds", value: $rest, range: 0...300, step: 5)
        }
    }

    private var chantSection: some View {
        Section {
            Picker("Chant on the exhale", selection: $chant) {
                ForEach(ChantSound.allCases) { Text($0.label).tag($0) }
            }
        } footer: {
            if chant == .aum {
                Text("A and U each take a quarter of the exhale and M takes the other half.")
            }
        }
    }

    private var soundSection: some View {
        Section("Sound") {
            PitchControl(hz: Binding(
                get: { store.settings.sound.frequencyHz },
                set: { newValue in
                    var sound = store.settings.sound
                    sound.frequencyHz = newValue
                    store.saveSound(sound)
                }
            ))
            Toggle("Phase chimes", isOn: soundBinding(\.chimes))
            Toggle("Guide tone while chanting", isOn: soundBinding(\.guideTone))
            Toggle("Soft drone", isOn: soundBinding(\.drone))
        }
    }

    private var actionSection: some View {
        Section {
            Text("About \(formatDuration(ms: Planner.build(request).totalMs))")
                .foregroundStyle(.secondary)
            Button {
                session = SessionSpec(
                    title: name.isEmpty ? practice.name : name, request: request,
                    sound: store.settings.sound, haptics: store.settings.haptics
                )
            } label: {
                Label("Begin", systemImage: "play.fill").frame(maxWidth: .infinity)
            }
            .buttonStyle(.borderedProminent)
            if isCustom {
                Button("Save to my practices") { saveCustom() }
                if practice.id != "new" {
                    Button("Delete", role: .destructive) {
                        store.deleteCustom(id: practice.id)
                        dismiss()
                    }
                }
            }
        }
    }

    private func soundBinding(_ keyPath: WritableKeyPath<SoundSettings, Bool>) -> Binding<Bool> {
        Binding(
            get: { store.settings.sound[keyPath: keyPath] },
            set: { newValue in
                var sound = store.settings.sound
                sound[keyPath: keyPath] = newValue
                store.saveSound(sound)
            }
        )
    }

    private func saveCustom() {
        let id = practice.id == "new" ? UUID().uuidString : practice.id
        store.saveCustom(CustomPractice(
            id: id, name: name.isEmpty ? "My practice" : name, inhale: pattern.inhale, holdIn: pattern.holdIn,
            exhale: pattern.exhale, holdOut: pattern.holdOut, rounds: rounds, chant: chant
        ))
        dismiss()
    }
}

private struct SecondsStepper: View {
    let title: String
    @Binding var value: Double
    let range: ClosedRange<Double>
    let step: Double

    var body: some View {
        Stepper(value: $value, in: range, step: step) {
            HStack {
                Text(title)
                Spacer()
                Text(formatSeconds(value)).foregroundStyle(.secondary)
            }
        }
    }
}
