import Foundation

/// Built-in practices. Numbers are gentle beginner defaults; everything can be changed before starting.
enum Practices {
    private static let pumpCaution =
        "A forceful practice. Skip it if you are pregnant, have high blood pressure, a heart condition, a hernia, " +
        "epilepsy or recent abdominal surgery, or ask your doctor first. Stop if you feel dizzy."

    private static let holdCaution = "Never strain on the holds. If you feel uncomfortable, shorten them or set them to 0."

    private static let calming: [Practice] = [
        Practice(
            id: "dirgha", name: "Deep belly breathing", sanskrit: "Dirgha", category: .calming,
            summary: "Slow, full breaths into the belly. A good place to begin.",
            howTo: [
                "Sit comfortably with a straight back.",
                "Breathe in slowly and let the belly expand.",
                "Breathe out slowly and let the belly soften.",
            ],
            caution: nil, kind: .rhythm, pattern: BreathPattern(inhale: 4, holdIn: 0, exhale: 6, holdOut: 0), rounds: 10
        ),
        Practice(
            id: "sama", name: "Equal breathing", sanskrit: "Sama Vritti", category: .calming,
            summary: "Inhale and exhale for the same length.",
            howTo: ["Breathe through the nose.", "Match the length of the inhale and the exhale."],
            caution: nil, kind: .rhythm, pattern: BreathPattern(inhale: 4, holdIn: 0, exhale: 4, holdOut: 0), rounds: 12
        ),
        Practice(
            id: "box", name: "Box breathing", sanskrit: "Sama Vritti with holds", category: .calming,
            summary: "Four equal sides: in, hold, out, hold.",
            howTo: ["Breathe in for the count.", "Hold gently.", "Breathe out for the count.", "Hold gently with the lungs empty."],
            caution: holdCaution, kind: .rhythm, pattern: BreathPattern(inhale: 4, holdIn: 4, exhale: 4, holdOut: 4), rounds: 8
        ),
        Practice(
            id: "478", name: "4-7-8 breathing", sanskrit: "Relaxing breath", category: .calming,
            summary: "A long exhale to help you unwind. Four rounds are plenty at first.",
            howTo: ["Inhale quietly through the nose.", "Hold.", "Exhale slowly through the mouth with a soft whoosh."],
            caution: holdCaution, kind: .rhythm, pattern: BreathPattern(inhale: 4, holdIn: 7, exhale: 8, holdOut: 0), rounds: 4
        ),
        Practice(
            id: "ujjayi", name: "Ocean breath", sanskrit: "Ujjayi", category: .calming,
            summary: "A soft, audible breath with a slightly narrowed throat, like a quiet ocean.",
            howTo: [
                "Narrow the back of the throat slightly, as if fogging a mirror, but with the mouth closed.",
                "Breathe in and out through the nose with a gentle ocean sound.",
            ],
            caution: nil, kind: .rhythm, pattern: BreathPattern(inhale: 4, holdIn: 0, exhale: 6, holdOut: 0), rounds: 10
        ),
        Practice(
            id: "bhramari", name: "Humming bee breath", sanskrit: "Bhramari", category: .calming,
            summary: "Breathe in, then hum on the exhale and feel the vibration in the head.",
            howTo: [
                "Close the lips and keep the teeth slightly apart.",
                "Breathe in through the nose.",
                "Exhale with a steady, smooth hum.",
            ],
            caution: nil, kind: .rhythm, pattern: BreathPattern(inhale: 4, holdIn: 0, exhale: 8, holdOut: 0), rounds: 7,
            chant: .hum
        ),
    ]

