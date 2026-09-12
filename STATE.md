# STATE.md

Live status of ExifDrop. Update as state changes.

## Current

- **Phase:** v0.1 Complete & Verified.
- **Name locked:** ExifDrop. Verified clear on GitHub, Play, App Store, F-Droid, domains.
- **Assigned:** 2026-09-06.
- **Release date:** 2026-09-12.
- **Device target:** Android 7.0+ (API 24 to API 36), arm64/x86_64.
- **Distro:** Apache 2.0, GitHub Releases (`exifdrop-v0.1-signed.apk`).

## Checklist (v0.1)

- [x] Gradle scaffold (Kotlin DSL, version catalog, minSdk 24, targetSdk 36).
- [x] Manifest: trampoline activity, `image/*` + `application/pdf`, `exported="true"`, `launchMode="standard"`.
- [x] JPEG/PNG wipe (exifinterface) + orientation guard + neutral filename.
- [x] WebP hand-rolled XMP chunk strip + clean 26-byte TIFF orientation injection.
- [x] PDF hand-rolled metadata rewriter (trailer `/Info` + XMP catalog stream).
- [x] FileProvider + `EXTRA_EXCLUDE_COMPONENTS` anti-recursion hand-off.
- [x] Cache purge in `Application.onCreate` (async background executor, >24h / >64MB).
- [x] Quick Settings Tile (`FilenameTileService`) for instant filename toggle.
- [x] Companion launcher shell (`MainActivity`) with persistent settings and Avant-Garde Editorial Brutalism styling.
- [x] Live Sanitizer Receipt ModalBottomSheet (`SanitizerReceiptSheet`): pre-flight metadata inspection for in-app picked files.
- [x] Scrub Confirmation Toast + haptic feedback (`HapticFeedbackConstants.CONFIRM`).
- [x] JUnit test suite: 15/15 unit tests passing (`StripperTest`).
- [x] Apache-2.0 LICENSE.

## Architecture Decisions Logged

- **Share-sheet native:** Two-tap flow (`share` -> `ExifDrop` -> `destination`) via translucent `TrampolineActivity`. Zero UI freeze, background executor.
- **Backward compatibility:** Old share workflow remains 100% untouched and pure zero-UI. In-app picker with Sanitizer Receipt sheet is strictly an opt-in companion tool for inspecting files directly.
- **Quick Settings Tile:** `FilenameTileService` toggles between `share_<hash>` and original filenames directly from the Android quick settings shade.
- **`pdfbox-android` rejected:** Dead, CVE-flagged, F-Droid KnownVuln -> hand-rolled PDF rewriter neutralizing trailer `/Info` and catalog XMP streams.
- **WebP XMP handling:** `androidx.exifinterface:1.4.2` cannot read/write WebP XMP. Hand-rolled RIFF chunk-stripper removes `XMP ` chunks, clears VP8X bit 2, and cleanly injects a minimal 26-byte TIFF chunk to preserve EXIF orientation without leaking data.
- **ICC Profile:** Preserved for color fidelity on JPEG and PNG. Removed on WebP when coupled with XMP.
- **Offline & zero-cost:** 100% offline, zero network permissions, ₹0 external dependencies.