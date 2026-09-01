package info.malondaovalle.riego.ui.device

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import info.malondaovalle.riego.data.device.CommandResult
import info.malondaovalle.riego.data.device.DeviceChannel
import info.malondaovalle.riego.data.device.DeviceCommand
import info.malondaovalle.riego.data.device.DeviceCommander
import info.malondaovalle.riego.data.device.DeviceConfigResponse
import info.malondaovalle.riego.data.device.DeviceControl
import info.malondaovalle.riego.data.device.LocalEndpoint
import info.malondaovalle.riego.data.device.MAX_DEVICE_CHANNELS
import info.malondaovalle.riego.data.device.ProgramDraft
import info.malondaovalle.riego.data.device.ProgramRepetition
import info.malondaovalle.riego.data.device.SetCanalPayload
import info.malondaovalle.riego.data.device.normalizeProgramDate
import info.malondaovalle.riego.data.device.toDeviceControl
import info.malondaovalle.riego.data.device.toDraft
import info.malondaovalle.riego.data.device.toProgramaDto
import info.malondaovalle.riego.data.remote.NetworkModule
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString

data class DeviceUiState(
    val loading: Boolean = true,
    val error: String? = null,
    val control: DeviceControl? = null,
    val powerBusy: Boolean = false,
    val nameBusy: Boolean = false,
    val channelBusy: Boolean = false,
    val programBusy: Boolean = false,
    /** Non-null while the add/modify program editor is open. */
    val editingProgram: ProgramDraft? = null,
    /** Ids of programs selected for deletion (empty = not in selection mode). */
    val programSelection: Set<Int> = emptySet(),
    val message: String? = null,
)

