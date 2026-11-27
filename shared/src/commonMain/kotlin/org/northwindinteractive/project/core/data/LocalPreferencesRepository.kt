package org.northwindinteractive.project.core.data

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.json.Json
import org.northwindinteractive.project.core.domain.model.UserPreferences
import org.northwindinteractive.project.core.domain.repository.PreferencesRepository
import org.northwindinteractive.project.core.platform.SecureStore

/** Device-local preferences. Contains no credentials or signing keys. */
class LocalPreferencesRepository(
    private val store: SecureStore,
) : PreferencesRepository {

    private val json = Json { ignoreUnknownKeys = true }

    private val _preferences = MutableStateFlow(load())
    override val preferences: StateFlow<UserPreferences> = _preferences.asStateFlow()

    private val _primerSeen = MutableStateFlow(store.getBoolean(KEY_PRIMER) ?: false)
    override val hasSeenLocationPrimer: StateFlow<Boolean> = _primerSeen.asStateFlow()

    override fun update(transform: (UserPreferences) -> UserPreferences) {
        val next = transform(_preferences.value)
        _preferences.value = next
        store.putString(KEY_PREFS, json.encodeToString(UserPreferences.serializer(), next))
    }

    override fun markLocationPrimerSeen() {
        _primerSeen.value = true
        store.putBoolean(KEY_PRIMER, true)
    }

    private fun load(): UserPreferences = store.getString(KEY_PREFS)
        ?.let { runCatching { json.decodeFromString(UserPreferences.serializer(), it) }.getOrNull() }
        ?: UserPreferences()

    private companion object {
        const val KEY_PREFS = "prefs.user"
        const val KEY_PRIMER = "prefs.locationPrimerSeen"
    }
}
