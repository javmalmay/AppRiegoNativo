package info.malondaovalle.riego.data.discovery

import info.malondaovalle.riego.data.device.DeviceCommand
import info.malondaovalle.riego.data.remote.NetworkModule
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.InetSocketAddress
import java.net.Socket
import java.net.SocketTimeoutException

/** Outcome of a raw TCP command sent to a device on the LAN. */
sealed interface DeviceTcpResult {
    data class Reply(val text: String) : DeviceTcpResult
    data object Unreachable : DeviceTcpResult
}

/** Outcome of pushing the access token to a device over its local TCP port. */
enum class TokenPushResult { OK, REJECTED, UNREACHABLE }

/**
 * Talks to a device over raw TCP on the `ip:port` it advertised during discovery.
 * The device accepts the same `{ "Comando", "Parametros" }` envelope as the API and
 * answers with a plain string (`"OK"`, `"KO"`, or a JSON payload).
 */
class DeviceTcpClient {

    suspend fun sendCommand(ip: String, port: Int, command: DeviceCommand): DeviceTcpResult =
        withContext(Dispatchers.IO) {
            try {
                Socket().use { socket ->
                    socket.connect(InetSocketAddress(ip, port), CONNECT_TIMEOUT_MS)
                    socket.soTimeout = READ_TIMEOUT_MS

                    val payload = NetworkModule.json.encodeToString(command) + "\n"
                    socket.getOutputStream().apply {
                        write(payload.toByteArray(Charsets.UTF_8))
                        flush()
                    }

                    DeviceTcpResult.Reply(readReply(socket))
                }
            } catch (e: IOException) {
                DeviceTcpResult.Unreachable
            }
        }

    suspend fun sendToken(ip: String, port: Int, deviceToken: String): TokenPushResult =
        when (val result = sendCommand(ip, port, DeviceCommand("SETTOKEN", deviceToken))) {
            is DeviceTcpResult.Reply ->
                if (result.text.uppercase().contains("OK")) TokenPushResult.OK
                else TokenPushResult.REJECTED
            DeviceTcpResult.Unreachable -> TokenPushResult.UNREACHABLE
        }

    private fun readReply(socket: Socket): String {
        val input = socket.getInputStream()
        val buffer = ByteArray(4096)
        val out = ByteArrayOutputStream()
        var receivedSomething = false
        while (out.size() < MAX_REPLY_BYTES) {
            val read = try {
                input.read(buffer)
            } catch (e: SocketTimeoutException) {
                break
            }
            if (read < 0) break
            out.write(buffer, 0, read)
            if (!receivedSomething) {
                receivedSomething = true
                // Shorter timeout once data is flowing, to detect the end quickly.
                socket.soTimeout = TAIL_TIMEOUT_MS
            }
        }
        return String(out.toByteArray(), Charsets.UTF_8).trim()
    }

    private companion object {
        const val CONNECT_TIMEOUT_MS = 3000
        const val READ_TIMEOUT_MS = 3000
        const val TAIL_TIMEOUT_MS = 600
        const val MAX_REPLY_BYTES = 128 * 1024
    }
}
