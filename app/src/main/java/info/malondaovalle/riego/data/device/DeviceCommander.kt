package info.malondaovalle.riego.data.device

import info.malondaovalle.riego.data.discovery.DeviceTcpClient
import info.malondaovalle.riego.data.discovery.DeviceTcpResult
import info.malondaovalle.riego.data.remote.DevicesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException

/** Where a device can be reached directly on the LAN. */
data class LocalEndpoint(val ip: String, val port: Int)

sealed interface CommandResult {
    /** Raw string the device answered (`"OK"`, `"KO"`, or a JSON payload). */
    data class Ok(val payload: String) : CommandResult
    data class Error(val message: String) : CommandResult
}

/**
 * Single entry point for talking to a device:
 *  - if a [LocalEndpoint] is known, go straight over TCP;
 *  - otherwise (or if the local attempt fails) route the command through the API
 *    (`POST /api/Devices/{id}/Comando`), which forwards it to the device.
 */
class DeviceCommander(
    private val tcpClient: DeviceTcpClient,
    private val devicesApi: DevicesApi,
) {

    suspend fun send(
        deviceId: Int,
        local: LocalEndpoint?,
        command: DeviceCommand,
    ): CommandResult {
        if (local != null) {
            when (val tcp = tcpClient.sendCommand(local.ip, local.port, command)) {
                is DeviceTcpResult.Reply -> return CommandResult.Ok(unwrapDeviceReply(tcp.text))
                DeviceTcpResult.Unreachable -> Unit // fall back to the API
            }
        }
        return sendViaApi(deviceId, command)
    }

    private suspend fun sendViaApi(deviceId: Int, command: DeviceCommand): CommandResult =
        withContext(Dispatchers.IO) {
            try {
                val response = devicesApi.sendCommand(deviceId, command)
                val raw = (response.body() ?: response.errorBody())?.string()
                if (response.isSuccessful && !raw.isNullOrBlank()) {
                    CommandResult.Ok(unwrapDeviceReply(raw))
                } else {
                    CommandResult.Error(
                        when (response.code()) {
                            401 -> "Sesión expirada"
                            404 -> "Dispositivo no encontrado"
                            else -> "Error ${response.code()}"
                        }
                    )
                }
            } catch (e: IOException) {
                CommandResult.Error("No se pudo conectar con el dispositivo")
            }
        }
}
