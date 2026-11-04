import SwiftUI

struct HistoryView: View {
    @EnvironmentObject private var store: AppStore

    var body: some View {
        let stats = store.stats
        List {
            Section {
                HStack {
                    StatTile(value: "\(stats.currentStreak)", label: "Day streak")
                    StatTile(value: "\(stats.bestStreak)", label: "Best streak")
                }
                HStack {
                    StatTile(value: "\(stats.sessions)", label: "Sessions")
                    StatTile(value: "\(stats.totalMinutes)", label: "Minutes")
                }
            }
            .listRowInsets(EdgeInsets(top: 8, leading: 12, bottom: 8, trailing: 12))
            Section("Last 7 days") {
                WeekBars(minutes: stats.last7Minutes).padding(.vertical, 8)
            }
            Section("Recent sessions") {
                if store.history.isEmpty {
                    Text("Finish a session and it appears here.").foregroundStyle(.secondary)
                }
                ForEach(Array(store.history.suffix(30).reversed().enumerated()), id: \.offset) { _, record in
                    SessionRow(record: record)
                }
            }
        }
        .navigationTitle("Progress")
    }
}

private struct StatTile: View {
    let value: String
    let label: String

    var body: some View {
        VStack(spacing: 2) {
            Text(value).font(.system(size: 34, weight: .semibold, design: .rounded)).foregroundStyle(Color.omTeal)
            Text(label).font(.caption).foregroundStyle(.secondary)
        }
        .frame(maxWidth: .infinity)
        .padding(.vertical, 8)
    }
}

private struct WeekBars: View {
    let minutes: [Int]

    var body: some View {
        let top = max(minutes.max() ?? 0, 1)
        HStack(alignment: .bottom, spacing: 10) {
            ForEach(Array(minutes.enumerated()), id: \.offset) { index, value in
                VStack(spacing: 4) {
                    Text(value > 0 ? "\(value)" : " ").font(.caption2).foregroundStyle(.secondary)
                    RoundedRectangle(cornerRadius: 5)
                        .fill(value > 0 ? Color.omTeal : Color.secondary.opacity(0.25))
                        .frame(height: max(6, 80 * CGFloat(value) / CGFloat(top)))
                    Text(dayLetter(back: minutes.count - 1 - index)).font(.caption2).foregroundStyle(.secondary)
                }
                .frame(maxWidth: .infinity)
            }
        }
        .frame(height: 120, alignment: .bottom)
    }

    private func dayLetter(back: Int) -> String {
        let date = Calendar.current.date(byAdding: .day, value: -back, to: Date()) ?? Date()
        let formatter = DateFormatter()
        formatter.dateFormat = "EEEEE"
        return formatter.string(from: date)
    }
}

private struct SessionRow: View {
    let record: SessionRecord

    var body: some View {
        HStack {
            VStack(alignment: .leading, spacing: 2) {
                Text(record.practice).font(.headline)
                Text("\(dateText) · \(record.rounds) rounds").font(.caption).foregroundStyle(.secondary)
            }
            Spacer()
            Text("\(max(1, record.seconds / 60)) min").font(.headline).foregroundStyle(Color.omTeal)
        }
    }

    private var dateText: String {
        let formatter = DateFormatter()
        formatter.dateStyle = .medium
        formatter.timeStyle = .none
        // The stored day is already in the phone's own time zone, so read it back as UTC.
        formatter.timeZone = TimeZone(identifier: "UTC")
        return formatter.string(from: Date(timeIntervalSince1970: Double(record.epochDay) * 86_400))
    }
}
