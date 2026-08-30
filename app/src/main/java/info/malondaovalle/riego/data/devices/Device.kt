package info.malondaovalle.riego.data.devices

import java.time.LocalDateTime

/** An irrigation device associated with the logged-in user. */
data class Device(
    val id: Int,
    val macAddress: String,
    val name: String,
    val isOnline: Boolean,
    /** Last time the device was seen. Null when it has never connected. */
    val lastSeenAt: LocalDateTime?,
)
