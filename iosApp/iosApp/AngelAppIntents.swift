import AppIntents
import Shared

/// Discreet native trigger (spec M6). Users can run it from Shortcuts, Siri, the Action button,
/// or assign it to Back Tap (Settings → Accessibility → Touch → Back Tap).
///
/// The intent only *requests* an alert: shared Kotlin code still requires an active venue
/// registration and fresh on-site presence before anything is sent.
struct SendDiscreetAlertIntent: AppIntent {
    static var title: LocalizedStringResource = "Send Discreet Alert"
    static var description = IntentDescription(
        "Sends a discreet alert to security at the participating venue you're registered with."
    )
    static var openAppWhenRun: Bool = true

    @MainActor
    func perform() async throws -> some IntentResult {
        ShortcutDispatcher.shared.dispatchFromAppIntent()
        return .result()
    }
}

struct AngelShortcuts: AppShortcutsProvider {
    static var appShortcuts: [AppShortcut] {
        AppShortcut(
            intent: SendDiscreetAlertIntent(),
            phrases: ["Send a discreet alert with \(.applicationName)"],
            shortTitle: "Discreet Alert",
            systemImageName: "shield.lefthalf.filled"
        )
    }
}
