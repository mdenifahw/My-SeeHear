package id.sch.osis.paperless.engine

import id.sch.osis.paperless.core.Ids
import id.sch.osis.paperless.core.nowMillis
import id.sch.osis.paperless.domain.AccountInput
import id.sch.osis.paperless.domain.AttendanceRecord
import id.sch.osis.paperless.domain.AuditEntry
import id.sch.osis.paperless.domain.AuditType
import id.sch.osis.paperless.domain.ChatMessage
import id.sch.osis.paperless.domain.DeviceLockState
import id.sch.osis.paperless.domain.MeetingDocument
import id.sch.osis.paperless.domain.MeetingSession
import id.sch.osis.paperless.domain.MessageKind
import id.sch.osis.paperless.domain.OrgCategory
import id.sch.osis.paperless.domain.Permissions
import id.sch.osis.paperless.domain.Presence
import id.sch.osis.paperless.domain.PresenceStatus
import id.sch.osis.paperless.domain.RoomState
import id.sch.osis.paperless.domain.SessionStatus
import id.sch.osis.paperless.domain.UnlockRequest
import id.sch.osis.paperless.domain.UnlockStatus
import id.sch.osis.paperless.domain.UserAccount
import id.sch.osis.paperless.domain.UserProfile
import id.sch.osis.paperless.domain.UserRole
import id.sch.osis.paperless.protocol.ClientCommand
import id.sch.osis.paperless.protocol.NoticeLevel
import id.sch.osis.paperless.protocol.ServerEvent
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Mesin state otoritatif ruang rapat. Dipakai bersama oleh:
 *  - Mode Demo (in-process di perangkat), dan
 *  - Server realtime (Ktor WebSocket) — sehingga aturan bisnis identik.
 *
 * Tidak thread-safe: pemanggil wajib menyerialkan akses (Main dispatcher / Mutex).
 */
