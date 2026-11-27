package org.northwindinteractive.project.core.platform

import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import org.northwindinteractive.project.core.domain.model.ActivationMethod

/**
 * Bridge for discreet native triggers (iOS App Intents / Back Tap via Shortcuts, Android launcher
 * shortcuts and Quick Settings tile). Native entry points call [dispatch]; shared code decides
 * whether an alert may be sent. A shortcut never bypasses eligibility checks.
 */
object ShortcutDispatcher {
    private val _requests = MutableSharedFlow<ActivationMethod>(
        replay = 1,
        extraBufferCapacity = 0,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    val requests: SharedFlow<ActivationMethod> = _requests.asSharedFlow()

    fun dispatch(method: ActivationMethod) {
        _requests.tryEmit(method)
    }

    /** Called once a request has been handled so it is not replayed on recomposition. */
    fun consume() {
        _requests.resetReplayCache()
    }

    /** Swift-friendly entry point for the App Intent. */
    fun dispatchFromAppIntent() = dispatch(ActivationMethod.APP_INTENT)
}
