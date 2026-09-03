package info.malondaovalle.riego.data.remote

import info.malondaovalle.riego.BuildConfig
import info.malondaovalle.riego.data.auth.AuthRepository
import info.malondaovalle.riego.data.auth.SessionStore
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.security.SecureRandom
import java.security.cert.X509Certificate
import java.util.concurrent.TimeUnit
import javax.net.ssl.HostnameVerifier
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager

/**
 * Builds the Retrofit stacks for the API.
 *
 * [createAuthApi] is unauthenticated (login / register / refresh). [createDevicesApi]
 * attaches the bearer token and refreshes it on 401.
 *
 * On debug builds ([BuildConfig.TRUST_ALL_CERTS] == true) the client trusts every
 * TLS certificate so the emulator can talk to the ASP.NET Core dev server
 * (https://10.0.2.2:7179) with its self-signed certificate. This MUST stay off in
 * release builds.
 */
object NetworkModule {

    val json: Json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
        coerceInputValues = true
        // Command payloads (SETPROGRAMAUNICO with Id=0, {Id,Duracion}, …) must send
        // every field even when it equals the Kotlin default.
        encodeDefaults = true
    }

    fun createAuthApi(): AuthApi =
        retrofit(baseClientBuilder().build()).create(AuthApi::class.java)

    fun createDevicesApi(
        sessionStore: SessionStore,
        authRepository: AuthRepository,
    ): DevicesApi {
        val client = baseClientBuilder()
            .addInterceptor(AuthInterceptor(sessionStore))
            .authenticator(TokenAuthenticator(sessionStore, authRepository))
            .build()
        return retrofit(client).create(DevicesApi::class.java)
    }

    /** Client for the user notifications WebSocket (keeps the connection alive). */
    fun webSocketClient(): OkHttpClient =
        baseClientBuilder()
            .pingInterval(20, TimeUnit.SECONDS)
            .build()

    /** `{API_BASE_URL}` with a `ws(s)` scheme + `ws/user`. */
    fun userSocketUrl(): String {
        val base = BuildConfig.API_BASE_URL.trimEnd('/')
            .replaceFirst("https://", "wss://")
            .replaceFirst("http://", "ws://")
        return "$base/ws/user"
    }

    private fun retrofit(client: OkHttpClient): Retrofit {
        val contentType = "application/json".toMediaType()
        return Retrofit.Builder()
            .baseUrl(BuildConfig.API_BASE_URL)
            .client(client)
            .addConverterFactory(json.asConverterFactory(contentType))
            .build()
    }

    private fun baseClientBuilder(): OkHttpClient.Builder {
        val builder = OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)

        if (BuildConfig.DEBUG) {
            builder.addInterceptor(
                HttpLoggingInterceptor().apply { level = HttpLoggingInterceptor.Level.BODY }
            )
        }

        if (BuildConfig.TRUST_ALL_CERTS) {
            applyTrustAllCerts(builder)
        }

        return builder
    }

    @Suppress("CustomX509TrustManager", "TrustAllX509TrustManager")
    private fun applyTrustAllCerts(builder: OkHttpClient.Builder) {
        val trustManager = object : X509TrustManager {
            override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) {}
            override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) {}
            override fun getAcceptedIssuers(): Array<X509Certificate> = arrayOf()
        }
        val sslContext = SSLContext.getInstance("TLS").apply {
            init(null, arrayOf<TrustManager>(trustManager), SecureRandom())
        }
        builder.sslSocketFactory(sslContext.socketFactory, trustManager)
        builder.hostnameVerifier(HostnameVerifier { _, _ -> true })
    }
}
