package info.malondaovalle.riego.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import info.malondaovalle.riego.data.auth.AuthRepository
import info.malondaovalle.riego.data.devices.Device
import info.malondaovalle.riego.data.devices.DevicesRepository
import info.malondaovalle.riego.data.devices.DevicesResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class HomeUiState(
    val username: String = "",
    val loggedOut: Boolean = false,
    val loading: Boolean = false,
    val error: String? = null,
    val devices: List<Device> = emptyList(),
) {
    val showEmpty: Boolean get() = !loading && error == null && devices.isEmpty()
}

class HomeViewModel(
    private val authRepository: AuthRepository,
    private val devicesRepository: DevicesRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(HomeUiState())
    val state: StateFlow<HomeUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val name = authRepository.currentSession()?.username.orEmpty()
            _state.update { it.copy(username = name) }
        }
        loadDevices()
    }

    fun loadDevices() {
        if (_state.value.loading) return
        _state.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            when (val result = devicesRepository.getDevices()) {
                is DevicesResult.Success ->
                    _state.update { it.copy(loading = false, devices = result.devices) }
                is DevicesResult.Error ->
                    _state.update { it.copy(loading = false, error = result.message) }
            }
        }
    }

    fun logout() {
        viewModelScope.launch {
            authRepository.logout()
            _state.update { it.copy(loggedOut = true) }
        }
    }
}
