# STATE.md

Live status of ExifDrop. Update as state changes.

## Current

- **Phase:** v0.1 scaffold (md skeleton). Awaiting code go.
- **Name locked:** ExifDrop. Verified clear on GitHub, Play, App Store, F-Droid, domains.
- **Assigned:** 2026-09-06.
- **Device target:** Realme GT7, Android 16 (API 36), arm64 — build/test over USB.
- **Distro:** Apache 2.0, GitHub Releases + F-Droid.

## Open

- [ ] Gradle scaffold (Kotlin DSL, version catalog).
- [ ] Manifest: trampoline activity, `image/*` + `application/pdf`, `exported="true"`.
- [ ] JPEG/PNG wipe (exifinterface) + orientation guard + neutral filename.
- [ ] WebP hand-rolled XMP chunk strip.
- [ ] PDF hand-rolled metadata rewriter.
- [ ] FileProvider + `EXTRA_EXCLUDE_COMPONENTS` hand-off.
- [ ] Cache purge in `Application.onCreate` (>24h / >64MB).
- [ ] JUnit fixtures + assertions.
- [ ] Apache-2.0 LICENSE.
- [ ] Build + test on Realme GT7.

## Decisions logged

- Wedge: share-sheet-native + lossless + zero-UI PDF scrubbing. No competitor holds it (Scrambled Exif is JPEG-only org mirror; ExifEraser images-only, maintenance mode). Thin moat — an afternoon's work for any active FOSS maintainer → ship fast.
- `pdfbox-android` rejected (dead, CVE-flagged, F-Droid KnownVuln) → hand-rolled PDF rewriter.
- WebP XMP not covered by exifinterface → hand-strip the chunk.
- ICC preserved (color data, not identity) except where a WebP XMP chunk means the privacy-clean is incomplete.