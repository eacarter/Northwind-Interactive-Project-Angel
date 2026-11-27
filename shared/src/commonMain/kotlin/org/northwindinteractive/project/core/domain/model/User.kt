package org.northwindinteractive.project.core.domain.model

import kotlinx.serialization.Serializable
import kotlin.time.Instant

/** Persistent Firebase Auth user. Minimal PII by design. */
@Serializable
data class UserProfile(
    val uid: String,
    val email: String?,
    val displayAlias: String?,
    val createdAt: Instant?,
    val preferences: UserPreferences = UserPreferences(),
)

@Serializable
data class UserPreferences(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val reducedMotion: Boolean = false,
    val hapticConfirmation: Boolean = true,
)

@Serializable
enum class ThemeMode { SYSTEM, LIGHT, DARK }

sealed interface AuthState {
    data object Loading : AuthState
    data object SignedOut : AuthState
    data class SignedIn(val uid: String, val email: String?) : AuthState
}
