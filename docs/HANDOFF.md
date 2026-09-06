# HANDOFF — ExifDrop

Handoff notes for the next session/agent picking this up. Everything needed to continue without re-deriving.

## Right now

- **Status:** v0.1 md skeleton mostly written. Awaiting: Apache-2.0 LICENSE, `memory/` files, git init, then code on user's go.
- **Repo dir:** `Desktop/exifdrop`. Not yet a git repo.
- **Name:** ExifDrop (clear everywhere, verified).

## To continue (exact next steps)

1. `gh search repos exifdrop` done → name free. Do not re-run.
2. Add `LICENSE` (Apache-2.0, author Akash Priyadarshi), `memory/*.md` per the skeleton pattern.
3. `git init` at `Desktop/exifdrop`, branch `main`.
4. On user's **"go"**: Gradle scaffold (Kotlin DSL, version catalog) → manifest (trampoline, `image/*`+`application/pdf`, `exported="true"`, FileProvider) → strippers (image + WebP chunk + PDF) → trampoline activity → cache purge → JUnit fixtures → build/test on Realme GT7 over USB.

## Decisions that are locked (don't silently reverse)

See `session-handoff.md` — the full table. Short version:
- Wedge = share-sheet-native + lossless + zero-UI **PDF**. Images alone is crowded.
- No `pdfbox-android` (dead, CVE-flagged) → hand-rolled PDF rewriter.
- No exifinterface for WebP XMP (invisible to it) → hand-rolled chunk-strip.
- Keep ICC except WebP-with-XMP.
- Trampoline + FileProvider + `EXTRA_EXCLUDE_COMPONENTS` (own-app doesn't recurse).
- Orientation read-before / re-apply-after.
- Neutral filename `share_<hash>.<ext>`.
- No INTERNET permission, offline-only.
- minSdk 24 / targetSdk 36, no Compose/DI, 2 deps.

## Open questions / risk queue

| Item | Status |
| --- | --- |
| Dual-segment XMP (tag-700 + separate) | Accept in v0.1, verify via exiftool fixtures. |
| Orientation UNDEFINED default when absent | Accept, document. |
| Large scanned PDF rewrite latency | ~100–300ms, no UI to freeze. Spinner later if real files prove slow. |
| Thin moat (Scrambled Exif + PDF-lib + intent-filter) | Ship fast. Don't gold-plate. |

## Context you'll want

- Research accepted: `docs/PRD.md` (product), `docs/DESIGN.md` (engineering), `docs/ARCHITECTURE.md` (structure). All claims sourced from two independent research subagents (market + feasibility), 2026-09-06.
- Device: Realme GT7, Android 16 (API 36), arm64. Host Windows 8GB — no emulator, test over USB.
- F-Droid path: trusted Maven repos only, no CVE-flagged deps (hand-rolled PDF avoids `KnownVuln`).

## Anti-slop reminder

Prose for README/PR/CHANGELOG/etc. passes the humanizer → stop-slop bar before shipping. Commit subjects imperative, conventional-commits type.