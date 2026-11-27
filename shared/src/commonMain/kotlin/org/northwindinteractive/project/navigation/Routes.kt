package org.northwindinteractive.project.navigation

import kotlinx.serialization.Serializable

/** Signed-out graph. */
sealed interface AuthRoute {
    @Serializable data object Welcome : AuthRoute
    @Serializable data object SignIn : AuthRoute
    @Serializable data object CreateAccount : AuthRoute
}

/** Signed-in graph. */
sealed interface MainRoute {
    @Serializable data object Home : MainRoute
    @Serializable data class AlertDetails(val incidentId: String) : MainRoute
    @Serializable data object AlertHistory : MainRoute
    @Serializable data object Settings : MainRoute
}
