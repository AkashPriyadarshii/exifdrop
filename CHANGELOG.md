# CHANGELOG.md

All notable changes to ExifDrop are documented here. Format follows [Keep a Changelog](https://keepachangelog.com/).

## [0.1.0] - 2026-09-12

### Added
- **Zero-UI Share Trampoline (`TrampolineActivity`):** Native Android share sheet interceptor for `image/jpeg`, `image/png`, `image/webp`, and `application/pdf`. Strips files in-memory on background executor without UI freeze.
- **Scrub Confirmation Feedback:** Ephemeral system Toast ("Cleaned N files · metadata stripped") and native haptic tick (`HapticFeedbackConstants.CONFIRM`) right before destination chooser handoff.
- **Quick Settings Tile (`FilenameTileService`):** System notification shade toggle to switch between `share_<hash>` scrambling and keeping original filenames with a single tap.
- **Lossless Image Metadata Stripper (`ImageStripper`):** Complete tag-wipe across 48 EXIF attributes (GPS, camera, exposure, optics, timestamps, serial numbers, XMP, IPTC) using `androidx.exifinterface:1.4.2`.
- **Hand-rolled WebP Stripper (`WebpStripper`):** Lossless RIFF container parsing, `XMP ` chunk removal, VP8X bit-2 reset, and 26-byte TIFF orientation injection to maintain upright photos without re-encoding pixels.
- **Hand-rolled PDF Metadata Stripper (`PdfStripper`):** In-place neutralization of trailer `/Info` dictionaries and catalog XMP stream pointers without heavy, vulnerable dependencies.
- **SHA-256 Neutral Renamer (`Renamer`):** Privacy-first filename masking (`share_<hash>.<ext>`) removing timestamps and device markers from shared files.
- **Companion Launcher Shell (`MainActivity`):** Avant-Garde Editorial Brutalism interface providing status verification, persistent strip settings, and cache management.
- **Live Sanitizer Receipt Sheet (`SanitizerReceiptSheet`):** Pre-flight metadata inspection sheet displaying detected EXIF, GPS, and XMP tags with individual purge indicators and direct strip & share action.
- **Asynchronous Cache Purge (`ExifDropApp`):** Cold-start cache janitor running on background executor to evict cleaned files older than the configured threshold or exceeding 64MB.
- **Automated Test Suite (`StripperTest`):** 15 comprehensive unit tests covering format validation, orientation preservation, and corruption recovery.
- **FOSS Ecosystem & Author Footer:** Attribution and official ecosystem links in compliance with repository standards.