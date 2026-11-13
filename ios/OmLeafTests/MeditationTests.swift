import XCTest
@testable import OmLeaf

final class MeditationTests: XCTestCase {
    private func plan(_ settings: MeditationSettings) -> Plan {
        Planner.build(Planner.meditationRequest(settings))
    }

    private func render(_ synth: PlanSynth, from seconds: Double, for length: Double) -> [Float] {
        synth.render(startFrame: Int(seconds * Double(synth.sampleRate)), count: Int(length * Double(synth.sampleRate)))
    }

    private func rms(_ samples: [Float]) -> Double {
        let sum = samples.reduce(0.0) { $0 + Double($1) * Double($1) }
        return (sum / Double(samples.count)).squareRoot()
    }

    func testTotalIsExactlyTheChosenMinutes() {
        for style in MeditationStyle.allCases {
            let result = plan(MeditationSettings(minutes: 7, style: style, bellEveryMinutes: 2))
            XCTAssertEqual(result.totalMs, 7 * 60_000, "\(style)")
            XCTAssertTrue(result.steps.allSatisfy { $0.phase == .listen })
            XCTAssertEqual(result.style, style)
        }
    }

    func testStepsAreContiguous() {
        let result = plan(MeditationSettings(minutes: 5, style: .aum, bellEveryMinutes: 1))
        for (first, second) in zip(result.steps, result.steps.dropFirst()) {
            XCTAssertEqual(first.endMs, second.startMs)
        }
        for (index, step) in result.steps.enumerated() {
            XCTAssertEqual(step.index, index)
        }
    }

    func testBellsAtStartIntervalsAndEnd() {
        let result = plan(MeditationSettings(minutes: 10, style: .bowl, bellEveryMinutes: 3))
        XCTAssertEqual(result.steps.filter(\.bell).map(\.startMs), [0, 180_000, 360_000, 540_000, 594_000])
        XCTAssertEqual(result.steps.last?.startMs, 594_000)
    }

    func testBellsCanBeSwitchedOff() {
        let result = plan(MeditationSettings(minutes: 5, bellEveryMinutes: 0, bells: false))
        XCTAssertTrue(result.steps.filter(\.bell).isEmpty)
    }

    func testIntervalLongerThanSessionMeansNoIntervalBells() {
        let result = plan(MeditationSettings(minutes: 5, bellEveryMinutes: 5))
        XCTAssertEqual(result.steps.filter(\.bell).map(\.startMs), [0, 294_000])
    }

    func testAumStyleChantsAndPauses() {
        let result = plan(MeditationSettings(minutes: 2, style: .aum))
        XCTAssertEqual(result.steps[0].chant, .aum)
        XCTAssertEqual(result.steps[0].durationMs, 12_000)
        XCTAssertEqual(result.steps[1].chant, .none)
        XCTAssertEqual(result.steps[1].durationMs, 4_000)
        XCTAssertEqual(result.steps.last?.chant, ChantSound.none)
    }

    func testMinutesAreClamped() {
        XCTAssertEqual(plan(MeditationSettings(minutes: 0)).totalMs, 60_000)
        XCTAssertEqual(plan(MeditationSettings(minutes: 9999)).totalMs, 180 * 60_000)
    }

    func testDroneIsHeardEvenWithBreathSwitchesOff() {
        let off = SoundSettings(chimes: false, guideTone: false, drone: false)
        let synth = PlanSynth(plan: plan(MeditationSettings(minutes: 2, style: .drone, bells: false)), settings: off)
        XCTAssertGreaterThan(rms(render(synth, from: 20, for: 1)), 0.003)
    }

    func testBowlStyleIsQuietBetweenBells() {
        let synth = PlanSynth(plan: plan(MeditationSettings(minutes: 3, style: .bowl, bells: true)), settings: SoundSettings())
        XCTAssertGreaterThan(rms(render(synth, from: 0, for: 1)), 0.003)
        XCTAssertEqual(rms(render(synth, from: 60, for: 1)), 0)
    }

    func testAumStyleSoundsDuringChantAndNotDuringPause() {
        let synth = PlanSynth(
            plan: plan(MeditationSettings(minutes: 2, style: .aum, bells: false)), settings: SoundSettings(chimes: false)
        )
        XCTAssertGreaterThan(rms(render(synth, from: 5, for: 1)), 0.003)
        XCTAssertEqual(rms(render(synth, from: 13, for: 2.5)), 0)
    }