    private static let balancing: [Practice] = [
        Practice(
            id: "nadi", name: "Alternate nostril breathing", sanskrit: "Nadi Shodhana (Anuloma Viloma)", category: .balancing,
            summary: "In through one nostril, out through the other, then back.",
            howTo: [
                "Use the right hand: thumb closes the right nostril, ring finger closes the left.",
                "Follow the on-screen nostril for each breath.",
                "One round is a full cycle: left in, right out, right in, left out.",
            ],
            caution: holdCaution, kind: .alternate, pattern: BreathPattern(inhale: 4, holdIn: 4, exhale: 8, holdOut: 0), rounds: 6,
            inhaleSides: [.left, .right]
        ),
        Practice(
            id: "chandra", name: "Moon-piercing breath", sanskrit: "Chandra Bhedana", category: .cooling,
            summary: "Inhale through the left nostril, exhale through the right.",
            howTo: ["Close the right nostril and breathe in through the left.", "Close the left and breathe out through the right."],
            caution: holdCaution, kind: .alternate, pattern: BreathPattern(inhale: 4, holdIn: 2, exhale: 8, holdOut: 0), rounds: 9,
            inhaleSides: [.left]
        ),
        Practice(
            id: "surya", name: "Sun-piercing breath", sanskrit: "Surya Bhedana", category: .energizing,
            summary: "Inhale through the right nostril, exhale through the left.",
            howTo: ["Close the left nostril and breathe in through the right.", "Close the right and breathe out through the left."],
            caution: holdCaution + " Many teachers advise against it with high blood pressure.",
            kind: .alternate, pattern: BreathPattern(inhale: 4, holdIn: 2, exhale: 8, holdOut: 0), rounds: 9,
            inhaleSides: [.right]
        ),
        Practice(
            id: "sheetali", name: "Cooling breath", sanskrit: "Sheetali", category: .cooling,
            summary: "Breathe in through a curled tongue, out through the nose.",
            howTo: [
                "Roll the tongue into a tube and breathe in through it.",
                "Close the mouth and exhale through the nose.",
                "If you cannot curl the tongue, use Sheetkari: breathe in through the teeth.",
            ],
            caution: "Avoid in very cold weather or if you have a cold.",
            kind: .rhythm, pattern: BreathPattern(inhale: 4, holdIn: 3, exhale: 6, holdOut: 0), rounds: 9
        ),
    ]

    private static let energizing: [Practice] = [
        Practice(
            id: "kapalabhati", name: "Skull-shining breath", sanskrit: "Kapalabhati", category: .energizing,
            summary: "Quick, sharp exhales with a passive inhale, then one slow breath.",
            howTo: [
                "Each pump is a short, sharp exhale from the belly. The inhale happens on its own.",
                "After the pumps, take a slow breath in, hold gently, and breathe out.",
                "Breathe normally in the rest between rounds.",
            ],
            caution: pumpCaution, kind: .pump, pattern: BreathPattern(inhale: 4, holdIn: 8, exhale: 6, holdOut: 0), rounds: 3,
            pump: PumpSettings(strokes: 30, strokesPerMinute: 60, finishInhale: 4, finishHold: 8, finishExhale: 6),
            restSeconds: 15
        ),
        Practice(
            id: "bhastrika", name: "Bellows breath", sanskrit: "Bhastrika", category: .energizing,
            summary: "Forceful, equal breaths in and out, then a slow breath.",
            howTo: [
                "Breathe in and out with equal force, like a bellows.",
                "After the breaths, take one slow breath, hold gently, and exhale.",
            ],
            caution: pumpCaution, kind: .pump, pattern: BreathPattern(inhale: 4, holdIn: 8, exhale: 6, holdOut: 0), rounds: 3,
            pump: PumpSettings(strokes: 20, strokesPerMinute: 40, finishInhale: 4, finishHold: 8, finishExhale: 6),
            restSeconds: 20
        ),
    ]

    private static let chanting: [Practice] = [
        Practice(
            id: "aum", name: "AUM / OM chanting", sanskrit: "Omkara", category: .chanting,
            summary: "Chant A, U, M on one exhale. The M takes half of the exhale.",
            howTo: [
                "Breathe in deeply.",
                "Exhale chanting A (open mouth), then U, then M (lips closed, humming).",
                "Follow the guide tone, tuned to the frequency you choose in Sound settings.",
            ],
            caution: nil, kind: .rhythm, pattern: BreathPattern(inhale: 4, holdIn: 0, exhale: 12, holdOut: 0), rounds: 9,
            chant: .aum
        ),
        Practice(
            id: "aakaar", name: "A-sound chanting", sanskrit: "Akaar", category: .chanting,
            summary: "Chant a steady open \"A\" on each exhale.",
            howTo: ["Breathe in deeply.", "Exhale chanting a long, relaxed \"ahh\" from the chest."],
            caution: nil, kind: .rhythm, pattern: BreathPattern(inhale: 4, holdIn: 0, exhale: 10, holdOut: 0), rounds: 9,
            chant: .a
        ),
    ]

    static let all: [Practice] = calming + balancing + energizing + chanting

    static func byId(_ id: String) -> Practice? {
        all.first { $0.id == id }
    }

    static func grouped() -> [(category: Category, practices: [Practice])] {
        Category.allCases.compactMap { category in
            let list = all.filter { $0.category == category }
            return list.isEmpty ? nil : (category, list)
        }
    }
}
