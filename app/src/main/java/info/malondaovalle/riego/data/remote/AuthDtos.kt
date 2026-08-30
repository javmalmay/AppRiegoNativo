package info.malondaovalle.riego.data.remote

import kotlinx.serialization.Serializable

@Serializable
data class RegisterRequest(
    val username: String,
    val email: String,
    val password: String,
)

@Serializable
data class LoginRequest(
    val username: String,
    val password: String,
)

@Serializable
data class RefreshRequest(
    val refreshToken: String,
)

@Serializable
data class UserDto(
    val id: Int = 0,
    val username: String = "",
    val email: String = "",
    val createdAt: String? = null,
)

/** Response shape for /api/Auth/register (both 201 and 400). */
@Serializable
data class RegisterResponse(
    val success: Boolean = false,
    val message: String? = null,
    val user: UserDto? = null,
)

/** Response shape shared by /api/Auth/login and /api/Auth/refresh (2xx and 4xx). */
@Serializable
data class AuthResponse(
    val success: Boolean = false,
    val message: String? = null,
    val token: String? = null,
    val tokenExpire: String? = null,
    val refreshToken: String? = null,
    val refreshTokenExpire: String? = null,
    val user: UserDto? = null,
)
