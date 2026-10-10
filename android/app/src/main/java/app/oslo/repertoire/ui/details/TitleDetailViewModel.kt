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
import app.oslo.repertoire.data.CatalogDatabase
import app.oslo.repertoire.navigation.TitleDetailRoute
import app.oslo.repertoire.tmdb.LibraryEntry
import app.oslo.repertoire.tmdb.TitleNormalizer
import app.oslo.repertoire.tmdb.Tmdb
import app.oslo.repertoire.tmdb.TmdbRef
import app.oslo.repertoire.tmdb.TmdbTitleDetail
import app.oslo.repertoire.tmdb.tmdbMediaType
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

@Immutable
data class TitleDetailState(
    val loading: Boolean = true,
    val detail: TmdbTitleDetail? = null,
    /** TMDB had no confident match for this catalog title. */
    val notFound: Boolean = false,
    val error: String? = null,
    /** Catalog entries for this title, best version first; empty when it is not in the catalog. */
    val library: List<LibraryEntry> = emptyList(),
)

class TitleDetailViewModel(app: Application, handle: SavedStateHandle) : AndroidViewModel(app) {
    val route: TitleDetailRoute = handle.toRoute()
    val mediaType: String = tmdbMediaType(route.kind)

    var state by mutableStateOf(TitleDetailState(loading = Tmdb.enabled))
        private set

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            val context = getApplication<Application>()
            val row = if (route.catalogId > 0) CatalogDatabase.get(context).catalog().titleById(route.catalogId) else null
            val own = row?.let { listOf(LibraryEntry(it.id, it.kind, it.name, it.icon, it.tmdbId, it.year)) }.orEmpty()
            if (!Tmdb.enabled) {
                state = TitleDetailState(loading = false, library = own)
                return@launch
            }
            state = state.copy(loading = true, error = null, library = state.library.ifEmpty { own })
            try {
                val id = route.tmdbId
                    ?: row?.let { Tmdb.resolve(context, it) }
                    ?: Tmdb.searchTitle(context, mediaType, TitleNormalizer.parse(route.name))
                if (id == null) {
                    state = TitleDetailState(loading = false, notFound = true, library = own)
                    return@launch
                }
                val detail = Tmdb.title(context, mediaType, id)
                val found = Tmdb.library(context).find(TmdbRef(mediaType, id, detail.displayTitle, detail.displayOriginal, detail.year))
                state = TitleDetailState(loading = false, detail = detail, library = (own + found).distinctBy { it.id })
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                state = TitleDetailState(loading = false, error = "Couldn't load details from TMDB. Check the connection and try again.", library = own)
            }
        }
    }
}
