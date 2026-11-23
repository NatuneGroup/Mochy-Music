// Native iOS audio-session and lock-screen controls for Mochy's AVPlayer.

import AVFoundation
import Foundation
import MediaPlayer
import UIKit

final class IosSystemMediaBridge {
    static let shared = IosSystemMediaBridge()

    /// How far the lock screen's skip arrows move, the interval the shared player uses.
    private static let skipIntervalSeconds: NSNumber = 15

    /// What the lock screen currently shows, for the app delegate's background snapshot.
    private(set) var isPlaying = false

    private let center = NotificationCenter.default
    private var observers: [NSObjectProtocol] = []
    private var artworkRequest: URLSessionDataTask?
    private var artworkURL: String?
    private var artwork: MPMediaItemArtwork?

    private init() {
        try? AVAudioSession.sharedInstance().setCategory(.playback, mode: .default)
        registerCommands()
        observeBridgeEvents()
        observeAudioSessionEvents()
    }

    private func registerCommands() {
        let commands = MPRemoteCommandCenter.shared()
        commands.playCommand.addTarget { [weak self] _ in
            self?.post("remotePlay")
            return .success
        }
        commands.pauseCommand.addTarget { [weak self] _ in
            self?.post("remotePause")
            return .success
        }
        commands.togglePlayPauseCommand.addTarget { [weak self] _ in
            self?.post("remoteToggle")
            return .success
        }
        commands.nextTrackCommand.addTarget { [weak self] _ in
            self?.post("remoteNext")
            return .success
        }
        commands.previousTrackCommand.addTarget { [weak self] _ in
            self?.post("remotePrevious")
            return .success
        }
        commands.changePlaybackPositionCommand.addTarget { [weak self] event in
            guard let event = event as? MPChangePlaybackPositionCommandEvent else {
                return .commandFailed
            }
            self?.post("remoteSeek", payload: ["positionSeconds": event.positionTime])
            return .success
        }
        // the arrows iOS shows beside the scrubber; the shared player moves by the same 15 seconds
        commands.skipForwardCommand.preferredIntervals = [Self.skipIntervalSeconds]
        commands.skipForwardCommand.addTarget { [weak self] _ in
            self?.post("remoteSkipForward")
            return .success
        }
        commands.skipBackwardCommand.preferredIntervals = [Self.skipIntervalSeconds]
        commands.skipBackwardCommand.addTarget { [weak self] _ in
            self?.post("remoteSkipBackward")
            return .success
        }
        // saved / not saved, from the lock screen and CarPlay: the shared library answers
        commands.likeCommand.addTarget { [weak self] _ in
            self?.post("remoteLike")
            return .success
        }
        commands.dislikeCommand.addTarget { [weak self] _ in
            self?.post("remoteUnlike")
            return .success
        }
        setCommandsEnabled(playback: false, liked: false)
    }

    private func observeBridgeEvents() {
        observe("publishNowPlaying") { [weak self] notification in
            guard let self,
                  let encoded = notification.object as? String,
                  let data = encoded.data(using: .utf8),
                  let payload = (try? JSONSerialization.jsonObject(with: data)) as? [String: Any]
            else { return }
            self.updateNowPlaying(payload)
        }
        observe("clearNowPlaying") { [weak self] _ in self?.clearNowPlaying() }
        observe("activateAudioSession") { [weak self] _ in self?.activateAudioSession() }
        observe("deactivateAudioSession") { [weak self] _ in self?.deactivateAudioSession() }
        // Playback > Play alongside other apps, from the shared settings store
        observe("audioSessionMix") { [weak self] notification in
            guard let value = notification.object as? String else { return }
            self?.applyAudioCategory(mixWithOthers: value == "true")
        }
    }

    private func observeAudioSessionEvents() {
        observers.append(center.addObserver(
            forName: AVAudioSession.interruptionNotification,
            object: AVAudioSession.sharedInstance(),
            queue: .main
        ) { [weak self] notification in
            self?.handleInterruption(notification)
        })
        observers.append(center.addObserver(
            forName: AVAudioSession.routeChangeNotification,
            object: AVAudioSession.sharedInstance(),
            queue: .main
        ) { [weak self] notification in
            self?.handleRouteChange(notification)
        })
    }

    private func observe(_ suffix: String, handler: @escaping (Notification) -> Void) {
        let name = Notification.Name("com.mochyy.mochymusic.ios.\(suffix)")
        observers.append(center.addObserver(forName: name, object: nil, queue: .main, using: handler))
    }

