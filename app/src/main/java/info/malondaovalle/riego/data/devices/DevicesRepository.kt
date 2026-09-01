package info.malondaovalle.riego.data.devices

import info.malondaovalle.riego.data.remote.DeviceActionResponse
import info.malondaovalle.riego.data.remote.DeviceDto
import info.malondaovalle.riego.data.remote.DevicesApi
import info.malondaovalle.riego.data.remote.RegisterDeviceRequest
import info.malondaovalle.riego.data.util.parseApiDateTime
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerializationException
import retrofit2.Response
import java.io.IOException

sealed interface DevicesResult {
    data class Success(val devices: List<Device>) : DevicesResult
    data class Error(val message: String) : DevicesResult
}

sealed interface DeviceActionResult {
    data class Success(val message: String?, val deviceToken: String? = null) : DeviceActionResult
    data class Error(val message: String) : DeviceActionResult
}

class DevicesRepository(private val api: DevicesApi) {

    suspend fun getDevices(): DevicesResult = withContext(Dispatchers.IO) {
        try {
            val response = api.getDevices()
            val body = response.body()
            if (response.isSuccessful && body?.success == true) {
                DevicesResult.Success(body.devices.map(DeviceDto::toDomain))
            } else {
                DevicesResult.Error(body?.message ?: deviceErrorMessage(response.code()))
            }
        } catch (e: IOException) {
            DevicesResult.Error("No se pudo conectar con el servidor")
        } catch (e: SerializationException) {
            DevicesResult.Error("Respuesta inesperada del servidor")
        }
    }

    suspend fun registerDevice(macAddress: String, name: String): DeviceActionResult =
        deviceAction { api.registerDevice(RegisterDeviceRequest(macAddress, name)) }

    suspend fun deleteDevice(id: Int): DeviceActionResult =
        deviceAction { api.deleteDevice(id) }

    private suspend fun deviceAction(
        call: suspend () -> Response<DeviceActionResponse>,
    ): DeviceActionResult = withContext(Dispatchers.IO) {
        try {
            val response = call()
            val body = response.body()
            if (response.isSuccessful && body?.success != false) {
                DeviceActionResult.Success(body?.message, body?.deviceToken)
            } else {
                DeviceActionResult.Error(body?.message ?: deviceErrorMessage(response.code()))
            }
        } catch (e: IOException) {
            DeviceActionResult.Error("No se pudo conectar con el servidor")
        } catch (e: SerializationException) {
            DeviceActionResult.Error("Respuesta inesperada del servidor")
        }
    }
}

private fun deviceErrorMessage(code: Int): String = when (code) {
    401 -> "Sesión expirada"
    404 -> "Dispositivo no encontrado"
    409 -> "El dispositivo ya está asociado"
    else -> "Error $code"
}

private fun DeviceDto.toDomain(): Device = Device(
    id = id,
    macAddress = macAddress,
    name = name.ifBlank { macAddress },
    isOnline = isOnline,
    lastSeenAt = parseApiDateTime(lastSeenAt),
)
