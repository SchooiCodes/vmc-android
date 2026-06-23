# Setup Guide — Vehicle Motion Cues (Android)

Complete, copy-paste-able instructions to go from zero to a running app on
your phone. Two paths below — pick the one that matches how you like to work.

---

## Path A — Android Studio (recommended, easiest)

Best if you don't already have a Java/Android CLI toolchain installed.

### 1. Install prerequisites

| What | Version | Where to get it |
|------|---------|-----------------|
| **Android Studio** | Hedgehog (2023.1.1) or newer | https://developer.android.com/studio |
| **JDK** | 17 (bundled with Android Studio — no separate install needed) | — |

Android Studio's installer also installs the Android SDK, so you don't need
to set that up manually.

### 2. Get the project

The project lives at `download/vmc-android/`. Copy that whole folder somewhere
on your machine, e.g. `~/projects/vmc-android`.

### 3. Open it in Android Studio

1. Launch Android Studio.
2. **File → Open…** (or "Open" on the welcome screen).
3. Select the `vmc-android` folder (the one containing `settings.gradle.kts`).
   **Do not** select the `app` subfolder — select the project root.
4. Click **Trust Project** if prompted.

Android Studio will start a **Gradle Sync**. The first sync downloads:
- Gradle 8.9 (~120 MB, one-time)
- All AndroidX / Compose / Play Services dependencies (~400 MB, one-time)

This takes 3–10 minutes depending on your connection. Wait for the status bar
at the bottom to say **"Gradle sync finished"** with no errors.

