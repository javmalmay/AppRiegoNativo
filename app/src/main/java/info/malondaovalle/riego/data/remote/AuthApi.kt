package info.malondaovalle.riego.data.remote

import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.POST

interface AuthApi {

    @POST("api/Auth/register")
    suspend fun register(@Body body: RegisterRequest): Response<RegisterResponse>

    @POST("api/Auth/login")
    suspend fun login(@Body body: LoginRequest): Response<AuthResponse>

    @POST("api/Auth/refresh")
    suspend fun refresh(@Body body: RefreshRequest): Response<AuthResponse>
}
