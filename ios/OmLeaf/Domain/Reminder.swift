import Foundation

/// Daily reminder time, kept as minutes after midnight (0...1439).
enum Reminder {
    static let defaultMinute = 7 * 60

    static func clamp(_ minuteOfDay: Int) -> Int {
        min(max(minuteOfDay, 0), 24 * 60 - 1)
    }

    static func label(_ minuteOfDay: Int) -> String {
        let m = clamp(minuteOfDay)
        let hour12 = m / 60 % 12 == 0 ? 12 : m / 60 % 12
        return String(format: "%d:%02d %@", hour12, m % 60, m / 60 < 12 ? "AM" : "PM")
    }
}
