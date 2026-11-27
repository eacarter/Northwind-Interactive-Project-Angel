package org.northwindinteractive.project

import androidx.compose.ui.window.ComposeUIViewController
import dev.gitlive.firebase.Firebase
import dev.gitlive.firebase.initialize
import org.koin.dsl.module
import org.northwindinteractive.project.core.AppConfig
import org.northwindinteractive.project.core.BackendMode
import org.northwindinteractive.project.core.platform.Haptics
import org.northwindinteractive.project.core.platform.PlatformInfo
import org.northwindinteractive.project.core.platform.PresenceService
import org.northwindinteractive.project.core.platform.SecureStore
import org.northwindinteractive.project.di.DevicePresence
import org.northwindinteractive.project.di.PlatformHaptics
import org.northwindinteractive.project.di.initKoin
import org.northwindinteractive.project.platform.IosHaptics
import org.northwindinteractive.project.platform.IosPlatformInfo
import org.northwindinteractive.project.platform.IosPresenceService
import org.northwindinteractive.project.platform.KeychainSecureStore
import platform.UIKit.UIViewController
import kotlin.time.Clock

/**
 * Starts the shared app. Called once from Swift (`iOSApp.init`).
 *
 * @param useFirebase true when `GoogleService-Info.plist` is bundled; Firebase is then configured
 * here and the real backend is used. Otherwise the in-memory demo backend runs.
 */
fun startAngel(useFirebase: Boolean) {
    if (useFirebase) Firebase.initialize()
    initKoin(
        config = AppConfig(backend = if (useFirebase) BackendMode.FIREBASE else BackendMode.DEMO),
        platformModule = module {
            single<SecureStore> { KeychainSecureStore() }
            single<PlatformInfo> { IosPlatformInfo() }
            single<Haptics>(PlatformHaptics) { IosHaptics() }
            single<PresenceService>(DevicePresence) { IosPresenceService(Clock.System) }
        },
    )
}

fun MainViewController(): UIViewController = ComposeUIViewController { App() }
