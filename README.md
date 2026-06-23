# Vehicle Motion Cues — Android

A from-scratch Android recreation of Apple's **Vehicle Motion Cues** accessibility
feature (iOS 18), built to the spec in the project brief. Animated peripheral dots
overlay the screen and shift in real time with the vehicle's actual lateral and
longitudinal forces, reducing the sensory conflict that causes motion sickness for
passengers reading their phones in a moving vehicle.

> **Safety:** this is a passenger-only feature. Do not use it while operating a
> moving vehicle. A disclaimer to that effect is shown on first launch.

## What's implemented (all 5 layers from the brief)

| Layer | File(s) | What it does |
|-------|---------|--------------|
| **1 — Context Gate** | `gate/ActivityRecognitionProvider.kt`, `gate/ContextGate.kt` | Google Play Services Activity Recognition (`IN_VEHICLE`) fused with motion-signal statistics. Entry/exit grace periods mirror Apple's "doesn't snap off instantly" behavior. |
| **2 — Motion Pipeline** | `motion/MotionPipeline.kt`, `motion/VehicleFrame.kt`, `motion/LowPassFilter.kt` | `TYPE_LINEAR_ACCELERATION` + `TYPE_GAME_ROTATION_VECTOR` at `SENSOR_DELAY_GAME`. Reference-frame transform (device→world→vehicle), EMA low-pass filter, outputs lateral + longitudinal scalars. |
| **3 — Overlay Renderer** | `overlay/OverlayService.kt`, `overlay/DotOverlayView.kt` | Foreground Service + `WindowManager` `TYPE_APPLICATION_OVERLAY` (touch-passthrough via `FLAG_NOT_TOUCHABLE`). Custom `View` draws the peripheral dot field, driven by `Choreographer`, with per-dot lerp for glide. |
| **4 — Settings UI** | `ui/SettingsScreen.kt`, `tile/VmcTileService.kt` | Jetpack Compose: 3-way mode (Off/On/Automatic), pattern, color, Larger/More Dots, Auto-Contrast, sensitivity & smoothing. Quick Settings tile for one-tap toggle. |
| **5 — Safety Guardrails** | `ui/SettingsScreen.kt` (disclaimer dialog) | Passenger-only disclaimer on first enable. |

## Tech stack

- **Kotlin 2.0.20**, **AGP 8.5.2**, **Jetpack Compose** (BOM 2024.09)
- **DataStore Preferences** for settings persistence (exposed as a hot `StateFlow`)
- **Coroutines + Flow** for reactive pipeline
- **Google Play Services Location** (Activity Recognition API)
- `minSdk 26` (Android 8.0) · `targetSdk/compileSdk 34`

## Build & install

> **See [SETUP.md](SETUP.md) for complete, step-by-step instructions** —
> prerequisites, Android Studio + CLI paths, device setup, first-run
> permissions, and troubleshooting.

**Quick start (Android Studio):**

1. **File → Open** → select this `vmc-android` folder.
2. Let Gradle sync (first run downloads Gradle 8.9 + deps, ~3–10 min).
3. Connect a phone (API 26+) with USB debugging, or start an emulator.
4. Press **Run** ▶.

**Quick start (CLI):**

