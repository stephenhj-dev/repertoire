package app.oslo.repertoire.ui.details

import android.app.Application
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import app.oslo.repertoire.Perf
import app.oslo.repertoire.data.CatalogDatabase
import app.oslo.repertoire.data.PortalStore
import app.oslo.repertoire.data.ResolvedTitle
import app.oslo.repertoire.data.SeriesProgressRow
import app.oslo.repertoire.series.EpisodeMerge
import app.oslo.repertoire.series.NextUp
import app.oslo.repertoire.series.SeriesProgress
import app.oslo.repertoire.data.VersionDetail
import app.oslo.repertoire.data.XtreamInfo
import app.oslo.repertoire.navigation.TitleDetailRoute
import app.oslo.repertoire.tmdb.CatalogLookup
import app.oslo.repertoire.tmdb.TitleNormalizer
import app.oslo.repertoire.tmdb.Tmdb
import app.oslo.repertoire.tmdb.TmdbRef
import app.oslo.repertoire.tmdb.TmdbTitleDetail
import app.oslo.repertoire.tmdb.tmdbMediaType
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/** Portal details for one version, loaded when the user asks. */
sealed interface VersionInfo {
    data object Loading : VersionInfo
    data class Loaded(val detail: VersionDetail?) : VersionInfo
    data object Failed : VersionInfo
}

@Immutable
data class TitleDetailState(
    val loading: Boolean = true,
    val detail: TmdbTitleDetail? = null,
    /** TMDB had no confident match for this catalog title. */
    val notFound: Boolean = false,
    val error: String? = null,
    /**
     * Raw provider names of the catalog versions known up front: the opened entry plus the
     * entries with the same TMDB id (one indexed query). Enough to play the preferred one.
     */
    val versions: List<String> = emptyList(),
    /** "More versions" was opened: also search by name, and show per-version details on tap. */
    val expanded: Boolean = false,
    val searchingMore: Boolean = false,
    /** Per-version portal details, by raw name. */
    val info: Map<String, VersionInfo> = emptyMap(),
    /** Seasons and episodes (series only). */
    val series: SeriesState = SeriesState(),
) {
    val inCatalog: Boolean get() = versions.isNotEmpty()
}

/**
 * Title page data. Up front only the cheap parts load: the catalog row, its TMDB id (stored
 * lookup or portal id first) and, in parallel, TMDB's details and the same-id versions from
 * the database. Name matching for other versions and per-version portal details wait until
 * "More versions" is opened, so the page and Play stay fast.
 */
class TitleDetailViewModel(app: Application, handle: SavedStateHandle) : AndroidViewModel(app) {
    val route: TitleDetailRoute = handle.toRoute()
    val mediaType: String = tmdbMediaType(route.kind)

    var state by mutableStateOf(TitleDetailState(loading = Tmdb.enabled))
        private set

    private var tmdbId: Int? = route.tmdbId
    private var loadJob: Job? = null
    private var moreJob: Job? = null
    private val infoJobs = HashMap<String, Job>()
    private var providerJob: Job? = null
    private var seasonJob: Job? = null
    private var progressJob: Job? = null
    val isSeries: Boolean get() = route.kind == "show"

    init {
        load()
    }

