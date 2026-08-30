package info.malondaovalle.riego.ui.register

import android.util.Patterns
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import info.malondaovalle.riego.data.auth.AuthRepository
import info.malondaovalle.riego.data.auth.SubmitResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class RegisterUiState(
    val username: String = "",
    val email: String = "",
    val password: String = "",
    val loading: Boolean = false,
    val error: String? = null,
    val successMessage: String? = null,
) {
    val emailValid: Boolean
        get() = email.isBlank() || Patterns.EMAIL_ADDRESS.matcher(email.trim()).matches()

    val canSubmit: Boolean
        get() = username.isNotBlank() &&
            email.isNotBlank() &&
            Patterns.EMAIL_ADDRESS.matcher(email.trim()).matches() &&
            password.length >= MIN_PASSWORD &&
            !loading

    companion object {
        const val MIN_PASSWORD = 6
    }
}

class RegisterViewModel(private val repository: AuthRepository) : ViewModel() {

    private val _state = MutableStateFlow(RegisterUiState())
    val state: StateFlow<RegisterUiState> = _state.asStateFlow()

    fun onUsernameChange(value: String) = _state.update { it.copy(username = value, error = null) }
    fun onEmailChange(value: String) = _state.update { it.copy(email = value, error = null) }
    fun onPasswordChange(value: String) = _state.update { it.copy(password = value, error = null) }

    fun submit() {
        val current = _state.value
        if (!current.canSubmit) return
        _state.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            when (val result = repository.register(current.username, current.email, current.password)) {
                is SubmitResult.Success ->
                    _state.update {
                        it.copy(
                            loading = false,
                            successMessage = result.message ?: "Usuario creado correctamente",
                        )
                    }
                is SubmitResult.Error ->
                    _state.update { it.copy(loading = false, error = result.message) }
            }
        }
    }

    fun consumeSuccess() = _state.update { it.copy(successMessage = null) }
}
