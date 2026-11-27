package org.northwindinteractive.project.platform

import androidx.activity.ComponentActivity
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import kotlinx.coroutines.CompletableDeferred
import java.lang.ref.WeakReference

/**
 * Connects the shared [AndroidPresenceService] to the current Activity's permission launcher.
 * MainActivity calls [register] in onCreate (before STARTED, as the Activity Result API requires).
 */
object AndroidPermissionBridge {
    private var launcher: ActivityResultLauncher<Array<String>>? = null
    private var pending: CompletableDeferred<Map<String, Boolean>>? = null
    private var activityRef: WeakReference<ComponentActivity>? = null

    val activity: ComponentActivity? get() = activityRef?.get()

    fun register(activity: ComponentActivity) {
        activityRef = WeakReference(activity)
        launcher = activity.registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
            pending?.complete(result)
            pending = null
        }
    }

    suspend fun request(permissions: Array<String>): Map<String, Boolean> {
        val activeLauncher = launcher ?: return permissions.associateWith { false }
        pending?.cancel()
        val deferred = CompletableDeferred<Map<String, Boolean>>()
        pending = deferred
        activeLauncher.launch(permissions)
        return deferred.await()
    }

    fun shouldShowRationale(permission: String): Boolean =
        activity?.shouldShowRequestPermissionRationale(permission) ?: false
}
