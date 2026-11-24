package id.sch.osis.paperless.data

import id.sch.osis.paperless.core.DeviceType
import id.sch.osis.paperless.domain.MeetingDocument
import id.sch.osis.paperless.domain.RoomState
import id.sch.osis.paperless.domain.UserProfile
import id.sch.osis.paperless.protocol.ClientCommand
import id.sch.osis.paperless.protocol.NoticeLevel
import id.sch.osis.paperless.protocol.OsisJson
import id.sch.osis.paperless.protocol.ServerEvent
import io.ktor.client.HttpClient
import io.ktor.client.plugins.websocket.webSocket
import io.ktor.websocket.Frame
import io.ktor.websocket.readText
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/** Membuat HttpClient Ktor dengan engine platform (OkHttp / CIO / Darwin) + plugin WebSockets. */
expect fun createRealtimeHttpClient(): HttpClient

/**
 * Klien realtime ke server OSIS Paperless melalui WebSocket.
 * Otomatis menyambung ulang (exponential backoff) dan mengirim ulang autentikasi.
 */
class RemoteMeetingRepository(
    private val serverUrl: String,
    private val scope: CoroutineScope,
) : MeetingRepository {
    private val client by lazy { createRealtimeHttpClient() }
    private val _room = MutableStateFlow<RoomState?>(null)
    override val room: StateFlow<RoomState?> = _room
    private val _connection = MutableStateFlow(ConnectionState.DISCONNECTED)
    override val connection: StateFlow<ConnectionState> = _connection
    private val _notices = MutableSharedFlow<ServerEvent.Notice>(extraBufferCapacity = 64)
    override val notices: SharedFlow<ServerEvent.Notice> = _notices

    private val commandQueue = Channel<ClientCommand>(Channel.UNLIMITED)
    private var loopJob: Job? = null
    private var cachedDocument: MeetingDocument? = null
    private var me: UserProfile? = null

    override suspend fun login(username: String, pin: String, device: DeviceType): Result<UserProfile> {
        val auth = CompletableDeferred<Result<UserProfile>>()
        loopJob?.cancel()
        loopJob = scope.launch { connectLoop(username, pin, device, auth) }
        val result = withTimeoutOrNull(12_000) { auth.await() }
        if (result == null || result.isFailure) {
            loopJob?.cancel()
            _connection.value = ConnectionState.DISCONNECTED
        }
        return result ?: Result.failure(IllegalStateException("Server tidak merespons. Periksa alamat $serverUrl dan jaringan Wi-Fi yang sama."))
    }

    private suspend fun connectLoop(username: String, pin: String, device: DeviceType, auth: CompletableDeferred<Result<UserProfile>>) {
        var attempt = 0
        while (scope.isActive) {
            _connection.value = if (attempt == 0) ConnectionState.CONNECTING else ConnectionState.RECONNECTING
            var authFailed = false
            try {
                client.webSocket(urlString = serverUrl) {
                    send(Frame.Text(OsisJson.encodeToString<ClientCommand>(ClientCommand.Hello(username, pin, device))))
                    val sender = launch {
                        for (cmd in commandQueue) send(Frame.Text(OsisJson.encodeToString<ClientCommand>(cmd)))
                    }
                    try {
                        for (frame in incoming) {
                            if (frame !is Frame.Text) continue
                            when (val event = OsisJson.decodeFromString<ServerEvent>(frame.readText())) {
                                is ServerEvent.AuthOk -> {
                                    me = event.user
                                    attempt = 0
                                    _connection.value = ConnectionState.CONNECTED
                                    send(Frame.Text(OsisJson.encodeToString<ClientCommand>(ClientCommand.JoinRoom(device))))
                                    if (!auth.isCompleted) auth.complete(Result.success(event.user))
                                }
                                is ServerEvent.AuthFailed -> {
                                    authFailed = true
                                    if (!auth.isCompleted) auth.complete(Result.failure(IllegalArgumentException(event.reason)))
                                    break
                                }
                                is ServerEvent.Snapshot -> {
                                    val doc = event.state.document ?: cachedDocument?.takeIf { it.id == event.state.session.documentId }
                                    _room.value = event.state.copy(document = doc)
                                }
                                is ServerEvent.DocumentPublished -> {
                                    cachedDocument = event.document
                                    _room.value?.let { _room.value = it.copy(document = event.document) }
                                }
                                is ServerEvent.Notice -> _notices.emit(event)
                            }
                        }
                    } finally {
                        sender.cancel()
                    }
                }
            } catch (e: Exception) {
                if (!scope.isActive) return
                if (!auth.isCompleted && attempt >= 1) {
                    auth.complete(Result.failure(IllegalStateException("Gagal terhubung ke server: ${e.message ?: e::class.simpleName}")))
                }
            }
            if (authFailed || (auth.isCompleted && auth.getCompleted().isFailure)) return
            attempt++
            _connection.value = ConnectionState.RECONNECTING
            delay((1000L shl attempt.coerceAtMost(4)).coerceAtMost(15_000))
        }
    }

    override suspend fun send(command: ClientCommand): String? {
        if (_connection.value != ConnectionState.CONNECTED) {
            _notices.emit(ServerEvent.Notice(NoticeLevel.WARNING, "Koneksi terputus — perintah akan dikirim saat tersambung kembali."))
        }
        commandQueue.send(command)
        return null
    }

    override suspend fun close() {
        runCatching { if (_connection.value == ConnectionState.CONNECTED) commandQueue.send(ClientCommand.LeaveRoom) }
        delay(150)
        loopJob?.cancel()
        _connection.value = ConnectionState.DISCONNECTED
        _room.value = null
        me = null
    }
}
