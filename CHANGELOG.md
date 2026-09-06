# CHANGELOG.md

All notable changes. Format: `[Unreleased]` → per-release.

## [Unreleased]

- v0.1 scaffold: md skeleton. Project md, README, docs, memory, LICENSE pending.
- Name locked: ExifDrop (verified clear on all channels).
- Research complete: market + feasibility accepted 2026-09-06.
- Core stripper: JPEG/PNG/WebP tag-wipe via exifinterface, PDF trailer `/Info` + XMP rewriter. 8 tests green.
- Trampoline share flow: read → strip in-memory → neutral name → cache → FileProvider hand-off. Two taps.
- Compose settings shell: status hub + filename pattern / GPS / orientation / cache-age controls, persisted.
- Prefs wired into strip + rename + purge paths.