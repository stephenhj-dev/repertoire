package app.oslo.repertoire.tune

/** TiviMate package names and how to bring it to the front over ADB. */
object TiviMate {
    /** The player, on Android TV, Google TV and Fire TV alike. */
    const val PLAYER = "ar.tvplayer.tv"
    /** TiviMate Companion is a phone app for the Premium account; it cannot play channels. */
    const val COMPANION = "ar.tvplayer.companion"
    /** Player packages in order of preference. */
    val PLAYERS = listOf(PLAYER)

    /** Picks the installed player from `pm list packages` output, or null when none is installed. */
    fun installedPlayer(packageList: String): String? {
        val installed = packageList.lineSequence()
            .map { it.trim().removePrefix("package:").trim() }
            .filter { it.isNotEmpty() }
            .toSet()
        return PLAYERS.firstOrNull { it in installed }
    }

    fun launchCommands(packageName: String): List<String> = listOf(
        "am start -n $packageName/.ui.MainActivity",
        "monkey -p $packageName -c android.intent.category.LEANBACK_LAUNCHER 1",
        "monkey -p $packageName 1",
    )

    fun launchFailed(output: String): Boolean {
        val text = output.lowercase()
        return "error" in text || "does not exist" in text || "no activities found" in text ||
            "monkey aborted" in text || "exception" in text
    }

    /** Starts TiviMate, trying the explicit activity first and the launcher entry after it. */
    fun launch(shell: TvShell, packageName: String = PLAYER): Boolean {
        for (command in launchCommands(packageName)) {
            if (!launchFailed(shell.exec(command))) return true
        }
        return false
    }
}
