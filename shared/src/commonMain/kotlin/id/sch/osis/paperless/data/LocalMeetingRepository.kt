package id.sch.osis.paperless.data

import id.sch.osis.paperless.core.DeviceType
import id.sch.osis.paperless.core.nowMillis
import id.sch.osis.paperless.domain.DeviceLockState
import id.sch.osis.paperless.domain.HighlightAnchor
import id.sch.osis.paperless.domain.PresenceStatus
import id.sch.osis.paperless.domain.RoomState
import id.sch.osis.paperless.domain.SampleData
import id.sch.osis.paperless.domain.SessionStatus
import id.sch.osis.paperless.domain.UnlockStatus
import id.sch.osis.paperless.domain.UserProfile
import id.sch.osis.paperless.domain.ViolationKind
import id.sch.osis.paperless.engine.MeetingEngine
import id.sch.osis.paperless.protocol.ClientCommand
import id.sch.osis.paperless.protocol.NoticeLevel
import id.sch.osis.paperless.protocol.ServerEvent
import id.sch.osis.paperless.protocol.isFor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.random.Random

/**
 * Mode Demo: [MeetingEngine] berjalan langsung di perangkat, ditemani "peserta simulasi"
 * sehingga seluruh alur (kunci rapat, izin, sorotan, statistik) dapat diuji dengan 1 perangkat.
 */
class LocalMeetingRepository(private val scope: CoroutineScope) : MeetingRepository {
    private val engine = MeetingEngine(
        seedAccounts = SampleData.accounts(),
        initialDocument = SampleData.sampleDocument(),
        seedAttendance = SampleData.attendanceHistory(),
    )
    private var me: UserProfile? = null
    private val jobs = mutableListOf<Job>()

    private val _room = MutableStateFlow<RoomState?>(null)
    override val room: StateFlow<RoomState?> = _room
    override val connection: StateFlow<ConnectionState> = MutableStateFlow(ConnectionState.DEMO)
    private val _notices = MutableSharedFlow<ServerEvent.Notice>(extraBufferCapacity = 64)
    override val notices: SharedFlow<ServerEvent.Notice> = _notices

    override suspend fun login(username: String, pin: String, device: DeviceType): Result<UserProfile> {
        val user = engine.authenticate(username, pin)
            ?: return Result.failure(IllegalArgumentException("Username atau PIN salah. (Demo: PIN 1234)"))
        if (!user.isLeader && !user.roomAccess) return Result.failure(IllegalStateException("Akun belum memiliki hak masuk ruang rapat."))
        me = user
        jobs += scope.launch { engine.state.collect { _room.value = it } }
        jobs += scope.launch { engine.notices.collect { n -> me?.let { if (n.isFor(it)) _notices.emit(n) } } }
        jobs += scope.launch { while (isActive) { delay(1000); engine.tick() } }
        engine.handle(user.id, ClientCommand.JoinRoom(device))
        jobs += scope.launch { SimulatedParticipants(engine, user).run() }
        return Result.success(user)
    }

    override suspend fun send(command: ClientCommand): String? {
        val user = me ?: return "Belum masuk."
        val error = engine.handle(user.id, command)
        if (error != null) _notices.emit(ServerEvent.Notice(NoticeLevel.ERROR, error, user.id))
        return error
    }

    override suspend fun close() {
        me?.let { engine.handle(it.id, ClientCommand.LeaveRoom) }
        jobs.forEach { it.cancel() }
        jobs.clear()
        me = null
        _room.value = null
    }
}

/** Sutradara peserta & pimpinan simulasi untuk Mode Demo. */
private class SimulatedParticipants(private val engine: MeetingEngine, private val me: UserProfile) {
    private val rnd = Random(nowMillis())
    private val state get() = engine.state.value

    private val questions = listOf(
        "Mohon dijelaskan rincian anggaran pada bagian ini.",
        "Apakah data ini sudah diverifikasi MPK?",
        "Usul agar poin ini dimasukkan dalam rekomendasi.",
        "Kalimat ini sebaiknya diperjelas agar tidak ambigu.",
        "Setuju, ini perlu jadi catatan evaluasi.",
        "Ada bukti dokumentasinya di lampiran?",
    )
    private val reasons = listOf(
        "Menghubungi orang tua untuk penjemputan",
        "Membuka materi pendukung di Google Drive",
        "Cek jadwal ulangan susulan",
        "Keperluan darurat keluarga",
    )

