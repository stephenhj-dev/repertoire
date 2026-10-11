package app.oslo.repertoire.library

import app.oslo.repertoire.tmdb.TitleNormalizer

/** Picture quality of a catalog version, best first in [rank] order when 4K is preferred. */
enum class Quality(val label: String) { UHD("4K"), FHD("FHD"), HD("HD"), SD("SD") }

/**
 * What a provider name says about one version of a title: audio languages, subtitles/dubbing,
 * picture quality and HDR. "EN - Heat (1995)" -> [EN]; "MULTI-SUB Heat 4K" -> multiSub, UHD.
 */
data class VersionTags(
    val languages: List<String> = emptyList(),
    val quality: Quality? = null,
    val multi: Boolean = false,
    val sub: Boolean = false,
    val dub: Boolean = false,
    val hdr: Boolean = false,
    val dolbyVision: Boolean = false,
) {
    /** Compact label, e.g. "EN 4K HDR", "MULTI-SUB FHD"; empty when the name says nothing. */
    val label: String
        get() = buildList {
            addAll(languages)
            when {
                multi && sub -> add("MULTI-SUB")
                multi -> add("MULTI")
                sub -> add("SUB")
            }
            if (dub) add("DUB")
            quality?.let { add(it.label) }
            if (hdr) add("HDR")
            if (dolbyVision) add("DV")
        }.joinToString(" ")
}

/**
 * Pure token parser for version tags. No regular expressions (Android's ICU engine is stricter
 * than the JVM's): names are split on separator characters and tokens are looked up in tables.
 * Words that belong to the title itself ("It", "No Time to Die", "La Casa de Papel") are
 * removed first, using [TitleNormalizer], so they are never read as language codes.
 */
object VersionTagParser {
    private const val SEPARATORS = " \t|[](){}:._/,;•»«★*~!?\"'"

    /** Language tokens as providers write them, mapped to a two-letter code. */
    private val LANGUAGES: Map<String, String> = buildMap {
        fun add(code: String, vararg forms: String) {
            put(code, code)
            forms.forEach { put(it, code) }
        }
        add("EN", "ENG", "ENGLISH", "UK", "US", "GB", "USA")
        add("FR", "FRE", "FRA", "FRENCH", "VF", "VFF", "VFQ", "VFI", "TRUEFRENCH")
        add("DE", "GER", "DEU", "GERMAN", "DEUTSCH")
        add("ES", "SPA", "ESP", "SPANISH", "LAT", "LATINO", "CAST", "CASTELLANO")
        add("IT", "ITA", "ITALIAN")
        add("PT", "POR", "PORTUGUESE", "BR", "PTBR", "PT-BR")
        add("NL", "DUT", "NLD", "DUTCH")
        add("AR", "ARA", "ARABIC")
        add("TR", "TUR", "TURKISH")
        add("PL", "POL", "POLISH")
        add("RU", "RUS", "RUSSIAN")
        add("SV", "SWE", "SE", "SWEDISH")
        add("NO", "NOR", "NORWEGIAN", "NB")
        add("DA", "DAN", "DK", "DANISH")
        add("FI", "FIN", "FINNISH")
        add("EL", "GRE", "GREEK", "GR")
        add("HE", "HEB", "HEBREW")
        add("HI", "HIN", "HINDI")
        add("JA", "JPN", "JP", "JAPANESE")
        add("KO", "KOR", "KR", "KOREAN")
        add("ZH", "CHI", "CN", "CHINESE")
        add("RO", "ROM", "RON", "ROMANIAN")
        add("HU", "HUN", "HUNGARIAN")
        add("CS", "CZE", "CZ", "CZECH")
        add("FA", "PER", "PERSIAN", "FARSI", "IR")
        add("UR", "URDU")
        add("AL", "ALB", "SQ", "ALBANIAN")
        add("EX-YU", "EXYU", "YU")
    }

    /** Language codes offered in Settings, in this order. */
    val SETTING_LANGUAGES = listOf("EN", "FR", "DE", "ES", "IT", "PT", "NL", "AR", "TR", "PL", "RU", "SV", "NO", "DA", "FI", "EL", "HE", "HI", "JA", "KO", "ZH", "RO", "HU", "CS", "FA")

