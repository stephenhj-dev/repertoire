package app.oslo.repertoire.setup

/**
 * Per-brand connection guides, as plain data so wording is easy to edit.
 *
 * Sources (checked Oct 2026):
 * - NVIDIA Shield: blog.differentpla.net/blog/2022/05/10/nvidia-shield-adb, r/ShieldAndroidTV,
 *   and our own Shield (Android 11, Network debugging on port 5555, "Allow network debugging?").
 * - Google TV Streamer / Chromecast with Google TV: allaboutchromecast.com (developer options and
 *   ADB guides), Home Assistant issue #148359 and community thread on the 2025 Android 14 update.
 * - Fire TV: developer.amazon.com/docs/fire-tv/connecting-adb-to-device.html, aftvnews.com
 *   (hidden Developer options, "Install unknown apps").
 * - Sony: helpguide.sony.net (Settings → About → Android TV OS build, press Enter 10 times).
 * - onn, Xiaomi, TCL, Hisense, Philips: vendor-neutral guides (tvboxtools.com, makeuseof.com,
 *   allabouthisense.com, webproeducation.org). Less authoritative: see UNCERTAIN notes below.
 *
 * Steps marked [uncertain] show "Menus differ between models and updates" in the app.
 */
data class TvGuide(
    val id: String,
    val name: String,
    /** Devices this guide covers, shown under the name. */
    val covers: String,
    val steps: List<GuideStep>,
    /** Brand-specific things to know, shown above the troubleshooting. */
    val quirks: List<String> = emptyList(),
    /** Extra troubleshooting entries for this brand, before the shared ones. */
    val troubleshooting: List<Trouble> = emptyList(),
    /**
     * Newer software on these devices moves network debugging behind Wireless debugging with
     * a pairing code (Android 13/14 Google TV), which Repertoire can't pair with yet.
     */
    val pairingCaveat: Boolean = false,
)

enum class StepKind { DeveloperOptions, Debugging, FindAddress, Allow, Install, Test }

data class GuideStep(
    val kind: StepKind,
    val title: String,
    /** Menu path, one entry per level ("Settings", "Device Preferences", "About"). */
    val path: List<String> = emptyList(),
    val body: String,
    val note: String? = null,
    /** Menu names or locations vary by model or software version. */
    val uncertain: Boolean = false,
)

data class Trouble(val problem: String, val fix: String)

object TvGuides {
    private val allow = GuideStep(
        StepKind.Allow,
        "Allow this phone",
        body = "The first time Repertoire connects, the TV shows a prompt asking to allow debugging from this phone (an RSA key fingerprint). " +
            "Tick “Always allow from this computer”, then choose OK. Keep the TV on and awake while you test, or the prompt can be missed.",
    )

    private fun test(extra: String? = null) = GuideStep(
        StepKind.Test,
        "Test the connection",
        body = "Repertoire connects to the TV the same way it does when it tunes a channel. Watch the TV for the prompt from the previous step." +
            (extra?.let { " $it" } ?: ""),
    )

    // Android TV (pre-Google TV) menus: Shield, Xiaomi Mi Box, older Sony and Philips.
    private fun androidTvDeveloperOptions(buildLabel: String = "Build", uncertain: Boolean = false) = GuideStep(
        StepKind.DeveloperOptions,
        "Unlock Developer options",
        path = listOf("Settings", "Device Preferences", "About"),
        body = "Highlight “$buildLabel” and press OK on the remote about 7 times, until the TV says “You are now a developer”.",
        uncertain = uncertain,
    )

    // Google TV menus: Chromecast with Google TV, Google TV Streamer, onn, TCL and Hisense sets.
    private fun googleTvDeveloperOptions(presses: String = "about 7 times", uncertain: Boolean = false) = GuideStep(
        StepKind.DeveloperOptions,
        "Unlock Developer options",
        path = listOf("Settings", "System", "About"),
        body = "Open Settings from your profile picture or the gear icon. In About, highlight “Android TV OS build” and press OK $presses, " +
            "until the TV says “You are now a developer”. If it asks for a PIN, enter the owner profile's PIN.",
        uncertain = uncertain,
    )

