package app.oslo.repertoire.tune

/**
 * Reading TiviMate's series page and player menu from a uiautomator dump. Pure, no regex:
 * labels are compared as lowercase words.
 */
object TiviMateScreens {
    private val SEASON_WORDS = setOf("season", "staffel", "saison", "temporada", "stagione", "seizoen", "sezon", "series", "s")
    private val EPISODE_WORDS = setOf("episode", "episodio", "épisode", "folge", "aflevering", "odcinek", "bolum", "e", "ep")

    /** The season number a label names ("Season 2", "S02", "Staffel 2"), or null. */
    fun seasonNumber(label: String): Int? {
        val w = words(label)
        if (w.size == 1) {
            val one = w[0]
            if (one.length > 1 && one[0] == 's' && one.drop(1).all { it.isDigit() }) return one.drop(1).toIntOrNull()
            return null
        }
        if (w.size == 2 && w[0] in SEASON_WORDS) return w[1].toIntOrNull()
        // "2nd season" style is rare; "Season 2 (10)" with an episode count after it.
        if (w.size == 3 && w[0] in SEASON_WORDS && w[2].all { it.isDigit() }) return w[1].toIntOrNull()
        return null
    }

    /** Season and episode from an "S01E02" / "s1e2" / "1x02" code anywhere in [label]. */
    fun episodeCode(label: String): Pair<Int, Int>? {
        for (word in words(label)) {
            if (word.length >= 4 && word[0] == 's' && word[1].isDigit()) {
                val e = word.indexOf('e')
                if (e > 1) {
                    val season = word.substring(1, e).toIntOrNull()
                    val episode = word.substring(e + 1).takeWhile { it.isDigit() }.toIntOrNull()
                    if (season != null && episode != null && word.substring(1, e).all { it.isDigit() }) return season to episode
                }
            }
            val x = word.indexOf('x')
            if (x in 1..2 && word.length > x + 1 && word.substring(0, x).all { it.isDigit() }) {
                val episode = word.substring(x + 1)
                if (episode.all { it.isDigit() }) return word.substring(0, x).toInt() to episode.toInt()
            }
        }
        return null
    }

    /** The episode number a row label starts with ("3. Title", "Episode 3", "E3 - Title", "3 Title"). */
    fun leadingEpisodeNumber(label: String): Int? {
        val trimmed = label.trim()
        val digits = trimmed.takeWhile { it.isDigit() }
        if (digits.isNotEmpty() && digits.length <= 3) {
            val after = trimmed.getOrNull(digits.length)
            if (after == null || after == '.' || after == ' ' || after == ':' || after == ')' || after == '-') return digits.toInt()
        }
        val w = words(trimmed)
        if (w.size >= 2 && w[0] in EPISODE_WORDS) return w[1].toIntOrNull()
        val first = w.firstOrNull() ?: return null
        if (first.length > 1 && (first[0] == 'e') && first.drop(1).all { it.isDigit() }) return first.drop(1).toIntOrNull()
        if (first.length > 2 && first.startsWith("ep") && first.drop(2).all { it.isDigit() }) return first.drop(2).toIntOrNull()
        return null
    }

    /**
     * How well a row label names the wanted episode: 0 an explicit S/E code, 1 the leading
     * number, 2 the episode title alone. Null when it names another episode or none.
     */
    fun episodeMatch(label: String, season: Int, episode: Int, title: String?): Int? {
        if (label.isBlank()) return null
        episodeCode(label)?.let { (s, e) -> return if (s == season && e == episode) 0 else null }
        leadingEpisodeNumber(label)?.let { return if (it == episode) 1 else null }
        if (!title.isNullOrBlank() && words(title).size >= 1 && words(label) == words(title)) return 2
        return null
    }

    /** The node naming season [season], preferring clickable ones. */
    fun seasonNode(items: List<UiNode>, season: Int): UiNode? =
        items.filter { seasonNumber(it.label) == season }.maxByOrNull { if (it.clickable) 1 else 0 }

    /** Every season label on screen. Used to tell a series page from a movie page. */
    fun seasonsOnScreen(items: List<UiNode>): Set<Int> = items.mapNotNull { seasonNumber(it.label) }.toSet()