    suspend fun run() {
        val bots = state.users.filter { it.id != me.id }
        val participantBots = bots.filter { !it.isLeader }.shuffled(rnd)
        val leaderBots = bots.filter { it.isLeader }
        val devices = listOf(DeviceType.ANDROID, DeviceType.ANDROID, DeviceType.ANDROID, DeviceType.IOS, DeviceType.DESKTOP)

        // Pimpinan lain langsung hadir, 60% peserta hadir di awal, sisanya menyusul.
        leaderBots.forEach { engine.handle(it.id, ClientCommand.JoinRoom(devices.random(rnd))) }
        val firstWave = (participantBots.size * 0.6).toInt()
        participantBots.take(firstWave).forEach { engine.handle(it.id, ClientCommand.JoinRoom(devices.random(rnd))) }
        val lateComers = participantBots.drop(firstWave).dropLast(3).toMutableList() // 3 orang tidak hadir

        var tick = 0
        while (true) {
            delay(1000)
            tick++
            val s = state
            val now = nowMillis()
            val joinedBots = participantBots.filter { s.presenceOf(it.id)?.status?.let { st -> st != PresenceStatus.OFFLINE } == true }

            if (tick % 7 == 0 && lateComers.isNotEmpty()) {
                engine.handle(lateComers.removeAt(0).id, ClientCommand.JoinRoom(devices.random(rnd)))
            }

            // Bot memperbarui status kunci perangkat.
            if (tick % 3 == 0) joinedBots.forEach { bot ->
                val lock = when {
                    s.activeGrantFor(bot.id, now) != null -> DeviceLockState.TEMP_UNLOCKED
                    s.session.status == SessionStatus.LIVE -> DeviceLockState.LOCKED
                    else -> DeviceLockState.UNLOCKED
                }
                engine.handle(bot.id, ClientCommand.Heartbeat(lock))
            }

            if (me.isLeader) leaderScenario(tick, s, joinedBots) else participantScenario(tick, s, leaderBots)
        }
    }

    private fun leaderScenario(tick: Int, s: RoomState, joinedBots: List<UserProfile>) {
        if (s.session.status != SessionStatus.LIVE || joinedBots.isEmpty()) return
        val now = nowMillis()
        val pending = s.unlockRequests.count { it.status == UnlockStatus.PENDING }
        if (tick % 25 == 0 && pending < 2) {
            val bot = joinedBots.filter { b -> s.unlockRequests.none { it.userId == b.id && (it.status == UnlockStatus.PENDING || it.isActiveAt(now)) } }.randomOrNull(rnd)
            bot?.let { engine.handle(it.id, ClientCommand.RequestUnlock(reasons.random(rnd), listOf(1, 3, 5).random(rnd))) }
        }
        if (tick % 19 == 0) postHighlight(joinedBots.random(rnd), s)
        if (tick % 53 == 0) {
            val bot = joinedBots.random(rnd)
            engine.handle(bot.id, ClientCommand.ReportViolation(listOf(ViolationKind.HOME_OR_RECENTS, ViolationKind.APP_BACKGROUNDED).random(rnd), "Simulasi"))
        }
    }

    private fun participantScenario(tick: Int, s: RoomState, leaderBots: List<UserProfile>) {
        val ketua1 = leaderBots.firstOrNull { it.username == "ketua1" } ?: return
        val now = nowMillis()
        // Pimpinan simulasi memulai rapat 8 detik setelah peserta bergabung.
        if (s.session.status == SessionStatus.PREPARING && tick == 8) engine.handle(ketua1.id, ClientCommand.StartMeeting)
        if (s.session.status != SessionStatus.LIVE) return
        // Pimpinan berpindah halaman setiap 45 detik.
        if (tick % 45 == 0) {
            val count = s.document?.pageCount ?: 1
            engine.handle(ketua1.id, ClientCommand.ChangePage(if (s.session.leaderPage >= count) 1 else s.session.leaderPage + 1))
        }
        // Permintaan izin saya diputuskan otomatis setelah ±5 detik.
        s.unlockRequests.firstOrNull { it.userId == me.id && it.status == UnlockStatus.PENDING && now - it.createdAt > 5_000 }?.let { req ->
            val approve = req.requestedMinutes <= 5
            engine.handle(
                (leaderBots.randomOrNull(rnd) ?: ketua1).id,
                ClientCommand.DecideUnlock(req.id, approve, req.requestedMinutes, if (approve) "Silakan, segera kembali ke rapat." else "Durasi terlalu lama, ajukan maksimal 5 menit."),
            )
        }
        if (tick % 23 == 0) postHighlight((leaderBots + s.participants.filter { it.id != me.id }).random(rnd), s)
    }

    private fun postHighlight(bot: UserProfile, s: RoomState) {
        val doc = s.document ?: return
        val page = doc.pages.getOrNull(s.session.leaderPage - 1) ?: return
        val block = page.blocks.filter { it.sentences.isNotEmpty() }.randomOrNull(rnd) ?: return
        val idx = rnd.nextInt(block.sentences.size)
        engine.handle(
            bot.id,
            ClientCommand.SendHighlight(HighlightAnchor(page.number, block.id, idx, idx, block.sentences[idx]), questions.random(rnd)),
        )
    }
}
