package id.sch.osis.paperless.platform

import id.sch.osis.paperless.domain.DeviceLockState
import id.sch.osis.paperless.domain.MeetingDocument
import id.sch.osis.paperless.domain.ViolationKind
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.Serializable

/** Pratinjau notifikasi masuk saat Mode Fokus aktif (hanya baca, tidak membuka aplikasi sumber). */
@Serializable
data class IncomingNotification(
    val id: String,
    val appName: String,
    val packageName: String,
    val title: String,
    val body: String,
    val receivedAt: Long,
)

/**
 * Kontrak KMP untuk Mode Kunci Aplikasi (Focus Lock System):
 *  - Android: Lock Task Mode / Screen Pinning + lifecycle & back-press guard.
 *  - Desktop: Always-on-Top Fullscreen Kiosk Guard + window-focus listener.
 */
interface FocusGuard {
    val lockState: StateFlow<DeviceLockState>
    val violations: Flow<Pair<ViolationKind, String>>
    val isSupported: Boolean

    fun engageLock()
    fun releaseLock(temporary: Boolean = false)
}

/**
 * Kontrak KMP untuk pemantau notifikasi masuk (baca-saja saat rapat terkunci):
 *  - Android: NotificationListenerService yang memblokir klik perpindahan aplikasi dan meneruskan
 *    ringkasan aman ke banner & panel riwayat di dalam aplikasi.
 *  - Desktop: feed lokal / simulasi.
 */
interface NotificationFeed {
    val notifications: StateFlow<List<IncomingNotification>>
    val isPermissionGranted: StateFlow<Boolean>

    fun openPermissionSettings()
    fun clear()
    fun pushSimulated(appName: String, title: String, body: String)
}

/** Pemilih & konverter dokumen PDF / Microsoft Word (.docx / .doc) per platform. */
interface DocumentBridge {
    /** Membuka pemilih file platform untuk PDF / DOCX lalu mengonversinya menjadi [MeetingDocument]. */
    suspend fun pickAndConvert(uploaderId: String, uploaderName: String): Result<MeetingDocument?>

    /** Mengekspor daftar revisi/notulensi ke file PDF (atau teks jika fallback) dan mengembalikan lokasi file. */
    suspend fun exportRevisionPdf(
        sessionTitle: String,
        sessionCode: String,
        documentTitle: String,
        lines: List<RevisionExportItem>,
    ): Result<String>
}

data class RevisionExportItem(
    val page: Int?,
    val quote: String?,
    val comment: String,
    val author: String,
    val note: String?,
)

/** Kumpulan bridge platform yang disuntikkan ke [AppController] dari Android / Desktop. */
data class PlatformServices(
    val focusGuard: FocusGuard,
    val notificationFeed: NotificationFeed,
    val documentBridge: DocumentBridge,
)
