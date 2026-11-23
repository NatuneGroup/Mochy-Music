// Mochy for iOS: a SwiftUI shell around the shared Compose UI (MainViewController in :shared).
// Everything else on iOS uses the shared Compose UI; CarPlay (MochyCarPlayScene) is the only other native UI.
// Based on Mochy (github.com/realidkroo/mochy-music); the host follows Metrolist's iOS app (GPL-3.0).

import AVFoundation
import MediaPlayer
import Shared
import SwiftUI
import UIKit

@main
struct MochyApp: App {
    // The delegate is where UIKit expects the audio session and the lock-screen bridge to be set up,
    // before the first scene and before any playback can start.
    @UIApplicationDelegateAdaptor(MochyAppDelegate.self) private var appDelegate

    var body: some Scene {
        WindowGroup {
            ComposeView()
                .ignoresSafeArea()
                .preferredColorScheme(.dark)
        }
    }
}

final class MochyAppDelegate: NSObject, UIApplicationDelegate {
    func application(
        _ application: UIApplication,
        didFinishLaunchingWithOptions launchOptions: [UIApplication.LaunchOptionsKey: Any]? = nil
    ) -> Bool {
        // Playback category so the app keeps playing with the screen off and shows its controls.
        try? AVAudioSession.sharedInstance().setCategory(.playback, mode: .default)
        // Retain the audio-session and lock-screen bridge for the lifetime of the app.
        _ = IosSystemMediaBridge.shared
        return true
    }

    func applicationDidEnterBackground(_ application: UIApplication) {
        // The shared queue saves every change as it happens, so backgrounding only has to keep the
        // now-playing panel's clock honest: a paused app must not report a playing rate.
        MPNowPlayingInfoCenter.default().playbackState = IosSystemMediaBridge.shared.isPlaying
            ? .playing
            : .paused
    }
}

private struct ComposeView: UIViewControllerRepresentable {
    func makeUIViewController(context: Context) -> UIViewController {
        MainViewControllerKt.MainViewController()
    }

    func updateUIViewController(_ uiViewController: UIViewController, context: Context) {}
}
