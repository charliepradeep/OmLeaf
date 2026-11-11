import SwiftUI

/// Pick a pitch exactly: type it, nudge it by 1 Hz, tap a preset, or drag the slider (0 to 1000 Hz).
struct PitchControl: View {
    @Binding var hz: Double
    @State private var text = ""
    @FocusState private var focused: Bool

    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            HStack(spacing: 12) {
                Button { nudge(-1) } label: { Image(systemName: "minus.circle.fill").font(.title2) }
                    .buttonStyle(.borderless)
                    .disabled(hz <= FrequencyPresets.minHz)
                    .accessibilityLabel("1 Hz lower")
                TextField("Hz", text: $text)
                    .keyboardType(.decimalPad)
                    .multilineTextAlignment(.center)
                    .font(.title3.monospacedDigit())
                    .textFieldStyle(.roundedBorder)
                    .focused($focused)
                    .accessibilityLabel("Pitch in hertz, 0 to 1000")
                Text("Hz").foregroundStyle(.secondary)
                Button { nudge(1) } label: { Image(systemName: "plus.circle.fill").font(.title2) }
                    .buttonStyle(.borderless)
                    .disabled(hz >= FrequencyPresets.maxHz)
                    .accessibilityLabel("1 Hz higher")
            }
            Text(noteText).font(.caption).foregroundStyle(.secondary)
            ScrollView(.horizontal, showsIndicators: false) {
                HStack {
                    ForEach(FrequencyPresets.all) { preset in
                        let selected = abs(hz - preset.hz) < 0.05
                        Button("\(preset.name) · \(Pitch.format(preset.hz))") { hz = preset.hz }
                            .buttonStyle(.bordered)
                            .tint(selected ? .omTeal : .secondary)
                    }
                }
            }
            Slider(value: rounded, in: FrequencyPresets.minHz...FrequencyPresets.maxHz)
        }
        .padding(.vertical, 4)
        .onAppear { text = Pitch.format(hz) }
        .onChange(of: text) { _, new in typed(new) }
        .onChange(of: hz) { _, new in follow(new) }
        .onChange(of: focused) { _, now in
            if !now { text = Pitch.format(hz) }
        }
        .toolbar {
            ToolbarItemGroup(placement: .keyboard) {
                Spacer()
                Button("Done") { focused = false }
            }
        }
    }

    private var noteText: String {
        if let note = Pitch.noteName(hz) { return "Nearest note: \(note)" }
        return "Too low to be a musical note; most phone speakers play very little below 40 Hz."
    }

    /// The slider moves in steps of 0.1 Hz.
    private var rounded: Binding<Double> {
        Binding(get: { hz }, set: { hz = ($0 * 10).rounded() / 10 })
    }

    private func nudge(_ by: Double) {
        hz = (hz + by).clamped(FrequencyPresets.minHz...FrequencyPresets.maxHz)
    }

    /// Only digits and one separator are allowed while typing.
    private func typed(_ raw: String) {
        let cleaned = String(raw.filter { "0123456789.,".contains($0) }.prefix(7))
        if cleaned != raw {
            text = cleaned
            return
        }
        if let value = FrequencyPresets.parse(cleaned), abs(value - hz) > 0.0005 { hz = value }
    }

    /// Follow changes made with the slider, the presets or the buttons, but never overwrite what is being typed.
    private func follow(_ new: Double) {
        if let value = FrequencyPresets.parse(text), abs(value - new) <= 0.0005 { return }
        text = Pitch.format(new)
    }
}
