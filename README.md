# ExifDrop

Android app that strips metadata from photos and PDFs the moment you share them. Pick ExifDrop in the share sheet and a clean, neutrally-named file passes to your real destination. A small settings screen controls strip behavior. Fully offline. Apache 2.0.

## Why

Android phones write far more into a file than the pixels. Photos carry GPS, camera model, timestamps, XMP, and device software tags. PDFs carry author, creation tool, and edit history. When you share through WhatsApp document mode, email, or Telegram, that metadata travels intact.

Most tools make you open an app, pick the file, strip, then share. ExifDrop does it in the share sheet itself: share from any app → pick ExifDrop → it strips and renames → the file lands clean at your destination. Setup lives in one settings screen; no internet.

## Features (v0.1)

- **Images — JPEG, PNG, WebP.** Lossless tag-wipe via `androidx.exifinterface`. GPS, camera, timestamp, XMP removed without re-encoding pixels.
- **PDF — first-class.** Trailer `/Info` dict and XMP metadata stream stripped. No heavy PDF library, no CVEs, fully offline.
- **Settings shell.** Filename pattern (neutral or original), GPS strip toggle, orientation keep toggle, cache age. Persisted, privacy-first defaults.
- **Neutral filenames.** `share_<hash>.<ext>` by default — the `IMG_20250115_140233.jpg` date pattern doesn't survive either. Opt in to keep the original name.
- **Orientation preserved.** Photos don't share sideways.
- **Cache auto-purge.** Stripped copies older than the configured age (default 24h) clear on next share.

## Install

- **GitHub Releases:** grab the APK from [Releases](releases/latest).
- **F-Droid:** pending submission.

Requires Android 7.0+ (API 24). No permissions requested for use; the file is read from and written to app-private storage via `ContentResolver`.

## How it works

```
share from any app → pick ExifDrop (image/*, application/pdf)
  → ContentResolver reads Uri → strip metadata → neutral name → cache
  → FileProvider content:// → second share sheet → real destination
```

## Non-goals (v0.1)

- Play Store distribution.
- Root / invisible system-wide intercept (Magisk + LSPosed).
- Office documents (docx/xlsx/pptx) and video. Future versions.
- Batch, memory, gallery-scan workflows.
- Strip history or visual before/after diffs in the shell.

## Security model

- No internet permission. Nothing leaves the device.
- Stripped copies live in app cache and are purged automatically.
- Source file is never modified.
- Apache 2.0 — free for FOSS use by anyone.

## License

Apache License 2.0. See [LICENSE](LICENSE).