    private fun googleTvDebugging(uncertain: Boolean = false) = GuideStep(
        StepKind.Debugging,
        "Turn on USB debugging",
        path = listOf("Settings", "System", "Developer options"),
        body = "Turn on “USB debugging” and confirm. On these devices it also allows debugging over the network (port 5555), which is what Repertoire uses. No cable is needed.",
        note = "After the 2025 update to Android 14, many Google TV devices only accept network debugging through “Wireless debugging” with a pairing code. " +
            "Repertoire can't pair that way yet; see the note below.",
        uncertain = uncertain,
    )

    private fun googleTvAddress(uncertain: Boolean = true) = GuideStep(
        StepKind.FindAddress,
        "Find the TV's IP address",
        // UNCERTAIN: Google TV shows the address under the selected network; the exact entry
        // name ("Network & Internet" → network → "IP address") varies by build.
        path = listOf("Settings", "Network & Internet", "your Wi-Fi or Ethernet network"),
        body = "The address looks like 192.168.1.20. Repertoire usually finds the TV by itself; you only need this if it doesn't show up in Devices.",
        uncertain = uncertain,
    )

    val shield = TvGuide(
        id = "shield",
        name = "NVIDIA Shield",
        covers = "Shield TV and Shield TV Pro",
        steps = listOf(
            androidTvDeveloperOptions(),
            GuideStep(
                StepKind.Debugging,
                "Turn on Network debugging",
                path = listOf("Settings", "Device Preferences", "Developer options"),
                body = "Turn on “Network debugging”. The Shield shows its IP address and port (5555) right below it.",
            ),
            GuideStep(
                StepKind.FindAddress,
                "Note the IP address",
                path = listOf("Settings", "Device Preferences", "Developer options", "Network debugging"),
                body = "The address is shown under Network debugging, e.g. 192.168.1.20:5555. Repertoire usually finds the Shield by itself.",
            ),
            allow.copy(body = "The Shield asks “Allow network debugging?” with this phone's key fingerprint. Tick “Always allow from this computer”, then choose OK."),
            test(),
        ),
        quirks = listOf(
            "Pair the remote too (Devices → Pair remote): the Shield has the Android TV remote service, which makes the remote buttons quicker than debugging.",
        ),
    )

    val googleTv = TvGuide(
        id = "google",
        name = "Google TV Streamer / Chromecast",
        covers = "Google TV Streamer and Chromecast with Google TV (4K and HD)",
        steps = listOf(googleTvDeveloperOptions(), googleTvDebugging(), googleTvAddress(), allow, test()),
        quirks = listOf(
            "These devices received Android 14 in 2025. Since then, network debugging over Wi-Fi needs “Wireless debugging” and a pairing code, and its port changes after every restart.",
            "The remote works without debugging: pair it in Devices (the Android TV remote service). Tuning channels needs debugging.",
        ),
        troubleshooting = listOf(
            Trouble(
                "Connection refused although USB debugging is on",
                "On Android 14 the TV no longer listens on port 5555. Until Repertoire can pair with Wireless debugging, a computer can turn the classic port back on: " +
                    "turn on Wireless debugging, run “adb pair IP:port” with the code shown, then “adb connect IP:port” and “adb tcpip 5555”. This lasts until the TV restarts.",
            ),
        ),
        pairingCaveat = true,
    )

