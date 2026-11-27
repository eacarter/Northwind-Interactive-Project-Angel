package org.northwindinteractive.project.platform

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.provider.Settings
import com.google.firebase.FirebaseApp
import com.liftric.kvault.KVault
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
import kotlin.time.Clock

class AndroidPlatformInfo(private val context: Context) : PlatformInfo {
    override val platformName: String = "Android"
    override val osVersion: String = "${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})"
    override val appVersion: String = runCatching {
        context.packageManager.getPackageInfo(context.packageName, 0).versionName
    }.getOrNull() ?: "dev"

    override fun openAppSettings() {
        context.startActivity(
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }

    override fun openLocationSettings() {
        context.startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}

class AndroidHaptics(context: Context) : Haptics {
    private val vibrator: Vibrator? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        context.getSystemService(VibratorManager::class.java)?.defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        context.getSystemService(Vibrator::class.java)
    }

    /** Two firm pulses: recognisable by touch alone, no bright flashing UI needed. */
    override fun confirm() = vibrate(VibrationEffect.createWaveform(longArrayOf(0, 90, 120, 90), -1))

    override fun tick() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_TICK))
        } else {
            vibrate(VibrationEffect.createOneShot(15, VibrationEffect.DEFAULT_AMPLITUDE))
        }
    }

    override fun warning() = vibrate(VibrationEffect.createWaveform(longArrayOf(0, 250), -1))

    private fun vibrate(effect: VibrationEffect) {
        runCatching { if (vibrator?.hasVibrator() == true) vibrator.vibrate(effect) }
    }
}

class KVaultSecureStore(context: Context) : SecureStore {
    private val vault = KVault(context, "angel_secure_prefs")
    override fun getString(key: String): String? = vault.string(key)
    override fun putString(key: String, value: String) { vault.set(key, value) }
    override fun getBoolean(key: String): Boolean? = vault.bool(key)
    override fun putBoolean(key: String, value: Boolean) { vault.set(key, value) }
    override fun remove(key: String) { vault.deleteObject(key) }
}

/**
 * Android entry point used by `AngelApplication`. Firebase is used automatically once
 * `google-services.json` is added (the Gradle plugin then initialises the default FirebaseApp).
 */
object AndroidAngel {
    fun start(context: Context) {
        val app = context.applicationContext
        val firebaseReady = runCatching { FirebaseApp.getApps(app).isNotEmpty() || FirebaseApp.initializeApp(app) != null }
            .getOrDefault(false)
        val config = AppConfig(backend = if (firebaseReady) BackendMode.FIREBASE else BackendMode.DEMO)
        initKoin(
            config = config,
            platformModule = module {
                single<Context> { app }
                single<SecureStore> { KVaultSecureStore(app) }
                single<PlatformInfo> { AndroidPlatformInfo(app) }
                single<Haptics>(PlatformHaptics) { AndroidHaptics(app) }
                single<PresenceService>(DevicePresence) { AndroidPresenceService(app, Clock.System) }
            },
        )
    }
}