    fun load() {
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            val started = System.nanoTime()
            fun mark(step: String) = Perf.log("detail ${route.name}: $step at ${(System.nanoTime() - started) / 1_000_000} ms")
            val context = getApplication<Application>()
            val dao = CatalogDatabase.get(context).catalog()
            val row = if (route.catalogId > 0) dao.titleById(route.catalogId) else null
            val own = listOfNotNull(row?.name ?: route.name.takeIf { route.catalogId > 0 })
            state = state.copy(loading = Tmdb.enabled, error = null, versions = state.versions.ifEmpty { own })
            mark("row")
            if (!Tmdb.enabled) {
                state = state.copy(loading = false)
                return@launch
            }
            try {
                val id = tmdbId
                    ?: row?.let { Tmdb.resolve(context, it) }
                    ?: Tmdb.searchTitle(context, mediaType, TitleNormalizer.parse(route.name))
                tmdbId = id
                mark("id $id")
                if (id == null) {
                    state = state.copy(loading = false, notFound = true)
                    return@launch
                }
                // Same-id versions straight from the database, while TMDB answers.
                val sameId = async {
                    runCatching { CatalogLookup.byIds(context, route.kind, listOf(id))[id].orEmpty().map { it.name } }.getOrDefault(emptyList())
                }
                val detail = async { Tmdb.title(context, mediaType, id) }
                state = state.copy(versions = (own + sameId.await()).distinct())
                mark("versions ${state.versions.size}")
                val loaded = detail.await()
                mark("tmdb detail")
                // Remember the answer so lists show this title cleanly too.
                if (row != null && row.tmdbId == null) {
                    runCatching {
                        dao.saveResolved(listOf(ResolvedTitle(row.kind, row.name, id, loaded.displayTitle, loaded.year, loaded.posterPath, System.currentTimeMillis())))
                    }
                }
                state = state.copy(loading = false, detail = loaded, notFound = false)
                if (isSeries) onSeriesDetail(loaded)
                if (state.expanded) searchMore()
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                state = state.copy(loading = false, error = "Couldn't load details from TMDB. Check the connection and try again.")
            }
        }
    }

    /** Opens "More versions": also looks for catalog entries without an id that match by name. */
    fun expand() {
        if (state.expanded) return
        state = state.copy(expanded = true)
        searchMore()
    }

    private fun searchMore() {
        val detail = state.detail ?: return
        val id = tmdbId ?: return
        moreJob?.cancel()
        moreJob = viewModelScope.launch {
            state = state.copy(searchingMore = true)
            try {
                val found = Perf.time("detail ${route.name}: name search") {
                    CatalogLookup.byName(getApplication(), TmdbRef(mediaType, id, detail.displayTitle, detail.displayOriginal, detail.year))
                }
                state = state.copy(versions = (state.versions + found.map { it.name }).distinct())
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                // Name matching is a bonus; keep what we have.
            } finally {
                state = state.copy(searchingMore = false)
            }
        }
    }

    /** Loads portal stream details (resolution, audio, length) for one version; tap again to retry. */
    fun loadInfo(name: String) {
        val now = state.info[name]
        if (now is VersionInfo.Loading || now is VersionInfo.Loaded) return
        val login = PortalStore(getApplication()).load() ?: return
        infoJobs[name]?.cancel()
        state = state.copy(info = state.info + (name to VersionInfo.Loading))
        infoJobs[name] = viewModelScope.launch {
            val result = try {
                val dao = CatalogDatabase.get(getApplication()).catalog()
                val row = dao.titlesNamed(route.kind, listOf(name)).firstOrNull()
                if (row == null) {
                    VersionInfo.Loaded(null)
                } else {
                    val detail = withTimeoutOrNull(10_000) {
                        withContext(Dispatchers.IO) { XtreamInfo.details(login.url, login.username, login.password, row.kind, row.remoteId) }
                    }
                    VersionInfo.Loaded(detail)
                }
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                VersionInfo.Failed
            }
            state = state.copy(info = state.info + (name to result))
        }
    }

    // Series: seasons and episodes. The provider list loads for the version that Play would use
    // (the screen knows the playback preferences); TMDB episodes load per season when shown.

    private fun updateSeries(block: (SeriesState) -> SeriesState) {
        state = state.copy(series = block(state.series))
    }

    /** Lists the episodes of [name] (the preferred version), and follows its saved progress. */
    fun showEpisodesOf(name: String) {
        if (!isSeries || state.series.seriesName == name) return
        updateSeries { it.copy(seriesName = name, provider = null, providerFailed = false) }
        watchProgress()
        providerJob?.cancel()
        providerJob = viewModelScope.launch {
            val found = try {
                Perf.time("detail ${route.name}: series info") { SeriesLoader.providerEpisodes(getApplication(), name) }
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                null
            }
            updateSeries { s ->
                val next = if (found == null) s.copy(provider = emptyList(), providerFailed = true) else s.copy(provider = found)
                withSeasons(next)
            }
            ensureSeasonLoaded()
        }
    }

    private fun onSeriesDetail(detail: TmdbTitleDetail) {
        updateSeries { withSeasons(it) }
        watchProgress()
        ensureSeasonLoaded()
    }

    private fun withSeasons(s: SeriesState): SeriesState {
        val seasons = EpisodeMerge.seasons(SeriesLoader.tmdbSeasons(state.detail), s.provider.orEmpty())
        val selected = s.selected?.takeIf { n -> seasons.any { it.number == n } } ?: NextUp.initialSeason(seasons, s.progress)
        return s.copy(seasons = seasons, selected = selected)
    }

    fun selectSeason(number: Int) {
        if (state.series.selected == number) return
        updateSeries { it.copy(selected = number) }
        ensureSeasonLoaded()
    }

    private fun ensureSeasonLoaded() {
        val season = state.series.selected ?: return
        val id = tmdbId ?: return
        if (!Tmdb.enabled || state.series.tmdb.containsKey(season) || state.detail == null) return
        seasonJob?.cancel()
        seasonJob = viewModelScope.launch {
            updateSeries { it.copy(loadingSeason = season) }
            val episodes = try {
                Perf.time("detail ${route.name}: tmdb season $season") { SeriesLoader.tmdbSeason(getApplication(), id, season) }
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                emptyList()
            }
            updateSeries { it.copy(tmdb = it.tmdb + (season to episodes), loadingSeason = null) }
        }
    }

    /** Follows the series' last played episode in Room. Keyed by TMDB id, else the provider name. */
    private fun watchProgress() {
        val key = SeriesProgressRow.key(tmdbId, state.series.seriesName ?: route.name.takeIf { route.catalogId > 0 }) ?: return
        if (state.series.progressKey == key) return
        updateSeries { it.copy(progressKey = key) }
        progressJob?.cancel()
        progressJob = viewModelScope.launch {
            CatalogDatabase.get(getApplication()).seriesProgress().progress(key).collect { row ->
                val progress = row?.let { SeriesProgress(it.season, it.episode) }
                val jump = state.series.progress == null && progress != null
                updateSeries { s ->
                    val next = s.copy(progress = progress)
                    // First time progress shows up, open its season.
                    if (jump && progress != null && next.seasons.any { it.number == progress.season }) next.copy(selected = progress.season) else next
                }
                if (jump) ensureSeasonLoaded()
            }
        }
    }

    /** Plays [episode] of the listed provider series, remembering it as the last played. */
    fun playEpisode(model: app.oslo.repertoire.GuideViewModel, episode: app.oslo.repertoire.series.Episode) {
        val name = state.series.seriesName ?: return
        model.playEpisode(name, episode.season, episode.number, episode.sourceTitle ?: episode.title, state.series.progressKey)
    }
}