    func testToneFadesOutAtTheEnd() {
        let synth = PlanSynth(plan: plan(MeditationSettings(minutes: 2, style: .drone, bells: false)), settings: SoundSettings())
        XCTAssertGreaterThan(rms(render(synth, from: 60, for: 1)), 3 * rms(render(synth, from: 118.5, for: 0.5)))
    }

    func testVolumeZeroIsSilent() {
        let synth = PlanSynth(plan: plan(MeditationSettings(minutes: 2)), settings: SoundSettings(volume: 0))
        XCTAssertEqual(rms(render(synth, from: 10, for: 1)), 0)
    }

    func testPitchRangeAllowsTypedValues() {
        XCTAssertEqual(SoundSettings(frequencyHz: -5).sanitized().frequencyHz, 0)
        XCTAssertEqual(SoundSettings(frequencyHz: 5000).sanitized().frequencyHz, 1000)
        XCTAssertEqual(SoundSettings(frequencyHz: 963).sanitized().frequencyHz, 963)
    }

    func testTypedPitchIsParsed() throws {
        XCTAssertEqual(try XCTUnwrap(FrequencyPresets.parse("136.1")), 136.1, accuracy: 1e-9)
        XCTAssertEqual(try XCTUnwrap(FrequencyPresets.parse(" 136,1 ")), 136.1, accuracy: 1e-9)
        XCTAssertEqual(FrequencyPresets.parse("99999"), 1000)
        XCTAssertNil(FrequencyPresets.parse(""))
        XCTAssertNil(FrequencyPresets.parse("abc"))
        XCTAssertNil(FrequencyPresets.parse("nan"))
    }

    func testNoteNames() {
        XCTAssertEqual(Pitch.noteName(440), "A4")
        XCTAssertEqual(Pitch.noteName(261.63), "C4")
        XCTAssertEqual(Pitch.noteName(136.1), "C#3 -31¢")
        XCTAssertEqual(Pitch.noteName(441), "A4 +4¢")
        XCTAssertNil(Pitch.noteName(5))
        XCTAssertNotNil(Pitch.noteName(963))
    }

    func testPitchFormatting() {
        XCTAssertEqual(Pitch.format(432), "432")
        XCTAssertEqual(Pitch.format(136.1), "136.1")
    }
}

@MainActor
final class MeditationStoreTests: XCTestCase {
    func testMeditationSettingsPersistAndOldSettingsStillLoad() throws {
        let name = "omleaf.tests.\(UUID().uuidString)"
        let defaults = try XCTUnwrap(UserDefaults(suiteName: name))
        defer { defaults.removePersistentDomain(forName: name) }

        // Settings saved by an older version have no "meditation" key.
        let oldJSON = #"{"sound":{"frequencyHz":432,"chimes":false,"guideTone":true,"drone":false,"volume":0.5},"haptics":false}"#
        defaults.set(Data(oldJSON.utf8), forKey: "omleaf.settings")
        let old = AppStore(defaults: defaults)
        XCTAssertEqual(old.settings.sound.frequencyHz, 432)
        XCTAssertFalse(old.settings.haptics)
        XCTAssertEqual(old.settings.meditation, MeditationSettings())

        old.saveMeditation(MeditationSettings(minutes: 25, style: .bowl, bellEveryMinutes: 5, bells: false))
        let reloaded = AppStore(defaults: defaults)
        XCTAssertEqual(reloaded.settings.meditation.minutes, 25)
        XCTAssertEqual(reloaded.settings.meditation.style, .bowl)
        XCTAssertEqual(reloaded.settings.meditation.bellEveryMinutes, 5)
        XCTAssertFalse(reloaded.settings.meditation.bells)
    }

    func testKeepPlayingDefaultsOnForOldSettings() throws {
        let old = #"{"sound":{"frequencyHz":432,"chimes":false,"guideTone":true,"drone":false,"volume":0.5},"haptics":false}"#
        let decoded = try JSONDecoder().decode(AppSettings.self, from: Data(old.utf8))
        XCTAssertTrue(decoded.keepPlaying)
    }
}
