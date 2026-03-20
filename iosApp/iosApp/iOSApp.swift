import SwiftUI

@main
struct iOSApp: App {
    init() {
        AudioSessionManager.shared.configure()
    }

    var body: some Scene {
        WindowGroup {
            ContentView()
        }
    }
}
