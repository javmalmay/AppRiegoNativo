package info.malondaovalle.riego.data.discovery

import android.content.Context
import android.net.wifi.WifiManager
import android.util.Log
import info.malondaovalle.riego.data.remote.NetworkModule
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.decodeFromString
import java.io.IOException
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.NetworkInterface
import java.net.SocketAddress
import java.net.SocketTimeoutException
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

/**
 * Discovers irrigation devices on the local network.
 *
 * Sends the `"PSE"` probe a few times (broadcast + per-interface directed
 * broadcast) because Wi-Fi drops broadcast packets aggressively, then keeps
 * calling `socket.receive()` for the whole [discover] window so that *every*
 * device that answers is collected — not just the first one.
 *
 * Requires `ACCESS_WIFI_STATE` and `CHANGE_WIFI_MULTICAST_STATE` (for the multicast
 * lock that lets Android deliver broadcast packets to the app).
 */
class DeviceDiscoveryService(context: Context) {

    private val appContext = context.applicationContext

    suspend fun discover(timeout: Duration = DEFAULT_TIMEOUT): List<DiscoveredDevice> =
        withContext(Dispatchers.IO) {
            val lock = acquireMulticastLock()
            val socket = openSocket() ?: run {
                lock?.let { runCatching { it.release() } }
                Log.w(TAG, "could not open UDP socket on :$DISCOVERY_PORT")
                return@withContext emptyList()
            }

            val localAddresses = localInetAddresses()
            val found = LinkedHashMap<String, DiscoveredDevice>()

            try {
                val endAt = System.nanoTime() + timeout.inWholeNanoseconds

                // First probe now; re-send a few more across the window (Wi-Fi drops
                // broadcast packets aggressively, and devices may be briefly busy).
                sendProbes(socket)
                val prober = launch {
                    repeat(PROBE_COUNT - 1) {
                        delay(PROBE_INTERVAL_MS)
                        sendProbes(socket)
                    }
                }

                val buffer = ByteArray(8 * 1024)
                while (true) {
                    val remainingMs = (endAt - System.nanoTime()) / 1_000_000
                    if (remainingMs <= 0) break
                    socket.soTimeout = remainingMs.coerceIn(1L, 10_000L).toInt()

                    val packet = DatagramPacket(buffer, buffer.size)
                    try {
                        socket.receive(packet)
                    } catch (e: SocketTimeoutException) {
                        break // window elapsed with no more replies
                    }

                    val senderIp = packet.address?.hostAddress
                    val payload = String(packet.data, packet.offset, packet.length, Charsets.UTF_8)

                    Log.d(TAG, "reply from $senderIp: $payload")
                    if (packet.address in localAddresses) {
                        Log.d(TAG, "ignoring own packet from $senderIp: $payload")
                        continue
                    }


                    val device = parseReply(payload, senderIp)
                    if (device == null) continue

                    val key = normalizeMac(device.mac)
                    val previous = found.put(key, device)
                    if (previous != null && previous != device) {
                        Log.w(TAG, "MAC '$key' seen twice: $previous vs $device")
                    }
                }
                prober.cancel()
            } catch (e: IOException) {
                Log.w(TAG, "discovery socket error: ${e.message}")
            } finally {
                runCatching { socket.close() }
                lock?.let { runCatching { it.release() } }
            }

            Log.d(
                TAG,
                "scan finished: ${found.size} device(s) -> " +
                    found.values.joinToString { "${it.mac}(${normalizeMac(it.mac)})@${it.ip}:${it.port}" },
            )
            found.values.toList()
        }

    private fun openSocket(): DatagramSocket? = runCatching {
        DatagramSocket(null as SocketAddress?).apply {
            reuseAddress = true
            broadcast = true
            runCatching { receiveBufferSize = 256 * 1024 }
            bind(InetSocketAddress(DISCOVERY_PORT))
        }
    }.getOrNull()

    /** Sends `"PSE"` to the limited broadcast and to every interface's broadcast. */
    private fun sendProbes(socket: DatagramSocket) {
        val message = PROBE.toByteArray(Charsets.US_ASCII)
        val targets = buildSet<InetAddress> {
            runCatching { add(InetAddress.getByName("255.255.255.255")) }
            addAll(interfaceBroadcasts())
        }
        for (target in targets) {
            runCatching {
                socket.send(DatagramPacket(message, message.size, target, DISCOVERY_PORT))
            }.onFailure { Log.w(TAG, "probe to $target failed: ${it.message}") }
        }
        Log.d(TAG, "probe sent to $targets")
    }

    private fun parseReply(payload: String, senderIp: String?): DiscoveredDevice? {
        val dto = try {
            NetworkModule.json.decodeFromString<DiscoveryReplyDto>(payload.trim())
        } catch (e: Exception) {
            Log.w(TAG, "unparseable reply from $senderIp: $payload (${e.message})")
            return null
        }

        val mac = dto.mac?.trim()?.takeIf { it.isNotEmpty() }
        if (mac == null) {
            Log.w(TAG, "reply without MAC from $senderIp: $payload")
            return null
        }
        return DiscoveredDevice(
            name = dto.nombre?.trim()?.takeIf { it.isNotEmpty() } ?: mac,
            ip = senderIp.orEmpty(),
            mac = mac,
            port = dto.portOrNull,
        )
    }

    private fun acquireMulticastLock(): WifiManager.MulticastLock? {
        val wifi = appContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager ?: return null
        return runCatching {
            wifi.createMulticastLock("riego-discovery").apply {
                setReferenceCounted(false)
                acquire()
            }
        }.getOrNull()
    }

    private fun localInetAddresses(): Set<InetAddress> = runCatching {
        NetworkInterface.getNetworkInterfaces().asSequence()
            .flatMap { it.inetAddresses.asSequence() }
            .toSet()
    }.getOrDefault(emptySet())

    private fun interfaceBroadcasts(): List<InetAddress> = runCatching {
        NetworkInterface.getNetworkInterfaces().asSequence()
            .filter { runCatching { it.isUp && !it.isLoopback }.getOrDefault(false) }
            .flatMap { it.interfaceAddresses.asSequence() }
            .mapNotNull { it.broadcast }
            .toList()
    }.getOrDefault(emptyList())

    private companion object {
        const val TAG = "DeviceDiscovery"
        const val DISCOVERY_PORT = 9123
        const val PROBE = "PSE"
        const val PROBE_COUNT = 4
        const val PROBE_INTERVAL_MS = 500L
        val DEFAULT_TIMEOUT: Duration = 4000.milliseconds
    }
}
