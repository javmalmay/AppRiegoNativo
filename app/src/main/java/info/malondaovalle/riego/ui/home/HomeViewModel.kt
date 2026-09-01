package info.malondaovalle.riego.ui.home

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import info.malondaovalle.riego.data.auth.AuthRepository
import info.malondaovalle.riego.data.devices.Device
import info.malondaovalle.riego.data.devices.DeviceActionResult
import info.malondaovalle.riego.data.devices.DevicesRepository
import info.malondaovalle.riego.data.devices.DevicesResult
import info.malondaovalle.riego.data.discovery.DeviceDiscoveryService
import info.malondaovalle.riego.data.discovery.DeviceTcpClient
import info.malondaovalle.riego.data.discovery.DiscoveredDevice
import info.malondaovalle.riego.data.discovery.TokenPushResult
import info.malondaovalle.riego.data.discovery.normalizeMac
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
    /** `true` while the UDP discovery sweep is running. */
    val discovering: Boolean = false,
    /** Every device that answered discovery (keeps their ip/port for local control). */
    val discovered: List<DiscoveredDevice> = emptyList(),
    /** Normalized MACs of registered devices currently reachable on the LAN. */
    val localMacs: Set<String> = emptySet(),
    /** Discovered on the LAN but not yet registered in the account. */
    val newDevices: List<DiscoveredDevice> = emptyList(),
    /** Normalized MACs of discovered devices currently being associated. */
    val addingMacs: Set<String> = emptySet(),
    /** One-shot message to surface in a snackbar. */
    val actionMessage: String? = null,
    /** Multi-select mode, entered with a long press on a device card. */
    val selectionMode: Boolean = false,
    /** Ids of the devices currently selected. */
    val selectedIds: Set<Int> = emptySet(),
    /** `true` while the selected devices are being deleted. */
    val deleting: Boolean = false,
) {
    val showEmpty: Boolean
        get() = !loading && error == null && devices.isEmpty()
}

