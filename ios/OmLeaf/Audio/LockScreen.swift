import MediaPlayer

/// Lock-screen and Control Centre controls: the title, a progress bar, and play/pause.
@MainActor
enum LockScreen {
    private static var targets: [Any] = []

    static func show(title: String, durationMs: Int, onPlay: @escaping () -> Void, onPause: @escaping () -> Void) {
        clear()
        let center = MPRemoteCommandCenter.shared()
        center.playCommand.isEnabled = true
        center.pauseCommand.isEnabled = true
        targets = [
            center.playCommand.addTarget { _ in onPlay(); return .success },
            center.pauseCommand.addTarget { _ in onPause(); return .success }
        ]
        MPNowPlayingInfoCenter.default().nowPlayingInfo = [
            MPMediaItemPropertyTitle: title,
            MPMediaItemPropertyArtist: "OmLeaf",
            MPMediaItemPropertyPlaybackDuration: Double(durationMs) / 1000,
            MPNowPlayingInfoPropertyElapsedPlaybackTime: 0.0,
            MPNowPlayingInfoPropertyPlaybackRate: 1.0
        ]
    }

    static func update(elapsedMs: Int, paused: Bool) {
        guard var info = MPNowPlayingInfoCenter.default().nowPlayingInfo else { return }
        info[MPNowPlayingInfoPropertyElapsedPlaybackTime] = Double(elapsedMs) / 1000
        info[MPNowPlayingInfoPropertyPlaybackRate] = paused ? 0.0 : 1.0
        MPNowPlayingInfoCenter.default().nowPlayingInfo = info
    }

    static func clear() {
        let center = MPRemoteCommandCenter.shared()
        if targets.count == 2 {
            center.playCommand.removeTarget(targets[0])
            center.pauseCommand.removeTarget(targets[1])
        }
        targets = []
        MPNowPlayingInfoCenter.default().nowPlayingInfo = nil
    }
}
