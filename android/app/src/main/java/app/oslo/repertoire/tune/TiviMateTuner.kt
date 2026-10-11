package app.oslo.repertoire.tune

import android.util.Log
import app.oslo.repertoire.tune.TiviMateScreens.Track

fun interface TvShell {
    fun exec(command: String): String
}

class TiviMateTuner(
    private val shell: TvShell,
    private val packageName: String = TiviMate.PLAYER,
    private val pause: (Long) -> Unit = { Thread.sleep(it) },
) {
    fun play(name: String, report: (String) -> Unit = {}): String {
        report("Opening TiviMate")
        val opened = ensureSearch()
        if (!inSearch(opened)) return "Could not open TiviMate search"
        report("Typing $name")
        focusField(opened)
        typeText(name)
        pause(600)
        return if (openAndPlay(name, report)) "Playing $name" else "Could not find $name"
    }

    /**
     * Plays one episode: searches the series, opens its page, picks the season, then finds the
     * episode row (scrolling the list if needed) and selects it. When the page doesn't look as
     * expected the series page is left open on the TV, so the user can finish with the remote.
     */
    fun playEpisode(series: String, season: Int, episode: Int, title: String?, report: (String) -> Unit = {}): String {
        val code = "S$season E$episode"
        report("Opening TiviMate")
        val opened = ensureSearch()
        if (!inSearch(opened)) return "Could not open TiviMate search"
        report("Typing $series")
        focusField(opened)
        typeText(series)
        pause(600)
        val page = openResult(series, report) ?: return "Could not find $series"
        report("Choosing season $season")
        var items = page
        // Some layouts open on a Play / Continue panel first; the seasons are below it.
        if (TiviMateScreens.seasonsOnScreen(items).isEmpty() && TiviMateScreens.episodeNode(items, season, episode, title) == null) {
            repeat(3) {
                if (TiviMateScreens.seasonsOnScreen(items).isNotEmpty()) return@repeat
                key("KEYCODE_DPAD_DOWN")
                pause(450)
                items = dumpNodes()
            }
        }
        log("series page", items)
        val seasonNode = TiviMateScreens.seasonNode(items, season)
        if (seasonNode != null && !seasonNode.focused && !seasonNode.selected) {
            // On TV a tap focuses; TiviMate switches the episode row when a season gets focus,
            // and OK confirms it where it doesn't.
            tap(seasonNode.left, seasonNode.top, seasonNode.right, seasonNode.bottom)
            pause(600)
            items = dumpNodes()
            if (!TiviMateScreens.showsSeason(items, season, episode, title)) {
                key("KEYCODE_DPAD_CENTER")
                pause(700)
                items = dumpNodes()
            }
        } else if (seasonNode == null && TiviMateScreens.seasonsOnScreen(items).isNotEmpty()) {
            return "Opened $series on the TV. Season $season isn't listed there; pick it with the remote"
        }
        report("Finding $code")
        if (!focusEpisode(items, season, episode, title)) {
            log("episode not found", dumpNodes())
            return "Opened $series on the TV. Pick $code there with the remote"
        }
        key("KEYCODE_DPAD_CENTER")
        pause(1000)
        var seen = dumpNodes()
        // An episode page with its own Play button. Never press Play on the series page itself:
        // that would start TiviMate's own "continue" episode instead of this one.
        val play = seen.firstOrNull { it.text in PLAY_LABELS && it.clickable }
        if (play != null && TiviMateScreens.seasonsOnScreen(seen).isEmpty()) {
            if (!play.focused) tap(play.left, play.top, play.right, play.bottom)
            pause(300)
            key("KEYCODE_DPAD_CENTER")
            pause(1400)
            seen = dumpNodes()
        }
        if (playing(seen) && TiviMateScreens.seasonsOnScreen(seen).isEmpty()) return "Playing $series $code"
        log("after episode", seen)
        return "Opened $series $code on the TV; press OK there if it didn't start"
    }

    /**
     * Puts focus on the episode tile: tap it when it's on screen (a tap focuses on TV),
     * otherwise focus a visible episode and step Left/Right by the difference in numbers.
     */
    private fun focusEpisode(start: List<UiNode>, season: Int, episode: Int, title: String?): Boolean {
        var items = start
        repeat(4) {
            val target = TiviMateScreens.episodeNode(items, season, episode, title)
            if (target != null && (target.focused || items.any { it.focused && it.contains(target) })) return true
            if (target != null) {
                tap(target.left, target.top, target.right, target.bottom)
                pause(500)
                items = dumpNodes()
                return@repeat
            }
            val focused = TiviMateScreens.focusedEpisode(items, season)
            if (focused == null) {
                val any = TiviMateScreens.episodeNodes(items, season).firstOrNull() ?: return false
                tap(any.second.left, any.second.top, any.second.right, any.second.bottom)
                pause(500)
                items = dumpNodes()
                return@repeat
            }
            val delta = episode - focused
            if (delta == 0) return true
            val move = if (delta > 0) "KEYCODE_DPAD_RIGHT" else "KEYCODE_DPAD_LEFT"
            shell.exec("input keyevent ${List(kotlin.math.abs(delta).coerceAtMost(60)) { move }.joinToString(" ")}")
            pause(700)
            items = dumpNodes()
        }
        val target = TiviMateScreens.episodeNode(items, season, episode, title) ?: return false
        return target.focused || items.any { it.focused && it.contains(target) }
    }

    /**
     * Opens TiviMate's audio or subtitle dialog on the TV and returns its choices. TiviMate
     * ignores KEYCODE_MEDIA_AUDIO_TRACK and KEYCODE_CAPTIONS (checked on a Shield, TiviMate
     * 5.3.3), so this goes through the player menu: long-press OK, Down, then Right to the tile
     * and OK. The menu hides itself after a few seconds, so the keys go in one shell command.
     */
    fun openTracks(track: Track): TrackPicker {
        if (!tivimateInFront(shell.exec("dumpsys activity activities"), packageName)) {
            return TrackPicker("TiviMate isn't open on the TV")
        }
        var items = dumpNodes()
        TiviMateScreens.trackDialog(items)?.let { open ->
            if (dialogTrack(open) == track) return picker(open, track)
            key("KEYCODE_BACK")
            pause(500)
            items = dumpNodes()
        }
        var row = TiviMateScreens.menuRow(items)
        if (row == null) {
            if (!TiviMateScreens.fullscreenPlayback(items)) {
                log("not in playback", items)
                return TrackPicker("Start playing something in TiviMate first")
            }
            shell.exec("input keyevent --longpress KEYCODE_DPAD_CENTER; sleep 0.5; input keyevent KEYCODE_DPAD_DOWN")
            pause(500)
            items = dumpNodes()
            row = TiviMateScreens.menuRow(items)
        }
        log("player menu", items)
        if (row == null) return TrackPicker("Couldn't open TiviMate's player menu. Long-press OK on the TV remote to open it")
        val target = TiviMateScreens.trackTile(row, track)
            ?: return TrackPicker("TiviMate's player menu has no ${trackName(track)} entry here")
        shell.exec("input keyevent ${TiviMateScreens.keysTo(row, target).joinToString(" ")}")
        pause(900)
        val dialogItems = dumpNodes()
        log("track dialog", dialogItems)
        val dialog = TiviMateScreens.trackDialog(dialogItems)
            ?: return TrackPicker("The ${trackName(track)} menu should be open on the TV")
        return picker(dialog, track)
    }

    private fun picker(dialog: TiviMateScreens.TrackDialog, track: Track): TrackPicker {
        val options = TiviMateScreens.trackOptions(dialog)
        return TrackPicker(if (options.isEmpty()) "No ${trackName(track)} choices for this stream" else "", options)
    }

    private fun dialogTrack(dialog: TiviMateScreens.TrackDialog): Track =
        if (TiviMateScreens.words(dialog.title).firstOrNull() == "audio") Track.Audio else Track.Subtitles

    /** Selects [label] in the open track dialog (Up/Down to it, then OK). */
    fun chooseTrack(track: Track, label: String): String {
        var dialog = TiviMateScreens.trackDialog(dumpNodes())
        if (dialog == null || dialogTrack(dialog) != track) {
            // Closed meanwhile (or another one is open): open it again.
            openTracks(track)
            dialog = TiviMateScreens.trackDialog(dumpNodes()) ?: return "Couldn't open the ${trackName(track)} menu on the TV"
        }
        val keys = TiviMateScreens.keysToOption(dialog, label) ?: return "$label isn't offered on the TV any more"
        shell.exec("input keyevent ${keys.joinToString(" ")}")
        pause(700)
        val after = dumpNodes()
        if (TiviMateScreens.trackDialog(after) != null) {
            key("KEYCODE_BACK")
            pause(300)
        }
        return "${trackName(track).replaceFirstChar { it.uppercase() }}: $label"
    }

    /** Closes an open track dialog (the phone sheet was dismissed). */
    fun closeTracks() {
        if (TiviMateScreens.trackDialog(dumpNodes()) != null) key("KEYCODE_BACK")
    }

    private fun trackName(track: Track) = if (track == Track.Audio) "audio track" else "subtitles"

    private fun ensureSearch(): List<UiNode> {
        TiviMate.launch(shell, packageName)
        pause(500)
        var items = dumpNodes()
        if (inSearch(items)) return items
        repeat(7) {
            if (onMainMenu(items)) {
                // Leaving playback, the main menu is still animating in, so the
                // first tap on Search is often dropped. Let it settle, then
                // retry the tap a few times before giving up.
                pause(400)
                items = dumpNodes()
                repeat(3) {
                    if (onMainMenu(items)) items = openSearch(items)
                    if (inSearch(items)) return items
                    pause(500)
                    items = dumpNodes()
                    if (inSearch(items)) return items
                }
                return items
            }
            key("KEYCODE_BACK")
            pause(450)
            if (!tivimateInFront(shell.exec("dumpsys activity activities"), packageName)) {
                TiviMate.launch(shell, packageName)
                pause(700)
            }
            items = dumpNodes()
            if (inSearch(items)) return items
        }
        if (onMainMenu(items)) items = openSearch(items)
        return items
    }

    private fun openSearch(items: List<UiNode>): List<UiNode> {
        val scale = screenScale(items)
        val search = items.firstOrNull { it.text == "Search" && it.left < scaled(500, scale) } ?: return items
        val pad = scaled(16, scale)
        tap(scaled(32, scale), (search.top - pad).coerceAtLeast(0), scaled(408, scale), search.bottom + pad)
        // Poll instead of one fixed wait: search can take a moment to open.
        var now = items
        repeat(5) {
            pause(400)
            now = dumpNodes()
            if (inSearch(now)) return now
        }
        return now
    }

    private fun focusField(items: List<UiNode>) {
        val field = items.firstOrNull { it.className.endsWith("StreamingTextView") }
            ?: items.firstOrNull { it.text in setOf("Speak to search", "Search") && it.top < scaled(180, screenScale(items)) }
            ?: return
        tap(field.left, field.top, field.right, field.bottom)
        pause(300)
        val current = field.text
        if (current.isNotBlank() && current.lowercase() !in setOf("search", "speak to search")) {
            key("123")
            val count = (current.length + 2).coerceAtMost(40)
            shell.exec("input keyevent ${List(count) { "67" }.joinToString(" ")}")
        }
    }

    /** Selects the search result for [name]; returns the page that opened, or null. */
    private fun openResult(name: String, report: (String) -> Unit): List<UiNode>? {
        val items = waitForResults()
        if (items.any { it.text == "Nothing found" } && !hasResultCard(items)) return null
        report("Closing the keyboard")
        if (!dismissKeyboard()) return null
        if (!focusResult(name)) return null
        if (keyboardShown(shell.exec("dumpsys input_method"))) return null
        report("Selecting $name")
        key("KEYCODE_DPAD_CENTER")
        var seen = emptyList<UiNode>()
        for (attempt in 0 until 5) {
            pause(500)
            seen = dumpNodes()
            if (seen.any { it.text in PLAY_LABELS && it.clickable } || !inSearch(seen)) break
        }
        return seen
    }

    private fun openAndPlay(name: String, report: (String) -> Unit): Boolean {
        var seen = openResult(name, report) ?: return false
        val play = seen.firstOrNull { it.text == "Play" && it.clickable }
        if (play != null) {
            if (play.focused) key("KEYCODE_DPAD_CENTER") else tap(play.left, play.top, play.right, play.bottom)
            pause(1400)
            seen = dumpNodes()
        }
        return playing(seen)
    }

    private fun waitForResults(): List<UiNode> {
        var items = emptyList<UiNode>()
        repeat(5) {
            items = dumpNodes()
            if (hasResultCard(items) || items.any { it.text == "Nothing found" }) return items
            pause(350)
        }
        return items
    }

    private fun dismissKeyboard(): Boolean {
        repeat(3) {
            if (!keyboardShown(shell.exec("dumpsys input_method"))) {
                pause(350)
                if (!keyboardShown(shell.exec("dumpsys input_method"))) return true
            }
            key("KEYCODE_BACK")
            pause(550)
        }
        return !keyboardShown(shell.exec("dumpsys input_method"))
    }

    private fun focusResult(wanted: String): Boolean {
        if (!dismissKeyboard()) return false
        var previous = ""
        var stuck = 0
        var movedDown = false
        repeat(12) {
            if (keyboardShown(shell.exec("dumpsys input_method")) && !dismissKeyboard()) return false
            val items = dumpNodes()
            if (fieldFocused(items) || !movedDown) {
                key("KEYCODE_DPAD_DOWN")
                movedDown = true
                pause(450)
                return@repeat
            }
            if (items.any { it.text == "Nothing found" } && !hasResultCard(items)) return false
            val title = selectedTitle(items)
            if (score(title, wanted) != null) return true
            stuck = if (title == previous) stuck + 1 else 0
            previous = title
            key(if (stuck > 0) "KEYCODE_DPAD_DOWN" else "KEYCODE_DPAD_RIGHT")
            pause(350)
        }
        return false
    }

    private fun dumpNodes(): List<UiNode> {
        shell.exec("uiautomator dump /sdcard/tm-tune.xml")
        val xml = shell.exec("cat /sdcard/tm-tune.xml")
        return parseNodes(xml)
    }

    private fun typeText(value: String) {
        val text = encodedText(value)
        if (text.isNotEmpty()) shell.exec("input text $text")
    }

    private fun key(name: String) {
        shell.exec("input keyevent $name")
    }

    private fun log(what: String, items: List<UiNode>) {
        runCatching {
            Log.d("Tune", "$what: " + items.filter { it.label.isNotBlank() }
                .joinToString(" | ") { "${it.label}@${it.left},${it.top}${if (it.clickable) "c" else ""}${if (it.focused) "f" else ""}" })
        }
    }

    private fun tap(left: Int, top: Int, right: Int, bottom: Int) {
        val x = (left + right) / 2
        val y = (top + bottom) / 2
        shell.exec("input tap $x $y")
    }
}

/** Track choices read from TiviMate's picker; [message] explains when there are none. */
data class TrackPicker(val message: String, val options: List<TiviMateScreens.TrackOption> = emptyList())

private val PLAY_LABELS = setOf("Play", "Resume", "Continue", "Watch")
