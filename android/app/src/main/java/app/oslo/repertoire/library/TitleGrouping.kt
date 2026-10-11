package app.oslo.repertoire.library

/** A catalog movie/series as listed, plus what TMDB lookups found for its name (if anything yet). */
data class CatalogItem(
    val id: Long,
    val kind: String,
    val name: String,
    val icon: String? = null,
    val year: Int? = null,
    /** TMDB id the portal gave. */
    val portalTmdbId: Int? = null,
    val resolved: ResolvedInfo? = null,
) {
    val tmdbId: Int? get() = portalTmdbId ?: resolved?.tmdbId
}

/** A stored TMDB lookup: id (null = no confident match), title, year and poster URL. */
data class ResolvedInfo(val tmdbId: Int?, val title: String? = null, val year: Int? = null, val poster: String? = null)

/** One list row: every catalog version of a TMDB title, or a single unmatched entry. */
data class TitleGroup(
    val representative: CatalogItem,
    val tmdbId: Int?,
    val display: DisplayTitle,
    /** Raw provider names, the listed ones first, then other versions from the catalog. */
    val versions: List<String>,
)

object TitleGrouping {
    /**
     * Folds [items] (one list, in order) into rows: entries with the same kind and TMDB id become
     * one row at the position of the first, carrying all their names plus [otherVersions] (other
     * catalog names for that TMDB id, by kind and id). Entries without a TMDB id stay separate.
     */
    fun group(items: List<CatalogItem>, otherVersions: Map<Pair<String, Int>, List<String>> = emptyMap()): List<TitleGroup> {
        val order = ArrayList<Any>()
        val byId = LinkedHashMap<Pair<String, Int>, MutableList<CatalogItem>>()
        for (item in items) {
            val id = item.tmdbId
            if (id == null) {
                order += item
                continue
            }
            val key = item.kind to id
            val list = byId[key]
            if (list == null) {
                byId[key] = mutableListOf(item)
                order += key
            } else {
                list += item
            }
        }
        return order.map { entry ->
            if (entry is CatalogItem) {
                TitleGroup(entry, null, displayOf(entry, entry), listOf(entry.name))
            } else {
                @Suppress("UNCHECKED_CAST")
                val key = entry as Pair<String, Int>
                val members = byId.getValue(key)
                val first = members.first()
                // TMDB's title from whichever member has it stored.
                val info = members.firstOrNull { !it.resolved?.title.isNullOrBlank() && it.resolved?.tmdbId == key.second } ?: first
                val names = (members.map { it.name } + otherVersions[key].orEmpty()).distinct()
                TitleGroup(first, key.second, displayOf(first, info), names)
            }
        }
    }

    private fun displayOf(item: CatalogItem, info: CatalogItem): DisplayTitle {
        val resolved = info.resolved?.takeIf { it.tmdbId != null && it.tmdbId == info.tmdbId }
        return TitleDisplay.of(
            kind = item.kind,
            rawName = item.name,
            tmdbTitle = resolved?.title,
            tmdbYear = resolved?.year,
            tmdbPoster = resolved?.poster,
            catalogIcon = item.icon,
            catalogYear = item.year,
        )
    }
}
