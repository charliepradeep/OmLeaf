# OmLeaf

A calm, offline pranayama timer for Android. Guided breathing with a visual breathing circle, optional
chimes, and a built-in guide tone for AUM, A and humming practice, tuned to a pitch you choose.

- **Practices:** deep belly breathing, equal and box breathing, 4-7-8, Ujjayi, Bhramari, Nadi Shodhana
  (alternate nostril), Chandra and Surya Bhedana, Sheetali, Kapalabhati, Bhastrika, AUM / OM chanting and
  A-sound chanting. Each is a starting point you can change.
- **Custom builder:** set breathe-in, hold, breathe-out and hold times, rounds and rest, then save it.
- **Chanting:** pick AUM (A and U a quarter each, M a half, the traditional 1:1:2), A, U, M or humming.
  A guide tone follows the chant and changes brightness from A to U to M.
- **Pitch:** presets (136.1 Hz "OM" tuning, 110 Hz, 261.6 Hz "Sa", 432 Hz, 528 Hz) or any value from 80 to
  600 Hz. These are tuning choices, not health claims.
- **Progress:** streaks, minutes and a seven-day chart, stored only on the phone.
- **Private:** no account, no ads, no analytics, no internet permission. All sound is generated on the phone.

## Build

Open this `android` folder in Android Studio (JDK 17 to 22), let Gradle sync, press Run. Unit tests for the breathing
engine, sound generator and statistics run without a device: `./gradlew testDebugUnitTest`.

## How it works

| Area | Where |
|---|---|
| Breathing plan (steps, rounds, nostrils, chant parts) | `domain/Plan.kt`, `domain/Practices.kt` |
| Sound generation (chimes, guide tone, drone) | `domain/Sound.kt` (pure Kotlin, tested) |
| Playback | `audio/SessionRunner.kt` (one `AudioTrack`; the audio clock is also the session clock) |
| Storage | `data/Storage.kt` (DataStore, on device) |
| Screens | `ui/` (Jetpack Compose) |

## Known limits

- **Not yet run on a device by its author.** It compiles in CI; please test on a phone and report problems.
- The session stops when the app is backgrounded or the screen locks (the screen stays on during a session).
  A foreground service for locked-screen sessions is a possible later addition.
- Practices are generic starting points and not medical advice. The app shows cautions for forceful practices.
- No reminders yet.

## Releasing

Copy `keystore.properties.example` to `keystore.properties` (git-ignored), fill it in, then build the release
bundle. Store listing text, data safety answers and the 512 px icon are in `OmLeafStore/`.

## License

MIT. See `LICENSE`.
