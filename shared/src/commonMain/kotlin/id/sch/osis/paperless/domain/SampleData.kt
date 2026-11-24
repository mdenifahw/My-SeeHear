package id.sch.osis.paperless.domain

import id.sch.osis.paperless.core.nowMillis
import id.sch.osis.paperless.document.DocumentFactory

/** Data awal untuk Mode Demo & seed server pengembangan. PIN default semua akun: 1234. */
object SampleData {
    const val DEFAULT_PIN = "1234"

    private fun leader(id: String, username: String, name: String, kelas: String, pos: LeaderPosition) = UserAccount(
        UserProfile(
            id = id, username = username, name = name, kelas = kelas, jabatan = pos.label,
            role = UserRole.LEADER, leaderPosition = pos, category = OrgCategory.PIMPINAN, unit = OrgCatalog.LEADER_UNIT,
        ),
        DEFAULT_PIN,
    )

    private fun participant(id: String, username: String, name: String, kelas: String, jabatan: String, cat: OrgCategory, unit: String) =
        UserAccount(
            UserProfile(
                id = id, username = username, name = name, kelas = kelas, jabatan = jabatan,
                role = UserRole.PARTICIPANT, category = cat, unit = unit,
            ),
            DEFAULT_PIN,
        )

    private val sekbidNames = listOf(
        "Ahmad Fauzan Hakim", "Bunga Citra Lestari", "Dimas Arya Saputra", "Keisha Amanda Putri",
        "Muhammad Rizky Ramadhan", "Zahra Aulia Nabila", "Fikri Haikal Akbar",
    )
    private val ekskulNames = listOf(
        "Galih Prasetyo", "Intan Permatasari", "Bagas Wicaksono", "Rafi Ardiansyah", "Yoga Pratama", "Citra Kirana",
        "Hafidz Al Farisi", "Nayla Khairunnisa", "Alya Rahmawati", "Shafa Azzahra", "Reza Mahendra", "Clarissa Wijaya",
    )

    fun accounts(): List<UserAccount> = buildList {
        add(leader("u_ketum", "ketum", "Raka Aditya Pratama", "XI-1", LeaderPosition.KETUA_UMUM))
        add(leader("u_ketua1", "ketua1", "Nadia Putri Maharani", "XI-3", LeaderPosition.KETUA_1))
        add(leader("u_sekretaris", "sekretaris", "Salsabila Azzahra", "XI-2", LeaderPosition.SEKRETARIS))
        sekbidNames.forEachIndexed { i, name ->
            val n = i + 1
            add(participant("u_sekbid$n", "sekbid$n", name, if (n % 2 == 0) "X-${n}" else "XI-${n}", "Koordinator Sekbid $n", OrgCategory.SEKBID, "Sekbid $n"))
        }
        add(participant("u_f1a", "fraksi1a", "Daffa Maulana Ishak", "X-2", "Anggota OSIS Fraksi 1", OrgCategory.OSIS_FRAKSI, "Fraksi 1"))
        add(participant("u_f1b", "fraksi1b", "Aurelia Nadine", "X-4", "Anggota OSIS Fraksi 1", OrgCategory.OSIS_FRAKSI, "Fraksi 1"))
        add(participant("u_f1c", "fraksi1c", "Rangga Putra Wijaya", "X-6", "Anggota OSIS Fraksi 1", OrgCategory.OSIS_FRAKSI, "Fraksi 1"))
        add(participant("u_f3a", "fraksi3a", "Kevin Sanjaya", "XI-5", "Anggota OSIS Fraksi 3", OrgCategory.OSIS_FRAKSI, "Fraksi 3"))
        add(participant("u_f3b", "fraksi3b", "Tiara Anindya", "XI-4", "Anggota OSIS Fraksi 3", OrgCategory.OSIS_FRAKSI, "Fraksi 3"))
        OrgCatalog.ekskulUnits.forEachIndexed { i, unit ->
            val username = unit.lowercase().replace(" ", "")
            add(participant("u_ek_$username", username, ekskulNames[i], listOf("X-1", "XI-2", "X-3", "XI-4")[i % 4], "Ketua Ekskul $unit", OrgCategory.EKSKUL, unit))
        }
        add(participant("u_mpk1", "mpk1", "Hana Safitri", "XI-6", "Ketua MPK", OrgCategory.MPK, OrgCatalog.MPK_UNIT))
        add(participant("u_mpk2", "mpk2", "Iqbal Ramadhan", "XI-1", "Wakil Ketua MPK", OrgCategory.MPK, OrgCatalog.MPK_UNIT))
        add(participant("u_mpk3", "mpk3", "Putri Ayu Lestari", "X-5", "Anggota MPK Komisi A", OrgCategory.MPK, OrgCatalog.MPK_UNIT))
        add(participant("u_mpk4", "mpk4", "Wahyu Nugroho", "X-3", "Anggota MPK Komisi B", OrgCategory.MPK, OrgCatalog.MPK_UNIT))
    }

