<!--
  Meta description (GitHub summary + search indexing):
  ExifDrop, the Android share-sheet EXIF stripper. Removes GPS, camera, timestamp and XMP metadata from JPEG, PNG, WebP and PDF before a file reaches WhatsApp, email or Telegram. Offline, lossless, two taps, no INTERNET permission. Apache 2.0.
  Keywords: exif stripper, remove gps from photo, android metadata scrubber, share sheet privacy, wipe photo location, pdf metadata remover, offline privacy app
-->
# ExifDrop: Android share-sheet EXIF & GPS metadata stripper

Strip EXIF, GPS location, camera data, timestamps and XMP from photos and PDFs at the moment you share them. On Android, share any file to WhatsApp, email or Telegram, pick ExifDrop in the share sheet, and a clean, neutrally-named copy hands off to your real destination. Android 7.0+ (API 24). Fully offline, no INTERNET permission, Apache 2.0.

## Why

Android phones write far more into a file than the pixels. Photos carry GPS coordinates, camera model, timestamps, XMP and device software tags. PDFs carry author, creator tool and edit history. When you share through WhatsApp document mode, email or Telegram, that metadata travels intact.

Most photo metadata removers make you open an app, pick the file, strip, then start a second share flow. ExifDrop runs inside the Android share sheet instead: share from any app, tap ExifDrop, it strips EXIF data and renames, the clean file lands at your destination. GPS wipe and orientation are toggles in one settings screen. No account, no cloud, no network.

## Features (v0.1)

- **Images: JPEG, PNG, WebP.** Lossless tag-wipe via `androidx.exifinterface`. GPS coordinates, camera model, timestamp and XMP removed without re-encoding pixels. No quality loss, no resize.
- **Settings shell.** Filename pattern (neutral or original), GPS strip toggle, orientation keep toggle, cache age. Persisted, privacy-first defaults.
- **Neutral filenames.** `share_<hash>.<ext>` by default. The `IMG_20250115_140233.jpg` date pattern does not survive either. Opt in to keep the original name.
- **Orientation preserved.** Photos don't share sideways.
- **Cache auto-purge.** Stripped copies older than the configured age (default 24h) clear on next share.

## Install

Grab the APK from [Releases](releases/latest) and install on Android 7.0+ (API 24). No permissions requested at runtime; the file is read from and written to app-private storage via `ContentResolver`.

> F-Droid submission was decided out of scope for v0.1.

## How it works

```
share from any app → pick ExifDrop (image/*, application/pdf)
  → ContentResolver reads Uri → strip metadata → neutral name → cache
  → FileProvider content:// → second share sheet → real destination
```

Photo metadata removal is lossless: the stripper edits the binary metadata segments and copies the pixels untouched. PDF cleaning neutralizes the trailer `/Info` dictionary and the XMP metadata stream in place, so stream offsets and the xref table stay valid.

## Non-goals (v0.1)

- Play Store distribution.
- Root / invisible system-wide intercept (Magisk + LSPosed).
- Office documents (docx/xlsx/pptx) and video. Future versions.
- Batch, memory, gallery-scan workflows.
- Strip history or visual before/after diffs in the shell.

## Privacy & security

- No INTERNET permission. Nothing leaves the device, ever.
- Stripped copies live in app-private cache and purge automatically, default one day.
- The original source file is never modified.
- No account, no telemetry, no ads.

## FAQ

**Does it remove GPS from photos?** Yes. GPS EXIF tags are wiped from JPEG and PNG by default, toggle off in settings if you want to keep them. WebP doesn't parse the embedded TIFF, so WebP is always stripped.

**Does it recompress or degrade photo quality?** No. Metadata removal is lossless; pixel data is copied byte-for-byte. A photo that is 100% quality stays 100% quality.

**Which file types are supported?** JPEG, PNG, WebP and PDF. Office documents and video are out of scope for v0.1.

**Does it need internet or a Google account?** No. Every byte is handled on-device through `ContentResolver` into app storage.

**Is it paid?** No. ExifDrop is free open-source software under Apache License 2.0.

## Contributing

Bug reports and PRs welcome. See [CONTRIBUTING](CONTRIBUTING.md) and [CODE_OF_CONDUCT](CODE_OF_CONDUCT.md).

## License

Apache License 2.0. See [LICENSE](LICENSE).