class DeviceViewModel(
    savedStateHandle: SavedStateHandle,
    private val commander: DeviceCommander,
) : ViewModel() {

    private val deviceId: Int = checkNotNull(savedStateHandle.get<Int>("deviceId"))
    private val fallbackName: String = savedStateHandle.get<String>("name").orEmpty()
    private val local: LocalEndpoint? = run {
        val ip = savedStateHandle.get<String>("ip")?.takeIf { it.isNotBlank() }
        val port = savedStateHandle.get<Int>("port")?.takeIf { it > 0 }
        if (ip != null && port != null) LocalEndpoint(ip, port) else null
    }

    private val _state = MutableStateFlow(DeviceUiState())
    val state: StateFlow<DeviceUiState> = _state.asStateFlow()

    init {
        load()
    }

    fun load() {
        _state.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            when (val result = commander.send(deviceId, local, DeviceCommand("GETCONFIG"))) {
                is CommandResult.Ok -> {
                    val control = runCatching {
                        NetworkModule.json
                            .decodeFromString<DeviceConfigResponse>(result.payload)
                            .toDeviceControl(fallbackName)
                    }.getOrNull()
                    if (control != null) {
                        _state.update { it.copy(loading = false, control = control) }
                    } else {
                        _state.update {
                            it.copy(loading = false, error = "Respuesta no válida del dispositivo")
                        }
                    }
                }
                is CommandResult.Error ->
                    _state.update { it.copy(loading = false, error = result.message) }
            }
        }
    }

    fun setPower(on: Boolean) {
        val control = _state.value.control ?: return
        if (_state.value.powerBusy) return
        _state.update {
            it.copy(powerBusy = true, control = control.copy(powerOn = on))
        }
        viewModelScope.launch {
            val command = DeviceCommand("ACTIVAR", if (on) "true" else "false")
            when (val result = commander.send(deviceId, local, command)) {
                is CommandResult.Ok -> {
                    val ok = result.payload.uppercase().contains("OK")
                    _state.update {
                        it.copy(
                            powerBusy = false,
                            control = it.control?.copy(powerOn = if (ok) on else !on),
                            message = if (ok) null else "El dispositivo rechazó el cambio",
                        )
                    }
                    if (ok) load()
                }
                is CommandResult.Error ->
                    _state.update {
                        it.copy(
                            powerBusy = false,
                            control = it.control?.copy(powerOn = !on),
                            message = result.message,
                        )
                    }
            }
        }
    }

    fun setDeviceName(name: String) {
        val newName = name.trim()
        val control = _state.value.control ?: return
        if (_state.value.nameBusy || newName.isBlank() || newName == control.name) return
        val previousName = control.name
        _state.update { it.copy(nameBusy = true, control = control.copy(name = newName)) }
        viewModelScope.launch {
            when (val result = commander.send(deviceId, local, DeviceCommand("SETNOMBRE", newName))) {
                is CommandResult.Ok -> {
                    val ok = result.payload.uppercase().contains("OK")
                    _state.update {
                        it.copy(
                            nameBusy = false,
                            control = it.control?.copy(name = if (ok) newName else previousName),
                            message = if (ok) "Nombre actualizado" else "El dispositivo rechazó el nombre",
                        )
                    }
                    if (ok) load()
                }
                is CommandResult.Error ->
                    _state.update {
                        it.copy(
                            nameBusy = false,
                            control = it.control?.copy(name = previousName),
                            message = result.message,
                        )
                    }
            }
        }
    }

    // --- Channels -----------------------------------------------------------
    // The channel id (1..16) maps to a physical output on the hardware, so the
    // user sets it explicitly. `SETCANAL` upserts by id: the device modifies the
    // channel if that id already exists, otherwise it creates it.

    fun addChannel(id: Int, name: String, active: Boolean) = saveChannel(id, name, active)

    fun updateChannel(oldId: Int, newId: Int, name: String, active: Boolean) =
        saveChannel(newId, name, active)

    private fun saveChannel(id: Int, name: String, active: Boolean) {
        val control = _state.value.control ?: return
        if (_state.value.channelBusy) return
        if (id !in 1..MAX_DEVICE_CHANNELS) {
            _state.update { it.copy(message = "El ID de canal debe estar entre 1 y $MAX_DEVICE_CHANNELS") }
            return
        }
        val cleanName = name.trim()
        val previous = control.channels
        val optimistic = if (previous.any { it.id == id }) {
            previous.map {
                if (it.id == id) it.copy(name = cleanName.ifBlank { it.name }, active = active) else it
            }
        } else {
            previous + DeviceChannel(id, cleanName.ifBlank { "Canal $id" }, active)
        }
        _state.update {
            it.copy(channelBusy = true, control = control.copy(channels = optimistic.sortedBy { c -> c.id }))
        }

        viewModelScope.launch {
            val payload = NetworkModule.json.encodeToString(SetCanalPayload(id, cleanName, active))
            when (val result = commander.send(deviceId, local, DeviceCommand("SETCANAL", payload))) {
                is CommandResult.Ok -> {
                    val ok = result.payload.uppercase().contains("OK")
                    if (ok) {
                        _state.update { it.copy(channelBusy = false) }
                        load()
                    } else {
                        _state.update {
                            it.copy(
                                channelBusy = false,
                                control = it.control?.copy(channels = previous),
                                message = "El dispositivo rechazó el canal",
                            )
                        }
                    }
                }
                is CommandResult.Error ->
                    _state.update {
                        it.copy(
                            channelBusy = false,
                            control = it.control?.copy(channels = previous),
                            message = result.message,
                        )
                    }
            }
        }
    }

    // TODO: no delete-channel command yet — local only.
    fun deleteChannel(id: Int) {
        _state.update { current ->
            val ctrl = current.control ?: return@update current
            current.copy(control = ctrl.copy(channels = ctrl.channels.filterNot { it.id == id }))
        }
    }

    // --- Programs ----------------------------------------------------------

    fun startAddProgram() {
        _state.update {
            it.copy(
                editingProgram = ProgramDraft(
                    id = 0,
                    active = true,
                    startDate = normalizeProgramDate(null),
                    repetition = ProgramRepetition.DAILY,
                    weekdays = emptySet(),
                    times = listOf("08:00"),
                    channels = emptyList(),
                ),
            )
        }
    }

    fun startEditProgram(programId: Int) {
        val program = _state.value.control?.programs?.firstOrNull { it.id == programId } ?: return
        _state.update { it.copy(editingProgram = program.toDraft()) }
    }

    fun updateDraft(draft: ProgramDraft) = _state.update { it.copy(editingProgram = draft) }

    fun cancelProgramEdit() = _state.update { it.copy(editingProgram = null) }

    fun saveProgram() {
        val draft = _state.value.editingProgram ?: return
        if (_state.value.programBusy) return
        if (draft.times.none { it.isNotBlank() } || draft.channels.isEmpty()) {
            _state.update { it.copy(message = "Añade al menos una hora y un canal") }
            return
        }
        _state.update { it.copy(programBusy = true) }
        viewModelScope.launch {
            val payload = NetworkModule.json.encodeToString(draft.toProgramaDto())
            when (val result = commander.send(deviceId, local, DeviceCommand("SETPROGRAMAUNICO", payload))) {
                is CommandResult.Ok -> {
                    val ok = result.payload.uppercase().contains("OK")
                    _state.update {
                        it.copy(
                            programBusy = false,
                            editingProgram = if (ok) null else it.editingProgram,
                            message = if (ok) "Programa guardado" else "El dispositivo rechazó el programa",
                        )
                    }
                    if (ok) load()
                }
                is CommandResult.Error ->
                    _state.update { it.copy(programBusy = false, message = result.message) }
            }
        }
    }

    fun toggleProgramSelection(programId: Int) = _state.update {
        val next = if (programId in it.programSelection) {
            it.programSelection - programId
        } else {
            it.programSelection + programId
        }
        it.copy(programSelection = next)
    }

    fun clearProgramSelection() = _state.update { it.copy(programSelection = emptySet()) }

    fun deleteSelectedPrograms() {
        val ids = _state.value.programSelection.toList()
        if (ids.isEmpty() || _state.value.programBusy) return
        _state.update { it.copy(programBusy = true) }
        viewModelScope.launch {
            var deleted = 0
            var firstError: String? = null
            for (id in ids) {
                when (val result = commander.send(deviceId, local, DeviceCommand("BORRARPROGRAMA", id.toString()))) {
                    is CommandResult.Ok ->
                        if (result.payload.uppercase().contains("OK")) deleted++
                        else if (firstError == null) firstError = "El dispositivo rechazó el borrado"
                    is CommandResult.Error -> if (firstError == null) firstError = result.message
                }
            }
            _state.update {
                it.copy(
                    programBusy = false,
                    programSelection = emptySet(),
                    message = when {
                        deleted == 0 -> firstError ?: "No se pudo borrar"
                        firstError != null -> "$deleted borrado(s), con errores"
                        deleted == 1 -> "Programa borrado"
                        else -> "$deleted programas borrados"
                    },
                )
            }
            load()
        }
    }

    fun consumeMessage() = _state.update { it.copy(message = null) }
}
