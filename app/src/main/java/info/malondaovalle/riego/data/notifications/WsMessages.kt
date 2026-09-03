package info.malondaovalle.riego.data.notifications

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

/**
 * A device command sent over the user WebSocket — the same `{Comando, Parametros}`
 * as the local/TCP path plus the target [deviceId]. The server reads these field
 * names case-insensitively.
 *
 * The server does not interpret [parametros]; it forwards it verbatim to the
 * device firmware, so we send the same string form as the local/TCP path
 * (`"true"`, a serialized JSON object, …).
 */
@Serializable
data class WsCommand(
    @SerialName("deviceId") val deviceId: Int,
    @SerialName("comando") val comando: String,
    @SerialName("parametros") val parametros: String = "",
)

/**
 * Every frame the server pushes is this envelope; [type] selects the meaning
 * (see [WsMessageType]).
 */
@Serializable
data class WsServerMessage(
    @SerialName("type") val type: String = "",
    @SerialName("deviceId") val deviceId: Int? = null,
    @SerialName("macAddress") val macAddress: String? = null,
    @SerialName("deviceName") val deviceName: String? = null,
    @SerialName("payload") val payload: JsonElement? = null,
    @SerialName("correlationId") val correlationId: String? = null,
    @SerialName("timestamp") val timestamp: String? = null,
)

object WsMessageType {
    /** Immediate confirmation that our command was relayed; carries a new correlationId. */
    const val COMMAND_SENT = "command_sent"
    /** The device's actual answer to one of our commands, matched by correlationId. */
    const val COMMAND_RESPONSE = "command_response"
    /** Spontaneous telemetry / state; not tied to a command. */
    const val DEVICE_STATUS = "device_status"
    const val DEVICE_ONLINE = "device_online"
    const val DEVICE_OFFLINE = "device_offline"
    /** Something failed processing our last message; payload is a plain string. */
    const val ERROR = "error"
}

/** `device_status` telemetry for a device. */
data class DeviceStatusEvent(val deviceId: Int, val payload: JsonElement?)

/** `device_online` / `device_offline` connectivity change for a device. */
data class DeviceConnectivityEvent(val deviceId: Int, val online: Boolean)
