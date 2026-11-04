# EffectCraft for Android

This folder packages EffectCraft's existing browser/WASM editor in an offline Android WebView. It is not a separate native rewrite of the editing engine.

## Build

The GitHub Actions workflow at `.github/workflows/android-apk.yml` downloads the pinned upstream EffectCraft source, applies mobile viewport/touch defaults, builds the WASM app, bundles it into `android/app/src/main/assets/web`, and creates an installable debug-signed APK.

After a workflow run completes, open **Actions → Build Android APK → Artifacts → effectcraft-android-apk** and download `effectcraft-android-debug.apk`.

## Android behavior

- Landscape editor layout, hardware-accelerated WebView, system bars kept out of the editor area.
- The editor and all bundled web assets run locally from the APK; no remote editor site is required.
- File import opens Android's system document picker and supports multiple files.
- Exported Blob files are written in chunks to `Downloads/EffectCraft/`, avoiding a single huge base64 bridge payload.
- One frame worker is used by default; the service worker is disabled because assets are already packaged in the APK.
- The app uses Android System WebView's available graphics backend. WebGPU availability varies by device/WebView version; WebGL2 fallback is expected where WebGPU is unavailable.

## Performance expectations

EffectCraft is a full desktop-style motion-graphics editor, and its browser build is substantial. The APK wrapper reduces browser overhead and worker count, but it cannot make desktop-class effects or large 4K projects lightweight on every phone. Start with short clips, modest preview resolution and small projects. Close other memory-heavy apps before rendering.

## Requirements

- Android 10 (API 29) or newer.
- A reasonably current Android System WebView / Chrome component.
- Hardware-accelerated graphics; a device with WebGL2 support is recommended.

## Source and licensing

The editing engine and web app are built from the pinned upstream `storytold/effectcraft` commit `58ca9c1c1b163fdada41291847f3d0f7559339b2`. See the upstream `LICENSE-MIT`, `LICENSE-APACHE`, `NOTICE` and `ATTRIBUTION.md` files for their respective terms and asset credits.
