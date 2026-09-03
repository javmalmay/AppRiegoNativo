package info.malondaovalle.riego.data.notifications

import android.util.Log
import info.malondaovalle.riego.data.auth.SessionStore
import info.malondaovalle.riego.data.remote.NetworkModule
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import java.util.concurrent.ConcurrentHashMap
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

sealed interface SocketState {
    /** No session — nothing to connect. */
    data object Idle : SocketState
    data object Connecting : SocketState
    data object Connected : SocketState
    /** Disconnected / failed; a reconnect is scheduled. */
    data class Error(val reason: String) : SocketState
}

/** Result of [UserSocket.request]. */
sealed interface CommandOutcome {
    /** The device answered; [payload] is its reply text (`"OK"`, `"KO"`, or JSON). */
    data class Ok(val payload: String) : CommandOutcome
    /** The server rejected the command (`error` frame); [reason] is user-facing. */
    data class Rejected(val reason: String) : CommandOutcome
    /** Sent, but no `command_response` arrived within the timeout. */
    data object NoResponse : CommandOutcome
    /** The socket was not connected, or dropped while waiting. */
    data object Disconnected : CommandOutcome
}

/**
 * Long-lived WebSocket to `{URL}/ws/user`, authenticated with the access token.
 *
 * It follows the stored session: connects when there's a token, reconnects when the
 * token changes (e.g. after a refresh), disconnects on logout, and retries with a
 * fixed backoff on failure.
 *
 * Remote device commands go through [request], which follows the server protocol:
 *  1. send `{deviceId, comando, parametros}`;
 *  2. the server answers `command_sent` with a fresh `correlationId` (we don't know
 *     it beforehand), so the send→ack step is serialized with [sendLock];
 *  3. the device's real answer arrives later as `command_response` carrying that
 *     same `correlationId` — several may be in flight at once and reply out of order.
 *
 * Unsolicited frames — `device_status`, `device_online`, `device_offline` — are
 * published on [deviceStatus] / [connectivity] / [deviceUpdates].
 */