    fun episodeNode(items: List<UiNode>, season: Int, episode: Int, title: String?): UiNode? =
        items.mapNotNull { node -> episodeMatch(node.label, season, episode, title)?.let { node to it } }
            .minWithOrNull(compareBy({ it.second }, { if (it.first.clickable) 0 else 1 }))?.first

    /** Episode numbers visible on screen (for knowing which way, and whether, to scroll). */
    fun visibleEpisodes(items: List<UiNode>, season: Int): List<Int> = items.mapNotNull { node ->
        episodeCode(node.label)?.let { (s, e) -> if (s == season) e else null } ?: leadingEpisodeNumber(node.label)
    }.distinct()

    // Player menu, as on TiviMate 5.3 (Shield): long-press OK opens a quick panel (TV guide,
    // History, recent channels); Down from there opens the menu row: Search, Channels list,
    // Recordings, Multiview, Picture-in-picture, <video: "1280 × 720">, <audio: "Stereo">,
    // <audio offset: "0 ms">, <CC: "Closed captions 1">, <display mode>, <sleep timer>. The tiles
    // show the current value, not a name, and have no content description; a tap only focuses a
    // tile, so they are reached with Right presses and opened with OK.

    enum class Track { Audio, Subtitles }

    /** The menu row: text of each tile left to right, and which one has focus. */
    data class MenuRow(val tiles: List<UiNode>, val focused: Int)

    fun menuRow(items: List<UiNode>): MenuRow? {
        val scale = screenScale(items)
        val band = scaled(700, scale)
        val tile = items.firstOrNull {
            it.focused && it.clickable && it.top >= band && (it.bottom - it.top) < scaled(300, scale) && (it.right - it.left) < scaled(500, scale)
        } ?: return null
        val tiles = items.filter { it.text.isNotBlank() && it.centerY in tile.top..tile.bottom && it.top >= band }
            .sortedBy { it.left }
            .distinctBy { it.left }
        if (tiles.size < 3) return null
        val focused = tiles.indexOfFirst { tile.contains(it) }
        if (focused < 0) return null
        return MenuRow(tiles, focused)
    }

    fun playerMenuShown(items: List<UiNode>): Boolean = menuRow(items) != null

    /** "0 ms", "-120 ms": the audio offset tile, which sits right after the audio track tile. */
    fun isOffset(label: String): Boolean {
        val w = words(label.replace("−", "-"))
        return w.size == 2 && w[1] == "ms" && w[0].all { it.isDigit() }
    }

    /** "1280 × 720", "1920x1080": the video track tile, right before the audio track tile. */
    fun isResolution(label: String): Boolean {
        val compact = label.replace(" ", "").lowercase()
        val sep = compact.indexOfFirst { it == '×' || it == 'x' }
        return sep > 0 && compact.substring(0, sep).all { it.isDigit() } &&
            compact.substring(sep + 1).isNotEmpty() && compact.substring(sep + 1).all { it.isDigit() }
    }

    private val AUDIO_LABELS = listOf(listOf("audio", "track"), listOf("audio", "tracks"), listOf("audio"))
    private val SUBTITLE_LABELS = listOf(listOf("closed", "captions"), listOf("subtitles"), listOf("subtitle"), listOf("cc"), listOf("captions"))
    private val NOT_TRACKS = setOf("offset", "delay", "sync", "description", "size", "style", "settings", "decoder", "passthrough", "boost")

    /** Whether [label] names [track] ("Audio track", "Closed captions 1", "Subtitles: English"). */
    fun isTrackEntry(label: String, track: Track): Boolean {
        val w = words(label)
        if (w.isEmpty() || w.any { it in NOT_TRACKS }) return false
        val labels = if (track == Track.Audio) AUDIO_LABELS else SUBTITLE_LABELS
        return labels.any { l -> w == l || (w.size > l.size && w.take(l.size) == l && w.size <= l.size + 3) }
    }

