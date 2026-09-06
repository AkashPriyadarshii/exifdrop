# session-handoff.md

## ExifDrop — handoff

Zero-UI Android share-sheet app. Strips metadata from images + PDF in one tap, passes a clean file to the real destination. Fully offline, Apache 2.0.

## Status

- Name locked: ExifDrop (clear everywhere, verified 2026-09-06).
- v0.1: md skeleton done. Awaiting code go.
- Two research subagents accepted: market + feasibility. All claims sourced. No open technical questions.

## Key decisions (do not silently reverse)

| Decision | Why |
| --- | --- |
| Share-sheet-native + lossless + zero-UI **PDF** | The only wedge with zero competitor. Images alone is crowded. |
| Hand-rolled PDF metadata rewriter, not pdfbox-android | pdfbox-android dead since 2023, CVE-flagged, F-Droid KnownVuln. |
| Hand-strip WebP XMP chunk | exifinterface never reads/writes WebP XMP — a plain wipe leaves it. |
| Keep ICC (except WebP-with-XMP) | Color data, not identity; widens output accuracy. |
| Trampoline + FileProvider + EXTRA_EXCLUDE_COMPONENTS | Zero-UI, content:// hand-off, own-app doesn't recurse in sheet two. |
| Orientation read-before, re-apply-after | Full wipe otherwise shares photos sideways. |
| Neutral filename `share_<hash>.<ext>` | Kills the date pattern in IMG_* names bytes-stripping alone misses. |
| offline-only, no INTERNET perm | The point. Nothing leaves the device. |
| minSdk 24 / targetSdk 36 | Breadth for FOSS users; API 36 matches Android 16 device, meets Play deadline. |

## Next actionable (under 2 min)

Say **"go"** → `gh search repos exifdrop` already done; scaffold desktop/exifdrop was underway — md skeleton files are written; add Apache-2.0 LICENSE, docs/{PRD,DESIGN,ARCHITECTURE,HANDOFF}, memory/, git init, then code behind the red-green gate.

## Thin-moat warning (carry forward)

Scrambled Exif is one PDF-lib + one intent-filter from closing this wedge. Ship fast; don't gold-plate v0.1.