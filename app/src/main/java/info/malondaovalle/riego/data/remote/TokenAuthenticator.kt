package info.malondaovalle.riego.data.remote

import info.malondaovalle.riego.data.auth.AuthRepository
import info.malondaovalle.riego.data.auth.SessionStore
import kotlinx.coroutines.runBlocking
import okhttp3.Authenticator
import okhttp3.Request
import okhttp3.Response
import okhttp3.Route

/**
 * When a request comes back 401, tries to refresh the token once and replays the
 * request with the new one. Serialized so concurrent 401s trigger a single refresh.
 */
class TokenAuthenticator(
    private val sessionStore: SessionStore,
    private val authRepository: AuthRepository,
) : Authenticator {

    @Synchronized
    override fun authenticate(route: Route?, response: Response): Request? {
        val failedToken = response.request.header("Authorization")?.removePrefix("Bearer ")
        val currentToken = runBlocking { sessionStore.read()?.token }

        // Another thread already refreshed while this request was in flight.
        if (!currentToken.isNullOrBlank() && currentToken != failedToken) {
            return response.request.newBuilder()
                .header("Authorization", "Bearer $currentToken")
                .build()
        }

        if (responseCount(response) >= 2) return null

        val refreshed = runBlocking { authRepository.refresh() }
        if (!refreshed) return null

        val newToken = runBlocking { sessionStore.read()?.token } ?: return null
        return response.request.newBuilder()
            .header("Authorization", "Bearer $newToken")
            .build()
    }

    private fun responseCount(response: Response): Int {
        var prior = response.priorResponse
        var count = 1
        while (prior != null) {
            count++
            prior = prior.priorResponse
        }
        return count
    }
}
