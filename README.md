# Repertoire

Guide and remote for TiviMate. Repertoire is a phone guide and remote for TiviMate on any Android TV, Google TV or Fire TV device: NVIDIA Shield, Chromecast with Google TV, Google TV Streamer, Sony / TCL / Hisense Google TV sets, Onn boxes, Fire TV sticks and cubes. The app shows no video. Search, country listings, and the remote run on the phone. TiviMate on the TV plays the channel.

## Build

```bash
cd android
./gradlew test assembleDebug
```

The debug APK is `android/app/build/outputs/apk/debug/app-debug.apk`.

```bash
adb install -r android/app/build/outputs/apk/debug/app-debug.apk
```

## Setup

1. Enter the Xtream portal, username, and password that TiviMate already uses (Settings). Catalogs stay on the phone after that. The portal password is stored in encrypted app storage and is not part of this repo.
2. Open Devices and tap Find. The phone lists every TV it can see on the Wi-Fi with its name and model (it listens for `_androidtvremote2._tcp`, `_adb-tls-connect._tcp`, `_adb._tcp`, `_googlecast._tcp` and Fire TV's `_amzn-wplay._tcp`). Tap one, or type its address.
3. Turn on network debugging on the TV. Tuning a channel always uses ADB over the network (port 5555):
   - Shield: Settings → Device preferences → Developer options → Network debugging.
   - Google TV / Android TV: Settings → System → About, tap the build number 7 times, then System → Developer options → USB debugging.
   - Fire TV: Settings → My Fire TV → Developer options → ADB debugging.

   The first connection shows an Allow prompt on the TV; check Always allow.
4. Tap Pair remote (Android TV / Google TV only). The TV shows a 6-character code.

## How the remote talks to the TV

Check device probes the selected TV for the Android TV Remote v2 service (port 6466) and for ADB.

| TV | Remote buttons | Channel tuning |
| --- | --- | --- |
| Android TV / Google TV, paired | Android TV Remote v2 (volume, app state) | ADB |
| Android TV / Google TV, not paired yet | ADB key events | ADB |
| Fire TV (no Remote v2 service) | ADB key events | ADB |

ADB key events cannot report volume and repeat while a button is held instead of sending a real long press.

Wireless debugging with a pairing code (`_adb-tls-connect`, Android 11+) is detected but not used; the app connects to classic network debugging on port 5555.
