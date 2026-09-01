package info.malondaovalle.riego.data.remote

import kotlinx.serialization.Serializable

/** Response shape for GET /api/Devices. */
@Serializable
data class DevicesResponse(
    val success: Boolean = false,
    val message: String? = null,
    val devices: List<DeviceDto> = emptyList(),
)

/** Body for POST /api/Devices (associate a device with the account). */
@Serializable
data class RegisterDeviceRequest(
    val macAddress: String,
    val name: String,
)

/** Response shape for POST /api/Devices. */
@Serializable
data class DeviceActionResponse(
    val success: Boolean = false,
    val message: String? = null,
    val device: DeviceDto? = null,
    /** Per-device credential issued on registration. Captured but not used yet. */
    val deviceToken: String? = null,
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
