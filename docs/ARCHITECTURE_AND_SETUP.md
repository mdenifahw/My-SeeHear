# OSISPaperless — Arsitektur, Skema Data, Kontrak API & Panduan Pengujian

## 1. Arsitektur Sistem & Struktur Proyek

Ekosistem **OSISPaperless** menggunakan **Kotlin Multiplatform (KMP) + Compose Multiplatform (Material 3)** untuk berbagi domain model, state engine, protokol WebSocket, dan UI adaptif antar Mobile (Android/iOS) dan Desktop (JVM).

```text
d:\My SeeHear\
├── settings.gradle.kts               # Modul :shared dan :androidApp (siap diperluas ke :desktopApp, :server, :web-portal)
├── gradle/libs.versions.toml         # Version catalog (Kotlin 2.4.21, Compose MP 1.12.2, Ktor 3.6.1, AGP 9.4.1)
├── shared/                           # Shared KMP Module (commonMain, androidMain, desktopMain, iosMain)
│   └── src/
│       ├── commonMain/
│       │   ├── composeResources/font/       # Plus Jakarta Sans (Regular..ExtraBold)
│       │   └── kotlin/id/sch/osis/paperless/
│       │       ├── core/Platform.kt         # expect/actual waktu, format tanggal, tipe perangkat
│       │       ├── domain/                  # UserProfile, MeetingSession, MeetingDocument, ChatMessage, UnlockRequest, AuditEntry, SampleData
│       │       ├── document/                # DocumentFactory, DocxParser (.docx XML → blok kalimat), PdfTextLayout
│       │       ├── protocol/Protocol.kt     # Kontrak JSON WebSocket (ClientCommand & ServerEvent)
│       │       ├── engine/MeetingEngine.kt  # Mesin state otoritatif (dipakai Mode Demo & Server)
│       │       ├── data/                    # MeetingRepository, LocalMeetingRepository, RemoteMeetingRepository, CsvAccountParser
│       │       ├── platform/                # Kontrak FocusGuard, NotificationFeed, DocumentBridge
│       │       ├── app/AppController.kt     # StateFlow MVI/MVVM Controller + orkestrasi Focus Lock
│       │       └── ui/
│       │           ├── theme/Theme.kt       # Sistem warna #8DB2FA (Modern Academic SMA) + Plus Jakarta Sans
│       │           ├── components/          # OsisCard, OrgBadge, FocusModeBanner, IncomingNotificationBanner
│       │           ├── screens/             # Login + 5 halaman adaptif (Pimpinan & Peserta)
│       │           └── OsisPaperlessApp.kt  # Navigasi adaptif (BottomBar Mobile / NavigationRail Desktop)
│       ├── androidMain/                     # AndroidFocusGuard, OsisNotificationListenerService, AndroidDocumentBridge
│       ├── desktopMain/                     # Platform & Ktor CIO actuals (siap untuk :desktopApp)
│       └── commonTest/                      # Unit test MeetingEngine, DocxParser, CsvAccountParser
└── androidApp/                              # Aplikasi Android (APK)
    └── src/main/
        ├── AndroidManifest.xml              # Lock Task Mode, resizeableActivity="false", NotificationListenerService
        └── java/id/sch/osis/paperless/MainActivity.kt
```

---

## 2. Skema Database (Relasional / PostgreSQL / SQLite)

| Tabel | Kolom Utama | Keterangan |
|---|---|---|
| `users` | `id (PK)`, `username (UNIQUE)`, `pin_hash`, `name`, `kelas`, `jabatan`, `role (LEADER/PARTICIPANT)`, `leader_position`, `category (PIMPINAN/SEKBID/OSIS_FRAKSI/EKSKUL/MPK)`, `unit`, `room_access (BOOL)` | Menyimpan 3 akun Pimpinan & seluruh akun Peserta. |
| `meeting_sessions` | `id (PK)`, `code (UNIQUE 6-char)`, `title`, `status (PREPARING/LIVE/BREAK/ENDED)`, `document_id (FK)`, `leader_page`, `started_at`, `ended_at`, `break_until`, `started_by (FK)` | Kode sesi juga digunakan untuk memvalidasi Dynamic QR Download di Web Portal. |
| `documents` | `id (PK)`, `session_id (FK)`, `title`, `file_name`, `format (PDF/DOCX/DOC)`, `pages_json (JSONB)`, `source_key`, `uploaded_by (FK)`, `uploaded_at` | `pages_json` berisi blok kalimat terstandarisasi agar koordinat highlight identik di semua perangkat. |
| `highlight_messages` | `id (PK)`, `session_id (FK)`, `kind (TEXT/HIGHLIGHT/SYSTEM)`, `sender_id (FK)`, `text`, `anchor_page`, `anchor_block_id`, `anchor_start`, `anchor_end`, `anchor_quote`, `reply_to_id`, `revision (BOOL)`, `revision_note`, `revision_marked_by`, `created_at` | Menyatukan chat diskusi, kutipan sorotan dokumen, dan penandaan poin revisi notulensi. |
| `unlock_requests` | `id (PK)`, `session_id (FK)`, `user_id (FK)`, `reason`, `requested_minutes`, `status (PENDING/APPROVED/REJECTED/EXPIRED/REVOKED/CANCELLED)`, `granted_minutes`, `decided_by (FK)`, `note`, `created_at`, `decided_at`, `expires_at` | Mengelola antrean izin sementara, hitung mundur, dan hak veto Ketua Umum. |
| `attendance_records` | `id (PK)`, `session_id (FK)`, `user_id (FK)`, `device`, `joined_at`, `last_seen`, `status` | Rekap kehadiran peserta per sesi rapat. |
| `audit_logs` | `id (PK)`, `session_id (FK)`, `type`, `actor_id (FK)`, `target_id`, `detail`, `created_at` | Mencatat percobaan keluar paksa (`HOME_OR_RECENTS`, `APP_BACKGROUNDED`, `BACK_PRESSED`, `PIN_REMOVED`) dan riwayat izin. |

