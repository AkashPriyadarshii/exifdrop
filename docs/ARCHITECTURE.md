# ARCHITECTURE — ExifDrop v0.1

Structure of the repo. Lightweight, two dependencies, no DI framework.

## Components

```
app/                                Android app module
  src/main/
    AndroidManifest.xml             TrampolineActivity, FilenameTileService, FileProvider
    java/dev/akashpriyadarshi/exifdrop/
      TrampolineActivity.kt         Zero-UI share trampoline: read → strip → rename → chooser → finish
      MainActivity.kt               Companion shell: status hub + settings + Live Sanitizer Receipt
      FilenameTileService.kt        Quick Settings Tile for instant filename scramble/keep toggle
      ExifDropApp.kt                Application: async cache purge on cold start
      Prefs.kt                      SharedPreferences persistence (pattern, GPS, orientation, cache age)
      strip/
        ImageStripper.kt            exifinterface tag-wipe + orientation guard (48 EXIF tags)
        WebpStripper.kt             hand-rolled XMP chunk strip + 26-byte TIFF orientation injection
        PdfStripper.kt              hand-rolled /Info + XMP stream rewrite
        Renamer.kt                  share_<hash>.<ext> or source stem preservation
      ui/theme/
        Theme.kt                    Avant-Garde Editorial Brutalism palette & typography
  src/test/java/...                 JUnit test suite: StripperTest (15 test fixtures)
docs/
  PRD.md, DESIGN.md, ARCHITECTURE.md, HANDOFF.md
README.md, LICENSE (Apache-2.0), CLAUDE.md, AGENTS.md, STATE.md, CHANGELOG.md
```

## Key flows

### 1. Primary Share-Sheet Flow (Zero-UI, Two Taps)

1. Source app fires `ACTION_SEND` or `ACTION_SEND_MULTIPLE` with image or PDF URIs.
2. System presents ExifDrop in the share sheet → user taps.
3. `TrampolineActivity` opens translucent cover with branded spinner.
4. Background `Executor` reads URIs via `ContentResolver`, strips metadata in-memory, writes `share_<hash>.<ext>` to cache.
5. System haptic tick (`HapticFeedbackConstants.CONFIRM`) and scrub Toast ("Cleaned N files · metadata stripped") fire.
6. `FileProvider` wraps `content://` + read permission grant.
7. `Intent.createChooser()` with `EXTRA_EXCLUDE_COMPONENTS` hides ExifDrop from second sheet.
8. User selects destination app → `TrampolineActivity.finish()` immediately.

### 2. In-App Pre-Flight Inspection (Sanitizer Receipt)

1. User launches ExifDrop from home screen (`MainActivity`).
2. Taps "Inspect & strip files" → picks images or PDFs via system document picker.
3. Background coroutine runs `inspectUris(context, uris)` reading EXIF and PDF streams.
4. `SanitizerReceiptSheet` opens as a modal bottom sheet displaying detected metadata (GPS in red, camera, lens, timestamps) with `PURGE` tags.
5. User reviews and taps "STRIP ALL & SHARE" → hands URIs to `TrampolineActivity`.

### 3. Quick Settings Tile

1. User pulls down the Android quick settings shade.
2. Taps the "ExifDrop: Scramble" tile.
3. `FilenameTileService` flips `Prefs.filenamePattern` between `neutral` (`share_<hash>`) and `original` (`keep name`), updating the tile icon and label dynamically without opening the app.

### 4. Startup Cache Purge

`ExifDropApp.onCreate`: spawns single-thread background executor to iterate `cache/cleaned`, deleting files older than configured days (`cache_age_days`, default 1) or oldest first if total size exceeds 64MB.

## File-format handling map

| Format | Wipe method | XMP cleared? | Orientation | Notes |
| --- | --- | --- | --- | --- |
| JPEG | exifinterface 1.4.2 | Yes (both APP1/seg and tag-700) | Preserved | Lossless binary tag wipe |
| PNG | exifinterface | Yes (iTXt) | Preserved | Lossless binary tag wipe |
| WebP | exifinterface + hand XMP chunk-strip | **Yes (via chunk strip)** | Preserved (26-byte TIFF) | Library can't see WebP XMP |
| PDF | hand-rolled rewriter | Yes (catalog stream) | n/a | Neutralizes trailer `/Info` |

## Dependencies (locked, minimal)

- `androidx.exifinterface:1.4.2` — image tag-wipe.
- Compose libraries (M3 1.3.0, BOM 2024.10.01) — companion shell only.
- JUnit 4 — automated test suite.
- Everything else: Kotlin stdlib, Android platform SDK.

## Security / permissions

- No `android.permission.INTERNET`. 100% offline.
- Source files read-only via `ContentResolver`. Never modified in place.
- `FileProvider` declared `exported=false`, grant-limited.
- Zero tracking, zero telemetry, zero analytics.