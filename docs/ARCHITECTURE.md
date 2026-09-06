# ARCHITECTURE — ExifDrop v0.1

Blue-thread structure of the repo. Small, two deps, no framework.

## Components

```
app/                          Android app module
  src/main/
    AndroidManifest.xml       share-target trampoline, FileProvider, exported=true
    java/dev/akashpriyadarshi/exifdrop/
      MainActivity.kt         trampoline: read→strip→rename→chooser→finish
      ExifDropApp.kt          Application: cache purge on cold start
      Strip/
        ImageStripper.kt      exifinterface tag-wipe + orientation guard
        WebpStripper.kt       hand-rolled XMP-chunk strip
        PdfStripper.kt        hand-rolled /Info + XMP stream rewrite
        Renamer.kt            share_<hash>.<ext>
  src/test/java/...           JUnit fixtures + assertions
docs/ md skeleton, this file
README.md, LICENSE (Apache-2.0), CLAUDE.md, AGENTS.md, STATE.md, CHANGELOG.md,
session-handoff.md, memory/
```

## Key flows

### Share → clean → destination (one tap)

1. Source app fires `ACTION_SEND` image/PDF.
2. System lists ExifDrop (registered target) → user taps.
3. Trampoline starts: read `Uri` via `ContentResolver`.
4. Strip per type (image vs PDF), rename neutral, write to cache.
5. `FileProvider` wraps `content://` + read grant.
6. `Intent.createChooser()` with `EXTRA_EXCLUDE_COMPONENTS` hides own target.
7. User picks destination → `finish()` immediately.

### Startup cache purge

`ExifDropApp.onCreate`: iterate cache dir, delete stripped files older than 24h; if total >64MB, delete oldest first.

## File-format handling map

| Format | Wipe method | XMP cleared? | ICC | Notes |
| --- | --- | --- | --- | --- |
| JPEG | exifinterface 1.4.2 | Yes (both APP1/seg and tag-700) | preserved | Standard |
| PNG | exifinterface | Yes (iTXt) | preserved | Standard |
| WebP | exifinterface + hand XMP chunk-strip | **Only via chunk strip** | preserved unless carries XMP chunk | Library can't see it |
| PDF | hand-rolled rewriter | Yes (catalog stream) | n/a | No pdfbox-android |

## Dependencies (locked, minimal)

- `androidx.exifinterface:1.4.2` — image tag-wipe.
- (test) JUnit.

Everything else: Kotlin stdlib, Android framework (Activity, ContentResolver, FileProvider from androidx.core).

## Security / permissions

- No INTERNET permission. Offline-only.
- Reads source via `ContentResolver` (user grant from share). Writes nothing outside app cache.
- `FileProvider` exposes only cache files, declared with `exported=false` content provider, grant-limited.

## Non-goals (deliberate, revisit later)

- Docs (docx/xlsx/pptx zip-strip), video. `mp4parser` dead upstream — never build on it.
- Play Store, root intercept, UI, batch/scan, remember-destination 1-tap.

## Thin-moat note

The wedge is one feature (share-sheet PDF + zero-UI). Scrambled Exif could add it in an afternoon. This architecture exists to ship v0.1 clean and fast — not to gold-plate.