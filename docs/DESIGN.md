# DESIGN — ExifDrop v0.1

Engineering design, as-of 2026-09-12.

## Overview

ExifDrop provides two seamless, non-interfering entry points:
1. **Primary Share-Sheet Trampoline (`TrampolineActivity`):** Registers as a share target for `image/*` + `application/pdf`. Two taps total: tap ExifDrop in the system share sheet, metadata is stripped in the background with a branded spinner cover, a haptic tick and ephemeral scrub confirmation Toast fire, and the clean file hands off to the destination chooser.
2. **Companion Launcher Shell (`MainActivity`):** Status readout, persisted preferences, and an in-app file picker that triggers a **Live Sanitizer Receipt (`SanitizerReceiptSheet`)**—a bottom sheet allowing users to inspect detected EXIF, GPS, and XMP tags before stripping.
3. **Quick Settings Tile (`FilenameTileService`):** Single-tap toggle in the Android system shade to switch between `share_<hash>` scrambling and preserving original filenames.

## Architecture

```
[Path A: Share Sheet (Zero-UI)]
[source app] --ACTION_SEND--> [TrampolineActivity]
                                   | (background Executor: read + strip + rename → cache)
                                   v
                    [FileProvider content:// + read grant]
                                   | (haptic tick + scrub confirmation toast)
                                   v
                      [Intent.createChooser(), EXTRA_EXCLUDE_COMPONENTS]
                                   v
                          [real destination app]

[Path B: In-App Companion (Sanitizer Receipt)]
[MainActivity] --file picker--> [inspectUris()]
                                    | (read-only ExifInterface / PDF stream scan)
                                    v
                         [SanitizerReceiptSheet]
                                    | (review tags: GPS in red, camera, timestamps)
                                    v (tap "STRIP ALL & SHARE")
                             [TrampolineActivity]
```

- **Trampoline activity:** `Theme.Translucent.NoTitleBar`, `noHistory`, `excludeFromRecents`, `exported="true"`, `launchMode="standard"`. Full-screen cover replaces background during strip.
- **FileProvider:** stripped copy lives in app-private cache; destination receives `content://` via `FileProvider.getUriForFile` + `Intent.FLAG_GRANT_READ_URI_PERMISSION`.
- **Anti-recursion:** `Intent.EXTRA_EXCLUDE_COMPONENTS` hides our own `image/*`/PDF target from the second chooser.

## Stripping

### Images (JPEG / PNG / WebP)

- `androidx.exifinterface:1.4.2` tag-wipe: GPS, camera make/model, date/time, software, IPTC-read, XMP across 48 attributes. Lossless — binary segments, no pixel decode.
- **WebP XMP gap:** exifinterface never reads/writes a separate WebP XMP chunk. Hand-rolled WebP chunk-strip removes the `XMP ` chunk, clears the XMP bit in `VP8X` flags, and cleanly re-injects a 26-byte TIFF header to preserve EXIF orientation upright.
- **Orientation guard:** read `TAG_ORIENTATION` before wipe, re-apply after (unless toggled off in settings).
- **ICC:** preserve for JPEG/PNG (color data, not identity). WebP with XMP drops ICC when paired with XMP chunk.

### PDF

Hand-rolled metadata rewriter (no `pdfbox-android`):
- Clear trailer `/Info` dictionary fields (Author, Creator, Producer, Title, CreationDate, ModDate).
- Remove the XMP metadata stream via the catalog: zero out the stream reference.
- Streams and pages untouched; full rewrite ensures no vestigial `/Info` bytes survive.

## UI/UX: Avant-Garde Editorial Brutalism

The companion shell and Sanitizer Receipt adhere to an austere, functional aesthetic:
- **Palette:** Warm Paper (`0xFFFBF9F2`), Ink (`0xFF2A2620`), Safety Green (`0xFF2E7D4F` / `#4CAF78` - reserved for clean/verified states), Error Red (`0xFFB3261E` - reserved for sensitive leaks like GPS).
- **Typography:** Display Roboto Medium tight, Monospace strictly for trust data (hashes, filenames, tags, and status readouts).
- **Hairlines:** 1dp rules (`outlineVariant`) for clean separation without drop shadows.
- **Sanitizer Receipt:** ModalBottomSheet with header badge, scrollable file list with tag breakdowns, sensitive tags highlighted in error red, and full-width brutalist `STRIP ALL & SHARE` button.

## Hand-off & Settings

- **Filename:** default `share_<hash>.<ext>` — kills date-in-name patterns. "Keep name" setting preserves source stem.
- **Quick Settings Tile:** `FilenameTileService` syncs bidirectionally with `Prefs.filenamePattern()`.
- **Cache purge:** on trampoline launch or app cold-start, delete stripped files older than configured days or if cache exceeds 64MB.
- **Settings flow into strip:** `ImageStripper.strip(..., stripGps, keepOrientation)`, `Renamer.name(..., pattern)` read `Prefs` dynamically.

## Stack & constraints

- Kotlin, minSdk 24, targetSdk 36. Compose shell only (BOM 2024.10.01, M3 1.3.0), no appcompat, no DI, no INTERNET permission.
- Two dependencies total: `androidx.exifinterface:1.4.2` and Compose libraries. 100% offline.
- Test suite: 15 unit tests in `StripperTest.kt`.