    val fireTv = TvGuide(
        id = "fire",
        name = "Amazon Fire TV",
        covers = "Fire TV Stick, Stick 4K / 4K Max, Fire TV Cube and Fire TV smart TVs",
        steps = listOf(
            GuideStep(
                StepKind.DeveloperOptions,
                "Unlock Developer options",
                path = listOf("Settings", "My Fire TV", "About"),
                body = "Select the first entry, your device's name (e.g. “Fire TV Stick 4K”), about 7 times until it says “No need, you are already a developer”. " +
                    "Then press Back: Developer options appears under My Fire TV.",
                note = "On Fire TV smart TVs the menu is Settings → Device & Software → About.",
            ),
            GuideStep(
                StepKind.Debugging,
                "Turn on ADB debugging",
                path = listOf("Settings", "My Fire TV", "Developer options"),
                body = "Turn on “ADB debugging”. This also covers Wi-Fi: Fire TV listens on port 5555.",
            ),
            GuideStep(
                StepKind.Install,
                "Install TiviMate (if you haven't)",
                path = listOf("Settings", "My Fire TV", "Developer options", "Install unknown apps"),
                // UNCERTAIN: TiviMate's download address isn't documented by Amazon; we point
                // at TiviMate's own site rather than a specific URL or Downloader code.
                body = "TiviMate isn't in the Amazon Appstore. Install the free “Downloader” app from the Appstore, allow it under Install unknown apps, " +
                    "then download TiviMate from TiviMate's official website.",
                note = "Older Fire OS versions show a single “Apps from Unknown Sources” switch instead.",
                uncertain = true,
            ),
            GuideStep(
                StepKind.FindAddress,
                "Find the IP address",
                path = listOf("Settings", "My Fire TV", "About", "Network"),
                body = "The IP address is listed on the right. Repertoire usually finds the Fire TV by itself.",
            ),
            allow.copy(body = "The Fire TV asks “Allow USB debugging?” even though this is over Wi-Fi. Tick “Always allow from this computer”, then choose OK."),
            test(),
        ),
        quirks = listOf(
            "Fire TV has no Android TV remote service, so the remote sends keys over debugging. No pairing needed.",
            "Fire OS updates can hide Developer options again or switch ADB debugging off. Repeat the first two steps if it stops connecting.",
            "Fire TV Stick 4K Select and other Vega OS models aren't Android and can't run TiviMate.",
        ),
        troubleshooting = listOf(
            Trouble("Developer options disappeared", "An update hid them. Select your device's name in About about 7 times again; your settings are usually kept."),
        ),
    )

    val onn = TvGuide(
        id = "onn",
        name = "onn (Walmart)",
        covers = "onn 4K Streaming Box, 4K Pro and Full HD stick (Google TV)",
        // UNCERTAIN: onn devices run Google TV with Android 12 to 14 depending on model and
        // update; older 2021 Android TV models use the Device Preferences menus instead.
        steps = listOf(
            googleTvDeveloperOptions(uncertain = true),
            googleTvDebugging(uncertain = true),
            googleTvAddress(),
            allow,
            test(),
        ),
        quirks = listOf(
            "Older onn Android TV boxes (before Google TV) use Settings → Device Preferences → About → Build instead.",
            "If Developer options lists “Wireless debugging”, your box may be on Android 14 and need pairing, which Repertoire can't do yet.",
        ),
        pairingCaveat = true,
    )

    val xiaomi = TvGuide(
        id = "xiaomi",
        name = "Xiaomi Mi Box / TV Stick",
        covers = "Mi Box S, Mi TV Stick and Mi TV Box (Android TV)",
        steps = listOf(
            androidTvDeveloperOptions(),
            GuideStep(
                StepKind.Debugging,
                "Turn on USB debugging",
                path = listOf("Settings", "Device Preferences", "Developer options"),
                body = "Turn on “USB debugging”. On these Android TV boxes it also allows debugging over Wi-Fi on port 5555.",
                uncertain = true,
            ),
            GuideStep(
                StepKind.FindAddress,
                "Find the IP address",
                path = listOf("Settings", "Network & Internet", "your Wi-Fi network"),
                body = "The address looks like 192.168.1.20. Repertoire usually finds the box by itself.",
                uncertain = true,
            ),
            allow,
            test(),
        ),
        quirks = listOf(
            "The Mi Box S 2nd Gen and TV Stick 4K run Google TV: their menus are Settings → System → About → Android TV OS build, and Developer options is under System.",
        ),
    )

    val sony = TvGuide(
        id = "sony",
        name = "Sony Bravia",
        covers = "Bravia Google TV and Android TV sets",
        steps = listOf(
            googleTvDeveloperOptions(presses = "repeatedly (Sony's own help says 10 times)").copy(
                path = listOf("Settings", "System", "About"),
                note = "Older Android TV Bravias: Settings → Device Preferences → About → Build.",
                uncertain = true,
            ),
            GuideStep(
                StepKind.Debugging,
                "Turn on USB debugging",
                path = listOf("Settings", "System", "Developer options"),
                body = "Turn on “USB debugging”. Newer sets also list “Wireless debugging”, which uses a pairing code Repertoire can't use yet.",
                note = "On older sets Developer options is under Device Preferences.",
                uncertain = true,
            ),
            googleTvAddress(),
            allow,
            test(),
        ),
        pairingCaveat = true,
    )

