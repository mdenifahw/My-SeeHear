package id.sch.osis.paperless.domain

import id.sch.osis.paperless.core.DeviceType
import kotlinx.serialization.Serializable

// ───────────────────────────── Pengguna ─────────────────────────────

@Serializable
enum class UserRole { LEADER, PARTICIPANT }

@Serializable
enum class LeaderPosition(val label: String) {
    KETUA_UMUM("Ketua Umum"),
    KETUA_1("Ketua 1"),
    SEKRETARIS("Sekretaris"),
}

@Serializable
enum class OrgCategory(val label: String, val shortLabel: String) {
    PIMPINAN("Pimpinan Rapat", "Pimpinan"),
    SEKBID("Seksi Bidang", "Sekbid"),
    OSIS_FRAKSI("Anggota OSIS (Fraksi)", "Fraksi"),
    EKSKUL("Perwakilan Ekstrakurikuler", "Ekskul"),
    MPK("Anggota MPK", "MPK"),
}

/** Profil publik pengguna (tanpa PIN). Disebarkan ke semua klien. */
@Serializable
data class UserProfile(
    val id: String,
    val username: String,
    val name: String,
    val kelas: String,
    val jabatan: String,
    val role: UserRole,
    val leaderPosition: LeaderPosition? = null,
    val category: OrgCategory,
    /** "Sekbid 3", "Fraksi 1", "Basket", "MPK", "Pengurus Inti OSIS" */
    val unit: String,
    /** Hak masuk ke ruang rapat aktif. */
    val roomAccess: Boolean = true,
) {
    val isLeader: Boolean get() = role == UserRole.LEADER

    val orgLabel: String
        get() = when (category) {
            OrgCategory.PIMPINAN -> unit
            OrgCategory.SEKBID -> unit
            OrgCategory.OSIS_FRAKSI -> "OSIS $unit"
            OrgCategory.EKSKUL -> "Ekskul $unit"
            OrgCategory.MPK -> "MPK"
        }

    val initials: String
        get() = name.split(" ").filter { it.isNotBlank() }.take(2)
            .joinToString("") { it.first().uppercase() }
}

/** Akun di sisi server (mengandung PIN). Tidak pernah dikirim ke klien. */
@Serializable
data class UserAccount(val profile: UserProfile, val pin: String)

/** Input formulir / impor CSV untuk mendaftarkan akun peserta. */
@Serializable
data class AccountInput(
    val name: String,
    val kelas: String,
    val jabatan: String,
    val category: OrgCategory,
    val unit: String,
    val username: String,
    val pin: String = "",
    val id: String? = null,
    val roomAccess: Boolean = true,
)

// ───────────────────────────── Sesi Rapat ─────────────────────────────

@Serializable
enum class SessionStatus(val label: String) {
    PREPARING("Persiapan"),
    LIVE("Rapat Berlangsung"),
    BREAK("Istirahat"),
    ENDED("Rapat Selesai"),
}

@Serializable
data class MeetingSession(
    val id: String,
    /** Kode sesi rapat — juga dipakai untuk validasi unduhan di Web Portal QR. */
    val code: String,
    val title: String,
    val status: SessionStatus = SessionStatus.PREPARING,
    val documentId: String? = null,
    val leaderPage: Int = 1,
    val startedAt: Long? = null,
    val endedAt: Long? = null,
    val breakUntil: Long? = null,
    val startedBy: String? = null,
)

// ───────────────────────────── Dokumen ─────────────────────────────

@Serializable
enum class DocumentFormat(val label: String) { PDF("PDF"), DOCX("Word .docx"), DOC("Word .doc"), SAMPLE("Contoh") }

@Serializable
enum class BlockKind { TITLE, HEADING, PARAGRAPH, BULLET, TABLE_ROW }

/**
 * Blok teks terstandarisasi. Setiap blok dipecah per kalimat agar "blok kalimat"
 * (highlight) memiliki koordinat yang identik di Mobile maupun Desktop.
 */
@Serializable
data class DocBlock(val id: String, val kind: BlockKind, val sentences: List<String>) {
    val text: String get() = sentences.joinToString(" ")
}

@Serializable
data class DocPage(val number: Int, val blocks: List<DocBlock>)

@Serializable
data class MeetingDocument(
    val id: String,
    val title: String,
    val fileName: String,
    val format: DocumentFormat,
    val pages: List<DocPage>,
    val uploadedById: String,
    val uploadedByName: String,
    val uploadedAt: Long,
    /** Kunci pratinjau halaman asli (path lokal / URL server PDF hasil konversi). */
    val sourceKey: String? = null,
    val conversionNote: String? = null,
) {
    val pageCount: Int get() = pages.size
}

// ───────────────────────────── Diskusi & Sorotan ─────────────────────────────

@Serializable
data class HighlightAnchor(
    val page: Int,
    val blockId: String,
    val startSentence: Int,
    val endSentence: Int,
    val quote: String,
) {
    fun covers(blockId: String, sentence: Int): Boolean =
        this.blockId == blockId && sentence in startSentence..endSentence
}

@Serializable
enum class MessageKind { TEXT, HIGHLIGHT, SYSTEM }

@Serializable
data class ChatMessage(
    val id: String,
    val kind: MessageKind,
    val senderId: String,
    val senderName: String,
    val senderRole: UserRole,
    val senderOrg: String,
    val text: String,
    val anchor: HighlightAnchor? = null,
    val replyToId: String? = null,
    val revision: Boolean = false,
    val revisionNote: String? = null,
    val revisionMarkedBy: String? = null,
    val createdAt: Long,
)

