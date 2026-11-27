package org.northwindinteractive.project

import android.graphics.Bitmap
import android.os.Looper
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import org.koin.mp.KoinPlatform
import org.northwindinteractive.project.core.AppConfig
import org.northwindinteractive.project.core.BackendMode
import org.northwindinteractive.project.core.data.demo.DemoControls
import org.northwindinteractive.project.core.domain.model.ActivationMethod
import org.northwindinteractive.project.core.domain.model.LocationPermission
import org.northwindinteractive.project.core.domain.model.ThemeMode
import org.northwindinteractive.project.core.domain.repository.AuthRepository
import org.northwindinteractive.project.core.domain.repository.PreferencesRepository
import org.northwindinteractive.project.core.domain.session.AlertSubmitter
import org.northwindinteractive.project.core.domain.session.VenueSessionCoordinator
import org.northwindinteractive.project.core.domain.session.VisitPhase
import org.northwindinteractive.project.core.platform.Haptics
import org.northwindinteractive.project.core.platform.PlatformInfo
import org.northwindinteractive.project.core.platform.SecureStore
import org.northwindinteractive.project.core.platform.SimulatedPresenceService.Scenario
import org.northwindinteractive.project.di.PlatformHaptics
import org.northwindinteractive.project.di.initKoin
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit

