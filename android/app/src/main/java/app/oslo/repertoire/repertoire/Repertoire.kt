package app.oslo.repertoire.repertoire

import android.content.Context
import app.oslo.repertoire.data.CatalogDatabase
import app.oslo.repertoire.data.FollowedPersonRow
import app.oslo.repertoire.tmdb.CatalogLookup
import app.oslo.repertoire.tmdb.Filmography
import app.oslo.repertoire.tmdb.Tmdb
import app.oslo.repertoire.tmdb.TmdbCombinedCredits
import app.oslo.repertoire.tmdb.TmdbPerson
import app.oslo.repertoire.tmdb.catalogKind
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap

/** A person's main body of work with catalog availability. */
data class PersonWork(val person: TmdbPerson, val items: List<WorkItem>)

/**
 * Watched titles and followed people. Watched state is shared: the Repertoire tab, person
 * pages, posters, list rows, title pages and watchlists all read the same table.
 */
class Repertoire(context: Context) {
    private val app = context.applicationContext
    private val db = CatalogDatabase.get(app)
    val dao = db.repertoire()

    val watched: Flow<Set<String>> = dao.watchedKeys().map { it.toHashSet() }
    val people: Flow<List<FollowedPersonRow>> = dao.people()
    val followedIds: Flow<Set<Int>> = dao.followedIds().map { it.toSet() }

    suspend fun setWatched(kind: String, tmdbId: Int, watched: Boolean, catalogName: String? = null, auto: Boolean = false) =
        dao.setWatched(kind, tmdbId, catalogName, watched, auto, System.currentTimeMillis())

    /**
     * Marks the catalog entry [name] watched after it was played through the app. Films only:
     * a series isn't "seen" because one episode played (its progress is kept per episode).
     */
    suspend fun markPlayed(name: String) {
        val catalog = db.catalog()
        val kind = catalog.kindOf(name) ?: return
        if (kind != "movie") return
        val id = catalog.tmdbIdOf(kind, name) ?: 0
        setWatched(kind, id, watched = true, catalogName = name.takeIf { id <= 0 }, auto = true)
    }

    suspend fun follow(id: Int, name: String, profilePath: String?, department: String?, backdropPath: String? = null) {
        dao.insertPerson(FollowedPersonRow(id, name, profilePath, department, backdropPath, dao.nextPosition(), System.currentTimeMillis()))
    }

    suspend fun unfollow(id: Int) = dao.unfollow(id)

    suspend fun anyFollowed(): Boolean = dao.followedCount() > 0

    /** Body of work for [personId]; TMDB answers are disk-cached, the catalog match is a few indexed queries. */
    suspend fun work(personId: Int): PersonWork {
        cache[personId]?.let { return it }
        val person = Tmdb.person(app, personId)
        val credits = withContext(Dispatchers.Default) {
            BodyOfWork.mainCredits(Filmography.build(person.combinedCredits ?: TmdbCombinedCredits()), person.knownForDepartment)
        }
        val owned = credits.groupBy { it.mediaType }.flatMap { (mediaType, list) ->
            CatalogLookup.byIds(app, catalogKind(mediaType), list.map { it.id }).map { (id, entries) -> (mediaType to id) to entries }
        }.toMap()
        val items = credits.map { credit -> WorkItem(credit, owned[credit.mediaType to credit.id].orEmpty().map { it.name }.distinct()) }
        val backdrop = person.combinedCredits?.let { Filmography.heroBackdrop(it, person.knownForDepartment) }
        dao.refreshPerson(personId, backdrop, person.profilePath)
        return PersonWork(person, items).also { cache[personId] = it }
    }

    companion object {
        // Per process: the catalog changes rarely and TMDB answers are cached on disk anyway.
        private val cache = ConcurrentHashMap<Int, PersonWork>()

        /** Drops cached bodies of work, e.g. after the catalog was reloaded. */
        fun invalidate() = cache.clear()
    }
}
