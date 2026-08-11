# NEWS1 Free — Panduan dari Nol Sampai APK Terpasang

## A. Kita sedang membuat apa?
Bayangkan ada tiga benda:

1. **GitHub = lemari + robot gratis.** Lemari menyimpan aplikasi. Robot mengambil data berkala dan membuat APK.
2. **`data/latest.json` = kertas laporan NEWS1 terbaru.** Aplikasi membaca kertas ini lewat internet.
3. **APK = aplikasi yang dipasang di HP Android.**

Versi ini **tidak memakai OpenAI API dan tidak memakai Render**. Tidak ada API key yang perlu dibuat.

> Penting: analisis otomatisnya adalah **rule-based**, bukan AI. Berita otomatis hanya memakai headline/metadata yang dapat diambil secara publik. Isi artikel paywall tidak ditebak.

---

# BAGIAN 1 — BUAT AKUN GITHUB

### 1. Buka GitHub
Di browser laptop/PC, buka GitHub lalu daftar akun jika belum punya.

### 2. Buat repository baru
Setelah login:
- tekan tanda **+** di kanan atas;
- pilih **New repository**;
- Repository name: `news1-free`;
- pilih **Public**;
- tekan **Create repository**.

Kenapa Public? Karena APK gratis ini membaca file laporan melalui alamat `raw.githubusercontent.com`. Jangan memasukkan data pribadi, password, atau secret apa pun ke repository.

---

# BAGIAN 2 — UPLOAD PROJECT

### 3. Ekstrak ZIP NEWS1 Free
Download ZIP yang diberikan ChatGPT, lalu klik kanan -> **Extract All / Ekstrak Semua**.

### 4. Buka folder hasil ekstrak
Di dalamnya harus terlihat kira-kira:

```text
.github/
android/
data/
scripts/
manual/
START_HERE.txt
PANDUAN_BAHASA_BAYI.md
README.md
```

### 5. Upload ke GitHub
Di repository `news1-free`:
- klik **Add file**;
- klik **Upload files**;
- seret **SEMUA ISI** folder NEWS1 Free ke GitHub;
- scroll ke bawah;
- tekan **Commit changes**.

Yang benar: di halaman depan repository langsung terlihat folder `.github`, `android`, `data`, dan `scripts`.

---

# BAGIAN 3 — HIDUPKAN ROBOT UPDATE NEWS1

### 6. Buka tab Actions
Di bagian atas repository, tekan **Actions**.

Kalau GitHub bertanya apakah workflow boleh dijalankan, aktifkan/izinkan Actions untuk repository tersebut.

### 7. Cari workflow `Update NEWS1 Free Data`
Klik nama workflow tersebut.

### 8. Tekan `Run workflow`
- klik tombol **Run workflow**;
- pilih branch `main` jika diminta;
- klik tombol hijau **Run workflow**.

### 9. Tunggu sampai tanda hijau
Jika berhasil, workflow akan mempunyai tanda centang hijau.

### 10. Cek apakah laporan dibuat
Kembali ke tab **Code** -> buka:

```text
data/latest.json
```

Kalau berhasil, isinya berubah dari data DEMO menjadi laporan yang memiliki antara lain:

```text
"freeMode": true
"generatedAtWib": "... WIB"
"summary": {...}
"marketSnapshot": [...]
"news": [...]
"calendar": [...]
```

Kalau beberapa bagian kosong, baca `warnings` di file tersebut. Sistem sengaja menampilkan gagal/cached, bukan mengarang data.

### 11. Setelah itu robot mencoba update setiap jam
Workflow memiliki jadwal satu kali setiap jam. GitHub tidak menjamin jadwal berjalan tepat pada detik/menit yang sama, jadi keterlambatan beberapa waktu masih normal.

---

# BAGIAN 4 — BUAT APK

### 12. Masih di tab Actions
Cari workflow:

**Build NEWS1 Free APK**

### 13. Tekan `Run workflow`
Sama seperti sebelumnya:
- klik **Run workflow**;
- branch `main`;
- klik **Run workflow** lagi.

### 14. Tunggu tanda hijau
Robot GitHub akan:
- menyiapkan Java;
- menyiapkan Android SDK;
- otomatis memasukkan alamat repository Anda ke aplikasi;
- membangun APK.

Anda tidak perlu mengedit kode URL secara manual.

### 15. Download hasil APK
Klik workflow yang sudah hijau -> scroll ke bagian **Artifacts**.

Klik:

```text
NEWS1-Free-APK
```

GitHub akan mendownload sebuah ZIP.

### 16. Ekstrak ZIP artifact
Di dalamnya ada:

```text
app-debug.apk
```

**Inilah aplikasi Android Anda.**

---

# BAGIAN 5 — PASANG DI HP

### 17. Pindahkan `app-debug.apk` ke HP
Bisa lewat:
- kabel USB;
- Google Drive;
- Telegram Saved Messages;
- metode transfer file pribadi lainnya.