    val tclHisense = TvGuide(
        id = "tcl-hisense",
        name = "TCL / Hisense",
        covers = "TCL and Hisense Google TV and Android TV sets",
        steps = listOf(
            googleTvDeveloperOptions(uncertain = true),
            GuideStep(
                StepKind.Debugging,
                "Turn on USB debugging",
                path = listOf("Settings", "System", "Developer options"),
                body = "Turn on “USB debugging”. Some newer TCL builds need “Wireless debugging” on as well, and the newest may only allow Wireless debugging with a pairing code.",
                uncertain = true,
            ),
            googleTvAddress(),
            allow,
            test(),
        ),
        quirks = listOf(
            "Only Google TV and Android TV sets can run TiviMate. Hisense VIDAA and TCL Roku or Fire TV sets can't (a TCL or Hisense Fire TV set: use the Fire TV guide).",
        ),
        pairingCaveat = true,
    )

    val philips = TvGuide(
        id = "philips",
        name = "Philips",
        covers = "Philips Android TV and Google TV sets",
        // UNCERTAIN: Philips Android TVs reach the Android menus through their own settings
        // ("All settings" → "Android settings" on some years); exact names differ by model year.
        steps = listOf(
            androidTvDeveloperOptions(uncertain = true).copy(
                note = "On some Philips sets the Android menus are under Settings → All settings → Android settings. Philips Google TV sets use Settings → System → About → Android TV OS build.",
            ),
            GuideStep(
                StepKind.Debugging,
                "Turn on USB debugging",
                path = listOf("Settings", "Device Preferences", "Developer options"),
                body = "Turn on “USB debugging”, and “Network debugging” if your set lists it.",
                uncertain = true,
            ),
            GuideStep(
                StepKind.FindAddress,
                "Find the IP address",
                path = listOf("Settings", "Network & Internet", "your network"),
                body = "The address looks like 192.168.1.20. Repertoire usually finds the TV by itself.",
                uncertain = true,
            ),
            allow,
            test(),
        ),
        quirks = listOf("Philips sets with Saphi or Titan OS aren't Android and can't run TiviMate."),
    )

    val generic = TvGuide(
        id = "generic",
        name = "Other Android TV",
        covers = "Any Android TV or Google TV device",
        steps = listOf(
            GuideStep(
                StepKind.DeveloperOptions,
                "Unlock Developer options",
                path = listOf("Settings", "System or Device Preferences", "About"),
                body = "Highlight “Android TV OS build” (or “Build”) and press OK about 7 times, until the TV says “You are now a developer”.",
                uncertain = true,
            ),
            GuideStep(
                StepKind.Debugging,
                "Turn on network debugging",
                path = listOf("Settings", "System or Device Preferences", "Developer options"),
                body = "Turn on “Network debugging” or “ADB debugging” if listed, otherwise “USB debugging”. Repertoire connects over Wi-Fi on port 5555.",
                note = "If only “Wireless debugging” with a pairing code works on your TV, Repertoire can't connect to it yet.",
                uncertain = true,
            ),
            googleTvAddress(),
            allow,
            test(),
        ),
        pairingCaveat = true,
    )

    val all: List<TvGuide> = listOf(shield, googleTv, fireTv, onn, xiaomi, sony, tclHisense, philips, generic)

    fun byId(id: String?): TvGuide? = all.firstOrNull { it.id == id }

    /** Troubleshooting that applies to every TV, after the brand's own entries. */
    val common: List<Trouble> = listOf(
        Trouble(
            "The allow prompt never appeared",
            "Make sure the TV is on and showing its home screen or an app (not a screensaver), then test again. If you tapped Deny before, " +
                "use “Revoke USB debugging authorizations” in Developer options (if listed), turn debugging off and on, and test again.",
        ),
        Trouble(
            "Connection refused",
            "The TV is reachable but debugging is off. Check the debugging switch again: some TVs turn it off after a restart or software update.",
        ),
        Trouble(
            "Timed out / the TV isn't answering",
            "Check the IP address, and that the phone and TV are on the same Wi-Fi (not a guest network, which often blocks devices from seeing each other). Turn off a VPN on the phone.",
        ),
        Trouble(
            "It worked before, now it doesn't",
            "The TV's IP address may have changed. Scan again in Devices, or reserve a fixed address for the TV in your router's settings so it stays the same.",
        ),
    )
}
