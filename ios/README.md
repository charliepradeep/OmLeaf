# OmLeaf for iOS and iPad

SwiftUI port of the Android app. Same practices, same breath planner and the same generated sounds (chimes, an
A-U-M guide tone and an optional drone), all made on the device. No account, no network, no analytics.

## Build
```
brew install xcodegen swiftlint
cd ios
xcodegen generate
open OmLeaf.xcodeproj
```
Pick your team under Signing & Capabilities, then run on a device or simulator (iOS 17+).
`xcodebuild -scheme OmLeaf -destination 'platform=iOS Simulator,name=iPhone 16' test` runs the unit tests.

## Layout
- `OmLeaf/Domain`: models, `Planner` (practice to timed steps), `PlanSynth` (steps to audio), stats. No UI or audio classes.
- `OmLeaf/Audio/SessionRunner.swift`: plays the synth with AVAudioEngine; the audio frame count is the session clock.
- `OmLeaf/Data/AppStore.swift`: settings, saved patterns and history in UserDefaults.
- `OmLeaf/UI`: SwiftUI screens, including the sound-only meditation and the pitch control (typed Hz, slider, presets).