```bash
cd vmc-android
cp local.properties.example local.properties   # edit sdk.dir
./gradlew assembleDebug                        # or: gradlew.bat on Windows
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

## First-run permissions

The app will walk you through three grants (Android users are rightly wary of
these, so each is paired with a plain-language reason in the UI):

1. **Display over other apps** (`SYSTEM_ALERT_WINDOW`) — required to draw the
   dot overlay on top of other apps. Opens the system Settings page.
2. **Activity Recognition** (`ACTIVITY_RECOGNITION`, Android 10+) — required
   for Automatic mode to detect when you're in a vehicle.
3. **Notifications** (`POST_NOTIFICATIONS`, Android 13+) — required for the
   foreground-service notification that keeps the pipeline alive.
4. **Battery optimization exemption** (recommended) — so OEM battery settings
   (Samsung, Xiaomi, etc.) don't silently kill the background service.

## Using it

1. Open the app, acknowledge the safety disclaimer.
2. Grant the overlay + notification permissions.
3. Pick **On** to always show dots, or **Automatic** to show them only when the
   context gate detects you're in a moving vehicle.
4. Press **Start** (or add the **Motion Cues** Quick Settings tile from the
   shade's edit mode for one-tap toggle).
5. Hold the phone upright, top toward the front of the vehicle, and read as
   normal. The dots around the screen edges will shift with cornering and
   braking.

## Architecture notes / engineering judgment calls

The brief explicitly flags (Section 7) that several parameters are undocumented
by Apple and must be chosen from scratch. Choices made here:

- **g-force → pixel mapping**: clamped linear, 18 px per m/s², 0.15 m/s²
  deadzone, ±120 px clamp, user-tunable `sensitivity` multiplier.
- **Smoothing**: EMA with user-tunable coefficient (0–0.95).
- **Reference-frame transform**: full quaternion rotation (device→world) then
  horizontal projection of the phone's forward/right vectors onto the vehicle
  frame. Assumes the user is seated facing forward holding the phone upright —
  the documented best-case orientation (brief Section 3.6).
- **Context gate thresholds**: ActivityRecognition confidence ≥ 60 (corroborated
  with motion stats), ~4 s entry confirmation, ~30 s exit grace.
- **Auto-contrast**: true per-pixel difference blending against underlying
  windows isn't exposed by the Android `WindowManager` API (Apple does this at
  the OS compositor level). We use a dual-tone halo (dark backing circle behind
  the colored dot) that stays visible on both light and dark backgrounds.
- **Dot layout**: 5 dots per edge (9 with "More Dots"), 35% center-exclusion
  band so dots hug the periphery and never occlude central content.

These all need real-world in-car testing to tune — the same way Apple's
accessibility team presumably did.

## Project structure

```
vmc-android/
├── .editorconfig                          # code style (ktlint-compatible)
├── .gitignore
├── README.md                              # this file
├── SETUP.md                               # ← complete step-by-step setup guide
├── local.properties.example               # copy to local.properties, set sdk.dir
├── gradlew                                # Gradle wrapper (macOS/Linux)
├── gradlew.bat                            # Gradle wrapper (Windows)
├── settings.gradle.kts
├── build.gradle.kts                       # root build
├── gradle.properties
├── gradle/
│   ├── libs.versions.toml                 # version catalog
│   └── wrapper/
│       ├── gradle-wrapper.jar             # binary wrapper (included!)
│       └── gradle-wrapper.properties      # points at Gradle 8.9
└── app/
    ├── build.gradle.kts                   # app build
    ├── proguard-rules.pro
    └── src/main/
        ├── AndroidManifest.xml
        ├── java/com/zai/vmccues/
        │   ├── VmcApplication.kt          # app-wide singletons
        │   ├── MainActivity.kt            # Compose host
        │   ├── data/                       # enums, CueSettings, DataStore repo
        │   ├── gate/                       # ActivityRecognition + ContextGate
        │   ├── motion/                     # pipeline, transform, filter
        │   ├── overlay/                    # foreground Service + DotOverlayView
        │   ├── tile/                       # Quick Settings tile
        │   └── ui/                         # Compose settings + permissions + theme
        └── res/                            # strings, themes, colors, icons, xml
```

> The Gradle wrapper binary (`gradle-wrapper.jar`) is **included**, so
> `./gradlew` works out of the box — no system Gradle install needed.

## Caveats

- Google's Activity Recognition accuracy on motorized-vehicle detection is
  mediocre out of the box (the brief cites academic findings of ~3% confident
  windows). We treat it as a **coarse gate** corroborated by the motion-signal
  statistics, not as ground truth.
- Real per-pixel difference blending against underlying apps is not achievable
  via public Android APIs; see the auto-contrast note above.
- Untested on a physical device in this sandbox — the parameters above are
  reasonable starting points, not tuned values. Tune `sensitivity`/`smoothing`
  in the UI and the constants in `VehicleFrame.kt` / `DotOverlayView.kt`
  based on real in-car testing.