    private func updateNowPlaying(_ payload: [String: Any]) {
        let available = payload["available"] as? Bool ?? false
        let title = payload["title"] as? String ?? ""
        let artist = payload["artist"] as? String ?? ""
        let album = payload["album"] as? String ?? ""
        let duration = payload["durationSeconds"] as? Double ?? 0
        let elapsed = payload["elapsedSeconds"] as? Double ?? 0
        let isPlaying = payload["isPlaying"] as? Bool ?? false
        let nextArtworkURL = payload["artworkURL"] as? String ?? ""
        let canGoNext = payload["canGoNext"] as? Bool ?? false
        let canGoPrevious = payload["canGoPrevious"] as? Bool ?? false
        let playbackRate = payload["playbackRate"] as? Double ?? (isPlaying ? 1.0 : 0.0)
        let isLiked = payload["isLiked"] as? Bool ?? false
        self.isPlaying = isPlaying

        if title.isEmpty {
            clearNowPlaying()
            return
        }

        updateArtwork(for: nextArtworkURL)
        var info: [String: Any] = [
            MPMediaItemPropertyTitle: title,
            MPMediaItemPropertyArtist: artist,
            MPNowPlayingInfoPropertyElapsedPlaybackTime: max(0, elapsed),
            MPNowPlayingInfoPropertyPlaybackRate: playbackRate,
            MPNowPlayingInfoPropertyDefaultPlaybackRate: 1.0,
        ]
        if !album.isEmpty {
            info[MPMediaItemPropertyAlbumTitle] = album
            // the car's lists show the album too, and album artist is what it groups by
            info[MPMediaItemPropertyAlbumArtist] = artist
        }
        if duration > 0 { info[MPMediaItemPropertyPlaybackDuration] = duration }
        // audio, so the lock screen's scrubber is the audio one and likes make sense
        info[MPNowPlayingInfoPropertyMediaType] = MPNowPlayingInfoMediaType.audio.rawValue
        if let artwork { info[MPMediaItemPropertyArtwork] = artwork }
        MPNowPlayingInfoCenter.default().nowPlayingInfo = info
        setCommandsEnabled(
            playback: available,
            liked: isLiked,
            canGoNext: canGoNext,
            canGoPrevious: canGoPrevious
        )
    }

    private func updateArtwork(for urlString: String) {
        guard urlString != artworkURL else { return }
        artworkRequest?.cancel()
        artworkRequest = nil
        artworkURL = urlString
        artwork = nil
        guard let url = URL(string: urlString), url.scheme == "https" else { return }

        artworkRequest = URLSession.shared.dataTask(with: url) { [weak self] data, _, _ in
            guard let data, let image = UIImage(data: data) else { return }
            DispatchQueue.main.async {
                guard let self, self.artworkURL == urlString else { return }
                let newArtwork = MPMediaItemArtwork(boundsSize: image.size) { _ in image }
                self.artwork = newArtwork
                var info = MPNowPlayingInfoCenter.default().nowPlayingInfo ?? [:]
                info[MPMediaItemPropertyArtwork] = newArtwork
                MPNowPlayingInfoCenter.default().nowPlayingInfo = info
            }
        }
        artworkRequest?.resume()
    }

    // Next and previous follow the phone queue state from the shared side, not just whether a song is loaded.
    private func setCommandsEnabled(
        playback: Bool,
        liked: Bool = false,
        canGoNext: Bool = false,
        canGoPrevious: Bool = false
    ) {
        let commands = MPRemoteCommandCenter.shared()
        commands.playCommand.isEnabled = playback
        commands.pauseCommand.isEnabled = playback
        commands.togglePlayPauseCommand.isEnabled = playback
        commands.changePlaybackPositionCommand.isEnabled = playback
        commands.skipForwardCommand.isEnabled = playback
        commands.skipBackwardCommand.isEnabled = playback
        commands.likeCommand.isEnabled = playback
        commands.dislikeCommand.isEnabled = playback
        // lit when the song is saved, so the car shows the same state the phone does
        commands.likeCommand.isActive = liked
        commands.nextTrackCommand.isEnabled = canGoNext
        commands.previousTrackCommand.isEnabled = canGoPrevious
    }

    private func clearNowPlaying() {
        isPlaying = false
        artworkRequest?.cancel()
        artworkRequest = nil
        artworkURL = nil
        artwork = nil
        MPNowPlayingInfoCenter.default().nowPlayingInfo = nil
        setCommandsEnabled(playback: false)
    }

    // The category follows the shared "Play alongside other apps" switch: mixing leaves other audio
    // playing, which is what the Android setting does with its audio focus change.
    private func applyAudioCategory(mixWithOthers: Bool) {
        let options: AVAudioSession.CategoryOptions = mixWithOthers ? [.mixWithOthers] : []
        try? AVAudioSession.sharedInstance().setCategory(.playback, mode: .default, options: options)
    }

    private func activateAudioSession() {
        try? AVAudioSession.sharedInstance().setActive(true)
    }

    private func deactivateAudioSession() {
        try? AVAudioSession.sharedInstance().setActive(false, options: .notifyOthersOnDeactivation)
    }

    private func handleInterruption(_ notification: Notification) {
        guard let userInfo = notification.userInfo,
              let rawType = userInfo[AVAudioSessionInterruptionTypeKey] as? UInt,
              let type = AVAudioSession.InterruptionType(rawValue: rawType)
        else { return }

        if type == .began {
            post("interruptionBegan")
        } else if type == .ended {
            let rawOptions = userInfo[AVAudioSessionInterruptionOptionKey] as? UInt ?? 0
            let options = AVAudioSession.InterruptionOptions(rawValue: rawOptions)
            post("interruptionEnded", payload: ["shouldResume": options.contains(.shouldResume)])
        }
    }

    private func handleRouteChange(_ notification: Notification) {
        guard let userInfo = notification.userInfo,
              let rawReason = userInfo[AVAudioSessionRouteChangeReasonKey] as? UInt,
              let reason = AVAudioSession.RouteChangeReason(rawValue: rawReason),
              reason == .oldDeviceUnavailable
        else { return }
        post("outputRemoved")
    }

    private func post(_ suffix: String, payload: [String: Any]? = nil) {
        var object: Any?
        if let payload,
           let data = try? JSONSerialization.data(withJSONObject: payload),
           let encoded = String(data: data, encoding: .utf8) {
            object = encoded
        }
        center.post(
            name: Notification.Name("com.mochyy.mochymusic.ios.\(suffix)"),
            object: object
        )
    }
}
