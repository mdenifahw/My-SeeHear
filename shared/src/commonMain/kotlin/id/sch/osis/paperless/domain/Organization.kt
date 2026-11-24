package id.sch.osis.paperless.domain

/** Katalog struktur organisasi OSIS & MPK tingkat SMA. */
object OrgCatalog {
    val sekbidUnits: List<String> = (1..7).map { "Sekbid $it" }

    val sekbidDescriptions: Map<String, String> = mapOf(
        "Sekbid 1" to "Ketakwaan terhadap Tuhan YME",
        "Sekbid 2" to "Budi Pekerti Luhur & Akhlak Mulia",
        "Sekbid 3" to "Kepribadian Unggul & Wawasan Kebangsaan",
        "Sekbid 4" to "Prestasi Akademik, Seni & Olahraga",
        "Sekbid 5" to "Demokrasi, HAM & Pendidikan Politik",
        "Sekbid 6" to "Kreativitas, Keterampilan & Kewirausahaan",
        "Sekbid 7" to "Kesehatan Jasmani & Gizi",
    )

    val fraksiUnits: List<String> = listOf("Fraksi 1", "Fraksi 3")

    val ekskulUnits: List<String> = listOf(
        "Pramuka", "PMR", "Paskibra", "Basket", "Futsal", "Voli",
        "Rohis", "KIR", "Jurnalistik", "Paduan Suara", "Teater", "English Club",
    )

    const val MPK_UNIT = "MPK"
    const val LEADER_UNIT = "Pengurus Inti OSIS"

    fun unitsFor(category: OrgCategory): List<String> = when (category) {
        OrgCategory.SEKBID -> sekbidUnits
        OrgCategory.OSIS_FRAKSI -> fraksiUnits
        OrgCategory.EKSKUL -> ekskulUnits
        OrgCategory.MPK -> listOf(MPK_UNIT)
        OrgCategory.PIMPINAN -> listOf(LEADER_UNIT)
    }

    val participantCategories = listOf(OrgCategory.SEKBID, OrgCategory.OSIS_FRAKSI, OrgCategory.EKSKUL, OrgCategory.MPK)
}

/** Matriks hak akses tiga posisi Pimpinan Rapat. */
object Permissions {
    private fun UserProfile.pos() = leaderPosition

    fun canStartOrPause(u: UserProfile) = u.isLeader
    fun canEnd(u: UserProfile) = u.pos() == LeaderPosition.KETUA_UMUM
    fun canNavigate(u: UserProfile) = u.pos() == LeaderPosition.KETUA_UMUM || u.pos() == LeaderPosition.KETUA_1
    fun canUpload(u: UserProfile) = u.pos() == LeaderPosition.KETUA_UMUM || u.pos() == LeaderPosition.SEKRETARIS
    fun canManageAccounts(u: UserProfile) = u.pos() == LeaderPosition.KETUA_UMUM || u.pos() == LeaderPosition.SEKRETARIS
    fun canDecideUnlock(u: UserProfile) = u.isLeader
    fun canVeto(u: UserProfile) = u.pos() == LeaderPosition.KETUA_UMUM
    fun canMarkRevision(u: UserProfile) = u.pos() == LeaderPosition.SEKRETARIS || u.pos() == LeaderPosition.KETUA_UMUM
    fun canExport(u: UserProfile) = u.pos() == LeaderPosition.SEKRETARIS || u.pos() == LeaderPosition.KETUA_UMUM

    fun describe(u: UserProfile): List<String> = buildList {
        if (!u.isLeader) {
            add("Membaca dokumen & memblok kalimat")
            add("Mengirim tanggapan di Ruang Diskusi")
            add("Mengajukan izin membuka aplikasi lain")
            return@buildList
        }
        add("Memulai & menjeda rapat (mengaktifkan Mode Fokus)")
        if (canEnd(u)) add("Mengakhiri rapat")
        if (canNavigate(u)) add("Memimpin navigasi halaman dokumen")
        add("Memblok kalimat (sorotan pimpinan)")
        add("Menyetujui / menolak izin peserta")
        if (canVeto(u)) add("Hak veto izin (mencabut / membatalkan penolakan)")
        if (canUpload(u)) add("Mengunggah laporan PDF / Word")
        if (canManageAccounts(u)) add("Mengelola akun peserta & impor CSV")
        if (canMarkRevision(u)) add("Menandai poin revisi / notulensi")
        if (canExport(u)) add("Mengekspor notulensi ke PDF")
    }
}
