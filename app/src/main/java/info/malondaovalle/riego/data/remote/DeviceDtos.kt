package info.malondaovalle.riego.data.remote

import kotlinx.serialization.Serializable

/** Response shape for GET /api/Devices. */
@Serializable
data class DevicesResponse(
    val success: Boolean = false,
    val message: String? = null,
    val devices: List<DeviceDto> = emptyList(),
)

@Serializable
data class DeviceDto(
    val id: Int = 0,
    val macAddress: String = "",
    val name: String = "",
    val configJson: String? = null,
    val createdAt: String? = null,
    val updatedAt: String? = null,
    val isOnline: Boolean = false,
    val lastSeenAt: String? = null,
)
