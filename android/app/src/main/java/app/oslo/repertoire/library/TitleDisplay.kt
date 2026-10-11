package app.oslo.repertoire.library

import app.oslo.repertoire.tmdb.TitleNormalizer

/** What a list row shows for a catalog movie or series. */
data class DisplayTitle(
    val title: String,
    val year: Int?,
    val poster: String?,
    /** True when [title] comes from a confident TMDB match. */
    val matched: Boolean,
)

object TitleDisplay {
    /**
     * The TMDB title, year and poster when a confident match exists; otherwise the catalog name
     * cleaned by [TitleNormalizer] (and stray capital tags), the catalog year and the portal's
     * cover. Live channels keep their name as is.
     */
    fun of(
        kind: String,
        rawName: String,
        tmdbTitle: String? = null,
        tmdbYear: Int? = null,
        tmdbPoster: String? = null,
        catalogIcon: String? = null,
        catalogYear: Int? = null,
    ): DisplayTitle {
        if (kind == "live") return DisplayTitle(rawName.trim(), null, catalogIcon, matched = false)
        val tmdb = tmdbTitle?.trim()?.takeIf { it.isNotEmpty() }
        if (tmdb != null) {
            return DisplayTitle(tmdb, tmdbYear ?: catalogYear, tmdbPoster ?: catalogIcon, matched = true)
        }
        val parsed = TitleNormalizer.parse(rawName)
        return DisplayTitle(cleanName(rawName), catalogYear ?: parsed.year, catalogIcon, matched = false)
    }

    /** "EN - Heat (1995) 4K" -> "Heat"; "MULTI-SUB Heat FR" -> "Heat". Never empty. */
    fun cleanName(raw: String): String {
        val words = TitleNormalizer.parse(raw).title.split(' ').filter { it.isNotEmpty() }.toMutableList()
        fun capitalTag(word: String): Boolean {
            val core = word.trim('-', ':', '|', ',', '.', '[', ']', '(', ')')
            return core.length >= 2 && core.none { it.isLowerCase() } && VersionTagParser.tokens(core).all(VersionTagParser::isTagWord)
        }
        fun hasCasedWord() = words.any { w -> w.any { it.isLowerCase() } }
        while (words.size > 1 && capitalTag(words.first()) && hasCasedWord()) words.removeAt(0)
        while (words.size > 1 && capitalTag(words.last()) && hasCasedWord()) words.removeAt(words.size - 1)
        return words.joinToString(" ").trim('-', ':', '|', ' ').ifBlank { raw.trim() }
    }

    /** "Heat (1995)" or "Heat". */
    fun line(title: DisplayTitle): String = title.year?.let { "${title.title} ($it)" } ?: title.title
}
