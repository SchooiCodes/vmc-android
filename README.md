# Vehicle Motion Cues — Android

A faithful recreation of Apple's **Vehicle Motion Cues** accessibility feature (iOS 18) for Android. Animated peripheral dots overlay the screen and shift in real time with the vehicle's lateral and longitudinal forces, reducing the sensory conflict that causes motion sickness.

## Features

- **Car Simulation** — interactive top-down car with drag-to-steer/accelerate; peripheral dots respond to simulated vehicle forces in real-time
- **Real Sensor Pipeline** — linear acceleration + rotation vector → vehicle frame → dead-reckoning integrator → dot displacement
- **Three Activation Modes** — Off / On / Automatic (context-aware via Activity Recognition)
- **Quick Settings Tile** — one-tap toggle from notification shade
- **Simplified UI** — bottom navigation (Drive / Settings), collapsed advanced settings
- **iOS-Faithful Rendering** — 6-10 dots per edge, 35% center exclusion, auto-contrast rings, smooth deadzone

## Quick Start

1. Clone and open in Android Studio
2. Let Gradle sync (~3-10 min first time)
3. Connect a phone (API 26+) with USB debugging
4. Press **Run**
5. Grant permissions (overlay, activity recognition, notifications)
6. Switch to **Drive** tab and interact with the car, or enable real motion in **Settings**

## Build

```bash
./gradlew assembleDebug    # debug APK
./gradlew assembleRelease  # release APK
./gradlew test             # unit tests
./gradlew lint             # lint check
```

## Project Structure

```
app/src/main/java/com/zai/vmccues/
├── MainActivity.kt           # Bottom nav host (Drive / Settings)
├── VmcApplication.kt         # App singletons
├── data/                     # CueSettings, enums, DataStore repo
├── gate/                     # ActivityRecognition + ContextGate
├── motion/                   # MotionPipeline, VehicleFrame, DeadReckoningIntegrator, ForceVector
├── overlay/                  # OverlayService, DotOverlayView, ScreenColorSampler
├── tile/                     # Quick Settings tile
└── ui/
    ├── CarSimulationScreen.kt    # Interactive car simulation
    ├── SettingsScreen.kt         # Simplified settings
    └── components/               # IosSwitch, IosSlider, IosSegmentedControl, LivePreview, etc.
```

## Architecture

Five-layer design mirroring Apple's iOS specification:

| Layer | Component | Role |
|-------|-----------|------|
| 1 | Context Gate | Detects when the user is in a moving vehicle |
| 2 | Motion Pipeline | Transforms sensor data into vehicle-frame forces |
| 3 | Overlay Renderer | Displays peripheral dots on top of other apps |
| 4 | Settings UI | Jetpack Compose configuration (mode, appearance, sensitivity) |
| 5 | Safety Guardrails | Passenger-only usage warnings |

## Testing

83 unit tests covering the core motion pipeline, data models, and settings logic.

## License

Educational recreation of Apple's Vehicle Motion Cues (iOS 18). Original implementation is proprietary to Apple Inc.