    fun attendanceHistory(): List<AttendanceRecord> {
        val day = 86_400_000L
        val now = nowMillis()
        val past = listOf(
            Triple("s_hist1", "Rapat Pleno Program Kerja Semester Ganjil", now - 42 * day),
            Triple("s_hist2", "Evaluasi MPLS & Penyambutan Siswa Baru", now - 28 * day),
            Triple("s_hist3", "Persiapan Peringatan HUT RI ke-81", now - 14 * day),
        )
        return accounts().filter { !it.profile.isLeader }.flatMapIndexed { idx, acc ->
            past.mapIndexed { j, (sid, title, date) ->
                AttendanceRecord(sid, title, acc.profile.id, date, if ((idx + j) % 7 == 3) "Izin" else "Hadir")
            }
        }
    }

    fun sampleDocument(): MeetingDocument = DocumentFactory.build(
        id = "doc_sample_lpj",
        title = "LPJ Sekbid 4 — PORSENI 2026",
        fileName = "LPJ_Sekbid4_PORSENI_2026.docx",
        format = DocumentFormat.SAMPLE,
        pages = DocumentFactory.parseMarkup(SAMPLE_MARKUP),
        uploaderId = "u_sekretaris",
        uploaderName = "Salsabila Azzahra",
        uploadedAt = nowMillis(),
        conversionNote = "Dokumen contoh (Mode Demo) — telah dikonversi ke format kanvas terstandarisasi.",
    )

