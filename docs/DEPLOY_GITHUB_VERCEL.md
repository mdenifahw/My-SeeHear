# Panduan Deploy My SeeHear ke GitHub & Vercel (Unduhan Eksklusif QR Code)

Dengan alur ini:
1. **Seluruh kode** (Aplikasi Android KMP + Website Portal QR) tersimpan rapi di **GitHub**.
2. **GitHub Actions** otomatis membangun file **`MySeeHear-Android.apk`** dan mengunggahnya ke **GitHub Releases** setiap kali Anda membuat rilis (`v1.0.0`, dst.).
3. **Vercel** menayangkan **Website Portal QR Code** (`web-portal/`) yang menghasilkan **Dynamic QR Code** bertanda tangan HMAC-SHA256 (masa aktif 5 menit) dan menyalurkan APK secara aman hanya setelah QR dipindai.

---

## Langkah 1 — Upload Proyek ke GitHub

Repositori Git lokal di `d:\My SeeHear` sudah diinisialisasi dan di-commit.

1. Buka [github.com/new](https://github.com/new) dan buat repositori baru, misalnya bernama **`my-seehear`** (boleh **Public** atau **Private**; **jangan** centang *Add a README*).
2. Jalankan perintah berikut di PowerShell (ganti `<USERNAME>` dengan username GitHub Anda):
   ```powershell
   cd "d:\My SeeHear"
   git remote add origin https://github.com/<USERNAME>/my-seehear.git
   git push -u origin main
   ```

---

## Langkah 2 — Terbitkan APK ke GitHub Releases

Pilih salah satu cara berikut:

### Cara A (Otomatis via GitHub Actions — Direkomendasikan)
Push tag versi pertama agar GitHub Actions otomatis mem-build dan merilis `MySeeHear-Android.apk`:
```powershell
git tag v1.0.0
git push origin v1.0.0
```
Tunggu ±3–5 menit di tab **Actions** GitHub. Setelah selesai, `MySeeHear-Android.apk` otomatis muncul di menu **Releases**.

### Cara B (Unggah APK yang Sudah Jadi Sekarang Juga)
APK debug sudah tersedia lokal di `androidApp\build\outputs\apk\debug\androidApp-debug.apk`:
1. Di halaman GitHub repo Anda → klik **Releases** → **Create a new release**.
2. Isi tag `v1.0.0`, judul `My SeeHear v1.0.0`.
3. Unggah file `androidApp-debug.apk` (ganti nama menjadi `MySeeHear-Android.apk` agar rapi) → klik **Publish release**.

---

## Langkah 3 — Deploy Website Portal QR ke Vercel

1. Buka [vercel.com/new](https://vercel.com/new) dan masuk menggunakan akun **GitHub** Anda.
2. Pilih repositori **`my-seehear`** → klik **Import**.
3. Pada bagian **Root Directory**, klik **Edit** dan pilih folder:
   ```text
   web-portal
   ```
4. Buka bagian **Environment Variables** dan tambahkan variabel berikut:
   | Key | Value (Contoh) | Keterangan |
   |---|---|---|
   | `GITHUB_REPO` | `<USERNAME>/my-seehear` | Repositori GitHub tempat GitHub Releases berada |
   | `QR_TOKEN_SECRET` | `ganti-dengan-teks-acak-panjang-32-karakter` | Kunci rahasia HMAC-SHA256 untuk tanda tangan token QR |
   | `MEETING_ACCESS_CODES` | `OSIS26,RAPAT1` | Kode Sesi Rapat yang diizinkan membuka QR (pisahkan koma) |
   | `GITHUB_TOKEN` | *(opsional — wajib hanya jika repo GitHub Private)* | Fine-grained Personal Access Token (izin `Contents: Read-only`) |
5. Klik **Deploy**.

---

## Langkah 4 — Instal Aplikasi di HP via Scan QR Code

1. Buka URL Vercel Anda (contoh: `https://my-seehear.vercel.app`) di laptop/proyektor ruang rapat.
2. Masukkan **Kode Sesi Rapat** (misalnya `OSIS26`) → klik **Tampilkan QR Code**.
3. Pindai QR Code menggunakan kamera HP Android Anda.
4. HP otomatis mengunduh **`MySeeHear-Android.apk`** → ketuk file → izinkan *Instal aplikasi dari sumber ini* → aplikasi **My SeeHear** siap digunakan!
