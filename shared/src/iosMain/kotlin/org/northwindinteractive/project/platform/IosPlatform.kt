package org.northwindinteractive.project.platform

import com.liftric.kvault.KVault
import org.northwindinteractive.project.core.platform.Haptics
import org.northwindinteractive.project.core.platform.PlatformInfo
import org.northwindinteractive.project.core.platform.SecureStore
import platform.Foundation.NSBundle
import platform.Foundation.NSURL
import platform.UIKit.UIApplication
import platform.UIKit.UIApplicationOpenSettingsURLString
import platform.UIKit.UIDevice
import platform.UIKit.UIImpactFeedbackGenerator
import platform.UIKit.UIImpactFeedbackStyle
import platform.UIKit.UINotificationFeedbackGenerator
import platform.UIKit.UINotificationFeedbackType

class IosPlatformInfo : PlatformInfo {
    override val platformName: String = "iOS"
    override val osVersion: String = UIDevice.currentDevice.systemVersion
    override val appVersion: String =
        (NSBundle.mainBundle.infoDictionary?.get("CFBundleShortVersionString") as? String) ?: "dev"

    override fun openAppSettings() {
        val url = NSURL.URLWithString(UIApplicationOpenSettingsURLString) ?: return
        UIApplication.sharedApplication.openURL(url, options = emptyMap<Any?, Any?>(), completionHandler = null)
    }

    /** iOS doesn't allow deep-linking to Location Services; the app's settings page has the toggle. */
    override fun openLocationSettings() = openAppSettings()
}

class IosHaptics : Haptics {
    override fun confirm() {
        UINotificationFeedbackGenerator().notificationOccurred(UINotificationFeedbackType.UINotificationFeedbackTypeSuccess)
    }

    override fun tick() {
        UIImpactFeedbackGenerator(style = UIImpactFeedbackStyle.UIImpactFeedbackStyleLight).impactOccurred()
    }

    override fun warning() {
        UINotificationFeedbackGenerator().notificationOccurred(UINotificationFeedbackType.UINotificationFeedbackTypeWarning)
    }
}

/** Keychain-backed store. */
class KeychainSecureStore : SecureStore {
    private val vault = KVault(serviceName = "org.northwindinteractive.project.angel", accessGroup = null)
    override fun getString(key: String): String? = vault.string(key)
    override fun putString(key: String, value: String) { vault.set(key, value) }
    override fun getBoolean(key: String): Boolean? = vault.bool(key)
    override fun putBoolean(key: String, value: Boolean) { vault.set(key, value) }
    override fun remove(key: String) { vault.deleteObject(key) }
}
