// Mochy for iOS: a SwiftUI shell around the shared Compose UI (MainViewController in :shared).
// Based on Mochy (github.com/realidkroo/mochy-music); the host follows Metrolist's iOS app (GPL-3.0).

import AVFoundation
import Shared
import SwiftUI
import UIKit

@main
struct MochyApp: App {
    init() {
        // playback keeps going with the screen locked, like the Android app
        try? AVAudioSession.sharedInstance().setCategory(.playback, mode: .default)
    }

    var body: some Scene {
        WindowGroup {
            ComposeView()
                .ignoresSafeArea()
                .preferredColorScheme(.dark)
        }
    }
}

private struct ComposeView: UIViewControllerRepresentable {
    func makeUIViewController(context: Context) -> UIViewController {
        MainViewControllerKt.MainViewController()
    }

    func updateUIViewController(_ uiViewController: UIViewController, context: Context) {}
}
