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
import app.oslo.repertoire.navigation.PersonRoute
import app.oslo.repertoire.tmdb.CreditGroup
import app.oslo.repertoire.tmdb.FilmCredit
import app.oslo.repertoire.tmdb.Filmography
import app.oslo.repertoire.tmdb.LibraryEntry
import app.oslo.repertoire.tmdb.Tmdb
import app.oslo.repertoire.tmdb.TmdbCombinedCredits
import app.oslo.repertoire.tmdb.TmdbPerson
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** A filmography entry plus the catalog versions of it, if any. */
@Immutable
data class PersonCredit(val credit: FilmCredit, val library: List<LibraryEntry>) {
    val inLibrary: Boolean get() = library.isNotEmpty()
}

@Immutable
data class PersonState(
    val loading: Boolean = true,
    val person: TmdbPerson? = null,
    val groups: Map<CreditGroup, List<PersonCredit>> = emptyMap(),
    val knownFor: List<PersonCredit> = emptyList(),
    val error: String? = null,
)

class PersonViewModel(app: Application, handle: SavedStateHandle) : AndroidViewModel(app) {
    val route: PersonRoute = handle.toRoute()

    var state by mutableStateOf(PersonState(loading = Tmdb.enabled))
        private set
    var group by mutableStateOf<CreditGroup?>(null)
    var newestFirst by mutableStateOf(false)
    var onlyLibrary by mutableStateOf(false)

    init {
        load()
    }

    /** The selected group's credits in the chosen order, optionally only those in the catalog. */
    val visible: List<PersonCredit>
        get() {
            val all = state.groups[group].orEmpty()
            val byCredit = all.associateBy { it.credit.mediaType to it.credit.id }
            val sorted = Filmography.sort(all.map { it.credit }, newestFirst).mapNotNull { byCredit[it.mediaType to it.id] }
            return if (onlyLibrary) sorted.filter { it.inLibrary } else sorted
        }

    fun load() {
        if (!Tmdb.enabled) {
            state = PersonState(loading = false, error = "TMDB isn't set up in this build.")
            return
        }
        viewModelScope.launch {
            state = state.copy(loading = true, error = null)
            val context = getApplication<Application>()
            try {
                val person = Tmdb.person(context, route.tmdbPersonId)
                val library = Tmdb.library(context)
                val (groups, known) = withContext(Dispatchers.Default) {
                    val built = Filmography.build(person.combinedCredits ?: TmdbCombinedCredits())
                    val withLibrary = built.mapValues { (_, credits) -> credits.map { PersonCredit(it, library.find(it.ref())) } }
                    val known = Filmography.knownFor(built).map { PersonCredit(it, library.find(it.ref())) }
                    withLibrary to known
                }
                if (group == null || group !in groups) group = Filmography.primaryGroup(built(groups), person.knownForDepartment)
                state = PersonState(loading = false, person = person, groups = groups, knownFor = known)
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                state = state.copy(loading = false, error = "Couldn't load this person from TMDB. Check the connection and try again.")
            }
        }
    }

    private fun built(groups: Map<CreditGroup, List<PersonCredit>>) = groups.mapValues { (_, list) -> list.map { it.credit } }
}
