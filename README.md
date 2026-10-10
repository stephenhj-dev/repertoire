# TiviMate Assistant

Phone guide and remote for TiviMate on an NVIDIA Shield. The app shows no video. Search, country listings, and the remote run on the phone. TiviMate on the Shield plays the channel.

## Build

```bash
cd android
./gradlew test assembleDebug
```

The debug APK is `android/app/build/outputs/apk/debug/app-debug.apk`.

```bash
adb install -r android/app/build/outputs/apk/debug/app-debug.apk
```

On first launch, enter the Xtream portal, username, and password that TiviMate already uses, then pair with the Shield. The TV shows a 6-character code. Catalogs stay on the phone after that. The portal password is stored in encrypted app storage and is not part of this repo.
