package info.malondaovalle.riego.data.auth

import info.malondaovalle.riego.data.remote.AuthApi
import info.malondaovalle.riego.data.remote.AuthResponse
import info.malondaovalle.riego.data.remote.LoginRequest
import info.malondaovalle.riego.data.remote.NetworkModule
import info.malondaovalle.riego.data.remote.RefreshRequest
import info.malondaovalle.riego.data.remote.RegisterRequest
import info.malondaovalle.riego.data.remote.RegisterResponse
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerializationException
import kotlinx.serialization.decodeFromString
import retrofit2.Response
import java.io.IOException
import java.time.Instant
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneOffset

class AuthRepository(
    private val api: AuthApi,
    private val sessionStore: SessionStore,
) {

    suspend fun register(username: String, email: String, password: String): SubmitResult =
        withContext(Dispatchers.IO) {
            runCatchingNetwork {
                val response = api.register(RegisterRequest(username.trim(), email.trim(), password))
                val body = response.body() ?: errorBody<RegisterResponse>(response)
                if (response.isSuccessful && body?.success == true) {
                    SubmitResult.Success(body.message)
                } else {
                    SubmitResult.Error(body?.message ?: httpError(response))
                }
            }
        }

    suspend fun login(username: String, password: String): SubmitResult =
        withContext(Dispatchers.IO) {
            runCatchingNetwork {
                val response = api.login(LoginRequest(username.trim(), password))
                handleAuthResponse(response, fallbackUsername = username.trim())
            }
        }

    /** Refreshes using the stored refresh token. Returns true on success. */
    suspend fun refresh(): Boolean = withContext(Dispatchers.IO) {
        val current = sessionStore.read() ?: return@withContext false
        val outcome = runCatchingNetwork {
            val response = api.refresh(RefreshRequest(current.refreshToken))
            handleAuthResponse(response, fallbackUsername = current.username)
        }
        outcome is SubmitResult.Success
    }

    suspend fun currentSession(): Session? = sessionStore.read()

    suspend fun logout() = sessionStore.clear()

    /**
     * Decides the start destination per the app spec:
     *  - no stored session -> LOGIN
     *  - access token still valid -> HOME
     *  - access token expired but refresh token valid -> try refresh, HOME/LOGIN
     *  - both expired -> LOGIN
     */
    suspend fun bootstrap(): StartDestination = withContext(Dispatchers.IO) {
        val session = sessionStore.read() ?: return@withContext StartDestination.LOGIN
        val now = Instant.now()
        when {
            session.isTokenValid(now) -> StartDestination.HOME
            session.isRefreshTokenValid(now) ->
                if (refresh()) StartDestination.HOME else StartDestination.LOGIN
            else -> StartDestination.LOGIN
        }
    }

    private suspend fun handleAuthResponse(
        response: Response<AuthResponse>,
        fallbackUsername: String,
    ): SubmitResult {
        val body = response.body() ?: errorBody<AuthResponse>(response)
        val token = body?.token
        if (response.isSuccessful && body?.success == true && token != null) {
            val session = Session(
                username = body.user?.username?.takeIf { it.isNotBlank() } ?: fallbackUsername,
                token = token,
                refreshToken = body.refreshToken.orEmpty(),
                tokenExpire = parseInstant(body.tokenExpire) ?: Instant.now(),
                refreshTokenExpire = parseInstant(body.refreshTokenExpire) ?: Instant.now(),
            )
            sessionStore.save(session)
            return SubmitResult.Success(body.message)
        }
        return SubmitResult.Error(body?.message ?: httpError(response))
    }

    private inline fun <reified T> errorBody(response: Response<*>): T? =
        response.errorBody()?.string()?.takeIf { it.isNotBlank() }?.let { raw ->
            runCatching { NetworkModule.json.decodeFromString<T>(raw) }.getOrNull()
        }

    private fun httpError(response: Response<*>): String = "Error ${response.code()}"

    private inline fun runCatchingNetwork(block: () -> SubmitResult): SubmitResult =
        try {
            block()
        } catch (e: IOException) {
            SubmitResult.Error("No se pudo conectar con el servidor")
        } catch (e: SerializationException) {
            SubmitResult.Error("Respuesta inesperada del servidor")
        }

    private fun parseInstant(value: String?): Instant? {
        if (value.isNullOrBlank()) return null
        return runCatching { Instant.parse(value) }
            .recoverCatching { OffsetDateTime.parse(value).toInstant() }
            .recoverCatching { LocalDateTime.parse(value).toInstant(ZoneOffset.UTC) }
            .getOrNull()
    }
}
