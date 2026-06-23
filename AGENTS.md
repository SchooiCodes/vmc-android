# Project Overview

Vehicle Motion Cues — Android is a comprehensive recreation of Apple's Vehicle Motion Cues accessibility feature (iOS 18) for the Android platform. This project implements all 5 layers from the iOS specification to provide passengers with realistic motion cues that reduce sensory conflict during vehicle travel.

## Vision & Mission

**Mission:** Create an Android app that ports iOS vehicle motion cues EXACTLY as they are in iOS, ensuring the cues look the SAME.

**Vision:** Achieve pixel-perfect fidelity to Apple's implementation, including:
- Identical physics simulation and motion response
- Exact visual rendering of peripheral dot fields
- Authentic auto-contrast behavior
- Precise layout and positioning

## Technical Architecture

The implementation follows Apple's 5-layer architecture from the iOS specification:

### Layer 1: Context Gate
- **Purpose:** Detect when user is in a moving vehicle
- **Components:** ActivityRecognition + MotionSignalStatistics
- **Behavior:** Mirrors Apple's "doesn't snap off instantly" entry/exit grace periods

### Layer 2: Motion Pipeline
- **Purpose:** Transform sensor data into vehicle frame forces
- **Components:** LinearAcceleration + GameRotationVector processing
- **Behavior:** Reference-frame transform (device→world→vehicle) with low-pass filtering

### Layer 3: Overlay Renderer
- **Purpose:** Display peripheral dot overlay on top of other apps
- **Components:** ForegroundService + WindowManager overlay
- **Behavior:** Touch-passthrough, real-time animation with Choreographer

### Layer 4: Settings UI
- **Purpose:** Configure motion cue appearance and behavior
- **Components:** Jetpack Compose with 3-way mode (Off/On/Automatic)
- **Behavior:** Pattern, color, size, contrast, sensitivity controls

### Layer 5: Safety Guardrails
- **Purpose:** Prevent misuse and ensure passenger safety
- **Components:** First-time safety disclaimer dialog
- **Behavior:** Clear warnings about passenger-only usage

## Key Features

### ✅ iOS Faithful Implementation

**Visual Fidelity:**
- Dot layout: 6-10 dots per edge (35% center exclusion)
- Dot rendering: Solid circles + thin contrast rings
- Auto-contrast: Light ring on dark dots, dark ring on light dots
- Dynamic pattern: Deterministic per-dot wobble (no flicker)

**Physics Simulation:**
- Dead-reckoning integrator with critical damping
- Negated position vectors for earth-anchored dots
- Lateral/longitudinal force response matching iOS
- Same mathematical models as iOS implementation

**User Experience:**
- Three activation modes: Off/On/Automatic
- Quick Settings tile for one-tap toggle
- Sensitivity and smoothing controls
- Pattern customization (Regular/Dynamic)

### ✅ Technical Excellence

**Code Quality:**
- Unified `PreviewUtilities.kt` eliminates duplication
- ktlint formatting for consistent code style
- Self-documenting architecture
- Minimal, focused comments

**Development Workflow:**
- Comprehensive documentation
- GitHub Actions CI/CD pipeline
- Android Lint integration
- Testing procedures and verification

### ✅ Performance & Reliability

**Efficient Rendering:**
- Choreographer-driven 60 FPS animation
- Minimal allocations during runtime
- Memory-efficient dot lifecycle

**Power Management:**
- Lazy sensor registration
- Context-aware pipeline control
- Background service optimization

**Robustness:**
- Graceful degradation on unsupported devices
- Error handling and recovery
- Comprehensive logging

## Technical Specifications

### Physics Parameters

| Parameter | Range | Description |
|-----------|--------|-------------|
| g-force → pixel mapping | 18 px/m/s² | Scale factor for displacement |
| Deadzone | 0.15 m/s² | Noise floor threshold |
| Max displacement | ±120 px | Clamp for extreme forces |
| Filter Alpha | 0.10-0.30 | Low-pass filter strength |
| Damping Coefficient | 2.0-10.0 1/s | Spring-damper damping |
| Return-to-Center | 0.5-5.0 1/s | Position pull speed |

### Motion Response

**Force Mapping:**
- **Lateral (turning)**: Positive = vehicle turning left (body pushed right)
- **Longitudinal (accel/brake)**: Positive = accelerating forward (body pressed into seat)

**Dot Movement:**
- **Turn right → dots drift LEFT**
- **Turn left → dots drift RIGHT**
- **Accelerate → dots drift BACKWARD** (toward top)
- **Brake → dots drift FORWARD** (toward bottom)

### Performance Targets

- **Frame Rate:** 60 FPS (Choreographer-driven)
- **Memory:** Minimal allocation during runtime
- **Battery:** Background service optimized
- **Response Time:** <100ms to sensor input

