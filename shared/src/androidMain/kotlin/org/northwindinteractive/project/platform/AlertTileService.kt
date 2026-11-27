package org.northwindinteractive.project.platform

import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import android.service.quicksettings.TileService
import org.northwindinteractive.project.core.domain.model.ActivationMethod
import org.northwindinteractive.project.core.platform.ShortcutDispatcher

/** Intent action shared by the launcher shortcut and the Quick Settings tile. */
const val ACTION_DISCREET_ALERT = "org.northwindinteractive.project.action.DISCREET_ALERT"
const val EXTRA_ACTIVATION_METHOD = "activation_method"

/**
 * Routes a discreet-alert intent into shared code. Returns true if the intent was handled.
 * The shared AlertSubmitter still enforces registration and fresh on-site presence.
 */
fun handleDiscreetAlertIntent(intent: Intent?): Boolean {
    if (intent?.action != ACTION_DISCREET_ALERT) return false
    val method = intent.getStringExtra(EXTRA_ACTIVATION_METHOD)
        ?.let { runCatching { ActivationMethod.valueOf(it) }.getOrNull() }
        ?: ActivationMethod.APP_SHORTCUT
    ShortcutDispatcher.dispatch(method)
    // Prevent re-dispatch on configuration change / recreation.
    intent.action = null
    return true
}

/**
 * Quick Settings tile ("Angel alert"). Opens the app straight into alert submission; Android
 * doesn't allow sensitive actions from a locked tile without unlocking first.
 */
class AlertTileService : TileService() {
    override fun onClick() {
        super.onClick()
        val launch = packageManager.getLaunchIntentForPackage(packageName)?.apply {
            action = ACTION_DISCREET_ALERT
            putExtra(EXTRA_ACTIVATION_METHOD, ActivationMethod.QUICK_SETTINGS_TILE.name)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        } ?: return
        unlockAndRun {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                startActivityAndCollapse(
                    PendingIntent.getActivity(this, 0, launch, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT),
                )
            } else {
                @Suppress("DEPRECATION", "StartActivityAndCollapseDeprecated")
                startActivityAndCollapse(launch)
            }
        }
    }
}
