package app.oslo.repertoire.series

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

/** One episode, merged from TMDB (text, stills) and the provider (what is actually playable). */
data class Episode(
    val season: Int,
    val number: Int,
    val title: String? = null,
    val overview: String? = null,
    val runtimeMinutes: Int? = null,
    /** Full image URL (TMDB still or the provider's image). */
    val still: String? = null,
    val airDate: String? = null,
    /** Provider episode stream id; null when only TMDB knows the episode. */
    val streamId: String? = null,
) {
    val code: String get() = "S$season E$number"
    val inCatalog: Boolean get() = streamId != null
}

data class Season(val number: Int, val name: String, val episodeCount: Int, val poster: String? = null) {
    val isSpecials: Boolean get() = number == 0
}

/** Where the user is in a series. */
data class SeriesProgress(val season: Int, val episode: Int)

/** The primary episode action on a series page, and an optional secondary "next" one. */
data class EpisodeAction(val label: String, val episode: Episode, val next: Episode? = null)

object EpisodeMerge {
    /**
     * Union of TMDB and provider episodes by (season, number). TMDB's title, overview, still and
     * runtime win; the provider fills gaps and always supplies the stream id.
     */
    fun merge(tmdb: List<Episode>, provider: List<Episode>): List<Episode> {
        val byKey = LinkedHashMap<Pair<Int, Int>, Episode>()
        for (e in provider) byKey[e.season to e.number] = e
        for (t in tmdb) {
            val p = byKey[t.season to t.number]
            byKey[t.season to t.number] = if (p == null) t else Episode(
                season = t.season,
                number = t.number,
                title = t.title?.takeIf { it.isNotBlank() } ?: p.title,
                overview = t.overview?.takeIf { it.isNotBlank() } ?: p.overview,
                runtimeMinutes = t.runtimeMinutes ?: p.runtimeMinutes,
                still = t.still ?: p.still,
                airDate = t.airDate ?: p.airDate,
                streamId = p.streamId,
            )
        }
        return byKey.values.sortedWith(compareBy({ it.season }, { it.number }))
    }

    /**
     * Seasons to offer: TMDB's list when known (names, posters), plus any season only the
     * provider has. Regular seasons in order, specials last.
     */
    fun seasons(tmdb: List<Season>, episodes: List<Episode>): List<Season> {
        val out = LinkedHashMap<Int, Season>()
        tmdb.forEach { out[it.number] = it }
        episodes.groupBy { it.season }.forEach { (number, list) ->
            val known = out[number]
            out[number] = known?.copy(episodeCount = maxOf(known.episodeCount, list.size))
                ?: Season(number, if (number == 0) "Specials" else "Season $number", list.size)
        }
        return out.values.filter { it.episodeCount > 0 }.sortedWith(compareBy({ it.isSpecials }, { it.number }))
    }
}

object NextUp {
    /** The next episode after [after] that can be played, in season order (specials excluded). */
    fun following(episodes: List<Episode>, after: SeriesProgress): Episode? =
        episodes.asSequence()
            .filter { it.season > 0 && it.inCatalog }
            .sortedWith(compareBy({ it.season }, { it.number }))
            .firstOrNull { it.season > after.season || (it.season == after.season && it.number > after.episode) }

    /**
     * "Resume S2 E5" (with the next episode as a secondary action) when there is progress,
     * otherwise "Play S1 E1" for the first playable regular episode. Null when nothing is
     * playable.
     */
    fun action(episodes: List<Episode>, progress: SeriesProgress?): EpisodeAction? {
        val playable = episodes.filter { it.inCatalog }
        if (progress != null) {
            val last = playable.firstOrNull { it.season == progress.season && it.number == progress.episode }
            val next = following(playable, progress)
            if (last != null) return EpisodeAction("Resume ${last.code}", last, next)
            if (next != null) return EpisodeAction("Next episode · ${next.code}", next, following(playable, SeriesProgress(next.season, next.number)))
        }
        val first = playable.filter { it.season > 0 }.minWithOrNull(compareBy({ it.season }, { it.number }))
            ?: playable.minWithOrNull(compareBy({ it.season }, { it.number }))
            ?: return null
        return EpisodeAction("Play ${first.code}", first, following(playable, SeriesProgress(first.season, first.number)))
    }

