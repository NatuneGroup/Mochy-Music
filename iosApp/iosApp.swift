// Mochy for iOS: a SwiftUI shell around the shared Compose UI (MainViewController in :shared).
// Based on Mochy (github.com/realidkroo/mochy-music); the host follows Metrolist's iOS app (GPL-3.0).

import Shared
import SwiftUI
import UIKit

@main
struct MochyApp: App {
    init() {
        // Retain the audio-session and lock-screen bridge for the lifetime of the app.
        _ = IosSystemMediaBridge.shared
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
