package info.malondaovalle.riego.data.auth

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import info.malondaovalle.riego.data.security.CryptoManager
import kotlinx.coroutines.flow.first
import java.time.Instant

private val Context.dataStore by preferencesDataStore(name = "riego_session")

/**
 * Persists the [Session] in a Preferences DataStore. The access token and refresh
 * token are encrypted with [CryptoManager] before being written; expiries and the
 * username are stored in the clear.
 */
class SessionStore(
    private val context: Context,
    private val crypto: CryptoManager = CryptoManager(),
) {

    suspend fun read(): Session? {
        val prefs = context.dataStore.data.first()
        val username = prefs[KEY_USERNAME] ?: return null
        val encToken = prefs[KEY_TOKEN] ?: return null
        val encRefresh = prefs[KEY_REFRESH_TOKEN] ?: return null
        val tokenExpire = prefs[KEY_TOKEN_EXPIRE] ?: return null
        val refreshExpire = prefs[KEY_REFRESH_EXPIRE] ?: return null

        return runCatching {
            Session(
                username = username,
                token = crypto.decrypt(encToken),
                refreshToken = crypto.decrypt(encRefresh),
                tokenExpire = Instant.ofEpochMilli(tokenExpire),
                refreshTokenExpire = Instant.ofEpochMilli(refreshExpire),
            )
        }.getOrNull()
    }

    suspend fun save(session: Session) {
        context.dataStore.edit { prefs ->
            prefs[KEY_USERNAME] = session.username
            prefs[KEY_TOKEN] = crypto.encrypt(session.token)
            prefs[KEY_REFRESH_TOKEN] = crypto.encrypt(session.refreshToken)
            prefs[KEY_TOKEN_EXPIRE] = session.tokenExpire.toEpochMilli()
            prefs[KEY_REFRESH_EXPIRE] = session.refreshTokenExpire.toEpochMilli()
        }
    }

    suspend fun clear() {
        context.dataStore.edit { it.clear() }
    }

    private companion object {
        val KEY_USERNAME = stringPreferencesKey("username")
        val KEY_TOKEN = stringPreferencesKey("token")
        val KEY_REFRESH_TOKEN = stringPreferencesKey("refresh_token")
        val KEY_TOKEN_EXPIRE = longPreferencesKey("token_expire")
        val KEY_REFRESH_EXPIRE = longPreferencesKey("refresh_token_expire")
    }
}
