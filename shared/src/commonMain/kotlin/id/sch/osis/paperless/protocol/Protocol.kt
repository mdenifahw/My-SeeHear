package id.sch.osis.paperless.protocol

import id.sch.osis.paperless.core.DeviceType
import id.sch.osis.paperless.domain.AccountInput
import id.sch.osis.paperless.domain.DeviceLockState
import id.sch.osis.paperless.domain.HighlightAnchor
import id.sch.osis.paperless.domain.MeetingDocument
import id.sch.osis.paperless.domain.RoomState
import id.sch.osis.paperless.domain.UserProfile
import id.sch.osis.paperless.domain.UserRole
import id.sch.osis.paperless.domain.ViolationKind
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Kontrak WebSocket OSIS Paperless (`ws://<server>:8080/ws`).
 * Semua frame berupa JSON dengan diskriminator `"type"`.
 */
val OsisJson: Json = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
    explicitNulls = false
    classDiscriminator = "type"
}

/** Perintah dari klien → server. */
@Serializable
sealed interface ClientCommand {
    @Serializable @SerialName("hello")
    data class Hello(val username: String, val pin: String, val device: DeviceType, val clientVersion: String = "1.0.0") : ClientCommand

    @Serializable @SerialName("join_room")
    data class JoinRoom(val device: DeviceType) : ClientCommand

    @Serializable @SerialName("leave_room")
    data object LeaveRoom : ClientCommand

    @Serializable @SerialName("heartbeat")
    data class Heartbeat(val lockState: DeviceLockState) : ClientCommand

    @Serializable @SerialName("upload_document")
    data class UploadDocument(val document: MeetingDocument) : ClientCommand

    @Serializable @SerialName("prepare_session")
    data class PrepareNewSession(val title: String) : ClientCommand

    @Serializable @SerialName("start_meeting")
    data object StartMeeting : ClientCommand

    @Serializable @SerialName("pause_meeting")
    data class PauseMeeting(val minutes: Int) : ClientCommand

    @Serializable @SerialName("resume_meeting")
    data object ResumeMeeting : ClientCommand

    @Serializable @SerialName("end_meeting")
    data object EndMeeting : ClientCommand

    @Serializable @SerialName("change_page")
    data class ChangePage(val page: Int) : ClientCommand

    @Serializable @SerialName("send_chat")
    data class SendChat(val text: String, val replyToId: String? = null) : ClientCommand

    @Serializable @SerialName("send_highlight")
    data class SendHighlight(val anchor: HighlightAnchor, val comment: String = "") : ClientCommand

    @Serializable @SerialName("toggle_revision")
    data class ToggleRevision(val messageId: String, val marked: Boolean, val note: String? = null) : ClientCommand

    @Serializable @SerialName("request_unlock")
    data class RequestUnlock(val reason: String, val minutes: Int) : ClientCommand

    @Serializable @SerialName("cancel_unlock")
    data class CancelUnlock(val requestId: String) : ClientCommand

    @Serializable @SerialName("decide_unlock")
    data class DecideUnlock(val requestId: String, val approve: Boolean, val minutes: Int = 3, val note: String? = null) : ClientCommand

    @Serializable @SerialName("revoke_unlock")
    data class RevokeUnlock(val requestId: String) : ClientCommand

    @Serializable @SerialName("report_violation")
    data class ReportViolation(val kind: ViolationKind, val detail: String = "") : ClientCommand

    @Serializable @SerialName("upsert_account")
    data class UpsertAccount(val input: AccountInput) : ClientCommand

    @Serializable @SerialName("delete_account")
    data class DeleteAccount(val userId: String) : ClientCommand

    @Serializable @SerialName("import_accounts")
    data class ImportAccounts(val inputs: List<AccountInput>) : ClientCommand

    @Serializable @SerialName("set_room_access")
    data class SetRoomAccess(val userId: String, val allowed: Boolean) : ClientCommand
}

@Serializable
enum class NoticeLevel { INFO, SUCCESS, WARNING, ERROR }

/** Event dari server → klien. */
@Serializable
sealed interface ServerEvent {
    @Serializable @SerialName("auth_ok")
    data class AuthOk(val user: UserProfile) : ServerEvent

    @Serializable @SerialName("auth_failed")
    data class AuthFailed(val reason: String) : ServerEvent

    /** State ruang rapat lengkap. `document` dapat null bila tidak berubah (lihat [DocumentPublished]). */
    @Serializable @SerialName("snapshot")
    data class Snapshot(val state: RoomState) : ServerEvent

    @Serializable @SerialName("document_published")
    data class DocumentPublished(val document: MeetingDocument) : ServerEvent

    @Serializable @SerialName("notice")
    data class Notice(
        val level: NoticeLevel,
        val message: String,
        val targetUserId: String? = null,
        val targetRole: UserRole? = null,
    ) : ServerEvent
}

fun ServerEvent.Notice.isFor(user: UserProfile): Boolean =
    (targetUserId == null || targetUserId == user.id) && (targetRole == null || targetRole == user.role)
