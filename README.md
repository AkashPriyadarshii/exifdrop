<!--
  Meta description (GitHub summary + search indexing):
  ExifDrop, the Android share-sheet EXIF stripper. Removes GPS, camera, timestamp and XMP metadata from JPEG, PNG, WebP and PDF before a file reaches WhatsApp, email or Telegram. Offline, lossless, two taps, no INTERNET permission. Apache 2.0.
  Keywords: exif stripper, remove gps from photo, android metadata scrubber, share sheet privacy, wipe photo location, pdf metadata remover, offline privacy app
-->
# ExifDrop: Android share-sheet EXIF & GPS metadata stripper

<div align="center">
  <img src="docs/icon.png" alt="ExifDrop app icon" width="120">
  <br>
  <a href="releases/latest"><img alt="Release" src="https://img.shields.io/github/v/release/AkashPriyadarshii/exifdrop?label=release&color=2E7D4F"></a>
  <a href="LICENSE"><img alt="License: Apache 2.0" src="https://img.shields.io/badge/license-Apache%202.0-2E7D4F"></a>
  <img alt="Android 7.0+" src="https://img.shields.io/badge/Android-7.0%2B-2E7D4F">
  <img alt="Offline" src="https://img.shields.io/badge/offline-no%20network-6E675C">
  <br>
  <a href="https://akashpriyadarshii.github.io/exifdrop/">Website</a>
  &#183;
  <a href="https://github.com/AkashPriyadarshii/exifdrop/releases/download/v0.1/exifdrop-v0.1-signed.apk">Download APK</a>
  &#183;
  <a href="https://github.com/AkashPriyadarshii/exifdrop">GitHub</a>
</div>

Strip EXIF, GPS location, camera data, timestamps and XMP from photos and PDFs at the moment you share them. On Android, share any file to WhatsApp, email or Telegram, pick ExifDrop in the share sheet, and a clean, neutrally-named copy hands off to your real destination. Android 7.0+ (API 24). Fully offline, no INTERNET permission, Apache 2.0.

## Why

Android phones write far more into a file than the pixels. Photos carry GPS coordinates, camera model, timestamps, XMP and device software tags. PDFs carry author, creator tool and edit history. When you share through WhatsApp document mode, email or Telegram, that metadata travels intact.

Most photo metadata removers make you open an app, pick the file, strip, then start a second share flow. ExifDrop runs inside the Android share sheet instead: share from any app, tap ExifDrop, it strips EXIF data and renames, the clean file lands at your destination. GPS wipe and orientation are toggles in one settings screen. No account, no cloud, no network.

## Features (v0.1)

- **Share-sheet native (2 taps).** Share from WhatsApp, Gallery, Files, or Telegram → pick ExifDrop → hands clean copy to your destination. Zero UI freeze, background executor.
- **Images: JPEG, PNG, WebP.** Lossless tag-wipe via `androidx.exifinterface`. GPS coordinates, camera model, timestamp and XMP removed without re-encoding pixels. No quality loss, no resize.
- **PDF cleaning.** Hand-rolled trailer `/Info` and catalog XMP stream rewriter. Strips author, generator, and timestamps without bloated CVE-flagged libraries.
- **Quick Settings Tile.** Toggle between neutral hash (`share_<hash>`) and original filenames instantly from your Android notification shade without opening the app.
- **Live Sanitizer Receipt.** In-app file inspector sheet: pick photos or PDFs, review detected metadata (GPS, camera, lens, timestamps) with visual purge indicators, and strip + share in one tap.
- **Scrub confirmation & haptics.** Ephemeral system toast ("Cleaned N files · metadata stripped") and native haptic tick upon successful scrub.
- **Orientation preserved.** WebP and JPEG/PNG orientation tags are guarded so shared photos stay upright.
- **Cache auto-purge.** Stripped copies in private cache clear on startup if older than configured days or >64MB total.
- **Fully offline.** No INTERNET permission. No ads, no tracking, no servers.

## Install

Grab the APK from [Releases](releases/latest) or [download `exifdrop-v0.1-signed.apk` directly](https://github.com/AkashPriyadarshii/exifdrop/releases/download/v0.1/exifdrop-v0.1-signed.apk) and install on Android 7.0+ (API 24). No runtime permissions requested.

See it in action at the [ExifDrop website](https://akashpriyadarshii.github.io/exifdrop/).

## How it works

### 1. Primary Share-Sheet Flow (Zero-UI)
```
share from any app → pick ExifDrop (image/*, application/pdf)
  → TrampolineActivity reads Uri in background → strips metadata → renames
  → FileProvider content:// → haptic tick & scrub toast → destination chooser
```

### 2. In-App Pre-Flight Inspection (Sanitizer Receipt)
```
open ExifDrop → tap "Inspect & strip files"
  → live sheet displays detected EXIF / GPS / XMP tags
  → tap "STRIP ALL & SHARE" → hands clean file to destination chooser
```

Photo metadata removal is lossless: the stripper edits binary segments and copies pixels byte-for-byte. PDF cleaning neutralizes trailer `/Info` fields and catalog XMP streams in place.

## Non-goals (v0.1)

- Play Store distribution.
- Root / invisible system-wide intercept (Magisk + LSPosed).
- Office documents (docx/xlsx/pptx) and video.
- Batch background gallery-crawling.

## Privacy & security

- No `android.permission.INTERNET`. Nothing leaves the device.
- Stripped files live in app-private cache and auto-purge.
- The original source file is never modified.
- 100% FOSS, Apache 2.0 license, ₹0 budget stack.

## FAQ

**Does it remove GPS from photos?** Yes. GPS EXIF tags are wiped by default across JPEG, PNG, and WebP.

**Does it degrade photo quality?** No. Metadata removal is lossless; pixels are copied untouched.

**Which file types are supported?** JPEG, PNG, WebP, and PDF.

**Does it need internet or an account?** No. Pure local execution via `ContentResolver`.

## License

Apache License 2.0. See [LICENSE](LICENSE).

---

## Ecosystem & Author

### Ecosystem
- [`design-genius`](https://github.com/AkashPriyadarshii/design-genius)
- [`akash-design-engineering`](https://github.com/AkashPriyadarshii/akash-design-engineering)
- [`tdlib-android`](https://github.com/AkashPriyadarshii/tdlib-android)
- [`kharcha`](https://github.com/AkashPriyadarshii/kharcha)

### Author
- **Akash Priyadarshi** (Patna, Bihar, India)
- [GitHub](https://github.com/AkashPriyadarshii) · [Portfolio](https://akashpriyadarshi.vercel.app) · [LinkedIn](https://linkedin.com/in/akash-priyadarshi-1aa51b37a) · [Resume](https://akashpriyadarshii.github.io/Resume/)

### Social
- [X / Twitter](https://x.com/Akash__ydv001) · [Threads](https://www.threads.com/@free_dev2026) · [Instagram](https://www.instagram.com/akash.priyadarshii/) · [Reddit](https://reddit.com/user/DragonfruitWeak2801)