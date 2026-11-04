import XCTest
@testable import OmLeaf

@MainActor
final class AppStoreTests: XCTestCase {
    private func makeDefaults() -> UserDefaults {
        let name = "omleaf.tests.\(UUID().uuidString)"
        let defaults = UserDefaults(suiteName: name)!
        defaults.removePersistentDomain(forName: name)
        return defaults
    }

    func testSettingsAndHistorySurviveARestart() {
        let defaults = makeDefaults()
        let first = AppStore(defaults: defaults)
        first.saveSound(SoundSettings(frequencyHz: 432, chimes: false))
        first.saveHaptics(false)
        first.addSession(SessionRecord(epochDay: 5, practice: "Box breathing", seconds: 120, rounds: 4))

        let second = AppStore(defaults: defaults)
        XCTAssertEqual(second.settings.sound.frequencyHz, 432)
        XCTAssertFalse(second.settings.sound.chimes)
        XCTAssertFalse(second.settings.haptics)
        XCTAssertEqual(second.history.count, 1)
    }

    func testCustomPracticesCanBeSavedReplacedAndDeleted() {
        let store = AppStore(defaults: makeDefaults())
        let item = CustomPractice(id: "a", name: "Mine", inhale: 4, holdIn: 0, exhale: 8, holdOut: 0, rounds: 5, chant: .aum)
        store.saveCustom(item)
        store.saveCustom(CustomPractice(id: "a", name: "Renamed", inhale: 4, holdIn: 0, exhale: 8, holdOut: 0, rounds: 5, chant: .aum))
        XCTAssertEqual(store.custom.map(\.name), ["Renamed"])
        XCTAssertEqual(store.practice(withId: "a")?.chant, .aum)
        store.deleteCustom(id: "a")
        XCTAssertNil(store.practice(withId: "a"))
    }

    func testHistoryIsCappedAndCanBeCleared() {
        let store = AppStore(defaults: makeDefaults())
        for day in 0..<(AppStore.maxHistory + 20) {
            store.addSession(SessionRecord(epochDay: day, practice: "x", seconds: 60, rounds: 1))
        }
        XCTAssertEqual(store.history.count, AppStore.maxHistory)
        XCTAssertEqual(store.history.first?.epochDay, 20)
        store.clearHistory()
        XCTAssertTrue(store.history.isEmpty)
    }

    func testFirstRunNoteIsShownOnce() {
        let defaults = makeDefaults()
        let store = AppStore(defaults: defaults)
        XCTAssertFalse(store.settings.acknowledgedNote)
        store.acknowledgeNote()
        XCTAssertTrue(AppStore(defaults: defaults).settings.acknowledgedNote)
    }
}