    /** Season to show first: the one in progress, else the first regular season. */
    fun initialSeason(seasons: List<Season>, progress: SeriesProgress?): Int? =
        progress?.season?.takeIf { s -> seasons.any { it.number == s } }
            ?: seasons.firstOrNull { !it.isSpecials }?.number
            ?: seasons.firstOrNull()?.number
}

/** Episodes from the provider's get_series_info answer. Pure and tolerant of odd shapes. */
object ProviderSeries {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    fun parse(body: String, seriesName: String? = null): List<Episode> {
        val root = runCatching { json.parseToJsonElement(body) }.getOrNull() as? JsonObject ?: return emptyList()
        val lists: List<Pair<Int?, JsonElement>> = when (val episodes = root["episodes"]) {
            is JsonObject -> episodes.entries.map { (k, v) -> k.trim().toIntOrNull() to v }
            is JsonArray -> episodes.mapIndexed { i, v -> (i + 1) to v }
            else -> emptyList()
        }
        val out = ArrayList<Episode>()
        for ((seasonKey, list) in lists) {
            val array = list as? JsonArray ?: continue
            for ((index, item) in array.withIndex()) {
                val e = item as? JsonObject ?: continue
                val info = e["info"] as? JsonObject
                val id = text(e, "id") ?: continue
                val season = number(e, "season") ?: number(info, "season") ?: seasonKey ?: continue
                val episode = number(e, "episode_num") ?: number(info, "episode_num") ?: (index + 1)
                val duration = number(info, "duration_secs") ?: text(info, "duration")?.let(::clock)
                out += Episode(
                    season = season,
                    number = episode,
                    title = text(e, "title")?.let { cleanTitle(it, seriesName) },
                    overview = text(info, "plot") ?: text(info, "overview"),
                    runtimeMinutes = duration?.let { (it + 30) / 60 }?.takeIf { it > 0 },
                    still = text(info, "movie_image")?.takeIf { it.startsWith("http") },
                    airDate = text(info, "releasedate") ?: text(info, "air_date"),
                    streamId = id,
                )
            }
        }
        return out.distinctBy { it.season to it.number }.sortedWith(compareBy({ it.season }, { it.number }))
    }

    /**
     * Provider titles are often "Show Name - S01E02 - The Title" or "EN - Show S01E02": keep
     * what follows the SxxEyy code; null when nothing meaningful is left.
     */
    fun cleanTitle(raw: String, seriesName: String?): String? {
        val text = raw.trim()
        val upper = text.uppercase()
        var cut = -1
        var i = 0
        while (i < upper.length - 3) {
            if (upper[i] == 'S' && upper[i + 1].isDigit() && (i == 0 || !upper[i - 1].isLetterOrDigit())) {
                var j = i + 1
                while (j < upper.length && upper[j].isDigit()) j++
                if (j < upper.length && upper[j] == 'E' && j + 1 < upper.length && upper[j + 1].isDigit()) {
                    var k = j + 1
                    while (k < upper.length && upper[k].isDigit()) k++
                    cut = k
                }
            }
            i++
        }
        val rest = if (cut >= 0) text.substring(cut) else text
        val trimmed = rest.trim().trimStart('-', '–', ':', '|', '.', ' ').trim()
        if (trimmed.isEmpty()) return null
        if (seriesName != null && trimmed.equals(seriesName.trim(), ignoreCase = true)) return null
        if (cut < 0 && seriesName != null && trimmed.startsWith(seriesName.trim(), ignoreCase = true)) return null
        return trimmed
    }

    private fun clock(value: String): Int? {
        val parts = value.trim().split(':').map { it.toIntOrNull() ?: return null }
        return when (parts.size) {
            3 -> parts[0] * 3600 + parts[1] * 60 + parts[2]
            2 -> parts[0] * 60 + parts[1]
            else -> null
        }
    }

    private fun text(obj: JsonObject?, name: String): String? =
        (obj?.get(name) as? JsonPrimitive)?.contentOrNull?.trim()?.takeIf { it.isNotEmpty() && it != "null" }

    private fun number(obj: JsonObject?, name: String): Int? = text(obj, name)?.toDoubleOrNull()?.toInt()
}
