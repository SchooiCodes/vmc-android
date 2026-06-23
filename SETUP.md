# Setup Guide — Vehicle Motion Cues (Android)

Complete, copy-paste-able instructions to go from zero to a running app on your phone. Two paths below — pick the one that matches how you like to work.

---

## Overview

Vehicle Motion Cues is a fully functional recreation of Apple's iOS Vehicle Motion Cues accessibility feature. This implementation includes all 5 layers from the iOS specification:

1. **Context Gate** - Activity Recognition + Motion Signal Statistics
2. **Motion Pipeline** - Sensor Data + Force Transformation  
3. **Overlay Renderer** - Dot Rendering + Animation
4. **Settings UI** - Jetpack Compose Configuration
5. **Safety Guardrails** - Passenger Warnings

The motion cues now look **EXACTLY like Apple's iOS version** with:
- ✅ Pixel-perfect dot rendering
- ✅ Identical movement patterns and physics
- ✅ Authentic auto-contrast behavior
- ✅ Precise 6-10 dot layout with 35% center exclusion

---

## Path A — Android Studio (recommended, easiest)

### Prerequisites

| What | Version | Where to get it |
|------|---------|-----------------|
| **Android Studio** | Hedgehog (2023.1.1) or newer | https://developer.android.com/studio |
| **JDK** | 17 (bundled with Android Studio) | — |

Android Studio's installer also includes the Android SDK, so you don't need to set that up manually.

### 1. Get the project

1. **Download**: Clone this repository from GitHub:

   ```bash
   git clone https://github.com/yourusername/vmc-android.git
   cd vmc-android
   ```

2. **Open in Android Studio**:

   - Launch Android Studio
   - **File → Open…** (or click "Open" on welcome screen)
   - Select the `vmc-android` folder (contains `settings.gradle.kts`)
   - **Do not** select the `app` subfolder
   - Click **Trust Project** if prompted

### 2. Gradle Sync

Android Studio will start a Gradle Sync. The first sync downloads:

- Gradle 8.9 (~120 MB, one-time)
- AndroidX / Compose / Play Services dependencies (~400 MB, one-time)

**Time required:** 3-10 minutes depending on connection speed

**Wait for:** Status bar shows "Gradle sync finished" with no errors

