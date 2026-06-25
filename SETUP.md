# Setup Guide — Vehicle Motion Cues (Android)

Zero to running app on your phone.

---

## Prerequisites

| What | Version | Where |
|------|---------|-------|
| Android Studio | Hedgehog (2023.1.1) or newer | https://developer.android.com/studio |
| JDK | 17 (bundled with Android Studio) | — |

---

## 1. Get the project

```bash
git clone https://github.com/SchooiCodes/vmc-android.git
cd vmc-android
```

Open the `vmc-android` folder in Android Studio (**File → Open**). Do not select the `app` subfolder.

## 2. Gradle sync

Android Studio will sync automatically. First run downloads ~500 MB (Gradle + dependencies). Wait for "Gradle sync finished" with no errors.

## 3. Set up a device

**Real phone (recommended):**
1. Settings → About phone → tap **Build number** 7 times
2. Settings → System → Developer Options → enable **USB debugging**
3. Plug phone into computer, accept the prompt
4. Device appears in the device dropdown next to **Run**

**Emulator (UI testing only):**
1. Tools → Device Manager → Create Device → Pixel 7 → API 34

## 4. Run the app

Select your device and click **Run ▶** (or `Shift+F10`).

## 5. Grant permissions (first run)

The app will guide you through permissions in the **Settings** tab:

| Permission | Why | How |
|-----------|-----|-----|
| Display over other apps | Dot overlay | Open Settings → toggle Allow for VMC |
| Activity Recognition | Automatic mode | Allow |
| Notifications | Foreground service | Allow (Android 13+) |
| Battery Optimization | Prevent background kill | Add to exemption list |

## 6. First run

- You land on the **Drive** tab with the interactive car simulation
- Drag anywhere on the road to steer and accelerate — dots respond in real-time
- Tap **Auto Drive** for automatic demo mode
- Switch to **Settings** tab to configure mode, appearance, and sensitivity

## 7. Test the real motion pipeline

1. Go to **Settings** → set Activation Mode to **On**
2. Tap **Start** — notification appears: "Vehicle Motion Cues is on"
3. Press **Home** — dots appear around screen edges
4. Hold phone upright (portrait, top pointing away from you):

| Motion | Dot Movement |
|--------|-------------|
| Accelerate (walk briskly) | Dots drift **down** |
| Brake/stop | Dots drift **up** |
| Turn right | Dots drift **left** |
| Turn left | Dots drift **right** |

---

## Command Line Build

```bash
./gradlew assembleDebug    # debug APK
./gradlew installDebug     # build + install
./gradlew test             # unit tests
./gradlew lint             # lint check
```

Install manually:
```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell am start -n com.zai.vmccues/.MainActivity
```

---

## Troubleshooting

**Dots don't appear:** Overlay permission not granted. Check Settings → Permissions.

**Dots don't move:** In Automatic mode, the context gate needs ~7 seconds of sustained motion.

**Service killed in background:** Grant Battery Optimization exemption. On Xiaomi, also enable Auto-start.

**Gradle sync fails:** File → Settings → Gradle → ensure "Offline work" is unchecked.

---

## License

Educational recreation of Apple's Vehicle Motion Cues (iOS 18). Original implementation is proprietary to Apple Inc.
