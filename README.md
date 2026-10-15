# Repertoire

**A phone guide and remote for TiviMate on Android TV — built for following directors and actors through their body of work.**

Repertoire is an Android phone app that sits next to [TiviMate](https://tivimate.com) running on an Android TV, Google TV or Fire TV device. It reads your IPTV provider's Xtream catalog, matches movies and series to TMDB, and lets you browse by person: open a director or actor, see their filmography in order, and see which of those titles your library actually has. Tap Play and TiviMate on the TV tunes it. The phone never plays video.

## Features

- **Guide and Search** over live channels, movies and series from an Xtream portal, with country listings, favourites and an optional poster grid.
- **TMDB title and person pages**: backdrop hero, cast and crew, and a chronological, library-aware filmography for each person.
- **Repertoire tab**: follow people and get compact cards with a fanned strip of their work; mark titles watched.
- **Version grouping**: provider duplicates (4K / HDR / language / cut variants) collapse into one title with version badges and a preferred-version pick.
- **Series support**: season picker, episode shelf, Resume / Next episode, and tuning a specific episode on the TV.
- **Watchlists**: ordered lists with Play all / Up next, shareable via `repertoire://list/` links, the share sheet or a paste.
- **Universal remote**: D-pad, media and volume keys over Android TV Remote v2 when paired, ADB key events otherwise (incl. Fire TV); audio-track and subtitle switching.
- **Device discovery and setup**: finds TVs on the LAN via mDNS, matches the brand, and walks through enabling network debugging with an in-app guide and live connection test.

## Screenshots

> TODO: add images. Promo stills and video live outside the repo in `~/Projects/Repertoire-promo/`
> (`still-6.png`, `still-16.png`, `still-33.png`, `still-59.png`, `repertoire-promo.mp4`).
> Suggest copying 3–4 compressed stills into `docs/screenshots/` and linking the video from a release or YouTube rather than committing it.

<!-- ![Guide](docs/screenshots/guide.png) ![Person](docs/screenshots/person.png) ![Remote](docs/screenshots/remote.png) -->

## Tech stack

| Area | Library / tool | Version |
| --- | --- | --- |
| Language | Kotlin (JVM target 17) | 2.1.10 |
| Build | Android Gradle Plugin / Gradle wrapper / KSP | 8.8.2 / 8.11.1 / 2.1.10-1.0.29 |
| SDK | compileSdk / targetSdk / minSdk | 35 / 35 / 26 |
| UI | Jetpack Compose BOM, Material 3, Navigation Compose | 2024.12.01, BOM, 2.8.5 |
| Images | Coil 3 (OkHttp network) | 3.0.4 |
| Blur | Haze | 1.2.2 |
| Persistence | Room (KSP) | 2.6.1 |
| Secure prefs | androidx.security-crypto | 1.1.0-alpha06 |
| Serialization | kotlinx-serialization-json | 1.7.3 |
| ADB client | dadb | 2.0.0 |
| Remote v2 TLS/pairing | Bouncy Castle bcpkix | 1.78.1 |
| Tests | JUnit 4, sqlite-jdbc (Room migration SQL on real SQLite) | 4.13.2, 3.46.1.3 |

## Architecture

Single `:app` module, package `app.oslo.repertoire`:

| Package | Responsibility |
| --- | --- |
| `data` | Room `CatalogDatabase` (v8), Xtream catalog fetch/parse, stores, search ranking, series progress, watchlists |
| `tmdb` | TMDB client with in-memory + on-disk cache, rate limiter, title normalizer, catalog matcher/resolver, filmography builder |
| `library` | Title grouping, version tags, display rules |
| `repertoire`, `series`, `watchlist` | Followed people / body of work, episode merging, list logic (pure Kotlin) |
| `remote` | mDNS discovery, Android TV Remote v2 protocol (pairing, TLS transport, key codes) |
| `tune` | ADB over Wi-Fi, TiviMate launch/tune automation, UI-dump parsing |
| `setup`, `splash` | Brand matching and setup guides as data; launch poster collage |
| `ui/*`, `navigation` | Compose screens, design system, Navigation Compose routes |

**Data flow**

```
Xtream portal (player_api) ──► Room catalog (indexed by kind/country/group/category/name/tmdbId)
                                   │
                                   ▼
                     TMDB enrichment (ids from provider, else normalized-title lookup; cached, rate-limited)
                                   │
                                   ▼
                     ViewModels ──► Compose UI (Guide, Search, Title, Person, Repertoire, Lists, Remote)
```

**TV control**: Tuning always goes through ADB over the network (port 5555, via `dadb`). Repertoire launches TiviMate, sends key events/taps, and reads the screen with `uiautomator dump` to find TiviMate's search, series page and player menu. Remote buttons use Android TV Remote v2 (port 6466) when paired and fall back to ADB key events.

## Engineering highlights

- **Title normalization and version grouping** — `TitleNormalizer` strips provider prefixes, quality/language tags and year noise; `VersionTags` parses variants so duplicates group under one TMDB title (16 tests on tags alone).
- **Performance redesign** — title pages load from indexed Room queries; versions and details are fetched on demand (`LazyVersionsTest`), portal TMDB-id lookups are capped at six seconds.
- **TiviMate tuner automation** — bounded retry loops for opening search while menus settle after playback, retrying taps, and stepping D-pad focus to episode tiles.
- **Audio/subtitle switching through TiviMate's own player menu** — parsed from real UI dumps checked in as test fixtures.
- **Room migrations v1 → v8** — explicit `Migration` objects; `RepertoireMigrationTest` runs the migration SQL on real SQLite and checks it against Room's schema.
- **Design system** — Fraunces + Inter type ramp, spacing/radius/motion tokens; Haze frosted-glass bars with a solid tint fallback; shared-element transitions; launch intro with a drifting wall of film posters handing off from the SplashScreen API.

## Testing

128 JVM unit tests across 20 test classes (`android/app/src/test`), covering title matching, version tags, filmography, TMDB cache and rate limiter, episodes, watchlists, discovery, Remote v2 transport, tune logic, TiviMate screen parsing (XML fixtures), brand matching, collage logic and Room migrations. `LiveShieldTest` is opt-in and only runs against a real TV when an address is provided.

```bash
cd android && ./gradlew test
```

## Building

Requirements: JDK 17 (e.g. `mise use java@temurin-17`), Android SDK 35.

```bash
cd android
cp tmdb.properties.example tmdb.properties          # optional: TMDB v4 read token
cp keystore.properties.example keystore.properties  # optional: signing key
./gradlew test assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

- `tmdb.properties` → `tmdb.readToken=…`, exposed as `BuildConfig.TMDB_READ_TOKEN`. Without it, TMDB features are hidden.
- `keystore.properties` → `storeFile`, `storePassword`, `keyAlias`, `keyPassword`. Without it, the default debug key is used.

Both files are gitignored. Portal credentials are entered in the app and stored in encrypted storage.

The debug APK is `android/app/build/outputs/apk/debug/app-debug.apk`. The application ID is `app.oslo.repertoire` (builds before the rename used `app.oslo.tivimate.guide` and install as a separate app).

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

## Roadmap

- Wireless debugging with pairing code (Android 11+ `_adb-tls-connect`), detected but not yet used
- Instrumented/UI tests for Compose screens
- Release build hardening (R8/minify is currently off)
- TODO: user to add priorities

## Credits

- This product uses the TMDB API but is not endorsed or certified by TMDB. Metadata and images © [TMDB](https://www.themoviedb.org).
- Fonts: [Fraunces](https://github.com/undercasetype/Fraunces) and [Inter](https://rsms.me/inter/), SIL Open Font License 1.1 (bundled in `assets/licenses/`).
- Promo video music: "Touching Moments Two - Higher" by Kevin MacLeod ([incompetech.com](https://incompetech.com)), licensed under [CC BY 4.0](https://creativecommons.org/licenses/by/4.0/).
- TiviMate is a third-party app; Repertoire is not affiliated with it. Users bring their own IPTV subscription.

## Status

Late alpha. Personal, non-commercial project.

## License

Copyright (c) 2026 Stephen Johnson. All rights reserved.

The source is published so it can be viewed for portfolio purposes only. No permission is granted to use, copy, modify or distribute it. See [LICENSE](LICENSE).
