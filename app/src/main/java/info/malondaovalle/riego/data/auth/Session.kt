package info.malondaovalle.riego.data.auth

import java.time.Instant

/** The persisted, authenticated session. */
data class Session(
    val username: String,
    val token: String,
    val refreshToken: String,
    val tokenExpire: Instant,
    val refreshTokenExpire: Instant,
) {
    fun isTokenValid(now: Instant = Instant.now()): Boolean = now.isBefore(tokenExpire)
    fun isRefreshTokenValid(now: Instant = Instant.now()): Boolean = now.isBefore(refreshTokenExpire)
}
