# CLAUDE.md

Project-level instructions for agents working on ExifDrop.

## What this is

Zero-UI Android share-sheet app. Picks up an image or PDF from any app, strips all metadata, renames it neutral, and hands a clean `content://` to the real destination. Fully offline. Apache 2.0.

## Ground rules

- **Images (JPEG/PNG/WebP):** tag-wipe via `androidx.exifinterface:1.4.2` — lossless, binary-segment. WebP XMP is not covered by the library and needs a hand-rolled chunk strip.
- **PDF:** own minimal metadata rewriter (trailer `/Info` + XMP stream). No `pdfbox-android` — unmaintained since 2023, CVE-flagged.
- **Trampoline:** one translucent activity, `noHistory` + `excludeFromRecents`, `exported="true"`. No layout, no visible screen.
- **FileProvider** for hand-off (`content://` + read grant). `EXTRA_EXCLUDE_COMPONENTS` so our own app isn't re-listed in sheet two.
- **Orientation** read before wipe, re-applied after.
- **Filename** scrubbed to `share_<hash>.<ext>`.
- **Offline:** no INTERNET permission, ever.

## Non-negotiables

Never re-encode. Never touch the source file. Never show UI in v0.1. Never add a stale/CVE'd dependency.

Stack: Kotlin, minSdk 24, targetSdk 36, no Compose, no DI, two dependencies.

## Process

Per the project gate: md skeleton first at root (this file, `AGENTS.md`, `STATE.md`, `CHANGELOG.md`, `session-handoff.md`, `README.md`, `docs/{PRD,DESIGN,ARCHITECTURE,HANDOFF}`, `memory/`), get go on docs, then code. Tests run locally in full. Build/test on the Realme GT7 over USB.