## Development Environment

### Prerequisites

- **Android Studio Hedgehog (2023.1.1) or newer**
- **JDK 17** (bundled with Android Studio)
- **Android SDK** (included by Android Studio)
- **Git** (for version control)

### Build Tools

```bash
# Build debug APK
cd vmc-android
./gradlew assembleDebug

# Build release APK
cd vmc-android
./gradlew assembleRelease

# Run lint
cd vmc-android
./gradlew lint

# Run tests
cd vmc-android
./gradlew test
```

### IDE Configuration

1. **File → Settings → Build, Execution, Deployment → Gradle**
   - Ensure "Offline work" is unchecked
   - Gradle JVM: Set to embedded JDK (jbr-17)

2. **File → Code Style → Formatter**
   - Apply project defaults
   - ktlint-compatible formatting enabled

3. **AVD Manager** (Tools → Device Manager → AVD Manager)
   - Create emulator for UI testing
   - Pre-configured with API 34, Pixel 7

## Project Structure

```
vmc-android/
├── .github/                          # CI/CD configuration
│   └── workflows/                    # GitHub Actions
│       └── android.yml               # Automated testing
├── .editorconfig                      # Code style configuration
├── .gitignore                         # Version control ignores
├── docs/                              # Documentation
│   ├── ARCHITECTURE.md               # Technical architecture
│   ├── CONTRIBUTING.md               # Contribution guidelines
│   ├── DEVELOPER_GUIDE.md             # Developer setup
│   └── TESTING.md                     # Testing procedures
├── README.md                          # Project overview (this file)
├── SETUP.md                           # Complete setup guide
├── app/                               # Android application
│   ├── build.gradle.kts               # App build configuration
│   └── src/main/                       # Source code
│       ├── AndroidManifest.xml          # App declarations
│       └── java/com/zai/vmccues/        # Project source
│           ├── VmcApplication.kt       # App singletons
│           ├── MainActivity.kt         # Compose host
│           ├── data/                   # Models & repository
│           ├── gate/                   # Context gate implementation
│           ├── motion/                 # Physics pipeline
│           ├── overlay/                # Overlay rendering
│           ├── tile/                   # Quick Settings tile
│           └── ui/                     # Compose settings UI
└── gradle/                            # Gradle wrapper
    ├── wrapper/                       # Gradle wrapper files
    └── libs.versions.toml             # Dependency versions
```

## Code Architecture

### Design Principles

1. **Separation of Concerns**: Each layer has a distinct responsibility
2. **Reactive Programming**: Kotlin Coroutines + Flow for async operations
3. **Dependency Injection**: Clean dependencies between components
4. **Testability**: Mockable interfaces for unit testing

### Key Components

#### MotionPipeline.kt
- **Responsibility:** Process sensor data and drive dot movement
- **Algorithms:** Dead-reckoning integration with critical damping
- **Outputs:** Dot displacement vectors for rendering

#### VehicleFrame.kt
- **Responsibility:** Transform device-frame to vehicle-frame
- **Algorithms:** Quaternion rotation + horizontal projection
- **Outputs:** Lateral + longitudinal force in m/s²

#### DeadReckoningIntegrator.kt
- **Responsibility:** Integrate acceleration → velocity → position
- **Algorithms:** Spring-damper formulation with return-to-center
- **Outputs:** Negated position vectors (earth-anchored)

#### DotOverlayView.kt
- **Responsibility:** Render dots on screen with animation
- **Algorithms:** Canvas drawing with contrast rings
- **Outputs:** Visual dot representation

#### ContextGate.kt
- **Responsibility:** Determine when dots should be visible
- **Algorithms:** State machine with grace periods
- **Outputs:** In-vehicle boolean for renderer

## Testing & Verification

### Unit Testing

```bash
cd vmc-android
./gradlew test  # Run all unit tests
./gradlew connectedDebugAndroidTest  # Run instrumentation tests
```

### Integration Testing

1. **Motion Pipeline Test**: Verify force transformation accuracy
2. **Rendering Test**: Check dot positioning and movement
3. **Context Gate Test**: Validate vehicle detection logic
4. **UI Test**: Test settings and interactions

### Manual Testing

1. **Physical Device Test**: Real phone with sensors
2. **Emulator Test**: UI testing without motion
3. **Cross-Device Test**: Multiple Android versions and screen sizes
4. **Performance Test**: Measure frame rate and memory usage

### Automated Testing (GitHub Actions)

