package app.oslo.repertoire.tmdb

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// Subsets of TMDB v3 responses. Every field has a default so missing or null values never fail.

@Serializable
data class TmdbGenre(val id: Int = 0, val name: String = "")

@Serializable
data class TmdbCast(
    val id: Int = 0,
    val name: String = "",
    val character: String? = null,
    @SerialName("profile_path") val profilePath: String? = null,
    val order: Int = 999,
)

@Serializable
data class TmdbCrew(
    val id: Int = 0,
    val name: String = "",
    val job: String? = null,
    val department: String? = null,
    @SerialName("profile_path") val profilePath: String? = null,
)

@Serializable
data class TmdbCredits(val cast: List<TmdbCast> = emptyList(), val crew: List<TmdbCrew> = emptyList())

@Serializable
data class TmdbVideo(
    val key: String = "",
    val site: String = "",
    val type: String = "",
    val official: Boolean = false,
    val name: String = "",
    @SerialName("published_at") val publishedAt: String? = null,
)

@Serializable
data class TmdbVideos(val results: List<TmdbVideo> = emptyList())

@Serializable
data class TmdbCreator(
    val id: Int = 0,
    val name: String = "",
    @SerialName("profile_path") val profilePath: String? = null,
)

/** /movie/{id} or /tv/{id} with credits and videos appended. */
@Serializable
data class TmdbTitleDetail(
    val id: Int = 0,
    val title: String? = null,
    val name: String? = null,
    @SerialName("original_title") val originalTitle: String? = null,
    @SerialName("original_name") val originalName: String? = null,
    val overview: String? = null,
    val tagline: String? = null,
    @SerialName("poster_path") val posterPath: String? = null,
    @SerialName("backdrop_path") val backdropPath: String? = null,
    @SerialName("release_date") val releaseDate: String? = null,
    @SerialName("first_air_date") val firstAirDate: String? = null,
    @SerialName("last_air_date") val lastAirDate: String? = null,
    val runtime: Int? = null,
    @SerialName("episode_run_time") val episodeRunTime: List<Int> = emptyList(),
    @SerialName("number_of_seasons") val numberOfSeasons: Int? = null,
    @SerialName("number_of_episodes") val numberOfEpisodes: Int? = null,
    val genres: List<TmdbGenre> = emptyList(),
    @SerialName("vote_average") val voteAverage: Double = 0.0,
    @SerialName("vote_count") val voteCount: Int = 0,
    val status: String? = null,
    @SerialName("created_by") val createdBy: List<TmdbCreator> = emptyList(),
    val credits: TmdbCredits? = null,
    val videos: TmdbVideos? = null,
) {
    val displayTitle: String get() = title ?: name ?: originalTitle ?: originalName ?: ""
    val displayOriginal: String? get() = originalTitle ?: originalName
    val date: String? get() = releaseDate?.takeIf { it.isNotBlank() } ?: firstAirDate?.takeIf { it.isNotBlank() }
    val year: Int? get() = TitleNormalizer.yearOf(date)
}

@Serializable
data class TmdbSearchItem(
    val id: Int = 0,
    val title: String? = null,
    val name: String? = null,
    @SerialName("original_title") val originalTitle: String? = null,
    @SerialName("original_name") val originalName: String? = null,
    @SerialName("release_date") val releaseDate: String? = null,
    @SerialName("first_air_date") val firstAirDate: String? = null,
    @SerialName("poster_path") val posterPath: String? = null,
    val popularity: Double = 0.0,
) {
    fun candidate() = SearchCandidate(
        id = id,
        title = title ?: name ?: "",
        originalTitle = originalTitle ?: originalName,
        year = TitleNormalizer.yearOf(releaseDate ?: firstAirDate),
        popularity = popularity,
        posterPath = posterPath,
    )
}

@Serializable
data class TmdbPage<T>(
    val results: List<T> = emptyList(),
    @SerialName("total_results") val totalResults: Int = 0,
)

@Serializable
data class TmdbKnownFor(
    val title: String? = null,
    val name: String? = null,
    @SerialName("media_type") val mediaType: String? = null,
)

/** A /search/person hit. */
@Serializable
data class TmdbPersonHit(
    val id: Int = 0,
    val name: String = "",
    @SerialName("profile_path") val profilePath: String? = null,
    @SerialName("known_for_department") val knownForDepartment: String? = null,
    val popularity: Double = 0.0,
    @SerialName("known_for") val knownFor: List<TmdbKnownFor> = emptyList(),
) {
    val knownForLine: String
        get() = knownFor.mapNotNull { it.title ?: it.name }.take(2).joinToString(", ")
}

/** One entry of a person's combined_credits (cast or crew). */
@Serializable
data class TmdbCredit(
    val id: Int = 0,
    @SerialName("media_type") val mediaType: String = "movie",
    val title: String? = null,
    val name: String? = null,
    @SerialName("original_title") val originalTitle: String? = null,
    @SerialName("original_name") val originalName: String? = null,
    @SerialName("release_date") val releaseDate: String? = null,
    @SerialName("first_air_date") val firstAirDate: String? = null,
    @SerialName("poster_path") val posterPath: String? = null,
    val character: String? = null,
    val job: String? = null,
    val department: String? = null,
    @SerialName("episode_count") val episodeCount: Int? = null,
    @SerialName("genre_ids") val genreIds: List<Int> = emptyList(),
    val popularity: Double = 0.0,
    @SerialName("vote_count") val voteCount: Int = 0,
    @SerialName("vote_average") val voteAverage: Double = 0.0,
    val adult: Boolean = false,
) {
    val displayTitle: String get() = title ?: name ?: originalTitle ?: originalName ?: ""
    val date: String? get() = (releaseDate ?: firstAirDate)?.takeIf { it.isNotBlank() }
}

@Serializable
data class TmdbCombinedCredits(val cast: List<TmdbCredit> = emptyList(), val crew: List<TmdbCredit> = emptyList())

/** /person/{id} with combined_credits appended. */
@Serializable
data class TmdbPerson(
    val id: Int = 0,
    val name: String = "",
    val biography: String? = null,
    val birthday: String? = null,
    val deathday: String? = null,
    @SerialName("place_of_birth") val placeOfBirth: String? = null,
    @SerialName("profile_path") val profilePath: String? = null,
    @SerialName("known_for_department") val knownForDepartment: String? = null,
    @SerialName("combined_credits") val combinedCredits: TmdbCombinedCredits? = null,
)

object TmdbImages {
    private const val BASE = "https://image.tmdb.org/t/p/"
    fun poster(path: String?): String? = path?.takeIf { it.isNotBlank() }?.let { "${BASE}w342$it" }
    fun backdrop(path: String?): String? = path?.takeIf { it.isNotBlank() }?.let { "${BASE}w780$it" }
    fun profile(path: String?): String? = path?.takeIf { it.isNotBlank() }?.let { "${BASE}w185$it" }
}

/** The YouTube trailer to show: official trailers first, then any trailer, then a teaser. */
fun pickTrailer(videos: List<TmdbVideo>): TmdbVideo? {
    val youtube = videos.filter { it.site.equals("YouTube", ignoreCase = true) && it.key.isNotBlank() }
    return youtube.filter { it.type == "Trailer" }.sortedWith(compareBy({ !it.official }, { it.publishedAt ?: "" })).firstOrNull()
        ?: youtube.firstOrNull { it.type == "Teaser" }
}
