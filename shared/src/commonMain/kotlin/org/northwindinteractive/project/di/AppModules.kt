package org.northwindinteractive.project.di

import dev.gitlive.firebase.Firebase
import dev.gitlive.firebase.auth.auth
import dev.gitlive.firebase.firestore.firestore
import dev.gitlive.firebase.functions.functions
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.koin.core.module.Module
import org.koin.core.module.dsl.viewModel
import org.koin.core.module.dsl.viewModelOf
import org.koin.core.qualifier.named
import org.koin.dsl.module
import org.northwindinteractive.project.core.AppConfig
import org.northwindinteractive.project.core.BackendMode
import org.northwindinteractive.project.core.PresenceMode
import org.northwindinteractive.project.core.data.LocalPreferencesRepository
import org.northwindinteractive.project.core.data.demo.DemoAlertRepository
import org.northwindinteractive.project.core.data.demo.DemoAuthRepository
import org.northwindinteractive.project.core.data.demo.DemoBackend
import org.northwindinteractive.project.core.data.demo.DemoControls
import org.northwindinteractive.project.core.data.demo.DemoProfileRepository
import org.northwindinteractive.project.core.data.demo.DemoVenueRepository
import org.northwindinteractive.project.core.data.firebase.FirebaseAlertRepository
import org.northwindinteractive.project.core.data.firebase.FirebaseAuthRepository
import org.northwindinteractive.project.core.data.firebase.FirebaseProfileRepository
import org.northwindinteractive.project.core.data.firebase.FirebaseVenueRepository
import org.northwindinteractive.project.core.domain.repository.AlertRepository
import org.northwindinteractive.project.core.domain.repository.AuthRepository
import org.northwindinteractive.project.core.domain.repository.PreferencesRepository
import org.northwindinteractive.project.core.domain.repository.ProfileRepository
import org.northwindinteractive.project.core.domain.repository.VenueRepository
import org.northwindinteractive.project.core.domain.session.AlertSubmitter
import org.northwindinteractive.project.core.domain.session.VenueSessionCoordinator
import org.northwindinteractive.project.core.platform.BeaconScanner
import org.northwindinteractive.project.core.platform.Haptics
import org.northwindinteractive.project.core.platform.KableBeaconScanner
import org.northwindinteractive.project.core.platform.PresenceService
import org.northwindinteractive.project.core.platform.SimulatedPresenceService
import org.northwindinteractive.project.feature.alert.AlertDetailsViewModel
import org.northwindinteractive.project.feature.alert.AlertHistoryViewModel
import org.northwindinteractive.project.feature.auth.AuthViewModel
import org.northwindinteractive.project.feature.settings.SettingsViewModel
import org.northwindinteractive.project.feature.venue.HomeViewModel
import kotlin.time.Clock

/** Qualifier for the raw platform haptics; the app-wide [Haptics] respects user preferences. */
val PlatformHaptics = named("platformHaptics")

/** Qualifier for the device (OS) presence implementation provided by each platform module. */
val DevicePresence = named("devicePresence")

private val AppScope = named("appScope")

fun coreModule(config: AppConfig): Module = module {
    single { config }
    single<Clock> { Clock.System }
    single<CoroutineScope>(AppScope) { CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate) }

    single<PreferencesRepository> { LocalPreferencesRepository(get()) }
    single<Haptics> { PreferenceAwareHaptics(get(PlatformHaptics), get()) }

    when (config.presence) {
        PresenceMode.SIMULATED -> {
            single { SimulatedPresenceService(get()) }
            single<PresenceService> { get<SimulatedPresenceService>() }
        }
        PresenceMode.DEVICE -> single<PresenceService> { get(DevicePresence) }
    }
    single<BeaconScanner> { KableBeaconScanner(get()) }

    single {
        VenueSessionCoordinator(
            authRepository = get(),
            venueRepository = get(),
            alertRepository = get(),
            presenceService = get(),
            beaconScanner = get(),
            clock = get(),
            scope = get(AppScope),
        )
    }
    single { AlertSubmitter(get(), get(), get(), get(), get()) }
}

fun backendModule(config: AppConfig): Module = when (config.backend) {
    BackendMode.DEMO -> module {
        single {
            DemoBackend(get(), get(AppScope)).also { backend ->
                // Start the simulated visitor inside the first demo venue so the full flow is visible.
                getOrNull<SimulatedPresenceService>()
                    ?.setScenario(SimulatedPresenceService.Scenario.InsideVenue(backend.venues.first()))
            }
        }
        single<AuthRepository> { DemoAuthRepository(get(), get(), get(AppScope)) }
        single<ProfileRepository> { DemoProfileRepository(get()) }
        single<VenueRepository> { DemoVenueRepository(get()) }
        single<AlertRepository> { DemoAlertRepository(get()) }
        single { DemoControls(get(), getOrNull<SimulatedPresenceService>()) }
    }
    BackendMode.FIREBASE -> module {
        single { Firebase.auth }
        single { Firebase.firestore }
        single { Firebase.functions(config.functionsRegion) }
        single<AuthRepository> { FirebaseAuthRepository(get(), get(), get(), get(AppScope)) }
        single<ProfileRepository> { FirebaseProfileRepository(get(), get()) }
        single<VenueRepository> { FirebaseVenueRepository(get(), get(), get()) }
        single<AlertRepository> { FirebaseAlertRepository(get(), get(), get()) }
    }
}

val viewModelModule = module {
    viewModelOf(::AuthViewModel)
    viewModelOf(::HomeViewModel)
    viewModelOf(::AlertHistoryViewModel)
    viewModel { params -> AlertDetailsViewModel(params.get(), get(), get(), get()) }
    viewModel {
        SettingsViewModel(
            authRepository = get(),
            profileRepository = get(),
            preferencesRepository = get(),
            coordinator = get(),
            platformInfo = get(),
            config = get(),
            demoControls = getOrNull(),
        )
    }
}

private class PreferenceAwareHaptics(
    private val delegate: Haptics,
    private val preferences: PreferencesRepository,
) : Haptics {
    private val enabled get() = preferences.preferences.value.hapticConfirmation
    override fun confirm() { if (enabled) delegate.confirm() }
    override fun tick() { if (enabled) delegate.tick() }
    override fun warning() { if (enabled) delegate.warning() }
}