    /** Index of the tile for [track] in the menu row, by name first, then by its neighbours. */
    fun trackTile(row: MenuRow, track: Track): Int? {
        val labels = row.tiles.map { it.text }
        labels.indexOfFirst { isTrackEntry(it, track) }.takeIf { it >= 0 }?.let { return it }
        val offset = labels.indexOfFirst(::isOffset)
        val video = labels.indexOfFirst(::isResolution)
        return when (track) {
            Track.Audio -> when {
                offset > 0 -> offset - 1
                video >= 0 && video + 1 < labels.size -> video + 1
                else -> null
            }
            Track.Subtitles -> if (offset >= 0 && offset + 1 < labels.size) offset + 1 else null
        }
    }

    /** Keys that move the menu focus from the focused tile to [target] and open it. */
    fun keysTo(row: MenuRow, target: Int): List<String> {
        val delta = target - row.focused
        val move = if (delta >= 0) "KEYCODE_DPAD_RIGHT" else "KEYCODE_DPAD_LEFT"
        return List(kotlin.math.abs(delta)) { move } + "KEYCODE_DPAD_CENTER"
    }

    data class TrackOption(val label: String, val selected: Boolean)

    /** A track dialog: "Audio tracks" or "Closed captions" with one row per choice. */
    data class TrackDialog(val title: String, val options: List<UiNode>, val focused: Int)

    private val DIALOG_TITLES = setOf("audio tracks", "audio track", "closed captions", "subtitles", "subtitle tracks")

    fun trackDialog(items: List<UiNode>): TrackDialog? {
        val title = items.firstOrNull { words(it.text).joinToString(" ") in DIALOG_TITLES && !it.clickable && it.resourceId.isNotEmpty() && !it.resourceId.endsWith("id/title") }
            ?: items.firstOrNull { words(it.text).joinToString(" ") in DIALOG_TITLES && !it.clickable }
            ?: return null
        val below = items.filter { it.top > title.top && it.text.isNotBlank() && it.left >= title.left - 100 }
        val titled = below.filter { it.resourceId.endsWith("id/title") }
        val rows = (titled.ifEmpty { below.filter { !it.resourceId.endsWith("id/summary") } })
            .filter { !isDialogChrome(it.text) }
            .sortedBy { it.top }
        if (rows.isEmpty()) return null
        val focusedRow = items.firstOrNull { it.focused && it.clickable && rows.any { r -> it.contains(r) } }
        val focused = focusedRow?.let { f -> rows.indexOfFirst { f.contains(it) } } ?: -1
        return TrackDialog(title.text, rows, focused)
    }

    /** The choices of an open track dialog. The focused row is the current track when it opens. */
    fun trackOptions(dialog: TrackDialog): List<TrackOption> =
        dialog.options.mapIndexed { i, node -> TrackOption(node.text, i == dialog.focused || node.checked) }

    /** Keys that move the dialog focus to the row labelled [label] and select it. */
    fun keysToOption(dialog: TrackDialog, label: String): List<String>? {
        val target = dialog.options.indexOfFirst { it.text == label }
        if (target < 0) return null
        val from = if (dialog.focused >= 0) dialog.focused else 0
        val delta = target - from
        val move = if (delta >= 0) "KEYCODE_DPAD_DOWN" else "KEYCODE_DPAD_UP"
        return List(kotlin.math.abs(delta)) { move } + "KEYCODE_DPAD_CENTER"
    }

    private fun isDialogChrome(label: String): Boolean {
        val text = words(label).joinToString(" ")
        return text in DIALOG_TITLES || text in setOf("cancel", "ok") || "settings" in words(label) ||
            text.startsWith("show closed captions") || text.startsWith("closed captions may not")
    }

    /** Full-screen playback with nothing open: the player's root view holds focus. */
    fun fullscreenPlayback(items: List<UiNode>): Boolean {
        val root = items.firstOrNull() ?: return false
        return items.any {
            it.focused && it.left == root.left && it.top == root.top && it.right == root.right && it.bottom == root.bottom
        } && items.none { it.className.endsWith("StreamingTextView") } && !onMainMenu(items)
    }

    fun words(value: String): List<String> {
        val out = ArrayList<String>()
        val current = StringBuilder()
        for (ch in value.lowercase() + " ") {
            if (ch.isLetterOrDigit()) current.append(ch) else {
                if (current.isNotEmpty()) out += current.toString()
                current.setLength(0)
            }
        }
        return out
    }
}
