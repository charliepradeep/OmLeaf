import Foundation

struct SessionRecord: Codable, Equatable {
    let epochDay: Int
    let practice: String
    let seconds: Int
    let rounds: Int
}

struct Stats: Equatable {
    let sessions: Int
    let totalMinutes: Int
    let currentStreak: Int
    let bestStreak: Int
    /// Minutes practised on each of the last seven days, oldest first, ending today.
    let last7Minutes: [Int]
}

enum EpochDay {
    /// Whole days since 1 January 1970 in the phone's own time zone.
    static func of(_ date: Date, timeZone: TimeZone = .current) -> Int {
        let local = date.timeIntervalSince1970 + Double(timeZone.secondsFromGMT(for: date))
        return Int((local / 86_400).rounded(.down))
    }
}

enum StatsCalculator {
    static func compute(_ records: [SessionRecord], today: Int) -> Stats {
        let days = Set(records.map(\.epochDay))
        var best = 0
        var run = 0
        var previous: Int?
        for day in days.sorted() {
            run = (previous != nil && day == previous! + 1) ? run + 1 : 1
            best = max(best, run)
            previous = day
        }
        // A streak is still alive if you practised yesterday and today is not over yet.
        var cursor = days.contains(today) ? today : today - 1
        var current = 0
        while days.contains(cursor) {
            current += 1
            cursor -= 1
        }
        let last7 = (0..<7).reversed().map { back -> Int in
            let day = today - back
            return records.filter { $0.epochDay == day }.reduce(0) { $0 + $1.seconds } / 60
        }
        let totalSeconds = records.reduce(0) { $0 + $1.seconds }
        return Stats(
            sessions: records.count, totalMinutes: totalSeconds / 60,
            currentStreak: current, bestStreak: best, last7Minutes: last7
        )
    }
}
