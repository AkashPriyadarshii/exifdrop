# DESIGN — ExifDrop v0.1

Engineering design, as-of 2026-09-06.

## Overview

Zero-UI Android app. Registers as a share target for `image/*` + `application/pdf`. On share: read the Uri, strip metadata, rename neutral, hand a clean `content://` to the user's chosen destination via FileProvider, then the trampoline finishes.

## Architecture

```
[source app] --share intent--> [ExifDrop trampoline activity]
                                    |  (ContentResolver.read + strip + rename → cache)
                                    v
                     [FileProvider content:// + read grant]
                                    |
                       [Intent.createChooser(), EXTRA_EXCLUDE_COMPONENTS]
                                    v
                           [real destination app]
```

- **Trampoline activity:** `Theme.Translucent.NoTitleBar`, no layout, `android:noHistory`, `android:excludeFromRecents`, `android:exported="true"`. Launches the chooser then `finish()`. Never visible, never in recents/back stack. Foreground (user-initiated) so BAL does not apply.
- **FileProvider:** stripped copy lives in app-private cache; the destination receives `content://` via `FileProvider.getUriForFile` + `Intent.FLAG_GRANT_READ_URI_PERMISSION`. (`file://` on minSdk 24 throws `FileUriExposedException`.)
- **Anti-recursion:** `Intent.EXTRA_EXCLUDE_COMPONENTS` hides our own `image/*`/PDF target from the second chooser.

## Stripping

### Images (JPEG / PNG / WebP)

- `androidx.exifinterface:1.4.2` tag-wipe: GPS, camera make/model, date/time, software, IPTC-read, XMP. Lossless — binary segments, no pixel decode.
- **WebP XMP gap:** exifinterface never reads/writes a separate WebP XMP chunk; `setAttribute(TAG_XMP, null)` is a no-op there. Hand-rolled WebP chunk-strip removes the `XMP` chunk and clears the XMP bit in `VP8X` flags.
- **Orientation guard:** read `TAG_ORIENTATION` before wipe, re-apply after (else shared photos rotate).
- **ICC:** preserve for JPEG/PNG (color data, not identity). WebP with XMP must lose ICC too if it carries an XMP chunk — a chunk-strip that keeps ICC still leaks the XMP embedded with it.
- **Note (edge):** JPEG/PNG with XMP in both Exif tag-700 and a separate segment — the wipe clears the preferred copy, orphans the other. Rare, documented; v0.1 accepts and tests the common single-segment path.

### PDF

Hand-rolled metadata rewriter (no `pdfbox-android`):
- Clear trailer `/Info` dictionary fields (Author, Creator, Producer, Title, CreationDate, ModDate).
- Remove the XMP metadata stream via the catalog: zero out the stream reference.
- Streams/pages untouched; linearize via a full rewrite, not incremental save, so no vestigial `/Info` bytes survive.

## Hand-off & UX

- **Filename scrub:** outgoing name `share_<hash>.<ext>` — kills the date-in-name pattern `IMG_20250115_140233.jpg`.
- **Cache purge:** in `Application.onCreate`, delete stripped files >24h old or when cache exceeds 64MB. Zero settings.
- **Cold start:** minimal `Application`, no DI, no Compose. Keep tens-of-ms.

## Stack & constraints

- Kotlin, minSdk 24, targetSdk 36. No Compose, no appcompat, no DI, no INTERNET permission.
- Deps: `androidx.exifinterface:1.4.2`. Everything else stdlib/platform.
- F-Droid path: FLOSS, trusted Maven repos only, no CVE-flagged deps (hand-rolled PDF avoids `KnownVuln`).

## Risks / edge cases

| Edge | Handling |
| --- | --- |
| Dual-segment XMP (tag-700 + separate) | Accept in v0.1, verify with exiftool fixtures. |
| Orientation absent in source | `addDefaultValuesForCompatibility` may land `ORIENTATION_UNDEFINED=0`; accept, document. |
| Large scanned PDF | Rewrite ~100–300ms, no UI to freeze; add spinner later only if real files prove slow. |
| Double-tap share | Single-flight guard/busy flag on the trampoline. |