    private val UHD = setOf("4K", "UHD", "2160P", "2160", "4KUHD", "UHD4K")
    private val FHD = setOf("FHD", "1080P", "1080", "FULLHD", "1080I")
    private val HD = setOf("HD", "720P", "720", "HQ")
    private val SD = setOf("SD", "480P", "480", "576P", "LQ")
    private val HDR = setOf("HDR", "HDR10", "HDR10+", "HDR10PLUS", "HLG")
    private val DV = setOf("DV", "DOVI", "DOLBYVISION")
    private val MULTI = setOf("MULTI", "MULTI-AUDIO", "MULTIAUDIO", "DUAL", "DUAL-AUDIO", "MULTILANG", "MULTI-LANG")
    private val SUB = setOf("SUB", "SUBS", "SUBBED", "SUBTITLED", "VOST", "VOSTA", "STFR", "VO")
    private val MULTI_SUB = setOf("MULTI-SUB", "MULTISUB", "MULTI-SUBS", "MULTISUBS")
    private val DUB = setOf("DUB", "DUBBED", "DUBLADO", "DUAL-DUB")

    /** Splits [raw] into tokens on separator characters, keeping '-' and '+' inside tokens. */
    internal fun tokens(raw: String): List<String> {
        val out = ArrayList<String>()
        val current = StringBuilder()
        fun flush() {
            val token = current.toString().trim('-', '+')
            if (token.isNotEmpty()) out += token
            current.setLength(0)
        }
        for (ch in raw) {
            if (SEPARATORS.indexOf(ch) >= 0) flush() else current.append(ch)
        }
        flush()
        // "EN - Heat": a lone dash between words is a separator, not part of a token.
        return out.filter { it != "-" }.flatMap { token ->
            if (token.contains('-') && !isTag(token.uppercase())) token.split('-').filter { it.isNotEmpty() } else listOf(token)
        }
    }

    /** True for a word that is only a version tag ("EN", "4K", "MULTI-SUB"), in any case. */
    fun isTagWord(word: String): Boolean = isTag(word.uppercase())

    private fun isTag(upper: String): Boolean =
        upper in LANGUAGES || upper in MULTI || upper in MULTI_SUB || upper in SUB || upper in DUB ||
            upper in UHD || upper in FHD || upper in HD || upper in SD || upper in HDR || upper in DV ||
            upper == "VOSTFR" || upper == "DOLBY" || upper == "VISION"

    /**
     * Words of the clean title. Tags the normalizer leaves at either end ("MULTI-SUB Heat",
     * "Heat FR") are dropped when written in capitals next to a normally cased title word.
     */
    internal fun titleTokens(raw: String): List<String> {
        val words = tokens(TitleNormalizer.parse(raw).title).toMutableList()
        fun capitalTag(word: String) = word.length >= 2 && word.none { it.isLowerCase() } && isTag(word.uppercase())
        fun hasCasedWord() = words.any { w -> w.any { it.isLowerCase() } }
        while (words.size > 1 && capitalTag(words.first()) && hasCasedWord()) words.removeAt(0)
        while (words.size > 1 && capitalTag(words.last()) && hasCasedWord()) words.removeAt(words.size - 1)
        return words
    }

    fun parse(raw: String): VersionTags {
        val all = tokens(raw)
        // Drop the words of the clean title once each, so title words never count as tags.
        val titleWords = titleTokens(raw).map { it.uppercase() }.toMutableList()
        val decoration = ArrayList<String>()
        for (token in all) {
            val upper = token.uppercase()
            val at = titleWords.indexOf(upper)
            if (at >= 0) titleWords.removeAt(at) else decoration += upper
        }

        val languages = LinkedHashSet<String>()
        var quality: Quality? = null
        var multi = false
        var sub = false
        var dub = false
        var hdr = false
        // "Dolby Vision" reads as two title words to the normalizer.
        var dv = all.indices.any { all[it].equals("DOLBY", true) && all.getOrNull(it + 1).equals("VISION", true) }
        fun better(q: Quality) {
            val now = quality
            if (now == null || q.ordinal < now.ordinal) quality = q
        }
        var i = 0
        while (i < decoration.size) {
            val t = decoration[i]
            val next = decoration.getOrNull(i + 1)
            when {
                t == "DOLBY" && next == "VISION" -> { dv = true; i++ }
                t == "MULTI" && (next == "SUB" || next == "SUBS") -> { multi = true; sub = true; i++ }
                t in MULTI_SUB -> { multi = true; sub = true }
                t in MULTI -> multi = true
                t == "VOSTFR" -> { languages += "FR"; sub = true }
                t in SUB -> sub = true
                t in DUB -> dub = true
                t in UHD -> better(Quality.UHD)
                t in FHD -> better(Quality.FHD)
                t in HD -> better(Quality.HD)
                t in SD -> better(Quality.SD)
                t in HDR -> hdr = true
                t in DV -> dv = true
                t in LANGUAGES -> languages += LANGUAGES.getValue(t)
                // "ENSUB", "FRSUB", "EN-SUB"
                t.endsWith("SUB") && t.length in 4..6 && t.removeSuffix("SUB").trimEnd('-') in LANGUAGES -> {
                    languages += LANGUAGES.getValue(t.removeSuffix("SUB").trimEnd('-'))
                    sub = true
                }
            }
            i++
        }
        return VersionTags(languages.toList(), quality, multi, sub, dub, hdr, dv)
    }

