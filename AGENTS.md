# AGENTS.md

Agent instructions for ExifDrop. Repo is Apache 2.0, FOSS-first, offline-only.

## Architecture

Greenfield Android app, Kotlin, no Compose, no DI. Two dependencies total.

| Layer | Tool | Note |
| --- | --- | --- |
| Image wipe | `androidx.exifinterface:1.4.2` | Lossless tag-wipe. WebP XMP needs a hand-stripped chunk — the library never reads/writes it. |
| PDF wipe | Hand-rolled | Trailer `/Info` + XMP stream rewrite. NOT `pdfbox-android` (dead since 2023, CVE-flagged, F-Droid KnownVuln). |
| Trampoline | Manifest activity | `Theme.Translucent.NoTitleBar`, `noHistory`, `excludeFromRecents`, `exported="true"`. |
| Hand-off | FileProvider | `content://` + `FLAG_GRANT_READ_URI_PERMISSION`. |
| Anti-recursion | `EXTRA_EXCLUDE_COMPONENTS` | Own app hidden from the second share sheet. |

## Hard rules

- **Share-sheet native, images + PDF only.** `image/*` and `application/pdf` intent-filter. No `*/*`, no videos, no office docs in v0.1.
- **Lossless or nothing.** Never re-encode pixels. Tag-wipe + chunk-strip only.
- **Fully offline.** No INTERNET permission, no network calls, ever.
- **Orientation guard.** Read `TAG_ORIENTATION` before wipe, re-apply after.
- **Neutral filenames.** Outgoing file is `share_<hash>.<ext>`, never the source name.
- **Cache purge.** In `Application.onCreate`, delete stripped files >24h old or >64MB total.
- **Zero UI.** The trampoline inflates no layout, shows nothing, can't be launched standalone in v0.1.
- **No dead deps.** Anything stale or CVE-flagged gets hand-rolled before it ships.

## Workflow

1. md files live at repo root with code: `CLAUDE.md`, `STATE.md`, `CHANGELOG.md`, `session-handoff.md`, `README.md`.
2. Docs under `docs/{PRD,DESIGN,ARCHITECTURE,HANDOFF}`, memory under `memory/`.
3. Code behind tests locally. Test on the Realme GT7 over USB — the host is an 8GB Windows box, no emulator.

## Testing

Local, full suite, on every change. JUnit. Fixtures: JPEG/PNG/WebP with GPS + XMP + orientation, a PDF with /Info. Assert GPS/XMP/ICC/stamps gone, orientation preserved, filename neutral, PDF metadata cleared.