class HomeViewModel(
    private val authRepository: AuthRepository,
    private val devicesRepository: DevicesRepository,
    private val discoveryService: DeviceDiscoveryService,
    private val deviceTcpClient: DeviceTcpClient,
) : ViewModel() {

    private val _state = MutableStateFlow(HomeUiState())
    val state: StateFlow<HomeUiState> = _state.asStateFlow()

    // Discovery is best-effort UDP: a device may miss a scan. Keep what we've seen
    // recently (keyed by normalized MAC) so its "local" status doesn't flicker.
    private val seenDevices = LinkedHashMap<String, DiscoveredDevice>()
    private val seenAt = HashMap<String, Long>()

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
                is DevicesResult.Success -> {
                    _state.update { it.copy(loading = false, devices = result.devices) }
                    discoverLocalDevices(result.devices)
                }
                is DevicesResult.Error ->
                    _state.update { it.copy(loading = false, error = result.message) }
            }
        }
    }

    /** UDP-broadcasts on the LAN and splits results into local / new. */
    fun discoverLocalDevices(registered: List<Device> = _state.value.devices) {
        if (_state.value.discovering) return
        _state.update { it.copy(discovering = true) }
        viewModelScope.launch {
            val found = runCatching { discoveryService.discover() }.getOrDefault(emptyList())

            val now = System.currentTimeMillis()
            found.forEach { device ->
                val key = normalizeMac(device.mac)
                seenDevices[key] = device
                seenAt[key] = now
            }
            // Forget devices not seen for a while so the list doesn't grow forever.
            val stale = seenAt.filterValues { now - it > SEEN_TTL_MS }.keys.toList()
            stale.forEach { seenDevices.remove(it); seenAt.remove(it) }

            val known = seenDevices.values.toList()
            val registeredMacs = registered.mapTo(HashSet()) { normalizeMac(it.macAddress) }
            val localMacs = seenDevices.keys.toHashSet()

            Log.d(
                TAG,
                "discovery: this scan=${found.map { it.mac }} known=$localMacs registered=$registeredMacs",
            )

            _state.update { current ->
                current.copy(
                    discovering = false,
                    discovered = known,
                    localMacs = localMacs,
                    newDevices = known.filter { normalizeMac(it.mac) !in registeredMacs },
                )
            }
        }
    }

    /** Associates a discovered device with the account (POST /api/Devices). */
    fun addDiscoveredDevice(device: DiscoveredDevice) {
        val mac = normalizeMac(device.mac)
        if (mac in _state.value.addingMacs) return
        _state.update { it.copy(addingMacs = it.addingMacs + mac) }
        viewModelScope.launch {
            when (val result = devicesRepository.registerDevice(device.mac, device.name)) {
                is DeviceActionResult.Success -> {
                    val message = provisionDevice(device, result.deviceToken)
                    _state.update {
                        it.copy(
                            addingMacs = it.addingMacs - mac,
                            newDevices = it.newDevices.filterNot { d -> normalizeMac(d.mac) == mac },
                            actionMessage = message,
                        )
                    }
                    loadDevices()
                }
                is DeviceActionResult.Error ->
                    _state.update {
                        it.copy(addingMacs = it.addingMacs - mac, actionMessage = result.message)
                    }
            }
        }
    }

    /**
     * After the API associates the device, open a TCP connection to the device's
     * advertised `ip:port` and hand it the access token so it can reach the server.
     */
    private suspend fun provisionDevice(device: DiscoveredDevice, deviceToken: String?): String {
        val port = device.port
        if (deviceToken.isNullOrBlank() || device.ip.isBlank() || port == null) {
            return "Dispositivo agregado (no se le pudo enviar el token)"
        }
        return when (deviceTcpClient.sendToken(device.ip, port, deviceToken)) {
            TokenPushResult.OK -> "Dispositivo agregado y configurado"
            TokenPushResult.REJECTED -> "Dispositivo agregado, pero rechazó el token"
            TokenPushResult.UNREACHABLE -> "Dispositivo agregado, pero no respondió al configurarlo"
        }
    }

    fun consumeActionMessage() = _state.update { it.copy(actionMessage = null) }

    // --- Selection / deletion -------------------------------------------------

    fun onDeviceLongPress(id: Int) = _state.update {
        if (it.selectionMode) it.toggleSelected(id)
        else it.copy(selectionMode = true, selectedIds = setOf(id))
    }

    fun onDeviceClick(id: Int) = _state.update {
        if (it.selectionMode) it.toggleSelected(id) else it
    }

    fun exitSelection() = _state.update {
        it.copy(selectionMode = false, selectedIds = emptySet())
    }

    private fun HomeUiState.toggleSelected(id: Int): HomeUiState {
        val next = if (id in selectedIds) selectedIds - id else selectedIds + id
        return copy(selectedIds = next, selectionMode = next.isNotEmpty())
    }

    /** Deletes every selected device (DELETE /api/Devices/{id}). */
    fun deleteSelected() {
        val ids = _state.value.selectedIds.toList()
        if (ids.isEmpty() || _state.value.deleting) return
        _state.update { it.copy(deleting = true) }
        viewModelScope.launch {
            var deleted = 0
            var firstError: String? = null
            for (id in ids) {
                when (val result = devicesRepository.deleteDevice(id)) {
                    is DeviceActionResult.Success -> deleted++
                    is DeviceActionResult.Error -> if (firstError == null) firstError = result.message
                }
            }
            _state.update {
                it.copy(
                    deleting = false,
                    selectionMode = false,
                    selectedIds = emptySet(),
                    actionMessage = when {
                        deleted == 0 -> firstError ?: "No se pudo eliminar"
                        firstError != null -> "$deleted eliminado(s); error: $firstError"
                        deleted == 1 -> "Dispositivo eliminado"
                        else -> "$deleted dispositivos eliminados"
                    },
                )
            }
            loadDevices()
        }
    }

    fun logout() {
        viewModelScope.launch {
            authRepository.logout()
            _state.update { it.copy(loggedOut = true) }
        }
    }

    private companion object {
        const val TAG = "HomeDiscovery"
        const val SEEN_TTL_MS = 3 * 60 * 1000L
    }
}
