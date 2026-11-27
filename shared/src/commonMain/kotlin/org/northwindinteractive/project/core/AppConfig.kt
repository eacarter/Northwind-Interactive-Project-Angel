package org.northwindinteractive.project.core

/**
 * Runtime wiring decided by the platform entry point.
 *
 * - [BackendMode.FIREBASE] is selected automatically when the Firebase config file is bundled
 *   (`google-services.json` on Android, `GoogleService-Info.plist` on iOS).
 * - [BackendMode.DEMO] runs a faithful in-memory simulation of the Cloud Functions contract so the
 *   full UX can be exercised before Firebase is connected.
 */
data class AppConfig(
    val backend: BackendMode,
    val presence: PresenceMode = if (backend == BackendMode.DEMO) PresenceMode.SIMULATED else PresenceMode.DEVICE,
    /** Cloud Functions region; must match the deployed functions. */
    val functionsRegion: String = "us-central1",
)

enum class BackendMode { FIREBASE, DEMO }

enum class PresenceMode { DEVICE, SIMULATED }
