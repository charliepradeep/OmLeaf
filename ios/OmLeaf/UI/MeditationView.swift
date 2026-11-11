import SwiftUI

private let durations = [5, 10, 15, 20, 30, 45, 60]
private let intervals = [0, 1, 2, 3, 5, 10, 15]

/// Sound only: pick how long, what to hear and which pitch. There are no breaths to follow.
struct MeditationView: View {
    @EnvironmentObject private var store: AppStore
    @State private var minutesText = ""
    @State private var session: SessionSpec?

    private var med: MeditationSettings { store.settings.meditation }

    var body: some View {
        Form {
            Section {
                VStack(alignment: .leading, spacing: 4) {
                    Text("Sound meditation").font(.title2.bold())
                    Text("Just sit and listen. There are no breaths to follow.").foregroundStyle(.secondary)
                }
            }
            timeSection
            styleSection
            bellSection
            Section("Pitch") {
                PitchControl(hz: pitchBinding)
            }
            Section {
                Text("\(med.minutes) min · fades out gently at the end").foregroundStyle(.secondary)
                Button {
                    session = SessionSpec(
                        title: "Sound meditation", request: Planner.meditationRequest(med),
                        sound: store.settings.sound, haptics: store.settings.haptics
                    )
                } label: {
                    Label("Begin", systemImage: "play.fill").frame(maxWidth: .infinity)
                }
                .buttonStyle(.borderedProminent)
            }
        }
        .navigationBarTitleDisplayMode(.inline)
        .onAppear { minutesText = String(med.minutes) }
        .fullScreenCover(item: $session) { spec in
            SessionView(spec: spec) { session = nil }
        }
    }

    private var timeSection: some View {
        Section("How long") {
            ScrollView(.horizontal, showsIndicators: false) {
                HStack {
                    ForEach(durations, id: \.self) { minutes in
                        Button("\(minutes) min") { update { $0.minutes = minutes } }
                            .buttonStyle(.bordered)
                            .tint(med.minutes == minutes ? .omTeal : .secondary)
                    }
                }
            }
            HStack {
                Text("Minutes (1 to 180)")
                Spacer()
                TextField("Minutes", text: $minutesText)
                    .keyboardType(.numberPad)
                    .multilineTextAlignment(.trailing)
                    .frame(width: 80)
                    .onChange(of: minutesText) { _, new in typedMinutes(new) }
            }
        }
    }

    private var styleSection: some View {
        Section("What you hear") {
            ForEach(MeditationStyle.allCases) { style in
                Button { update { $0.style = style } } label: {
                    HStack(alignment: .top) {
                        VStack(alignment: .leading, spacing: 2) {
                            Text(style.label).font(.headline).foregroundStyle(.primary)
                            Text(style.blurb).font(.subheadline).foregroundStyle(.secondary)
                        }
                        Spacer()
                        if med.style == style {
                            Image(systemName: "checkmark").foregroundStyle(Color.omTeal)
                        }
                    }
                }
            }
        }
    }

    private var bellSection: some View {
        Section("Bowls") {
            Toggle("Opening and closing bowl", isOn: Binding(get: { med.bells }, set: { value in update { $0.bells = value } }))
            Picker("Ring a bowl every", selection: intervalBinding) {
                ForEach(intervals, id: \.self) { Text($0 == 0 ? "Never" : "\($0) min").tag($0) }
            }
        }
    }

    private var intervalBinding: Binding<Int> {
        Binding(get: { med.bellEveryMinutes }, set: { value in update { $0.bellEveryMinutes = value } })
    }

    private var pitchBinding: Binding<Double> {
        Binding(
            get: { store.settings.sound.frequencyHz },
            set: { newValue in
                var sound = store.settings.sound
                sound.frequencyHz = newValue
                store.saveSound(sound)
            }
        )
    }

    private func update(_ change: (inout MeditationSettings) -> Void) {
        var copy = med
        change(&copy)
        store.saveMeditation(copy)
        if Int(minutesText) != store.settings.meditation.minutes { minutesText = String(store.settings.meditation.minutes) }
    }

    private func typedMinutes(_ raw: String) {
        let digits = String(raw.filter(\.isNumber).prefix(3))
        if digits != raw {
            minutesText = digits
            return
        }
        if let value = Int(digits) { update { $0.minutes = value } }
    }
}
