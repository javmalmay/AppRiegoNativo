package info.malondaovalle.riego

import android.app.Application
import info.malondaovalle.riego.data.auth.AuthRepository
import info.malondaovalle.riego.data.auth.SessionStore
import info.malondaovalle.riego.data.device.DeviceCommander
import info.malondaovalle.riego.data.devices.DevicesRepository
import info.malondaovalle.riego.data.discovery.DeviceDiscoveryService
import info.malondaovalle.riego.data.discovery.DeviceTcpClient
import info.malondaovalle.riego.data.notifications.UserSocket
import info.malondaovalle.riego.data.remote.DevicesApi
import info.malondaovalle.riego.data.remote.NetworkModule
import info.malondaovalle.riego.data.settings.ThemePreferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

class RiegoApplication : Application() {

    // Simple manual DI container. No framework needed for this scope.
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val sessionStore: SessionStore by lazy { SessionStore(this) }

    val authRepository: AuthRepository by lazy {
        AuthRepository(
            api = NetworkModule.createAuthApi(),
            sessionStore = sessionStore,
        )
    }

    private val devicesApi: DevicesApi by lazy {
        NetworkModule.createDevicesApi(sessionStore, authRepository)
    }

    val devicesRepository: DevicesRepository by lazy { DevicesRepository(devicesApi) }

    val deviceDiscoveryService: DeviceDiscoveryService by lazy {
        DeviceDiscoveryService(this)
    }

    val deviceTcpClient: DeviceTcpClient by lazy { DeviceTcpClient() }

    val themePreferences: ThemePreferences by lazy { ThemePreferences(this) }

    val userSocket: UserSocket by lazy { UserSocket(sessionStore, appScope) }

    val deviceCommander: DeviceCommander by lazy {
        DeviceCommander(tcpClient = deviceTcpClient, userSocket = userSocket)
    }

    override fun onCreate() {
        super.onCreate()
        // Follows the stored session: connects once logged in (fresh or restored).
        userSocket.start()
    }
}
