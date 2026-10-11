package app.oslo.repertoire.tmdb

import java.text.Normalizer
import java.util.Calendar

/** A catalog title with the IPTV decoration removed: "EN - Dune (2021) [4K]" -> "Dune", 2021. */
data class ParsedTitle(val title: String, val year: Int?)

/**
 * Pure helpers that turn IPTV catalog names into something TMDB can search for, and that fold
 * titles into comparison keys. No Android dependencies so they can be unit tested on the JVM.
 */
object TitleNormalizer {
    private val maxYear = Calendar.getInstance().get(Calendar.YEAR) + 2

    /** Bracketed or piped prefixes and tags: "[EN]", "|NF|", "(MULTI)", "{4K}". */
    private val BRACKET_PREFIX = Regex("""^\s*(\[[^\]]{1,20}\]|\|[^|]{1,20}\||\{[^}]{1,20}\}|★+)\s*[-:|]?\s*""")

    /** "EN - ", "4K-NF : ", "FR| ", "MULTI - ": a short upper-case code then a separator. */
    private val CODE_PREFIX = Regex("""^\s*([A-Z0-9][A-Z0-9+&]{0,5}(?:[ /-][A-Z0-9+&]{1,5}){0,2})\s*(?:-|:|\||•|»)\s+""")

    private val QUALITY = setOf(
        "4k", "uhd", "fhd", "hd", "sd", "hq", "hdr", "hdr10", "hdr10+", "dv", "dolby vision", "1080p", "720p", "2160p",
        "480p", "1080", "720", "2160", "hevc", "h264", "h265", "x264", "x265", "multi", "multisub", "multi-sub",
        "dual", "vostfr", "vost", "sub", "subs", "subbed", "dubbed", "dub", "web-dl", "webrip", "web", "bluray", "bdrip",
        "remux", "imax", "60fps", "50fps", "atmos", "10bit", "lq", "cam", "ts", "hdcam", "new", "vip",
    )

    private val BRACKET_GROUP = Regex("""[\[({]([^\])}]*)[\])}]""")
    private val YEAR_IN_BRACKETS = Regex("""[\[(]\s*((?:19|20)\d{2})\s*[\])]""")
    private val TRAILING_YEAR = Regex("""[\s._-]+((?:19|20)\d{2})\s*$""")
    private val SEASON = Regex("""[\s._-]+S\d{1,2}(?:\s*E\d{1,3})?\s*$""", RegexOption.IGNORE_CASE)
    private val SEASON_WORD = Regex("""[\s._-]+(?:Season|Saison|Temporada|Staffel)\s*\d{1,2}\s*$""", RegexOption.IGNORE_CASE)

    fun parse(raw: String): ParsedTitle {
        var text = raw.trim()
        // Scene-style names use dots or underscores for spaces.
        if (' ' !in text && (text.count { it == '.' } >= 2 || '_' in text)) text = text.replace('.', ' ').replace('_', ' ')

        // Leading provider/language/quality codes, possibly stacked ("4K - EN - ...").
        repeat(4) {
            val before = text
            text = text.replace(BRACKET_PREFIX, "")
            CODE_PREFIX.find(text)?.let { match ->
                val rest = text.substring(match.range.last + 1)
                if (rest.isNotBlank() && isCode(match.groupValues[1])) text = rest
            }
            if (text == before) return@repeat
        }

        var year: Int? = null
        YEAR_IN_BRACKETS.findAll(text).lastOrNull()?.let { match ->
            val value = match.groupValues[1].toInt()
            if (value in 1900..maxYear) {
                year = value
                text = text.removeRange(match.range)
            }
        }

        // Bracketed groups that are only quality/language tags.
        text = BRACKET_GROUP.replace(text) { match ->
            val inner = match.groupValues[1].trim().lowercase()
            val words = inner.split(' ', '-', '/', ',', '|').filter { it.isNotBlank() }
            if (words.all { it in QUALITY || it in LANG }) " " else match.value
        }

        // Trailing quality words ("Dune 4K", "Dune FHD HDR", "Dune - 1080p").
        text = stripTrailingTags(text)
        text = text.replace(SEASON, "").replace(SEASON_WORD, "")
        text = stripTrailingTags(text)

        if (year == null) {
            TRAILING_YEAR.find(text)?.let { match ->
                val value = match.groupValues[1].toInt()
                val rest = text.substring(0, match.range.first)
                if (value in 1900..maxYear && rest.isNotBlank()) {
                    year = value
                    text = rest
                }
            }
            text = stripTrailingTags(text)
        }

        text = text.replace(Regex("""\s+"""), " ").trim().trim('-', ':', '|', '•', '–', ' ', '.', ',')
        return ParsedTitle(text.ifBlank { raw.trim() }, year)
    }

    private fun stripTrailingTags(input: String): String {
        var text = input.trim()
        while (true) {
            val trimmed = text.trimEnd('-', ':', '|', '•', '–', ' ', '.', '+')
            val cut = trimmed.lastIndexOfAny(charArrayOf(' ', '-', '|'))
            if (cut <= 0) return trimmed
            val word = trimmed.substring(cut + 1).lowercase()
            if (word !in QUALITY) return trimmed
            text = trimmed.substring(0, cut)
        }
    }

    private val LANG = setOf(
        "en", "eng", "fr", "fre", "de", "ger", "es", "spa", "it", "ita", "nl", "pt", "br", "pl", "tr", "ar", "ru",
        "sv", "no", "da", "fi", "hi", "ja", "ko", "zh", "us", "uk", "gb", "ca", "au", "nf", "amz", "dsnp", "hbo",
    )

    /** "EN", "4K", "NF", "FR-VIP": every part a language/provider/quality code, so "X-MEN: " survives. */
    private fun isCode(code: String): Boolean =
        code.split(' ', '/', '-').filter { it.isNotEmpty() }.all { part ->
            val lower = part.lowercase()
            lower in LANG || lower in QUALITY || (part.length == 2 && part.all { it in 'A'..'Z' })
        }

    /**
     * Folds a title for comparison: lower case, no accents or punctuation, "&" as "and", no
     * leading article. "The Lord of the Rings: The Two Towers" -> "lord of the rings the two towers".
     */
    fun key(title: String): String {
        val folded = Normalizer.normalize(title, Normalizer.Form.NFD)
            .replace(Regex("""\p{Mn}+"""), "")
            .lowercase()
            .replace("&", " and ")
            .replace(Regex("""['’`´]"""), "")
            .replace(Regex("""[^\p{L}\p{N}]+"""), " ")
            .trim()
        return folded.replace(Regex("""^(the|a|an)\s+"""), "").trim()
    }

    /** The year from "2021", "2021-05-01" or "May 2021". */
    fun yearOf(value: String?): Int? =
        value?.let { Regex("""(?:19|20)\d{2}""").find(it)?.value?.toIntOrNull() }
}
