package info.malondaovalle.riego.data.device

import info.malondaovalle.riego.data.remote.NetworkModule
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString

/**
 * The command envelope understood by the device, both over local TCP and through
 * the API (`POST /api/Devices/{id}/Comando`). Same structure in both cases.
 * `Parametros` is always a string, even for boolean-ish values (e.g. `ACTIVAR`
 * takes `"true"`/`"false"`, not a raw JSON boolean).
 */
@Serializable
data class DeviceCommand(
    @SerialName("Comando") val comando: String,
    @SerialName("Parametros") val parametros: String = "",
)

/**
 * The device / API always answers with a string: `"OK"`, `"KO"`, or a JSON payload.
 * When it comes back as a quoted JSON string, unwrap it to the inner text.
 */
internal fun unwrapDeviceReply(raw: String): String {
    val trimmed = raw.trim()
    return if (trimmed.length >= 2 && trimmed.first() == '"' && trimmed.last() == '"') {
        runCatching { NetworkModule.json.decodeFromString<String>(trimmed) }.getOrDefault(trimmed)
    } else {
        trimmed
    }
}