class MeetingEngine(
    seedAccounts: List<UserAccount>,
    initialDocument: MeetingDocument? = null,
    seedAttendance: List<AttendanceRecord> = emptyList(),
    sessionTitle: String = "Rapat Pembahasan LPJ Program Kerja OSIS",
    private val clock: () -> Long = { nowMillis() },
) {
    private val accounts = LinkedHashMap<String, UserAccount>().apply { seedAccounts.forEach { put(it.profile.id, it) } }

    private val _state = MutableStateFlow(
        RoomState(
            session = MeetingSession(
                id = Ids.newId("s"), code = Ids.sessionCode(), title = sessionTitle,
                documentId = initialDocument?.id,
            ),
            users = accounts.values.map { it.profile },
            document = initialDocument,
            attendance = seedAttendance,
        ),
    )
    val state: StateFlow<RoomState> = _state.asStateFlow()

    private val _notices = MutableSharedFlow<ServerEvent.Notice>(extraBufferCapacity = 128)
    val notices: SharedFlow<ServerEvent.Notice> = _notices.asSharedFlow()

    fun authenticate(username: String, pin: String): UserProfile? =
        accounts.values.firstOrNull { it.profile.username.equals(username.trim(), ignoreCase = true) && it.pin == pin.trim() }?.profile

    fun profile(id: String): UserProfile? = accounts[id]?.profile

    // ───────────────────────────── helpers ─────────────────────────────

    private inline fun mutate(block: (RoomState) -> RoomState) {
        val next = block(_state.value)
        _state.value = next.copy(revision = next.revision + 1, users = accounts.values.map { it.profile })
    }

    private fun audit(s: RoomState, type: AuditType, actor: UserProfile, detail: String, targetId: String? = null): RoomState =
        s.copy(
            auditLog = (listOf(AuditEntry(Ids.newId("a"), type, actor.id, actor.name, detail, clock(), targetId)) + s.auditLog).take(500),
        )

    private fun system(s: RoomState, text: String): RoomState = s.copy(
        messages = (s.messages + ChatMessage(
            id = Ids.newId("m"), kind = MessageKind.SYSTEM, senderId = "system", senderName = "Sistem",
            senderRole = UserRole.LEADER, senderOrg = "", text = text, createdAt = clock(),
        )).takeLast(1000),
    )

    private fun notify(level: NoticeLevel, message: String, userId: String? = null, role: UserRole? = null) {
        _notices.tryEmit(ServerEvent.Notice(level, message, userId, role))
    }

    private fun upsertPresence(s: RoomState, p: Presence): RoomState =
        s.copy(presence = s.presence.filterNot { it.userId == p.userId } + p)

    private fun derivePresence(s: RoomState, now: Long): RoomState = s.copy(
        presence = s.presence.map { p ->
            if (p.status == PresenceStatus.OFFLINE) p
            else p.copy(status = if (s.activeGrantFor(p.userId, now) != null) PresenceStatus.ON_LEAVE else PresenceStatus.ONLINE)
        },
    )

    // ───────────────────────────── command handling ─────────────────────────────

    /** Memproses perintah dari [actorId]. Mengembalikan pesan galat, atau null bila sukses. */
    fun handle(actorId: String, cmd: ClientCommand): String? {
        val actor = accounts[actorId]?.profile ?: return "Akun tidak dikenal."
        val now = clock()
        val s = _state.value
        val session = s.session
        return when (cmd) {
            is ClientCommand.Hello -> null

            is ClientCommand.JoinRoom -> {
                if (!actor.isLeader && !actor.roomAccess) return "Anda belum diberi hak masuk ke ruang rapat ini. Hubungi Sekretaris."
                val existing = s.presenceOf(actor.id)
                mutate { st ->
                    var next = upsertPresence(
                        st,
                        Presence(
                            userId = actor.id, device = cmd.device, status = PresenceStatus.ONLINE,
                            lockState = existing?.lockState ?: DeviceLockState.UNLOCKED,
                            joinedAt = existing?.joinedAt ?: now, lastSeen = now,
                        ),
                    )
                    if (existing == null || existing.status == PresenceStatus.OFFLINE) {
                        next = audit(next, AuditType.USER_JOINED, actor, "${actor.name} (${actor.orgLabel}) bergabung via ${cmd.device.label}")
                    }
                    derivePresence(next, now)
                }
                null
            }

            is ClientCommand.LeaveRoom -> {
                val p = s.presenceOf(actor.id) ?: return null
                mutate { st ->
                    audit(
                        upsertPresence(st, p.copy(status = PresenceStatus.OFFLINE, lastSeen = now)),
                        AuditType.USER_LEFT, actor, "${actor.name} keluar dari ruang rapat",
                    )
                }
                null
            }

            is ClientCommand.Heartbeat -> {
                val p = s.presenceOf(actor.id) ?: return null
                if (p.lockState == cmd.lockState && now - p.lastSeen < 10_000) return null
                mutate { st -> derivePresence(upsertPresence(st, p.copy(lockState = cmd.lockState, lastSeen = now)), now) }
                null
            }

            is ClientCommand.UploadDocument -> {
                if (!Permissions.canUpload(actor)) return "Hanya Ketua Umum atau Sekretaris yang dapat mengunggah laporan."
                if (session.status == SessionStatus.LIVE) return "Jeda rapat terlebih dahulu sebelum mengganti dokumen."
                mutate { st ->
                    val doc = cmd.document.copy(uploadedById = actor.id, uploadedByName = actor.name)
                    system(
                        audit(
                            st.copy(document = doc, session = st.session.copy(documentId = doc.id, leaderPage = 1)),
                            AuditType.DOCUMENT_UPLOADED, actor, "${doc.fileName} (${doc.pageCount} halaman)",
                        ),
                        "📄 ${actor.name} melampirkan dokumen \"${doc.title}\" (${doc.pageCount} halaman).",
                    )
                }
                notify(NoticeLevel.INFO, "Dokumen baru: ${cmd.document.title}")
                null
            }

            is ClientCommand.PrepareNewSession -> {
                if (!actor.isLeader) return "Hanya pimpinan rapat."
                if (session.status == SessionStatus.LIVE || session.status == SessionStatus.BREAK) return "Akhiri rapat yang sedang berjalan terlebih dahulu."
                mutate { st ->
                    st.copy(
                        session = MeetingSession(
                            id = Ids.newId("s"), code = Ids.sessionCode(), title = cmd.title.ifBlank { st.session.title },
                            documentId = st.document?.id,
                        ),
                        messages = emptyList(),
                        unlockRequests = emptyList(),
                        presence = st.presence.filter { it.status != PresenceStatus.OFFLINE },
                    )
                }
                null
            }

            is ClientCommand.StartMeeting -> {
                if (!Permissions.canStartOrPause(actor)) return "Hanya pimpinan rapat yang dapat memulai rapat."
                if (s.document == null) return "Lampirkan dokumen laporan terlebih dahulu."
                if (session.status == SessionStatus.LIVE) return null
                if (session.status == SessionStatus.ENDED) return "Rapat sudah diakhiri. Siapkan sesi baru."
                val resumed = session.status == SessionStatus.BREAK
                mutate { st ->
                    system(
                        audit(
                            st.copy(
                                session = st.session.copy(
                                    status = SessionStatus.LIVE, startedAt = st.session.startedAt ?: now,
                                    startedBy = st.session.startedBy ?: actor.id, breakUntil = null,
                                ),
                            ),
                            if (resumed) AuditType.SESSION_RESUMED else AuditType.SESSION_STARTED, actor,
                            if (resumed) "Rapat dilanjutkan" else "Rapat dimulai — Mode Fokus aktif untuk seluruh peserta",
                        ),
                        if (resumed) "▶️ Rapat dilanjutkan oleh ${actor.name}. Mode Fokus kembali aktif."
                        else "🔒 Rapat dimulai oleh ${actor.name} (${actor.jabatan}). Mode Fokus aktif.",
                    )
                }
                notify(NoticeLevel.WARNING, if (resumed) "Rapat dilanjutkan — Mode Fokus aktif kembali" else "Rapat dimulai — Mode Fokus aktif")
                null
            }

            is ClientCommand.PauseMeeting -> {
                if (!Permissions.canStartOrPause(actor)) return "Hanya pimpinan rapat."
                if (session.status != SessionStatus.LIVE) return "Rapat belum berlangsung."
                val minutes = cmd.minutes.coerceIn(1, 60)
                mutate { st ->
                    system(
                        audit(
                            st.copy(session = st.session.copy(status = SessionStatus.BREAK, breakUntil = now + minutes * 60_000L)),
                            AuditType.SESSION_PAUSED, actor, "Istirahat $minutes menit — kunci dibuka massal",
                        ),
                        "☕ Istirahat $minutes menit. Kunci perangkat peserta dibuka sementara.",
                    )
                }
                notify(NoticeLevel.INFO, "Istirahat $minutes menit — kunci dibuka sementara")
                null
            }

            is ClientCommand.ResumeMeeting -> handle(actorId, ClientCommand.StartMeeting)

            is ClientCommand.EndMeeting -> {
                if (!Permissions.canEnd(actor)) return "Hanya Ketua Umum yang dapat mengakhiri rapat."
                if (session.status == SessionStatus.ENDED || session.status == SessionStatus.PREPARING) return "Rapat belum berlangsung."
                mutate { st ->
                    val records = st.participants.map { u ->
                        val p = st.presenceOf(u.id)
                        val hadLeave = st.unlockRequests.any { it.userId == u.id && it.status in setOf(UnlockStatus.APPROVED, UnlockStatus.EXPIRED, UnlockStatus.REVOKED) }
                        AttendanceRecord(
                            st.session.id, st.session.title, u.id, now,
                            when {
                                p == null -> "Tidak hadir"
                                hadLeave -> "Hadir (izin keluar)"
                                else -> "Hadir"
                            },
                        )
                    }
                    system(
                        audit(
                            st.copy(
                                session = st.session.copy(status = SessionStatus.ENDED, endedAt = now, breakUntil = null),
                                unlockRequests = st.unlockRequests.map {
                                    when (it.status) {
                                        UnlockStatus.APPROVED -> it.copy(status = UnlockStatus.EXPIRED, expiresAt = now)
                                        UnlockStatus.PENDING -> it.copy(status = UnlockStatus.CANCELLED)
                                        else -> it
                                    }
                                },
                                attendance = st.attendance + records,
                            ),
                            AuditType.SESSION_ENDED, actor, "Rapat diakhiri — seluruh kunci dibuka",
                        ),
                        "✅ Rapat diakhiri oleh ${actor.name}. Rekap revisi tersedia di halaman Profil.",
                    )
                }
                notify(NoticeLevel.SUCCESS, "Rapat telah diakhiri. Terima kasih atas partisipasinya!")
                null
            }

            is ClientCommand.ChangePage -> {
                if (!Permissions.canNavigate(actor)) return "Hanya Ketua Umum / Ketua 1 yang memimpin navigasi halaman."
                val doc = s.document ?: return null
                val page = cmd.page.coerceIn(1, doc.pageCount)
                if (page == session.leaderPage) return null
                mutate { st -> st.copy(session = st.session.copy(leaderPage = page)) }
                null
            }

            is ClientCommand.SendChat -> {
                val text = cmd.text.trim().take(1000)
                if (text.isEmpty()) return "Pesan kosong."
                mutate { st ->
                    st.copy(
                        messages = (st.messages + ChatMessage(
                            id = Ids.newId("m"), kind = MessageKind.TEXT, senderId = actor.id, senderName = actor.name,
                            senderRole = actor.role, senderOrg = actor.orgLabel, text = text, replyToId = cmd.replyToId, createdAt = now,
                        )).takeLast(1000),
                    )
                }
                null
            }

            is ClientCommand.SendHighlight -> {
                val doc = s.document ?: return "Belum ada dokumen."
                val page = doc.pages.getOrNull(cmd.anchor.page - 1) ?: return "Halaman tidak valid."
                val block = page.blocks.firstOrNull { it.id == cmd.anchor.blockId } ?: return "Blok kalimat tidak ditemukan."
                val start = cmd.anchor.startSentence.coerceIn(0, block.sentences.lastIndex)
                val end = cmd.anchor.endSentence.coerceIn(start, block.sentences.lastIndex)
                val quote = block.sentences.subList(start, end + 1).joinToString(" ").take(600)
                mutate { st ->
                    st.copy(
                        messages = (st.messages + ChatMessage(
                            id = Ids.newId("m"), kind = MessageKind.HIGHLIGHT, senderId = actor.id, senderName = actor.name,
                            senderRole = actor.role, senderOrg = actor.orgLabel, text = cmd.comment.trim().take(1000),
                            anchor = cmd.anchor.copy(startSentence = start, endSentence = end, quote = quote), createdAt = now,
                        )).takeLast(1000),
                    )
                }
                null
            }

            is ClientCommand.ToggleRevision -> {
                if (!Permissions.canMarkRevision(actor)) return "Hanya Sekretaris / Ketua Umum yang menandai poin revisi."
                if (s.messages.none { it.id == cmd.messageId }) return "Pesan tidak ditemukan."
                mutate { st ->
                    st.copy(messages = st.messages.map {
                        if (it.id == cmd.messageId) it.copy(
                            revision = cmd.marked,
                            revisionNote = if (cmd.marked) cmd.note?.trim()?.ifBlank { null } else null,
                            revisionMarkedBy = if (cmd.marked) actor.name else null,
                        ) else it
                    })
                }
                null
            }

            is ClientCommand.RequestUnlock -> {
                if (actor.isLeader) return "Pimpinan rapat tidak terkunci."
                if (session.status != SessionStatus.LIVE) return "Mode Fokus sedang tidak aktif — Anda bebas membuka aplikasi lain."
                if (s.unlockRequests.any { it.userId == actor.id && (it.status == UnlockStatus.PENDING || it.isActiveAt(now)) }) {
                    return "Anda masih memiliki permintaan/izin yang aktif."
                }
                val reason = cmd.reason.trim().ifBlank { return "Tuliskan alasan permintaan izin." }.take(300)
                val minutes = cmd.minutes.coerceIn(1, 10)
                mutate { st ->
                    audit(
                        st.copy(
                            unlockRequests = st.unlockRequests + UnlockRequest(
                                id = Ids.newId("r"), userId = actor.id, userName = actor.name, userOrg = actor.orgLabel,
                                reason = reason, requestedMinutes = minutes, createdAt = now,
                            ),
                        ),
                        AuditType.UNLOCK_REQUESTED, actor, "Meminta izin $minutes menit: $reason",
                    )
                }
                notify(NoticeLevel.INFO, "${actor.name} (${actor.orgLabel}) meminta izin $minutes menit", role = UserRole.LEADER)
                null
            }

            is ClientCommand.CancelUnlock -> {
                val req = s.unlockRequests.firstOrNull { it.id == cmd.requestId && it.userId == actor.id } ?: return "Permintaan tidak ditemukan."
                if (req.status != UnlockStatus.PENDING) return null
                mutate { st -> st.copy(unlockRequests = st.unlockRequests.map { if (it.id == req.id) it.copy(status = UnlockStatus.CANCELLED) else it }) }
                null
            }

            is ClientCommand.DecideUnlock -> {
                if (!Permissions.canDecideUnlock(actor)) return "Hanya pimpinan rapat."
                val req = s.unlockRequests.firstOrNull { it.id == cmd.requestId } ?: return "Permintaan tidak ditemukan."
                val isVetoOverride = req.status == UnlockStatus.REJECTED && cmd.approve && Permissions.canVeto(actor)
                if (req.status != UnlockStatus.PENDING && !isVetoOverride) return "Permintaan ini sudah diputuskan oleh ${req.decidedByName ?: "pimpinan lain"}."
                val minutes = cmd.minutes.coerceIn(1, 10)
                val updated = if (cmd.approve) req.copy(
                    status = UnlockStatus.APPROVED, grantedMinutes = minutes, decidedById = actor.id, decidedByName = actor.name,
                    decidedAt = now, expiresAt = now + minutes * 60_000L, note = cmd.note?.trim()?.ifBlank { null },
                ) else req.copy(
                    status = UnlockStatus.REJECTED, decidedById = actor.id, decidedByName = actor.name, decidedAt = now,
                    note = cmd.note?.trim()?.ifBlank { null } ?: "Mohon tetap fokus mengikuti rapat.",
                )
                mutate { st ->
                    derivePresence(
                        audit(
                            st.copy(unlockRequests = st.unlockRequests.map { if (it.id == req.id) updated else it }),
                            if (cmd.approve) AuditType.UNLOCK_APPROVED else AuditType.UNLOCK_REJECTED, actor,
                            if (cmd.approve) "${actor.name}${if (isVetoOverride) " (veto)" else ""} menyetujui izin ${req.userName} selama $minutes menit"
                            else "${actor.name} menolak izin ${req.userName}",
                            targetId = req.userId,
                        ),
                        now,
                    )
                }
                if (cmd.approve) notify(NoticeLevel.SUCCESS, "Izin disetujui ${actor.name}: $minutes menit. Kunci dibuka sementara.", userId = req.userId)
                else notify(NoticeLevel.ERROR, "Izin ditolak oleh ${actor.name}: ${updated.note}", userId = req.userId)
                null
            }

            is ClientCommand.RevokeUnlock -> {
                if (!Permissions.canVeto(actor)) return "Hak veto hanya dimiliki Ketua Umum."
                val req = s.unlockRequests.firstOrNull { it.id == cmd.requestId && it.isActiveAt(now) } ?: return "Izin tidak aktif."
                mutate { st ->
                    derivePresence(
                        audit(
                            st.copy(unlockRequests = st.unlockRequests.map {
                                if (it.id == req.id) it.copy(status = UnlockStatus.REVOKED, expiresAt = now, note = "Dicabut oleh Ketua Umum (veto)") else it
                            }),
                            AuditType.UNLOCK_REVOKED, actor, "Veto: izin ${req.userName} dicabut", targetId = req.userId,
                        ),
                        now,
                    )
                }
                notify(NoticeLevel.ERROR, "Izin Anda dicabut oleh Ketua Umum (veto). Kembali ke rapat.", userId = req.userId)
                null
            }

            is ClientCommand.ReportViolation -> {
                if (actor.isLeader) return null
                mutate { st ->
                    audit(st, cmd.kind.auditType, actor, "${actor.name} (${actor.orgLabel}): ${cmd.kind.label}${if (cmd.detail.isNotBlank()) " — ${cmd.detail}" else ""}", actor.id)
                }
                notify(NoticeLevel.WARNING, "⚠ ${actor.name}: ${cmd.kind.label}", role = UserRole.LEADER)
                null
            }

            is ClientCommand.UpsertAccount -> {
                val err = upsertAccount(actor, cmd.input, now)
                if (err == null) notify(NoticeLevel.SUCCESS, "Akun ${cmd.input.name} tersimpan.", userId = actor.id)
                err
            }

            is ClientCommand.ImportAccounts -> {
                if (!Permissions.canManageAccounts(actor)) return "Hanya Ketua Umum / Sekretaris."
                var ok = 0
                val errors = mutableListOf<String>()
                cmd.inputs.forEach { input ->
                    val existing = accounts.values.firstOrNull { it.profile.username.equals(input.username, true) }
                    val err = upsertAccount(actor, input.copy(id = existing?.profile?.id), now, silent = true)
                    if (err == null) ok++ else errors += "${input.username}: $err"
                }
                mutate { st -> audit(st, AuditType.ACCOUNT_CHANGED, actor, "Impor CSV: $ok akun berhasil, ${errors.size} dilewati") }
                notify(
                    if (errors.isEmpty()) NoticeLevel.SUCCESS else NoticeLevel.WARNING,
                    "Impor selesai: $ok akun berhasil" + if (errors.isNotEmpty()) ", ${errors.size} dilewati (${errors.take(2).joinToString("; ")})" else "",
                    userId = actor.id,
                )
                null
            }

            is ClientCommand.DeleteAccount -> {
                if (!Permissions.canManageAccounts(actor)) return "Hanya Ketua Umum / Sekretaris."
                val target = accounts[cmd.userId]?.profile ?: return "Akun tidak ditemukan."
                if (target.isLeader) return "Akun pimpinan tidak dapat dihapus dari aplikasi."
                accounts.remove(target.id)
                mutate { st ->
                    audit(st.copy(presence = st.presence.filterNot { it.userId == target.id }), AuditType.ACCOUNT_CHANGED, actor, "Akun ${target.name} dihapus")
                }
                null
            }

            is ClientCommand.SetRoomAccess -> {
                if (!Permissions.canManageAccounts(actor)) return "Hanya Ketua Umum / Sekretaris."
                val acc = accounts[cmd.userId] ?: return "Akun tidak ditemukan."
                accounts[acc.profile.id] = acc.copy(profile = acc.profile.copy(roomAccess = cmd.allowed))
                mutate { st ->
                    val presence = if (!cmd.allowed) st.presence.map { if (it.userId == acc.profile.id) it.copy(status = PresenceStatus.OFFLINE) else it } else st.presence
                    audit(st.copy(presence = presence), AuditType.ACCOUNT_CHANGED, actor, "Hak masuk ${acc.profile.name}: ${if (cmd.allowed) "diizinkan" else "dicabut"}")
                }
                if (!cmd.allowed) notify(NoticeLevel.ERROR, "Hak masuk ruang rapat Anda dicabut oleh ${actor.name}.", userId = acc.profile.id)
                null
            }
        }
    }

    private fun upsertAccount(actor: UserProfile, input: AccountInput, now: Long, silent: Boolean = false): String? {
        if (!Permissions.canManageAccounts(actor)) return "Hanya Ketua Umum / Sekretaris."
        val username = input.username.trim().lowercase()
        if (input.name.isBlank()) return "Nama wajib diisi."
        if (!Regex("^[a-z0-9._]{3,24}$").matches(username)) return "Username 3–24 karakter (huruf kecil, angka, titik, garis bawah)."
        if (input.category == OrgCategory.PIMPINAN) return "Akun pimpinan tidak dapat dibuat dari panel ini."
        val existing = input.id?.let { accounts[it] }
        if (accounts.values.any { it.profile.username == username && it.profile.id != existing?.profile?.id }) return "Username sudah dipakai."
        val pin = input.pin.trim()
        if (existing == null && !Regex("^\\d{4,6}$").matches(pin)) return "PIN wajib 4–6 digit angka."
        if (existing != null && pin.isNotEmpty() && !Regex("^\\d{4,6}$").matches(pin)) return "PIN wajib 4–6 digit angka."
        val profile = UserProfile(
            id = existing?.profile?.id ?: Ids.newId("u"), username = username, name = input.name.trim(),
            kelas = input.kelas.trim(), jabatan = input.jabatan.trim().ifBlank { "Peserta Rapat" },
            role = UserRole.PARTICIPANT, category = input.category, unit = input.unit.trim().ifBlank { input.category.shortLabel },
            roomAccess = input.roomAccess,
        )
        accounts[profile.id] = UserAccount(profile, if (pin.isEmpty()) existing!!.pin else pin)
        if (!silent) mutate { st -> audit(st, AuditType.ACCOUNT_CHANGED, actor, "${if (existing == null) "Akun baru" else "Perbarui akun"}: ${profile.name} (${profile.orgLabel})") }
        else mutate { it }
        return null
    }

    /** Dipanggil tiap detik oleh host (Mode Demo / server). */
    fun tick() {
        val now = clock()
        val s = _state.value
        val expired = s.unlockRequests.filter { it.status == UnlockStatus.APPROVED && (it.expiresAt ?: 0) <= now }
        val breakOver = s.session.status == SessionStatus.BREAK && (s.session.breakUntil ?: Long.MAX_VALUE) <= now
        if (expired.isEmpty() && !breakOver) return
        mutate { st ->
            var next = st.copy(unlockRequests = st.unlockRequests.map { r -> if (expired.any { it.id == r.id }) r.copy(status = UnlockStatus.EXPIRED) else r })
            expired.forEach { r ->
                val u = accounts[r.userId]?.profile
                if (u != null) next = audit(next, AuditType.UNLOCK_EXPIRED, u, "Waktu izin ${u.name} habis — kembali terkunci", u.id)
            }
            if (breakOver) {
                next = system(next.copy(session = next.session.copy(status = SessionStatus.LIVE, breakUntil = null)), "⏰ Waktu istirahat selesai. Mode Fokus aktif kembali.")
            }
            derivePresence(next, now)
        }
        expired.forEach { notify(NoticeLevel.WARNING, "Waktu izin habis — perangkat kembali terkunci.", userId = it.userId) }
        if (breakOver) notify(NoticeLevel.WARNING, "Istirahat selesai — Mode Fokus aktif kembali")
    }

    /** Koneksi terputus (server). */
    fun disconnect(userId: String) {
        val p = _state.value.presenceOf(userId) ?: return
        val now = clock()
        mutate { st -> upsertPresence(st, p.copy(status = PresenceStatus.OFFLINE, lastSeen = now)) }
    }
}
