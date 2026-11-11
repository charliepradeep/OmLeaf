import SwiftUI

struct PracticesView: View {
    @EnvironmentObject private var store: AppStore

    var body: some View {
        List {
            Section {
                NavigationLink(value: "meditate") {
                    VStack(alignment: .leading, spacing: 3) {
                        Label("Sound meditation", systemImage: "waveform").font(.headline)
                        Text("Just listen: a steady tone, a singing bowl or AUM chanting. No breaths to follow.")
                            .font(.subheadline).foregroundStyle(.secondary)
                    }
                    .padding(.vertical, 2)
                }
            }
            ForEach(Practices.grouped(), id: \.category) { group in
                Section(group.category.title) {
                    ForEach(group.practices) { practice in
                        NavigationLink(value: practice.id) { PracticeRow(practice: practice) }
                    }
                }
            }
            Section("Your own") {
                ForEach(store.custom) { item in
                    NavigationLink(value: item.id) { PracticeRow(practice: item.practice) }
                }
                NavigationLink(value: "new") {
                    Label("Create your own pattern", systemImage: "plus.circle.fill")
                        .font(.headline)
                }
            }
        }
        .navigationTitle("OmLeaf")
        .navigationDestination(for: String.self) { id in
            if id == "meditate" {
                MeditationView()
            } else {
                SetupView(practiceId: id)
            }
        }
    }
}

private struct PracticeRow: View {
    let practice: Practice

    var body: some View {
        VStack(alignment: .leading, spacing: 3) {
            Text(practice.name).font(.headline)
            Text(practice.sanskrit).font(.subheadline).foregroundStyle(.secondary)
            Text(summary).font(.caption).foregroundStyle(.secondary)
        }
        .padding(.vertical, 2)
    }

    private var summary: String {
        let total = Planner.build(Planner.request(for: practice)).totalMs
        return "\(practice.rounds) rounds · \(formatDuration(ms: total))"
    }
}