class UserSocket(
    private val sessionStore: SessionStore,
    private val scope: CoroutineScope,
) {

    private val _state = MutableStateFlow<SocketState>(SocketState.Idle)
    val state: StateFlow<SocketState> = _state.asStateFlow()

    /** Device ids the server pushed an update for — observers should re-fetch. */
    private val _deviceUpdates = MutableSharedFlow<Int>(extraBufferCapacity = 32)
    val deviceUpdates: SharedFlow<Int> = _deviceUpdates.asSharedFlow()

    /** `device_status` telemetry frames. */
    private val _deviceStatus = MutableSharedFlow<DeviceStatusEvent>(extraBufferCapacity = 32)
    val deviceStatus: SharedFlow<DeviceStatusEvent> = _deviceStatus.asSharedFlow()

    /** `device_online` / `device_offline` frames. */
    private val _connectivity = MutableSharedFlow<DeviceConnectivityEvent>(extraBufferCapacity = 32)
    val connectivity: SharedFlow<DeviceConnectivityEvent> = _connectivity.asSharedFlow()

    // Send→command_sent is serialized: the server generates the correlationId, so we
    // must not have two sends racing for the next `command_sent`.
    private val sendLock = Mutex()

    @Volatile
    private var pendingAck: CompletableDeferred<String>? = null

    // correlationId -> waiter for the eventual `command_response`.
    private val pendingResponses = ConcurrentHashMap<String, CompletableDeferred<JsonElement?>>()

    private var webSocket: WebSocket? = null
    private var token: String? = null
    private var retryJob: Job? = null
    private var started = false

    /** Call once at app startup. */
    fun start() {
        if (started) return
        started = true
        scope.launch {
            sessionStore.session
                .map { it?.token?.takeIf { t -> t.isNotBlank() } }
                .distinctUntilChanged()
                .collect { newToken -> onTokenChanged(newToken) }
        }
    }

    /** Reconnect right now instead of waiting for the backoff. */
    fun reconnect() {
        token?.let { connect(it) }
    }

    /**
     * Sends the command and suspends until the device answers (or a timeout /
     * disconnect / rejection ends the wait). See [CommandOutcome].
     */
    suspend fun request(
        deviceId: Int,
        comando: String,
        parametros: String,
        responseTimeout: Duration = RESPONSE_TIMEOUT,
    ): CommandOutcome {
        val frame = NetworkModule.json.encodeToString(
            WsCommand(deviceId = deviceId, comando = comando, parametros = parametros),
        )

        // --- Phase 1: send and capture the server-assigned correlationId. --------
        val correlationId = sendLock.withLock {
            val ws = webSocket
            if (ws == null || _state.value != SocketState.Connected) {
                Log.w(TAG, "device=$deviceId $comando descartado (sin conexión)")
                return CommandOutcome.Disconnected
            }
            val ack = CompletableDeferred<String>()
            pendingAck = ack
            try {
                if (!ws.send(frame)) {
                    Log.w(TAG, "device=$deviceId $comando falló al enviar")
                    return CommandOutcome.Disconnected
                }
                Log.d(TAG, ">> device=$deviceId $frame")
                withTimeout(ACK_TIMEOUT) { ack.await() }
            } catch (e: CommandRejected) {
                Log.w(TAG, "device=$deviceId $comando rechazado: ${e.reason}")
                return CommandOutcome.Rejected(e.reason)
            } catch (e: TimeoutCancellationException) {
                Log.w(TAG, "device=$deviceId $comando sin command_sent en $ACK_TIMEOUT")
                return CommandOutcome.NoResponse
            } catch (e: SocketUnavailable) {
                return CommandOutcome.Disconnected
            } finally {
                if (pendingAck === ack) pendingAck = null
            }
        }

        // --- Phase 2: wait for the command_response with that correlationId. ----
        val slot = CompletableDeferred<JsonElement?>()
        pendingResponses[correlationId] = slot
        Log.d(TAG, "device=$deviceId $comando enviado, corr=$correlationId")
        return try {
            val payload = withTimeout(responseTimeout) { slot.await() }
            Log.d(TAG, "device=$deviceId $comando <- corr=$correlationId payload=$payload")
            CommandOutcome.Ok(payloadToText(payload))
        } catch (e: TimeoutCancellationException) {
            Log.w(TAG, "device=$deviceId $comando corr=$correlationId sin respuesta en $responseTimeout")
            CommandOutcome.NoResponse
        } catch (e: SocketUnavailable) {
            Log.w(TAG, "device=$deviceId $comando corr=$correlationId cancelado (conexión perdida)")
            CommandOutcome.Disconnected
        } finally {
            pendingResponses.remove(correlationId, slot)
        }
    }

    private fun onTokenChanged(newToken: String?) {
        token = newToken
        retryJob?.cancel()
        webSocket?.cancel()
        webSocket = null
        failAllPending()
        if (newToken == null) {
            _state.value = SocketState.Idle
        } else {
            connect(newToken)
        }
    }

    private fun connect(authToken: String) {
        retryJob?.cancel()
        webSocket?.cancel()
        _state.value = SocketState.Connecting
        val url = NetworkModule.userSocketUrl()
        Log.d(TAG, "conectando a $url")
        val request = Request.Builder()
            .url(url)
            .header("Authorization", "Bearer $authToken")
            .build()
        webSocket = NetworkModule.webSocketClient().newWebSocket(request, listener)
    }

    private fun scheduleRetry() {
        retryJob?.cancel()
        retryJob = scope.launch {
            delay(RETRY_DELAY_MS)
            token?.let { connect(it) }
        }
    }

    /** Fail every in-flight command (ack + responses) — the connection is gone. */
    private fun failAllPending() {
        pendingAck?.completeExceptionally(SocketUnavailable())
        pendingAck = null
        val slots = pendingResponses.values.toList()
        pendingResponses.clear()
        slots.forEach { it.completeExceptionally(SocketUnavailable()) }
    }

    private fun payloadToText(payload: JsonElement?): String = when {
        payload == null -> ""
        payload is JsonPrimitive && payload.isString -> payload.content
        else -> payload.toString()
    }

    /** The socket dropped while a caller was waiting. */
    private class SocketUnavailable : Exception()

    /** The server sent an `error` frame for the command we just posted. */
    private class CommandRejected(val reason: String) : Exception(reason)

    private val listener = object : WebSocketListener() {
        override fun onOpen(ws: WebSocket, response: Response) {
            Log.d(TAG, "conectado")
            _state.value = SocketState.Connected
        }

        override fun onMessage(ws: WebSocket, text: String) {
            Log.d(TAG, "<< $text")
            val msg = runCatching {
                NetworkModule.json.decodeFromString<WsServerMessage>(text)
            }.getOrNull()
            if (msg == null) {
                Log.w(TAG, "   frame no reconocido, ignorado")
                return
            }
            when (msg.type) {
                WsMessageType.COMMAND_SENT -> {
                    val corr = msg.correlationId
                    if (corr == null) {
                        Log.w(TAG, "   command_sent sin correlationId")
                    } else {
                        Log.d(TAG, "   command_sent corr=$corr (device=${msg.deviceId})")
                        pendingAck?.complete(corr)
                    }
                }

                WsMessageType.COMMAND_RESPONSE -> {
                    val slot = msg.correlationId?.let { pendingResponses[it] }
                    if (slot != null) {
                        Log.d(TAG, "   command_response corr=${msg.correlationId} emparejado")
                        slot.complete(msg.payload)
                    } else {
                        Log.w(TAG, "   command_response corr=${msg.correlationId} sin comando en curso")
                    }
                }

                WsMessageType.ERROR -> {
                    val reason = (msg.payload as? JsonPrimitive)?.contentOrNull
                        ?: "El servidor rechazó el comando"
                    val ack = pendingAck
                    if (ack != null) {
                        Log.w(TAG, "   error para el comando en curso: $reason")
                        ack.completeExceptionally(CommandRejected(reason))
                    } else {
                        Log.w(TAG, "   error sin comando en curso: $reason")
                    }
                }

                WsMessageType.DEVICE_STATUS -> {
                    val id = msg.deviceId ?: return
                    Log.d(TAG, "   device_status device=$id -> recarga")
                    _deviceStatus.tryEmit(DeviceStatusEvent(id, msg.payload))
                    _deviceUpdates.tryEmit(id)
                }

                WsMessageType.DEVICE_ONLINE -> {
                    val id = msg.deviceId ?: return
                    Log.d(TAG, "   device_online device=$id")
                    _connectivity.tryEmit(DeviceConnectivityEvent(id, online = true))
                    _deviceUpdates.tryEmit(id)
                }

                WsMessageType.DEVICE_OFFLINE -> {
                    val id = msg.deviceId ?: return
                    Log.d(TAG, "   device_offline device=$id")
                    _connectivity.tryEmit(DeviceConnectivityEvent(id, online = false))
                    _deviceUpdates.tryEmit(id)
                }

                else -> Log.w(TAG, "   type desconocido: ${msg.type}")
            }
        }

        override fun onClosing(ws: WebSocket, code: Int, reason: String) {
            ws.close(NORMAL_CLOSE, null)
        }

        override fun onClosed(ws: WebSocket, code: Int, reason: String) {
            Log.d(TAG, "cerrado code=$code reason=$reason")
            if (ws == webSocket && token != null) {
                _state.value = SocketState.Error("Conexión cerrada")
                failAllPending()
                scheduleRetry()
            }
        }

        override fun onFailure(ws: WebSocket, t: Throwable, response: Response?) {
            Log.w(TAG, "failure: ${t.message}")
            if (ws == webSocket) {
                _state.value = SocketState.Error(t.message ?: "Sin conexión")
                failAllPending()
                scheduleRetry()
            }
        }
    }

    private companion object {
        const val TAG = "UserSocket"
        const val RETRY_DELAY_MS = 5_000L
        const val NORMAL_CLOSE = 1000
        /** How long to wait for `command_sent` after posting a command. */
        val ACK_TIMEOUT: Duration = 10.seconds
        /** How long to wait for the device's `command_response`. */
        val RESPONSE_TIMEOUT: Duration = 15.seconds
    }
}
