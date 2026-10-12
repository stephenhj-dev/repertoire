package app.oslo.repertoire.repertoire

import app.oslo.repertoire.data.WatchKey
import app.oslo.repertoire.tmdb.CreditGroup
import app.oslo.repertoire.tmdb.FilmCredit
import app.oslo.repertoire.tmdb.Filmography
import app.oslo.repertoire.tmdb.catalogKind

/** One title in a person's body of work, with the catalog versions to play (empty = unavailable). */
data class WorkItem(val credit: FilmCredit, val versions: List<String>) {
    val available: Boolean get() = versions.isNotEmpty()
    val kind: String get() = catalogKind(credit.mediaType)
    val key: String get() = WatchKey.of(kind, credit.id)
}

/** Where the user stands with one person. */
data class WorkProgress(
    val watched: Int,
    val total: Int,
    val available: Int,
    val availableWatched: Int,
    /** Earliest unwatched available title, chronologically. */
    val next: WorkItem?,
) {
    /** Every available title is watched (and there was at least one). */
    val completed: Boolean get() = available > 0 && availableWatched == available
    val fraction: Float get() = if (total == 0) 0f else watched / total.toFloat()
    val label: String get() = "$watched of $total watched"
}

object BodyOfWork {
    /** Below this many episodes an actor's TV credit is a guest spot, not part of their work. */
    const val REGULAR_EPISODES = 4

    /**
     * The person's main body of work: credits of their main department (directing for
     * directors, acting for actors), released titles only, oldest first. Films always count;
     * series count when the person was a regular (acting) or worked on them (other departments).
     */
    fun mainCredits(groups: Map<CreditGroup, List<FilmCredit>>, department: String?): List<FilmCredit> {
        val group = Filmography.primaryGroup(groups, department) ?: return emptyList()
        val credits = groups[group].orEmpty().filter { !it.date.isNullOrBlank() }.filter { credit ->
            when {
                credit.mediaType == "movie" -> true
                group == CreditGroup.Acting -> (credit.episodeCount ?: 0) >= REGULAR_EPISODES
                else -> true
            }
        }
        return Filmography.sort(credits, newestFirst = false)
    }

    fun progress(items: List<WorkItem>, watched: Set<String>): WorkProgress {
        var seen = 0
        var available = 0
        var availableSeen = 0
        var next: WorkItem? = null
        for (item in items) {
            val isSeen = item.key in watched
            if (isSeen) seen++
            if (item.available) {
                available++
                if (isSeen) availableSeen++ else if (next == null) next = item
            }
        }
        return WorkProgress(seen, items.size, available, availableSeen, next)
    }

    /**
     * Whether a person newly completed: the last progress wasn't complete and this one is.
     * Used to play the milestone once, not every time the tab opens.
     */
    fun justCompleted(before: WorkProgress?, now: WorkProgress): Boolean = now.completed && before != null && !before.completed

}
