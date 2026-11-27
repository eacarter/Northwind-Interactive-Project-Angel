package org.northwindinteractive.project

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import org.jetbrains.compose.resources.painterResource
import org.koin.compose.koinInject
import org.northwindinteractive.project.core.AppConfig
import org.northwindinteractive.project.core.BackendMode
import org.northwindinteractive.project.core.designsystem.AngelTheme
import org.northwindinteractive.project.core.domain.model.AuthState
import org.northwindinteractive.project.core.domain.repository.AuthRepository
import org.northwindinteractive.project.core.domain.repository.PreferencesRepository
import org.northwindinteractive.project.core.domain.session.AlertSubmitter
import org.northwindinteractive.project.core.domain.session.VenueSessionCoordinator
import org.northwindinteractive.project.core.platform.ShortcutDispatcher
import org.northwindinteractive.project.feature.alert.AlertDetailsScreen
import org.northwindinteractive.project.feature.alert.AlertHistoryScreen
import org.northwindinteractive.project.feature.auth.CreateAccountScreen
import org.northwindinteractive.project.feature.auth.SignInScreen
import org.northwindinteractive.project.feature.auth.WelcomeScreen
import org.northwindinteractive.project.feature.settings.SettingsScreen
import org.northwindinteractive.project.feature.venue.HomeScreen
import org.northwindinteractive.project.navigation.AuthRoute
import org.northwindinteractive.project.navigation.MainRoute
import org.northwindinteractive.project.resources.Res
import org.northwindinteractive.project.resources.angel_wings

@Composable
fun App() {
    val preferencesRepository = koinInject<PreferencesRepository>()
    val preferences by preferencesRepository.preferences.collectAsStateWithLifecycle()
    AngelTheme(themeMode = preferences.themeMode, reducedMotion = preferences.reducedMotion) {
        AngelRoot()
    }
}

@Composable
private fun AngelRoot() {
    val authRepository = koinInject<AuthRepository>()
    val coordinator = koinInject<VenueSessionCoordinator>()
    val config = koinInject<AppConfig>()
    val auth by authRepository.authState.collectAsStateWithLifecycle()

    // Location is collected only while the app is visible.
    LifecycleEventEffect(Lifecycle.Event.ON_START) { coordinator.setForeground(true) }
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) { coordinator.setForeground(false) }

    when (auth) {
        AuthState.Loading -> Splash()
        AuthState.SignedOut -> {
            // A shortcut fired while signed out must never send later on sign-in.
            LaunchedEffect(Unit) { ShortcutDispatcher.requests.collect { ShortcutDispatcher.consume() } }
            AuthGraph(isDemo = config.backend == BackendMode.DEMO)
        }
        is AuthState.SignedIn -> MainGraph()
    }
}

@Composable
private fun Splash() {
    Box(Modifier.fillMaxSize().background(AngelTheme.colors.canvas), contentAlignment = Alignment.Center) {
        Image(painterResource(Res.drawable.angel_wings), contentDescription = "Project Angel", modifier = Modifier.size(96.dp))
    }
}

@Composable
private fun AuthGraph(isDemo: Boolean) {
    val nav = rememberNavController()
    NavHost(navController = nav, startDestination = AuthRoute.Welcome) {
        composable<AuthRoute.Welcome> {
            WelcomeScreen(
                onSignIn = { nav.navigate(AuthRoute.SignIn) },
                onCreateAccount = { nav.navigate(AuthRoute.CreateAccount) },
                isDemo = isDemo,
            )
        }
        composable<AuthRoute.SignIn> {
            SignInScreen(
                onBack = { nav.popBackStack() },
                onCreateAccount = {
                    nav.navigate(AuthRoute.CreateAccount) { popUpTo(AuthRoute.Welcome) }
                },
            )
        }
        composable<AuthRoute.CreateAccount> {
            CreateAccountScreen(
                onBack = { nav.popBackStack() },
                onSignIn = { nav.navigate(AuthRoute.SignIn) { popUpTo(AuthRoute.Welcome) } },
            )
        }
    }
}

@Composable
private fun MainGraph() {
    val nav = rememberNavController()
    val alertSubmitter = koinInject<AlertSubmitter>()

    // Discreet native triggers (App Intents, launcher shortcut, Quick Settings tile).
    LaunchedEffect(Unit) {
        ShortcutDispatcher.requests.collect { method ->
            ShortcutDispatcher.consume()
            nav.navigate(MainRoute.Home) {
                popUpTo(MainRoute.Home) { inclusive = true }
                launchSingleTop = true
            }
            alertSubmitter.submit(method)
        }
    }

    NavHost(navController = nav, startDestination = MainRoute.Home) {
        composable<MainRoute.Home> {
            HomeScreen(
                onOpenIncident = { id -> nav.navigate(MainRoute.AlertDetails(id)) { launchSingleTop = true } },
                onOpenHistory = { nav.navigate(MainRoute.AlertHistory) },
                onOpenSettings = { nav.navigate(MainRoute.Settings) },
            )
        }
        composable<MainRoute.AlertDetails> { entry ->
            val route = entry.toRoute<MainRoute.AlertDetails>()
            AlertDetailsScreen(incidentId = route.incidentId, onBack = { nav.popBackStack() })
        }
        composable<MainRoute.AlertHistory> {
            AlertHistoryScreen(
                onBack = { nav.popBackStack() },
                onOpenIncident = { id -> nav.navigate(MainRoute.AlertDetails(id)) },
            )
        }
        composable<MainRoute.Settings> {
            SettingsScreen(onBack = { nav.popBackStack() })
        }
    }
}
