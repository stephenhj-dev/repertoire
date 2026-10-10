package app.oslo.repertoire.remote

/** Result of probing one capability on the TV. Unknown means "not checked" or "no answer yet". */
enum class Support { Yes, No, Unknown;
    companion object {
        fun parse(value: String?): Support = entries.firstOrNull { it.name == value } ?: Unknown
    }
}

/** How remote-control keys reach the TV. Channel tuning always goes over ADB. */
enum class NavTransport { RemoteV2, Adb, None }

/**
 * Picks the navigation transport.
 *
 * 1. A paired Remote v2 link wins unless the TV is known to lack the service. This keeps the
 *    Shield behaviour unchanged, including before the first capability check.
 * 2. Otherwise ADB key events, when debugging is known to work (Fire TV, or not paired yet).
 * 3. Otherwise Remote v2 if the TV may have it (the user needs to pair).
 * 4. Otherwise try ADB if it has not been ruled out.
 */
fun navigationTransport(remoteV2: Support, paired: Boolean, adb: Support): NavTransport = when {
    remoteV2 != Support.No && paired -> NavTransport.RemoteV2
    adb == Support.Yes -> NavTransport.Adb
    remoteV2 != Support.No -> NavTransport.RemoteV2
    adb == Support.Unknown -> NavTransport.Adb
    else -> NavTransport.None
}

/** mDNS advertisement is proof; otherwise fall back to what a direct probe found. */
fun remoteSupport(advertised: Boolean, probed: Support): Support =
    if (advertised) Support.Yes else probed

/** The shell command that sends one key event over ADB. Codes are Android KeyEvent codes. */
fun adbKeyCommand(code: Int, longPress: Boolean = false): String =
    if (longPress) "input keyevent --longpress $code" else "input keyevent $code"
