package info.malondaovalle.riego.data.remote

import retrofit2.Response
import retrofit2.http.GET

interface DevicesApi {

    @GET("api/Devices")
    suspend fun getDevices(): Response<DevicesResponse>
}
