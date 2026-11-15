# 🎱 8BallLine - 8 Ball Pool Digital Guideline & Diamond System Overlay

<p align="center">
  <img src="app/src/main/res/drawable/ic_ball8.xml" width="100" height="100" alt="8BallLine Logo"/>
</p>

Aplikasi Android Floating Screen Overlay (layar mengambang transparan) untuk game **8 Ball Pool (Miniclip)**. Aplikasi ini memproyeksikan garis-garis panduan pantulan (*bank shot trajectories*) berbasis **Diamond System** secara digital langsung di atas layar game, tanpa memerlukan stiker fisik atau tempered glass.

---

## 💡 Latar Belakang & Inspirasi

Aplikasi ini diadaptasi dan dikembangkan dari konsep **"Plastik & Stiker Garis Pantul 8 Ball Pool"** yang viral di komunitas (seperti pada tutorial TikTok [@rikisenjaya8bp](https://www.tiktok.com/@rikisenjaya8bp/video/7433864950143044871)). 

Alih-alih membeli stiker mika atau tempered glass fisik yang permanen dan hanya cocok untuk satu tipe layar tertentu, **8BallLine** menghadirkan solusi digital yang:
1. **100% Aman & Anti-Banned**: Bekerja menggunakan fitur resmi Android `SYSTEM_ALERT_WINDOW` (*Display over other apps*). Sama sekali tidak memodifikasi APK game, tidak menginjeksi memori game, dan tidak memerlukan akses Root.
2. **Touch Pass-Through**: Saat bermain, layar overlay diatur dengan flag `FLAG_NOT_TOUCHABLE`, sehingga seluruh ketukan dan geseran stik Anda tembus 100% langsung ke game 8 Ball Pool tanpa terganggu.
3. **Fleksibel untuk Semua HP (Fitur Kalibrasi 4 Sudut)**: Dilengkapi handle kalibrasi interaktif yang memungkinkan Anda menggeser 4 sudut meja secara manual agar pas dengan posisi meja 8BP di resolusi layar HP mana pun (bezel tipis, punch hole, notch, rasio 20:9, 16:9, tablet, dll).

---

## ✨ Fitur Utama

- 📐 **Sistem Diamond & Titik Pantul Lengkap**:
  - **Titik 1, 2, 3**: Diamond paruh pertama rel meja.
  - **Titik 4**: Lubang tengah (*middle pocket*) terhitung sebagai Titik 4.
  - **Titik 5, 6**: Diamond paruh kedua rel meja.
  - Titik antara (*half-diamond*: 1.5, 2.5, 3.5, 5.5).
- 🎯 **Filter Lubang Target (Pocket)**:
  - Tampilkan garis menuju semua lubang (*All Pockets*).
  - Tampilkan khusus **Lubang Kiri Bawah** atau **Lubang Kiri Atas** (seperti pada video tutorial).
  - Tampilkan khusus **Lubang Kanan Bawah** atau **Lubang Kanan Atas**.
- 🎛️ **Floating Control Bubble**:
  - Tombol melayang berbentuk bola 8 yang bisa digeser bebas ke tepi layar mana pun.
  - Sekali ketuk untuk membuka kontrol cepat: nyalakan/matikan garis, ubah opasitas, ganti target lubang, dan masuk mode kalibrasi.
- 🎨 **Kustomisasi Tampilan**:
  - Pilihan warna garis neon (Cyan, Emerald Green, Gold Yellow, Neon Red).
  - Slider tingkat transparansi (opasitas) agar tidak menghalangi pandangan bola.
  - Dukungan orientasi **Landscape** (standar bermain) dan **Portrait** (mode pass 'n play / tutorial).

---

## 🚀 Cara Menggunakan

1. **Unduh & Pasang APK**:
   - Download file APK hasil kompilasi GitHub Actions dari tab [Actions](../../actions) atau kompilasi sendiri via Android Studio.
2. **Beri Izin Overlay**:
   - Buka aplikasi **8BallLine**.
   - Klik **"Beri Izin Tampilkan di Atas Aplikasi Lain"** dan aktifkan toggle izin untuk 8BallLine.
3. **Mulai Overlay**:
   - Tekan tombol hijau **"Mulai Overlay Garis Pantul"**.
   - Ikon bola 8 melayang akan muncul di layar Anda.
4. **Kalibrasi Meja (Hanya Sekali di Awal)**:
   - Buka game **8 Ball Pool**.
   - Masuk ke meja latihan atau game biasa.
   - Ketuk ikon bola 8 melayang, lalu klik tombol merah **"Masuk Mode Kalibrasi"**.
   - Tarik 4 lingkaran merah di sudut layar hingga tepat berada di ujung bantalan (*cushion*) meja 8 Ball Pool Anda.
   - Ketuk kembali tombol hijau **"Kunci Meja (Mode Main)"**. Posisi akan tersimpan otomatis!
5. **Mainkan Game**:
   - Sejajarkan stik putih Anda dengan garis panduan titik 1-6 untuk memasukkan bola pantulan (*bank shot*) dengan presisi tinggi.

---

## 🛠️ Panduan Build (Untuk Pengembang)

Proyek ini dibangun menggunakan **Android Gradle Plugin** modern dan **Kotlin**:

```bash
# Clone repositori
git clone https://github.com/Dayat222/8BallLine.git
cd 8BallLine

# Build APK Debug
./gradlew assembleDebug

# Lokasi file APK
app/build/outputs/apk/debug/app-debug.apk
```

Setiap *push* ke branch `main` akan secara otomatis memicu alur kerja **GitHub Actions** untuk mengompilasi APK dan menyimpannya di tab *Artifacts*.

---

## 📄 Lisensi
Didistribusikan untuk tujuan edukasi analisis fisika biliar dan latihan geometri pantulan.