> **If sync fails**, see the [Troubleshooting](#troubleshooting) section below.

### 4. Set up a device to run on

You need either a **real Android phone** (much better for this app — it needs
real motion sensors) or an emulator (sensors are simulated, so the dots won't
respond to real motion; only useful for checking the UI builds and renders).

#### Real phone (recommended — this app needs real sensors)

1. On the phone: **Settings → About phone** → tap **Build number** 7 times to
   enable Developer Options.
2. **Settings → System → Developer Options** → enable **USB debugging**.
3. Plug the phone into your computer with USB. A prompt will appear on the
   phone asking to allow USB debugging from this computer — tap **Allow**.
4. In Android Studio, the phone should appear in the **device dropdown** at
   the top of the window (next to the Run ▶ button).

#### Emulator (UI check only — no real motion)

1. In Android Studio: **Tools → Device Manager → Create Device**.
2. Pick a phone, e.g. **Pixel 7** → **Next**.
3. Pick a system image, e.g. **API 34 (UpsideDownCake)** → **Next → Finish**.
4. The emulator appears in the device dropdown.

### 5. Run the app

1. Select your device in the dropdown at the top.
2. Click the green **Run ▶** button (or press `Shift+F10`).

Android Studio will:
- Compile the app
- Build a `app-debug.apk`
- Install it on the device
- Launch `MainActivity`

You should see the settings screen with the "Vehicle Motion Cues" header.

### 6. Grant the first-run permissions

The app needs three permissions. The settings screen has a **Permissions**
section with a one-tap button for each:

1. **Display over other apps** → taps **Open Settings** → flips you to the
   system page → toggle **"Allow display over other apps"** for VMC → press
   back. This is the critical one — without it, no dots will appear.
2. **Activity Recognition** → taps **Grant** → system permission dialog →
   **Allow**. (Only needed for Automatic mode; skip if you'll only use On
   mode.)
3. **Notifications** → taps **Grant** → **Allow**. (Android 13+; needed so
   the foreground service can show its "Cues active" notification.)
4. *(Optional but recommended)* **Battery Optimization** → **Open Settings**
   → exempt the app so OEM battery settings don't kill the background service.

### 7. Acknowledge the safety disclaimer

A dialog appears explaining this is a passenger-only feature. Read it, tap
**I understand**.

### 8. Turn it on and test

1. Set **Activation Mode** to **On**.
2. Tap **Start**.
3. A persistent notification appears: "Vehicle Motion Cues is on".
4. Press the **Home** button (so you're on the home screen or in another app).
5. You should see faint dots around the edges of the screen.
6. Move your phone around — tilt it, accelerate it, simulate turning. The dots
   should shift laterally and vertically with the motion.

To stop: open the app → tap **Stop**, or use the Quick Settings tile (see
below).

### 9. (Optional) Add the Quick Settings tile

For one-tap on/off from anywhere:

1. Swipe down from the top of the screen twice to open the full Quick Settings
   shade.
2. Tap the **pencil/edit** icon.
3. Find **"Motion Cues"** in the list of available tiles.
4. Drag it into the active tiles area.
5. Now you can toggle the feature on/off from the shade without opening the
   app.

---

## Path B — Command line

Best if you already have a Java/Android SDK toolchain and prefer CLI, or want
to script the build.

### 1. Install prerequisites

| What | Version | How to verify |
|------|---------|---------------|
| **JDK** | 17 | `java -version` (must say 17.x) |
| **Android SDK** | API 34 + build-tools 34.0.0 | `sdkmanager --list_installed` |
| **Android Platform Tools** | latest (includes `adb`) | `adb --version` |

If you don't have the SDK: install **Android command line tools** from
https://developer.android.com/studio#command-line-tools-only, then:

```bash
sdkmanager "platform-tools" "platforms;android-34" "build-tools;34.0.0"
```

### 2. Configure the SDK path

```bash
cd vmc-android
cp local.properties.example local.properties
# Edit local.properties — set sdk.dir to your SDK location, e.g.:
#   sdk.dir=/Users/yourname/Library/Android/sdk        (macOS)
#   sdk.dir=/home/yourname/Android/Sdk                  (Linux)
#   sdk.dir=C\:\\Users\\yourname\\AppData\\Local\\Android\\Sdk   (Windows)
```

### 3. Build the debug APK

The Gradle wrapper is included, so you don't need Gradle installed system-wide:

```bash
# macOS / Linux:
./gradlew assembleDebug

# Windows:
gradlew.bat assembleDebug
```

First run downloads Gradle 8.9 + all dependencies (3–10 min). When it finishes,
the APK is at:

```
app/build/outputs/apk/debug/app-debug.apk
```

### 4. Install on a connected phone

```bash
# Verify the phone is visible:
adb devices
# Should list a device, e.g.:
#   List of devices attached
#   XXXXXXXX    device

# Install:
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

### 5. Launch + grant permissions

```bash
# Launch the app:
adb shell am start -n com.zai.vmccues/.MainActivity
```

Then grant permissions manually on the phone screen (same as Path A step 6).
The overlay permission can also be granted via adb:

```bash
adb shell appops set com.zai.vmccues SYSTEM_ALERT_WINDOW allow
```

### 6. Useful Gradle tasks

```bash
./gradlew tasks                    # list all available tasks
./gradlew assembleDebug            # build debug APK
./gradlew installDebug             # build + install on connected device
./gradlew lint                     # run Android lint
./gradlew clean                    # clean build outputs
./gradlew dependencies             # print dependency tree (debugging)
```

---

## Troubleshooting

### "Gradle sync failed: Could not resolve all files"

Your network may be blocking the Google Maven repo. Try:
- **File → Settings → Build, Execution, Deployment → Gradle** and ensure
  "Offline work" is **unchecked**.
- If behind a corporate proxy, configure it in
  `gradle.properties` (`systemProp.https.proxyHost=…`).

### "ERROR: JAVA_HOME is set to an invalid directory"

Android Studio needs JDK 17. In Android Studio:
**File → Project Structure → SDK Location → Gradle Settings** → set **Gradle
JDK** to a 17 (usually the embedded one: "jbr-17").

### "SDK location not found"

You're missing `local.properties` (CLI path). Run:
```bash
cp local.properties.example local.properties
```
and edit the `sdk.dir` line. Android Studio generates this automatically.

### The dots don't appear

In order of likelihood:
1. **Overlay permission not granted** — check the Permissions section of the
   settings screen; "Display over other apps" must say **Granted**.
2. **Mode is Off** — set it to **On** or **Automatic**.
3. **In Automatic mode, the gate hasn't engaged** — you need to actually be
   moving (in a car, or at least generating sustained acceleration). Switch to
   **On** mode to test the overlay itself without needing motion.
4. **The phone lacks a linear-acceleration sensor** — rare on modern phones,
   but possible on very cheap devices. The app degrades gracefully (dots stay
   at rest) but won't animate.

### The dots appear but don't move

- **On mode, no real motion**: hold the phone still — dots sit at rest. That's
  correct (constant velocity = no force to depict). Move around: walk, jog,
  ride in a car.
- **Automatic mode**: the context gate needs ~7 seconds of sustained motion to
  engage (mirroring Apple's "doesn't snap on instantly" behavior). The dots
  won't appear until the gate says CONFIRMED.
- **Sensor unavailable**: very cheap/old phones may not expose
  `TYPE_LINEAR_ACCELERATION`. Check `logcat` for the "available=false" path.

### The service gets killed in the background

OEM battery optimizations (Samsung, Xiaomi, Huawei, etc.) are notoriously
aggressive about killing background services beyond stock Android's limits.

1. Grant the **Battery Optimization exemption** (Permissions section → Open
   Settings).
2. On Xiaomi/MIUI: also enable **Auto-start** for the app in Security →
   Permissions.
3. On Samsung: also disable **"Put unused apps to sleep"** for this app in
   Battery settings.

### "Cannot draw over other apps" toast

You're on a newer Android that requires the user to manually toggle the
overlay permission. Go to the Permissions section → **Open Settings** → enable
the toggle for VMC → return to the app.

---

## Verifying the motion pipeline works

To confirm the sensor → transform → filter → dots pipeline is wired correctly:

1. Set mode to **On**, tap **Start**.
2. Watch `logcat` filtered to the app:
   ```bash
   adb logcat | grep -E "MotionPipeline|ActivityRecog|OverlayService"
   ```
3. Hold the phone upright (portrait, top pointing away from you).
4. Accelerate forward (walk briskly while holding the phone): dots should
   drift **down** (body feels pressed back).
5. Brake/stop: dots drift **up** (body lurches forward).
6. Turn right: dots drift **left** (body feels pushed left).
7. Turn left: dots drift **right**.

If the directions are inverted, your phone's natural hold orientation differs
from the assumed "top-forward" pose — tilt/rotate the phone so its top points
toward the front of the vehicle (brief Section 3.6: "works best when seated
facing forward").

---

## What's intentionally NOT tuned (and why)

The brief's Section 7 flags several parameters Apple hasn't published. We
picked reasonable starting values, but they need **real in-car testing** to
dial in:

- **Sensitivity** (g-force → pixel scale): start at 1.0×, adjust the slider
  while riding as a passenger until the dot motion feels matched to what you
  feel.
- **Smoothing** (EMA coefficient): 0.8 is a good default; lower it if the dots
  feel laggy, raise it if they feel jittery.
- **Deadzone** (rest threshold): hardcoded at 0.15 m/s² in
  `VehicleFrame.kt` — increase if the dots never fully settle at rest.
- **Gate thresholds**: in `ContextGate.kt` — the `WALKING_THRESHOLD` and grace
  periods may need adjustment based on your typical driving conditions.

Edit these in the source and rebuild; they're all in two files:
`motion/VehicleFrame.kt` and `gate/ContextGate.kt`.
