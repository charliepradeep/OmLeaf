import Foundation

struct CustomPractice: Codable, Identifiable, Equatable {
    let id: String
    var name: String
    var inhale: Double
    var holdIn: Double
    var exhale: Double
    var holdOut: Double
    var rounds: Int
    var chant: ChantSound

    var practice: Practice {
        Practice(
            id: id, name: name, sanskrit: "Your own", category: .custom,
            summary: "A pattern you made yourself.", howTo: [], caution: nil, kind: .rhythm,
            pattern: BreathPattern(inhale: inhale, holdIn: holdIn, exhale: exhale, holdOut: holdOut),
            rounds: rounds, chant: chant
        )
    }
}

struct AppSettings: Codable, Equatable {
    var sound = SoundSettings()
    var haptics = true
    var acknowledgedNote = false
}

/// Everything is stored on this phone in UserDefaults. Nothing leaves the device.
@MainActor
final class AppStore: ObservableObject {
    static let maxHistory = 1000

    @Published private(set) var settings: AppSettings
    @Published private(set) var custom: [CustomPractice]
    @Published private(set) var history: [SessionRecord]

    private let defaults: UserDefaults

    private enum Key {
        static let settings = "omleaf.settings"
        static let custom = "omleaf.custom"
        static let history = "omleaf.history"
    }

    init(defaults: UserDefaults = .standard) {
        self.defaults = defaults
        var loaded = Self.load(AppSettings.self, key: Key.settings, from: defaults) ?? AppSettings()
        loaded.sound = loaded.sound.sanitized()
        self.settings = loaded
        self.custom = Self.load([CustomPractice].self, key: Key.custom, from: defaults) ?? []
        self.history = Self.load([SessionRecord].self, key: Key.history, from: defaults) ?? []
    }

    var stats: Stats {
        StatsCalculator.compute(history, today: EpochDay.of(Date()))
    }

    func practice(withId id: String) -> Practice? {
        Practices.byId(id) ?? custom.first { $0.id == id }?.practice
    }

    func saveSound(_ sound: SoundSettings) {
        settings.sound = sound.sanitized()
        save(settings, key: Key.settings)
    }

    func saveHaptics(_ on: Bool) {
        settings.haptics = on
        save(settings, key: Key.settings)
    }

    func acknowledgeNote() {
        settings.acknowledgedNote = true
        save(settings, key: Key.settings)
    }

    func saveCustom(_ item: CustomPractice) {
        custom.removeAll { $0.id == item.id }
        custom.append(item)
        save(custom, key: Key.custom)
    }

    func deleteCustom(id: String) {
        custom.removeAll { $0.id == id }
        save(custom, key: Key.custom)
    }

    func addSession(_ record: SessionRecord) {
        history.append(record)
        if history.count > Self.maxHistory { history.removeFirst(history.count - Self.maxHistory) }
        save(history, key: Key.history)
    }

    func clearHistory() {
        history = []
        defaults.removeObject(forKey: Key.history)
    }

    private func save<T: Encodable>(_ value: T, key: String) {
        if let data = try? JSONEncoder().encode(value) { defaults.set(data, forKey: key) }
    }

    private static func load<T: Decodable>(_ type: T.Type, key: String, from defaults: UserDefaults) -> T? {
        guard let data = defaults.data(forKey: key) else { return nil }
        return try? JSONDecoder().decode(type, from: data)
    }
}