    /** The two-letter code for a device locale language ("nb" -> "NO"), or EN when unknown. */
    fun languageForLocale(language: String?): String {
        val upper = language.orEmpty().uppercase()
        val mapped = when (upper) {
            "NN", "NB" -> "NO"
            "IW" -> "HE"
            else -> LANGUAGES[upper]
        }
        return mapped?.takeIf { it in SETTING_LANGUAGES } ?: "EN"
    }
}

/** How the user likes to watch: preferred audio language and whether 4K comes first. */
data class PlaybackPrefs(val language: String = "EN", val prefer4k: Boolean = false)

/** One playable catalog entry of a title. */
data class Version(val name: String, val tags: VersionTags = VersionTagParser.parse(name))

object VersionPicker {
    private fun qualityRank(q: Quality?, prefer4k: Boolean): Int = if (prefer4k) {
        when (q) { Quality.UHD -> 0; Quality.FHD -> 1; Quality.HD -> 2; null -> 3; Quality.SD -> 4 }
    } else {
        // 4K streams are heavy; unless asked for, a good HD stream comes first.
        when (q) { Quality.FHD -> 0; Quality.HD -> 1; null -> 2; Quality.UHD -> 3; Quality.SD -> 4 }
    }

    /**
     * 0 = audio in the preferred language, 1 = English audio (the fallback), 2 = multi-audio,
     * 3 = preferred language as subtitles only, 4 = untagged, 5 = anything else.
     */
    private fun languageRank(tags: VersionTags, preferred: String): Int {
        val subOnly = tags.sub && !tags.dub
        return when {
            preferred in tags.languages && !subOnly -> 0
            "EN" in tags.languages && !subOnly -> 1
            tags.multi && !tags.sub -> 2
            preferred in tags.languages -> 3
            tags.languages.isEmpty() && !tags.multi -> 4
            tags.multi -> 4
            else -> 5
        }
    }

    /** Versions best first for [prefs]; ties keep the plainest (shortest) provider name. */
    fun order(versions: List<Version>, prefs: PlaybackPrefs): List<Version> =
        versions.sortedWith(
            compareBy<Version>(
                { languageRank(it.tags, prefs.language) },
                { qualityRank(it.tags.quality, prefs.prefer4k) },
                { if (it.tags.hdr || it.tags.dolbyVision) (if (prefs.prefer4k) 0 else 1) else (if (prefs.prefer4k) 1 else 0) },
                { it.name.length },
                { it.name },
            ),
        )

    fun best(versions: List<Version>, prefs: PlaybackPrefs): Version? = order(versions, prefs).firstOrNull()

    /** Badge line for a group of versions: languages (preferred first) and the best quality. */
    fun summary(versions: List<Version>, preferred: String, maxLanguages: Int = 4): VersionSummary {
        val counts = LinkedHashMap<String, Int>()
        versions.forEach { v -> v.tags.languages.forEach { counts[it] = (counts[it] ?: 0) + 1 } }
        val languages = counts.keys.sortedWith(compareBy({ if (it == preferred) 0 else if (it == "EN") 1 else 2 }, { -(counts[it] ?: 0) }, { it }))
        val shown = languages.take(maxLanguages).toMutableList()
        if (versions.any { it.tags.multi && !it.tags.sub }) shown += "MULTI"
        if (versions.any { it.tags.sub }) shown += if (versions.any { it.tags.multi && it.tags.sub }) "MULTI-SUB" else "SUB"
        val quality = versions.mapNotNull { it.tags.quality }.minByOrNull { it.ordinal }
        return VersionSummary(
            languages = shown,
            moreLanguages = (languages.size - maxLanguages).coerceAtLeast(0),
            quality = quality,
            hdr = versions.any { it.tags.hdr || it.tags.dolbyVision },
            count = versions.size,
        )
    }
}

data class VersionSummary(
    val languages: List<String>,
    val moreLanguages: Int,
    val quality: Quality?,
    val hdr: Boolean,
    val count: Int,
) {
    /** "EN · FR · DE  4K" */
    val line: String
        get() {
            val langs = (languages + listOfNotNull(if (moreLanguages > 0) "+$moreLanguages" else null)).joinToString(" · ")
            val pic = listOfNotNull(quality?.label, if (hdr) "HDR" else null).joinToString(" ")
            return listOf(langs, pic).filter { it.isNotEmpty() }.joinToString("  ")
        }

    /** "+2 versions" beside the badges, when there is more than one. */
    val moreVersions: String? get() = if (count > 1) "+${count - 1} ${if (count == 2) "version" else "versions"}" else null
}
