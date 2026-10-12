# Phone Mouse Controller (Android, Kotlin, native Views)

Phone A runs this app and acts as a Bluetooth HID mouse for Phone B. **Nothing is installed on Phone B.**

## Build status
This project was **not compiled** by its author (no network or Android SDK in the generation sandbox). The first real validation is the GitHub Actions run (unit tests + `assembleDebug`). If it fails, fix per the log. Versions: Gradle 8.11.1, AGP 8.9.1, Kotlin 2.0.21, JDK 17, compileSdk/targetSdk 36, minSdk 28. No Gradle wrapper is committed; CI installs Gradle itself. Android Studio will offer to create a wrapper.

## What works without installing anything on Phone B
- **Bluetooth HID mouse**: works if Phone B accepts Bluetooth mice (most Android phones do; pointer appears as a cursor) and Phone A's firmware exposes `BluetoothHidDevice`.

## What requires existing support on Phone B
- **Wi-Fi mode**: Wi-Fi cannot inject mouse events into a phone by itself. It only works if Phone B already runs a listener or privileged service speaking this app's protocol (stock phones do not). Without one, the app reports "unavailable". Host/port are user-entered; none are hardcoded.

Wi-Fi protocol (newline-delimited JSON over TCP, 3 s connect timeout):
```
{"type":"move","dx":5,"dy":-3}
{"type":"scroll","steps":-1}
{"type":"click","button":"left","count":1}
```

## GitHub setup and cloud build
1. Create an empty GitHub repository.
2. In this folder: `git init && git add . && git commit -m "Initial" && git branch -M main`
3. `git remote add origin https://github.com/<you>/<repo>.git && git push -u origin main`
4. Open the **Actions** tab, select "Build debug APK" (runs on push; or use *Run workflow*).
5. When green, open the run and download the artifact **phone-mouse-debug-apk** (zip containing `app-debug.apk`).

## Install the APK on Phone A
Unzip, copy `app-debug.apk` to the phone, open it, and allow "install unknown apps" for your file manager/browser when prompted.

## Pairing and testing
1. Phone A: open the app, tap **Enable HID**; grant "Nearby devices" and enable Bluetooth if asked. Status should read "Registered".
2. Tap **Discoverable** and accept the dialog (120 s).
3. Phone B: Settings > Bluetooth > pair with "Phone Mouse" (or whatever Phone A is named), or pair first then choose it on Phone A's spinner and tap **Connect**.
4. Status shows "Connected". Drag on the touchpad; tap = left click, two-finger tap = right click, two-finger drag = scroll. Buttons offer left/double/right/scroll. Sensitivity slider adjusts speed.
5. Unit tests: `gradle testDebugUnitTest`.

## Known compatibility limitations
- Some manufacturers (and some Android 12-16 builds) hide, disable, or break the HID Device profile; the app then shows "Unsupported" or `registerApp failed`.
- Only one app can hold the HID Device profile at a time.
- Some hosts only accept a HID device they paired with while it was registered, or ignore the wheel.
- Phone B must have Bluetooth mouse support enabled; some devices show no cursor until a mouse connects, and some lock screens ignore mice.
- Not supported: touchscreen injection, lock-screen control, drag-and-hold, full remote control.

## Troubleshooting
- **"HID Device profile not available"**: firmware limitation; try another phone as Phone A.
- **registerApp failed**: toggle Bluetooth off/on, force-stop other Bluetooth-mouse/keyboard apps, retry.
- **Connect rejected**: pair from Phone B first; remove old pairings on both phones and re-pair.
- **Connected but no movement**: Phone B may not accept this device class; unpair and re-pair; check Phone B's pointer settings.
- **Permission denied**: Settings > Apps > Phone Mouse > Permissions > Nearby devices.
- **Spinner empty**: no bonded devices yet, or permission not granted.

## Architecture
`MainActivity` (UI only) · `TouchpadView` · `ConnectionManager` (routing) · `MouseTransport` (interface) · `BluetoothMouseController` · `WifiMouseController` · `MouseReport` (descriptor + report builder, unit tested) · `PermissionManager`. Permissions: `BLUETOOTH_CONNECT` (API 31+), legacy `BLUETOOTH`/`BLUETOOTH_ADMIN` (API 30-), `INTERNET` (Wi-Fi mode only). No root, no hidden APIs, no extra dependencies beyond JUnit for tests. The Bluetooth proxy and HID app are released in `onDestroy`.
