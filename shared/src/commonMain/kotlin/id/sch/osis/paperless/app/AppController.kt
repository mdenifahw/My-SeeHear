package id.sch.osis.paperless.app

import id.sch.osis.paperless.core.currentDeviceType
import id.sch.osis.paperless.core.nowMillis
import id.sch.osis.paperless.data.ConnectionState
import id.sch.osis.paperless.data.LocalMeetingRepository
import id.sch.osis.paperless.data.MeetingRepository
import id.sch.osis.paperless.data.RemoteMeetingRepository
import id.sch.osis.paperless.domain.AccountInput
import id.sch.osis.paperless.domain.DeviceLockState
import id.sch.osis.paperless.domain.HighlightAnchor
import id.sch.osis.paperless.domain.MessageKind
import id.sch.osis.paperless.domain.RoomState
import id.sch.osis.paperless.domain.SessionStatus
import id.sch.osis.paperless.domain.UserProfile
import id.sch.osis.paperless.platform.IncomingNotification
import id.sch.osis.paperless.platform.PlatformServices
import id.sch.osis.paperless.platform.RevisionExportItem
import id.sch.osis.paperless.protocol.ClientCommand
import id.sch.osis.paperless.protocol.NoticeLevel
import id.sch.osis.paperless.protocol.ServerEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/** 5 halaman utama untuk masing-masing peran (Pimpinan Rapat & Peserta Rapat). */
enum class AppTab(val leaderLabel: String, val participantLabel: String) {
    ROOM("Dokumen", "Dokumen"),
    ATTENDANCE("Kehadiran", "Kehadiran"),
    ACCESS("Izin & Kunci", "Minta Izin"),
    CHAT("Diskusi", "Diskusi"),
    PROFILE("Profil & Akun", "Profil"),
}

enum class ChatFilter(val label: String) {
    ALL("Semua"),
    LEADER_HIGHLIGHTS("Sorotan Pimpinan"),
    PARTICIPANT_HIGHLIGHTS("Sorotan Peserta"),
    REVISIONS("Poin Revisi"),
}

data class BannerNotice(
    val id: Long,
    val level: NoticeLevel,
    val message: String,
)

data class AppUiState(
    val user: UserProfile? = null,
    val room: RoomState? = null,
    val connection: ConnectionState = ConnectionState.DISCONNECTED,
    val activeTab: AppTab = AppTab.ROOM,
    val isLoggingIn: Boolean = false,
    val loginError: String? = null,
    val serverUrl: String = "ws://192.168.1.10:8080/ws",
    val demoMode: Boolean = true,
    /** Nomor halaman lokal yang sedang dilihat pengguna. */
    val viewingPage: Int = 1,
    /** Mode Ikuti Layar Pimpinan (default aktif). */
    val followLeader: Boolean = true,
    /** Zoom teks dokumen (0.85f .. 1.6f). */
    val docZoom: Float = 1.0f,
    /** Anchor sorotan yang baru saja diklik dari Ruang Diskusi (jump-to-page). */
    val focusedAnchor: HighlightAnchor? = null,
    /** Filter aktif di Ruang Diskusi. */
    val chatFilter: ChatFilter = ChatFilter.ALL,
    /** Waktu sekarang (diperbarui tiap detik agar hitung mundur izin & istirahat hidup). */
    val now: Long = nowMillis(),
    /** Status kunci perangkat lokal. */
    val localLockState: DeviceLockState = DeviceLockState.UNLOCKED,
    /** Notifikasi masuk saat Mode Fokus aktif (baca-saja). */
    val notifications: List<IncomingNotification> = emptyList(),
    val notificationPermissionGranted: Boolean = false,
    /** Banner notifikasi terbaru (muncul sebentar di atas layar). */
    val latestNotificationBanner: IncomingNotification? = null,
    /** Pesan umpan balik singkat. */
    val banner: BannerNotice? = null,
    val isBusy: Boolean = false,
) {
    val isLockedForMe: Boolean
        get() {
            val u = user ?: return false
            val r = room ?: return false
            if (u.isLeader) return false
            if (r.session.status != SessionStatus.LIVE) return false
            return r.activeGrantFor(u.id, now) == null
        }

    val activeUnlockGrant
        get() = user?.let { u -> room?.activeGrantFor(u.id, now) }
}

