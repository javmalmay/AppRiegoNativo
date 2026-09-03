package info.malondaovalle.riego.data.remote

import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path

interface DevicesApi {

    @GET("api/Devices")
    suspend fun getDevices(): Response<DevicesResponse>

    @POST("api/Devices")
    suspend fun registerDevice(@Body body: RegisterDeviceRequest): Response<DeviceActionResponse>

    @DELETE("api/Devices/{id}")
    suspend fun deleteDevice(@Path("id") id: Int): Response<DeviceActionResponse>
    // Remote device commands go through the user WebSocket (UserSocket.request), not REST.
}
