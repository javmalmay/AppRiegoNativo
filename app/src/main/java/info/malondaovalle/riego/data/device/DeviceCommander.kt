package info.malondaovalle.riego.data.device

import android.util.Log
import info.malondaovalle.riego.data.discovery.DeviceTcpClient
import info.malondaovalle.riego.data.discovery.DeviceTcpResult
import info.malondaovalle.riego.data.notifications.CommandOutcome
import info.malondaovalle.riego.data.notifications.UserSocket

private const val TAG = "DeviceCommander"

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
 *  - otherwise (or if the local attempt fails) route the command through the user
 *    WebSocket; the reply is correlated back by the server-assigned `correlationId`
 *    (see [UserSocket.request]).
 */
class DeviceCommander(
    private val tcpClient: DeviceTcpClient,
    private val userSocket: UserSocket,
) {

    suspend fun send(
        deviceId: Int,
        local: LocalEndpoint?,
        command: DeviceCommand,
    ): CommandResult {
        if (local != null) {
            Log.d(TAG, "device=$deviceId ${command.comando} -> TCP ${local.ip}:${local.port}")
            when (val tcp = tcpClient.sendCommand(local.ip, local.port, command)) {
                is DeviceTcpResult.Reply -> {
                    Log.d(TAG, "device=$deviceId ${command.comando} <- TCP ${tcp.text}")
                    return CommandResult.Ok(unwrapDeviceReply(tcp.text))
                }
                DeviceTcpResult.Unreachable ->
                    Log.d(TAG, "device=$deviceId inalcanzable por TCP, se prueba el WebSocket")
            }
        }
        return sendViaSocket(deviceId, command)
    }

    private suspend fun sendViaSocket(deviceId: Int, command: DeviceCommand): CommandResult {
        Log.d(TAG, "device=$deviceId ${command.comando} -> WebSocket")
        return when (val outcome = userSocket.request(deviceId, command.comando, command.parametros)) {
            is CommandOutcome.Ok -> CommandResult.Ok(unwrapDeviceReply(outcome.payload))
            is CommandOutcome.Rejected -> CommandResult.Error(outcome.reason)
            CommandOutcome.NoResponse ->
                CommandResult.Error("El dispositivo no respondió")
            CommandOutcome.Disconnected ->
                CommandResult.Error("Sin conexión con el servicio de notificaciones")
        }
    }
}
