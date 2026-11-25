# iLauncher

Launcher Android bergaya iPhone (dock + halaman, ikon squircle, panel liquid glass). Tanpa library tambahan, minSdk 21.

## Build
Push ke GitHub (branch main) lalu buka tab **Actions** → **Build APK**. APK ada di Artifacts. Tidak butuh gradle wrapper.

## Pakai
1. Install APK, tekan tombol Home, pilih iLauncher sebagai default.
2. Tahan area kosong di layar utama: **Impor Tema (.json)**, **Reset Tema**, **Wallpaper**.
3. Tahan ikon: Dock, Info, Hapus.
4. Contoh tema di folder `themes/`.

## Field config JSON (semua opsional)
columns, rows, iconSizeDp, iconSquircle (2=lingkaran, 5=iOS), showLabels, labelSizeSp, labelColor,
glassTint, glassAlpha, glassStrokeAlpha, glassRadiusDp, dockSize, wallpaperDim, wallpaperBlur (blur nyata hanya Android 12+), darkMenu
