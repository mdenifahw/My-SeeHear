package id.sch.osis.paperless.data

import id.sch.osis.paperless.core.DeviceType
import id.sch.osis.paperless.domain.RoomState
import id.sch.osis.paperless.domain.UserProfile
import id.sch.osis.paperless.protocol.ClientCommand
import id.sch.osis.paperless.protocol.ServerEvent
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow

enum class ConnectionState(val label: String) {
    DISCONNECTED("Terputus"),
    CONNECTING("Menghubungkan…"),
    CONNECTED("Terhubung"),
    RECONNECTING("Menyambung ulang…"),
    DEMO("Mode Demo (Offline)"),
}

/** Abstraksi sumber data realtime ruang rapat (Demo in-process atau Server WebSocket). */
interface MeetingRepository {
    val room: StateFlow<RoomState?>
    val connection: StateFlow<ConnectionState>
    val notices: SharedFlow<ServerEvent.Notice>

    suspend fun login(username: String, pin: String, device: DeviceType): Result<UserProfile>
    suspend fun send(command: ClientCommand): String?
    suspend fun close()
}
