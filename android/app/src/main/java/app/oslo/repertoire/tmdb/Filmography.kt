package app.oslo.repertoire.tmdb

/** How a person's credits are split on their page. */
enum class CreditGroup(val label: String, val department: String) {
    Directing("Directing", "Directing"),
    Acting("Acting", "Acting"),
    Writing("Writing", "Writing"),
    Production("Production", "Production"),
    Crew("Other crew", ""),
}

/** One title in a filmography, with every role the person had on it merged together. */
data class FilmCredit(
    val mediaType: String,
    val id: Int,
    val title: String,
    val originalTitle: String?,
    val date: String?,
    val posterPath: String?,
    val roles: List<String>,
    val episodeCount: Int?,
    val popularity: Double,
    val voteCount: Int,
) {
    val year: Int? get() = TitleNormalizer.yearOf(date)
    val roleLine: String get() = roles.joinToString(" · ")
    fun ref() = TmdbRef(mediaType, id, title, originalTitle, year)
}

object Filmography {
    private const val TALK = 10767
    private const val NEWS = 10763
    private val SELF = Regex("""^\s*(self|himself|herself|themselves|themself)\b""", RegexOption.IGNORE_CASE)
    private val IGNORED_JOBS = setOf("Thanks", "Special Thanks", "In Memory Of", "Dedicated To")
    private val DIRECTOR_JOBS = setOf("Director", "Co-Director")

    /**
     * Splits combined credits into groups, drops talk-show and news appearances as "Self",
     * and merges duplicates (the same title credited several times) into one entry per group.
     * Each group is sorted oldest first.
     */
    /**
     * Backdrop for a person's page: from their most-voted film in their own department (acting
     * for actors, directing for directors), falling back to any credit; series and "Self"
     * appearances only when there is nothing else.
     */
    fun heroBackdrop(credits: TmdbCombinedCredits, department: String?): String? {
        val own = when (department) {
            null, "Acting" -> credits.cast
            "Directing" -> credits.crew.filter { it.job.orEmpty() in DIRECTOR_JOBS }
            else -> credits.crew.filter { it.department == department }
        }
        fun best(list: List<TmdbCredit>) = list
            .filter { !it.backdropPath.isNullOrBlank() && !it.adult }
            .filterNot { it.mediaType == "tv" && (TALK in it.genreIds || NEWS in it.genreIds || it.character?.let(SELF::containsMatchIn) == true) }
            .sortedWith(compareBy<TmdbCredit> { it.mediaType != "movie" }.thenByDescending { it.voteCount })
            .firstOrNull()?.backdropPath
        return best(own) ?: best(credits.cast + credits.crew)
    }

    fun build(credits: TmdbCombinedCredits): Map<CreditGroup, List<FilmCredit>> {
        val grouped = linkedMapOf<CreditGroup, MutableList<Pair<TmdbCredit, String>>>()
        for (credit in credits.cast) {
            if (credit.mediaType != "movie" && credit.mediaType != "tv") continue
            if (credit.mediaType == "tv" && (TALK in credit.genreIds || NEWS in credit.genreIds)) continue
            if (credit.mediaType == "tv" && credit.character?.let(SELF::containsMatchIn) == true) continue
            grouped.getOrPut(CreditGroup.Acting) { mutableListOf() } += credit to credit.character.orEmpty()
        }
        for (credit in credits.crew) {
            if (credit.mediaType != "movie" && credit.mediaType != "tv") continue
            val job = credit.job.orEmpty()
            if (job in IGNORED_JOBS) continue
            val group = when {
                job in DIRECTOR_JOBS -> CreditGroup.Directing
                credit.department == "Writing" -> CreditGroup.Writing
                credit.department == "Production" -> CreditGroup.Production
                else -> CreditGroup.Crew
            }
            grouped.getOrPut(group) { mutableListOf() } += credit to job
        }
        return CreditGroup.entries.mapNotNull { group ->
            val items = grouped[group] ?: return@mapNotNull null
            group to sort(merge(items), newestFirst = false)
        }.toMap()
    }

    private fun merge(items: List<Pair<TmdbCredit, String>>): List<FilmCredit> =
        items.groupBy { (credit, _) -> credit.mediaType to credit.id }.map { (_, same) ->
            val first = same.first().first
            FilmCredit(
                mediaType = first.mediaType,
                id = first.id,
                title = first.displayTitle,
                originalTitle = first.originalTitle ?: first.originalName,
                date = same.mapNotNull { it.first.date }.minOrNull(),
                posterPath = same.firstNotNullOfOrNull { it.first.posterPath },
                roles = same.map { it.second.trim() }.filter { it.isNotEmpty() }.distinct(),
                episodeCount = same.mapNotNull { it.first.episodeCount }.maxOrNull(),
                popularity = same.maxOf { it.first.popularity },
                voteCount = same.maxOf { it.first.voteCount },
            )
        }

    /** Chronological order; titles without a date (announced, unreleased) always go last. */
    fun sort(credits: List<FilmCredit>, newestFirst: Boolean): List<FilmCredit> {
        val (dated, undated) = credits.partition { !it.date.isNullOrBlank() }
        val byDate = compareBy<FilmCredit>({ it.date }, { it.title.lowercase() })
        val ordered = if (newestFirst) dated.sortedWith(byDate.reversed()) else dated.sortedWith(byDate)
        return ordered + undated.sortedBy { it.title.lowercase() }
    }

    /** The group shown first: the person's known-for department, else the biggest group. */
    fun primaryGroup(groups: Map<CreditGroup, List<FilmCredit>>, knownFor: String?): CreditGroup? {
        val preferred = CreditGroup.entries.firstOrNull { it.department.isNotEmpty() && it.department == knownFor }
        if (preferred != null && !groups[preferred].isNullOrEmpty()) return preferred
        return groups.maxByOrNull { it.value.size }?.key
    }

    /** Best-known titles across all groups, by vote count. */
    fun knownFor(groups: Map<CreditGroup, List<FilmCredit>>, limit: Int = 12): List<FilmCredit> =
        groups.values.flatten().distinctBy { it.mediaType to it.id }
            .filter { it.posterPath != null }
            .sortedByDescending { it.voteCount }
            .take(limit)
}