    private val SAMPLE_MARKUP = """
# Laporan Pertanggungjawaban Pekan Olahraga dan Seni (PORSENI) 2026
Disusun oleh Seksi Bidang 4 — Prestasi Akademik, Seni dan Olahraga. OSIS SMA Negeri Harapan Bangsa periode 2026/2027.
## Bab I — Pendahuluan
PORSENI merupakan program kerja tahunan Sekbid 4 yang bertujuan menyalurkan minat dan bakat siswa di bidang olahraga dan seni. Kegiatan ini juga menjadi sarana mempererat kebersamaan antarkelas setelah pelaksanaan Penilaian Akhir Semester. Tahun ini PORSENI mengusung tema "Satu Semangat, Seribu Prestasi".
Laporan ini disusun sebagai bentuk pertanggungjawaban panitia kepada Pengurus Inti OSIS, MPK, dan pembina kesiswaan. Seluruh data di dalam laporan diambil dari notulensi rapat panitia, dokumentasi lapangan, dan rekap keuangan bendahara.
## Tujuan Kegiatan
- Menumbuhkan sportivitas dan kerja sama tim antarsiswa.
- Menjaring bibit atlet dan seniman sekolah untuk lomba tingkat kota.
- Memberikan ruang apresiasi karya seni siswa melalui pentas dan pameran.
- Melatih kepanitiaan anggota OSIS dalam mengelola acara berskala besar.
===
## Bab II — Pelaksanaan Kegiatan
Kegiatan dilaksanakan selama empat hari, yaitu tanggal 8 sampai 11 Desember 2026, bertempat di lapangan utama, aula, dan GOR sekolah. Upacara pembukaan dipimpin langsung oleh Kepala Sekolah dan dihadiri oleh seluruh warga sekolah.
Jumlah peserta mencapai 864 siswa dari 24 kelas. Cabang lomba yang dipertandingkan terdiri atas enam cabang olahraga dan empat cabang seni.
## Cabang Lomba
| Cabang | Kategori | Jumlah Tim | Lokasi
| Futsal | Olahraga | 24 | Lapangan Utama
| Basket 3x3 | Olahraga | 18 | GOR Sekolah
| Voli | Olahraga | 20 | Lapangan Samping
| Tarik Tambang | Olahraga | 24 | Lapangan Utama
| Band Akustik | Seni | 12 | Aula
| Poster Digital | Seni | 30 | Lab Komputer
Pada hari kedua terjadi keterlambatan jadwal sekitar 45 menit akibat hujan deras. Panitia memindahkan dua pertandingan voli ke GOR agar jadwal final tidak terganggu.
===
## Bab III — Laporan Keuangan
Sumber dana kegiatan berasal dari anggaran OSIS, iuran pendaftaran tim, dan sponsor dari dua mitra usaha lokal. Seluruh transaksi dicatat oleh bendahara panitia dan diverifikasi oleh MPK Komisi B.
| Uraian | Pemasukan | Pengeluaran
| Anggaran OSIS | Rp4.500.000 | -
| Iuran pendaftaran tim | Rp3.240.000 | -
| Sponsor mitra lokal | Rp2.000.000 | -
| Trofi, medali & piagam | - | Rp3.850.000
| Konsumsi panitia & juri | - | Rp2.400.000
| Sewa sound system | - | Rp1.750.000
| Dekorasi & dokumentasi | - | Rp1.120.000
| Total | Rp9.740.000 | Rp9.120.000
Sisa dana sebesar Rp620.000 telah diserahkan kepada bendahara umum OSIS pada tanggal 14 Desember 2026. Bukti transaksi dilampirkan pada lampiran laporan ini.
===
## Bab IV — Evaluasi Kegiatan
Secara umum kegiatan berjalan lancar dan mendapat respons positif dari siswa. Berdasarkan survei kepuasan, 87 persen responden menyatakan puas terhadap penyelenggaraan PORSENI tahun ini.
Kendala utama yang dihadapi adalah kurangnya koordinasi antara divisi acara dan divisi perlengkapan. Akibatnya, beberapa perlengkapan pertandingan terlambat disiapkan pada hari pertama.
Selain itu, publikasi jadwal pertandingan hanya dilakukan melalui grup kelas sehingga sebagian siswa tidak mendapatkan informasi terbaru. Panitia juga mencatat adanya keluhan mengenai antrean konsumsi yang terlalu panjang.
## Rekomendasi
- Membuat rapat koordinasi lintas divisi minimal dua kali seminggu sebelum acara.
- Menyediakan papan jadwal digital dan publikasi melalui akun media sosial resmi OSIS.
- Menambah jumlah petugas konsumsi dan membagi jalur antrean berdasarkan angkatan.
- Menyiapkan rencana cadangan lokasi pertandingan apabila cuaca tidak mendukung.
===
## Bab V — Penutup
Demikian laporan pertanggungjawaban ini kami susun dengan sebenar-benarnya. Kami mengucapkan terima kasih kepada Kepala Sekolah, pembina OSIS, MPK, serta seluruh panitia yang telah bekerja keras menyukseskan PORSENI 2026.
Kritik dan saran dari peserta rapat sangat kami harapkan sebagai bahan perbaikan program kerja di periode berikutnya. Semoga laporan ini dapat diterima dan menjadi arsip yang bermanfaat bagi kepengurusan OSIS selanjutnya.
## Pengesahan
- Ketua Panitia: Keisha Amanda Putri (Koordinator Sekbid 4).
- Sekretaris Panitia: Aurelia Nadine.
- Bendahara Panitia: Tiara Anindya.
- Mengetahui: Raka Aditya Pratama, Ketua Umum OSIS.
""".trimIndent()
}