---

## 3. Kontrak WebSocket (`ws://<host>:8080/ws`)

Format pesan JSON menggunakan diskriminator `"type"`:

### Client → Server (`ClientCommand`)
- `hello`: `{ "type": "hello", "username": "ketum", "pin": "1234", "device": "ANDROID" }`
- `join_room` / `leave_room` / `heartbeat`: sinkronisasi kehadiran & `lockState` (`LOCKED`, `TEMP_UNLOCKED`, `UNLOCKED`).
- `upload_document`: melampirkan `MeetingDocument` hasil konversi PDF/DOCX.
- `start_meeting` / `pause_meeting` (`minutes`) / `resume_meeting` / `end_meeting` / `prepare_session`.
- `change_page` (`page`): sinkronisasi halaman *Follow Leader*.
- `send_highlight` (`anchor`, `comment`) & `send_chat` (`text`, `replyToId`) & `toggle_revision` (`messageId`, `marked`, `note`).
- `request_unlock` (`reason`, `minutes`), `cancel_unlock`, `decide_unlock` (`requestId`, `approve`, `minutes`, `note`), `revoke_unlock` (veto Ketua Umum).
- `report_violation` (`kind`, `detail`): laporan otomatis dari `FocusGuard`.
- `upsert_account`, `delete_account`, `import_accounts`, `set_room_access`.

### Server → Client (`ServerEvent`)
- `auth_ok` (`user`) / `auth_failed` (`reason`).
- `snapshot` (`state: RoomState`): state otoritatif ruang rapat.
- `document_published` (`document: MeetingDocument`).
- `notice` (`level`, `message`, `targetUserId`, `targetRole`).

---

## 4. Implementasi Focus Lock & Notification Listener (Android)

1. **Lock Task Mode / Screen Pinning (`AndroidFocusGuard.kt`):**
   - Saat Pimpinan menekan **Mulai Rapat**, `AppController` memanggil `focusGuard.engageLock()` pada perangkat Peserta.
   - `MainActivity` mengaktifkan `startLockTask()`, `FLAG_KEEP_SCREEN_ON`, menonaktifkan split-screen (`android:resizeableActivity="false"`), dan mencegat tombol Back lewat `OnBackPressedCallback`.
   - Untuk mode sekolah tanpa dialog konfirmasi pin (kiosk penuh), perangkat dapat didaftarkan sebagai Device Owner via ADB/MDM; tanpa Device Owner, Android otomatis menggunakan **Screen Pinning** bawaan dan mencatat ke Audit Log bila peserta melepas pin atau berpindah ke latar belakang (`onUserLeaveHint` / `onStop`).
2. **Notifikasi Tetap Masuk Tapi Tidak Bisa Dibuka (`OsisNotificationListenerService.kt`):**
   - `OsisNotificationListenerService` menangkap notifikasi aplikasi lain dan hanya meneruskan teks aman (`appName`, `title`, `body`) ke `SafeNotificationHub` — `PendingIntent` tidak pernah disimpan/dipicu sehingga klik tidak dapat memindahkan pengguna keluar dari ruang rapat.
   - Pratinjau tampil sebagai banner di atas layar dan tersimpan di **Halaman 3 (Pusat Permintaan Izin)** pada panel **Notifikasi Masuk (Pratinjau Aman)**.

---

## 5. Panduan Build & Pengujian (Android)

1. **Build APK Debug & Jalankan Unit Test:**
   ```powershell
   .\gradlew.bat :androidApp:assembleDebug :shared:desktopTest
   ```
   Output APK: `androidApp\build\outputs\apk\debug\androidApp-debug.apk`.
2. **Instalasi ke Perangkat / Emulator Android:**
   ```powershell
   adb install -r androidApp\build\outputs\apk\debug\androidApp-debug.apk
   ```
3. **Pengujian Cepat (1 Perangkat — Mode Demo):**
   - Pilih **Mode Demo** di layar Login, lalu ketuk pintasan **Ketua Umum (Pimpinan)** atau **Sekbid 4 (Peserta)** (PIN default: `1234`).
   - Sebagai **Pimpinan**: unggah file `.docx`/`.pdf` atau pakai dokumen contoh → tekan **Mulai Rapat** → coba sorot kalimat, terima/tolak antrean izin simulasi, tandai **Poin Revisi**, lalu klik **Ekspor Revisi** untuk menghasilkan file PDF notulensi.
   - Sebagai **Peserta**: tunggu ±8 detik hingga rapat dimulai → perangkat masuk Mode Fokus → buka tab **Minta Izin** untuk mengajukan izin (1–5 mnt) atau tekan **Simulasi Notifikasi** untuk melihat pratinjau notifikasi aman.
4. **Aktifkan Akses Notifikasi Asli (Opsional di Perangkat Fisik):**
   - Di tab **Minta Izin** → ketuk **Aktifkan Akses** → centang *OSISPaperless — Pratinjau Notifikasi Fokus*.
