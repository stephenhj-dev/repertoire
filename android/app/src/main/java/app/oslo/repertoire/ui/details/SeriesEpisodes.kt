package app.oslo.repertoire.ui.details

import android.content.Context
import androidx.compose.runtime.Immutable
import app.oslo.repertoire.data.CatalogDatabase
import app.oslo.repertoire.data.PortalStore
import app.oslo.repertoire.data.XtreamInfo
import app.oslo.repertoire.series.Episode
import app.oslo.repertoire.series.EpisodeAction
import app.oslo.repertoire.series.EpisodeMerge
import app.oslo.repertoire.series.NextUp
import app.oslo.repertoire.series.ProviderSeries
import app.oslo.repertoire.series.Season
import app.oslo.repertoire.series.SeriesProgress
import app.oslo.repertoire.tmdb.Tmdb
import app.oslo.repertoire.tmdb.TmdbImages
import app.oslo.repertoire.tmdb.TmdbTitleDetail
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/** Seasons and episodes on a series page. */
@Immutable
data class SeriesState(
    /** The provider series (raw name) whose episodes are listed and tuned. */
    val seriesName: String? = null,
    val seasons: List<Season> = emptyList(),
    val selected: Int? = null,
    /** Provider episodes for [seriesName]; null while loading. */
    val provider: List<Episode>? = null,
    /** The provider list couldn't be read: TMDB's episodes are offered as playable instead. */
    val providerFailed: Boolean = false,
    /** TMDB episodes per season, loaded when the season is shown. */
    val tmdb: Map<Int, List<Episode>> = emptyMap(),
    val loadingSeason: Int? = null,
    val progress: SeriesProgress? = null,
    val progressKey: String? = null,
) {
    /** Every known episode, TMDB and provider merged. */
    val all: List<Episode>
        get() {
            val provider = provider.orEmpty()
            val tmdbAll = tmdb.values.flatten().let { list ->
                // Without a provider list, TiviMate may still have the episode: let it be played.
                if (providerFailed) list.map { it.copy(streamId = it.streamId ?: "") } else list
            }
            return EpisodeMerge.merge(tmdbAll, provider)
        }

    fun episodes(season: Int): List<Episode> = all.filter { it.season == season }

    val action: EpisodeAction? get() = NextUp.action(all, progress)

    val loading: Boolean get() = provider == null && !providerFailed
}

/** Data access for the series page; the view model keeps the state. */
internal object SeriesLoader {
    /** get_series_info for the catalog series named [name] (kind "show"). Empty when unknown. */
    suspend fun providerEpisodes(context: Context, name: String): List<Episode>? {
        val login = PortalStore(context).load() ?: return null
        val row = CatalogDatabase.get(context).catalog().titlesNamed("show", listOf(name)).firstOrNull() ?: return emptyList()
        val body = withTimeoutOrNull(15_000) {
            withContext(Dispatchers.IO) { XtreamInfo.seriesInfo(login.url, login.username, login.password, row.remoteId) }
        } ?: return null
        return withContext(Dispatchers.Default) { ProviderSeries.parse(body, name) }
    }

    suspend fun tmdbSeason(context: Context, tvId: Int, season: Int): List<Episode> {
        val detail = Tmdb.season(context, tvId, season)
        return detail.episodes.map {
            Episode(
                season = season,
                number = it.episodeNumber,
                title = it.name?.takeIf { n -> n.isNotBlank() },
                overview = it.overview?.takeIf { o -> o.isNotBlank() },
                runtimeMinutes = it.runtime?.takeIf { r -> r > 0 },
                still = TmdbImages.still(it.stillPath),
                airDate = it.airDate,
            )
        }
    }

    fun tmdbSeasons(detail: TmdbTitleDetail?): List<Season> = detail?.seasons.orEmpty().map {
        Season(it.seasonNumber, it.name?.takeIf { n -> n.isNotBlank() } ?: if (it.seasonNumber == 0) "Specials" else "Season ${it.seasonNumber}", it.episodeCount, TmdbImages.poster(it.posterPath))
    }
}