/**
 * Renders the main screens against the demo backend and writes PNGs to
 * `shared/build/screenshots`. Run with: ./gradlew :shared:testAndroidHostTest -Pangel.screenshots --tests "*ScreenshotTour*"
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w393dp-h852dp-xxhdpi")
class ScreenshotTour {

    @get:Rule
    val compose = createComposeRule()

    private val outDir = File(System.getProperty("angel.screenshots") ?: "build/screenshots").apply { mkdirs() }

    private inline fun <reified T : Any> koin(): T = KoinPlatform.getKoin().get()

    @After
    fun tearDown() = stopKoin()

    private fun start(theme: ThemeMode) {
        val memory = mutableMapOf<String, Any>()
        initKoin(
            config = AppConfig(backend = BackendMode.DEMO),
            platformModule = module {
                single<SecureStore> {
                    object : SecureStore {
                        override fun getString(key: String) = memory[key] as? String
                        override fun putString(key: String, value: String) { memory[key] = value }
                        override fun getBoolean(key: String) = memory[key] as? Boolean
                        override fun putBoolean(key: String, value: Boolean) { memory[key] = value }
                        override fun remove(key: String) { memory.remove(key) }
                    }
                }
                single<PlatformInfo> {
                    object : PlatformInfo {
                        override val platformName = "Android"
                        override val osVersion = "15"
                        override val appVersion = "0.1.0"
                        override fun openAppSettings() = Unit
                        override fun openLocationSettings() = Unit
                    }
                }
                single<Haptics>(PlatformHaptics) {
                    object : Haptics {
                        override fun confirm() = Unit
                        override fun tick() = Unit
                        override fun warning() = Unit
                    }
                }
            },
        )
        // Static rendering: disable decorative infinite animations.
        koin<PreferencesRepository>().update { it.copy(themeMode = theme, reducedMotion = true) }
        compose.setContent { App() }
        settle()
    }

    private fun settle(millis: Long = 400) {
        repeat((millis / 100).toInt().coerceAtLeast(1)) {
            shadowOf(Looper.getMainLooper()).idleFor(100, TimeUnit.MILLISECONDS)
            compose.mainClock.advanceTimeBy(100)
        }
        compose.waitForIdle()
    }

    private fun waitFor(timeoutMs: Long = 20_000, condition: () -> Boolean) {
        var waited = 0L
        while (!condition() && waited < timeoutMs) {
            settle(200)
            waited += 200
        }
        check(condition()) { "Timed out waiting for condition" }
        settle(600)
    }

    private fun snap(name: String) {
        val bitmap = compose.onRoot().captureToImage().asAndroidBitmap()
        FileOutputStream(File(outDir, "$name.png")).use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    private fun phase() = koin<VenueSessionCoordinator>().state.value.phase

    private fun signIn() {
        runBlocking { koin<AuthRepository>().createAccount("visitor@example.com", "correct-horse", "Sam") }
        settle(1_000)
    }

    @Test
    fun lightTour() {
        start(ThemeMode.LIGHT)
        snap("01_welcome_light")

        compose.onNodeWithText("Sign In").performClick()
        settle()
        snap("02_sign_in_light")

        signIn()
        waitFor { phase() == VisitPhase.PERMISSION_REQUIRED }
        snap("03_location_primer_light")

        compose.onNodeWithText("Allow location access").performClick()
        waitFor { phase() == VisitPhase.INSIDE_NOT_JOINED }
        snap("04_registration_light")

        compose.onNodeWithText("Register for Vesper Lounge").performScrollTo().performClick()
        waitFor { phase() == VisitPhase.INSIDE_ACTIVE }
        snap("05_active_venue_light")

        runBlocking { koin<AlertSubmitter>().submit(ActivationMethod.IN_APP_HOLD) }
        waitFor { koin<VenueSessionCoordinator>().state.value.openIncident != null }
        settle(1_000)
        snap("06_alert_received_light")

        settle(7_000)
        snap("07_alert_acknowledged_light")

        val demo = koin<DemoControls>()
        demo.setScenario(Scenario.OutsideCoverage)
        androidx.test.espresso.Espresso.pressBack()
        waitFor { phase() == VisitPhase.EXITED_GRACE && koin<VenueSessionCoordinator>().state.value.registration?.expiresAt != null }
        snap("08_exited_grace_light")
    }

    @Test
    fun darkTour() {
        start(ThemeMode.DARK)
        snap("11_welcome_dark")
        signIn()
        val demo = koin<DemoControls>()
        demo.setPermission(LocationPermission.GRANTED_FOREGROUND)
        waitFor { phase() == VisitPhase.INSIDE_NOT_JOINED }
        snap("12_registration_dark")

        compose.onNodeWithText("Register for Vesper Lounge").performScrollTo().performClick()
        waitFor { phase() == VisitPhase.INSIDE_ACTIVE }
        snap("13_active_venue_dark")

        runBlocking { koin<AlertSubmitter>().submit(ActivationMethod.IN_APP_HOLD) }
        waitFor { koin<VenueSessionCoordinator>().state.value.openIncident != null }
        settle(8_000)
        snap("14_alert_details_dark")
    }

    @Test
    fun edgeStates() {
        start(ThemeMode.LIGHT)
        signIn()
        val demo = koin<DemoControls>()
        demo.setPermission(LocationPermission.GRANTED_FOREGROUND)

        demo.setScenario(Scenario.OutsideCoverage)
        waitFor { phase() == VisitPhase.OUTSIDE_COVERAGE }
        snap("21_outside_coverage")

        demo.setScenario(Scenario.Imprecise(demo.venues.first()))
        waitFor { phase() == VisitPhase.LOCATION_UNKNOWN }
        snap("22_location_unknown")

        demo.setPermission(LocationPermission.DENIED_PERMANENTLY)
        waitFor { phase() == VisitPhase.PERMISSION_REQUIRED }
        snap("23_permission_denied")

        demo.setPermission(LocationPermission.GRANTED_FOREGROUND)
        demo.setScenario(Scenario.InsideVenue(demo.venues.first { it.venueId == "atrium-club" }))
        waitFor { phase() == VisitPhase.INSIDE_NOT_JOINED }
        demo.setAutoApprove(false)
        compose.onNodeWithText("Request access").performScrollTo().performClick()
        waitFor { phase() == VisitPhase.PENDING_APPROVAL }
        snap("24_pending_approval")

        compose.onNodeWithContentDescription("Account and settings").performClick()
        settle(600)
        snap("25_settings")
    }
}