// ───────────────────────────── Izin Akses (Temporary Unlock) ─────────────────────────────

@Serializable
enum class UnlockStatus(val label: String) {
    PENDING("Menunggu Persetujuan"),
    APPROVED("Disetujui"),
    REJECTED("Ditolak"),
    EXPIRED("Selesai"),
    REVOKED("Dicabut (Veto)"),
    CANCELLED("Dibatalkan"),
}

@Serializable
data class UnlockRequest(
    val id: String,
    val userId: String,
    val userName: String,
    val userOrg: String,
    val reason: String,
    val requestedMinutes: Int,
    val status: UnlockStatus = UnlockStatus.PENDING,
    val grantedMinutes: Int? = null,
    val decidedById: String? = null,
    val decidedByName: String? = null,
    val note: String? = null,
    val createdAt: Long,
    val decidedAt: Long? = null,
    val expiresAt: Long? = null,
) {
    fun isActiveAt(now: Long): Boolean = status == UnlockStatus.APPROVED && (expiresAt ?: 0L) > now
}

// ───────────────────────────── Kehadiran & Perangkat ─────────────────────────────

@Serializable
enum class PresenceStatus(val label: String) {
    ONLINE("Online"),
    ON_LEAVE("Sedang Izin Keluar"),
    OFFLINE("Belum Bergabung"),
}

@Serializable
enum class DeviceLockState(val label: String) {
    LOCKED("Terkunci"),
    TEMP_UNLOCKED("Izin Sementara"),
    UNLOCKED("Tidak Terkunci"),
    NOT_SUPPORTED("Kunci Tidak Didukung"),
}

@Serializable
data class Presence(
    val userId: String,
    val device: DeviceType,
    val status: PresenceStatus,
    val lockState: DeviceLockState,
    val joinedAt: Long,
    val lastSeen: Long,
)

@Serializable
data class AttendanceRecord(
    val sessionId: String,
    val sessionTitle: String,
    val userId: String,
    val date: Long,
    val status: String,
)

// ───────────────────────────── Audit Log Keamanan ─────────────────────────────

@Serializable
enum class AuditType(val label: String, val isViolation: Boolean = false, val isPermission: Boolean = false) {
    SESSION_STARTED("Rapat dimulai"),
    SESSION_PAUSED("Rapat dijeda"),
    SESSION_RESUMED("Rapat dilanjutkan"),
    SESSION_ENDED("Rapat diakhiri"),
    DOCUMENT_UPLOADED("Dokumen diunggah"),
    UNLOCK_REQUESTED("Permintaan izin", isPermission = true),
    UNLOCK_APPROVED("Izin disetujui", isPermission = true),
    UNLOCK_REJECTED("Izin ditolak", isPermission = true),
    UNLOCK_EXPIRED("Izin berakhir", isPermission = true),
    UNLOCK_REVOKED("Izin dicabut (veto)", isPermission = true),
    FORCE_EXIT_ATTEMPT("Percobaan keluar paksa", isViolation = true),
    LOCK_BROKEN("Kunci layar dilepas paksa", isViolation = true),
    FOCUS_LOST("Fokus jendela hilang", isViolation = true),
    USER_JOINED("Bergabung"),
    USER_LEFT("Keluar ruang rapat"),
    ACCOUNT_CHANGED("Perubahan akun"),
}

@Serializable
enum class ViolationKind(val auditType: AuditType, val label: String) {
    HOME_OR_RECENTS(AuditType.FORCE_EXIT_ATTEMPT, "Menekan Home/Recent Apps"),
    APP_BACKGROUNDED(AuditType.FORCE_EXIT_ATTEMPT, "Aplikasi berpindah ke latar belakang"),
    BACK_PRESSED(AuditType.FORCE_EXIT_ATTEMPT, "Menekan tombol kembali saat terkunci"),
    WINDOW_SWITCH(AuditType.FORCE_EXIT_ATTEMPT, "Mencoba berpindah jendela (Alt+Tab/Cmd+Tab)"),
    PIN_REMOVED(AuditType.LOCK_BROKEN, "Sematan layar (Screen Pinning) dilepas"),
    FOCUS_LOST(AuditType.FOCUS_LOST, "Fokus jendela rapat hilang"),
}

@Serializable
data class AuditEntry(
    val id: String,
    val type: AuditType,
    val actorId: String,
    val actorName: String,
    val detail: String,
    val createdAt: Long,
    val targetId: String? = null,
)

// ───────────────────────────── State Ruang Rapat ─────────────────────────────

/** Satu-satunya sumber kebenaran ruang rapat (disinkronkan real-time ke semua klien). */
@Serializable
data class RoomState(
    val session: MeetingSession,
    val users: List<UserProfile>,
    val presence: List<Presence> = emptyList(),
    val document: MeetingDocument? = null,
    val messages: List<ChatMessage> = emptyList(),
    val unlockRequests: List<UnlockRequest> = emptyList(),
    val auditLog: List<AuditEntry> = emptyList(),
    val attendance: List<AttendanceRecord> = emptyList(),
    val revision: Long = 0,
) {
    fun user(id: String): UserProfile? = users.firstOrNull { it.id == id }
    fun presenceOf(id: String): Presence? = presence.firstOrNull { it.userId == id }
    fun activeGrantFor(userId: String, now: Long): UnlockRequest? =
        unlockRequests.firstOrNull { it.userId == userId && it.isActiveAt(now) }

    val participants: List<UserProfile> get() = users.filter { !it.isLeader }
    val leaders: List<UserProfile> get() = users.filter { it.isLeader }
}
