import SwiftUI
import Shared

@main
struct iOSApp: App {
    init() {
        // Firebase is used automatically once GoogleService-Info.plist is added to the app target.
        // Without it, Project Angel runs against the in-memory demo backend.
        let hasFirebaseConfig = Bundle.main.path(forResource: "GoogleService-Info", ofType: "plist") != nil
        MainViewControllerKt.startAngel(useFirebase: hasFirebaseConfig)
    }

    var body: some Scene {
        WindowGroup {
            ContentView()
        }
    }
}
