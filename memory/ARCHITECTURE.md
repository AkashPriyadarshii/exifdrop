# memory/ARCHITECTURE.md

Long-term architectural memory for ExifDrop. See `docs/ARCHITECTURE.md` for full detail; this is the standing-kn owledge index.

## What ExifDrop is

Zero-UI Android share-sheet app. Strips metadata from images + PDF, renames neutral, passes clean `content://` to destination. Offline, Apache 2.0.

## Standing decisions (never silently reverse)

- Wedge = share-sheet-native + lossless + zero-UI **PDF**. Images alone is crowded.
- No `pdfbox-android` (dead 2023, CVE-flagged, F-Droid KnownVuln) → hand-rolled PDF rewriter.
- No exifinterface for WebP XMP (invisible to it) → hand-rolled WebP XMP chunk-strip.
- Keep ICC except when WebP carries an XMP chunk.
- Trampoline + FileProvider + `EXTRA_EXCLUDE_COMPONENTS` (anti-recursion).
- Orientation read-before / re-apply-after.
- Neutral filename `share_<hash>.<ext>`.
- No INTERNET permission. Offline-only.
- minSdk 24 / targetSdk 36, no Compose/DI, 2 deps (exifinterface + JUnit).

## Format handling truth (verified 2026-09-06)

| Format | exifinterface covers | Hand-work needed |
| --- | --- | --- |
| JPEG | XMP both segments + EXIF cleared | — |
| PNG | XMP iTXt cleared | — |
| WebP | XMP invisible | Chunk-strip required |
| PDF | n/a | Full metadata rewriter |

## Edge cases owned

- Dual-segment XMP (tag-700 + separate): accept, verify via exiftool fixtures.
- Orientation UNDEFINED default when source absent: accept, document.
- Large scanned PDF latency (~100–300ms): invisible (no UI), spinner later if real files prove slow.
- Thin moat: ship fast, don't gold-plate.