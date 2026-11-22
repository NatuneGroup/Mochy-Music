// Native iOS audio-session and lock-screen controls for Mochy's AVPlayer.

import AVFoundation
import Foundation
import MediaPlayer
import UIKit

final class IosSystemMediaBridge {
    static let shared = IosSystemMediaBridge()

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
        commands.changePlaybackPositionCommand.addTarget { [weak self] event in
            guard let event = event as? MPChangePlaybackPositionCommandEvent else {
                return .commandFailed
            }
            self?.post("remoteSeek", payload: ["positionSeconds": event.positionTime])
            return .success
        }
        setCommandsEnabled(false)
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

        if title.isEmpty {
            clearNowPlaying()
            return
        }

        updateArtwork(for: nextArtworkURL)
        var info: [String: Any] = [
            MPMediaItemPropertyTitle: title,
            MPMediaItemPropertyArtist: artist,
            MPNowPlayingInfoPropertyElapsedPlaybackTime: max(0, elapsed),
            MPNowPlayingInfoPropertyPlaybackRate: isPlaying ? 1.0 : 0.0,
            MPNowPlayingInfoPropertyDefaultPlaybackRate: 1.0,
        ]
        if !album.isEmpty { info[MPMediaItemPropertyAlbumTitle] = album }
        if duration > 0 { info[MPMediaItemPropertyPlaybackDuration] = duration }
        if let artwork { info[MPMediaItemPropertyArtwork] = artwork }
        MPNowPlayingInfoCenter.default().nowPlayingInfo = info
        setCommandsEnabled(available)
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

    private func setCommandsEnabled(_ enabled: Bool) {
        let commands = MPRemoteCommandCenter.shared()
        commands.playCommand.isEnabled = enabled
        commands.pauseCommand.isEnabled = enabled
        commands.togglePlayPauseCommand.isEnabled = enabled
        commands.changePlaybackPositionCommand.isEnabled = enabled
    }

    private func clearNowPlaying() {
        artworkRequest?.cancel()
        artworkRequest = nil
        artworkURL = nil
        artwork = nil
        MPNowPlayingInfoCenter.default().nowPlayingInfo = nil
        setCommandsEnabled(false)
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
