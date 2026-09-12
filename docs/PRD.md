# PRD — ExifDrop v0.1

Product requirements, as-of 2026-09-06.

## Problem

Android files carry far more than content. Photos embed GPS, camera make/model, timestamps, XMP, software tags. PDFs embed author, creation tool, edit history. Shared via WhatsApp document mode, email, or Telegram, that metadata travels intact. Existing tools force an open-app → pick → strip → share flow, or crop the workflow to photos only.

## Solution

An app that sits inside the Android share sheet. Share from any app → pick ExifDrop → it strips metadata, renames the file per the filename setting, and passes a clean `content://` to the real destination. Two taps total, no intermediate screen in the strip path, fully offline. A thin settings screen controls strip behavior (filename pattern, GPS, orientation, cache age).

## Users

FOSS/privacy-conscious Android users. The casual-photo crowd is served by native "remove location" + image-only tools. The gap is PDFs and the one-tap workflow. Primary early user: the developer (this is a personal/portfolio FOSS app first).

## Scope — in (v0.1)

- Images: JPEG, PNG, WebP — lossless tag-wipe + WebP XMP chunk-strip with orientation re-injection.
- PDF: trailer `/Info` + XMP stream removal — hand-rolled, no pdfbox-android.
- Trampoline activity — zero-UI share-sheet strip path with background executor, haptic tick, and scrub toast.
- Quick Settings Tile — system shade toggle between `share_<hash>` and original filename.
- Compose settings shell: status hub + filename pattern / GPS / orientation / cache age, persisted.
- Live Sanitizer Receipt sheet — in-app pre-flight metadata inspection displaying detected tags and one-tap strip & share.
- FileProvider hand-off, `EXTRA_EXCLUDE_COMPONENTS` anti-recursion.
- Orientation guard, neutral filenames, cache auto-purge.
- Apache 2.0, GitHub Releases (`exifdrop-v0.1-signed.apk`).

## Scope — out (v0.1)

- Play Store, root/invisible intercept, office docs, video, batch/scan, background gallery-crawler.

## Success criteria

- Share an image or PDF → clean file lands at destination in two taps (trampoline + destination), no internet.
- Old share workflow remains pure zero-UI and 100% operational.
- Live Sanitizer Receipt sheet lets users inspect files picked within the app before stripping.
- GPS/XMP/ICC/timestamps verified gone (15 unit tests green); orientation preserved; filename neutral.
- Installs on Android 7–16 (minSdk 24), no permissions, offline.

## Key risks

| Risk | Mitigation |
| --- | --- |
| Thin moat (Scrambled Exif could add PDF in an afternoon) | Ship fast, don't gold-plate v0.1. |
| WebP XMP invisible to exifinterface | Hand-rolled chunk-strip per format. |
| pdfbox-android CVE-flagged | Hand-rolled PDF rewriter. |
| OS-level stripping erodes the category | Keep PDF + zero-UI as the defensible wedge. |

## Timeline

Research complete 2026-09-06. Code on go behind md-gate approval. First target: v0.1 APK on the test device.