### 18. Tap `app-debug.apk`
Android mungkin menampilkan peringatan **Install unknown apps / aplikasi dari sumber tidak dikenal**.

### 19. Beri izin instalasi untuk aplikasi pembuka
Contoh: kalau APK dibuka dari Chrome, Android mungkin meminta izin agar Chrome boleh memasang APK. Aktifkan hanya untuk proses instalasi jika Anda memang percaya APK yang baru Anda build sendiri.

### 20. Tekan Install
Selesai -> tekan **Open / Buka**.

---

# BAGIAN 6 — PERTAMA KALI BUKA NEWS1 FREE

### 21. Izinkan notifikasi
Kalau HP meminta izin notifikasi, pilih **Izinkan** jika ingin menerima update.

### 22. Tekan Refresh
APK yang dibangun dari GitHub Actions sudah diberi alamat seperti:

```text
https://raw.githubusercontent.com/NAMA-ANDA/news1-free/main/data/latest.json
```

Jadi biasanya Anda **tidak perlu mengatur endpoint sendiri**.

### 23. Kalau alamat belum benar
Tekan tombol **⚙** di aplikasi.

Isi **URL data GitHub HTTPS** dengan format:

```text
https://raw.githubusercontent.com/USERNAME/NAMA-REPOSITORY/main/data/latest.json
```

Contoh jika username `budi123` dan repo `news1-free`:

```text
https://raw.githubusercontent.com/budi123/news1-free/main/data/latest.json
```

Tekan **Simpan Pengaturan**, kembali, lalu tekan **Refresh**.

---

# BAGIAN 7 — BAGAIMANA NEWS1 FREE BEKERJA?

Robot GitHub menjalankan `scripts/build_news1_free.py`.

Sistem mencoba mengumpulkan:

- harga/market snapshot dari endpoint web publik Yahoo Finance;
- kalender dari feed publik Forex Factory/FairEconomy ketika tersedia;
- headline/metadata 24 jam melalui indeks RSS gratis dan hanya mempertahankan publisher Reuters, Bloomberg, Yahoo Finance, Trading Economics, dan Forex Factory.

Kemudian rule engine membuat:

- bias XAUUSD;
- bias DXY;
- bias US10Y yield;
- bias oil;
- dominant asset;
- next catalyst;
- penanda `MENGIKUTI / TERTAHAN / MELAWAN` bila arah rule dapat dibandingkan dengan harga;
- konflik fundamental sederhana;
- confidence **RULE-BASED**.

### Yang TIDAK dilakukan
NEWS1 Free tidak:
- memakai OpenAI API;
- memakai ChatGPT Plus sebagai API;
- memakai Render;
- menerobos paywall;
- mengarang isi artikel yang tidak bisa dibaca;
- menganggap headline sebagai isi lengkap artikel.

---

# BAGIAN 8 — KALAU ERROR, LIHAT INI

## Kasus A — `Update NEWS1 Free Data` merah
Buka workflow merah -> buka langkah yang merah.

Kemungkinan:
- endpoint publik sedang tidak merespons;
- GitHub sementara bermasalah;
- format sumber berubah.

Tekan **Re-run jobs** sekali. Kalau tetap gagal, simpan screenshot error dan kirim ke ChatGPT.

## Kasus B — Workflow hijau tetapi market/news kosong
Buka `data/latest.json` -> cari `warnings`.

Kalau tertulis endpoint publik gagal, sistem memang tidak mendapatkan data pada run itu. Ia tidak akan membuat angka palsu.

## Kasus C — Build APK merah
Kirim screenshot langkah merah dari workflow `Build NEWS1 Free APK` ke ChatGPT. Jangan hanya kirim tulisan “error”; bagian merahnya penting.

## Kasus D — APK terpasang tetapi data DEMO
1. Pastikan `data/latest.json` di GitHub sudah live.
2. Buka ⚙ di APK.
3. Pastikan URL memakai username dan repository Anda.
4. Tekan Save.
5. Tekan Refresh.

## Kasus E — Notifikasi tidak muncul
- pastikan notifikasi NEWS1 Free diizinkan di Android;
- pastikan **Refresh otomatis + notifikasi** aktif;
- Android dapat menunda background work untuk menghemat baterai.

---

# BAGIAN 9 — URUTAN PALING MUDAH DIINGAT

```text
UPLOAD KE GITHUB
      ↓
ACTION: UPDATE DATA
      ↓
CEK data/latest.json
      ↓
ACTION: BUILD APK
      ↓
DOWNLOAD app-debug.apk
      ↓
INSTALL DI HP
      ↓
REFRESH
      ↓
SELESAI
```

Jika Anda baru pertama kali, jangan kerjakan semuanya sekaligus. Target pertama hanya sampai **`data/latest.json` berhasil berubah**. Setelah itu baru build APK.
