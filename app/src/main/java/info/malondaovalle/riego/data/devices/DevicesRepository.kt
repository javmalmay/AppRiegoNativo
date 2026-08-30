package info.malondaovalle.riego.data.devices

import info.malondaovalle.riego.data.remote.DeviceDto
import info.malondaovalle.riego.data.remote.DevicesApi
import info.malondaovalle.riego.data.util.parseApiDateTime
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerializationException
import java.io.IOException

sealed interface DevicesResult {
    data class Success(val devices: List<Device>) : DevicesResult
    data class Error(val message: String) : DevicesResult
}

class DevicesRepository(private val api: DevicesApi) {

    suspend fun getDevices(): DevicesResult = withContext(Dispatchers.IO) {
        try {
            val response = api.getDevices()
            val body = response.body()
            if (response.isSuccessful && body?.success == true) {
                DevicesResult.Success(body.devices.map(DeviceDto::toDomain))
            } else {
                DevicesResult.Error(
                    body?.message ?: when (response.code()) {
                        401 -> "Sesión expirada"
                        else -> "Error ${response.code()}"
                    }
                )
            }
        } catch (e: IOException) {
            DevicesResult.Error("No se pudo conectar con el servidor")
        } catch (e: SerializationException) {
            DevicesResult.Error("Respuesta inesperada del servidor")
        }
    }
}

private fun DeviceDto.toDomain(): Device = Device(
    id = id,
    macAddress = macAddress,
    name = name.ifBlank { macAddress },
    isOnline = isOnline,
    lastSeenAt = parseApiDateTime(lastSeenAt),
)
