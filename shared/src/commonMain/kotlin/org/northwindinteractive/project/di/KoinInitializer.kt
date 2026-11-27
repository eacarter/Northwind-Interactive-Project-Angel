package org.northwindinteractive.project.di

import org.koin.core.KoinApplication
import org.koin.core.context.startKoin
import org.koin.core.module.Module
import org.koin.mp.KoinPlatform
import org.northwindinteractive.project.core.AppConfig

/**
 * Starts Koin once per process. [platformModule] supplies the Android / iOS adapters
 * (SecureStore, PlatformInfo, platform haptics and the device PresenceService).
 */
fun initKoin(
    config: AppConfig,
    platformModule: Module,
    appDeclaration: KoinApplication.() -> Unit = {},
) {
    if (KoinPlatform.getKoinOrNull() != null) return
    startKoin {
        appDeclaration()
        modules(
            platformModule,
            coreModule(config),
            backendModule(config),
            viewModelModule,
        )
    }
}