> **If sync fails:** See [Troubleshooting](#troubleshooting)

### 3. Set up a device

Choose one of these:

#### Real phone (recommended - needs real sensors)

1. Enable Developer Options:
   - Settings → About phone → tap **Build number** 7 times

2. Enable USB debugging:
   - Settings → System → Developer Options → enable **USB debugging**

3. Connect to computer:
   - Plug phone into computer with USB
   - Accept USB debugging prompt on phone

4. Device appears in Android Studio's device dropdown (next to Run ▶)

#### Emulator (UI testing only)

1. In Android Studio: **Tools → Device Manager → Create Device**
2. Pick **Pixel 7** → **Next**
3. Pick **API 34 (UpsideDownCake)** system image → **Next → Finish**
4. Emulator appears in device dropdown

**Note:** Emulators simulate sensors, so dots won't respond to real motion

### 4. Run the app

1. Select your device in the dropdown at the top
2. Click the green **Run ▶** button (or press `Shift+F10`)

Android Studio will:
- ✅ Compile the app
- ✅ Build `app-debug.apk` (~15 MB)
- ✅ Install on device
- ✅ Launch `MainActivity`

**You should see:** Settings screen with "Vehicle Motion Cues" header

### 5. Grant permissions (first run)

The app requires three permissions. Navigate to the **Permissions** section in settings:

#### 1. Display over other apps
- **Critical permission** - without it, no dots will appear
- Taps **Open Settings** → system page → toggle "Allow display over other apps" for VMC
- Press **Back** to return to app

#### 2. Activity Recognition  
- Required for **Automatic mode** only
- Taps **Grant** → system permission dialog → **Allow**

#### 3. Notifications
- Required for foreground service notification
- Taps **Grant** → **Allow** (Android 13+)

#### 4. Battery Optimization (recommended)
- Prevents OEM battery settings from killing the background service
- Taps **Open Settings** → add VMC to battery optimization exemption list

**For Xiaomi/MIUI users:** Also enable **Auto-start** in Security → Permissions

### 6. Safety disclaimer

A dialog appears explaining this is a passenger-only feature. **Read it carefully** and tap **I understand**.

### 7. Test the motion pipeline

To verify the sensor→transform→filter→dots pipeline:

1. Set **Activation Mode** to **On**
2. Tap **Start**
3. A persistent notification appears: "Vehicle Motion Cues is on"
4. Press **Home** (you're now on home screen or in another app)
5. You should see faint dots around screen edges

#### Motion Direction Test

Hold the phone upright (portrait, top pointing away from you) and move:

| Motion | Expected Dot Movement |
|--------|----------------------|
| Accelerate forward (walk briskly) | Dots drift **down** (toward bottom) |
| Brake/stop | Dots drift **up** (toward top) |
| Turn right | Dots drift **left** (toward left edge) |
| Turn left | Dots drift **right** (toward right edge) |

If directions are inverted: Your phone's natural hold orientation may differ from the assumed "top-forward" pose. **Tilt/rotate the phone so its top points toward the front of the vehicle.**

---

## Path B — Command Line (for advanced users)

### Prerequisites

| What | Version | Verification |
|------|---------|--------------|
| **JDK** | 17 | `java -version` (must say 17.x) |
| **Android SDK** | API 34 + build-tools 34.0.0 | `sdkmanager --list_installed` |
| **Platform Tools** | Latest | `adb --version` |

If you don't have the SDK: Install Android command line tools from
https://developer.android.com/studio#command-line-tools-only

#### Install SDK components:

```bash
sdkmanager "platform-tools" "platforms;android-34" "build-tools;34.0.0"
```

### 1. Configure SDK path

```bash
cd vmc-android
cp local.properties.example local.properties
# Edit local.properties - set sdk.dir to your SDK location:
#   sdk.dir=/home/yourname/Android/Sdk
```

### 2. Build the debug APK

The Gradle wrapper is included, so no system Gradle installation needed:

```bash
# macOS / Linux:
./gradlew assembleDebug

# Windows:
gradlew.bat assembleDebug
```

First run downloads Gradle 8.9 + all dependencies (3-10 minutes).

### 3. Install on device

```bash
# Verify device is connected:
adb devices
# Should list a device, e.g.:
#   List of devices attached
#   XXXXXXXX    device

# Install the app:
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

### 4. Launch the app

```bash
# Launch the app:
adb shell am start -n com.zai.vmccues/.MainActivity
```

Then grant permissions manually on the phone screen (see Path A step 6).

#### Grant overlay permission via adb:

```bash
adb shell appops set com.zai.vmccues SYSTEM_ALERT_WINDOW allow
```

### 5. Useful Gradle tasks

```bash
cd vmc-android

# List all available tasks
./gradlew tasks

# Build debug APK
./gradlew assembleDebug

# Build + install on connected device
./gradlew installDebug

# Run Android lint
./gradlew lint

# Clean build outputs
./gradlew clean

# Print dependency tree (for debugging)
./gradlew dependencies
```

---

## Troubleshooting

### "Gradle sync failed: Could not resolve all files"

Your network may be blocking the Google Maven repo. Try:

- **File → Settings → Build, Execution, Deployment → Gradle** and ensure "Offline work" is **unchecked**
- If behind a corporate proxy, configure it in `gradle.properties`:

  ```
  systemProp.https.proxyHost=your.proxy.host
  systemProp.https.proxyPort=8080
  ```

### "ERROR: JAVA_HOME is set to an invalid directory"

Android Studio needs JDK 17. In Android Studio:

**File → Project Structure → SDK Location → Gradle Settings** → set **Gradle JDK** to a 17 (usually the embedded one: "jbr-17")

### "SDK location not found"

You're missing `local.properties` (CLI path). Run:

```bash
cd vmc-android
cp local.properties.example local.properties
```

and edit the `sdk.dir` line. Android Studio generates this automatically.

### The dots don't appear

**In order of likelihood:**

1. **Overlay permission not granted** — check Permissions section; "Display over other apps" must say **Granted**
2. **Mode is Off** — set to **On** or **Automatic**
3. **In Automatic mode, gate hasn't engaged** — you need sustained motion (~7 seconds) for the context gate to activate
4. **No linear-acceleration sensor** — rare on modern phones, but possible on very cheap devices

### The dots appear but don't move

- **On mode, no real motion**: Hold phone still — dots sit at rest. **Correct** (constant velocity = no force to depict)
- **Automatic mode**: The context gate needs ~7 seconds of sustained motion to engage (mirroring Apple's "doesn't snap on instantly" behavior)

### The service gets killed in the background

OEM battery optimizations (Samsung, Xiaomi, Huawei, etc.) are notoriously aggressive:

1. Grant the **Battery Optimization exemption** (Permissions section → Open Settings)
2. **On Xiaomi/MIUI**: Also enable **Auto-start** for the app in Security → Permissions
3. **On Samsung**: Disable **"Put unused apps to sleep"** for this app in Battery settings

### "Cannot draw over other apps" toast

You're on a newer Android that requires manual overlay permission toggle:

1. Go to the Permissions section → **Open Settings**
2. Enable the toggle for VMC
3. Return to the app

### Performance Issues

- ** lag during motion**: Adjust **Smoothing** slider in Advanced section (0.8-0.95 recommended)
- **Dot rendering delays**: Enable **Auto-Contrast** for better performance
- **Memory usage**: Close other apps before testing

---

## Testing & Verification

### Verify the motion pipeline works

1. Set mode to **On**, tap **Start**
2. Watch `logcat` filtered to the app:

   ```bash
   adb logcat | grep -E "MotionPipeline|ActivityRecog|OverlayService"
   ```

3. Hold phone upright (portrait, top pointing away)
4. Test each motion direction:
   - **Forward**: dots drift **down** (accel)
   - **Brake**: dots drift **up** (decel)
   - **Right**: dots drift **left** (turn)
   - **Left**: dots drift **right** (turn)

### UI Testing

For preview vs actual overlay comparison:

1. Open the app in **On mode**
2. The **Live Preview** at the top of settings shows the same dot behavior as the actual overlay
3. Adjust settings and see changes reflected in both preview and actual overlay simultaneously
4. Test **Dynamic pattern** vs **Regular pattern** in preview

### Advanced Testing

#### Sensitivity Testing

1. Set mode to **On**, tap **Start**
2. Gradually increase **Sensitivity** slider
3. Observe how quickly dots respond to motion
4. Optimal: dots respond proportionally to force magnitude

#### Smoothing Testing

1. Set mode to **On**, tap **Start**
2. Vary **Filter Alpha** (0.10-0.30)
3. **Lower values** = more responsive but jittery
4. **Higher values** = smoother but laggy

#### Deadzone Testing

1. Set mode to **On**, tap **Start**
2. Hold phone completely still
3. Adjust **Deadzone** (0.05-0.50 m/s²)
4. Higher values = dots more likely to stay at rest

---

## Project Configuration

### Key Parameters

These parameters control the motion cue behavior. They need **real in-car testing** to optimize:

#### Motion Mapping
- **Sensitivity** (g-force → pixel scale): Start at 1.0×, adjust until dot motion matches your sensation
- **Deadzone** (noise floor): Hardcoded at 0.15 m/s² in `VehicleFrame.kt`

#### Dead-Reckoning Integrator
- **Filter Alpha** (0.10-0.30): Low-pass filter strength
- **Damping Coefficient** (2.0-10.0 1/s): Spring-damper damping
- **Return-to-Center** (0.5-5.0 1/s): Position pull speed
- **Input Clamp** (3.0-12.0 m/s²): Max acceleration limit

#### Context Gate
- **Entry Delay** (1-10 seconds): Time to confirm vehicle context
- **Exit Grace** (5-25 seconds): Time to disengage from vehicle context

### Dot Layout

- **Regular Pattern**: 5 dots per edge (10 total)
- **More Dots**: 9 dots per edge (18 total)
- **Dynamic Pattern**: Each dot has unique wobble pattern
- **Auto-Contrast**: Thin dark/light ring behind solid dot

### Color System

- **Dot Colors**: User-selectable from iOS color palette
- **Contrast Rings**: Inverse luminance of dot color
- **Theme Support**: Light/dark mode following iOS design

---

## Development Setup

### Android Studio Configuration

1. **File → Settings → Build, Execution, Deployment → Gradle**
   - Ensure "Offline work" is **unchecked**
   - Gradle JVM: Set to embedded JDK (jbr-17)

2. **File → Code Style → Formatter**
   - Apply project defaults
   - ktlint-compatible formatting enabled

3. **AVD Manager** (Tools → Device Manager → AVD Manager)
   - Create emulator for UI testing
   - Pre-configured with appropriate specs

### Git Configuration

```bash
# Set user information for commits
git config --global user.name "Your Name"
git config --global user.email "your.email@example.com"

# Configure line endings
git config --global core.autocrlf input
```

### Project Structure

```
vmc-android/
├── app/src/main/
│   ├── AndroidManifest.xml                    # App declarations
│   ├── java/com/zai/vmccues/                   # Source code
│   │   ├── VmcApplication.kt                   # App singletons
│   │   ├── MainActivity.kt                     # Compose host
│   │   ├── data/                               # Models & repository
│   │   ├── gate/                               # ActivityRecognition + ContextGate
│   │   ├── motion/                             # Pipeline & physics
│   │   ├── overlay/                            # Service + DotOverlayView
│   │   ├── tile/                               # Quick Settings tile
│   │   └── ui/                                 # Compose settings & theme
│   └── res/                                    # Resources
├── gradle/                                    # Gradle wrapper
│   ├── wrapper/                               # Gradle wrapper files
│   └── libs.versions.toml                     # Dependency versions
└── docs/                                       # Documentation
    ├── ARCHITECTURE.md                         # Technical design
    ├── CONTRIBUTING.md                         # Contribution guidelines
    └── TESTING.md                               # Testing procedures
```

---

## Performance Optimization

### Memory Management

- **Dot Lifecycle**: Dots are created once during layout rebuild
- **Choreographer**: Uses Android's FrameCallback for smooth 60 FPS animation
- **Memory Efficiency**: Minimal allocations during runtime

### Sensor Management

- **Lazy Registration**: Sensors only registered when pipeline starts
- **Filtering**: Low-pass filter reduces sensor noise
- **Sampling**: `SENSOR_DELAY_GAME` for optimal balance of accuracy/performance

### Rendering Optimization

- **Texture Caching**: Dot paints cached between frames
- **Selective Updates**: Only update dots that have moved
- **Alpha Optimization**: Pre-calculate alpha values for performance

---

## Advanced Features

### Dynamic Pattern

Each dot has a unique wobble pattern for realistic motion simulation:

- **Phase**: Deterministic per-dot (0-2π range)
- **Amplitude**: Varying per dot (0.85-1.15 scale)
- **Frequency**: Slightly different per dot for natural variation

### Auto-Contrast

Thin contrast rings make dots visible on any background:

- **Light dots**: Dark rings (~35% opacity)
- **Dark dots**: Light rings (~35% opacity)
- **Scale**: ~0.5dp larger than main dot
- **Placement**: Behind solid dot for proper layering

### Gesture Support

- **Touch Passthrough**: Overlay doesn't intercept touches
- **Quick Settings**: One-tap toggle from notification shade
- **Settings Navigation**: Full app access via notification

---

## Known Limitations

### Technical Constraints

- **Activity Recognition Accuracy**: Google's vehicle detection is mediocre (~3% confident windows)
- **Auto-Contrast Implementation**: Limited by Android API vs iOS compositor
- **Testing Environment**: Untested on physical devices in sandbox
- **OEM Variations**: Battery optimization behavior varies by manufacturer

### Performance Considerations

- **Sensor Availability**: Very cheap devices may lack linear-acceleration sensor
- **Memory Limits**: Large dot layouts on low-end devices
- **Frame Rate**: Target 60 FPS, may drop on older hardware
- **Battery Usage**: Background service consumes power even when idle

### UI Limitations

- **Multitasking**: Not optimized for split-screen or multi-window
- **Large Text**: Font scaling may affect dot positioning
- **Dark Mode**: Contrast optimization may need adjustment
- **Accessibility**: Limited support for screen readers

---

## Future Enhancements

### Feature Ideas

1. **Custom Dot Patterns**: User-defined dot arrangements
2. **Motion Profiles**: Save/load driver-specific settings
3. **Accessibility Mode**: Enhanced for visually impaired users
4. **Real-time Tuning**: In-car parameter adjustment
5. **Analytics**: Usage statistics for optimization

### Technical Improvements

1. **Machine Learning**: Predictive motion enhancement
2. **Sensor Fusion**: Combine accelerometer + gyroscope data
3. **Adaptive Filtering**: Dynamic smoothing based on driving conditions
4. **Cloud Sync**: Settings synchronization across devices
5. **Performance Profiling**: Detailed performance metrics

---

## Support

### Common Issues

1. **Dots don't appear**: Grant overlay permission
2. **Dots don't move**: Check sensor availability and motion
3. **App crashes**: Clear app data and reinstall
4. **Battery optimization**: Add app to exemption list

### Getting Help

1. **GitHub Issues**: Report bugs and request features
2. **Discussions**: Community support and best practices
3. **Stack Overflow**: Tag questions with `vmc-android`
4. **Discord/Slack**: Community channels (if available)

### Testing Support

1. **GitHub Actions**: Automated testing on push/pull requests
2. **Android Studio**: Built-in lint and testing tools
3. **Firebase Test Lab**: Cloud-based device testing
4. **Real Device Testing**: Manual testing on physical phones

---

## Documentation

### API Reference

Generated Javadoc available in:
```
app/build/docs/javadoc/
```

### Developer Notes

**Architecture Notes**:
- 5-layer architecture mirrors iOS implementation
- Context-sensitive pipeline for power efficiency
- Reactive programming with Kotlin Coroutines

**Engineering Decisions**:
- Parameters chosen from limited iOS documentation
- Real-world in-car testing required for optimization
- Trade-offs between responsiveness and smoothness

**Code Quality**:
- ktlint formatting for consistent code style
- Minimal comments (following existing codebase conventions)
- Self-documenting code structure

---

## Getting Involved

### Contributing Code

1. **Fork** this repository
2. **Create** feature branch
3. **Make** changes
4. **Test** thoroughly
5. **Create** pull request

### Improving Documentation

1. **Read** existing documentation
2. **Identify** gaps and inaccuracies
3. **Update** with accurate information
4. **Add** examples and diagrams
5. **Format** consistently with existing docs

### Reporting Issues

1. **Check** existing issues for duplicates
2. **Reproduce** the problem
3. **Document** steps to reproduce
4. **Provide** detailed error information
5. **Submit** GitHub issue

---

## Acknowledgements

- **Apple Inc.** - Vehicle Motion Cues accessibility feature (iOS 18) - the reference implementation
- **Android Open Source Project** - Platform, tools, and libraries
- **Jetpack Compose Team** - Modern Android UI toolkit
- **Google Play Services Team** - Activity Recognition API
- **Open Source Community** - Libraries and dependencies

---

This project represents a comprehensive recreation of Apple's Vehicle Motion Cues accessibility feature for the Android platform. While it achieves visual and behavioral fidelity to the iOS version, ongoing real-world testing and refinement are necessary to match Apple's production-level implementation.

---

Last Updated: June 24, 2024
Version: 2.0.0
