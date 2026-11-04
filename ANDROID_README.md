# EffectCraft for Android

An Android WebView shell for EffectCraft's existing WASM editor. The first GitHub Actions build imports the pinned upstream source into this repository, then builds an offline, installable APK. Later builds use the source files in this repository, so changes to the editor are included.

## Build an APK

1. Open **Actions** in this repository and select **Build Android APK** (or push a commit to `main`).
2. Wait for the workflow to finish successfully.
3. Open its run → **Artifacts** → `effectcraft-android-apk`.
4. Download and install `effectcraft-android-debug.apk` on Android 10 or newer.

The workflow builds the web/WASM editor, bundles it inside the APK, and signs the debug build automatically. No personal access token or signing secret is needed.

## Android adaptations

- Landscape orientation and touch-friendly canvas behavior.
- Locally bundled WASM and WebView assets; the editor itself works offline.
- Android document picker for importing multiple media/project files.
- Chunked native export bridge that saves browser Blob exports to `Downloads/EffectCraft/`.
- Single frame worker and no service-worker cache to reduce duplicate memory use in the APK.
- Hardware-accelerated WebView with WebGL2 fallback where WebGPU is unavailable.

## Requirements and limitations

- Android 10 (API 29) or newer, with a current Android System WebView.
- This remains a desktop-style motion-graphics editor running on a phone. Small screens and large timelines/effects can be difficult to operate; big 4K projects can exceed a phone's RAM/GPU budget. Start with small projects and short clips.
- WebGPU support depends on the phone and installed WebView. WebGL2 fallback is used where available; a device without a compatible graphics backend may not render correctly.

## Upstream licensing

The editor is built from `storytold/effectcraft`, pinned in `UPSTREAM_COMMIT`. Review upstream `LICENSE-MIT`, `LICENSE-APACHE`, `NOTICE` and `ATTRIBUTION.md` for code and bundled asset terms.