/**
 * Controller utama aplikasi (MVI/MVVM + StateFlow) yang dibagi lintas Android, Desktop, dan iOS.
 */
class AppController(
    private val scope: CoroutineScope,
    val platform: PlatformServices,
) {
    private val _ui = MutableStateFlow(AppUiState())
    val ui: StateFlow<AppUiState> = _ui.asStateFlow()

    private var repo: MeetingRepository? = null
    private val sessionJobs = mutableListOf<Job>()
    private var lastLockedDecision: Boolean? = null
    private var lastNotifId: String? = null

    init {
        // Jam lokal 1 detik untuk timer hitung mundur & evaluasi kunci perangkat.
        scope.launch {
            while (isActive) {
                delay(1000)
                val now = nowMillis()
                _ui.update { it.copy(now = now) }
                syncFocusLock()
            }
        }
        // Pantau status kunci dari platform & kirim heartbeat.
        scope.launch {
            platform.focusGuard.lockState.collect { lockState ->
                _ui.update { it.copy(localLockState = lockState) }
                if (_ui.value.user != null) {
                    repo?.send(ClientCommand.Heartbeat(lockState))
                }
            }
        }
        // Laporkan percobaan keluar paksa dari FocusGuard ke Audit Log server/engine.
        scope.launch {
            platform.focusGuard.violations.collect { (kind, detail) ->
                if (_ui.value.isLockedForMe) {
                    repo?.send(ClientCommand.ReportViolation(kind, detail))
                    showBanner(NoticeLevel.ERROR, "Mode Fokus aktif — tidak dapat keluar aplikasi tanpa izin Pimpinan.")
                }
            }
        }
        // Pantau notifikasi masuk (baca-saja saat terkunci).
        scope.launch {
            platform.notificationFeed.notifications.collect { list ->
                val newest = list.firstOrNull()
                val showBannerNotif = newest != null && newest.id != lastNotifId && _ui.value.isLockedForMe
                if (newest != null) lastNotifId = newest.id
                _ui.update {
                    it.copy(
                        notifications = list,
                        latestNotificationBanner = if (showBannerNotif) newest else it.latestNotificationBanner,
                    )
                }
            }
        }
        scope.launch {
            platform.notificationFeed.isPermissionGranted.collect { granted ->
                _ui.update { it.copy(notificationPermissionGranted = granted) }
            }
        }
    }

    fun setDemoMode(demo: Boolean) = _ui.update { it.copy(demoMode = demo, loginError = null) }
    fun setServerUrl(url: String) = _ui.update { it.copy(serverUrl = url, loginError = null) }

    fun login(username: String, pin: String) {
        if (_ui.value.isLoggingIn) return
        scope.launch {
            _ui.update { it.copy(isLoggingIn = true, loginError = null) }
            cleanupSession()
            val newRepo: MeetingRepository = if (_ui.value.demoMode) {
                LocalMeetingRepository(scope)
            } else {
                RemoteMeetingRepository(_ui.value.serverUrl.trim(), scope)
            }
            val result = newRepo.login(username.trim(), pin.trim(), currentDeviceType)
            result.fold(
                onSuccess = { profile ->
                    repo = newRepo
                    _ui.update {
                        it.copy(
                            user = profile,
                            isLoggingIn = false,
                            loginError = null,
                            activeTab = AppTab.ROOM,
                            followLeader = true,
                        )
                    }
                    observeRepository(newRepo)
                },
                onFailure = { err ->
                    runCatching { newRepo.close() }
                    _ui.update { it.copy(isLoggingIn = false, loginError = err.message ?: "Gagal masuk.") }
                },
            )
        }
    }

    private fun observeRepository(r: MeetingRepository) {
        sessionJobs += scope.launch {
            r.room.collect { roomState ->
                _ui.update { current ->
                    val updatedUser = current.user?.let { u -> roomState?.user(u.id) ?: u }
                    val targetPage = when {
                        roomState == null -> current.viewingPage
                        current.followLeader -> roomState.session.leaderPage
                        else -> current.viewingPage.coerceIn(1, (roomState.document?.pageCount ?: 1).coerceAtLeast(1))
                    }
                    current.copy(user = updatedUser, room = roomState, viewingPage = targetPage)
                }
                syncFocusLock()
            }
        }
        sessionJobs += scope.launch {
            r.connection.collect { conn -> _ui.update { it.copy(connection = conn) } }
        }
        sessionJobs += scope.launch {
            r.notices.collect { notice -> showBanner(notice.level, notice.message) }
        }
    }

    private fun syncFocusLock() {
        val state = _ui.value
        val shouldLock = state.isLockedForMe
        if (lastLockedDecision == shouldLock) return
        lastLockedDecision = shouldLock
        if (shouldLock) {
            platform.focusGuard.engageLock()
        } else {
            val isTemp = state.user?.isLeader == false && state.room?.session?.status == SessionStatus.LIVE && state.activeUnlockGrant != null
            platform.focusGuard.releaseLock(temporary = isTemp)
        }
    }

    fun logout() {
        if (_ui.value.isLockedForMe) {
            showBanner(NoticeLevel.ERROR, "Tidak dapat keluar akun saat Mode Fokus aktif. Minta izin terlebih dahulu.")
            return
        }
        scope.launch {
            cleanupSession()
            platform.focusGuard.releaseLock(temporary = false)
            lastLockedDecision = null
            _ui.update {
                it.copy(
                    user = null,
                    room = null,
                    connection = ConnectionState.DISCONNECTED,
                    activeTab = AppTab.ROOM,
                    focusedAnchor = null,
                    latestNotificationBanner = null,
                )
            }
        }
    }

    private suspend fun cleanupSession() {
        sessionJobs.forEach { it.cancel() }
        sessionJobs.clear()
        repo?.close()
        repo = null
    }

    // ───────────────────────────── Navigasi & Dokumen ─────────────────────────────

    fun selectTab(tab: AppTab) = _ui.update { it.copy(activeTab = tab) }

    fun setFollowLeader(follow: Boolean) = _ui.update { st ->
        st.copy(
            followLeader = follow,
            viewingPage = if (follow) st.room?.session?.leaderPage ?: st.viewingPage else st.viewingPage,
        )
    }

    fun changePage(page: Int) {
        val doc = _ui.value.room?.document ?: return
        val clamped = page.coerceIn(1, doc.pageCount)
        val user = _ui.value.user ?: return
        _ui.update { it.copy(viewingPage = clamped, focusedAnchor = null) }
        if (user.isLeader && _ui.value.followLeader) {
            send(ClientCommand.ChangePage(clamped))
        } else if (!user.isLeader && clamped != _ui.value.room?.session?.leaderPage) {
            _ui.update { it.copy(followLeader = false) }
        }
    }

    fun setZoom(zoom: Float) = _ui.update { it.copy(docZoom = zoom.coerceIn(0.85f, 1.6f)) }

    fun jumpToHighlight(anchor: HighlightAnchor) {
        _ui.update {
            it.copy(
                activeTab = AppTab.ROOM,
                followLeader = false,
                viewingPage = anchor.page,
                focusedAnchor = anchor,
            )
        }
    }

    fun clearFocusedAnchor() = _ui.update { it.copy(focusedAnchor = null) }
    fun setChatFilter(filter: ChatFilter) = _ui.update { it.copy(chatFilter = filter) }
    fun dismissBanner() = _ui.update { it.copy(banner = null) }
    fun dismissNotificationBanner() = _ui.update { it.copy(latestNotificationBanner = null) }

    // ───────────────────────────── Perintah Ruang Rapat ─────────────────────────────

    fun startMeeting() = send(ClientCommand.StartMeeting)
    fun pauseMeeting(minutes: Int) = send(ClientCommand.PauseMeeting(minutes))
    fun resumeMeeting() = send(ClientCommand.ResumeMeeting)
    fun endMeeting() = send(ClientCommand.EndMeeting)
    fun prepareNewSession(title: String) = send(ClientCommand.PrepareNewSession(title))

    fun sendHighlight(anchor: HighlightAnchor, comment: String) {
        send(ClientCommand.SendHighlight(anchor, comment))
        showBanner(NoticeLevel.SUCCESS, "Sorotan dikirim ke Ruang Diskusi (Hal. ${anchor.page}).")
    }

    fun sendChat(text: String, replyToId: String? = null) = send(ClientCommand.SendChat(text, replyToId))
    fun toggleRevision(messageId: String, marked: Boolean, note: String? = null) =
        send(ClientCommand.ToggleRevision(messageId, marked, note))

    fun requestUnlock(reason: String, minutes: Int) = send(ClientCommand.RequestUnlock(reason, minutes))
    fun cancelUnlock(requestId: String) = send(ClientCommand.CancelUnlock(requestId))
    fun decideUnlock(requestId: String, approve: Boolean, minutes: Int = 3, note: String? = null) =
        send(ClientCommand.DecideUnlock(requestId, approve, minutes, note))
    fun revokeUnlock(requestId: String) = send(ClientCommand.RevokeUnlock(requestId))

    fun upsertAccount(input: AccountInput) = send(ClientCommand.UpsertAccount(input))
    fun deleteAccount(userId: String) = send(ClientCommand.DeleteAccount(userId))
    fun importAccounts(inputs: List<AccountInput>) = send(ClientCommand.ImportAccounts(inputs))
    fun setRoomAccess(userId: String, allowed: Boolean) = send(ClientCommand.SetRoomAccess(userId, allowed))

    fun pickAndUploadDocument() {
        val user = _ui.value.user ?: return
        scope.launch {
            _ui.update { it.copy(isBusy = true) }
            val result = platform.documentBridge.pickAndConvert(user.id, user.name)
            _ui.update { it.copy(isBusy = false) }
            result.fold(
                onSuccess = { doc ->
                    if (doc != null) {
                        repo?.send(ClientCommand.UploadDocument(doc))
                    }
                },
                onFailure = { err ->
                    showBanner(NoticeLevel.ERROR, err.message ?: "Gagal membaca dokumen.")
                },
            )
        }
    }

    fun exportRevisions() {
        val room = _ui.value.room ?: return
        val revisions = room.messages.filter { it.revision || (it.kind == MessageKind.HIGHLIGHT && it.revision) }
        if (revisions.isEmpty()) {
            showBanner(NoticeLevel.WARNING, "Belum ada pesan yang ditandai sebagai Poin Revisi.")
            return
        }
        scope.launch {
            _ui.update { it.copy(isBusy = true) }
            val items = revisions.map { m ->
                RevisionExportItem(
                    page = m.anchor?.page,
                    quote = m.anchor?.quote,
                    comment = m.text,
                    author = "${m.senderName} (${m.senderOrg})",
                    note = m.revisionNote,
                )
            }
            val result = platform.documentBridge.exportRevisionPdf(
                sessionTitle = room.session.title,
                sessionCode = room.session.code,
                documentTitle = room.document?.title ?: "-",
                lines = items,
            )
            _ui.update { it.copy(isBusy = false) }
            result.fold(
                onSuccess = { path -> showBanner(NoticeLevel.SUCCESS, "Notulensi revisi diekspor: $path") },
                onFailure = { err -> showBanner(NoticeLevel.ERROR, err.message ?: "Gagal mengekspor notulensi.") },
            )
        }
    }

    private fun send(cmd: ClientCommand) {
        scope.launch { repo?.send(cmd) }
    }

    fun showBanner(level: NoticeLevel, message: String) {
        val id = nowMillis()
        _ui.update { it.copy(banner = BannerNotice(id, level, message)) }
        scope.launch {
            delay(4500)
            _ui.update { if (it.banner?.id == id) it.copy(banner = null) else it }
        }
    }
}
