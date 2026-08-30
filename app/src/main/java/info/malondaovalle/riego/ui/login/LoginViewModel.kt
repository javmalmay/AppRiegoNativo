package info.malondaovalle.riego.ui.login

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import info.malondaovalle.riego.data.auth.AuthRepository
import info.malondaovalle.riego.data.auth.SubmitResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class LoginUiState(
    val username: String = "",
    val password: String = "",
    val loading: Boolean = false,
    val error: String? = null,
    val loggedIn: Boolean = false,
) {
    val canSubmit: Boolean
        get() = username.isNotBlank() && password.isNotBlank() && !loading
}

class LoginViewModel(private val repository: AuthRepository) : ViewModel() {

    private val _state = MutableStateFlow(LoginUiState())
    val state: StateFlow<LoginUiState> = _state.asStateFlow()

    fun onUsernameChange(value: String) = _state.update { it.copy(username = value, error = null) }
    fun onPasswordChange(value: String) = _state.update { it.copy(password = value, error = null) }

    fun submit() {
        val current = _state.value
        if (!current.canSubmit) return
        _state.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            when (val result = repository.login(current.username, current.password)) {
                is SubmitResult.Success -> _state.update { it.copy(loading = false, loggedIn = true) }
                is SubmitResult.Error ->
                    _state.update { it.copy(loading = false, error = result.message) }
            }
        }
    }
}
