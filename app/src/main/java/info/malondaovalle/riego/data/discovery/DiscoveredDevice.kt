package info.malondaovalle.riego.data.discovery

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonNames
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.intOrNull

/** A device that answered the UDP discovery broadcast on the local network. */
data class DiscoveredDevice(
    val name: String,
    val ip: String,
    val mac: String,
    val port: Int?,
)

/**
 * Raw UDP reply payload. The device sends PascalCase keys and a numeric port:
 * `{ "Nombre": "...", "IP": "...", "MAC": "...", "Puerto": 13400 }`.
 * Lowercase aliases are accepted too in case the firmware varies.
 */
@OptIn(ExperimentalSerializationApi::class)
@Serializable
data class DiscoveryReplyDto(
    @SerialName("Nombre") @JsonNames("nombre", "name")
    val nombre: String? = null,
    @SerialName("IP") @JsonNames("ip")
    val ip: String? = null,
    @SerialName("MAC") @JsonNames("mac", "macAddress")
    val mac: String? = null,
    @SerialName("Puerto") @JsonNames("puerto", "port")
    val puerto: JsonPrimitive? = null,
) {
    /** Port as an Int whether the device sent it as a number or a quoted string. */
    val portOrNull: Int? get() = puerto?.intOrNull
}

/** Normalized MAC (hex only, uppercase) for comparing against registered devices. */
fun normalizeMac(mac: String): String =
    mac.uppercase().filter { it in "0123456789ABCDEF" }
