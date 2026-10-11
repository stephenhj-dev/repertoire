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
        if (seasonNode != null) {
            tap(seasonNode.left, seasonNode.top, seasonNode.right, seasonNode.bottom)
            pause(700)
            items = dumpNodes()
        } else if (TiviMateScreens.seasonsOnScreen(items).isNotEmpty()) {
            return "Opened $series on the TV. Season $season isn't listed there; pick it with the remote"
        }
        report("Finding $code")
        val row = findEpisode(items, season, episode, title)
            ?: return "Opened $series on the TV. Pick $code there with the remote"
        tap(row.left, row.top, row.right, row.bottom)
        pause(900)
        var seen = dumpNodes()
        // Only press Play on a new (episode) page: on the series page itself Play would start
        // TiviMate's own "continue" episode instead of this one.
        val play = seen.firstOrNull { it.text in PLAY_LABELS && it.clickable }
        if (play != null && TiviMateScreens.seasonsOnScreen(seen).isEmpty()) {
            tap(play.left, play.top, play.right, play.bottom)
            pause(1400)
            seen = dumpNodes()
        }
        if (playing(seen) && TiviMateScreens.seasonsOnScreen(seen).isEmpty()) return "Playing $series $code"
        log("after episode", seen)
        return "Opened $series $code on the TV; press OK there if it didn't start"
    }

    /** Finds the episode row, swiping the episode list up or down until it is on screen. */
    private fun findEpisode(start: List<UiNode>, season: Int, episode: Int, title: String?): UiNode? {
        var items = start
        var lastSeen = emptyList<Int>()
        repeat(14) {
            TiviMateScreens.episodeNode(items, season, episode, title)?.let { return it }
            val visible = TiviMateScreens.visibleEpisodes(items, season)
            if (visible.isEmpty() || visible == lastSeen) return null
            lastSeen = visible
            val rows = items.filter { TiviMateScreens.leadingEpisodeNumber(it.label) != null || TiviMateScreens.episodeCode(it.label) != null }
            val top = rows.minOf { it.top }
            val bottom = rows.maxOf { it.bottom }
            val x = rows.first().centerX
            val span = ((bottom - top) * 0.7).toInt().coerceAtLeast(120)
            val down = episode > visible.max()
            if (down) swipe(x, bottom, x, bottom - span) else swipe(x, top, x, top + span)
            pause(600)
            items = dumpNodes()
        }
        return null
    }

    /**
     * Opens the audio or subtitle track picker in the player (long-press OK opens TiviMate's
     * player menu; its Audio track and CC entries open the pickers) and returns the choices.
     */
    fun openTracks(track: Track): TrackPicker {
        if (!tivimateInFront(shell.exec("dumpsys activity activities"), packageName)) {
            return TrackPicker("TiviMate isn't open on the TV")
        }
        var items = dumpNodes()
        if (!TiviMateScreens.playerMenuShown(items)) {
            shell.exec("input keyevent --longpress KEYCODE_DPAD_CENTER")
            pause(800)
            items = dumpNodes()
        }
        if (!TiviMateScreens.playerMenuShown(items)) {
            // Older versions and some remotes: Down twice opens the same menu.
            key("KEYCODE_DPAD_DOWN")
            pause(400)
            key("KEYCODE_DPAD_DOWN")
            pause(700)
            items = dumpNodes()
        }
        log("player menu", items)
        val entry = TiviMateScreens.trackEntry(items, track)
            ?: return TrackPicker(
                if (TiviMateScreens.playerMenuShown(items)) "This menu has no ${trackName(track)} entry"
                else "Couldn't open the player menu. Start playback first",
            )
        tap(entry.left, entry.top, entry.right, entry.bottom)
        pause(800)
        val dialog = dumpNodes()
        log("track dialog", dialog)
        val options = TiviMateScreens.trackOptions(items, dialog, track)
        return TrackPicker(
            if (options.isEmpty()) "The ${trackName(track)} menu is open on the TV" else "",
            options,
        )
    }

    /** Selects [label] in the open track dialog, then closes the player menu. */
    fun chooseTrack(track: Track, label: String): String {
        var items = dumpNodes()
        var row = items.firstOrNull { it.label == label }
        if (row == null) {
            // The dialog was closed meanwhile: open it again.
            openTracks(track)
            items = dumpNodes()
            row = items.firstOrNull { it.label == label } ?: return "Couldn't find $label on the TV"
        }
        tap(row.left, row.top, row.right, row.bottom)
        pause(600)
        repeat(2) {
            val now = dumpNodes()
            if (TiviMateScreens.playerMenuShown(now) || now.any { it.label == label }) {
                key("KEYCODE_BACK")
                pause(400)
            }
        }
        return "${trackName(track).replaceFirstChar { it.uppercase() }}: $label"
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

    private fun swipe(x1: Int, y1: Int, x2: Int, y2: Int) {
        shell.exec("input swipe $x1 $y1 $x2 $y2 350")
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
