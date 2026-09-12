# CLAUDE.md

Project-level instructions for agents working on ExifDrop.

## What this is

Android share-sheet metadata stripper. Picks up an image or PDF from any app, strips all metadata, and hands a clean `content://` to the real destination. The share trampoline is the primary entry (zero-UI, two taps); a thin Jetpack Compose companion shell shows status, hosts settings, and offers a live Sanitizer Receipt sheet for inspecting files picked in-app. Fully offline. Apache 2.0.

## Ground rules

- **Images (JPEG/PNG/WebP):** tag-wipe via `androidx.exifinterface:1.4.2` — lossless, binary-segment. WebP XMP is not covered by the library and uses a hand-rolled chunk strip with a clean 26-byte TIFF orientation injection.
- **PDF:** own minimal metadata rewriter (trailer `/Info` + XMP stream). No `pdfbox-android` — unmaintained since 2023, CVE-flagged.
- **Trampoline:** translucent `TrampolineActivity`, `noHistory` + `excludeFromRecents`, `exported="true"`, `launchMode="standard"`. Full-screen cover with branded spinner while stripping on background executor. Emits haptic tick and scrub confirmation Toast before destination chooser handoff.
- **Compose shell:** `MainActivity` = status hub + settings + live Sanitizer Receipt bottom sheet for pre-flight metadata inspection.
- **Quick Settings Tile:** `FilenameTileService` toggles between `share_<hash>` scrambling and keeping original filenames directly from the Android quick settings shade.
- **Settings:** persisted in SharedPreferences (`dev.akashpriyadarshi.exifdrop.Prefs`). Filename pattern, GPS strip toggle, keep-orientation, cache age. Defaults are privacy-first (neutral names, GPS stripped, orientation kept).
- **FileProvider** for hand-off (`content://` + read grant). `EXTRA_EXCLUDE_COMPONENTS` so our own app isn't re-listed in sheet two.
- **Orientation** read before wipe, re-applied after (unless the setting is off).
- **Filenames** default to `share_<hash>.<ext>`; "keep name" opt-in preserves the source stem.
- **Offline:** no INTERNET permission, ever.

## Non-negotiables

Never re-encode. Never touch the source file. Never add a stale/CVE'd dependency. Old share workflow must remain zero-UI and 100% functional.

Stack: Kotlin, minSdk 24, targetSdk 36, Jetpack Compose (shell only), no DI.

## Process

Run local test suite: `.\gradlew.bat test` (15 unit tests covering JPEG, PNG, WebP, PDF). Build/test on the test device over USB.

## Release / asset update (v0.1) — do this, never debug

The GitHub release asset `exifdrop-v0.1-signed.apk` is a **release build signed with the local debug keystore**. There is no private release key. Its cert (SHA-256 `2704efdf07ac4da492c764b353233cf943efca5975625a839ebe207ffc5fbbe9`) is the identity your installed app must keep — a different cert breaks install-update.

Every asset update must:
1. Build: `.\gradlew.bat :app:assembleRelease` (R8 + shrink; output `app/build/outputs/apk/release/app-release-unsigned.apk`).
2. Sign with the debug key — this IS the "signed" APK:
   ```
   zipalign -p -f 4 app/build/outputs/apk/release/app-release-unsigned.apk <tmp-aligned>
   apksigner sign --ks <debug-keystore> --ks-pass pass:android --ks-key-alias androiddebugkey --out exifdrop-v0.1-signed.apk <tmp-aligned>
   ```
3. Verify the cert matches the known digest before touching GitHub: `apksigner.bat verify --print-certs exifdrop-v0.1-signed.apk` → SHA-256 must equal the value above.
4. Upload (replaces the existing asset): `gh release upload v0.1 --repo AkashPriyadarshii/exifdrop --clobber exifdrop-v0.1-signed.apk`.

Never ship `app/build/outputs/apk/debug/app-debug.apk` as a release asset. `*.apk` is gitignored; the signed file stays local-only until pushed via `gh release upload`. Push code changes to `main` separately.