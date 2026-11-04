import XCTest
@testable import OmLeaf

final class SynthStatsTests: XCTestCase {
    private func plan(chant: ChantSound = .aum) -> Plan {
        Planner.build(PlanRequest(
            kind: .rhythm, pattern: BreathPattern(inhale: 2, holdIn: 0, exhale: 4, holdOut: 0), rounds: 2, chant: chant
        ))
    }

    private func render(_ synth: PlanSynth, seconds: Double) -> [Float] {
        synth.render(startFrame: 0, count: Int(seconds * Double(synth.sampleRate)))
    }

    func testSilentWhenEverythingIsOff() {
        let synth = PlanSynth(plan: plan(), settings: SoundSettings(chimes: false, guideTone: false, drone: false))
        XCTAssertTrue(render(synth, seconds: 5).allSatisfy { $0 == 0 })
    }

    func testMakesSoundAndStaysInRange() {
        let out = render(PlanSynth(plan: plan(), settings: SoundSettings(drone: true)), seconds: 12)
        XCTAssertTrue(out.contains { $0 != 0 })
        XCTAssertTrue(out.allSatisfy { abs($0) <= 1 })
    }

    func testVolumeZeroIsSilent() {
        let out = render(PlanSynth(plan: plan(), settings: SoundSettings(volume: 0)), seconds: 5)
        XCTAssertTrue(out.allSatisfy { $0 == 0 })
    }

    func testChunkedRenderMatchesOneShot() {
        let synth = PlanSynth(plan: plan(), settings: SoundSettings(drone: true))
        let whole = render(synth, seconds: 6)
        var pieces: [Float] = []
        while pieces.count < whole.count {
            pieces += synth.render(startFrame: pieces.count, count: min(1234, whole.count - pieces.count))
        }
        XCTAssertEqual(whole, pieces)
    }

    func testGuideToneOnlyDuringChantedExhale() {
        let synth = PlanSynth(plan: plan(), settings: SoundSettings(chimes: false, drone: false))
        let rate = synth.sampleRate
        let inhale = synth.render(startFrame: 0, count: rate)
        let exhale = synth.render(startFrame: 3 * rate, count: rate)
        XCTAssertTrue(inhale.allSatisfy { $0 == 0 })
        XCTAssertTrue(exhale.contains { $0 != 0 })
    }

    func testFrequencyIsClamped() {
        XCTAssertEqual(SoundSettings(frequencyHz: 5000).sanitized().frequencyHz, FrequencyPresets.maxHz)
        XCTAssertEqual(SoundSettings(frequencyHz: 1).sanitized().frequencyHz, FrequencyPresets.minHz)
    }

    private func record(_ day: Int, _ seconds: Int = 600) -> SessionRecord {
        SessionRecord(epochDay: day, practice: "x", seconds: seconds, rounds: 5)
    }

    func testStreaksCountConsecutiveDays() {
        let stats = StatsCalculator.compute([10, 11, 12, 15, 16].map { record($0) }, today: 16)
        XCTAssertEqual(stats.currentStreak, 2)
        XCTAssertEqual(stats.bestStreak, 3)
        XCTAssertEqual(stats.sessions, 5)
    }

    func testStreakSurvivesUntilTodayEnds() {
        XCTAssertEqual(StatsCalculator.compute([record(9), record(10)], today: 11).currentStreak, 2)
        XCTAssertEqual(StatsCalculator.compute([record(8), record(9)], today: 11).currentStreak, 0)
    }

    func testLast7AndTotals() {
        let stats = StatsCalculator.compute([record(20, 300), record(20, 300), record(14, 120)], today: 20)
        XCTAssertEqual(stats.last7Minutes, [2, 0, 0, 0, 0, 0, 10])
        XCTAssertEqual(stats.totalMinutes, 12)
        let none = StatsCalculator.compute([], today: 20)
        XCTAssertEqual(none.currentStreak, 0)
        XCTAssertEqual(none.sessions, 0)
    }

    func testEpochDayUsesTheGivenTimeZone() {
        let date = Date(timeIntervalSince1970: 86_400 * 100 + 23 * 3600)
        XCTAssertEqual(EpochDay.of(date, timeZone: TimeZone(secondsFromGMT: 0)!), 100)
        XCTAssertEqual(EpochDay.of(date, timeZone: TimeZone(secondsFromGMT: 5 * 3600 + 1800)!), 101)
    }
}