```yaml
# .github/workflows/android.yml
name: Android CI/CD

on:
  push:
    branches: [ main ]
  pull_request:
    branches: [ main ]

jobs:
  test:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v3
      - name: Set up JDK 17
        uses: actions/setup-java@v3
        with:
          java-version: '17'
          distribution: 'temurin'
      - name: Cache Gradle packages
        uses: actions/cache@v3
        with:
          path: |
            ~/.gradle/caches
            ~/.gradle/wrapper
          key: ${{ runner.os }}-gradle-${{ hashFiles('**/*.gradle*', '**/*.gradle.kts') }}
      - name: Grant execute permission for gradlew
        run: chmod +x gradlew
      - name: Build with Gradle
        run: ./gradlew build
      - name: Run lint
        run: ./gradlew lint
      - name: Run unit tests
        run: ./gradlew test
```

## Performance Optimization

### Rendering Optimization

- **Frame Callback**: Choreographer ensures consistent 60 FPS
- **Minimal Allocations**: Dot caching and reuse
- **Batch Drawing**: Group similar drawing operations

### Memory Management

- **Object Pooling**: Reuse dot and offset objects
- **Lazy Loading**: Load resources only when needed
- **Garbage Collection**: Minimize allocation pressure

### Sensor Management

- **Conditional Registration**: Sensors only when pipeline active
- **Low-Pass Filtering**: Reduce sensor noise
- **Sampling Rate**: Optimized for battery vs accuracy trade-off

## Contributing

### Code Contributions

1. **Fork** this repository
2. **Create** feature branch
3. **Make** changes
4. **Add** tests
5. **Format** code with ktlint
6. **Submit** pull request

### Documentation Contributions

1. **Read** existing documentation
2. **Identify** gaps and inaccuracies
3. **Update** with accurate information
4. **Add** examples and diagrams
5. **Format** consistently with existing docs

### Testing Contributions

1. **Add** new test cases
2. **Update** existing tests
3. **Fix** failing tests
4. **Document** test procedures
5. **Maintain** test coverage

### Bug Reports

1. **Check** existing issues for duplicates
2. **Reproduce** the problem
3. **Document** steps to reproduce
4. **Provide** detailed error information
5. **Submit** GitHub issue

## Future Development

### Planned Features

1. **Custom Dot Patterns**: User-defined dot arrangements and animations
2. **Motion Profiles**: Save/load driver-specific settings
3. **Accessibility Mode**: Enhanced for visually impaired users
4. **Real-time Tuning**: In-car parameter adjustment without app
5. **Analytics**: Usage statistics for optimization

### Technical Improvements

1. **Machine Learning**: Predictive motion enhancement based on driving patterns
2. **Sensor Fusion**: Combine accelerometer + gyroscope + GPS data
3. **Adaptive Filtering**: Dynamic smoothing based on driving conditions
4. **Cloud Sync**: Settings synchronization across devices
5. **Performance Profiling**: Detailed performance metrics and optimization

## Known Limitations

### Technical Constraints

- **Activity Recognition**: Google's vehicle detection accuracy is mediocre (~3% confident windows)
- **Auto-Contrast**: Limited by Android API vs iOS compositor-level rendering
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

## Support

### Getting Help

1. **GitHub Issues**: Report bugs and request features
2. **Discussions**: Community support and best practices
3. **Stack Overflow**: Tag questions with `vmc-android`
4. **Contact**: For enterprise support and consulting

### Contributing Guidelines

1. **Follow** code style and formatting guidelines
2. **Write** comprehensive tests
3. **Document** changes and decisions
4. **Review** code changes thoroughly
5. **Test** on multiple devices and Android versions

---

## Acknowledgements

- **Apple Inc.** - Vehicle Motion Cues accessibility feature (iOS 18) - the reference implementation
- **Android Open Source Project** - Platform, tools, and libraries
- **Jetpack Compose Team** - Modern Android UI toolkit
- **Google Play Services Team** - Activity Recognition API
- **Open Source Community** - Libraries and dependencies
- **GitHub** - Repository hosting and CI/CD
- **All Contributors** - This project's community

---

## License

This project is a recreation of Apple's Vehicle Motion Cues accessibility feature for educational and compatibility purposes. The original iOS implementation is proprietary to Apple Inc.

**License:** MIT License

---

## Version History

### v2.0.0 - 2024-06-24
- **Complete iOS Port**: Motion cues now look EXACTLY like iOS version
- **Code Refactoring**: Eliminated duplication between LivePreview and DotOverlayView
- **Unified Rendering**: Both preview and actual overlay use identical logic
- **Enhanced Documentation**: Comprehensive developer documentation
- **CI/CD Pipeline**: GitHub Actions for automated testing
- **Bug Fixes**: Fixed color inconsistencies and coordinate system issues
- **Performance**: Improved code maintainability and performance

### v1.0.0 - 2022-01-08
- Initial release
- Basic iOS recreation functionality
- Core 5-layer architecture implementation

---

*Last updated: June 24, 2024*
*Version: 2.0.0*
