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

    // Player menu (long-press OK while watching): an icon row with Audio track, CC, Video tracks...

    enum class Track { Audio, Subtitles }

    private val AUDIO_LABELS = listOf(listOf("audio", "track"), listOf("audio", "tracks"), listOf("audio"), listOf("soundtrack"), listOf("audio", "language"))
    private val SUBTITLE_LABELS = listOf(listOf("cc"), listOf("closed", "captions"), listOf("subtitles"), listOf("subtitle"), listOf("captions"), listOf("subtitle", "track"), listOf("subtitles", "tracks"))

    /** Neighbouring menu entries that start with the same words ("Audio offset", "Subtitle size"). */
    private val NOT_TRACKS = setOf("offset", "delay", "sync", "description", "size", "style", "settings", "decoder", "passthrough", "boost")

    /** Whether [label] is the player-menu entry for [track]. */
    fun isTrackEntry(label: String, track: Track): Boolean {
        val w = words(label)
        if (w.isEmpty() || w.any { it in NOT_TRACKS }) return false
        val labels = if (track == Track.Audio) AUDIO_LABELS else SUBTITLE_LABELS
        // The entry may carry the current value after its name ("Audio track English", "CC Off").
        return labels.any { l -> w == l || (w.size > l.size && w.take(l.size) == l && w.size <= l.size + 3) }
    }

    fun trackEntry(items: List<UiNode>, track: Track): UiNode? =
        items.filter { isTrackEntry(it.label, track) }.maxByOrNull { (if (it.clickable) 2 else 0) + (if (it.text.isBlank()) 1 else 0) }

    /** The player menu is showing: TiviMate's menu has these entries side by side. */
    fun playerMenuShown(items: List<UiNode>): Boolean =
        trackEntry(items, Track.Audio) != null || trackEntry(items, Track.Subtitles) != null

    data class TrackOption(val label: String, val selected: Boolean)

    /**
     * The choices in a track dialog: the rows that appeared after opening it. Uses checkable
     * rows when TiviMate marks them, else new clickable or text rows; drops the dialog title
     * and settings entries.
     */
    fun trackOptions(before: List<UiNode>, after: List<UiNode>, track: Track): List<TrackOption> {
        val old = before.map { it.label to it.top }.toSet()
        val fresh = after.filter { it.label.isNotBlank() && (it.label to it.top) !in old }
        val checkable = fresh.filter { it.checkable }
        val rows = (checkable.ifEmpty { fresh.filter { it.clickable || it.text.isNotBlank() } })
            .filter { !isTrackEntry(it.label, track) && !isDialogChrome(it.label) }
        val selectedTexts = after.filter { it.checked || it.selected }.map { it.label }.toSet()
        return rows.distinctBy { it.label }.map { TrackOption(it.label, it.checked || it.selected || it.label in selectedTexts) }
    }

    private fun isDialogChrome(label: String): Boolean {
        val w = words(label)
        val text = w.joinToString(" ")
        return text in setOf("audio tracks", "audio track", "closed captions", "subtitles", "cc", "cancel", "ok") ||
            "settings" in w || text.startsWith("show closed captions") || text.startsWith("system settings")
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
