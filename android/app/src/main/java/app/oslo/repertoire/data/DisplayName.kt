package app.oslo.repertoire.data

/** A live channel name split into its provider tag ("US", "4K", "CA EN") and the channel itself. */
data class DisplayName(val tag: String?, val title: String)

private val TAG = Regex("""^([A-Z0-9][A-Z0-9 /+&|-]{0,15}?)\s*:\s*(\S.*)$""")

/**
 * Channel lists prefix names with an upper-case tag ("US: HBO HD"). Only live channels are split,
 * since movie titles contain colons of their own ("Alien: Romulus").
 */
fun splitDisplayName(name: String, kind: String = "live"): DisplayName {
    val clean = name.trim()
    if (kind != "live") return DisplayName(null, clean)
    val match = TAG.matchEntire(clean) ?: return DisplayName(null, clean)
    return DisplayName(match.groupValues[1].trim(), match.groupValues[2].trim())
}

/** One or two letters for a logo placeholder. */
fun monogram(title: String): String {
    val words = title.split(' ', '-', '_', '.', '|').filter { word -> word.any { it.isLetterOrDigit() } }
    val letters = words.take(2).map { word -> word.first { it.isLetterOrDigit() }.uppercaseChar() }
    return letters.joinToString("").ifEmpty { "TV" }
}

/** Favourites are matched by kind and name, which survive a catalog refresh (row ids do not). */
fun favoriteKey(kind: String, name: String): String = "$kind\t$name"
