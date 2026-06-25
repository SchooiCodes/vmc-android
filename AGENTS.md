# AGENTS.md — Vehicle Motion Cues (Android)

## Project Overview

Recreation of Apple's Vehicle Motion Cues accessibility feature (iOS 18) for Android. Animated peripheral dots overlay the screen and shift with vehicle forces to reduce motion sickness.

## Architecture

Five layers mirroring iOS specification:

| Layer | Files | Role |
|-------|-------|------|
| Context Gate | `gate/ContextGate.kt`, `gate/ActivityRecognitionProvider.kt` | Detect moving vehicle |
| Motion Pipeline | `motion/MotionPipeline.kt`, `motion/VehicleFrame.kt`, `motion/DeadReckoningIntegrator.kt`, `motion/ForceVector.kt` | Sensor → vehicle-frame forces |
| Overlay Renderer | `overlay/OverlayService.kt`, `overlay/DotOverlayView.kt`, `overlay/ScreenColorSampler.kt` | Dot rendering on screen |
| Settings UI | `ui/SettingsScreen.kt`, `ui/CarSimulationScreen.kt`, `ui/components/*` | Configuration |
| Safety Guardrails | Built into SettingsScreen | Passenger-only warnings |

## Key Files

- `MainActivity.kt` — Bottom nav host (Drive / Settings tabs)
- `data/CueSettings.kt` — Settings data class + DataStore serialization
- `data/Enums.kt` — ActivationMode, DotPattern, DotColor, DotSize
- `data/SettingsRepository.kt` — DataStore persistence
- `overlay/DotOverlayView.kt` — Core dot rendering (Choreographer-driven)
- `motion/MotionPipeline.kt` — Sensor processing + force output
- `ui/components/PreviewUtilities.kt` — Shared dot layout/rendering helpers

## Build & Test

```bash
./gradlew assembleDebug    # build debug APK
./gradlew test             # run 83 unit tests
./gradlew lint             # lint check
```

## Code Style

- Kotlin with Jetpack Compose
- Minimal comments
- ktlint-compatible formatting
- .editorconfig enforced

## Current Version

v1.5.0 — Car simulation, simplified UI, performance optimizations, 83 unit tests.
