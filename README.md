# Cyber-shot Classic — Xperia 1 II

An unofficial, native Android Camera2 prototype inspired by the operating feel and visual language of Sony's early-2000s DSC-P compact cameras. It does **not** replace Sony Photography Pro.

## Included in v0.1

- Landscape shooting UI tuned for the Xperia 1 II's wide display.
- Centered 4:3 live viewfinder with focus reticle and small camera-status panel.
- Rear main camera, automatic exposure and autofocus, JPEG capture.
- Xperia physical shutter support when Android delivers `KEYCODE_FOCUS` (half press) and `KEYCODE_CAMERA` (full press) to the foreground app.
- Touch equivalents for focus and capture; optional shutter/focus sounds.
- Pictures saved in `Pictures/CyberShotClassic` using Android MediaStore, plus a playback shortcut.
- No ads, account, analytics, network access or external Android libraries.

## Open and build

1. Open this **CyberShotClassic** folder in Android Studio.
2. Use JDK 17 and Gradle 8.9 with Android Gradle Plugin 8.7.3 (already specified in the project). Install Android SDK Platform 35 if prompted.
3. Sync the project. Because the build environment that created this source had no Android SDK or Gradle distribution, a binary Gradle wrapper JAR and an APK are **not included**. If Android Studio requires a wrapper, run `gradle wrapper --gradle-version 8.9` with locally installed Gradle, then sync again.
4. Build **app** and install on the Xperia 1 II (Android 10 or newer); allow Camera permission.
5. In the app, half-press the phone's shutter to focus and full-press to shoot. If the hardware events are captured by Sony's system camera shortcut, use the two touch controls for now.

## Important limitations

- The Xperia 1 II's manufacturer-specific key dispatch behavior needs testing on a physical device. This prototype handles standard Android camera/focus key codes; it cannot force the OS to route them to this app.
- Camera2 preview and still capture must be verified on-device. No Android build or hardware run was possible in the source-generation environment.
- The camera output uses modern Xperia processing; **no nostalgic image filter, flash mode, optical zoom, DSC-P emulated menu or exact OEM startup sound is implemented yet**.
- The app uses the default rear camera and is locked to landscape. Display cutouts and OEM-specific orientation behavior may need a small layout adjustment.
- This is a clean-room homage, not an official Sony product or an emulation of Sony firmware.

## Project structure

`MainActivity.java` — UI, physical shutter key events, lifecycle, permission and sound.

`ClassicCamera.java` — Camera2 pipeline, autofocus request, JPEG capture and MediaStore writing.

`ViewfinderOverlay.java` — Minimal retro framing and focus reticle.

## Next steps after a first device test

1. Confirm physical shutter key codes with `adb logcat` on the Xperia 1 II and fix routing if needed.
2. Adjust preview transform on the actual camera sensor if landscape preview is rotated or stretched.
3. Add retro DSC-P menus and optional image profiles after the core shooting pipeline works.
