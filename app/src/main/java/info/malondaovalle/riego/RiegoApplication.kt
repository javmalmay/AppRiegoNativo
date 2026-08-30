package info.malondaovalle.riego

import android.app.Application
import info.malondaovalle.riego.data.auth.AuthRepository
import info.malondaovalle.riego.data.auth.SessionStore
import info.malondaovalle.riego.data.devices.DevicesRepository
import info.malondaovalle.riego.data.remote.NetworkModule

class RiegoApplication : Application() {

    // Simple manual DI container. No framework needed for this scope.
    private val sessionStore: SessionStore by lazy { SessionStore(this) }

    val authRepository: AuthRepository by lazy {
        AuthRepository(
            api = NetworkModule.createAuthApi(),
            sessionStore = sessionStore,
        )
    }

    val devicesRepository: DevicesRepository by lazy {
        DevicesRepository(
            api = NetworkModule.createDevicesApi(sessionStore, authRepository),
        )
    }
}
