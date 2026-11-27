package org.northwindinteractive.project.feature.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.northwindinteractive.project.core.domain.model.toAngelError
import org.northwindinteractive.project.core.domain.repository.AuthRepository

data class AuthFormState(
    val email: String = "",
    val password: String = "",
    val displayAlias: String = "",
    val isSubmitting: Boolean = false,
    val error: String? = null,
    val info: String? = null,
) {
    val emailValid: Boolean get() = EMAIL_REGEX.matches(email.trim())
    val canSignIn: Boolean get() = emailValid && password.isNotEmpty() && !isSubmitting
    val canCreate: Boolean get() = emailValid && password.length >= MIN_PASSWORD && !isSubmitting

    companion object {
        const val MIN_PASSWORD = 8
        private val EMAIL_REGEX = Regex("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")
    }
}

class AuthViewModel(
    private val authRepository: AuthRepository,
) : ViewModel() {

    private val _form = MutableStateFlow(AuthFormState())
    val form: StateFlow<AuthFormState> = _form.asStateFlow()

    fun onEmailChange(value: String) = _form.update { it.copy(email = value, error = null, info = null) }
    fun onPasswordChange(value: String) = _form.update { it.copy(password = value, error = null) }
    fun onAliasChange(value: String) = _form.update { it.copy(displayAlias = value.take(40)) }

    fun clearMessages() = _form.update { it.copy(error = null, info = null) }

    fun signIn() {
        val state = _form.value
        if (!state.canSignIn) return
        submit { authRepository.signIn(state.email, state.password) }
    }

    fun createAccount() {
        val state = _form.value
        if (!state.canCreate) return
        submit { authRepository.createAccount(state.email, state.password, state.displayAlias.ifBlank { null }) }
    }

    fun sendPasswordReset() {
        val state = _form.value
        if (!state.emailValid) {
            _form.update { it.copy(error = "Enter your email address first") }
            return
        }
        viewModelScope.launch {
            _form.update { it.copy(isSubmitting = true, error = null) }
            authRepository.sendPasswordReset(state.email)
                .onSuccess { _form.update { it.copy(isSubmitting = false, info = "If an account exists, a reset link is on its way.") } }
                .onFailure { e -> _form.update { it.copy(isSubmitting = false, error = e.toAngelError().message) } }
        }
    }

    private fun submit(action: suspend () -> Result<Unit>) {
        viewModelScope.launch {
            _form.update { it.copy(isSubmitting = true, error = null, info = null) }
            action()
                .onSuccess { _form.update { it.copy(isSubmitting = false, password = "") } }
                .onFailure { e -> _form.update { it.copy(isSubmitting = false, error = e.toAngelError().message) } }
        }
    }
}
