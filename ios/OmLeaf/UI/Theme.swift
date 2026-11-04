import SwiftUI

extension Color {
    /// Calm teal, the same as the Android app.
    static let omTeal = Color(red: 0.122, green: 0.478, blue: 0.455)
    static let omSaffron = Color(red: 0.851, green: 0.541, blue: 0.169)
    static let omViolet = Color(red: 0.478, green: 0.361, blue: 0.780)
}

func formatSeconds(_ value: Double) -> String {
    value == value.rounded() ? "\(Int(value)) s" : String(format: "%.1f s", value)
}

/// "5:30" for 330 000 ms.
func formatDuration(ms: Int) -> String {
    let total = max(0, (ms + 500) / 1000)
    return String(format: "%d:%02d", total / 60, total % 60)
}

func versionName() -> String {
    Bundle.main.infoDictionary?["CFBundleShortVersionString"] as? String ?? ""
}
